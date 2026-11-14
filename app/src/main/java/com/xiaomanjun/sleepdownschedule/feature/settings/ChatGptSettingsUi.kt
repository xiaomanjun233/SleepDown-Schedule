package com.xiaomanjun.sleepdownschedule.feature.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.button.CouiButton
import com.itos.coui.button.CouiButtonDefaults
import com.itos.coui.button.CouiButtonStyle
import com.itos.coui.button.CouiCapsuleButton
import com.itos.coui.theme.CouiTheme
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.settings.CouiSettingsToolbarTextButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.GlassPreferenceSection
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsBasicComponent
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsDivider
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsGroup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SettingsInfoRow
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.feature.importing.AiReasoningEffort
import com.xiaomanjun.sleepdownschedule.feature.importing.ChatGptUsageStatus
import com.xiaomanjun.sleepdownschedule.feature.importing.chatGptReasoningEfforts
import com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt.ChatGptAuthException
import com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt.ChatGptAuthManager
import com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt.ChatGptBrowser
import com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt.ChatGptModel
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ChatGptSettingsPanel(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    selectedModel: String,
    reasoningEffort: AiReasoningEffort,
    signingOut: Boolean,
    onReasoningChanged: (AiReasoningEffort) -> Unit,
    onBeforeLogin: () -> Unit,
    onModelsChanged: (List<ChatGptModel>, ChatGptModel) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth by ChatGptAuthManager.state.collectAsStateWithLifecycle()
    val usage by ChatGptUsageStatus.state.collectAsStateWithLifecycle()
    val account = auth.account
    val models = auth.models
    val signedIn = account?.isSignedIn == true
    val planEnabled = signedIn && account?.planUsageEnabled == true
    var actionMessage by remember(account?.id) { mutableStateOf<String?>(null) }
    val latestSelectedModel by rememberUpdatedState(selectedModel)
    val latestModelsChanged by rememberUpdatedState(onModelsChanged)

    suspend fun refreshModels() {
        actionMessage = null
        try {
            val refreshed = ChatGptAuthManager.models(context)
            if (refreshed.isEmpty()) actionMessage = "账号尚未提供可用模型，请检查 ChatGPT 中的授权。"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ChatGptAuthException) {
            actionMessage = error.message
        } catch (_: Exception) {
            actionMessage = "模型读取失败，请检查网络后重试。"
        }
    }

    LaunchedEffect(Unit) { ChatGptAuthManager.initialize(context) }
    LaunchedEffect(account?.id, signedIn, planEnabled, auth.isSigningIn) {
        if (planEnabled && !auth.isSigningIn) refreshModels()
    }
    LaunchedEffect(account?.id, models, selectedModel) {
        if (models.isNotEmpty()) {
            val selected = models.firstOrNull { it.slug == latestSelectedModel } ?: models.first()
            latestModelsChanged(models, selected)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        GlassPreferenceSection("ChatGPT 账号") {
            SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow(
                    title = if (signedIn) account.email ?: account.displayName ?: "ChatGPT" else "使用你的 ChatGPT 套餐",
                    body = when {
                        auth.isSigningIn -> auth.loginStage.label
                        planEnabled -> "已授权用于 AI 对话、AI 助理和课表识别。"
                        signedIn -> "账号已登录，请授权使用 ChatGPT 套餐后继续。"
                        else -> "登录后使用套餐内的 AI 用量，无需填写 API Key。"
                    },
                    badgeText = when {
                        auth.isSigningIn -> "登录中"
                        planEnabled -> "已连接"
                        signedIn -> "待授权"
                        else -> null
                    }
                )
                val accountMessage = actionMessage ?: auth.errorMessage
                if (auth.isSigningIn || !planEnabled || accountMessage != null) Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (auth.isSigningIn) {
                        CouiButton(text = "取消登录", dark = CouiTheme.isDark,
                            onClick = { ChatGptAuthManager.cancelLogin() })
                    } else if (!planEnabled) {
                        CouiButton(
                            text = "使用 ChatGPT 继续", dark = CouiTheme.isDark,
                            modifier = Modifier.fillMaxWidth(), enabled = !signingOut,
                            style = CouiButtonStyle.Primary,
                            colors = CouiButtonDefaults.primaryColors(CouiTheme.colorScheme.accent),
                            onClick = {
                                actionMessage = null
                                onBeforeLogin()
                                ChatGptAuthManager.startLogin(context, requestConsent = signedIn)
                            }
                        )
                        Text("将在浏览器安全窗口中完成授权。登录信息仅加密保存在此设备。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    accountMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (account != null && !auth.isSigningIn) {
                    SettingsDivider()
                    SettingsBasicComponent(title = "使用其他账号", endActions = {
                        CouiSettingsToolbarTextButton("切换", enabled = !signingOut) {
                            actionMessage = null
                            onBeforeLogin()
                            ChatGptAuthManager.startLogin(context, addAccount = true, requestConsent = true)
                        }
                    })
                }
            }
        }
        if (planEnabled) {
            GlassPreferenceSection("模型与思考") {
                val modelRefresh: @Composable () -> Unit = {
                    val dark = CouiTheme.isDark
                    val colors = CouiButtonDefaults.secondaryColors(dark)
                    CouiCapsuleButton(
                        modifier = Modifier.size(40.dp),
                        dark = dark, colors = colors, height = 40.dp,
                        contentPadding = PaddingValues(0.dp),
                        enabled = !auth.isLoadingModels && !auth.isSigningIn && !signingOut,
                        onClick = { scope.launch { refreshModels() } }
                    ) {
                        if (auth.isLoadingModels) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(painterResource(R.drawable.ic_refresh), "刷新模型", Modifier.size(18.dp),
                                tint = if (auth.isSigningIn || signingOut) colors.disabledContent else colors.content)
                        }
                    }
                }
                SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
                    if (models.isNotEmpty()) {
                        SleepDownLiquidDropdownPreference(
                            items = models.map { it.displayName },
                            selectedIndex = models.indexOfFirst { it.slug == selectedModel }.coerceAtLeast(0),
                            title = "当前模型", backdrop = backdrop, config = config,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            maxHeight = 318.dp, onExpandedChange = {},
                            onSelectedIndexChange = { index -> models.getOrNull(index)?.let { onModelsChanged(models, it) } }
                        )
                    } else {
                        SettingsInfoRow("可用模型", if (auth.isLoadingModels) "正在读取…" else "暂未获取到模型，请刷新后重试。")
                    }
                    if (models.isNotEmpty()) {
                        SettingsDivider()
                        val efforts = chatGptReasoningEfforts(selectedModel)
                        if (efforts.isNotEmpty()) {
                            SleepDownLiquidDropdownPreference(
                                items = efforts.map { it.label },
                                selectedIndex = efforts.indexOf(reasoningEffort).coerceAtLeast(0),
                                title = "思考强度", backdrop = backdrop, config = config,
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                maxHeight = 318.dp, onExpandedChange = {},
                                onSelectedIndexChange = { index -> efforts.getOrNull(index)?.let(onReasoningChanged) }
                            )
                        } else {
                            SettingsInfoRow("思考强度", "当前模型使用默认强度，暂未提供可调档位。")
                        }
                    }
                    SettingsDivider()
                    SettingsBasicComponent(
                        title = "模型列表",
                        summary = if (auth.isLoadingModels) "正在刷新…" else null,
                        endActions = { modelRefresh() },
                    )
                }
            }
        }
        GlassPreferenceSection("套餐用量") {
            SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow(
                    title = if (planEnabled) usage.status else "用量由 ChatGPT 管理",
                    body = buildString {
                        if (planEnabled) {
                            usage.checkedAtMillis?.let {
                                append(DateFormat.getTimeFormat(context).format(Date(it))).append(" · ")
                            }
                            usage.diagnostic?.let { append(it).append("\n") }
                        }
                        append("套餐余额和使用限制请在 ChatGPT 官方用量页查看。此处显示最近一次调用状态，无法直接查询套餐剩余额度。")
                    },
                    badgeText = if (planEnabled && usage.limitReached) "用量受限" else null
                )
                SettingsDivider()
                SettingsBasicComponent(
                    title = "管理用量", summary = "打开 ChatGPT 用量设置",
                    endActions = {
                        CouiSettingsToolbarTextButton("查看") {
                            try { ChatGptBrowser.open(context, "https://chatgpt.com/settings/usage") }
                            catch (error: ChatGptAuthException) { actionMessage = error.message }
                        }
                    }
                )
            }
        }
    }
}
