package com.xiaomanjun.sleepdownschedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdentityTest {
    private val production = AppIdentity.PRODUCTION_PACKAGE_NAME

    @Test
    fun legacyV115BackupIsTrustedByProduction() {
        assertTrue(
            AppIdentity.isTrustedBackupSource(
                AppIdentity.LEGACY_PACKAGE_NAME,
                production
            )
        )
    }

    @Test
    fun productionBackupIsTrustedByProduction() {
        assertTrue(AppIdentity.isTrustedBackupSource(production, production))
    }

    @Test
    fun unknownPackageIsRejected() {
        assertFalse(AppIdentity.isTrustedBackupSource("com.fake.sleepdown", production))
    }

    @Test
    fun debugPackageOnlyTrustsItselfAndLegacy() {
        val debug = "$production.debug"
        assertTrue(AppIdentity.isTrustedBackupSource(debug, debug))
        assertTrue(AppIdentity.isTrustedBackupSource(AppIdentity.LEGACY_PACKAGE_NAME, debug))
        assertFalse(AppIdentity.isTrustedBackupSource(production, debug))
    }
}
