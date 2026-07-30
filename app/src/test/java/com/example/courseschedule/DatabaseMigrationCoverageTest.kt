package com.example.courseschedule

import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationCoverageTest {
    @Test
    fun everySupportedVersionCanReachCurrentSchema() {
        val edges = APP_DATABASE_MIGRATIONS.groupBy { it.startVersion }

        for (startVersion in 1 until APP_DATABASE_VERSION) {
            val reachable = mutableSetOf(startVersion)
            val pending = ArrayDeque<Int>().apply { add(startVersion) }
            while (pending.isNotEmpty()) {
                val version = pending.removeFirst()
                edges[version].orEmpty()
                    .map { it.endVersion }
                    .filter { it <= APP_DATABASE_VERSION && reachable.add(it) }
                    .forEach(pending::addLast)
            }

            assertTrue(
                "Database version $startVersion has no migration path to $APP_DATABASE_VERSION",
                APP_DATABASE_VERSION in reachable
            )
        }
    }

    @Test
    fun registeredMigrationsOnlyMoveForward() {
        assertTrue(APP_DATABASE_MIGRATIONS.isNotEmpty())
        assertTrue(APP_DATABASE_MIGRATIONS.all { it.endVersion > it.startVersion })
    }
}
