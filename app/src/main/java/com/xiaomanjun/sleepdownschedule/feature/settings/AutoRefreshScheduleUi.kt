package com.xiaomanjun.sleepdownschedule.feature.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.app.ui.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.app.ui.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.core.wallpaper.loadWallpaperSource
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchoolPickerScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWarehouseUpdater
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.describeAdapterRefresh
import com.xiaomanjun.sleepdownschedule.feature.importing.toIntentKey
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.*
import com.xiaomanjun.sleepdownschedule.glass.GlassBackdropDomain
import com.xiaomanjun.sleepdownschedule.glass.glassBackdropProducer
import com.xiaomanjun.sleepdownschedule.glass.rememberGlassLayerBackdrop
import com.xiaomanjun.sleepdownschedule.app.ui.settingsPageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun AutoRefreshScheduleSettingsScreen(
    state: AppState,
    backdrop: Backdrop?,
    warehouseRefreshRequest: Int = 0
) {
    val context = LocalContext.current
    val profile by AutoRefreshScheduleStore.observe(context).collectAsState()
    var adapters by remember { mutableStateOf<List<EduAdapter>?>(null) }
    var apiAdapters by remember { mutableStateOf<List<EduAdapter>>(emptyList()) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var showRefreshingDialog by remember { mutableStateOf(false) }
    var adapterRefreshMessage by remember { mutableStateOf<String?>(null) }
    var handledWarehouseRefreshRequest by remember { mutableIntStateOf(warehouseRefreshRequest) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(retry) {
        catalogError = null
        runCatching {
            apiAdapters = ShiguangApiAdapterCatalog.loadSupported(context)
            ShiguangApiAdapterCatalog.loadLoginAdapters(context)
        }
            .onSuccess { adapters = it }
            .onFailure { catalogError = "学校列表读取失败，请重试" }
    }
    LaunchedEffect(warehouseRefreshRequest) {
        if (warehouseRefreshRequest == handledWarehouseRefreshRequest || manualRefreshing) return@LaunchedEffect
        handledWarehouseRefreshRequest = warehouseRefreshRequest
        manualRefreshing = true
        showRefreshingDialog = true
        scope.launch {
            try {
                val previousAdapters = adapters.orEmpty()
                val result = ShiguangWarehouseUpdater.refresh(context)
                val refreshedAdapters = ShiguangApiAdapterCatalog.loadLoginAdapters(context)
                apiAdapters = ShiguangApiAdapterCatalog.loadSupported(context)
                adapters = refreshedAdapters
                catalogError = null
                adapterRefreshMessage = describeAdapterRefresh(previousAdapters, refreshedAdapters, result.changed)
            } catch (error: Exception) {
                adapterRefreshMessage = "更新失败，继续使用当前适配列表：${error.message ?: "网络请求失败"}"
            } finally {
                manualRefreshing = false
                showRefreshingDialog = false
            }
        }
    }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val saved = profile
    if (saved != null) {
        val savedAdapter = ShiguangApiAdapterCatalog.find(
            if (saved.sessionOnly) adapters.orEmpty() else apiAdapters, saved.schoolId, saved.adapterId
        )
        AutoRefreshDashboardContent(
            state = state,
            backdrop = backdrop,
            profile = saved.copy(
                adapterName = savedAdapter?.adapterName
                    ?: saved.adapterName.removeSuffix("（纯接口试验）").removeSuffix("（纯接口实验）")
            ),
            onReconnect = {
                savedAdapter?.let {
                    authLauncher.launch(SwuUnifiedAuthActivity.intent(context, it, saved.scheduleId))
                }
            },
            reconnectAvailable = savedAdapter != null,
            onManualRefresh = {
                savedAdapter?.let {
                    authLauncher.launch(android.content.Intent(context, com.xiaomanjun.sleepdownschedule.EduImportActivity::class.java)
                        .putExtra("edu_adapter", it.toIntentKey())
                        .putExtra(AutoRefreshWebSession.RestoreSessionExtra, true))
                }
            },
            connectionStatus = when {
                catalogError != null -> catalogError!!
                adapters == null -> "正在检查学校支持状态"
                else -> "该入口已更新，请退出后重新选择学校"
            },
            onRetryCatalog = { retry++ }
        )
    } else if (adapters != null && catalogError == null) {
        EduSchoolPickerScreen(
            state = state,
            backdrop = backdrop,
            availableAdapters = adapters,
            adapterBadge = {
                if (ShiguangApiAdapterCatalog.supportsAutomaticRefresh(it, apiAdapters)) null
                else "可能需要手动刷新"
            },
            onSelect = { selected ->
                val reviewed = ShiguangApiAdapterCatalog.find(apiAdapters, selected.school.id, selected.adapterId)
                authLauncher.launch(SwuUnifiedAuthActivity.intent(context, reviewed ?: selected, state.config.id))
            }
        )
    } else {
        Box(Modifier.fillMaxSize().padding(top = detailContentTopPadding()), contentAlignment = Alignment.Center) {
            if (catalogError == null) CircularProgressIndicator()
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(catalogError!!)
                SettingsActionButton("重新加载", backdrop, onClick = { retry++ })
            }
        }
    }
    if (showRefreshingDialog) LiquidAlertDialog(
        title = "正在更新适配器",
        message = "正在获取最新适配列表，完成后会重新检查可用于自动刷新的学校。",
        actions = listOf(
            LiquidAlertAction("后台继续", LiquidAlertActionStyle.Secondary) {
                showRefreshingDialog = false
            }
        ),
        backdrop = backdrop,
        config = state.config,
        onDismissRequest = { showRefreshingDialog = false }
    )
    adapterRefreshMessage?.let { result ->
        LiquidAlertDialog(
            title = "适配器刷新结果",
            message = result,
            actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) {
                adapterRefreshMessage = null
            }),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { adapterRefreshMessage = null }
        )
    }
}

