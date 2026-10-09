package com.xiaomanjun.sleepdownschedule.feature.backup.webdav

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kyant.backdrop.Backdrop
import com.xiaomanjun.sleepdownschedule.AppState
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.app.ui.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.*
import com.xiaomanjun.sleepdownschedule.core.ui.settings.SleepDownLiquidDropdownPreference
import com.xiaomanjun.sleepdownschedule.feature.settings.*
import com.xiaomanjun.sleepdownschedule.feature.backup.*
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun WebDavSettingsScreen(state: AppState, backdrop: Backdrop?, onOpenPreview: (Uri) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CourseScheduleApp
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    // Deliberately not rememberSaveable: no credentials in saved-instance-state bundles.
    var password by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var savedConnection by remember { mutableStateOf<WebDavConnection?>(null) }
    val connectionSaved = savedConnection != null
    var editingConnection by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val automation by WebDavAutomation.state.collectAsState()
    var files by remember { mutableStateOf<List<WebDavEntry>>(emptyList()) }
    var listed by remember { mutableStateOf(false) }
    var filename by remember { mutableStateOf("SleepDown-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.sleepdown") }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var overwrite by remember { mutableStateOf<WebDavEntry?>(null) }
    val editable = loaded && busy == null

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) { WebDavCredentials.read(context) }?.let {
                address = it.address; username = it.username; password = it.password
                savedConnection = it
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            message = "无法解密已保存的连接，请重新填写。"
        } finally { loaded = true }
        WebDavAutomation.load(context)
    }

    fun connection() = WebDavConnection(address.trim(), username, password)
    fun runTask(label: String, settings: WebDavConnection? = savedConnection, action: suspend (WebDavClient) -> Unit) {
        if (!editable) return
        val target = settings ?: return
        busy = label; message = null
        job = scope.launch {
            try {
                val client = WebDavClient(target)
                action(client)
            } catch (cancelled: CancellationException) {
                message = "已取消。远端提交结果如未收到确认，请刷新列表核对。"
                throw cancelled
            } catch (error: Exception) {
                // Do not log server responses, URLs, authentication material or provider exceptions.
                message = (error as? WebDavFailure)?.reason?.message ?: "操作未完成，请检查连接与本机存储后重试。"
            } finally { busy = null; job = null }
        }
    }
    fun connect() {
        val candidate = connection()
        runTask("正在验证连接…", candidate) { client ->
            val remoteFiles = client.list()
            withContext(Dispatchers.IO) { WebDavCredentials.save(context, candidate) }
            savedConnection = candidate
            files = remoteFiles; listed = true
            message = "连接成功"
            focusManager.clearFocus()
            editingConnection = false
        }
    }
    fun upload(existing: WebDavEntry? = null) {
        val name = existing?.name ?: filename.trim()
        if (!WebDavClient.validName(name)) { message = "请输入不含斜杠的 .sleepdown 文件名"; return }
        runTask("正在生成并上传备份…") { client ->
            val temporary = withContext(Dispatchers.IO) { File.createTempFile("webdav-upload-", ".sleepdown", context.cacheDir) }
            try {
                val archive = BackupExportService(app, app.database).export()
                withContext(Dispatchers.IO) { temporary.outputStream().use { BackupCodec.write(it, archive) } }
                currentCoroutineContext().ensureActive()
                client.upload(temporary, name, overwrite = existing != null, etag = existing?.etag)
                WebDavAutomation.update(context) { it.copy(lastUploadAt = System.currentTimeMillis(), lastUploadedName = name) }
                message = "上传完成：$name"
                // A list failure must not misreport a completed commit as a failed upload.
                files = try { client.list().also { listed = true } } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    message = "备份已上传，列表刷新失败，可稍后重试。"; files
                }
            } catch (error: WebDavFailure) {
                if (error.reason == WebDavFailure.Reason.COLLISION) {
                    files = client.list(); listed = true
                    overwrite = files.firstOrNull { it.name == name }
                    message = "远端已有同名备份，请确认覆盖或更换名称。"
                } else throw error
            } finally { withContext(NonCancellable + Dispatchers.IO) { temporary.delete() } }
        }
    }
    fun download(entry: WebDavEntry) = runTask("正在下载并校验备份…") { client ->
        val temporary = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "webdav").apply { mkdirs() }
            directory.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 86_400_000L }
                ?.forEach { it.delete() }
            File.createTempFile("restore-", ".sleepdown", directory)
        }
        var handedOff = false
        try {
            client.download(entry, temporary)
            try { withContext(Dispatchers.IO) { temporary.inputStream().use { BackupCodec.decode(it) } } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                message = "备份损坏、格式不支持或校验失败，本机数据没有变化。"
                return@runTask
            }
            currentCoroutineContext().ensureActive()
            onOpenPreview(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", temporary))
            WebDavAutomation.acknowledge(context, entry)
            handedOff = true
        } finally { if (!handedOff) withContext(NonCancellable + Dispatchers.IO) { temporary.delete() } }
    }

    fun cancelConnectionEdit() {
        job?.cancel()
        address = savedConnection?.address.orEmpty()
        username = savedConnection?.username.orEmpty()
        password = savedConnection?.password.orEmpty()
        message = null
        editingConnection = false
        focusManager.clearFocus()
    }
    BackHandler(enabled = editingConnection && connectionSaved) { cancelConnectionEdit() }
    Crossfade(
        targetState = when { !loaded -> 0; editingConnection || !connectionSaved -> 1; else -> 2 },
        animationSpec = tween(300), label = "webdav-login",
        modifier = Modifier.fillMaxSize()
    ) { page ->
        if (page == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (page == 1) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                top = detailContentTopPadding(), bottom = DockScrollPadding),
                modifier = Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    GlassPreferenceSection("连接 WebDAV") {
                        GlassPreferenceCategory("输入网盘或 NAS 的 WebDAV 连接信息")
                        SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                OutlinedTextField(address, { address = it }, label = { Text("文件夹地址") },
                                    placeholder = { Text("https://…/备份/") }, enabled = editable,
                                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(username, { username = it }, label = { Text("用户名") },
                                    enabled = editable, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                    modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(password, { password = it }, label = { Text("密码 / 应用密码") },
                                    enabled = editable, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth())
                                Button(onClick = { focusManager.clearFocus(); connect() },
                                    enabled = editable && address.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                                    Text(busy ?: "连接并保存")
                                }
                                if (busy != null) TextButton(onClick = { job?.cancel() }) { Text("取消连接") }
                                else if (connectionSaved) TextButton(onClick = { cancelConnectionEdit() }) { Text("返回备份") }
                            }
                        }
                        GlassPreferenceCategory("验证成功后保存，密码仅加密存储在本机")
                        message?.let { GlassPreferenceCategory(it) }
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                top = detailContentTopPadding(), bottom = DockScrollPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    GlassPreferenceSection("WebDAV 连接") {
                        SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                            SettingsNavigationRow(if (connectionSaved) "连接设置" else "配置连接",
                                savedConnection?.address ?: "填写地址、用户名和密码后连接", enabled = editable,
                                onClick = {
                                    address = savedConnection?.address.orEmpty()
                                    username = savedConnection?.username.orEmpty()
                                    password = savedConnection?.password.orEmpty()
                                    message = null; editingConnection = true
                                })
                        }
                        GlassPreferenceCategory("备份全部课表、设置、壁纸、助手记录及 AI 导入历史，与本地 .sleepdown 备份范围一致；API Key、WebDAV 凭据和登录状态不会上传；备份文件未加密；当前开启超级性能模式时，恢复不会自动退出该模式")
                        if (savedConnection?.address?.startsWith("http://", true) == true)
                            GlassPreferenceCategory("当前为 HTTP，传输未加密，建议使用 HTTPS")
                    }
                }
                if (connectionSaved) {
                item {
                    GlassPreferenceSection("自动备份与恢复") {
                        SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                            SettingsToggleRow("自动备份", "联网后按所选频率上传新文件；由系统调度，时间可能延后", automation.backup,
                                backdrop, enabled = editable && connectionSaved, onCheckedChange = {
                                    WebDavAutomation.configure(context, it, automation.restoreCheck, automation.intervalHours)
                                })
                            SettingsDivider()
                            SleepDownLiquidDropdownPreference(items = WebDavAutomation.frequencies.map { it.second },
                                selectedIndex = WebDavAutomation.frequencies.indexOfFirst { it.first == automation.intervalHours }.coerceAtLeast(0),
                                title = "备份频率", backdrop = backdrop, config = state.config, enabled = editable && connectionSaved,
                                onSelectedIndexChange = { index -> WebDavAutomation.frequencies.getOrNull(index)?.let {
                                    WebDavAutomation.configure(context, automation.backup, automation.restoreCheck, it.first)
                                } })
                            SettingsDivider()
                            SettingsToggleRow("自动恢复检查", "定期及打开 App 时检查新备份，提示后仍须预览并确认恢复", automation.restoreCheck,
                                backdrop, enabled = editable && connectionSaved, onCheckedChange = {
                                    WebDavAutomation.configure(context, automation.backup, it, automation.intervalHours)
                                })
                        }
                        GlassPreferenceCategory("自动备份保留历史文件，不自动删除远端备份；请按需在服务端清理；自动检查依赖服务端文件修改时间，无有效时间的备份仍可手动恢复")
                        if (automation.status.isNotBlank()) GlassPreferenceCategory(automation.status)
                        automation.pending?.takeIf { automation.restoreCheck }?.let { entry ->
                            SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                                SettingsNavigationRow("发现远端备份", entry.name + " · 下载后预览并确认恢复", enabled = editable && connectionSaved,
                                    onClick = { download(entry) })
                            }
                        }
                    }
                }
                item {
                    GlassPreferenceSection("上传当前数据") {
                        SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                            SettingsTextFieldRow("文件名", filename, { filename = it }, enabled = editable)
                            SettingsDivider()
                            SettingsNavigationRow("手动上传", "先上传临时文件，完成后提交为备份；同名文件需要确认", enabled = editable,
                                onClick = { val existing = files.firstOrNull { it.name == filename.trim() }
                                    if (existing != null) overwrite = existing else upload() })
                        }
                    }
                }
                item {
                    SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                        SettingsNavigationRow("远端备份", if (listed) "${files.size} 份 · 点击刷新" else "点击获取列表", enabled = editable,
                            onClick = { runTask("正在获取列表…") { client -> files = client.list(); listed = true; message = "列表已更新" } })
                    }
                }
                items(files, key = { it.name }) { entry ->
                    SettingsGroup(backdrop, state.config, Modifier.fillMaxWidth()) {
                        SettingsNavigationRow(entry.name,
                            listOfNotNull(entry.size?.let { "${it / 1024} KB" }, entry.modified, "下载后检查并确认恢复").joinToString(" · "),
                            enabled = editable, onClick = { download(entry) })
                    }
                }
                }
                item {
                    busy?.let { GlassPreferenceCategory(it); TextButton(onClick = { job?.cancel() }) { Text("取消") } }
                    message?.let { GlassPreferenceCategory(it) }
                }
            }
        }
    }
    overwrite?.let { entry ->
        LiquidAlertDialog(title = "覆盖远端备份？", message = "将用当前数据替换「${entry.name}」。该远端文件原有内容将被覆盖，本机数据不会改变。",
            actions = listOf(LiquidAlertAction("取消", LiquidAlertActionStyle.Secondary) { overwrite = null },
                LiquidAlertAction("覆盖", LiquidAlertActionStyle.Destructive) { overwrite = null; upload(entry) }),
            backdrop = backdrop, config = state.config, onDismissRequest = { overwrite = null })
    }
}
