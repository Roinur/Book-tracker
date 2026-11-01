package com.roinur.booktracker.data.booklist

import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormatSymbols
import java.util.Locale

internal object BookListSheetFormulas {
    fun separator(locale: String): String = if (DecimalFormatSymbols.getInstance(Locale.forLanguageTag(locale.replace('_','-'))).decimalSeparator == ',') ";" else ","
    fun formulas(locale: String): Map<String, String> {
        val s = separator(locale)
        // Carry ownership and status with each sorted record, including duplicate titles.
        val ownership = "IF(ISBLANK(D2:D10001)${s}\"\"${s}D2:D10001)"
        val weight = "IF(ISNUMBER(F2:F10001)${s}F2:F10001${s}0)"
        val columns = "HSTACK(A2:A10001${s}B2:B10001${s}C2:C10001${s}$weight${s}$ownership${s}E2:E10001)"
        val length = "IF(${ '$' }Y${ '$' }1=\"Smaller\"${s}C2:C10001<${ '$' }X${ '$' }1${s}C2:C10001>${ '$' }X${ '$' }1)"
        fun sorted(filter: String) = "=IFNA(SORT($filter${s}4${s}FALSE${s}1${s}TRUE${s}2${s}TRUE)${s}\"\")"
        return linkedMapOf(
            "P2" to sorted("FILTER($columns${s}B2:B10001<>\"\")"),
            "W3" to sorted("FILTER($columns${s}B2:B10001<>\"\"${s}E2:E10001=\"Unread\"${s}C2:C10001>0${s}$length)"),
            "AE1" to "=SUMIF(E2:E10001${s}\"Completed\"${s}G2:G10001)",
            "AE3" to "=IFNA(SORT(FILTER(HSTACK(B2:B10001${s}F2:F10001${s}J2:J10001)${s}E2:E10001=\"Completed\")${s}3${s}TRUE${s}1${s}TRUE)${s}\"\")",
            "AI2" to "=COUNTA(B2:B10001)",
            "AI3" to "=COUNTIF(E2:E10001${s}\"Completed\")",
            "AI4" to "=SUM(G2:G10001)", "AI5" to "=SUM(H2:H10001)"
        )
    }
}

/** Personal version of the reference's reading status and weight rules. */
internal object BookListSheetLayout {
    const val VERSION = 7
    private fun color(hex: String) = JSONObject().put("red",hex.substring(0,2).toInt(16)/255.0)
        .put("green",hex.substring(2,4).toInt(16)/255.0).put("blue",hex.substring(4,6).toInt(16)/255.0)
    private fun range(sid: Int, start: Int, end: Int, startRow: Int = 1, endRow: Int = 10001) = JSONObject()
        .put("sheetId",sid).put("startColumnIndex",start).put("endColumnIndex",end).put("startRowIndex",startRow).put("endRowIndex",endRow)

