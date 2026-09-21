package com.xiaomanjun.sleepdownschedule.feature.settings

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.shapes.Capsule
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.app.ui.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.app.ui.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownSecondaryPageList
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.sleepDownPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidCascadingPopup
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidMenuItem
import com.xiaomanjun.sleepdownschedule.feature.importing.EduAdapter
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleCoordinator
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleProfile
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleStore
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.AutoRefreshScheduleWorker
import com.xiaomanjun.sleepdownschedule.feature.schedule.autorefresh.ShiguangApiAdapterCatalog
import com.xiaomanjun.sleepdownschedule.glass.ui.appUsesDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Lock
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun AutoRefreshScheduleSettingsScreen(
    state: AppState,
    backdrop: Backdrop?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(AutoRefreshScheduleStore.load(context)) }
    var adapters by remember { mutableStateOf<List<EduAdapter>>(emptyList()) }
    var loadingAdapters by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        AutoRefreshScheduleStore.observe(context).collectLatest { profile = it }
    }
    LaunchedEffect(Unit) {
        runCatching { ShiguangApiAdapterCatalog.loadSupported(context) }
            .onSuccess { adapters = it }
            .onFailure { catalogError = it.message ?: "支持学校读取失败" }
        loadingAdapters = false
    }

    if (profile == null) {
        AutoRefreshLoginContent(
            state = state,
            backdrop = backdrop,
            adapters = adapters,
            loadingAdapters = loadingAdapters,
            catalogError = catalogError
        )
    } else {
        AutoRefreshDashboardContent(
            state = state,
            backdrop = backdrop,
            profile = profile!!,
            onProfileChange = { next ->
                AutoRefreshScheduleStore.save(context, next)
                AutoRefreshScheduleWorker.updateSchedule(context, next)
            },
            onRefresh = { onRunning, onResult ->
                scope.launch {
                    onRunning(true)
                    val result = AutoRefreshScheduleCoordinator.refreshSaved(context)
                    onResult(result.message)
                    onRunning(false)
                }
            }
        )
    }
}

@Composable
private fun AutoRefreshLoginContent(
    state: AppState,
    backdrop: Backdrop?,
    adapters: List<EduAdapter>,
    loadingAdapters: Boolean,
    catalogError: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember(adapters) { mutableStateOf<EduAdapter?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loggingIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var schoolMenuVisible by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }

    val groups = remember(adapters, selected) {
        adapters.groupBy { it.school.initial.ifBlank { "#" } }.map { (initial, group) ->
            SleepDownLiquidMenuItem(
                key = "initial-$initial",
                text = initial,
                children = group.map { adapter ->
                    SleepDownLiquidMenuItem(
                        key = "${adapter.school.id}/${adapter.adapterId}",
                        text = adapter.school.name,
                        summary = adapter.adapterName.takeUnless { it == adapter.school.name },
                        selected = adapter == selected,
                        onClick = {
                            selected = adapter
                            schoolMenuVisible = false
                            error = null
                        }
                    )
                }
            )
        }
    }

    SleepDownSecondaryPageList(
        contentTopPadding = detailContentTopPadding(),
        contentBottomPadding = DockScrollPadding
    ) {
        item(key = "auto-refresh-login-intro") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("连接教务系统", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "仅展示已核对为接口请求、不会抓取课表 HTML 的拾光适配器。登录成功后，账号、密码与 Cookie 会加密保存在本机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        item(key = "auto-refresh-login-form") {
            GlassPreferenceSection("教务登录") {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { anchorBounds = it.boundsInWindow() }
                    ) {
                        SettingsNavigationRow(
                            title = selected?.school?.name ?: "选择学校",
                            subtitle = when {
                                loadingAdapters -> "正在读取拾光接口适配器…"
                                catalogError != null -> catalogError
                                adapters.isEmpty() -> "暂无满足条件的适配器"
                                selected != null -> selected!!.adapterName
                                else -> "${adapters.size} 个已审计接口适配器"
                            },
                            onClick = { if (!loadingAdapters && adapters.isNotEmpty()) schoolMenuVisible = true }
                        )
                    }
                    SettingsDivider()
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        selected?.description?.takeIf(String::isNotBlank)?.let { description ->
                            Text(
                                "拾光说明：$description",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AutoRefreshLiquidField(
                            value = username,
                            onValueChange = { username = it; error = null },
                            label = "学号 / 账号",
                            icon = MiuixIcons.ContactsCircle,
                            backdrop = backdrop,
                            state = state,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        )
                        AutoRefreshLiquidField(
                            value = password,
                            onValueChange = { password = it; error = null },
                            label = "教务密码",
                            icon = MiuixIcons.Lock,
                            backdrop = backdrop,
                            state = state,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            password = true
                        )
                        error?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        SettingsActionButton(
                            label = if (loggingIn) "正在登录并验证…" else "登录并启用",
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            glowing = true,
                            onClick = {
                                val adapter = selected
                                when {
                                    loggingIn -> Unit
                                    adapter == null -> error = "请先选择学校"
                                    username.isBlank() -> error = "请输入学号或账号"
                                    password.isBlank() -> error = "请输入教务密码"
                                    else -> scope.launch {
                                        loggingIn = true
                                        error = null
                                        val result = AutoRefreshScheduleCoordinator.loginAndRefresh(
                                            context = context,
                                            adapter = adapter,
                                            username = username.trim(),
                                            password = password,
                                            scheduleId = state.config.id
                                        )
                                        if (!result.success) error = result.message
                                        loggingIn = false
                                    }
                                }
                            }
                        )
                        if (loggingIn) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在通过拾光接口读取并校验课表", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item(key = "auto-refresh-network-note") {
            GlassPreferenceSection("使用说明") {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        "校园网络限制",
                        "部分学校教务系统需要内网登录，刷新时可能需要挂代理或连接校园网。若学校启用了验证码、短信或二次认证，首次自动登录也可能需要先在教务导入页完成网页登录。"
                    )
                }
            }
        }
    }

    SleepDownLiquidCascadingPopup(
        show = schoolMenuVisible,
        anchorBounds = anchorBounds,
        items = groups,
        onDismissRequest = { schoolMenuVisible = false },
        backdrop = backdrop,
        config = state.config,
        panelMinWidth = 210.dp,
        menuMaxHeight = 420.dp
    )
}

