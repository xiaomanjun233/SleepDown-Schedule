# SleepDown-Schedule 1.1.5：持久化审计

审计日期：2026-08-10
阶段：Phase A（迁移设计前的只读盘点）
当前代码身份：`com.example.courseschedule`，`versionCode=24`，`versionName=1.1.4`
当前 Room schema：`34`

## 1. 审计结论

SleepDown 的用户数据不是一个单独的 SQLite 文件。当前持久化边界由下面几部分组成：

1. Room 主数据库 `course_schedule.db`：课表、多个课表档案、作息方案、个性化设置、助手消息和桌面组件外观。
2. `SharedPreferences`：应用图标、AI 导入配置、AI 导入历史索引、今日助手选项/记忆、教务登录历史、更新状态、一次性导入桥接、课程提醒和 Live Update 运行态。
3. `filesDir` 私有资源：课表壁纸、组件壁纸、助手图片附件、AI 导入历史的进度 JSON、课表预览图缓存。
4. Android Keystore、WebView/CookieManager、通知/闹钟/组件/权限等系统状态：其中有些是秘密，有些是不可迁移的系统实例。

代码搜索未发现 DataStore、`PreferenceManager` 或第二个 Room/SQLite 数据库。所有普通偏好均通过 `getSharedPreferences(..., MODE_PRIVATE)` 读取。

本阶段的核心判断：

- 1.1.5 必须把 Room 行和用户可恢复的私有资源作为同一个数据集设计，不能只导出当前屏幕上的 `AppState`。
- 不能把旧的 `file://` / `content://` URI 字符串直接写进跨安装备份；必须把资源内容转成备份资产，再在新安装中生成新的私有 URI。
- `scheduleId`、课程自增 `id`、作息方案自增 `id` 和 Android `appWidgetId` 都不能作为跨设备稳定 ID。
- 不能把数据库文件、SharedPreferences XML、Keystore 密文、WebView Cookie 或系统组件状态原样复制作为 `.sleepdown` 协议。
- 当前 `ScheduleRepository.snapshot()` 是给活动课表/UI 使用的快照，不是全量导出 API：它明确把 `allConfigs`、`allPeriods` 留为空列表（`Data.kt:1909-1927`）。导出实现必须新增独立的全量读取边界。

## 2. 分类定义

| 分类 | 含义 | 1.1.5 处理原则 |
| --- | --- | --- |
| `MUST_MIGRATE` | 用户重新安装后应继续拥有的真实数据或设置 | 导出/导入都要有计数、校验和、失败明细；单个资源损坏不能静默覆盖其他数据 |
| `SHOULD_MIGRATE` | 有恢复价值，但可以因稳定 ID、容量或系统限制降级 | 默认携带；无法安全恢复时给出可见 warning |
| `REGENERATE` | 可由数据库或系统重新生成的派生数据 | 不写入协议，导入后重建 |
| `TEMPORARY` | 任务中间态、短期缓存或一次性桥接 | 不迁移 |
| `DO_NOT_MIGRATE` | 秘密、登录态、系统授权或与原包绑定的状态 | 协议中不得出现；导入 UI 明确提示用户重新授权/登录 |

## 3. Room 主库盘点

### 3.1 数据库结构

来源：`app/src/main/java/com/example/courseschedule/Data.kt:419-444`、`app/schemas/com.example.courseschedule.AppDatabase/{32,34}.json`。

