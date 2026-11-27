package com.trucca.alarm.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.trucca.alarm.model.DaySchedule
import com.trucca.alarm.model.DefaultScheduleData
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipInputStream

data class ParseResult(
    val schedules: List<DaySchedule>,
    val summary: String,
    val kipMembers: Map<String, String> = emptyMap()
)

object OfficeScheduleParser {

    fun parseDocumentUri(
        context: Context,
        uri: Uri,
        fileName: String,
        targetYear: Int,
        targetMonth: Int
    ): ParseResult {
        val lower = fileName.lowercase()
        val input = context.contentResolver.openInputStream(uri)
            ?: return ParseResult(emptyList(), "Không đọc được nội dung tệp.")

        return input.use { stream ->
            when {
                lower.endsWith(".xlsx") -> parseXlsx(stream, targetYear, targetMonth)
                lower.endsWith(".docx") -> parseDocx(stream, targetYear, targetMonth)
                lower.endsWith(".csv") || lower.endsWith(".txt") -> parseCsv(stream, targetYear, targetMonth)
                else -> {
                    val bytes = stream.readBytes()
                    if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                        val xlsxTry = parseXlsx(ByteArrayInputStream(bytes), targetYear, targetMonth)
                        if (xlsxTry.schedules.isNotEmpty() || xlsxTry.kipMembers.isNotEmpty()) xlsxTry
                        else parseDocx(ByteArrayInputStream(bytes), targetYear, targetMonth)
                    } else {
                        parseCsv(ByteArrayInputStream(bytes), targetYear, targetMonth)
                    }
                }
            }
        }
    }

    fun parseRowsMatrix(
        rows: List<List<String>>,
        targetYear: Int,
        targetMonth: Int
    ): ParseResult {
        if (rows.isEmpty()) return ParseResult(emptyList(), "Không tìm thấy bảng dữ liệu.")

        val extractedMembers = extractKipMembersFromRows(rows)

        var dayRow: List<String>? = null
        var morningRow: List<String>? = null
        var afternoonRow: List<String>? = null
        var nightRow: List<String>? = null

        for (row in rows) {
            val cleaned = row.map { it.trim() }
            if (cleaned.all { it.isEmpty() }) continue
            val first = cleaned.firstOrNull()?.lowercase().orEmpty()

            when {
                first.contains("ngày") || first.contains("ngay") -> dayRow = cleaned
                first.contains("sáng") || first.contains("sang") -> morningRow = cleaned
                first.contains("chiều") || first.contains("chieu") -> afternoonRow = cleaned
                first.contains("đêm") || first.contains("dem") -> nightRow = cleaned
            }
        }

        if (morningRow == null || afternoonRow == null || nightRow == null) {
            val msg = if (extractedMembers.isNotEmpty()) {
                "Đã đọc được nhân sự ${extractedMembers.size} kíp nhưng chưa tìm thấy đủ 3 hàng 'Sáng', 'Chiều', 'Đêm'."
            } else {
                "Chưa tìm thấy đủ 3 hàng 'Sáng', 'Chiều', 'Đêm' trong tệp."
            }
            return ParseResult(emptyList(), msg, extractedMembers)
        }

        val ym = YearMonth.of(targetYear, targetMonth)
        val maxDay = ym.lengthOfMonth()
        val result = mutableListOf<DaySchedule>()

        val colCount = maxOf(dayRow?.size ?: 0, morningRow.size, afternoonRow.size, nightRow.size)

        var sequentialDay = 1
        for (col in 1 until colCount) {
            val dayNum = dayRow?.getOrNull(col)?.filter { it.isDigit() }?.toIntOrNull() ?: sequentialDay
            if (dayNum in 1..maxDay) {
                val mKip = normalizeKip(morningRow.getOrNull(col))
                val aKip = normalizeKip(afternoonRow.getOrNull(col))
                val nKip = normalizeKip(nightRow.getOrNull(col))
                if (mKip.isNotEmpty() || aKip.isNotEmpty() || nKip.isNotEmpty()) {
                    val mClean = mKip.ifEmpty { "-" }
                    val aClean = aKip.ifEmpty { "-" }
                    val nClean = nKip.ifEmpty { "-" }
                    result.add(
                        DaySchedule(
                            date = LocalDate.of(targetYear, targetMonth, dayNum).toString(),
                            morningKip = mClean,
                            afternoonKip = aClean,
                            nightKip = nClean,
                            morningMembers = extractedMembers[mClean].orEmpty(),
                            afternoonMembers = extractedMembers[aClean].orEmpty(),
                            nightMembers = extractedMembers[nClean].orEmpty()
                        )
                    )
                }
            }
            sequentialDay++
        }

        val crewSuffix = if (extractedMembers.isNotEmpty()) {
            " Kèm nhân sự ${extractedMembers.keys.sorted().joinToString(", ") { "Kíp $it" }}."
        } else {
            ""
        }

        return ParseResult(
            schedules = result,
            summary = "Đã đọc được ${result.size} ngày trực cho tháng %02d/%04d.$crewSuffix".format(targetMonth, targetYear),
            kipMembers = extractedMembers
        )
    }

    fun parseOcrText(rawText: String, targetYear: Int, targetMonth: Int): ParseResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rows = mutableListOf<List<String>>()
        val kipLineCandidates = mutableListOf<List<String>>()
        val ocrMembers = extractKipMembersFromLines(lines)

        for (line in lines) {
            val lower = line.lowercase()
            val tokens = Regex("\\b[A-Ea-e]\\b").findAll(line).map { it.value.uppercase() }.toList()
            val label = when {
                lower.startsWith("sáng") || lower.startsWith("sang") -> "Sáng"
                lower.startsWith("chiều") || lower.startsWith("chieu") -> "Chiều"
                lower.startsWith("đêm") || lower.startsWith("dem") -> "Đêm"
                else -> null
            }
            if (label != null && tokens.isNotEmpty()) {
                rows.add(listOf(label) + tokens)
            } else if (tokens.size >= 10) {
                kipLineCandidates.add(tokens)
            }
        }

        val baseResult = if (rows.size < 3 && kipLineCandidates.size >= 3) {
            val fallbackRows = listOf(
                listOf("Sáng") + kipLineCandidates[0],
                listOf("Chiều") + kipLineCandidates[1],
                listOf("Đêm") + kipLineCandidates[2]
            )
            parseRowsMatrix(fallbackRows, targetYear, targetMonth)
        } else {
            parseRowsMatrix(rows, targetYear, targetMonth)
        }

        val mergedMembers = if (ocrMembers.isNotEmpty()) ocrMembers else baseResult.kipMembers
        val enrichedSchedules = if (mergedMembers.isNotEmpty()) {
            baseResult.schedules.map { s ->
                s.copy(
                    morningMembers = s.morningMembers.ifBlank { mergedMembers[s.morningKip].orEmpty() },
                    afternoonMembers = s.afternoonMembers.ifBlank { mergedMembers[s.afternoonKip].orEmpty() },
                    nightMembers = s.nightMembers.ifBlank { mergedMembers[s.nightKip].orEmpty() }
                )
            }
        } else {
            baseResult.schedules
        }

        val suffix = if (mergedMembers.isNotEmpty() && !baseResult.summary.contains("Kèm nhân sự")) {
            " Kèm nhân sự ${mergedMembers.keys.sorted().joinToString(", ") { "Kíp $it" }}."
        } else {
            ""
        }

        return baseResult.copy(
            schedules = enrichedSchedules,
            summary = baseResult.summary + suffix,
            kipMembers = mergedMembers
        )
    }

    private fun extractKipMembersFromRows(rows: List<List<String>>): Map<String, String> {
        var kipColMap: Map<Int, String> = emptyMap()
        val priorityCrew = linkedMapOf<String, MutableList<String>>()
        val fallbackCrew = linkedMapOf<String, MutableList<String>>()

        for (row in rows) {
            val cleaned = row.map { it.trim() }
            if (cleaned.all { it.isEmpty() }) continue

            val detectedCols = mutableMapOf<Int, String>()
            cleaned.forEachIndexed { idx, cell ->
                val upper = cell.uppercase().replace("KÍP", "").replace("KIP", "").trim()
                if (upper in DefaultScheduleData.ALL_KIPS) {
                    detectedCols[idx] = upper
                }
            }
            if (detectedCols.size >= 2) {
                kipColMap = detectedCols
                continue
            }

            if (kipColMap.isEmpty()) continue
            val rowText = cleaned.joinToString(" ").lowercase()
            val isPriorityRole = rowText.contains("tc tđđk") ||
                rowText.contains("trực chính tự động điều khiển") ||
                rowText.contains("tđđk")

            kipColMap.forEach { (colIdx, kip) ->
                val rawCell = cleaned.getOrNull(colIdx)?.trim().orEmpty()
                if (rawCell.isNotBlank() && rawCell != "-") {
                    val splitNames = rawCell.split("\n", ";", ",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    if (splitNames.isNotEmpty()) {
                        val targetBucket = if (isPriorityRole) priorityCrew else fallbackCrew
                        val list = targetBucket.getOrPut(kip) { mutableListOf() }
                        splitNames.forEach { name ->
                            if (name !in list) list.add(name)
                        }
                    }
                }
            }
        }

        val result = linkedMapOf<String, String>()
        DefaultScheduleData.ALL_KIPS.forEach { kip ->
            val chosen = priorityCrew[kip]?.takeIf { it.isNotEmpty() }
                ?: fallbackCrew[kip]?.takeIf { it.isNotEmpty() }
            if (chosen != null) {
                result[kip] = chosen.joinToString(", ")
            }
        }
        return result
    }

    private fun extractKipMembersFromLines(lines: List<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var currentRoleIsPriority = false
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("tc tđđk") || lower.contains("trực chính tự động điều khiển")) {
                currentRoleIsPriority = true
            } else if (lower.startsWith("phụ lục 2") || lower.startsWith("ngày") || lower.startsWith("sáng")) {
                currentRoleIsPriority = false
            }

            val match = Regex("(?i)^k[íi]p\\s*([A-E])\\s*[:\\-–]\\s*(.+)$").find(line)
            if (match != null) {
                val kip = match.groupValues[1].uppercase()
                val names = match.groupValues[2].trim()
                if (names.isNotBlank() && (currentRoleIsPriority || kip !in result)) {
                    result[kip] = names
                }
            }
        }
        return result
    }

    private fun normalizeKip(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim().uppercase().replace("KÍP", "").replace("KIP", "").trim()
    }

    private fun parseCsv(input: InputStream, targetYear: Int, targetMonth: Int): ParseResult {
        val rows = mutableListOf<List<String>>()
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
            reader.lineSequence().forEach { line ->
                val delimiter = if (line.count { it == ';' } > line.count { it == ',' }) ';' else ','
                rows.add(line.split(delimiter).map { it.trim().trim('"') })
            }
        }
        return parseRowsMatrix(rows, targetYear, targetMonth)
    }

    private fun parseDocx(input: InputStream, targetYear: Int, targetMonth: Int): ParseResult {
        var docXmlBytes: ByteArray? = null
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    docXmlBytes = zis.readBytes()
                    break
                }
                entry = zis.nextEntry
            }
        }
        val xmlBytes = docXmlBytes ?: return ParseResult(emptyList(), "Không tìm thấy nội dung bảng trong file Word (.docx).")

        val rows = mutableListOf<List<String>>()
        val paragraphLines = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var currentRow: MutableList<String>? = null
        var currentCellText: StringBuilder? = null
        var currentParagraphText: StringBuilder? = null
        var inTableCell = false
        var inText = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "w:tr" -> currentRow = mutableListOf()
                    "w:tc" -> {
                        inTableCell = true
                        currentCellText = StringBuilder()
                    }
                    "w:p" -> if (!inTableCell) currentParagraphText = StringBuilder()
                    "w:t" -> inText = true
                }
                XmlPullParser.TEXT -> if (inText) {
                    currentCellText?.append(parser.text)
                    if (!inTableCell) currentParagraphText?.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "w:t" -> inText = false
                    "w:tc" -> {
                        currentRow?.add(currentCellText?.toString()?.trim().orEmpty())
                        currentCellText = null
                        inTableCell = false
                    }
                    "w:p" -> {
                        if (!inTableCell) {
                            val pLine = currentParagraphText?.toString()?.trim().orEmpty()
                            if (pLine.isNotEmpty()) paragraphLines.add(pLine)
                            currentParagraphText = null
                        }
                    }
                    "w:tr" -> {
                        currentRow?.let { rows.add(it) }
                        currentRow = null
                    }
                }
            }
            event = parser.next()
        }

        val matrixResult = parseRowsMatrix(rows, targetYear, targetMonth)
        if (matrixResult.schedules.isNotEmpty() || matrixResult.kipMembers.isNotEmpty()) {
            return matrixResult
        }
        return parseOcrText(paragraphLines.joinToString("\n"), targetYear, targetMonth)
    }

    private fun parseXlsx(input: InputStream, targetYear: Int, targetMonth: Int): ParseResult {
        var sharedStringsBytes: ByteArray? = null
        val sheetEntries = mutableListOf<Pair<String, ByteArray>>()

        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/sharedStrings.xml" -> sharedStringsBytes = zis.readBytes()
                    entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") -> {
                        sheetEntries.add(entry.name to zis.readBytes())
                    }
                }
                entry = zis.nextEntry
            }
        }

        if (sheetEntries.isEmpty()) {
            return ParseResult(emptyList(), "Không tìm thấy sheet dữ liệu trong file Excel (.xlsx).")
        }

        val sharedStrings = sharedStringsBytes?.let { parseSharedStrings(it) } ?: emptyList()

        val allRows = mutableListOf<List<String>>()
        for ((_, sheetBytes) in sheetEntries.sortedBy { it.first }) {
            allRows.addAll(parseSheetXml(sheetBytes, sharedStrings))
        }

        val combinedResult = parseRowsMatrix(allRows, targetYear, targetMonth)
        if (combinedResult.schedules.isNotEmpty() || combinedResult.kipMembers.isNotEmpty()) {
            return combinedResult
        }
        return ParseResult(emptyList(), "Không tìm thấy bảng có 3 hàng Sáng/Chiều/Đêm trong file Excel.")
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var current = StringBuilder()
        var inSi = false
        var inT = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; current = StringBuilder() }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inT) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> { inSi = false; list.add(current.toString()) }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseSheetXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var currentRowMap = mutableMapOf<Int, String>()
        var currentColIndex = 0
        var cellType: String? = null
        var valueBuilder = StringBuilder()
        var inValue = false
        var inInlineText = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRowMap = mutableMapOf()
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r") ?: ""
                        currentColIndex = columnRefToIndex(ref)
                        cellType = parser.getAttributeValue(null, "t")
                        valueBuilder = StringBuilder()
                    }
                    "v" -> inValue = true
                    "t" -> inInlineText = true
                }
                XmlPullParser.TEXT -> if (inValue || inInlineText) valueBuilder.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val rawVal = valueBuilder.toString().trim()
                        val resolved = when (cellType) {
                            "s" -> rawVal.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                            else -> rawVal
                        }
                        if (currentColIndex >= 0) currentRowMap[currentColIndex] = resolved
                    }
                    "row" -> if (currentRowMap.isNotEmpty()) {
                        val maxCol = currentRowMap.keys.maxOrNull() ?: 0
                        rows.add(MutableList(maxCol + 1) { idx -> currentRowMap[idx].orEmpty() })
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun columnRefToIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return -1
        var index = 0
        for (ch in letters) index = index * 26 + (ch - 'A' + 1)
        return index - 1
    }
}
