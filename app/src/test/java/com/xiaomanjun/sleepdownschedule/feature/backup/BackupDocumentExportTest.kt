package com.xiaomanjun.sleepdownschedule.feature.backup

import com.xiaomanjun.sleepdownschedule.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

class BackupDocumentExportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun savedDocumentMatchesValidatedArchiveAndCanBeRestored() {
        val directory = temporary.newFolder("staging")
        val export = BackupDocumentExport(directory)
        val archive = fixtureArchive()
        val name = export.prepare(archive)
        val destination = temporary.newFile("saved.sleepdown")

        // A picker may recreate the Activity; only the saved file name is needed to resume.
        val bytes = BackupDocumentExport(directory).save(
            name, { destination.outputStream() }, { destination.inputStream() }
        )

        assertTrue(bytes > 0)
        assertEquals(bytes, destination.length())
        assertArrayEquals(File(directory, name).readBytes(), destination.readBytes())
        val decoded = destination.inputStream().use { BackupCodec.decode(it) }
        assertEquals(archive.data, decoded.data)
        assertEquals(archive.preferences, decoded.preferences)
        assertEquals(listOf(1, 2), decoded.data.schedules.single().courses.single().periods)
        export.discard(name)
        assertFalse(File(directory, name).exists())
        assertTrue(destination.exists())
    }

    @Test fun invalidArchiveDoesNotLeaveAStagedFileOrNeedADestination() {
        val directory = temporary.newFolder("staging")
        val export = BackupDocumentExport(directory)
        val archive = fixtureArchive()
        val invalid = archive.copy(metadata = archive.metadata.copy(sourceVersionCode = -1))

        assertThrows(BackupCodecException::class.java) { export.prepare(invalid) }

        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun missingOrEmptyStagingFailsBeforeOpeningDestination() {
        val directory = temporary.newFolder("staging")
        val export = BackupDocumentExport(directory)
        val name = export.prepare(fixtureArchive())
        for (empty in listOf(true, false)) {
            if (empty) File(directory, name).writeBytes(byteArrayOf()) else export.discard(name)
            assertThrows(IllegalStateException::class.java) {
                export.save(name, { error("Must not create a destination") }, { error("Must not read") })
            }
        }
    }

    @Test fun providerSilentlyDroppingWritesCannotReportSuccess() {
        val export = BackupDocumentExport(temporary.newFolder("staging"))
        val name = export.prepare(fixtureArchive())
        val sink = object : OutputStream() { override fun write(value: Int) = Unit }

        val error = assertThrows(IOException::class.java) {
            export.save(name, { sink }, { ByteArrayInputStream(byteArrayOf()) })
        }

        assertTrue(error.message!!.contains("实际 0 字节"))
    }

    @Test fun truncatedAndSameSizeCorruptedDocumentsCannotReportSuccess() {
        val export = BackupDocumentExport(temporary.newFolder("staging"))
        val name = export.prepare(fixtureArchive())
        for (truncate in listOf(true, false)) {
            val written = ByteArrayOutputStream()
            assertThrows(IOException::class.java) {
                export.save(name, { written }, {
                    val bytes = written.toByteArray()
                    ByteArrayInputStream(
                        if (truncate) bytes.copyOf(bytes.size - 1)
                        else bytes.apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
                    )
                })
            }
        }
    }

    @Test fun destinationMustBeClosedBeforeVerificationAndCloseFailureIsNotSuccess() {
        val export = BackupDocumentExport(temporary.newFolder("staging"))
        val name = export.prepare(fixtureArchive())
        for (failOnClose in listOf(false, true)) {
            var closed = false
            val written = object : ByteArrayOutputStream() {
                override fun close() {
                    closed = true
                    if (failOnClose) throw IOException("provider close failed")
                    super.close()
                }
            }
            val save = {
                export.save(name, { written }, {
                    assertTrue(closed)
                    assertFalse(failOnClose)
                    ByteArrayInputStream(written.toByteArray())
                })
            }
            if (failOnClose) assertThrows(IOException::class.java) { save() }
            else assertTrue(save() > 0)
        }
    }

    @Test fun providerOpenOrReadFailureCannotReportSuccess() {
        val export = BackupDocumentExport(temporary.newFolder("staging"))
        val name = export.prepare(fixtureArchive())
        assertThrows(IOException::class.java) {
            export.save(name, { null }, { error("Must not read after open failed") })
        }
        assertThrows(IOException::class.java) {
            export.save(name, { ByteArrayOutputStream() }, { null })
        }
        assertThrows(IOException::class.java) {
            export.save(name, { object : OutputStream() {
                override fun write(value: Int) { throw IOException("disk full") }
            } }, { error("Must not read after write failed") })
        }
    }

    @Test fun cancelledPickerCleanupOnlyRemovesItsPreparedFile() {
        val directory = temporary.newFolder("staging")
        val export = BackupDocumentExport(directory)
        val cancelled = export.prepare(fixtureArchive())
        val other = export.prepare(fixtureArchive())
        export.discard(cancelled)
        assertFalse(File(directory, cancelled).exists())
        assertTrue(File(directory, other).exists())
        assertThrows(IllegalArgumentException::class.java) { export.discard("../backup-other.sleepdown") }
    }

    private fun fixtureArchive(): BackupArchive = BackupExportMapper.toArchive(
        metadata = BackupSourceMetadata(
            createdAt = "2026-10-12T00:00:00Z",
            sourceAppVersionName = "1.2.7_beta5",
            sourceVersionCode = 34,
            sourcePackageName = "com.xiaomanjun.sleepdownschedule",
            sourceDatabaseVersion = 34,
            devicePlatform = "Android"
        ),
        snapshot = BackupRoomSnapshot(
            schedules = listOf(ScheduleProfileEntity(1, "合成课表", true)),
            configs = listOf(defaultConfig(1)),
            periods = listOf(PeriodEntity(1, "08:00", "08:45", 1), PeriodEntity(2, "08:55", "09:40", 1)),
            courses = listOf(CourseEntity(
                id = 1, name = "合成课程", teacher = "", location = "", weekday = 1,
                periods = listOf(1, 2), weeks = listOf(1, 2), weekParity = WeekParity.ALL,
                note = "", scheduleId = 1
            ))
        ),
        preferences = BackupPreferences(preferencesVersion = BackupFormatV1.PREFERENCES_VERSION)
    )
}
