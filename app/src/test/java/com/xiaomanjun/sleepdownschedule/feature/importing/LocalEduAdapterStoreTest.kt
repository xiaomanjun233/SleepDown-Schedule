package com.xiaomanjun.sleepdownschedule.feature.importing

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class LocalEduAdapterStoreTest {
    @Test fun interruptedAtomicWriteCanRestoreThePreviousCommittedAdapter() {
        val context = RuntimeEnvironment.getApplication()
        val script = "(async()=>{window.shiguangBridge.notifyTaskCompletion()})()"
        val saved = LocalEduAdapterStore.save(context, "中断恢复", "https://edu.example.edu/", script)
        try {
            val base = java.io.File(context.filesDir, "local-edu-adapters/${saved.adapterId}.zip")
            val backup = java.io.File(base.parentFile, base.name + ".bak")
            assertTrue(base.renameTo(backup))
            assertEquals(saved, LocalEduAdapterStore.load(context).first { it.adapterId == saved.adapterId })
            assertEquals(script, LocalEduAdapterStore.source(context, saved))
        } finally { LocalEduAdapterStore.delete(context, saved) }
    }
    @Test fun legacyJsonRemainsReadableAndAnExplicitUpdateWritesACanonicalPackage() {
        val context = RuntimeEnvironment.getApplication()
        val id = java.util.UUID.randomUUID().toString()
        val script = "// original attribution\n(async()=>{window.shiguangBridge.notifyTaskCompletion()})()"
        val legacy = EduAdapter(EduSchool("LOCAL_$id", "旧自用适配", "local", "#"), id, "自用适配", "BACHELOR_AND_ASSOCIATE", "$id.js",
            "https://edu.example.edu/login", "小漫君", "保存在本机的自用拾光适配", "self-use-v1")
        val file = java.io.File(context.filesDir, "local-edu-adapters/$id.json")
        file.parentFile!!.mkdirs()
        file.writeText(org.json.JSONObject().put("adapter", legacy.toIntentKey()).put("source", script).toString())
        try {
            assertEquals(legacy, LocalEduAdapterStore.load(context).first { it.adapterId == id })
            assertEquals(script, LocalEduAdapterStore.source(context, legacy))
            val revised = LocalEduAdapterStore.save(context, legacy.school.name, legacy.importUrl, script, id)
            assertEquals(revised, LocalEduAdapterStore.load(context).single { it.adapterId == id })
            assertEquals(script, LocalEduAdapterStore.source(context, revised))
            assertTrue(java.io.File(file.parentFile, "$id.zip").isFile)
            assertTrue(file.isFile)
        } finally { LocalEduAdapterStore.delete(context, legacy) }
        assertFalse(file.exists())
    }

    @Test fun savedArchiveContainsOnlyTheCanonicalFilesAndAWorkingProtocolV2Index() {
        val context = RuntimeEnvironment.getApplication()
        val script = "// MIT / original author preserved\n(async()=>{window.shiguangBridge.notifyTaskCompletion()})()"
        val saved = LocalEduAdapterStore.save(context, "规范学校：\"自用\"", "https://edu.example.edu/login?service=https%3A%2F%2Fedu.example.edu%2Fportal&ticket=ephemeral#fragment", script)
        try {
            val archive = java.io.File(context.filesDir, "local-edu-adapters/${saved.adapterId}.zip")
            java.util.zip.ZipFile(archive).use { zip ->
                assertEquals(setOf("index/root_index.yaml", "resources/${saved.school.folder}/adapters.yaml", "resources/${saved.school.folder}/adapter.js", "school_index.pb"),
                    zip.entries().asSequence().map { it.name }.toSet())
                val index = zip.getInputStream(zip.getEntry("school_index.pb")).use { ShiguangWarehouse.parseProtocolV2Snapshot(it.readBytes()) }
                assertEquals(2, index.protocolVersion)
                assertTrue(index.versionId.matches(Regex("TIME_\\d{14}_\\d{3}")))
                assertEquals(saved.copy(warehouseGeneration = index.indexSha), index.adapters.single())
                assertEquals(script, zip.getInputStream(zip.getEntry("resources/${saved.school.folder}/adapter.js")).reader().readText())
            }
            assertEquals("https://edu.example.edu/login?service=https%3A%2F%2Fedu.example.edu%2Fportal", saved.importUrl)
            val fixture = java.io.File("build/test-artifacts/local-adapter-format.zip")
            fixture.parentFile.mkdirs()
            archive.copyTo(fixture, overwrite = true)
        } finally { LocalEduAdapterStore.delete(context, saved) }
    }
    @Test fun optInScriptsRoundTripIndependentlyOfWarehouseUpdatesAndDeleteOnlyTheirOwnRecord() {
        val context = RuntimeEnvironment.getApplication()
        val script = "(async()=>{await window.shiguangBridgePromise.saveImportedCourses('[]');window.shiguangBridge.notifyTaskCompletion()})()"
        val saved = LocalEduAdapterStore.save(context, "自用学校", "https://edu.example.edu/login", script)
        val other = LocalEduAdapterStore.save(context, "其他学校", "https://other.example.edu/login", script)
        try {
            val restored = LocalEduAdapterStore.load(context).first { it.adapterId == saved.adapterId }
            assertEquals(saved, restored)
            assertEquals(script, LocalEduAdapterStore.source(context, restored))
            assertEquals(listOf(saved), matchingLocalEduAdapters("https://edu.example.edu/courses", listOf(saved, other)))
            val revised = LocalEduAdapterStore.save(context, saved.school.name, saved.importUrl, script + "\n// edit", saved.adapterId)
            assertTrue(LocalEduAdapterStore.source(context, revised).endsWith("// edit"))
            LocalEduAdapterStore.delete(context, revised)
            assertFalse(LocalEduAdapterStore.load(context).any { it.adapterId == saved.adapterId })
            assertEquals(script, LocalEduAdapterStore.source(context, other))
        } finally { LocalEduAdapterStore.delete(context, saved); LocalEduAdapterStore.delete(context, other) }
    }
}