| 表 | 关键字段/关系 | 分类 | 迁移备注 |
| --- | --- | --- | --- |
| `schedule_profiles` | `id`、`name`、`isActive` | `MUST_MIGRATE` | `id` 只在源设备稳定；导入时建立 `sourceScheduleId -> targetScheduleId` 映射，并只保留一个明确的 active 标记 |
| `schedule_config` | 以 `id` 对应课表档案；包含学期、提醒、主题、壁纸、玻璃卡片、暗色模式、作息计数和更新偏好 | `MUST_MIGRATE` | 全列都属于用户配置；`wallpaperUri` 只作为资源引用线索，不能原样跨设备恢复 |
| `courses` | 自增 `id`、课程文本、星期、节次/周次 JSON、单双周、`scheduleId` | `MUST_MIGRATE` | 课程 `id` 不稳定；导入后需重新生成并维护旧 ID 到新 ID 的本次导入映射 |
| `periods` | 复合主键 `scheduleId + periodIndex`、起止时间 | `MUST_MIGRATE` | 必须按课表 remap；不能只依赖 `schedule_config` 的计数 |
| `period_schemes` | 自增 `id`、`scheduleId`、名称、模式、四段起始时间、特殊课间/覆盖 JSON | `MUST_MIGRATE` | 作息方案是用户编辑的数据；方案 ID 需独立 remap |
| `period_scheme_times` | 复合主键 `schemeId + periodIndex` | `MUST_MIGRATE` | `schemeId` 使用本次导入的方案映射，不使用源自增 ID |
| `agent_daily_sessions` | `scheduleId + date`、daily pack、模型、生成状态和错误 | `MUST_MIGRATE`（若有行） | 表在 schema 和清理逻辑中存在，但当前源码搜索未发现写入路径；需在实现前确认真实设备是否可能有数据 |
| `agent_messages` | 自增 `id`、`scheduleId`、日期、角色、内容、时间、状态 | `MUST_MIGRATE`（保留期内） | 今日助手对话是用户历史；内容里的 `[[agent_image:<file>]]` 还指向 `filesDir/agent_attachments`，需连同资产改写引用 |
| `widget_appearances` | `variant + appWidgetId`、启用状态、壁纸 URI、裁剪/缩放、模糊和亮度 | `MUST_MIGRATE`（逻辑默认）/ `SHOULD_MIGRATE`（实例行） | `appWidgetId=0` 是逻辑默认外观；真实 Android widget ID 由新设备重新分配，不能照搬 |

Room 类型转换器还负责：课程 `periods/weeks` 列表 JSON、`WeekParity`、通知/主题/作息枚举和 `ScheduleTermState`。转换器对未知枚举有默认回退，备份协议不能依赖这种静默回退来掩盖坏数据，应在导出/导入校验时报告未知值。

### 3.2 DAO 和仓库边界

- `CourseDao` 能读取全量课程，也能按 `scheduleId` 读取；写入使用 `REPLACE`。
- `ConfigDao` 能读取全量配置/节次，但按活动课表的查询也很多；写入配置/节次应在事务中完成。
- `PeriodSchemeDao` 能读取某一课表的方案和方案节次；没有一个面向备份的全量快照对象。
- `ScheduleProfileDao` 能读取全量课表档案和活动档案。
- `WidgetAppearanceDao` 能读取全量外观行；仓库会根据系统当前真实 widget ID 创建、删除和清理实例行。
- `AgentDao` 目前只有按日期观察/读取消息、插入消息、状态更新和按日期删除的方法；没有 `getAllMessages()`、`getAllSessions()` 或备份专用查询。后续不能为了方便从 UI Flow 收集消息。
- `ScheduleRepository` 的关键写入大多使用 `database.withTransaction`，包括导入、配置保存、课表创建/切换/删除和 Agent 计划。恢复流程也必须使用事务或可回滚的 staging 流程。
- `ScheduleRepository.ensureDefaults()` 会补建默认档案、配置、节次和作息。导入前后要明确调用时机，避免默认数据与备份数据混合造成“多出一张空课表”。

## 4. SharedPreferences 盘点

### 4.1 用户设置和历史

