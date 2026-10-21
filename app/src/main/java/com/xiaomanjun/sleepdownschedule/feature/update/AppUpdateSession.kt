package com.xiaomanjun.sleepdownschedule.feature.update

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.identity.AppDistribution
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One page's update interaction; download transport remains owned by GiteeAppUpdater. */
internal class AppUpdateSession(
    private val context: Context,
    private val scope: CoroutineScope,
    val versionName: String
) {
    var dialog by mutableStateOf<AppUpdateDialog?>(null)
        private set
    private var downloadedApk: File? = null

    fun dismiss() { dialog = null }

    fun resumeInstall() {
        val apk = downloadedApk
        if (apk != null && GiteeAppUpdater.canRequestPackageInstalls(context)) {
            runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                .onFailure { dialog = AppUpdateDialog.Error(it.readableUpdateMessage()) }
        }
    }

    fun checkForUpdate(markDailyCheck: Boolean = true) {
        if (dialog is AppUpdateDialog.Checking || dialog is AppUpdateDialog.Downloading) return
        dialog = AppUpdateDialog.Checking
        if (markDailyCheck) GiteeAppUpdater.markDailyCheckStarted(context)
        scope.launch {
            dialog = GiteeAppUpdater.checkForUpdate(context, versionName).fold(
                onSuccess = { result ->
                    GiteeAppUpdater.recordCheckResult(context, result)
                    when (result) {
                        is GiteeUpdateCheckResult.UpdateAvailable -> AppUpdateDialog.Available(result.release)
                        is GiteeUpdateCheckResult.UpToDate -> AppUpdateDialog.UpToDate(result.release.tagName)
                    }
                },
                onFailure = { AppUpdateDialog.Error(it.readableUpdateMessage()) }
            )
        }
    }

    fun downloadAndInstall(release: GiteeReleaseInfo) {
        if (release.apkUrl == null) {
            dialog = AppUpdateDialog.NoApk(release)
            return
        }
        dialog = AppUpdateDialog.Downloading(release)
        scope.launch {
            GiteeAppUpdater.downloadApk(context, release).fold(
                onSuccess = { apk ->
                    downloadedApk = apk
                    if (GiteeAppUpdater.canRequestPackageInstalls(context)) {
                        runCatching { GiteeAppUpdater.launchInstaller(context, apk) }
                            .onSuccess { dialog = null }
                            .onFailure { dialog = AppUpdateDialog.Error(it.readableUpdateMessage()) }
                    } else {
                        dialog = AppUpdateDialog.InstallPermissionRequired
                    }
                },
                onFailure = { dialog = AppUpdateDialog.Error(it.readableUpdateMessage()) }
            )
        }
    }

    suspend fun checkDaily(loaded: Boolean, enabled: Boolean) {
        if (!loaded || !AppDistribution.supportsSelfUpdate) return
        GiteeAppUpdater.restoreCachedStatus(context, versionName)
        if (!enabled || !GiteeAppUpdater.shouldRunDailyCheck(context)) return
        GiteeAppUpdater.markDailyCheckStarted(context)
        GiteeAppUpdater.checkForUpdate(context, versionName).onSuccess { result ->
            GiteeAppUpdater.recordCheckResult(context, result)
            if (result is GiteeUpdateCheckResult.UpdateAvailable) {
                dialog = AppUpdateDialog.Available(result.release)
            }
        }
    }
}

@Composable
internal fun rememberAppUpdateSession(versionName: String? = null): AppUpdateSession {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember(context, versionName) {
        versionName ?: runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    return remember(context, scope, currentVersion) { AppUpdateSession(context, scope, currentVersion) }
}

@Composable
internal fun AutomaticAppUpdateCheck(session: AppUpdateSession, loaded: Boolean, enabled: Boolean) {
    LaunchedEffect(loaded, enabled, session) { session.checkDaily(loaded, enabled) }
}

@Composable
internal fun AppUpdateDialogHost(
    session: AppUpdateSession,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity,
    onRetry: () -> Unit,
    onOpenRelease: (GiteeReleaseInfo) -> Unit,
    onOpenBackup: () -> Unit
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        session.resumeInstall()
    }
    AppUpdateDialogHost(
        dialog = session.dialog,
        backdrop = backdrop,
        config = config,
        onDismiss = session::dismiss,
        onRetry = onRetry,
        onDownload = session::downloadAndInstall,
        onOpenRelease = onOpenRelease,
        onOpenBackup = onOpenBackup,
        onRequestInstallPermission = {
            permissionLauncher.launch(GiteeAppUpdater.unknownSourcesSettingsIntent(context))
        }
    )
}
