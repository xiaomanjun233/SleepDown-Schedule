package com.xiaomanjun.sleepdownschedule.feature.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.app.config.SleepDownRemoteConfig
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.domain.agent.AgentActionHandler
import com.xiaomanjun.sleepdownschedule.feature.importing.AiImportSettingsStore
import com.xiaomanjun.sleepdownschedule.feature.importing.AiProviderPresets
import java.time.LocalDate

@Composable
fun TodayAgentHost(
    state: AppState,
    date: LocalDate,
    backdrop: Backdrop?,
    textColor: Color,
    collapsed: Boolean,
    isActive: Boolean = true,
    backgroundMotionState: DayAgentBackgroundMotionState,
    onPrepareOpen: suspend () -> Unit = {},
    onAgentDismissed: () -> Unit = {},
    onAgentAction: AgentActionHandler = { _, _ -> },
    onImportFile: ((android.net.Uri) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun todayAgentHasUsableAi(): Boolean {
        if (AiImportSettingsStore.resolveAvailableSettings(context) != null) return true
        // The backend-issued daily-free AI may still be loading its signed config. Opening the
        // conversation is allowed whenever the daily-free provider is the selected provider; the
        // run then surfaces the config state and the click below refreshes the config.
        return AiImportSettingsStore.load(context).profile.id == AiProviderPresets.dailyFree.id
    }
    var hasApiKey by remember {
        mutableStateOf(todayAgentHasUsableAi())
    }
    var enabled by remember(date, state.config.id) { mutableStateOf(DayAgentPreferences.isEnabled(context)) }
    var hasDecision by remember(date, state.config.id) { mutableStateOf(DayAgentPreferences.hasDecision(context)) }
    val preferenceVersion by DayAgentPreferences.changes.collectAsStateWithLifecycle(initialValue = 0L)
    val aiSettingsVersion by AiImportSettingsStore.changes.collectAsStateWithLifecycle()

    LaunchedEffect(preferenceVersion) {
        enabled = DayAgentPreferences.isEnabled(context)
        hasDecision = DayAgentPreferences.hasDecision(context)
    }

    LaunchedEffect(aiSettingsVersion) {
        hasApiKey = todayAgentHasUsableAi()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = DayAgentPreferences.isEnabled(context)
        hasDecision = DayAgentPreferences.hasDecision(context)
        hasApiKey = todayAgentHasUsableAi()
    }

    if (!hasDecision) {
        LiquidAlertDialog(
            title = "启用 AI 助理？",
            message = "AI 助理会在日视图显示下一节课、实时倒计时与天气。课程和倒计时均在本地计算；绑定 API Key 后，点击卡片可以继续对话。你可以随时在 AI 助理设置中关闭。",
            actions = listOf(
                LiquidAlertAction("暂不启用", LiquidAlertActionStyle.Cancel) {
                    DayAgentPreferences.setEnabled(context, false)
                    enabled = false
                    hasDecision = true
                },
                LiquidAlertAction("启用", LiquidAlertActionStyle.Primary) {
                    DayAgentPreferences.setEnabled(context, true)
                    enabled = true
                    hasDecision = true
                }
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = {
                DayAgentPreferences.setEnabled(context, false)
                enabled = false
                hasDecision = true
            }
        )
    }

    if (enabled) {
        TodayAgentCard(
            state = state,
            date = date,
            backdrop = backdrop,
            textColor = textColor,
            collapsed = collapsed,
            isActive = isActive,
            weatherEnabled = DayAgentPreferences.isWeatherEnabled(context),
            hasApiKey = hasApiKey,
            activateAvailableAi = {
                val ready = AiImportSettingsStore.activateAvailableSettings(context) != null
                val usable = ready ||
                    AiImportSettingsStore.load(context).profile.id == AiProviderPresets.dailyFree.id
                if (!ready && usable) {
                    // The daily-free config was still loading; force a refresh so the first run
                    // can use the backend-issued key as soon as it is available.
                    SleepDownRemoteConfig.refresh(scope, force = true)
                }
                hasApiKey = usable
                usable
            },
            backgroundMotionState = backgroundMotionState,
            onPrepareOpen = onPrepareOpen,
            onAgentDismissed = onAgentDismissed,
            onAgentAction = onAgentAction,
            onImportFile = onImportFile
        )
    }
}