| Preferences 文件 | 已发现的 key/内容 | 分类 | 处理规则 |
| --- | --- | --- | --- |
| `app_icon_preferences` | `mode`、`follows_system_dark_mode`、`dark_theme` | `MUST_MIGRATE` | 迁移逻辑偏好；导入后调用 `AppIconManager.applyStoredMode()`，不迁移 PackageManager 中 alias 的 enabled 状态 |
| `ai_import_settings` | 当前 provider、各 provider 的 base URL/model/type、capabilities、endpoint/input/structured-output 模式、模型列表、reasoning effort、自定义 provider JSON、managed free offer decision | `MUST_MIGRATE` | 迁移非秘密配置和自定义 provider；provider ID 应保持逻辑值，若目标版本不识别要 warning 并回退到安全的 none |
| `ai_import_settings` | `encrypted_api_key` 及按 provider 拼接的动态 key | `DO_NOT_MIGRATE` | 这是 API secret 的 AES/GCM 密文；源 Keystore key 不会随备份存在，导出明文或密文都不允许；导入后提示重新填写 |
| `day_agent_preferences` | `has_decision`、`enabled`、`daily_ai_enabled`、`weather_enabled`、`memory_enabled`、`memory`、`memory_turn_day`、`memory_turn_count`、`memory_last_agent_update_day` | `MUST_MIGRATE` | 用户 consent、选项和记忆属于普通用户数据；计数可迁移，也可在导入后安全归零并在报告中注明 |
| `day_agent_preferences` | `applied_actions_<scheduleId>` StringSet | `SHOULD_MIGRATE` | 这是 Agent 动作幂等记录，key 绑定源 schedule ID；只有在 schedule 映射和动作 key 语义确认后才 remap，否则清空以避免阻止新课表动作 |
| `day_agent_weather` | `fetched_at`、天气摘要、温度、体感、降水、风速 | `REGENERATE` | 30 分钟天气缓存，依赖位置权限、时间和网络；不迁移 |
| `ai_import_history` | `entries` JSON（最多 10 条）、`retention_days` | `MUST_MIGRATE`（保留范围内） | 这是用户主动保留的导入历史；遵守现有 retention/10 条上限，严格解析并记录坏条目 |
| `edu_login_history` | 加密 `encrypted_entries`，内部含 adapter、标题、URL、Cookie、更新时间（最多 8 条） | `DO_NOT_MIGRATE`（Cookie）；`SHOULD_MIGRATE`（去 Cookie 的元数据，需产品确认） | Cookie 是登录凭据；如果未来迁移学校/URL 历史，只能剥离 Cookie，并把每条标为“需要重新登录” |
| `pending_import_setup` | 一次性 `schedule_id` | `TEMPORARY` | 仅用于 Secondary Activity 返回主流程；不能把源 schedule ID 写进备份 |
| `app_update_state` | `last_check_date`、`latest_tag` | `REGENERATE` | 更新检查缓存；导入后重新检查 |

源码位置：`AppIconMode.kt:33-113`、`AiImport.kt:702-1182`、`DayAgentPreferences.kt:15-127`、`DayAgentService.kt:180-208`、`AiImportHistory.kt:20-174`、`EduLoginHistoryStore.kt:26-121`、`PendingImportSetupStore.kt:7-23`、`AppUpdate.kt:45-225`。

### 4.2 提醒和运行态

| Preferences 文件 | key/内容 | 分类 | 原因 |
| --- | --- | --- | --- |
| `course_alarm_prefs` | `request_codes`、`schedule_signature`、`muted_course`、`muted_until`、`dnd_enabled_by_app` | `REGENERATE` / `DO_NOT_MIGRATE` | request code、PendingIntent、闹钟和 DND 是源设备运行态；数据库中的 `notificationsEnabled`、`notificationMode` 仍属于 `MUST_MIGRATE` |
| `live_update_service_state` | `name`、`time`、`location`、`actions`、`mute_key`、`mute_until`、`chip_mode` | `TEMPORARY` | 前台服务恢复 payload；新设备不应恢复一个已失效的课程 Live Update |

`ScheduleLogic.kt:769-1187,1255-1430` 是上述状态的唯一来源。导入结束后应按新数据库重建提醒，并根据系统权限状态显示“通知、精确闹钟、DND 访问”重新授权提示。

### 4.3 DataStore 搜索结果

在 `app/src/main/java`、`app/src/test` 和 `app/src/androidTest` 中未发现 `DataStore`、`preferencesDataStore` 或 `PreferenceManager`。因此 1.1.5 不需要额外处理 DataStore migration，但协议实现应保留对未来新增存储的审计入口。

