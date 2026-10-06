package com.example.export

import com.example.data.model.SheetState
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {

    fun exportToStream(state: SheetState, outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml
        writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml())

        // 2. _rels/.rels
        writeZipEntry(zip, "_rels/.rels", buildPackageRelsXml())

        // 3. xl/workbook.xml
        writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml())

        // 4. xl/_rels/workbook.xml.rels
        writeZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())

        // 5. xl/styles.xml
        writeZipEntry(zip, "xl/styles.xml", buildStylesXml())

        // 6. xl/worksheets/sheet1.xml
        writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildWorksheetXml(state))

        zip.finish()
        zip.flush()
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
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
    }

    private fun buildPackageRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
    }

    private fun buildWorkbookRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
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
      <color theme="1"/>
      <name val="Calibri"/>
      <family val="2"/>
    </font>
    <font>
      <b/>
      <sz val="11"/>
      <color theme="1"/>
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

    private fun buildWorksheetXml(state: SheetState): String {
        val columns = state.config.columns
        val cells = state.cells
        val maxRow = (cells.keys.maxOfOrNull { it.first } ?: 1).coerceAtLeast(1)

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
""")

        // Row 1: Column Headers
        sb.append("""    <row r="1">""").append("\n")
        columns.forEachIndexed { colIdx, colName ->
            val colLetter = getColumnLetter(colIdx)
            val cellRef = "${colLetter}1"
            val escaped = escapeXml(colName)
            sb.append("""      <c r="$cellRef" s="1" t="inlineStr"><is><t>$escaped</t></is></c>""").append("\n")
        }
        sb.append("""    </row>""").append("\n")

        // Rows 2 .. (maxRow + 1): Data Rows corresponding to User Row 1 .. maxRow
        for (userRow in 1..maxRow) {
            val excelRow = userRow + 1
            sb.append("""    <row r="$excelRow">""").append("\n")
            columns.forEachIndexed { colIdx, _ ->
                val value = cells[userRow to colIdx]
                if (value != null && value.isNotEmpty()) {
                    val colLetter = getColumnLetter(colIdx)
                    val cellRef = "$colLetter$excelRow"
                    val escaped = escapeXml(value)
                    sb.append("""      <c r="$cellRef" s="0" t="inlineStr"><is><t>$escaped</t></is></c>""").append("\n")
                }
            }
            sb.append("""    </row>""").append("\n")
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }
}
