package com.xiaomanjun.sleepdownschedule.feature.coloros

import android.content.Context
import com.xiaomanjun.sleepdownschedule.feature.update.DownloadPackageKind
import com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdater
import com.xiaomanjun.sleepdownschedule.feature.update.UpdateDownloadState
import java.io.File

object ColorOSCourseComponentInstaller {
    private const val COMPONENT_DOWNLOAD_TAG_PREFIX = "course-component-"
    private val componentFileNames = setOf(
        "SleepDown-ColorOS-Course-Component.apk",
        "coloros-wakeup-proxy-release.apk"
    )

    suspend fun download(context: Context): Result<File> {
        val release = GiteeAppUpdater.findReleaseAsset(
            expectedNames = componentFileNames,
            displayName = "课程组件",
            packageKind = DownloadPackageKind.CourseComponent
        ).getOrElse { return Result.failure(it) }
        return GiteeAppUpdater.downloadApk(context, release)
    }

    fun isComponentDownload(state: UpdateDownloadState): Boolean = when (state) {
        is UpdateDownloadState.Downloading -> state.releaseTag.startsWith(COMPONENT_DOWNLOAD_TAG_PREFIX)
        is UpdateDownloadState.Completed -> state.releaseTag.startsWith(COMPONENT_DOWNLOAD_TAG_PREFIX)
        is UpdateDownloadState.Failed -> state.releaseTag.startsWith(COMPONENT_DOWNLOAD_TAG_PREFIX)
        UpdateDownloadState.Idle -> false
    }
}
