package com.xiaomanjun.sleepdownschedule.feature.coloros

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.xiaomanjun.sleepdownschedule.feature.update.DownloadPackageKind
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.feature.update.UpdateDownloadState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface CourseComponentDownloadCheck {
    data class Install(val apk: File, val versionName: String) : CourseComponentDownloadCheck
    data class NoUpdate(
        val installedVersionName: String,
        val downloadedVersionName: String,
        val downloadedIsOlder: Boolean
    ) : CourseComponentDownloadCheck
}

internal fun componentUpdateAvailable(downloadedVersionCode: Long, installedVersionCode: Long?): Boolean =
    installedVersionCode == null || downloadedVersionCode > installedVersionCode

object ColorOSCourseComponentInstaller {
    private const val COMPONENT_DOWNLOAD_TAG_PREFIX = "course-component-"
    private val componentFileNames = setOf(
        "SleepDown-ColorOS-Course-Component.apk",
        "coloros-wakeup-proxy-release.apk"
    )

    suspend fun download(context: Context): Result<CourseComponentDownloadCheck> {
        val release = GiteeAppUpdater.findReleaseAsset(
            expectedNames = componentFileNames,
            displayName = "课程组件",
            packageKind = DownloadPackageKind.CourseComponent
        ).getOrElse { return Result.failure(it) }
        val apk = GiteeAppUpdater.downloadApk(context, release).getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) { runCatching { inspectDownload(context, apk) } }
    }

    @Suppress("DEPRECATION")
    private fun inspectDownload(context: Context, apk: File): CourseComponentDownloadCheck {
        val packageManager = context.packageManager
        val flags = PackageManager.GET_META_DATA or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val downloaded = packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("下载的文件不是有效的课程组件 APK")
        require(downloaded.packageName == ColorOSCourseContract.PROXY_PACKAGE) {
            "课程组件包名不符，已停止安装"
        }
        require(downloaded.applicationInfo?.metaData?.getString(ColorOSCourseContract.PROXY_METADATA_KEY) ==
            ColorOSCourseContract.PROXY_METADATA_VERSION) {
            "下载的 APK 缺少 SleepDown 课程组件标识"
        }
        val app = packageManager.getPackageInfo(context.packageName, flags)
        val appSigners = signers(app)
        require(appSigners.isNotEmpty() && signers(downloaded) == appSigners) {
            "课程组件签名与 SleepDown 不一致，已停止安装"
        }
        val installed = runCatching {
            packageManager.getPackageInfo(ColorOSCourseContract.PROXY_PACKAGE, flags)
        }.getOrNull()
        require(installed == null || signers(installed) == appSigners) {
            "已安装的 WakeUp 课程表占用相同包名，无法覆盖"
        }
        val downloadedCode = downloaded.longVersionCodeCompat()
        val installedCode = installed?.longVersionCodeCompat()
        return if (componentUpdateAvailable(downloadedCode, installedCode)) {
            CourseComponentDownloadCheck.Install(apk, downloaded.versionName.orEmpty())
        } else {
            CourseComponentDownloadCheck.NoUpdate(
                installedVersionName = installed?.versionName.orEmpty(),
                downloadedVersionName = downloaded.versionName.orEmpty(),
                downloadedIsOlder = installedCode != null && downloadedCode < installedCode
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners.orEmpty()
        else info.signatures.orEmpty()).mapTo(linkedSetOf()) { it.toCharsString() }

    fun isComponentDownload(state: UpdateDownloadState): Boolean = when (state) {
        is UpdateDownloadState.Downloading -> state.releaseTag.startsWith(COMPONENT_DOWNLOAD_TAG_PREFIX)
        is UpdateDownloadState.Completed -> state.releaseTag.startsWith(COMPONENT_DOWNLOAD_TAG_PREFIX)
        is UpdateDownloadState.Failed -> state.releaseTag.startsWith(COMPONENT_DOWNLOAD_TAG_PREFIX)
        UpdateDownloadState.Idle -> false
    }
}
