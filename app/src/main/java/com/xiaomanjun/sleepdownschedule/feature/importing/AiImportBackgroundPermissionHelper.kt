package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog

/** One-time, non-blocking guidance for OEM background network restrictions. */
internal object AiImportBackgroundPermissionHelper {
    private const val PreferencesName = "ai_import_background_permission"
    internal const val TipShownKey = "ai_import_background_tip_shown"

    fun isBatteryOptimizationIgnored(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }.getOrDefault(false)

    fun shouldShowTip(context: Context): Boolean = shouldShowTip(
        batteryOptimizationIgnored = isBatteryOptimizationIgnored(context),
        tipAlreadyShown = preferences(context).getBoolean(TipShownKey, false)
    )

    internal fun shouldShowTip(
        batteryOptimizationIgnored: Boolean,
        tipAlreadyShown: Boolean
    ): Boolean = !batteryOptimizationIgnored && !tipAlreadyShown

    fun markTipShown(context: Context) {
        preferences(context).edit().putBoolean(TipShownKey, true).apply()
    }

    fun requestIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE
    )
}

@Stable
internal class AiImportBackgroundPermissionGateState(
    private val context: Context
) {
    var showTip by mutableStateOf(false)
        private set

    private var pendingAction: (() -> Unit)? = null
    internal var launchPermissionRequest: (() -> Unit)? = null

    fun continueWithPermissionTip(action: () -> Unit) {
        if (!AiImportBackgroundPermissionHelper.shouldShowTip(context)) {
            action()
            return
        }
        // Persist before rendering so process recreation cannot turn this into a repeated prompt.
        AiImportBackgroundPermissionHelper.markTipShown(context)
        pendingAction = action
        showTip = true
    }

    fun cancelAndContinue() {
        showTip = false
        continuePendingAction()
    }

    fun requestPermission() {
        showTip = false
        launchPermissionRequest?.invoke() ?: continuePendingAction()
    }

    fun onPermissionRequestReturned() {
        // Re-read the system state as requested. Permission remains optional either way.
        AiImportBackgroundPermissionHelper.isBatteryOptimizationIgnored(context)
        continuePendingAction()
    }

    fun onPermissionRequestUnavailable() {
        continuePendingAction()
    }

    private fun continuePendingAction() {
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }
}

@Composable
internal fun rememberAiImportBackgroundPermissionGate(): AiImportBackgroundPermissionGateState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContext = context.applicationContext
    val state = remember(appContext) { AiImportBackgroundPermissionGateState(appContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        state.onPermissionRequestReturned()
    }
    state.launchPermissionRequest = {
        runCatching { launcher.launch(AiImportBackgroundPermissionHelper.requestIntent(context)) }
            .onFailure { state.onPermissionRequestUnavailable() }
    }
    return state
}

@Composable
internal fun AiImportBackgroundPermissionTip(
    state: AiImportBackgroundPermissionGateState,
    backdrop: Backdrop?,
    config: ScheduleConfigEntity
) {
    if (!state.showTip) return
    LiquidAlertDialog(
        title = "建议允许后台运行",
        message = "AI 教务导入需要几十秒处理课程信息。\n部分手机系统会限制应用后台网络，\n切换后台可能导致导入中断。\n建议开启后台运行权限。",
        actions = listOf(
            LiquidAlertAction(
                label = "取消",
                style = LiquidAlertActionStyle.Secondary,
                onClick = state::cancelAndContinue
            ),
            LiquidAlertAction(
                label = "去开启",
                style = LiquidAlertActionStyle.Primary,
                onClick = state::requestPermission
            )
        ),
        backdrop = backdrop,
        config = config,
        onDismissRequest = state::cancelAndContinue
    )
}
