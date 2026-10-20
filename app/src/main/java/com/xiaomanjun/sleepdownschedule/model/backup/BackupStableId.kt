package com.xiaomanjun.sleepdownschedule.model.backup

import java.util.UUID

/**
 * Stable, Room-independent identifiers used inside a .sleepdown archive.
 *
 * These identifiers intentionally do not preserve Room auto-increment values. They only need to
 * be stable for the lifetime of one archive and its import plan.
 */
object BackupStableId {
    const val SCHEDULE_PREFIX = "schedule"
    const val COURSE_PREFIX = "course"
    const val SCHEME_PREFIX = "scheme"
    const val SESSION_PREFIX = "session"
    const val MESSAGE_PREFIX = "message"
    const val WIDGET_PREFIX = "widget"
    const val ASSET_PREFIX = "asset"
    const val HISTORY_PREFIX = "history"

    private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")

    fun new(prefix: String): String {
        require(prefix.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "非法备份 ID 前缀" }
        return "${prefix}_${UUID.randomUUID()}"
    }

    fun isValid(value: String, prefix: String? = null): Boolean {
        if (value.length > 128) return false
        val separator = value.indexOf('_')
        if (separator <= 0 || separator == value.lastIndex) return false
        val actualPrefix = value.substring(0, separator)
        if (prefix != null && actualPrefix != prefix) return false
        return actualPrefix.matches(Regex("[a-z][a-z0-9_-]{0,31}")) &&
            value.substring(separator + 1).matches(uuidPattern)
    }

    fun requireValid(value: String, prefix: String? = null): String {
        require(isValid(value, prefix)) { "非法备份 stable ID: $value" }
        return value
    }
}
