package com.roinur.booktracker

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal class BookLookupClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(24, TimeUnit.SECONDS)
        .build()

    fun lookupByIsbn(isbn: String): BookSeed? {
        runCatching { lookupOpenLibrary(isbn) }.getOrNull()?.let { return it }
        runCatching { lookupGoogleBooks(isbn) }.getOrNull()?.let { return it }
        return null
    }

    fun lookupCover(isbn: String?, title: String, authors: String): String? {
        isbn?.takeIf { it.isNotBlank() }?.let { safeIsbn ->
            lookupByIsbn(safeIsbn)?.coverUrl?.takeIf { it.isNotBlank() }?.let { return it }
            return "https://covers.openlibrary.org/b/isbn/$safeIsbn-L.jpg?default=false"
        }
        val query = buildString {
            if (title.isNotBlank()) append("intitle:$title")
            if (authors.isNotBlank()) {
                if (isNotBlank()) append(" ")
                append("inauthor:$authors")
            }
        }.trim()
        if (query.isBlank()) return null
        return lookupGoogleBooksQuery(query)?.coverUrl?.takeIf { it.isNotBlank() }
    }

    fun searchGoogleImages(query: String): List<CoverImageResult> {
        val googleBooks = runCatching { googleBooksCoverFallback(query) }.getOrDefault(emptyList())
        val openLibrary = runCatching { openLibraryCoverResults(query) }.getOrDefault(emptyList())
        return (googleBooks + openLibrary)
            .distinctBy { it.imageUrl }
            .take(18)
    }

    private fun googleBooksCoverFallback(query: String): List<CoverImageResult> {
        return lookupGoogleBooksCoverResults(query)
    }

    private fun openLibraryCoverResults(query: String): List<CoverImageResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://openlibrary.org/search.json?q=$encoded&fields=title,cover_i&limit=18")
            .header("User-Agent", "BookTracker-Android/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val docs = JSONObject(response.body?.string().orEmpty()).optJSONArray("docs") ?: return emptyList()
            return (0 until docs.length()).mapNotNull { index ->
                val item = docs.optJSONObject(index) ?: return@mapNotNull null
                val coverId = item.optLong("cover_i", 0L).takeIf { it > 0L } ?: return@mapNotNull null
                CoverImageResult(
                    title = item.optString("title").ifBlank { "Open Library ${index + 1}" },
                    imageUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                )
            }
        }
    }

    private fun lookupOpenLibrary(isbn: String): BookSeed? {
        val request = Request.Builder()
            .url("https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&format=json&jscmd=data")
            .header("User-Agent", "BookTracker-Android/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = JSONObject(response.body?.string().orEmpty())
            val item = root.optJSONObject("ISBN:$isbn") ?: return null
            val authorsJson = item.optJSONArray("authors")
            val authors = if (authorsJson == null) {
                ""
            } else {
                (0 until authorsJson.length()).joinToString(", ") { index ->
                    authorsJson.optJSONObject(index)?.optString("name").orEmpty()
                }
            }
            val cover = item.optJSONObject("cover")
            val coverUrl = cover?.optString("large").orEmpty()
                .ifBlank { cover?.optString("medium").orEmpty() }
                .ifBlank { cover?.optString("small").orEmpty() }
            val sourceUrl = item.optString("url").let { url ->
                when {
                    url.isBlank() -> ""
                    url.startsWith("http") -> url
                    else -> "https://openlibrary.org$url"
                }
            }
            return BookSeed(
                isbn = isbn,
                title = item.optString("title").ifBlank { "ISBN $isbn" },
                authors = authors,
                pageCount = item.optInt("number_of_pages", 0).coerceAtLeast(0),
                coverUrl = coverUrl,
                sourceUrl = sourceUrl,
                collections = ""
            )
        }
    }

    private fun lookupGoogleBooks(isbn: String): BookSeed? {
        val encoded = URLEncoder.encode("isbn:$isbn", "UTF-8")
        return lookupGoogleBooksUrl(encoded, fallbackTitle = "ISBN $isbn", isbn = isbn)
    }

    private fun lookupGoogleBooksQuery(query: String): BookSeed? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return lookupGoogleBooksUrl(encoded, fallbackTitle = query, isbn = "")
    }

    private fun lookupGoogleBooksCoverResults(query: String): List<CoverImageResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=10")
            .header("User-Agent", "BookTracker-Android/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val root = JSONObject(response.body?.string().orEmpty())
            val items = root.optJSONArray("items") ?: return emptyList()
            return (0 until items.length()).mapNotNull { index ->
                val volume = items.optJSONObject(index)?.optJSONObject("volumeInfo") ?: return@mapNotNull null
                val links = volume.optJSONObject("imageLinks") ?: return@mapNotNull null
                val cover = links.optString("extraLarge").ifBlank { links.optString("large") }
                    .ifBlank { links.optString("medium") }
                    .ifBlank { links.optString("thumbnail") }
                    .ifBlank { links.optString("smallThumbnail") }
                    .replace("http://", "https://")
                cover.takeIf { it.isNotBlank() }?.let {
                    CoverImageResult(volume.optString("title").ifBlank { "Google Books ${index + 1}" }, it)
                }
            }.distinctBy { it.imageUrl }
        }
    }

    private fun lookupGoogleBooksUrl(encodedQuery: String, fallbackTitle: String, isbn: String): BookSeed? {
        val request = Request.Builder()
            .url("https://www.googleapis.com/books/v1/volumes?q=$encodedQuery")
            .header("User-Agent", "BookTracker-Android/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val root = JSONObject(response.body?.string().orEmpty())
            val items = root.optJSONArray("items") ?: return null
            val item = items.optJSONObject(0) ?: return null
            val volume = item.optJSONObject("volumeInfo") ?: return null
            val authorsJson = volume.optJSONArray("authors")
            val authors = if (authorsJson == null) {
                ""
            } else {
                (0 until authorsJson.length()).joinToString(", ") { index -> authorsJson.optString(index) }
            }
            val imageLinks = volume.optJSONObject("imageLinks")
            val coverUrl = imageLinks?.optString("thumbnail").orEmpty()
                .ifBlank { imageLinks?.optString("smallThumbnail").orEmpty() }
                .replace("http://", "https://")
            return BookSeed(
                isbn = isbn,
                title = volume.optString("title").ifBlank { fallbackTitle },
                authors = authors,
                pageCount = volume.optInt("pageCount", 0).coerceAtLeast(0),
                coverUrl = coverUrl,
                sourceUrl = volume.optString("infoLink").orEmpty(),
                collections = ""
            )
        }
    }
}
