package com.xiaomanjun.sleepdownschedule.feature.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AiImportDocumentTextTest {
    @Test
    fun csvIsDecodedLocallyIncludingGb18030Files() {
        val bytes = "星期,节次,课程\n周一,1-2,高等数学".toByteArray(Charset.forName("GB18030"))

        val result = extractLocalAiDocumentText("课表.csv", "application/octet-stream", bytes)

        assertEquals("文本", result?.formatLabel)
        assertTrue(result?.text.orEmpty().contains("周一,1-2,高等数学"))
    }

    @Test
    fun xlsxSharedStringsAndCellColumnsBecomeReadableRows() {
        val bytes = zip(
            "xl/workbook.xml" to """
                <workbook><sheets><sheet name="秋季课表" sheetId="1"/></sheets></workbook>
            """.trimIndent(),
            "xl/sharedStrings.xml" to """
                <sst><si><t>星期</t></si><si><t>节次</t></si><si><t>课程</t></si><si><t>周一</t></si><si><t>高等数学</t></si></sst>
            """.trimIndent(),
            "xl/worksheets/sheet1.xml" to """
                <worksheet><sheetData>
                  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
                  <row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2"><v>1</v></c><c r="C2" t="s"><v>4</v></c></row>
                </sheetData></worksheet>
            """.trimIndent()
        )

        val result = extractLocalAiDocumentText(
            "schedule.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes
        )

        assertEquals("XLSX 工作簿", result?.formatLabel)
        assertTrue(result?.text.orEmpty().contains("【秋季课表】"))
        assertTrue(result?.text.orEmpty().contains("星期\t节次\t课程"))
        assertTrue(result?.text.orEmpty().contains("周一\t1\t高等数学"))
    }

    @Test
    fun docxTableTextIsExtractedWithoutUploadingTheBinaryDocument() {
        val bytes = zip(
            "word/document.xml" to """
                <w:document xmlns:w="urn:w"><w:body><w:tbl><w:tr>
                  <w:tc><w:p><w:r><w:t>星期</w:t></w:r></w:p></w:tc>
                  <w:tc><w:p><w:r><w:t>课程</w:t></w:r></w:p></w:tc>
                </w:tr><w:tr>
                  <w:tc><w:p><w:r><w:t>周二</w:t></w:r></w:p></w:tc>
                  <w:tc><w:p><w:r><w:t>大学英语</w:t></w:r></w:p></w:tc>
                </w:tr></w:tbl></w:body></w:document>
            """.trimIndent()
        )

        val result = extractLocalAiDocumentText("schedule.docx", "application/octet-stream", bytes)

        assertEquals("DOCX 文档", result?.formatLabel)
        assertTrue(result?.text.orEmpty().contains("星期"))
        assertTrue(result?.text.orEmpty().contains("大学英语"))
    }

    @Test
    fun mimeTypeIsRecoveredFromExtensionWhenDocumentProviderReportsOctetStream() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            normalizeAiImportMimeType("schedule.xlsx", "application/octet-stream")
        )
        assertEquals(
            AiImportDocumentKind.PDF,
            classifyAiImportDocument("SCHEDULE.PDF", "application/octet-stream")
        )
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
