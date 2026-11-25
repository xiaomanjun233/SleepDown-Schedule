package com.xiaomanjun.sleepdownschedule.feature.settings.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.FileNotFoundException

internal const val CurrentReleaseVersion = "2.0.4"
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
        parseReleaseNotes(lines, includeIntroduction = false)
    }
}

internal fun parseReleaseNotes(lines: Iterable<String>, includeIntroduction: Boolean = true): List<ReleaseNotesSection> =
    buildList {
        var title = ""
        val entries = mutableListOf<String>()
        fun flush() {
            if (entries.isNotEmpty()) add(ReleaseNotesSection(title, entries.toList()))
            entries.clear()
        }
        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.startsWith("## ") || line.startsWith("### ") -> {
                    flush()
                    title = line.trimStart('#').trim()
                }
                line.startsWith("# ") || line.isEmpty() -> Unit
                line.startsWith("- ") && (title.isNotEmpty() || includeIntroduction) ->
                    entries.add(line.removePrefix("- "))
                includeIntroduction -> entries.add(line.removePrefix("* "))
            }
        }
        flush()
    }
