package com.roinur.booktracker

import com.roinur.booktracker.data.markdown.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class GoogleMarkdownTransportTest {
    private fun server(block: (MockWebServer, GoogleMarkdownTransport) -> Unit) {
        MockWebServer().use { server -> server.start(); block(server,GoogleMarkdownTransport("synthetic-test-token",server.url("/").toString())) }
    }
    private fun metadata(text: String,version: String="10",etag: String="\"version-10\"") = MockResponse().setBody(
        JSONObject().put("id","test-file").put("title","book-test.md").put("mimeType","text/markdown").put("etag",etag)
            .put("parents",org.json.JSONArray().put(JSONObject().put("id","test-folder"))).put("version",version)
            .put("capabilities",JSONObject().put("canEdit",true)).put("md5Checksum",MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }).toString())

    @Test fun conditionalUpdateUsesStrongEtagAndUtf8MediaWithoutMetadataReplacement() = server { server,remote ->
        server.enqueue(MockResponse().setBody("{}"))
        remote.update("test-folder",MarkdownRemote("test-file","book-test.md","old","\"etag-42\""),"åäö\n\n[[idé]]")
        val request=server.takeRequest()
        assertEquals("PUT",request.method); assertEquals("\"etag-42\"",request.getHeader("If-Match"))
        assertTrue(request.path!!.startsWith("/upload/drive/v2/files/test-file?")); assertTrue(request.path!!.contains("uploadType=media"))
        assertEquals("text/markdown; charset=utf-8",request.getHeader("Content-Type")); assertEquals("åäö\n\n[[idé]]",request.body.readUtf8())
    }
    @Test fun serverPreconditionFailureIsAnExplicitConflict() = server { server,remote ->
        server.enqueue(MockResponse().setResponseCode(412))
        assertThrows(MarkdownConflict::class.java) { remote.update("test-folder",MarkdownRemote("test-file","book-test.md","old","\"stale\""),"new") }
        assertEquals("\"stale\"",server.takeRequest().getHeader("If-Match"))
    }
    @Test fun consistentReadChecksVersionContentChecksumAndStrongEtag() = server { server,remote ->
        val text="UTF-8: åäö\n"; server.enqueue(metadata(text)); server.enqueue(MockResponse().setBody(text)); server.enqueue(metadata(text))
        val result=remote.read("test-folder","test-file")!!
        assertEquals(text,result.text); assertEquals("\"version-10\"",result.etag)
        assertTrue(server.takeRequest().path!!.contains("fields=")); assertTrue(server.takeRequest().path!!.contains("alt=media"))
    }
    @Test fun changingFileDuringReadFailsClosed() = server { server,remote ->
        server.enqueue(metadata("old")); server.enqueue(MockResponse().setBody("old")); server.enqueue(metadata("new","11","\"version-11\""))
        assertThrows(MarkdownConflict::class.java) { remote.read("test-folder","test-file") }
    }
    @Test fun mismatchedChecksumFailsClosed() = server { server,remote ->
        server.enqueue(metadata("expected")); server.enqueue(MockResponse().setBody("other")); server.enqueue(metadata("expected"))
        assertThrows(MarkdownConflict::class.java) { remote.read("test-folder","test-file") }
    }
    @Test fun missingOrWeakEtagsNeverAllowUnconditionalOverwrite() = server { server,remote ->
        server.enqueue(metadata("same",etag="W/\"weak\"")); server.enqueue(MockResponse().setBody("same")); server.enqueue(metadata("same",etag="W/\"weak\""))
        assertThrows(MarkdownConflict::class.java) { remote.read("test-folder","test-file") }
        assertThrows(IllegalArgumentException::class.java) { remote.update("test-folder",MarkdownRemote("test-file","book-test.md","same",""),"new") }
    }
    @Test fun createUsesReservedIdCorrectParentAndPlainMarkdownAnd409IsRecoverable() = server { server,remote ->
        server.enqueue(MockResponse().setResponseCode(409))
        remote.create("test-folder","reserved-id","book-uuid.md","# Åäö\n")
        val request=server.takeRequest(); val body=request.body.readUtf8()
        assertTrue(body.contains("\"id\":\"reserved-id\"")); assertTrue(body.contains("\"parents\":[\"test-folder\"]"))
        assertTrue(body.contains("\"mimeType\":\"text/markdown\"")); assertFalse(body.contains("application/vnd.google-apps.document"))
        assertTrue(body.contains("# Åäö")); assertEquals(1,server.requestCount)
    }
    @Test fun only404IsMissingOtherFailuresNeverBecomeNewFiles() = server { server,remote ->
        server.enqueue(MockResponse().setResponseCode(404)); assertNull(remote.read("test-folder","test-file"))
        server.enqueue(MockResponse().setResponseCode(403)); assertThrows(MarkdownHttpError::class.java) { remote.read("test-folder","test-file") }
    }
    @Test fun generateIdsUsesDriveFileScopeCompatibleEndpoint() = server { server,remote ->
        server.enqueue(MockResponse().setBody("{\"ids\":[\"reserved-id\"]}")); assertEquals("reserved-id",remote.generateId())
        assertTrue(server.takeRequest().path!!.startsWith("/drive/v3/files/generateIds?"))
    }
    @Test fun readonlyOrNonFolderTargetCannotBeUsed() = server { server,remote ->
        server.enqueue(MockResponse().setBody("{\"mimeType\":\"text/markdown\"}"))
        assertThrows(MarkdownConflict::class.java) { remote.folder("test-folder") }
        server.enqueue(MockResponse().setBody("{\"mimeType\":\"application/vnd.google-apps.folder\",\"capabilities\":{\"canAddChildren\":false}}"))
        assertThrows(MarkdownConflict::class.java) { remote.folder("test-folder") }
    }
}
