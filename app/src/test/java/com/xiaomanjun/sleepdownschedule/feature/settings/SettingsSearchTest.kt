package com.xiaomanjun.sleepdownschedule.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test fun searchesLabelsAndFeatureKeywordsWithoutCaseSensitivity() {
        assertEquals(listOf(SettingsPage.BackupRestore), settingsSearchResults("webdav").map { it.page })
        assertEquals(listOf(SettingsPage.Notifications), settingsSearchResults("课前").map { it.page })
        assertEquals(listOf(SettingsPage.General), settingsSearchResults("深色 主题").map { it.page })
    }

    @Test fun blankOrUnknownQueriesDoNotCreateNavigationResults() {
        assertTrue(settingsSearchResults("  ").isEmpty())
        assertTrue(settingsSearchResults("不存在的设置项").isEmpty())
    }
}
