package com.xiaomanjun.sleepdownschedule

object AppIdentity {
    const val PRODUCTION_PACKAGE_NAME = "com.xiaomanjun.sleepdownschedule"
    const val LEGACY_PACKAGE_NAME = "com.example.courseschedule"

    fun isTrustedBackupSource(sourcePackageName: String, currentPackageName: String): Boolean =
        sourcePackageName == currentPackageName || sourcePackageName == LEGACY_PACKAGE_NAME

    fun requireTrustedBackupSource(sourcePackageName: String, currentPackageName: String) {
        require(isTrustedBackupSource(sourcePackageName, currentPackageName)) {
            "备份来源 package 不受信任"
        }
    }
}
