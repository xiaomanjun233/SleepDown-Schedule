# SleepDown Backup Format v1

状态：Phase B 设计冻结；Phase C codec/assets 已按本协议实现，供未来 Phase D Repository importer 和未来 2.0 importer 共用。
扩展名：`.sleepdown`
格式版本：`1`
与 Room `databaseVersion` 完全独立。

## 1. 目标和不变量

`.sleepdown` 是面向用户的完整应用数据迁移包，不是 Room 数据库快照，也不是 Android Auto Backup 的替代 XML。它必须满足：

- 1.1.5 导出的 v1 包可以由未来 2.0 新包解析。
- 不复制 `course_schedule.db`、SQLite WAL/SHM、SharedPreferences XML、Keystore、WebView profile 或 Android 系统状态。
- 备份内部使用独立 stable ID；不暴露或依赖 Room auto-increment primary key、旧包私有路径和 Android `appWidgetId`。
- 备份中只出现明确允许的非敏感配置；API key、Cookie、token、密码、Keystore/signing key 永不进入普通包。
- 资源以 asset manifest + SHA-256 管理；URI 只在源端读取阶段使用，导入后生成新应用私有 URI。
- v1 只实现可靠的完整恢复 / Replace 语义，不实现 merge engine。
- 未知 `formatVersion` fail-fast；不能猜测解析。

## 2. ZIP 容器

ZIP entry 结构固定为：

```text
SleepDown-Backup-YYYYMMDD-HHmm.sleepdown
├── manifest.json
├── data.json
├── preferences.json
├── checksums.json
└── assets/
    ├── schedules/
    ├── wallpapers/
    ├── widgets/
    └── other/
```

Phase C codec 只接受下列 entry：四个固定 JSON entry，以及 manifest 中列出的 `assets/<category>/<assetId>`。目录 entry、未知关键 entry、重复 entry、反斜杠路径、绝对路径和包含 `..` 的路径均拒绝；读取结束还必须存在有效 ZIP end-of-central-directory。

ZIP 不是安全边界。读取器必须先做 entry/path/大小/重复检查，再解析 JSON，并验证 checksums。checksum 只提供完整性检测，不提供加密、身份认证或秘密保护。

### 2.1 建议限制

这些限制是 v1 codec 的协议限制，不取代 Android 文件选择器的 UI 限制：

| 项目 | 限制 |
| --- | ---: |
| ZIP 原始输入累计读取量 | 128 MiB |
| ZIP entry 数量 | 1024 |
| 单个 JSON entry 解压后大小 | 8 MiB |
| 单个 asset 解压后大小 | 32 MiB |
| 全部 entry 解压后累计大小 | 256 MiB |
| asset manifest 数量 | 256 |
| 已知压缩大小时的最大解压比 | 100:1 |
| 单个 stable ID / path / MIME 字段长度 | 256 bytes |

超过限制是损坏/不可信输入错误，不允许静默截断或用默认值继续恢复。
`mediaType` 还必须是非空的 `type/subtype` MIME 形状；未知业务 purpose 不按默认资源处理，而是拒绝 v1 包。

## 3. manifest.json

manifest 的必需字段：

```json
{
  "formatVersion": 1,
  "product": "SleepDown Backup",
  "createdAt": "2026-08-10T12:00:00Z",
  "sourceAppVersionName": "1.1.4",
  "sourceVersionCode": 24,
  "sourcePackageName": "com.example.courseschedule",
  "sourceDatabaseVersion": 34,
  "devicePlatform": "Android",
  "assetCount": 1,
  "missingAssetCount": 1,
  "assets": [
    {
      "assetId": "asset_550e8400-e29b-41d4-a716-446655440000",
      "category": "wallpapers",
      "purpose": "schedule_wallpaper",
      "relativePath": "assets/wallpapers/asset_550e8400-e29b-41d4-a716-446655440000",
      "mediaType": "image/webp",
      "byteLength": 123456,
      "sha256": "...64 lowercase hex...",
      "present": true,
      "optional": false,
      "ownerId": "schedule_550e8400-e29b-41d4-a716-446655440000"
    },
    {
      "assetId": "asset_650e8400-e29b-41d4-a716-446655440000",
      "category": "schedules",
      "purpose": "agent_attachment",
      "relativePath": "assets/schedules/asset_650e8400-e29b-41d4-a716-446655440000",
      "mediaType": "image/png",
      "byteLength": 0,
      "sha256": "",
      "present": false,
      "optional": true,
      "missingReason": "source URI no longer readable"
    }
  ]
}
```

`createdAt` 使用 UTC ISO-8601 字符串。manifest 不记录 Android ID、设备序列号、账号、路径、MAC、IP 或其他不必要的设备唯一标识。