## 5. 私有文件和二进制资源

| 路径 | 来源/用途 | 分类 | 迁移规则 |
| --- | --- | --- | --- |
| `filesDir/wallpaper/` | `WallpaperImageStore` 将外部图片采样并写成 WebP；`schedule_config.wallpaperUri` 通常指向这里的 `file://` URI | `MUST_MIGRATE`（被引用时） | 读取实际 bytes 生成 asset；导入到目标 `filesDir/wallpaper/` 后写入新 URI；保留模糊、亮度、portrait/landscape center/scale 和 source width/height |
| `filesDir/widget_wallpaper/` | `WidgetAppearanceRepository` 的组件壁纸资源 | `MUST_MIGRATE`（逻辑默认）/ `SHOULD_MIGRATE`（实例引用） | 同上；按 variant/default 资源建立新 URI；不把原 `appWidgetId` 当稳定主键 |
| `filesDir/agent_attachments/` | 用户发送给今日助手的 PNG/WebP/JPG；消息 content 前置图片 marker | `MUST_MIGRATE`（被消息引用时） | 资产名改为备份内 asset ID，导入时生成新的安全文件名并重写 marker；孤立文件可列入 warning，不应阻塞全部恢复 |
| `filesDir/ai_import_history/` | 每条 AI 导入历史的进度 JSON；含页面文本、用户 prompt、AI reasoning/output、对话轮次和 screenshot base64 | `MUST_MIGRATE`（保留条目） | 与 `ai_import_history.entries` 一一对应；必须限制大小、严格解析；未来格式优先把截图转成资产引用，避免把未经校验的 base64 直接拼接进数据库 |
| `filesDir/schedule_snapshots_v3/` | `schedule-$scheduleId.jpg`，课表选择器/预览的二级视觉缓存 | `REGENERATE` | Room 是源数据；导入后按新 schedule ID 重新生成，不携带旧文件 |
| `cacheDir/updates/` | 更新 APK 下载目录，启动/更新时会清理 | `TEMPORARY` | 不迁移，也不把 APK 当用户备份资产 |
| `cacheDir/shared_schedules/` | ICS 分享文件 | `TEMPORARY` | `IcsScheduleCodec.writeShareFile()` 生成的分享副本，过期后清理 |
| `cacheDir/sleepdown_ai_pdf_*.pdf` | PDF 预览临时文件，`finally` 删除 | `TEMPORARY` | 不迁移 |
| 公共 `Downloads/` | WebView/下载功能的用户主动下载结果 | `DO_NOT_MIGRATE`（不属于应用私有数据） | 由系统/用户自己的文件迁移机制负责，应用备份不扫描公共目录 |

应用源码没有使用 `noBackupFilesDir`。`FileProvider` 的 `cache-path` 和 `files-path` 允许分享临时 ICS、APK 或文件，但 FileProvider URI 是按包名和当前文件生成的临时访问句柄，不是备份数据。

### 5.1 壁纸 URI 的特殊风险

`WallpaperImageStore.persistWallpaperSource()` 对非 `file` URI 会尝试 `takePersistableUriPermission()`，随后将采样图写入应用私有 `wallpaper` 目录；如果复制失败，旧配置仍可能保留原始 `content://`。因此：

- 备份不能只读取 `wallpaperUri` 字符串。
- 导出时要尝试从 `file://` 或 `content://` 读 bytes，并把读失败作为资源 warning。
- 导入后必须写新私有文件 URI，不能依赖源设备的 persisted URI permission。
- `defaultWallpaperStyle=KANBAN` 的内置资源不需要导出；只有用户自定义资源需要导出。

`WidgetAppearanceRepository.persistSelectedImage()` 使用同一套私有文件策略，路径是 `widget_wallpaper`。

## 6. URI、WebView、Keystore 和系统状态

### 6.1 URI 和 Activity 生命周期