    fun requests(sid: Int, oldRules: Int, rowCount: Int, columnCount: Int, locale: String, installCheckboxes: Boolean = true): JSONArray {
        val requests=JSONArray()
        fun repeat(start: Int, end: Int, format: JSONObject, fields: String, startRow: Int = 1, endRow: Int = 10001) {
            requests.put(JSONObject().put("repeatCell",JSONObject().put("range",range(sid,start,end,startRow,endRow))
                .put("cell",JSONObject().put("userEnteredFormat",format)).put("fields",fields)))
        }
        fun dimensions(dimension: String, start: Int, end: Int, properties: JSONObject, fields: String) {
            requests.put(JSONObject().put("updateDimensionProperties",JSONObject().put("range",JSONObject().put("sheetId",sid)
                .put("dimension",dimension).put("startIndex",start).put("endIndex",end)).put("properties",properties).put("fields",fields)))
        }
        requests.put(JSONObject().put("updateSheetProperties",JSONObject().put("properties",JSONObject().put("sheetId",sid)
            .put("gridProperties",JSONObject().put("rowCount",maxOf(10001,rowCount)).put("columnCount",maxOf(35,columnCount))
                .put("frozenRowCount",1).put("frozenColumnCount",2).put("hideGridlines",false))).put("fields","gridProperties")))
        for(i in oldRules-1 downTo 0) requests.put(JSONObject().put("deleteConditionalFormatRule",JSONObject().put("sheetId",sid).put("index",i)))
        repeat(0,35,JSONObject().put("textFormat",JSONObject().put("fontFamily","Arial").put("fontSize",10).put("foregroundColor",color("000000")))
            .put("horizontalAlignment","CENTER").put("verticalAlignment","MIDDLE").put("wrapStrategy","CLIP"),"userEnteredFormat",0)
        for((start,end) in listOf(0 to 13,15 to 19,22 to 26,33 to 35)) repeat(start,end,JSONObject().put("backgroundColor",color("B7E1CD")),"userEnteredFormat.backgroundColor",0,1)
        repeat(0,1,JSONObject().put("backgroundColor",color("A2C4C9")),"userEnteredFormat.backgroundColor",0,1)
        repeat(15,19,JSONObject().put("backgroundColor",color("B6D7A8")),"userEnteredFormat.backgroundColor",0,1)
        repeat(22,26,JSONObject().put("backgroundColor",color("B6D7A8")),"userEnteredFormat.backgroundColor",1,2)
        repeat(8,9,JSONObject().put("horizontalAlignment","LEFT"),"userEnteredFormat.horizontalAlignment")
        for(col in listOf(9,10,13)) repeat(col,col+1,JSONObject().put("numberFormat",JSONObject().put("type","TEXT")),"userEnteredFormat.numberFormat",0)
        repeat(7,8,JSONObject().put("numberFormat",JSONObject().put("type","NUMBER").put("pattern","0.00")),"userEnteredFormat.numberFormat")
        repeat(34,35,JSONObject().put("numberFormat",JSONObject().put("type","NUMBER").put("pattern","0")),"userEnteredFormat.numberFormat",1,4)
        repeat(34,35,JSONObject().put("numberFormat",JSONObject().put("type","NUMBER").put("pattern","0.00")),"userEnteredFormat.numberFormat",4,5)
        for(col in listOf(5,18,25)) repeat(col,col+1,JSONObject().put("numberFormat",JSONObject().put("type","NUMBER").put("pattern","0.0;-0.0;;")),"userEnteredFormat.numberFormat")
        dimensions("ROWS",0,1,JSONObject().put("pixelSize",21),"pixelSize")
        dimensions("COLUMNS",14,35,JSONObject().put("hiddenByUser",false),"hiddenByUser")
        // Keep the old ownership field only as hidden sync-schema compatibility.
        // Every book in this personal library is owned; ownership is no longer a visible column.
        for((start,end) in listOf(3 to 4,11 to 12,13 to 14,19 to 21,26 to 28,31 to 33)) dimensions("COLUMNS",start,end,JSONObject().put("hiddenByUser",true),"hiddenByUser")
        for((col,width) in listOf(0 to 200,1 to 310,2 to 90,3 to 105,4 to 130,5 to 100,14 to 24,15 to 200,16 to 310,17 to 90,18 to 100,21 to 24,22 to 200,23 to 310,24 to 90,25 to 100,28 to 24,29 to 150,30 to 310,33 to 150,34 to 110)) {
            dimensions("COLUMNS",col,col+1,JSONObject().put("pixelSize",width),"pixelSize")
        }
        fun dropdown(start: Int, end: Int, startRow: Int, endRow: Int, values: List<String>) {
            requests.put(JSONObject().put("setDataValidation",JSONObject().put("range",range(sid,start,end,startRow,endRow)).put("rule",JSONObject()
                .put("condition",JSONObject().put("type","ONE_OF_LIST").put("values",JSONArray(values.map { JSONObject().put("userEnteredValue",it) })))
                .put("strict",true).put("showCustomUi",true))))
        }
        dropdown(24,25,0,1,listOf("Smaller","Larger"))
        fun rule(start: Int, end: Int, formula: String, background: String, foreground: String = "000000", startRow: Int = 1, bold: Boolean = false) {
            requests.put(JSONObject().put("addConditionalFormatRule",JSONObject().put("index",0).put("rule",JSONObject()
                .put("ranges",JSONArray().put(range(sid,start,end,startRow))).put("booleanRule",JSONObject()
                    .put("condition",JSONObject().put("type","CUSTOM_FORMULA").put("values",JSONArray().put(JSONObject().put("userEnteredValue",formula))))
                    .put("format",JSONObject().put("backgroundColor",color(background)).put("textFormat",JSONObject().put("foregroundColor",color(foreground)).apply { if(bold) put("bold",true) }))))))
        }
        rule(6,13,"=${ '$' }B2<>\"\"","B7E1CD")
        rule(0,1,"=${ '$' }B2<>\"\"","A2C4C9")
        rule(2,3,"=${ '$' }B2<>\"\"","9FC5E8")
        data class View(val author: Int, val title: Int, val pages: Int, val status: String, val titleRef: String, val row: Int)
        for(v in listOf(View(0,1,2,"E","B",2),View(15,16,17,"U","Q",2),View(22,23,24,"AB","X",3))) {
            val title="${ '$' }${v.titleRef}${v.row}"
            val present="$title<>\"\""
            rule(v.title,v.title+1,"=$present","B7E1CD",startRow=v.row-1)
            if(v.row==2 && v.title==1) {
                rule(3,4,"=$present","B7E1CD","2D6C4E")
            } else {
                rule(v.author,v.author+1,"=$present","A2C4C9",startRow=v.row-1)
                rule(v.pages,v.pages+1,"=$present","9FC5E8",startRow=v.row-1)
                rule(v.author,v.author+1,"=${ '$' }${v.status}${v.row}=\"Completed\"","46BDC6","000000",v.row-1,true)
            }
        }
        for((status,hex,text) in listOf(Triple("Unread","A75E4F","000000"),Triple("Reading","FFE5A0","000000"),Triple("On hold","D9D2E9","000000"),Triple("Completed","11734B","FFFFFF"))) {
            rule(4,5,"=${ '$' }E2=\"$status\"",hex,text)
        }
        // Exact half-step colours reproduce the reference scale without changing
        // when only a few books have weights. Expressions avoid locale decimal ambiguity.
        val referenceWeights=listOf("EA4335","ED583C","F06D43","F3824A","F69751","F9AC58","FCC15F","FFD666","C8CE71","90C57D","57BB8A")
        for((col,letter,row) in listOf(Triple(5,"F",2),Triple(18,"S",2),Triple(25,"Z",3))) {
            for(units in 1..10) {
                val hex=referenceWeights[units]
                rule(col,col+1,"=${ '$' }$letter$row*2=$units",hex,startRow=row-1)
            }
        }
        for((value,hex) in listOf(0 to "E06666",1 to "F6B26B",2 to "FFD966",3 to "B6D7A8")) {
            rule(30,31,"=AND(${ '$' }AE3<>\"\",${ '$' }AF3>$value)",hex,startRow=2)
        }
        rule(30,31,"=AND(${ '$' }AE3<>\"\",${ '$' }AF3>4)","57BB8A",startRow=2)
        rule(30,31,"=AND(${ '$' }AE3<>\"\",${ '$' }AF3=5)","FFD666","0000FF",2,true)
        // Locale-specific separators are needed only inside multi-argument CF expressions.
        val sep=BookListSheetFormulas.separator(locale)
        for(i in 0 until requests.length()) {
            val condition=requests.getJSONObject(i).optJSONObject("addConditionalFormatRule")?.optJSONObject("rule")
                ?.optJSONObject("booleanRule")?.optJSONObject("condition") ?: continue
            val value=condition.getJSONArray("values").getJSONObject(0)
            value.put("userEnteredValue",value.getString("userEnteredValue").replace(",${ '$' }AF",sep+"${ '$' }AF"))
        }
        repeat(15,19,JSONObject().put("textFormat",JSONObject().put("bold",true).put("fontSize",13)),"userEnteredFormat.textFormat",0,1)
        repeat(29,31,JSONObject().put("backgroundColor",color("CCCCCC")).put("textFormat",JSONObject().put("bold",true).put("fontSize",11)),"userEnteredFormat.backgroundColor,userEnteredFormat.textFormat.bold,userEnteredFormat.textFormat.fontSize",0,1)
        repeat(29,30,JSONObject().put("backgroundColor",color("FFD666")).put("textFormat",JSONObject().put("bold",true)),"userEnteredFormat.backgroundColor,userEnteredFormat.textFormat.bold",1,2)
        repeat(30,31,JSONObject().put("backgroundColor",color("0B5394")).put("textFormat",JSONObject().put("bold",true).put("foregroundColor",color("FFFFFF"))),"userEnteredFormat",1,2)
        repeat(30,31,JSONObject().put("numberFormat",JSONObject().put("type","NUMBER").put("pattern","0")),"userEnteredFormat.numberFormat",0,1)
        for((start,end,row) in listOf(Triple(15,19,0),Triple(22,26,1))) {
            val r=range(sid,start,end,row,row+1)
            requests.put(JSONObject().put("unmergeCells",JSONObject().put("range",r)))
            requests.put(JSONObject().put("mergeCells",JSONObject().put("range",r).put("mergeType","MERGE_ALL")))
        }
        // The reference has no explicit borders. Preserve Sheets' native grid even on
        // coloured cells, rather than drawing a second grid over the sheet on every sync.
        val clearBorders=JSONObject().put("range",range(sid,0,35,0))
        for(edge in listOf("top","bottom","left","right","innerHorizontal","innerVertical")) clearBorders.put(edge,JSONObject().put("style","NONE"))
        requests.put(JSONObject().put("updateBorders",clearBorders))
        return requests
    }