### 3.1 Asset manifest 规则

- `assetId` 是包内稳定 ID，格式为 `asset_<UUID>`；它不是源文件名，也不是 URI。
- `category` 只允许 `schedules`、`wallpapers`、`widgets`、`other`。`purpose` 用于区分 `agent_attachment`、`ai_import_history_context`、`ai_import_screenshot` 等业务用途。
- `relativePath` 必须由 codec 根据 category 和 assetId 生成，不能由外部输入任意指定。
- `ownerId` 可引用一个 backup stable ID，例如 schedule 或 widget appearance；没有 owner 时为 null。
- `present=true` 时 ZIP 中必须有同名 asset entry，`byteLength > 0`，且 `sha256` 必须匹配。
- `present=false` 时 ZIP 中不得有该 entry，必须给出 `missingReason`；它仍然保留在 manifest 中，以便导出报告和导入 Preview 显示缺失资源。
- `assetCount` 与 `missingAssetCount` 分别等于 `present=true` 与 `present=false` 的数量；`assetCount` 不包含缺失资源。
- `optional` 只影响“资源缺失是否阻止用户确认”，不允许绕过 checksum、path 或 JSON 校验。
- `assetId` 和 path 必须唯一；同一 owner/purpose 可以对应多个附件，不能把 owner/purpose 当作唯一键。需要单值的导入槽位（例如 schedule wallpaper）由 Phase D importer 单独拒绝歧义。

当前映射：

| 源数据 | category | purpose | 备注 |
| --- | --- | --- | --- |
| `filesDir/wallpaper` | `wallpapers` | `schedule_wallpaper` | 从 `schedule_config.wallpaperUri` 读取 bytes；保留裁剪/模糊/亮度字段 |
| `filesDir/widget_wallpaper` | `widgets` | `widget_wallpaper` | 迁移逻辑默认外观；真实 widget ID 不进入协议 |
| `filesDir/agent_attachments` | `schedules` | `agent_attachment` | Agent message 的图片 marker 改成 asset ID |
| `filesDir/ai_import_history` | `schedules` | `ai_import_history_context` | 进度 JSON 由 Phase D 转换为不含旧路径的内容 |
| 导入历史中的截图 | `schedules` | `ai_import_screenshot` | 推荐单独 asset；不把大段 base64 继续嵌在 data rows 中 |

## 4. stable ID 模型

stable ID 只在一个 `.sleepdown` 文件内承担关系和引用稳定性；它不要求与源设备下一次导出的 ID 相同，也不要求能直接插入目标 Room。

格式：

```text
schedule_<UUID>
course_<UUID>
scheme_<UUID>
session_<UUID>
message_<UUID>
widget_<UUID>
asset_<UUID>
history_<UUID>
```

导出映射：

```text
source schedule_profiles.id       -> schedule_<UUID>
source courses.id                 -> course_<UUID>
source period_schemes.id          -> scheme_<UUID>
source agent_daily_sessions key   -> session_<UUID>
source agent_messages.id          -> message_<UUID>
source widget appearance row      -> widget_<UUID>
source file/URI                   -> asset_<UUID>
source AI history id              -> history_<UUID>
```

导入映射由目标 Repository 在 staging 阶段建立：

```text
backup stable ID -> target Room ID
```

每个映射必须显式保存于本次 `ImportPlan` 中。不能通过数组顺序、旧 ID 数值或名字猜测关系。`isActive` 只允许一个最终 schedule 为 true；如果 v1 包没有 active schedule，导入计划必须明确选择第一个合法 schedule 并在 warning 中记录，而不是静默伪造多个 active。

## 5. data.json

data.json 是独立于 Room Entity 的长期 DTO。枚举保存为协议字符串，不直接使用 Kotlin enum serializer；未来新增枚举值由 importer 明确处理。

顶层形状：

```json
{
  "dataVersion": 1,
  "schedules": [
    {
      "id": "schedule_<UUID>",
      "name": "我的课表",
      "isActive": true,
      "config": { "...": "完整的 ScheduleConfig 非 URI 字段" },
      "courses": [],
      "periods": [],
      "periodSchemes": [],
      "agentDailySessions": [],
      "agentMessages": []
    }
  ],
  "widgetAppearances": []
}
```

### 5.1 ScheduleConfig DTO

`config` 必须覆盖当前 `ScheduleConfigEntity` 的全部用户字段：