- `ImportUi.kt:324-348` 使用 `ActivityResultContracts.OpenDocument()` 读取 ICS/PDF/图片，`loadAiImportFile()` 立即读取 bytes；未发现把源 URI 持久化到数据库或 SharedPreferences 的逻辑。
- `MainActivity.kt:38-104` 接收 `ACTION_VIEW` / `ACTION_SEND` 的 ICS URI，存入进程内 `pendingExternalIcsUri`，消费一次后清空；这是临时导入桥接。
- 壁纸选择是唯一明确调用 `takePersistableUriPermission()` 的路径，而且调用失败会被捕获；这个 Android 系统权限本身不可跨设备搬运。
- `FileProvider` authority 为 `${applicationId}.fileprovider`，由 `res/xml/file_paths.xml` 暴露 `cache` 和 `files`。分享 URI、APK 安装 URI 和 ICS URI 都是临时句柄。

### 6.2 WebView 和 Cookie

教务导入 WebView 启用 JavaScript、DOM storage 和 Cookie，并在页面完成时把当前 URL/Cookie 写入 `EduLoginHistoryStore`。`WebView.releaseSleepDownWebView()` 会清 history 和资源 cache，但故意保留 Cookie 与 DOM storage，以免丢失登录态。

迁移边界：

- WebView 的 Cookie、DOM storage、cache、历史和 WebView provider 内部数据库：`DO_NOT_MIGRATE`。
- `EduLoginHistoryStore` 内保存的 Cookie：`DO_NOT_MIGRATE`，不能因为它被 AES/GCM 包装就当普通配置。
- 学校 adapter、标题、URL 等去秘密元数据：是否携带需要产品确认；默认不自动恢复登录态，导入后要求用户重新登录。
- AI 导入历史里的页面文本、截图和 prompt 是用户主动保存的导入历史，和 WebView 登录 Cookie 分开处理；历史文本不是登录凭据，但可能含敏感课程信息，应随 `.sleepdown` 加密/完整性保护。

### 6.3 Android Keystore

已确认的密钥别名：

| alias | 用途 | 分类 |
| --- | --- | --- |
| `sleepdown_ai_import_key` | 加密 AI provider API key | `DO_NOT_MIGRATE` |
| `sleepdown_edu_login_history_key` | 加密教务登录历史 JSON（含 Cookie） | `DO_NOT_MIGRATE` |

两者都是 Android Keystore 中的 AES/GCM key。密文、IV、alias 名称和原始 key 都不能写入普通备份；导入后通过设置页重新输入用户自定义 API key、重新登录教务系统。官方免费 AI 配置由后端远程下发，本地只缓存服务端密文及其有效期，不持久化或备份明文 API key。

### 6.4 不随应用数据迁移的系统状态

下列状态不是 `.sleepdown` 的普通数据，导入后必须检查并重新创建/请求：

- `POST_NOTIFICATIONS`、精确闹钟、忽略电池优化、DND policy access、位置权限；
- `AlarmManager` alarms、`PendingIntent`、通知 channel 的系统用户修改；
- `AppWidgetManager` 真实 widget 实例、真实 `appWidgetId`、桌面布局；
- Launcher activity-alias 的 PackageManager enabled 状态；
- app task 是否从最近任务隐藏、前台服务运行状态、开机/时间变化注册状态；
- Custom Tabs/Edge、系统浏览器的 Cookie 和登录资料；
- Android Auto Backup（Manifest 目前 `android:allowBackup="true"`）本身。它是系统备份机制，不等同于本项目要定义的 `.sleepdown` 协议，协议实现不能依赖它的 XML/数据库复制结果。

## 7. 数据清理和生命周期约束

`CourseScheduleApp.cleanupPersistedAppData()` 在进程后台任务中执行：

- 清理更新目录、过期分享文件和 PDF 临时文件；
- `repository.ensureDefaults()`；
- 清理未引用的课表壁纸、课表预览图和组件壁纸；
- `DayAgentRepository.cleanup(LocalDate.now())`：删除两天前的助手消息/日会话，标记旧的 pending 消息失败并清理孤立附件。

因此导出需要在数据库/文件资产一致的时点进行，至少要处理以下竞态：

