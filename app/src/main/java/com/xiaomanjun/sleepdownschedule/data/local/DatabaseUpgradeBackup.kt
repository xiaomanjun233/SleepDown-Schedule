package com.xiaomanjun.sleepdownschedule.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** ATTACH + one transaction copies the source snapshot, including committed WAL contents. */
internal fun backupBeforeTimingUpgrade(context: Context, source: File) {
    if (!source.exists()) return
    SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
        if (db.version !in 1..49) return
        val directory = File(context.noBackupFilesDir, "database-upgrades").also { check(it.mkdirs() || it.isDirectory) }
        val target = File(directory, "${source.name}-v${db.version}-${System.currentTimeMillis()}.db")
        check(!target.exists()) { "升级回退副本已存在，未覆盖" }
        SQLiteDatabase.openOrCreateDatabase(target,null).close()
        db.execSQL("ATTACH DATABASE ? AS migration_backup", arrayOf(target.absolutePath))
        try {
            db.beginTransaction()
            try {
                val tables = buildList {
                    db.rawQuery("SELECT name,sql FROM main.sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND sql IS NOT NULL", null).use {
                        while (it.moveToNext()) add(it.getString(0) to it.getString(1))
                    }
                }
                fun quoted(value: String) = "\"${value.replace("\"", "\"\"")}\""
                tables.forEach { (name, sql) ->
                    val columns = sql.indexOf('(')
                    check(columns >= 0) { "无法建立数据库升级回退副本" }
                    db.execSQL("CREATE TABLE IF NOT EXISTS migration_backup.${quoted(name)} ${sql.substring(columns)}")
                    db.execSQL("DELETE FROM migration_backup.${quoted(name)}")
                    db.execSQL("INSERT INTO migration_backup.${quoted(name)} SELECT * FROM main.${quoted(name)}")
                }
                db.rawQuery("SELECT 1 FROM main.sqlite_master WHERE name='sqlite_sequence'",null).use {
                    if(it.moveToFirst()) {
                        db.execSQL("DELETE FROM migration_backup.sqlite_sequence")
                        db.execSQL("INSERT INTO migration_backup.sqlite_sequence SELECT * FROM main.sqlite_sequence")
                    }
                }
                db.rawQuery("SELECT name,sql FROM main.sqlite_master WHERE type='index' AND sql IS NOT NULL", null).use {
                    while (it.moveToNext()) {
                        val sql = it.getString(1)
                        val on = Regex("\\sON\\s", RegexOption.IGNORE_CASE).find(sql) ?: error("数据库索引无效")
                        val unique = if (Regex("CREATE\\s+UNIQUE", RegexOption.IGNORE_CASE).containsMatchIn(sql)) "UNIQUE " else ""
                        db.execSQL("CREATE ${unique}INDEX migration_backup.${quoted(it.getString(0))}${sql.substring(on.range.first)}")
                    }
                }
                db.execSQL("PRAGMA migration_backup.user_version = ${db.version}")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } finally { db.execSQL("DETACH DATABASE migration_backup") }
    }
}
