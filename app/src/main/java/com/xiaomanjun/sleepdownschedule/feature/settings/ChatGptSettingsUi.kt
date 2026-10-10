package com.xiaomanjun.sleepdownschedule.feature.settings

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.ScheduleConfigEntity
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.DialogLiquidButton
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.feature.importing.ChatGptUsageStatus
import com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt.ChatGptAuthManager
import com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt.ChatGptAuthException
import com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt.ChatGptModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Date

/** Account controls deliberately have no API-key, endpoint or arbitrary-model editor. */
@Composable
internal fun ChatGptSettingsPanel(
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    selectedModel: String,
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
    var signingOut by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var connectionCheckedAt by remember(account?.id) { mutableStateOf<Long?>(null) }
    var connectionCheckFailed by remember(account?.id) { mutableStateOf(false) }
    val latestSelectedModel by rememberUpdatedState(selectedModel)
    val latestModelsChanged by rememberUpdatedState(onModelsChanged)

    suspend fun refreshConnection() {
        actionMessage = null
        try {
            ChatGptAuthManager.models(context)
            connectionCheckedAt = System.currentTimeMillis()
            connectionCheckFailed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ChatGptAuthException) {
            actionMessage = error.message ?: "连接状态刷新失败，请稍后重试。"
            connectionCheckFailed = true
        } catch (_: Exception) {
            // The manager exposes only a sanitized error. Raw OAuth/network exceptions may
            // include credentials and must never reach this settings page or a log.
            actionMessage = "连接状态刷新失败，请稍后重试。"
            connectionCheckFailed = true
        }
    }

    LaunchedEffect(Unit) { ChatGptAuthManager.initialize(context) }
    LaunchedEffect(account?.id, signedIn, planEnabled) {
        if (planEnabled) refreshConnection()
    }
    LaunchedEffect(account?.id, models, selectedModel) {
        if (models.isNotEmpty()) {
            val selected = models.firstOrNull { it.slug == latestSelectedModel } ?: models.first()
            latestModelsChanged(models, selected)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassPreferenceSection("账号与额度") {
            SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
                SettingsInfoRow(
                    title = account?.displayName?.takeIf(String::isNotBlank) ?: "ChatGPT 账号",
                    body = account?.email?.takeIf(String::isNotBlank)
                        ?: if (signedIn) "已在本机安全保存登录凭据" else "使用系统浏览器登录，不需要填写 API Key。",
                    badgeText = when {
                        auth.isSigningIn -> "等待授权"
                        planEnabled -> "已连接"
                        signedIn -> "需授权调用"
                        else -> "未登录"
                    }
                )
                SettingsDivider()
                SettingsInfoRow(
                    "套餐调用权限",
                    when {
                        planEnabled -> "已启用。AI 请求将使用 ChatGPT 套餐用量，可在下方用量页管理。实际可用模型和用量以账号授权及服务端响应为准。"
                        signedIn -> "当前登录未提供套餐调用权限，请重新授权或检查账号资格。"
                        else -> "授权后的 AI 请求将使用 ChatGPT 套餐用量，可在下方用量页管理。登录过期或用量受限时，会提示处理，不会改用其他接口。"
                    }
                )
                SettingsDivider()
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        SettingsInfoRow("剩余额度", "暂无法读取")
                    }
                    Column(Modifier.weight(1f)) {
                        SettingsInfoRow("重置时间", "暂无法读取")
                    }
                }
                SettingsInfoRow(
                    "用量状态",
                    buildString {
                        append(if (signedIn) usage.status else "登录后可查看调用状态")
                        usage.checkedAtMillis?.takeIf { signedIn }?.let { timestamp ->
                            append("\n最近调用：")
                            append(DateFormat.getTimeFormat(context).format(Date(timestamp)))
                        }
                        usage.diagnostic?.takeIf { signedIn }?.let { append("\n").append(it) }
                        append("\n当前登录接口未提供额度百分比或重置时间，请在 ChatGPT 用量页查看。")
                    },
                    badgeText = if (signedIn && usage.limitReached) "用量受限" else null
                )
                SettingsDivider()
                SettingsActionRow(
                    title = "ChatGPT 用量",
                    subtitle = "在系统浏览器打开官方用量页",
                    buttonText = "查看",
                    iconRes = R.drawable.ic_ai_import,
                    backdrop = backdrop,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure { actionMessage = "没有可打开用量页的浏览器。" }
                    }
                )
                SettingsDivider()
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (auth.isSigningIn) {
                        Text("请在系统浏览器完成授权，然后返回这里。", style = MaterialTheme.typography.bodySmall)
                        DialogLiquidButton(
                            label = "取消登录",
                            backdrop = backdrop,
                            onClick = { ChatGptAuthManager.cancelLogin() }
                        )
                    } else if (!planEnabled) {
                        DialogLiquidButton(
                            label = "使用 ChatGPT 继续",
                            backdrop = backdrop,
                            enabled = !signingOut,
                            onClick = {
                                actionMessage = null
                                onBeforeLogin()
                                ChatGptAuthManager.startLogin(context, requestConsent = signedIn)
                            }
                        )
                    }
                    if (signedIn) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogLiquidButton(
                                label = if (auth.isLoadingModels) "刷新中" else "刷新连接状态",
                                backdrop = backdrop,
                                enabled = !auth.isLoadingModels && !auth.isSigningIn && !signingOut,
                                iconRes = R.drawable.ic_refresh,
                                onClick = { scope.launch { refreshConnection() } }
                            )
                            DialogLiquidButton(
                                label = if (signingOut) "退出中" else "退出登录",
                                backdrop = backdrop,
                                enabled = !signingOut && !auth.isSigningIn,
                                onClick = {
                                    signingOut = true
                                    scope.launch {
                                        try {
                                            actionMessage = ChatGptAuthManager.signOut(context).message
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            actionMessage = "退出未完成，请重试。"
                                        } finally {
                                            signingOut = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                    connectionCheckedAt?.let { timestamp ->
                        Text(
                            "${if (connectionCheckFailed) "上次成功连接检查（已过期）" else "连接检查"}：${DateFormat.getTimeFormat(context).format(Date(timestamp))}；此操作不读取剩余额度。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    (actionMessage ?: auth.errorMessage)?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        GlassPreferenceSection("账号可用模型") {
            SettingsGroup(backdrop = backdrop, config = config, modifier = Modifier.fillMaxWidth()) {
                if (planEnabled && models.isNotEmpty()) {
                    SleepDownLiquidDropdownPreference(
                        items = models.map { "${it.displayName} · ${it.slug}" },
                        selectedIndex = models.indexOfFirst { it.slug == selectedModel }.coerceAtLeast(0),
                        title = "模型",
                        backdrop = backdrop,
                        config = config,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        maxHeight = 318.dp,
                        onExpandedChange = {},
                        onSelectedIndexChange = { index -> onModelsChanged(models, models[index.coerceIn(models.indices)]) }
                    )
                } else {
                    SettingsInfoRow(
                        "模型列表",
                        if (auth.isLoadingModels) "正在读取账号可用模型…"
                        else if (planEnabled) "尚无可用模型，请刷新连接状态。"
                        else "完成登录和套餐调用授权后，显示该账号实际可用的模型。"
                    )
                }
                SettingsDivider()
                SettingsInfoRow(
                    "登录预览说明",
                    "本功能用于个人、本机登录预览。账号资格和可用套餐可能变化，Free 账号目前不支持此调用授权；这不代表已获批面向所有用户分发。登录凭据只在本机加密保存，不随课表备份迁移。"
                )
            }
        }
    }
}