@Composable
private fun AutoRefreshDashboardContent(
    state: AppState,
    backdrop: Backdrop?,
    profile: AutoRefreshScheduleProfile,
    onProfileChange: (AutoRefreshScheduleProfile) -> Unit,
    onRefresh: ((Boolean) -> Unit, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var transientResult by remember(profile.lastResult) { mutableStateOf(profile.lastResult) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val directory = File(context.filesDir, "auto_refresh").apply { mkdirs() }
                    val destination = File(directory, "avatar.image")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destination.outputStream().use(input::copyTo)
                    } ?: error("无法读取所选图片")
                    destination.absolutePath
                }
            }.onSuccess { path ->
                onProfileChange(profile.copy(avatarPath = path))
                avatarVersion += 1
            }.onFailure { transientResult = it.message ?: "头像保存失败" }
        }
    }
    val avatarBitmap = remember(profile.avatarPath, avatarVersion) {
        profile.avatarPath?.let { path -> runCatching { decodeStoredAvatar(path) }.getOrNull() }
    }
    val frequencyOptions = AutoRefreshScheduleWorker.FrequencyMinutes
    val frequencyLabels = frequencyOptions.map(::formatRefreshFrequency)
    val selectedFrequency = frequencyOptions.indexOf(profile.frequencyMinutes).coerceAtLeast(0)
    val lastRefresh = remember(profile.lastRefreshAt) {
        if (profile.lastRefreshAt <= 0) "尚未刷新" else DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT
        ).format(Date(profile.lastRefreshAt))
    }

    SleepDownSecondaryPageList(
        contentTopPadding = detailContentTopPadding(),
        contentBottomPadding = DockScrollPadding
    ) {
        item(key = "auto-refresh-dashboard-profile") {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .clickable { avatarLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap.asImageBitmap(),
                            contentDescription = "自定义头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = MiuixIcons.ContactsCircle,
                            contentDescription = "选择头像",
                            modifier = Modifier.size(62.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(profile.schoolName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${profile.adapterName} · ${maskAccount(profile.username)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("点按头像可自定义", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "auto-refresh-dashboard-status") {
            GlassPreferenceSection("刷新状态") {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    SettingsInfoRow("最近刷新", "$lastRefresh\n$transientResult")
                    SettingsDivider()
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        SettingsActionButton(
                            label = if (refreshing) "正在刷新…" else "手动刷新课表",
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth(),
                            glowing = true,
                            onClick = {
                                if (!refreshing) {
                                    onRefresh(
                                        { refreshing = it },
                                        { transientResult = it }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        item(key = "auto-refresh-dashboard-schedule") {
            GlassPreferenceSection("自动刷新") {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    SettingsToggleRow(
                        title = "后台自动刷新",
                        subtitle = if (profile.automatic) "仅在设备联网时按设定频率运行" else "当前只会在手动点击时刷新",
                        checked = profile.automatic,
                        backdrop = backdrop,
                        onCheckedChange = { onProfileChange(profile.copy(automatic = it)) }
                    )
                    SettingsDivider()
                    SleepDownLiquidDropdownPreference(
                        items = frequencyLabels,
                        selectedIndex = selectedFrequency,
                        title = "刷新频率",
                        summary = "Android 会结合电量与后台策略安排实际执行时间",
                        backdrop = backdrop,
                        config = state.config,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        onSelectedIndexChange = { index ->
                            frequencyOptions.getOrNull(index)?.let { minutes ->
                                onProfileChange(profile.copy(frequencyMinutes = minutes))
                            }
                        }
                    )
                }
            }
        }
        item(key = "auto-refresh-dashboard-note") {
            GlassPreferenceSection("连接说明") {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    SettingsInfoRow(
                        "可能需要校园网",
                        "部分学校教务系统需要内网登录，后台刷新时可能需要挂代理或连接校园网。系统省电策略也可能推迟后台任务；需要立即更新时可使用上方手动刷新。"
                    )
                }
            }
        }
        item(key = "auto-refresh-dashboard-account") {
            GlassPreferenceSection("账户") {
                SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        SettingsActionButton(
                            label = "退出并清除登录信息",
                            backdrop = backdrop,
                            destructive = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showLogoutDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        LiquidAlertDialog(
            title = "清除自动刷新登录？",
            message = "本机保存的账号、密码、Cookie 和自定义头像会被移除，当前课表内容不会删除。",
            actions = listOf(
                LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { showLogoutDialog = false },
                LiquidAlertAction("清除", LiquidAlertActionStyle.Destructive) {
                    AutoRefreshScheduleWorker.updateSchedule(context, null)
                    deleteStoredAvatar(context.filesDir, profile.avatarPath)
                    AutoRefreshScheduleStore.clear(context)
                    showLogoutDialog = false
                }
            ),
            backdrop = backdrop,
            config = state.config,
            onDismissRequest = { showLogoutDialog = false }
        )
    }
}

@Composable
private fun AutoRefreshLiquidField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    backdrop: Backdrop?,
    state: AppState,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    password: Boolean = false
) {
    val dark = appUsesDarkTheme(state.config)
    val foreground = sleepDownPanelForegroundColor(state.config)
    val surface = if (dark) Color(0xFF16181D).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.22f)
    val shape = Capsule()
    val field: @Composable () -> Unit = {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = foreground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(),
            modifier = Modifier.fillMaxSize(),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = foreground.copy(alpha = 0.72f), modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(label, color = foreground.copy(alpha = 0.48f), style = MaterialTheme.typography.bodyMedium)
                        }
                        inner()
                    }
                }
            }
        )
    }
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        if (backdrop != null) {
            LiquidButton(
                onClick = {},
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                isInteractive = false,
                height = 52.dp,
                surfaceColor = surface,
                contentPadding = PaddingValues(0.dp),
                blurRadius = 5.dp,
                lensHeight = 14.dp,
                lensAmount = 24.dp
            ) {}
        } else {
            Surface(Modifier.fillMaxSize(), shape = shape, color = surface) {}
        }
        field()
    }
}

private fun formatRefreshFrequency(minutes: Long): String = when {
    minutes < 60 -> "每 $minutes 分钟"
    minutes % 60L == 0L -> "每 ${minutes / 60} 小时"
    else -> "每 $minutes 分钟"
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
        val rootPath = avatarRoot.canonicalPath + File.separator
        if (target.canonicalPath.startsWith(rootPath)) target.delete()
    }
}

private fun decodeStoredAvatar(path: String): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
