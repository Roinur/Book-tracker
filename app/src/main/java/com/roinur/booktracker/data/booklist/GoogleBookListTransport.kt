package com.roinur.booktracker.data.booklist

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class BookListCellWrite(val range: String, val rows: List<List<Any>>)
internal interface BookListTransport {
    fun create(instance: String): String
    fun verify(id: String, instance: String)
    fun initialize(id: String, records: Collection<BookListRecord>)
    fun read(id: String): List<List<String>>
    fun write(id: String, changes: List<BookListCellWrite>)
    fun finishSync(id: String) = Unit
    fun prepareSync(id: String) = Unit
}

/** Access is restricted to a newly created, app-marked spreadsheet. No existing-file picker. */
internal class GoogleBookListTransport(private val token: String, private val channel: String) : BookListTransport {
    private var verifiedId = ""
    private var verifiedInstance = ""
    private var layoutVersion = 0
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private fun request(host: String, path: String, query: Map<String, String> = emptyMap(), body: JSONObject? = null, method: String? = null): JSONObject {
        val url = "https://$host/".toHttpUrl().newBuilder().addPathSegments(path).apply { query.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        val call = Request.Builder().url(url).header("Authorization", "Bearer $token")
        call.method(method ?: if (body == null) "GET" else "POST", body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()))
        return client.newCall(call.build()).execute().use { response ->
            val buffer = okio.Buffer()
            response.body?.source()?.use { source ->
                while(source.read(buffer, 8192) >= 0) require(buffer.size <= 8 * 1024 * 1024) { "Book List is too large to sync." }
            }
            val text = buffer.readUtf8()
            if (!response.isSuccessful) {
                val disabled = text.contains("SERVICE_DISABLED") || text.contains("has not been used")
                throw IOException(when {
                    disabled && host.startsWith("sheets") -> "Enable Google Sheets API in your Google Cloud project, then try again. Drive backup is unchanged."
                    response.code == 401 -> "Google authorization expired. Reconnect Google Drive."
                    response.code == 403 -> "Google denied Book List access. Check Sheets API and reconnect Google Drive."
                    response.code == 404 -> "The linked Book List was not found. No library changes saved."
                    response.code == 400 -> "Book List: " + runCatching { JSONObject(text).getJSONObject("error").getString("message").take(240) }.getOrDefault("Google rejected the request.")
                    else -> "Book List request failed (HTTP ${response.code}). Try again later."
                })
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
    override fun create(instance: String): String = request("www.googleapis.com", "drive/v3/files", mapOf("fields" to "id"),
        JSONObject().put("name", "Book Tracker · Book List").put("mimeType", "application/vnd.google-apps.spreadsheet")
            .put("appProperties", JSONObject().put("book_kind", "book_list_v1").put("book_channel", channel).put("book_instance", instance))).getString("id")

    override fun verify(id: String, instance: String) {
        require(id.matches(Regex("[A-Za-z0-9_-]+")))
        val metadata = request("www.googleapis.com", "drive/v3/files/$id", mapOf("fields" to "id,mimeType,trashed,appProperties"))
        val p = metadata.optJSONObject("appProperties")
        require(!metadata.optBoolean("trashed") && metadata.optString("mimeType") == "application/vnd.google-apps.spreadsheet" &&
            p != null && p.optString("book_kind") == "book_list_v1" && p.optString("book_channel") == channel && p.optString("book_instance") == instance) {
            "This file was not created for this Book Tracker connection. Sync cancelled."
        }
        verifiedId = id; verifiedInstance = instance; layoutVersion = p.optInt("book_list_layout", 0)
    }

    override fun read(id: String): List<List<String>> {
        val result = request("sheets.googleapis.com", "v4/spreadsheets/$id/values/'Book List'!A1:N10001",
            mapOf("valueRenderOption" to "UNFORMATTED_VALUE", "dateTimeRenderOption" to "FORMATTED_STRING"))
        val rows = result.optJSONArray("values") ?: JSONArray()
        return (0 until rows.length()).map { row -> rows.getJSONArray(row).let { cells ->
            List(14) { if (it >= cells.length() || cells.isNull(it)) "" else cells.get(it).toString() }
        } }.dropLastWhile(BookListRecord::isEmptyRow)
    }
    override fun prepareSync(id: String) {
        check(verifiedId == id)
        val before=read(id)
        val legacyHeaders=BookListRecord.headers.toMutableList().also { it[5]="Weight / 10" }
        if(before.firstOrNull() != legacyHeaders) return
        val writes=mutableListOf(BookListCellWrite("'Book List'!F1",listOf(listOf("Weight / 5"))))
        before.drop(1).forEachIndexed { index,row ->
            if(!BookListRecord.isEmptyRow(row)) {
                val cells=row.toMutableList()
                if(cells[5].isNotBlank()) cells[5]=(cells[5].toBigDecimal()/2.toBigDecimal()).toPlainString()
                BookListRecord.parse(cells[13],cells)
                writes += BookListCellWrite("'Book List'!F${index+2}",listOf(listOf(cells[5].toDoubleOrNull() ?: "")))
            }
        }
        check(read(id)==before) { "Book List changed during scale conversion. Sync again." }
        write(id,writes)
    }
    override fun write(id: String, changes: List<BookListCellWrite>) {
        if (changes.isEmpty()) return
        require(changes.all { it.range.matches(Regex("'Book List'![A-N]+[0-9]+(:[A-N]+[0-9]+)?")) })
        request("sheets.googleapis.com", "v4/spreadsheets/$id/values:batchUpdate", body = JSONObject()
            .put("valueInputOption", "RAW").put("data", JSONArray().apply { changes.forEach {
                put(JSONObject().put("range", it.range).put("values", JSONArray(it.rows.map(::JSONArray))))
            } }))
    }

    override fun initialize(id: String, records: Collection<BookListRecord>) {
        val info = request("sheets.googleapis.com", "v4/spreadsheets/$id", mapOf("fields" to "sheets.properties"))
        val sheets = info.getJSONArray("sheets")
        require(sheets.length() == 1) { "New Book List must contain exactly one sheet." }
        val sid = sheets.getJSONObject(0).getJSONObject("properties").getInt("sheetId")
        if (sheets.getJSONObject(0).getJSONObject("properties").optString("title") == "Book List") {
            val existing = read(id)
            if (existing.isNotEmpty()) {
                require(existing.first() == BookListRecord.headers) { "Partially created Book List was edited. Setup stopped to preserve it." }
                val stored = existing.drop(1).filterNot(BookListRecord::isEmptyRow).map { BookListRecord.parse(it[13], it) }.associateBy { it.key }
                require(stored == records.associateBy { it.key }) { "Partially created Book List or library changed. Setup stopped to preserve both." }
            }
        }
        fun obj(text: String) = JSONObject(text)
        val requests = JSONArray()
        requests.put(obj("""{"updateSheetProperties":{"properties":{"sheetId":$sid,"title":"Book List","gridProperties":{"rowCount":10001,"columnCount":28,"frozenRowCount":1,"frozenColumnCount":2,"hideGridlines":true}},"fields":"title,gridProperties"}}"""))
        requests.put(obj("""{"repeatCell":{"range":{"sheetId":$sid},"cell":{"userEnteredFormat":{"textFormat":{"fontFamily":"Arial","fontSize":10},"verticalAlignment":"MIDDLE"}},"fields":"userEnteredFormat"}}"""))
        requests.put(obj("""{"repeatCell":{"range":{"sheetId":$sid,"startRowIndex":0,"endRowIndex":1},"cell":{"userEnteredFormat":{"backgroundColor":{"red":0.69,"green":0.83,"blue":0.78},"textFormat":{"bold":true},"wrapStrategy":"WRAP"}},"fields":"userEnteredFormat.backgroundColor,userEnteredFormat.textFormat.bold,userEnteredFormat.wrapStrategy"}}"""))
        requests.put(obj("""{"updateDimensionProperties":{"range":{"sheetId":$sid,"dimension":"ROWS","startIndex":0,"endIndex":1},"properties":{"pixelSize":46},"fields":"pixelSize"}}"""))
        requests.put(obj("""{"setBasicFilter":{"filter":{"range":{"sheetId":$sid,"startRowIndex":0,"endRowIndex":10001,"startColumnIndex":0,"endColumnIndex":14}}}}"""))
        for ((col,width) in listOf(0 to 200,1 to 290,2 to 100,3 to 120,4 to 125,5 to 95,6 to 105,7 to 105,8 to 300,9 to 130,10 to 155,11 to 95,12 to 100,
            14 to 24,15 to 300,16 to 95,17 to 95,18 to 24,19 to 300,20 to 100,21 to 95,22 to 24,23 to 150,24 to 110)) {
            requests.put(obj("""{"updateDimensionProperties":{"range":{"sheetId":$sid,"dimension":"COLUMNS","startIndex":$col,"endIndex":${col+1}},"properties":{"pixelSize":$width},"fields":"pixelSize"}}"""))
        }
        requests.put(obj("""{"updateDimensionProperties":{"range":{"sheetId":$sid,"dimension":"COLUMNS","startIndex":13,"endIndex":14},"properties":{"hiddenByUser":true},"fields":"hiddenByUser"}}"""))
        for (col in listOf(9,10,13)) requests.put(obj("""{"repeatCell":{"range":{"sheetId":$sid,"startColumnIndex":$col,"endColumnIndex":${col+1}},"cell":{"userEnteredFormat":{"numberFormat":{"type":"TEXT"}}},"fields":"userEnteredFormat.numberFormat"}}"""))
        requests.put(obj("""{"repeatCell":{"range":{"sheetId":$sid,"startRowIndex":1,"startColumnIndex":7,"endColumnIndex":8},"cell":{"userEnteredFormat":{"numberFormat":{"type":"NUMBER","pattern":"0.00"}}},"fields":"userEnteredFormat.numberFormat"}}"""))
        requests.put(obj("""{"addProtectedRange":{"protectedRange":{"range":{"sheetId":$sid,"startColumnIndex":13,"endColumnIndex":14},"description":"Book identities used for sync. Do not edit or remove.","warningOnly":true}}}"""))
        for ((col, options) in listOf(3 to listOf("Unknown","Owned","Not owned"),4 to listOf("Unread","Reading","On hold","Completed"))) {
            requests.put(JSONObject().put("setDataValidation", JSONObject().put("range", JSONObject().put("sheetId",sid).put("startRowIndex",1).put("startColumnIndex",col).put("endColumnIndex",col+1))
                .put("rule", JSONObject().put("condition",JSONObject().put("type","ONE_OF_LIST").put("values",JSONArray(options.map { JSONObject().put("userEnteredValue",it) }))).put("strict",true).put("showCustomUi",true))))
        }
        for ((status,color) in listOf("Unread" to "0.95,0.78,0.78","Reading" to "1,0.9,0.62","On hold" to "0.82,0.82,0.88","Completed" to "0.66,0.86,0.72")) {
            val rgb=color.split(',').map(String::toDouble)
            requests.put(JSONObject().put("addConditionalFormatRule", JSONObject().put("index",0).put("rule", JSONObject()
                .put("ranges",JSONArray().put(JSONObject().put("sheetId",sid).put("startRowIndex",1).put("startColumnIndex",4).put("endColumnIndex",5)))
                .put("booleanRule",JSONObject().put("condition",JSONObject().put("type","TEXT_EQ").put("values",JSONArray().put(JSONObject().put("userEnteredValue",status))))
                    .put("format",JSONObject().put("backgroundColor",JSONObject().put("red",rgb[0]).put("green",rgb[1]).put("blue",rgb[2])))))))
        }
        request("sheets.googleapis.com", "v4/spreadsheets/$id:batchUpdate", body = JSONObject().put("requests",requests))
        write(id, listOf(BookListCellWrite("'Book List'!A1:N${records.size+1}", listOf(BookListRecord.headers) + records.map { it.cells() })))
        layoutVersion = 0
        finishSync(id)
    }

    override fun finishSync(id: String) {
        check(verifiedId == id) { "Verify the app-created Book List before updating its layout." }
        val info = request("sheets.googleapis.com", "v4/spreadsheets/$id", mapOf("fields" to "properties,sheets(properties,conditionalFormats)"))
        val sheets = info.getJSONArray("sheets")
        val sheet = (0 until sheets.length()).map(sheets::getJSONObject).single { it.getJSONObject("properties").getString("title") == "Book List" }
        val properties = sheet.getJSONObject("properties")
        val sid = properties.getInt("sheetId")
        if (layoutVersion < BookListSheetLayout.VERSION) {
            val locale = info.getJSONObject("properties").optString("locale", "en_US")
            val before = read(id)
            require(before.firstOrNull() == BookListRecord.headers)
            val conversions = before.drop(1).mapIndexedNotNull { index, row ->
                if (BookListRecord.isEmptyRow(row)) null else {
                    val record = BookListRecord.parse(row[13], row)
                    listOf(BookListCellWrite("'Book List'!D${index+2}", listOf(listOf(record.cells()[3]))),
                        BookListCellWrite("'Book List'!L${index+2}", listOf(listOf(record.cells()[11]))))
                }
            }.flatten()
            val grid = properties.getJSONObject("gridProperties")
            val requests = BookListSheetLayout.requests(sid, sheet.optJSONArray("conditionalFormats")?.length() ?: 0,
                grid.getInt("rowCount"), grid.getInt("columnCount"), locale, installCheckboxes = layoutVersion < 2)
            request("sheets.googleapis.com", "v4/spreadsheets/$id:batchUpdate", body = JSONObject().put("requests", requests))
            check(read(id) == before) { "Book List changed during layout update. Sync again." }
            write(id, conversions)
            val thresholdCell = if(layoutVersion >= 3) "X1" else "U1"
            val threshold = request("sheets.googleapis.com", "v4/spreadsheets/$id/values/'Book List'!$thresholdCell", mapOf("valueRenderOption" to "UNFORMATTED_VALUE"))
                .optJSONArray("values")?.optJSONArray(0)?.opt(0) ?: 300
            val comparison = if(layoutVersion >= 3) request("sheets.googleapis.com", "v4/spreadsheets/$id/values/'Book List'!Y1", mapOf("valueRenderOption" to "UNFORMATTED_VALUE"))
                .optJSONArray("values")?.optJSONArray(0)?.optString(0)?.takeIf { it in listOf("Smaller","Larger") } ?: "Smaller" else "Smaller"
            // Only the generated views are replaced. A:N contains the editable library
            // and its hidden identities, and is never cleared by a layout migration.
            request("sheets.googleapis.com", "v4/spreadsheets/$id/values/'Book List'!P1:AI10001:clear", body = JSONObject())
            val entries = JSONArray()
                .put(JSONObject().put("range", "'Book List'!P1").put("values", JSONArray().put(JSONArray(listOf("Ranked Books")))))
                .put(JSONObject().put("range", "'Book List'!W1:Z2").put("values", JSONArray().put(JSONArray(listOf("Pages", threshold, comparison)))
                    .put(JSONArray(listOf("Unread books")))))
                .put(JSONObject().put("range", "'Book List'!AD1:AE2").put("values", JSONArray().put(JSONArray(listOf("Pages so far")))
                    .put(JSONArray(listOf("Completed books","User")))))
                .put(JSONObject().put("range", "'Book List'!AH1:AI5").put("values", JSONArray().put(JSONArray(listOf("Library", "Total")))
                    .put(JSONArray(listOf("Books"))).put(JSONArray(listOf("Completed"))).put(JSONArray(listOf("Pages read"))).put(JSONArray(listOf("Hours read")))))
            // Headings and formulas are separate from RAW library values, so titles remain literal text.
            for ((cell, formula) in BookListSheetFormulas.formulas(locale)) entries.put(JSONObject().put("range", "'Book List'!$cell")
                .put("values", JSONArray().put(JSONArray(listOf(formula)))))
            request("sheets.googleapis.com", "v4/spreadsheets/$id/values:batchUpdate", body = JSONObject().put("valueInputOption", "USER_ENTERED").put("data", entries))
            val audit = request("sheets.googleapis.com", "v4/spreadsheets/$id", mapOf("includeGridData" to "true", "ranges" to "'Book List'!P1:AI6"))
            val blocks = audit.getJSONArray("sheets").getJSONObject(0).optJSONArray("data")
            if (blocks != null) for (b in 0 until blocks.length()) {
                val rows = blocks.getJSONObject(b).optJSONArray("rowData") ?: continue
                for (r in 0 until rows.length()) {
                    val cells = rows.getJSONObject(r).optJSONArray("values") ?: continue
                    for (c in 0 until cells.length()) check(cells.getJSONObject(c).optJSONObject("effectiveValue")?.has("errorValue") != true) {
                        "Book List summary formula could not be calculated. Sync again."
                    }
                }
            }
            request("www.googleapis.com", "drive/v3/files/$id", body = JSONObject().put("appProperties", JSONObject()
                .put("book_kind", "book_list_v1").put("book_channel", channel).put("book_instance", verifiedInstance)
                .put("book_list_layout", BookListSheetLayout.VERSION.toString())), method = "PATCH")
            layoutVersion = BookListSheetLayout.VERSION
        }
        // Sort entire records including their hidden identity, never individual columns.
        request("sheets.googleapis.com", "v4/spreadsheets/$id:batchUpdate", body = JSONObject()
            .put("requests", JSONArray().put(BookListSheetLayout.sortRequest(sid))))
        val books=read(id).drop(1).filterNot(BookListRecord::isEmptyRow)
        request("sheets.googleapis.com", "v4/spreadsheets/$id:batchUpdate", body=JSONObject().put("requests",
            BookListSheetLayout.entryRequests(sid,books.size,books.count { it[4]=="Completed" })))
    }
}