1. 导出读取 Agent 消息时，后台清理可能正删除两天前的数据。
2. 壁纸/组件壁纸清理以 URI 字符串判断引用，导出不能在读取 URI 后才等待文件出现。
3. `ScheduleSnapshotStore` 和 cache 可能在导出期间被重建或删除；它们本来就不应进入协议。
4. 备份失败不能调用现有 cleanup 逻辑来“修复”用户数据，也不能先清空原库再试导入。

建议的实现边界是：短事务读取结构化 rows，随后在受保护的私有文件读取阶段生成资产 manifest；恢复先写临时 staging，再一次性事务导入 rows，最后重建派生缓存/系统状态。

## 8. 数据库迁移考古

`Data.kt:446-973` 注册了从 v1 到 v34 的迁移图，并由 `createAppDatabase()` 在打开数据库前调用 `repairDatabaseFileBeforeRoomOpen()`。功能性迁移摘要如下：

| 版本 | 变化 |
| --- | --- |
| 1 → 2 | 学期开始日期、自动计算周次 |
| 2 → 3 | 通知开关 |
| 3 → 4 | 通知模式 |
| 4 → 5 | 壁纸 URI、模糊、亮度 |
| 5 → 6 | 课程卡片颜色和透明度 |
| 6 → 7 | 周卡片高度、浅色文字 |
| 7 → 8 | 跟随系统暗色、暗色模式 |
| 8 → 9 | 默认壁纸样式 |
| 9 → 10 | 课程卡片模糊 |
| 10 → 11 | 课程卡片玻璃开关 |
| 11 → 12 | 隐藏空周末 |
| 12 → 13 | Dock 对齐 |
| 13 → 14 | 首页模式、Live Update action 开关 |
| 14 → 15 → 16 | Live Update chip mode 增加并把旧 `AUTO`/`NORMAL` 映射到 `LOCATION` |
| 16 → 17 → 18 | 上课时长、课间时长 |
| 18 → 19 | 再次归一化旧 chip mode |
| 19 → 20 | 隐藏最近任务 |
| 20 → 21 → 22 | 多课表：档案、课程 `scheduleId`、复合节次主键、活动档案修复 |
| 22 → 23 | 课程卡片字体缩放 |
| 23 → 24 | 壁纸 portrait/landscape crop 参数和源尺寸 |
| 24 → 25 | `agent_daily_sessions`、`agent_messages` |
| 25 → 26 | 自动检查更新 |
| 26 → 27 | 四段节次计数、`period_schemes`、`period_scheme_times`，从旧节次种子默认作息方案 |
| 27 → 28 | noon 计数和 `period_schemes.noonStartTime` 重建 |
| 28 → 29 | `termState` 并根据日期推导 |
| 29 → 30 | `widget_appearances` |
| 30 → 31 → 32 | 修复组件默认亮度、模糊值 |
| 32 → 34 | 当前注册的 no-op 路径 |
| 33 → 34 | 重建 `courses` 表以匹配最终 schema |

### 8.1 打开前结构修复

`repairDatabaseFileBeforeRoomOpen()` 会检查表/列是否存在；结构异常时进入 `repairSQLiteDatabase()`，创建缺失表、修复课程/节次/作息/Agent 表、重建活动档案，删除 `room_master_table` 并把 SQLite user version 设置为 26（`Data.kt:996-1042`）。

这套修复是历史兼容代码，不是备份协议。1.1.5 不得删除、跳过或用“复制数据库文件”替代它。特别是 v20 之后的多课表和 v33→34 的课程表重建，都是后续升级测试的重点。

## 9. 现有测试与 schema 证据

### 9.1 迁移/数据边界测试

- `app/src/test/.../DatabaseMigrationCoverageTest.kt`：确认每个支持版本都有到 `APP_DATABASE_VERSION` 的前向路径，并禁止反向迁移。
- `app/src/androidTest/.../AppDatabaseMigrationTest.kt`：验证 v32→v34 的用户课程数据、v26/v27→v34 的节次/作息方案、部分 v28 结构修复；使用 `MigrationTestHelper` 和真实 SQLite 文件。
- `app/src/androidTest/.../ScheduleRepositoryDataBoundaryTest.kt`：验证导入写入配置/节次/课程、创建/切换多课表、stale editor/Agent plan 保护、全局设置继承和 active/manager 状态边界。
- `app/src/test/.../ScheduleConvertersTest.kt`：枚举/列表转换容错。
- `app/src/test/.../ScheduleConfigChangeMergeTest.kt`：当前工作树中已有设置草稿合并测试，属于未提交改动，不能在本阶段覆盖。

