package com.xiaomanjun.sleepdownschedule.core.identity

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
    fun releaseEditionsCanRestoreEachOthersBackups() {
        val publishedColorOsPackage = "com.xiaomanjun.sleepdownschedule.coloros"
        AppIdentity.requireTrustedBackupSource(production, publishedColorOsPackage)
        AppIdentity.requireTrustedBackupSource(publishedColorOsPackage, production)
    }

    @Test
    fun colorosEditionKeepsItsOwnAndLegacyMigrationPaths() {
        val coloros = AppIdentity.COLOROS_PACKAGE_NAME
        AppIdentity.requireTrustedBackupSource(coloros, coloros)
        AppIdentity.requireTrustedBackupSource(AppIdentity.LEGACY_PACKAGE_NAME, coloros)
    }

    @Test
    fun editionSupportDoesNotTrustLookalikesOrDevelopmentBuilds() {
        for (target in listOf(production, AppIdentity.COLOROS_PACKAGE_NAME)) {
            for (source in listOf("$production.fake", "$production.debug", "$production.benchmark",
                "${AppIdentity.COLOROS_PACKAGE_NAME}.debug", "${AppIdentity.COLOROS_PACKAGE_NAME}.fake")) {
                assertFalse("$source must not be accepted by $target",
                    AppIdentity.isTrustedBackupSource(source, target))
            }
        }
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
        assertFalse(AppIdentity.isTrustedBackupSource(AppIdentity.COLOROS_PACKAGE_NAME, debug))
    }
}
