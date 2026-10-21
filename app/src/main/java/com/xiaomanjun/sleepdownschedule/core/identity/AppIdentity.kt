package com.xiaomanjun.sleepdownschedule.core.identity

object AppIdentity {
    const val PRODUCTION_PACKAGE_NAME = "com.xiaomanjun.sleepdownschedule"
    const val COLOROS_PACKAGE_NAME = "com.xiaomanjun.sleepdownschedule.coloros"
    const val LEGACY_PACKAGE_NAME = "com.example.courseschedule"

    // Both release editions use the same backup protocol. Keep this an exact allowlist:
    // debug/benchmark builds and similarly named packages retain their existing isolation.
    private val releaseBackupPackages = setOf(PRODUCTION_PACKAGE_NAME, COLOROS_PACKAGE_NAME)

    fun isTrustedBackupSource(sourcePackageName: String, currentPackageName: String): Boolean =
        sourcePackageName == currentPackageName ||
            sourcePackageName == LEGACY_PACKAGE_NAME ||
            (sourcePackageName in releaseBackupPackages && currentPackageName in releaseBackupPackages)

    fun requireTrustedBackupSource(sourcePackageName: String, currentPackageName: String) {
        require(isTrustedBackupSource(sourcePackageName, currentPackageName)) {
            "备份来源 package 不受信任"
        }
    }
}