### 9.2 当前测试文件全量目录清单

以下是本次审计通过 `rg --files app/src/test app/src/androidTest` 看到的现有测试文件；其中只有上面 5 组直接覆盖数据库/持久化边界，其余是相关行为回归证据：

```text
app/src/androidTest/java/com/example/courseschedule/AppDatabaseMigrationTest.kt
app/src/androidTest/java/com/example/courseschedule/ScheduleRepositoryDataBoundaryTest.kt
app/src/test/java/com/example/courseschedule/AgentAttachmentCleanupTest.kt
app/src/test/java/com/example/courseschedule/AgentExecutionTest.kt
app/src/test/java/com/example/courseschedule/AgentToolsTest.kt
app/src/test/java/com/example/courseschedule/AiEduImportProgressSessionTest.kt
app/src/test/java/com/example/courseschedule/AiImportFileLimitTest.kt
app/src/test/java/com/example/courseschedule/AiProviderPresetsTest.kt
app/src/test/java/com/example/courseschedule/AiProviderRequestNormalizationTest.kt
app/src/test/java/com/example/courseschedule/AppIconModeTest.kt
app/src/test/java/com/example/courseschedule/CourseCardColorAssignmentTest.kt
app/src/test/java/com/example/courseschedule/CourseConflictResolutionTest.kt
app/src/test/java/com/example/courseschedule/CourseEditorGroupingTest.kt
app/src/test/java/com/example/courseschedule/CourseEditorWeekSelectionTest.kt
app/src/test/java/com/example/courseschedule/CourseGlassTintTest.kt
app/src/test/java/com/example/courseschedule/DatabaseMigrationCoverageTest.kt
app/src/test/java/com/example/courseschedule/DayAgentActionsTest.kt
app/src/test/java/com/example/courseschedule/DayAgentWeatherCacheTest.kt
app/src/test/java/com/example/courseschedule/DayCourseGroupingTest.kt
app/src/test/java/com/example/courseschedule/DetailMorphBlurTest.kt
app/src/test/java/com/example/courseschedule/DockImeCompensationTest.kt
app/src/test/java/com/example/courseschedule/EduImportMapperTest.kt
app/src/test/java/com/example/courseschedule/EduWebSecurityTest.kt
app/src/test/java/com/example/courseschedule/GiteeAppUpdaterTest.kt
app/src/test/java/com/example/courseschedule/HomeAdaptiveMetricsTest.kt
app/src/test/java/com/example/courseschedule/HomeAnchoredMorphGeometryTest.kt
app/src/test/java/com/example/courseschedule/IcsScheduleCodecTest.kt
app/src/test/java/com/example/courseschedule/ImportedWeekNormalizationTest.kt
app/src/test/java/com/example/courseschedule/LegacyDownloadPermissionTest.kt
app/src/test/java/com/example/courseschedule/NotificationSchedulingTest.kt
app/src/test/java/com/example/courseschedule/PeriodSchemesTest.kt
app/src/test/java/com/example/courseschedule/RenderEffectCompatTest.kt
app/src/test/java/com/example/courseschedule/ScheduleConfigChangeMergeTest.kt
app/src/test/java/com/example/courseschedule/ScheduleConvertersTest.kt
app/src/test/java/com/example/courseschedule/ScheduleTermBoundaryTest.kt
app/src/test/java/com/example/courseschedule/WallpaperCachePolicyTest.kt
app/src/test/java/com/example/courseschedule/WidgetCustomizationLogicTest.kt
app/src/test/java/com/example/courseschedule/WidgetRefreshActionTest.kt
app/src/test/java/com/kyant/backdrop/catalog/components/LiquidSliderInteractionTest.kt
```