- `totalWeeks`、`currentWeek`、`notificationLeadMinutes`、`termStartDate`、`autoCurrentWeek`、`termState`；
- `notificationsEnabled`、`notificationMode`；
- `wallpaperAssetId`（不再保存 `wallpaperUri`）、`wallpaperBlur`、`wallpaperBrightness`；
- portrait/landscape 的 centerX、centerY、scale，以及 `wallpaperSourceWidth/Height`；
- card color/alpha、course card blur/glass/font scale、week card height、home text/dark mode；
- default wallpaper、hide empty weekends、dock/home mode、Live Update preferences；
- class/break duration、morning/noon/afternoon/evening period count；
- hide from recents、auto check updates。

使用 `wallpaperAssetId=null` 表示没有用户壁纸；内置 `KANBAN` 仍由 `defaultWallpaperStyle` 表达，不需要把 APK 内置资源复制进备份。

### 5.2 课程和节次

`BackupCourse` 包含 stable `id`、name、teacher、location、weekday、periods、weeks、weekParity 和 note；不包含 source `scheduleId`。它位于对应 `BackupSchedule` 内。

`BackupPeriod` 包含 `periodIndex`、`startTime`、`endTime`。`BackupPeriodScheme` 包含 stable `id`、名称、mode、active、时长、四段起始时间、special breaks、overrides 和嵌套的 `times`。方案 time 不重复保存 source scheme ID，父对象 stable ID 已表达关系。

### 5.3 Agent

`agentDailySessions` 迁移 `dailyPackJson`、provider/model、时间、generation status 和 last error；`agentMessages` 迁移 stable message ID、stable session ID、日期、role、plain content、创建时间和 status。

Agent 图片不再把源文件名 marker 当作长期协议。Phase D 导出时把 `[[agent_image:fileName]]` 解出为 `attachmentAssetIds`，导入时先落地资产，再使用目标 `filesDir/agent_attachments` 生成新的安全文件名和 marker。没有对应资产时保留消息文本、记录 warning，不把旧 URI 写入目标库。

## 6. preferences.json

preferences.json 只允许明确定义的非敏感 DTO：

```json
{
  "preferencesVersion": 1,
  "appIcon": {
    "mode": "FOLLOW_DARK_MODE",
    "followsSystemDarkMode": true,
    "darkTheme": false
  },
  "dayAgent": {
    "hasDecision": true,
    "enabled": true,
    "dailyAiEnabled": true,
    "weatherEnabled": true,
    "memoryEnabled": false,
    "memory": "",
    "memoryTurnDay": null,
    "memoryTurnCount": 0,
    "memoryLastAgentUpdateDay": null,
    "appliedActionsBySchedule": {}
  },
  "aiImport": {
    "selectedProviderId": "custom:example",
    "providers": []
  },
  "aiImportHistoryRetentionDays": 30,
  "aiImportHistory": []
}
```

AI provider DTO 可以包含 provider ID、展示名、provider type、非敏感 endpoint、model、capabilities、endpoint style、structured output/input mode、available models、reasoning effort 和用户选择；绝不能包含 `apiKey`、加密 API key、Authorization header 或任何 token。

不进入 preferences.json：`course_alarm_prefs`、`live_update_service_state`、`app_update_state`、`pending_import_setup`、天气缓存、教务 Cookie、WebView state、PackageManager/permission/AlarmManager/AppWidget 实例状态。

AI import history 保留用户主动保存的导入记录；`aiImportHistoryRetentionDays` 显式保存现有 `retention_days`（允许 7、30、90、0），最多 10 条上限作为导出边界。历史 context JSON 中的截图应转为 asset ID，不应携带旧临时 URI。

## 7. checksums.json

checksums.json 结构：

```json
{
  "checksumVersion": 1,
  "algorithm": "SHA-256",
  "entries": {
    "manifest.json": "...",
    "data.json": "...",
    "preferences.json": "...",
    "assets/wallpapers/asset_<UUID>": "..."
  }
}
```

必须覆盖 manifest、data、preferences 和每一个 present asset。`checksums.json` 不自引用，避免 checksum 循环；读取器必须要求所有 required payload 都有 checksum，并拒绝未知 checksum path。sha256 使用 64 位小写十六进制字符串。

## 8. JSON 兼容和错误语义

- `formatVersion != 1`：直接拒绝；高版本不能被 1.1.5 “尽量解析”。
- 必需字段缺失、类型错误、JSON 根类型错误：corrupted backup，拒绝。
- 明确定义为 optional 的新字段：v1 parser 忽略，但不能改变已有字段语义。
- 未知 enum：不能静默改成当前默认值；导入计划应拒绝该 row 或把它列入用户可见 warning，具体字段按 importer 定义。
- ZIP entry 未知、重复、路径不安全、checksum mismatch、超过大小限制：拒绝整个包，不执行恢复。
- 已知资源缺失：如果 manifest 明确 `present=false`，允许进入 Preview；如果 present asset 缺失或 hash 错误，则是损坏包，拒绝。
- 单条可选 history/context 资源缺失：保留主体数据并在 report 中记录；不能把“资源不存在”伪装成默认壁纸。

