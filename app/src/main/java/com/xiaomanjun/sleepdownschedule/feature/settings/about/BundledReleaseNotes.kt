package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.FileNotFoundException

internal const val ConsolidatedReleaseVersion = "2.0"

internal data class ReleaseNotesSection(val title: String, val entries: List<String>)

/** The release-note asset mirrors the Markdown used for the corresponding distribution release. */
@Composable
internal fun rememberBundledReleaseNotes(version: String): List<ReleaseNotesSection> {
    val assets = LocalContext.current.assets
    val tag = version.removePrefix("v").removeSuffix("-edu-test")
    return remember(assets, tag) {
        val lines = try {
            assets.open("release-notes/v$tag.md").bufferedReader().use { it.readLines() }
        } catch (_: FileNotFoundException) {
            emptyList()
        }
        buildList {
            var title = ""
            val entries = mutableListOf<String>()
            for (line in lines) {
                if (line.startsWith("## ")) {
                    if (entries.isNotEmpty()) add(ReleaseNotesSection(title, entries.toList()))
                    title = line.removePrefix("## ")
                    entries.clear()
                } else if (line.startsWith("- ") && title.isNotEmpty()) {
                    entries.add(line.removePrefix("- "))
                }
            }
            if (entries.isNotEmpty()) add(ReleaseNotesSection(title, entries.toList()))
        }
    }
}
