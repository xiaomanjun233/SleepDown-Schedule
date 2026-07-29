package com.example.courseschedule

import android.content.Context
import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.io.File

enum class WidgetAppearanceVariant(val key: String, val displayName: String, val canonicalAspect: Float) {
    COURSES_LARGE("COURSES_LARGE", "今日课程 4×2", 2f),
    COURSES_SQUARE("COURSES_SQUARE", "今日课程 2×2", 1f),
    TODAY_ASSISTANT("TODAY_ASSISTANT", "今日助手 4×2", 2f);

    companion object {
        fun fromKey(value: String): WidgetAppearanceVariant =
            entries.firstOrNull { it.key == value } ?: COURSES_LARGE
    }
}

const val WidgetDefaultAppearanceId = 0
const val DefaultWidgetBlurDp = 0f
const val DefaultWidgetBrightness = 1f

internal fun unreferencedWidgetWallpaperUris(
    appearances: Collection<WidgetAppearanceEntity>,
    candidateUris: Collection<String>
): Set<String> {
    val referenced = appearances.mapNotNull { it.wallpaperUri }.toSet()
    return candidateUris.filterTo(linkedSetOf()) { it !in referenced }
}

@Entity(tableName = "widget_appearances", primaryKeys = ["variant", "appWidgetId"])
data class WidgetAppearanceEntity(
    val variant: String,
    val appWidgetId: Int,
    @ColumnInfo(defaultValue = "0") val enabled: Boolean = false,
    val wallpaperUri: String? = null,
    @ColumnInfo(defaultValue = "0.5") val centerX: Float = 0.5f,
    @ColumnInfo(defaultValue = "0.5") val centerY: Float = 0.5f,
    @ColumnInfo(defaultValue = "1") val scale: Float = 1f,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    @ColumnInfo(defaultValue = "0") val blurDp: Float = DefaultWidgetBlurDp,
    @ColumnInfo(defaultValue = "1") val brightness: Float = DefaultWidgetBrightness,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L
) {
    val type: WidgetAppearanceVariant get() = WidgetAppearanceVariant.fromKey(variant)

    fun normalized(): WidgetAppearanceEntity = copy(
        centerX = centerX.coerceIn(0f, 1f),
        centerY = centerY.coerceIn(0f, 1f),
        scale = scale.coerceIn(1f, 6f),
        blurDp = blurDp.coerceIn(0f, 10f),
        brightness = brightness.coerceIn(0.35f, 1f)
    )

    companion object {
        fun defaults(type: WidgetAppearanceVariant, appWidgetId: Int = WidgetDefaultAppearanceId) =
            WidgetAppearanceEntity(type.key, appWidgetId)
    }
}

@Dao
interface WidgetAppearanceDao {
    @Query("SELECT * FROM widget_appearances ORDER BY variant, appWidgetId")
    fun observeAll(): Flow<List<WidgetAppearanceEntity>>

    @Query("SELECT * FROM widget_appearances ORDER BY variant, appWidgetId")
    suspend fun getAll(): List<WidgetAppearanceEntity>

    @Query("SELECT * FROM widget_appearances WHERE variant=:variant AND appWidgetId=:appWidgetId LIMIT 1")
    suspend fun get(variant: String, appWidgetId: Int): WidgetAppearanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: WidgetAppearanceEntity)

    @Query("DELETE FROM widget_appearances WHERE variant=:variant AND appWidgetId=:appWidgetId")
    suspend fun delete(variant: String, appWidgetId: Int)

    @Query("DELETE FROM widget_appearances WHERE variant=:variant AND appWidgetId != 0 AND appWidgetId NOT IN (:activeIds)")
    suspend fun deleteStale(variant: String, activeIds: List<Int>)
}

class WidgetAppearanceRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val dao = database.widgetAppearanceDao()

    fun observeAll(): Flow<List<WidgetAppearanceEntity>> = dao.observeAll()

    suspend fun all(): List<WidgetAppearanceEntity> {
        ensureDefaults()
        return dao.getAll()
    }

    suspend fun ensureDefaults() {
        database.withTransaction {
            WidgetAppearanceVariant.entries.forEach { type ->
                val existing = dao.get(type.key, WidgetDefaultAppearanceId)
                if (existing == null) {
                    dao.upsert(WidgetAppearanceEntity.defaults(type))
                } else if (
                    existing.updatedAt == 0L &&
                    existing.wallpaperUri == null &&
                    !existing.enabled &&
                    existing.brightness == 0.85f
                ) {
                    dao.upsert(existing.copy(brightness = DefaultWidgetBrightness))
                }
            }
        }
    }

    suspend fun get(type: WidgetAppearanceVariant, appWidgetId: Int): WidgetAppearanceEntity =
        database.withTransaction {
            val existing = dao.get(type.key, appWidgetId)
            if (existing != null) return@withTransaction existing.normalized()
            val base = dao.get(type.key, WidgetDefaultAppearanceId)
                ?: WidgetAppearanceEntity.defaults(type).also { dao.upsert(it) }
            val created = base.copy(appWidgetId = appWidgetId, updatedAt = System.currentTimeMillis()).normalized()
            if (appWidgetId != WidgetDefaultAppearanceId) dao.upsert(created)
            created
        }

    suspend fun save(value: WidgetAppearanceEntity): WidgetAppearanceEntity {
        val normalized = value.normalized().copy(updatedAt = System.currentTimeMillis())
        dao.upsert(normalized)
        cleanupUnreferencedFiles()
        return normalized
    }

    suspend fun reset(type: WidgetAppearanceVariant, appWidgetId: Int): WidgetAppearanceEntity {
        val reset = if (appWidgetId == WidgetDefaultAppearanceId) {
            WidgetAppearanceEntity.defaults(type).copy(updatedAt = System.currentTimeMillis())
        } else {
            get(type, WidgetDefaultAppearanceId).copy(
                appWidgetId = appWidgetId,
                updatedAt = System.currentTimeMillis()
            )
        }
        dao.upsert(reset)
        cleanupUnreferencedFiles()
        return reset
    }

    suspend fun deleteInstance(type: WidgetAppearanceVariant, appWidgetId: Int) {
        if (appWidgetId == WidgetDefaultAppearanceId) return
        dao.delete(type.key, appWidgetId)
        cleanupUnreferencedFiles()
    }

    suspend fun reconcile(type: WidgetAppearanceVariant, activeIds: IntArray) {
        ensureDefaults()
        activeIds.forEach { get(type, it) }
        if (activeIds.isEmpty()) {
            dao.getAll().filter { it.variant == type.key && it.appWidgetId != WidgetDefaultAppearanceId }
                .forEach { dao.delete(type.key, it.appWidgetId) }
        } else {
            dao.deleteStale(type.key, activeIds.toList())
        }
        cleanupUnreferencedFiles()
    }

    suspend fun persistSelectedImage(uri: Uri): Uri? {
        val directory = File(context.filesDir, "widget_wallpaper").apply { mkdirs() }
        val extension = runCatching {
            context.contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        }.getOrNull() ?: "jpg"
        val output = File(directory, "widget_${System.currentTimeMillis()}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                output.outputStream().use(input::copyTo)
            } ?: return null
            Uri.fromFile(output)
        }.getOrElse {
            output.delete()
            null
        }
    }

    private suspend fun cleanupUnreferencedFiles() {
        val directory = File(context.filesDir, "widget_wallpaper")
        val files = directory.listFiles().orEmpty()
        val unused = unreferencedWidgetWallpaperUris(dao.getAll(), files.map { Uri.fromFile(it).toString() })
        files.forEach { file ->
            if (Uri.fromFile(file).toString() in unused) file.delete()
        }
    }
}