## 9. 恢复事务和回滚边界

v1 的恢复是 Replace：用户确认后，当前应用数据被备份内容替换。确认前绝不写活动 Room、现有壁纸或用户设置。

### 9.1 两阶段流程

```text
READ_ONLY
  -> VALIDATED
  -> STAGED
  -> DB_COMMITTED
  -> PREFS_COMMITTED
  -> FINALIZED
```

#### READ_ONLY / VALIDATED

读取 SAF URI 到受限输入流，校验 ZIP、JSON、stable IDs、asset manifest、checksums、secret-free preferences 和数量统计，生成 Preview/`ImportPlan`。此阶段不能创建目标 Room 行，不能清空现有数据。

#### STAGED

每次恢复使用随机 `operationId`，目录位于应用私有目录，例如：

```text
filesDir/.sleepdown_restore/<operationId>/
  marker.json
  assets/...
  preferences.json
```

资产先写临时文件，再 fsync/rename 到 operation staging；禁止直接覆盖现有壁纸/组件文件。路径由 codec 生成，输入中的 path 只作为已验证 manifest key。

#### DB_COMMITTED

所有 Room rows 在一次 `database.withTransaction {}` 内 Replace：先删除现有用户 rows，再按 stable-ID 映射插入 profiles/config/periods/courses/schemes/Agent/widget logical rows。任何异常都让 Room transaction 回滚；不能 `catch` 后继续报告成功。

文件系统不能与 SQLite 获得严格 ACID，因此 DB transaction 前把新资源写成唯一的新文件并保留 marker；数据库只引用已校验、已准备好的目标文件。旧用户文件在此之前不删除。

#### PREFS_COMMITTED / FINALIZED

非敏感 SharedPreferences 使用 restore marker 分批、可重入地写入；secret、Cookie、运行态和系统状态保持不变。写入成功后按数据库引用和 marker 清理旧的用户资源/临时目录，重新生成 snapshot、alarms、widget update 和权限提示。

### 9.2 Crash window 和恢复策略

| 崩溃位置 | 下次启动行为 |
| --- | --- |
| STAGED 前/期间 | 现有 DB/prefs 不变；删除未提交 staging 或 orphan 新文件 |
| 新目标文件已生成、DB 未提交 | marker 表明未到 `DB_COMMITTED`；保留现有数据，删除本次新文件/staging |
| DB transaction 内 | Room 自动回滚；新文件不被现有 DB 引用，清理 marker/staging |
| DB 已提交、prefs 未完成 | 不回滚已提交 Room；依据 marker 继续完成同一 operation 的 safe prefs apply，再重建派生状态 |
| prefs 已完成、清理未完成 | 依据 marker 继续清理；用户数据已可用，清理失败必须可见记录 |

这不是声称跨 SQLite/文件系统严格 ACID，而是明确的 write-ahead marker + idempotent resume 语义。没有 marker 的半成品目录不得自动猜测恢复；只能作为 orphan 安全清理对象。

### 9.3 回滚边界

- 用户在 Preview 取消：只删除 staging，当前数据完全不变。
- 验证失败：不触碰 current DB/prefs/files。
- Room transaction 失败：依赖 Room 回滚，不能手动删除当前库。
- DB 已提交后 prefs 失败：优先 resume，不把一部分 Room rows 回滚成旧数据；因为旧资源和新资源的交叉状态更危险。
- 现有用户文件只在 `FINALIZED` 且新 DB 引用验证完成后清理；清理失败不构成数据恢复失败，但必须在 report/日志中记录。

## 10. 普通备份明确不包含的内容

API key、access/refresh token、密码、session secret、Keystore key material、signing key、教务 Cookie、WebView Cookie/DOM/cache/history、通知/定位/精确闹钟/电池优化/DND 权限、真实 AppWidget 实例和 ID、Launcher alias 状态、AlarmManager/PendingIntent、更新 APK/cache、schedule snapshot 均不进入普通包。

导入 Preview 必须把这些列为“需要重新配置/重新授权”，不能暗示它们已经恢复。

## 11. 协议与 Room 的独立性

manifest 中可记录 `sourceDatabaseVersion=34` 作为诊断信息，但 importer 不据此选择复制数据库或跳过 Room migrations。Room 历史 migration/repair 继续服务于 1.1.x 覆盖升级；Backup Format v1 只解析自己的 DTO。