@Composable
private fun AutoRefreshDashboardContent(
    state: AppState,
    backdrop: Backdrop?,
    profile: AutoRefreshScheduleProfile,
    reconnectAvailable: Boolean,
    connectionStatus: String,
    onRetryCatalog: () -> Unit,
    onManualRefresh: () -> Unit,
    onReconnect: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var message by remember(profile.lastResult) { mutableStateOf(profile.lastResult) }
    var refreshResultMessage by remember { mutableStateOf<String?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    var cropUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cropVisible by rememberSaveable { mutableStateOf(false) }
    var avatar by remember(profile.avatarPath) { mutableStateOf<Bitmap?>(null) }
    val foreground = sleepDownPanelForegroundColor(state.config)
    val pageColor = settingsPageBackground(state.config)
    // Same producer / sibling consumer arrangement as the notification settings floating action.
    val contentBackdrop = rememberGlassLayerBackdrop(GlassBackdropDomain.Content, "auto-refresh-settings-body") {
        drawRect(pageColor)
        drawContent()
    }
    LaunchedEffect(profile.avatarPath) {
        avatar = profile.avatarPath?.let { path ->
            withContext(Dispatchers.IO) {
                loadWallpaperSource(context.applicationContext, Uri.fromFile(File(path)), 512).bitmap
            }
        }
    }
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cropUri = uri.toString()
            cropVisible = true
        }
    }
    fun updateProfile(transform: (AutoRefreshScheduleProfile) -> AutoRefreshScheduleProfile) {
        runCatching {
            val updated = AutoRefreshScheduleStore.update(context, transform)
            AutoRefreshScheduleWorker.updateSchedule(context, updated)
        }.onFailure { message = "设置保存失败，请重试" }
    }
    val lastRefresh = remember(profile.lastRefreshAt) {
        if (profile.lastRefreshAt <= 0) "尚未刷新"
        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(profile.lastRefreshAt))
    }
    Box(Modifier.fillMaxSize()) {
        SleepDownSecondaryPageList(
            contentTopPadding = detailContentTopPadding() + 12.dp,
            contentBottomPadding = DockScrollPadding,
            modifier = Modifier.fillMaxSize().glassBackdropProducer(contentBackdrop)
        ) {
            item(key = "profile") {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(92.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val image = avatar
                            if (image != null) Image(
                                image.asImageBitmap(), "个人头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                            ) else Icon(
                                MiuixIcons.ContactsCircle, "默认头像", Modifier.size(54.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(profile.schoolName, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, color = foreground)
                        Text(
                            if (profile.sessionOnly) "教务账号 · 登录态已保留"
                            else if (profile.username.isBlank()) "教务账号 · 已连接"
                            else "教务账号 · ${maskAccount(profile.username)}",
                            style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.62f)
                        )
                        DialogLiquidButton(
                            backdrop = backdrop,
                            label = "编辑头像",
                            onClick = { avatarLauncher.launch("image/*") },
                            modifier = Modifier.widthIn(min = 96.dp).height(32.dp),
                            monochromeNeutral = true
                        )
                    }
                    SettingsDivider()
                    SettingsInfoRow("教务系统", profile.adapterName)
                }
            }
            item(key = "refresh") {
                GlassPreferenceSection("课表更新") {
                    SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                        if (profile.sessionOnly) {
                            SettingsInfoRow("刷新方式", "保留登录态，打开教务页面手动刷新")
                        } else SleepDownLiquidDropdownPreference(
                            items = listOf("从不", "每天", "每7天"),
                            selectedIndex = AutoRefreshFrequency.selectedIndex(profile.automatic, profile.frequencyMinutes),
                            title = "自动刷新",
                            summary = if (profile.automatic) "联网时更新，实际时间由系统安排" else "仅在手动刷新时更新课表",
                            backdrop = backdrop,
                            config = state.config,
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                            onSelectedIndexChange = { index ->
                                updateProfile { it.copy(
                                    automatic = index != 0,
                                    frequencyMinutes = if (index == 2) AutoRefreshFrequency.WeeklyMinutes else AutoRefreshFrequency.DailyMinutes
                                ) }
                            }
                        )
                        SettingsDivider()
                        SettingsInfoRow("最近刷新", lastRefresh)
                        Text(message, color = foreground.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            SettingsActionButton(
                                if (profile.sessionOnly) "打开教务手动刷新"
                                else if (refreshing) "正在刷新…" else "立即刷新课表",
                                backdrop,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (profile.sessionOnly) {
                                        if (reconnectAvailable) onManualRefresh() else onRetryCatalog()
                                    } else if (!refreshing) scope.launch {
                                        refreshing = true
                                        try {
                                            message = AutoRefreshScheduleCoordinator.refreshSaved(context).message
                                            refreshResultMessage = message
                                        } finally {
                                            refreshing = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            item(key = "connection") {
                GlassPreferenceSection("登录与连接") {
                    SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                        SettingsNavigationRow(
                            title = "重新登录",
                            subtitle = if (reconnectAvailable) "会话失效时，打开学校登录页重新连接" else connectionStatus,
                            onClick = { if (!refreshing) { if (reconnectAvailable) onReconnect() else onRetryCatalog() } }
                        )
                        SettingsDivider()
                        SettingsInfoRow("校园网络", "部分学校需要校园网或校园 VPN，自动刷新时也需要保持连接。会话失效后请重新登录。")
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(168.dp)
                .background(Brush.verticalGradient(listOf(
                    pageColor.copy(alpha = 0f), pageColor.copy(alpha = 0.36f),
                    pageColor.copy(alpha = 0.70f), pageColor.copy(alpha = 0.94f)
                )))
        )
        SettingsActionButton(
            "退出登录", contentBackdrop, glowing = true, destructive = true,
            onClick = { if (!refreshing) showLogout = true },
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp).fillMaxWidth()
        )
    }
    cropUri?.let { uri ->
        AutoRefreshAvatarCropDialog(
            uri = Uri.parse(uri), show = cropVisible, config = state.config, backdrop = backdrop,
            onDismiss = { cropVisible = false },
            onDismissFinished = { cropUri = null },
            onSaved = { path ->
                val oldPath = profile.avatarPath
                try {
                    checkNotNull(AutoRefreshScheduleStore.update(context) {
                        check(it.schoolId == profile.schoolId && it.adapterId == profile.adapterId && it.scheduleId == profile.scheduleId)
                        it.copy(avatarPath = path)
                    })
                    deleteStoredAvatar(context.filesDir, oldPath)
                    cropVisible = false
                } catch (_: Exception) {
                    deleteStoredAvatar(context.filesDir, path)
                    throw IllegalStateException("头像保存失败，请重试")
                }
            }
        )
    }
    if (showLogout) LiquidAlertDialog(
        title = "退出自动刷新登录？",
        message = "将移除此功能在本机保存的登录信息和头像，已导入的课表会保留。",
        actions = listOf(
            LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { showLogout = false },
            LiquidAlertAction("退出登录", LiquidAlertActionStyle.Destructive) {
                scope.launch {
                    AutoRefreshScheduleCoordinator.logout(context)
                    deleteStoredAvatar(context.filesDir, profile.avatarPath)
                    showLogout = false
                }
            }
        ),
        backdrop = backdrop, config = state.config,
        onDismissRequest = { showLogout = false }
    )
    refreshResultMessage?.let { result ->
        LiquidAlertDialog(
            title = "课表刷新结果",
            message = result,
            actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) {
                refreshResultMessage = null
            }),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { refreshResultMessage = null }
        )
    }
}

private fun maskAccount(value: String): String = when {
    value.length <= 2 -> "••"
    value.length <= 5 -> value.take(1) + "••" + value.takeLast(1)
    else -> value.take(2) + "••••" + value.takeLast(2)
}

private fun deleteStoredAvatar(filesDir: File, path: String?) {
    val target = path?.let(::File) ?: return
    val avatarRoot = File(filesDir, "auto_refresh")
    runCatching {
        if (target.canonicalPath.startsWith(avatarRoot.canonicalPath + File.separator)) target.delete()
    }
}
