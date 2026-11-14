package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import android.util.Base64
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class AiImportHistoryImagesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val draft = ImportDraft(defaultConfig(), defaultPeriods(), listOf(
        CourseEntity(name = "数学", teacher = null, location = "A101", weekday = 1, periods = listOf(1),
            weeks = listOf(1), weekParity = WeekParity.ALL, note = null)))
    private val images = listOf(RenderedPageImage(1, "image/jpeg", "cGFnZTE="), RenderedPageImage(2, "image/png", "cGFnZTI="))
    @Before fun prepare() { AiImportHistoryStore.clear(context); AiImportHistoryStore.setRetentionDays(context, 30) }
    @After fun cleanup() { AiImportHistoryStore.clear(context) }

    @Test fun largeOriginalImagesSurviveDiskReloadMetadataUpdatesAndDeleteWithTheirHistory() {
        val large = images + RenderedPageImage(3, "image/png", Base64.encodeToString(ByteArray(MaxAiImportContextBytes + 1), Base64.NO_WRAP))
        val progress = AiEduImportProgress(taskId = "persist-images", screenshotPreviews = large, finished = true)
        AiImportHistoryStore.record(context, draft, progress)
        val listed = AiImportHistoryStore.load(context).single()
        assertTrue(listed.context!!.screenshotPreviews.isEmpty())
        val directory = File(context.filesDir, "ai_import_history")
        val json = File(directory, "${listed.id}.json")
        assertTrue(json.length() < MaxAiImportContextBytes)
        assertFalse(json.readText().contains("\"base64\""))
        assertEquals(large, AiImportHistoryStore.loadEntry(context, listed.id)!!.context!!.screenshotPreviews)
        AiImportHistoryStore.update(context, listed.id, draft, listed.context!!.copy(assistantMessage = "更新文字"))
        assertEquals(large, AiImportHistoryStore.loadEntry(context, listed.id)!!.context!!.screenshotPreviews)
        assertNull(AiImportHistoryImages.file(directory, listed.id, JSONObject().put("fileName", "../outside.img")))
        AiImportHistoryStore.delete(context, listed.id)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun backupRestoresSourcePagesInOrderAndRepeatedPreferenceFinalizationKeepsThem() {
        AiImportHistoryStore.record(context, draft, AiEduImportProgress(taskId = "backup-images", screenshotPreviews = images))
        val exported = BackupPreferencesReader.read(context, emptyMap())
        assertEquals(2, exported.additionalAssets.count { it.purpose == BackupAssetPurpose.AI_IMPORT_SCREENSHOT })
        val archive = BackupCodec.decode(BackupCodec.encode(BackupExportMapper.toArchive(
            BackupSourceMetadata("2026-11-01T00:00:00Z", "test", 36, "com.xiaomanjun.sleepdownschedule", APP_DATABASE_VERSION, "Android"),
            BackupRoomSnapshot(), exported.preferences, additionalAssets = exported.additionalAssets)))
        BackupImportPlanBuilder.build(archive, "history-images")
        val stage = BackupAssetStager.stage(context.cacheDir, archive, "history-images")
        AiImportHistoryStore.clear(context)
        val restored = BackupPrivateAssetRestorer.prepare(context, "history-images", archive, stage)
        repeat(2) { AiImportHistoryStore.applyBackup(context, archive.preferences.aiImportHistory,
            restored.filesByAssetId, archive.preferences.aiImportHistoryRetentionDays) }
        // The staged context bytes stay intact, so a replay of asset preparation is idempotent.
        assertTrue(BackupPrivateAssetRestorer.prepare(context, "history-images", archive, stage).newlyCreatedPaths.isEmpty())
        val id = AiImportHistoryStore.load(context).single().id
        assertEquals(images, AiImportHistoryStore.loadEntry(context, id)!!.context!!.screenshotPreviews)
        val listed = AiImportHistoryStore.load(context).single()
        AiImportHistoryStore.update(context, id, draft, listed.context!!)
        assertEquals(images, AiImportHistoryStore.loadEntry(context, id)!!.context!!.screenshotPreviews)
        // Expiring an entry cleans its private image assets too.
        val preferences = context.getSharedPreferences("ai_import_history", 0)
        val rows = org.json.JSONArray(preferences.getString("entries", "[]"))
        rows.getJSONObject(0).put("createdAt", 1L)
        preferences.edit().putString("entries", rows.toString()).commit()
        assertTrue(AiImportHistoryStore.load(context).isEmpty())
        assertTrue(File(context.filesDir, "ai_import_history").listFiles().orEmpty().isEmpty())
    }
}