### 9.3 Schema 快照

`app/schemas/com.example.courseschedule.AppDatabase/32.json` 和 `34.json` 都存在，当前 identity hash 为 `15d7d86af03f4bec34134bfa2b2802be`。schema JSON 列出的 9 张表与源码实体一致。1.1.5 若只增加独立备份代码，不应无理由改变 Room schema；若确需改变，必须增加 schema snapshot、迁移和升级回归测试。

## 10. Git 历史证据

本地仓库：`D:\Android studio\CourseSchedule`；当前分支 `main`，工作树有用户已有未提交改动，HEAD 为网站部署提交 `cba8bd9`，相对 `origin/main` 显示 behind 2；已发布基线为 `v1.1.4` / `430d807`。本阶段不整理、回滚或覆盖这些改动。

和持久化边界直接相关的历史提交：

| 提交 | 证据和对 1.1.5 的意义 |
| --- | --- |
| `5325675` `fix: protect migrations and web imports` | 加入 32/34 schema 快照、迁移覆盖测试和真实迁移测试；说明历史升级路径是受保护资产 |
| `13bc9da` `fix: harden scheduling and user data handling` | 加固提醒、Agent、AI 导入和文件限制；说明运行态与用户数据边界不能混写 |
| `dacbf3b` `refactor: harden agent and AI import flows` | 引入进程独立的 Agent 偏好、AI 进度/历史结构和自定义 provider；迁移时要处理 prefs 与 files 的组合 |
| `e328bd8` `fix: protect schedule data and background flows` | 增加仓库数据边界保护、壁纸/后台清理和多课表一致性测试 |
| `6a238ee` `fix: prevent widget wallpaper update overload` | 组件壁纸与渲染更新有性能/生命周期约束，恢复后不能一次性触发无限刷新 |
| `59f23dc` `feat: add local ICS schedule import and sharing` | ICS 输入/分享文件是一次性 URI/cache 流程，不是内部持久化源 |
| `365269d` `fix: align timezones and widget refresh lifecycle` | widget/时间相关系统状态需在新设备重建 |
| `603b538` `refactor: remove orphaned widget resources` | 组件资源存在清理和历史孤儿问题，备份不能把未引用缓存当真数据 |
| `430d807` tag `v1.1.4` | 当前正式升级起点；签名证据见 `RELEASE_SIGNING_AUDIT.md` |

GitHub 仓库与 Release 已通过已连接的 GitHub connector 核验；远端源码/Release 证据与本地 tag 盘点互相印证。

## 11. Phase A 未决项和下一阶段入口

以下不是当前审计失败，但在写 `BackupService` 前必须明确：

1. **稳定 ID**：为课表、课程、作息方案、Agent 消息和资源建立仅在备份内稳定的 ID；导入不能保留源自增 ID。
2. **全量读取 API**：补充一次性全量 rows/资产引用读取；不能复用当前 active `AppState`，也不能只依赖 Agent 的 Flow。
3. **AgentDailySession 真实使用情况**：确认是否存在旧版本/真实设备写入行；若存在，必须连同 provider/model/status/错误字段迁移。
4. **AI 导入历史二进制边界**：确认 screenshot base64 在 v1 中是内嵌数据还是资产引用，并设置总大小上限。
5. **教务历史政策**：默认不迁移 Cookie；是否迁移去 Cookie 的 URL/学校元数据需要在格式文档中固定。
6. **widget 实例策略**：迁移逻辑默认外观和资源，导入后由系统新建/重建 widget；不能承诺恢复原桌面实例位置。
7. **资源缺失语义**：壁纸/附件读取失败只影响对应资源，必须进入 warning/report，不得导致整个备份被静默截断。
8. **升级基线**：Phase H 才在真实已安装 v1.1.4 数据上做 `adb install -r` 升级；不能在 Phase A 用卸载重装代替。

本文件只记录审计结果，不新增数据库列、不改现有迁移、不实现 BackupService、不把版本号改为 1.1.5。
