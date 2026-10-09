package com.example.export

import com.example.data.model.SheetState
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Robust XLSX and CSV Exporter fully compliant with OpenXML standard
 * so that Google Sheets, Microsoft Excel, LibreOffice, and mobile viewers
 * open the spreadsheet cleanly with zero infinite loading or corruption errors.
 */
object XlsxExporter {

    /**
     * Exports the SheetState as a fully compliant XLSX (.xlsx) zip archive.
     */
    fun exportToStream(state: SheetState, outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)

        val columns = state.config.columns
        val cells = state.cells
        val maxDataRow = (cells.keys.maxOfOrNull { it.first } ?: 1).coerceAtLeast(1)
        val maxColIdx = (columns.size - 1).coerceAtLeast(0)
        val endColLetter = getColumnLetter(maxColIdx)
        val totalExcelRows = maxDataRow + 1
        val dimensionRef = "A1:$endColLetter$totalExcelRows"

        // 1. [Content_Types].xml
        writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml())

        // 2. _rels/.rels
        writeZipEntry(zip, "_rels/.rels", buildPackageRelsXml())

        // 3. docProps/app.xml
        writeZipEntry(zip, "docProps/app.xml", buildAppXml())

        // 4. docProps/core.xml
        writeZipEntry(zip, "docProps/core.xml", buildCoreXml())

        // 5. xl/workbook.xml
        writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml())

        // 6. xl/_rels/workbook.xml.rels
        writeZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())

        // 7. xl/styles.xml
        writeZipEntry(zip, "xl/styles.xml", buildStylesXml())

        // 8. xl/worksheets/sheet1.xml
        writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildWorksheetXml(state, dimensionRef))

        zip.finish()
        zip.flush()
    }

    /**
     * Exports the SheetState as a universally compatible CSV format (UTF-8 with BOM for Excel/Google Sheets).
     */
    fun exportToCsvStream(state: SheetState, outputStream: OutputStream) {
        val columns = state.config.columns
        val cells = state.cells
        val maxDataRow = (cells.keys.maxOfOrNull { it.first } ?: 0)

        val sb = StringBuilder()
        // Write UTF-8 BOM so Excel opens non-ASCII characters without encoding issues
        outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

        // Header row
        sb.append(columns.joinToString(",") { escapeCsv(it) }).append("\r\n")

        // Data rows
        for (userRow in 1..maxDataRow) {
            val rowValues = columns.indices.map { colIdx ->
                val v = cells[userRow to colIdx] ?: ""
                escapeCsv(v)
            }
            sb.append(rowValues.joinToString(",")).append("\r\n")
        }

        outputStream.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
    }

    private fun escapeCsv(value: String): String {
        var str = value
        val needsQuotes = str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")
        if (str.contains("\"")) {
            str = str.replace("\"", "\"\"")
        }
        return if (needsQuotes) "\"$str\"" else str
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder()
        for (c in input) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> {
                    // Filter out invalid XML control characters
                    if (c.code in 0x20..0xD7FF || c == '\t' || c == '\n' || c == '\r' || c.code in 0xE000..0xFFFD) {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    fun getColumnLetter(colIndex: Int): String {
        var n = colIndex
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun buildContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
    }

    private fun buildPackageRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""
    }

    private fun buildAppXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>BDX Data Collector</Application>
</Properties>"""
    }

    private fun buildCoreXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>BDX Sheet Export</dc:title>
  <dc:creator>BDX App</dc:creator>
</cp:coreProperties>"""
    }

    private fun buildWorkbookXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <bookViews>
    <workbookView xWindow="0" yWindow="0" windowWidth="20480" windowHeight="10240"/>
  </bookViews>
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
    }

    private fun buildWorkbookRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font>
      <sz val="11"/>
      <color rgb="FF000000"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color rgb="FF000000"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
  </fonts>
  <fills count="2">
    <fill>
      <patternFill patternType="none"/>
    </fill>
    <fill>
      <patternFill patternType="gray125"/>
    </fill>
  </fills>
  <borders count="1">
    <border>
      <left/><right/><top/><bottom/><diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <!-- 0: Normal data cell -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <!-- 1: Bold header cell -->
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""
    }

    private fun buildWorksheetXml(state: SheetState, dimensionRef: String): String {
        val columns = state.config.columns
        val cells = state.cells
        val maxRow = (cells.keys.maxOfOrNull { it.first } ?: 1).coerceAtLeast(1)

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <dimension ref="$dimensionRef"/>
  <sheetViews>
    <sheetView tabSelected="1" workbookViewId="0"/>
  </sheetViews>
  <sheetFormatPr defaultRowHeight="15"/>
  <sheetData>
""")

        // Row 1: Column Headers
        sb.append("""    <row r="1" spans="1:${columns.size.coerceAtLeast(1)}">""").append("\n")
        columns.forEachIndexed { colIdx, colName ->
            val colLetter = getColumnLetter(colIdx)
            val cellRef = "${colLetter}1"
            val escaped = escapeXml(colName)
            sb.append("""      <c r="$cellRef" s="1" t="inlineStr"><is><t xml:space="preserve">$escaped</t></is></c>""").append("\n")
        }
        sb.append("""    </row>""").append("\n")

        // Rows 2 .. (maxRow + 1): Data Rows corresponding to User Row 1 .. maxRow
        for (userRow in 1..maxRow) {
            val excelRow = userRow + 1
            sb.append("""    <row r="$excelRow" spans="1:${columns.size.coerceAtLeast(1)}">""").append("\n")
            columns.forEachIndexed { colIdx, _ ->
                val value = cells[userRow to colIdx]
                if (!value.isNullOrEmpty()) {
                    val colLetter = getColumnLetter(colIdx)
                    val cellRef = "$colLetter$excelRow"
                    val escaped = escapeXml(value)
                    sb.append("""      <c r="$cellRef" s="0" t="inlineStr"><is><t xml:space="preserve">$escaped</t></is></c>""").append("\n")
                }
            }
            sb.append("""    </row>""").append("\n")
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }
}
