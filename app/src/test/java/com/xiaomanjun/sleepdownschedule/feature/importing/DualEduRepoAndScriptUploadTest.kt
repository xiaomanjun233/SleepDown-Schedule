package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.ShiguangApiAdapterCatalog
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.AiDockWarehouseUpdater
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWarehouseUpdater
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class DualEduRepoAndScriptUploadTest {
    private val originalRequester = AiEduScriptUploader.postRequester

    @After
    fun tearDown() {
        AiEduScriptUploader.postRequester = originalRequester
    }

    @Test
    fun mergeDualAdaptersUnifiesSchoolsAndRoundTripsIntentKeySource() {
        val shiguang = EduAdapter(
            school = EduSchool(id = "tsinghua", name = "清华大学", folder = "THU", initial = "Q"),
            adapterId = "thu-ug",
            adapterName = "本科教务",
            category = "BACHELOR_AND_ASSOCIATE",
            assetJsPath = "thu.js",
            importUrl = "https://zhjw.cic.tsinghua.edu.cn/",
            maintainer = "shiguang",
            description = "本科",
            source = EduAdapterSource.SHIGUANG
        )
        val aiDockSameSchool = EduAdapter(
            school = EduSchool(id = "thu-ai", name = "清华大学", folder = "thu-ai", initial = "Q"),
            adapterId = "thu-ai-1",
            adapterName = "AI 适配",
            category = "GENERAL_TOOL",
            assetJsPath = "resources/thu/thu.js",
            importUrl = "https://zhjw.cic.tsinghua.edu.cn/",
            maintainer = "ai",
            description = "AI 自动生成",
            source = EduAdapterSource.AI_DOCK
        )
        val duplicateAiDock = aiDockSameSchool.copy(adapterName = "重复条目")

        val merged = ShiguangWarehouse.mergeDualAdapters(
            primary = listOf(shiguang),
            secondary = listOf(aiDockSameSchool, duplicateAiDock)
        )
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.source == EduAdapterSource.SHIGUANG })
        val aiEntry = merged.first { it.isAiDockAdapter() }
        // Same school name merges to existing canonical Shiguang school object
        assertEquals("tsinghua", aiEntry.school.id)
        val roundTripped = eduAdapterFromIntentKey(aiEntry.toIntentKey())
        assertEquals(aiEntry, roundTripped)
        assertEquals(EduAdapterSource.AI_DOCK, roundTripped?.source)
    }

    @Test
    fun aiDockIndexParserFiltersUnsafePathsAndValidatesSafeOnes() {
        val raw = """
            {
              "protocol_version": 1,
              "version_id": "2026.11.17",
              "schools": [
                {
                  "id": "demo",
                  "name": "示例大学",
                  "adapters": [
                    {
                      "adapter_id": "valid-1",
                      "adapter_name": "正方教务",
                      "category": "BACHELOR_AND_ASSOCIATE",
                      "asset_js_path": "resources/demo/valid.js",
                      "import_url": "https://jw.demo.edu.cn/"
                    },
                    {
                      "adapter_id": "evil-traversal",
                      "asset_js_path": "resources/../secret.js"
                    },
                    {
                      "adapter_id": "evil-prefix",
                      "asset_js_path": "other/evil.js"
                    }
                  ]
                }
              ]
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val snapshot = AiDockWarehouseUpdater.parseIndex(raw)
        assertEquals(1, snapshot.protocolVersion)
        assertEquals("2026.11.17", snapshot.versionId)
        assertEquals(1, snapshot.adapters.size)
        assertEquals("valid-1", snapshot.adapters.single().adapterId)
        assertEquals(EduAdapterSource.AI_DOCK, snapshot.adapters.single().source)
        assertTrue(AiDockWarehouseUpdater.isSafeScriptPath("resources/a/b.js"))
        assertFalse(AiDockWarehouseUpdater.isSafeScriptPath("resources/../b.js"))
        assertFalse(AiDockWarehouseUpdater.isSafeScriptPath("http://evil.com/b.js"))
    }

    @Test
    fun giteeAntiHotlinkAndRepoSwitchingWorkAsExpected() {
        val context = RuntimeEnvironment.getApplication()
        ShiguangWarehouseUpdater.setRepoUrl(context, ShiguangWarehouseUpdater.GITHUB_REPO_URL)
        assertTrue(ShiguangWarehouseUpdater.isGithubSource(context))
        assertEquals("GitHub", ShiguangWarehouseUpdater.sourceLabel(context))

        ShiguangWarehouseUpdater.setRepoUrl(context, ShiguangWarehouseUpdater.GITEE_REPO_URL)
        assertFalse(ShiguangWarehouseUpdater.isGithubSource(context))
        assertEquals("Gitee", ShiguangWarehouseUpdater.sourceLabel(context))

        val antiHotlinkHtml = """<html><a href="https://raw.giteeusercontent.com/foo/bar?token=a&amp;b=1">go</a></html>"""
            .toByteArray(Charsets.UTF_8)
        assertTrue(ShiguangWarehouseUpdater.isGiteeAntiHotlinkPage(antiHotlinkHtml))
        assertEquals(
            "https://raw.giteeusercontent.com/foo/bar?token=a&b=1",
            ShiguangWarehouseUpdater.extractGiteeSignedUrl(antiHotlinkHtml)
        )
    }

    @Test
    fun netLogStoreBoundsEntriesAndTracksLatestTopDocument() {
        val log = EduNetLogStore()
        log.append("""{"id":"doc1","ts":1000,"kind":"document","url":"https://jw.edu/kb","top":true}""")
        for (i in 1..150) {
            log.append("""{"id":"r$i","ts":${1000 + i},"kind":"fetch","method":"GET","url":"https://jw.edu/api/$i","status":200}""")
        }
        assertEquals(120, log.snapshot().size)
        assertEquals(0L, log.latestTopDocumentTs())

        log.append("""{"id":"doc2","ts":2000,"kind":"document","url":"https://jw.edu/kb","top":true}""")
        assertEquals(2000L, log.latestTopDocumentTs())
        assertTrue(log.snapshot("https://another.edu:443").isEmpty())
    }

    @Test
    fun aiEduScriptStorePersistsAndUploaderPostsExpectedPayload(): Unit = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        var capturedUrl: String? = null
        var capturedBody: String? = null
        AiEduScriptUploader.postRequester = { url, body ->
            capturedUrl = url
            capturedBody = body
            201 to """{"id":"123","createdAt":1700000000}"""
        }

        val bad = AiEduScriptUploader.upload(
            context = context,
            school = "   ",
            pageUrl = "https://jw.edu.cn/kb",
            script = "console.log(1)",
            courseCount = 5,
            baseUrl = "https://api.example.com"
        )
        assertTrue(bad.isFailure)

        val adapterId = "SLEEPDOWN_AI_0123456789abcdef0123456789abcdef"
        val ok = AiEduScriptUploader.upload(
            context = context,
            school = "测试大学",
            pageUrl = "https://jw.edu.cn/kb?ticket=secret&term=1",
            script = "window.shiguangBridge.notifyTaskCompletion();",
            courseCount = 12,
            note = "本科课表",
            baseUrl = "https://api.example.com",
            adapterId = adapterId
        )
        assertTrue(ok.isSuccess)
        assertEquals("123", ok.getOrNull())
        assertEquals("https://api.example.com/api/v1/edu-scripts", capturedUrl)

        val payload = Json.parseToJsonElement(checkNotNull(capturedBody)).jsonObject
        assertEquals("测试大学", payload["school"]?.jsonPrimitive?.content)
        assertEquals("https://jw.edu.cn/kb", payload["page_url"]?.jsonPrimitive?.content)
        assertEquals("维护者：SleepDown课程表AI\n适配 ID：$adapterId\n本科课表", payload["note"]?.jsonPrimitive?.content)
        assertEquals(true, payload["ok"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(12, payload["course_count"]?.jsonPrimitive?.intOrNull)
        assertEquals("android", payload["platform"]?.jsonPrimitive?.content)
        assertTrue(payload["device_id"]?.jsonPrimitive?.content.orEmpty().isNotBlank())

        val record = AiEduScriptRecord.create(
            pageUrl = "https://jw.edu.cn/kb",
            note = "note-1",
            script = "code",
            ok = true,
            courseCount = 12,
            error = null
        )
        assertTrue(AiEduScriptStore.append(context, record))
        val loaded = AiEduScriptStore.load(context)
        assertNotNull(loaded.firstOrNull { it.id == record.id })
        java.io.File(context.filesDir, "ai_edu_scripts.json").delete()
        assertNull(AiEduScriptStore.load(context).firstOrNull { it.id == record.id })
    }

    @Test
    fun savedScriptRetriesUploadAndPublishedIdTakesOverEvenIfSchoolAndSourceChange(): Unit = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val proposal = EduAdapterSaveProposal(
            "a973707a-b20b-4fe8-9281-e263427feefd", "fixture", "预填大学",
            "https://jw.example.edu/", "window.shiguangBridge.notifyTaskCompletion();"
        )
        val verified = EduVerifiedScript(proposal, "", "test-hash", courseCount = 1)
        var requests = 0
        AiEduScriptUploader.postRequester = { _, _ ->
            if (++requests == 1) 503 to "{}" else 201 to """{"id":"123"}"""
        }
        AiEduScriptUploader.saveAndUploadInBackground(context, verified, proposal.name)
        val failed = withTimeout(5_000) { verified.submission.state.first { !it.saving && it.savedName != null } }
        assertFalse(failed.uploaded)
        assertEquals(1, requests)
        val local = LocalEduAdapterStore.load(context).single { it.adapterId == proposal.adapterId }
        assertEquals(AiEduScriptMaintainer, local.maintainer)
        val cachedIndex = AiDockWarehouseUpdater.cachedIndexFile(context)
        cachedIndex.writeText("""
            {
              "version_id": "publication",
              "schools": [{
                "id": "canonical-school", "name": "正式大学",
                "adapters": [{
                  "adapter_id": "${proposal.adapterId}",
                  "category": "BACHELOR_AND_ASSOCIATE",
                  "asset_js_path": "resources/canonical-school/reviewed.js"
                }]
              }]
            }
        """.trimIndent())
        try {
            AiEduScriptUploader.saveAndUploadInBackground(context, verified, proposal.name)
            withTimeout(5_000) { verified.submission.state.first { it.uploaded } }
            assertEquals(2, requests)
            val adapters = ShiguangWarehouse.loadVisibleAdapters(context)
            val published = adapters.single { it.adapterId == proposal.adapterId }
            assertTrue(published.isAiDockAdapter())
            assertEquals("正式大学", published.school.name)
            assertEquals("resources/canonical-school/reviewed.js", published.assetJsPath)
            assertEquals(published, ShiguangApiAdapterCatalog.find(adapters, local.school.id, local.adapterId))
            assertEquals(proposal.source, LocalEduAdapterStore.source(context, local))
        } finally {
            cachedIndex.delete()
            LocalEduAdapterStore.delete(context, local)
        }
    }

    @Test
    fun plainTextEduClarificationDetectsUserQuestionsOnly() {
        assertEquals(
            listOf("请先在网页中登录教务系统并进入个人课表页，再继续。"),
            extractPlainTextEduQuestions("请先在网页中登录教务系统并进入个人课表页，再继续。")
        )
        assertTrue(extractPlainTextEduQuestions("""{"courses":[]}""").isEmpty())
        assertTrue(extractPlainTextEduQuestions("正在分析接口返回结构。").isEmpty())
    }
}