    fun entryRequests(sid: Int, books: Int, completed: Int): JSONArray {
        val result=JSONArray()
        for(col in listOf(3,4,5)) result.put(JSONObject().put("setDataValidation",JSONObject().put("range",range(sid,col,col+1))))
        if(books>0) {
            result.put(JSONObject().put("setDataValidation",JSONObject().put("range",range(sid,4,5,1,books+1)).put("rule",JSONObject()
                .put("condition",JSONObject().put("type","ONE_OF_LIST").put("values",JSONArray(listOf("Unread","Reading","On hold","Completed").map { JSONObject().put("userEnteredValue",it) })))
                .put("strict",true).put("showCustomUi",true))))
            result.put(JSONObject().put("setDataValidation",JSONObject().put("range",range(sid,5,6,1,books+1)).put("rule",JSONObject()
                .put("condition",JSONObject().put("type","NUMBER_BETWEEN").put("values",JSONArray().put(JSONObject().put("userEnteredValue","0")).put(JSONObject().put("userEnteredValue","5"))))
                .put("strict",true))))
        }
        result.put(JSONObject().put("setBasicFilter",JSONObject().put("filter",JSONObject().put("range",range(sid,0,14,0,maxOf(1,books+1))))))
        return result
    }

    fun sortRequest(sid: Int): JSONObject = JSONObject().put("sortRange",JSONObject().put("range",range(sid,0,14))
        .put("sortSpecs",JSONArray().put(JSONObject().put("dimensionIndex",0).put("sortOrder","ASCENDING"))
            .put(JSONObject().put("dimensionIndex",1).put("sortOrder","ASCENDING"))))
}
