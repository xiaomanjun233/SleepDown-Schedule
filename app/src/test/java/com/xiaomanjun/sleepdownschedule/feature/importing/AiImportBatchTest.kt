package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class AiImportBatchTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun text(name: String, value: String) = AiImportFile(Uri.EMPTY, name, "text/plain", value.toByteArray())

    @Test fun everyFileRemainsReadablePastItsInitialPreviewAndIsAddressedByIndex(): Unit = runBlocking {
        val first = "周一课程".repeat(9_000) + "第一份末尾"
        val files = listOf(text("一.txt", first), text("二.txt", "周二课程：独立第二份"))
        val preview = prepareAiImportBatch(context, files)
        assertFalse(preview.text.contains("第一份末尾"))
        assertTrue(preview.text.contains("二.txt"))
        AiMaterialWorkspace.fromFiles(context, files).use { workspace ->
            fun read(file: Int, offset: Int = 0) = workspace.execute(ReadImportMaterialTool, buildJsonObject {
                put("file", file); put("page", 0); put("offset", offset)
            }).text
            assertTrue(read(0).contains("file=2"))
            assertTrue(read(1, first.length - 10).contains("第一份末尾"))
            assertTrue(read(2).contains("独立第二份"))
            assertThrows(IllegalArgumentException::class.java) { read(3) }
        }
    }

    @Test fun imageGroupRetainsOrderAndTextCatalogForBothProviderInputs() = runBlocking {
        val bytes = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray()
        }
        val preview = prepareAiImportBatch(context, listOf(AiImportFile(Uri.EMPTY, "前.png", "image/png", bytes),
            text("说明.txt", "本学期共18周"), AiImportFile(Uri.EMPTY, "后.png", "image/png", bytes)))
        assertEquals(2, preview.images.size)
        val input = preview.input() as AiScheduleInput.Images
        assertTrue(input.text.indexOf("前.png") < input.text.indexOf("说明.txt"))
        assertTrue(input.text.indexOf("说明.txt") < input.text.indexOf("后.png"))
        assertTrue(input.text.contains("本学期共18周"))
        AiMaterialWorkspace.fromFiles(context, listOf(text("单.txt", "保留旧单文件编号"))).use {
            assertTrue(it.execute(ReadImportMaterialTool, buildJsonObject { put("file", 1) }).text.contains("保留旧单文件编号"))
        }
    }

    @Test fun viewingTheSecondFileUsesItsOwnPageAndOriginalRegion() {
        var rendered = false
        val child = AiMaterialWorkspace("second.pdf", "", 3, view = { page, region ->
            assertEquals(2, page)
            assertEquals(MaterialRegion(200, 300, 700, 900), region)
            rendered = true
            RenderedPageImage(page, "image/jpeg", "AA==")
        })
        AiMaterialWorkspace("目录", "两个文件", 0, files = listOf(AiMaterialWorkspace("一", "", 0), child)).use {
            assertTrue(it.canView)
            val reply = it.execute(ViewImportDetailTool, buildJsonObject {
                put("file", 2); put("page", 3); put("left", 200); put("top", 300); put("right", 700); put("bottom", 900)
            })
            assertTrue(rendered)
            assertEquals("AA==", reply.image?.base64)
        }
    }
}
