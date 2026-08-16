# 1.1.5 Migration Bridge Worklog

最后更新：2026-08-11
当前阶段：Phase D/E — 全量导出、独立预览、Replace/marker/resume 和 SAF/UI 已实现；真实导出已验收，最终 Replace 与故障注入仍待验证

## 已完成

- 读取附件中的 1.1.5 迁移桥需求，确认目标是用户数据迁移，不是 2.0 重构。
- 保持应用身份与版本不变：`com.example.courseschedule`、`versionCode=24`、`versionName=1.1.4`。
- 保留现有 dirty worktree 和未跟踪文件；未执行 reset、checkout、清理、提交、推送或发布。
- 通过已连接的 GitHub 仓库/Release 能力确认官方 v1.1.4 资产，并下载到 `tmp/SleepDown-1.1.4-official.apk` 做签名核验。
- 完成官方包与当前本地 Release 候选的 package/version/certificate 对比；证书 SHA-256 相同，签名没有形成当前阻塞项。
- 全量盘点 Room v34、9 张表、DAO/Repository 边界、SharedPreferences、私有文件、URI、FileProvider、WebView/Cookie、Keystore、widget/notification/alarm/system state、schema snapshots、迁移测试和相关 Git 历史。
- 新增：
  - `docs/migration/RELEASE_SIGNING_AUDIT.md`
  - `docs/migration/PERSISTENCE_AUDIT.md`
- Phase B 冻结：
  - `docs/migration/BACKUP_FORMAT_V1.md`
  - `docs/migration/MIGRATION_TEST_PLAN.md`
  - `docs/migration/POST_2_0_TECH_DEBT.md`
- Phase C 实现：
  - `app/src/main/java/com/example/courseschedule/BackupFormat.kt`
  - `app/src/main/java/com/example/courseschedule/BackupCodec.kt`
  - `app/src/main/java/com/example/courseschedule/BackupAssetStager.kt`
  - `app/src/test/java/com/example/courseschedule/BackupCodecTest.kt`
- Phase D 只读/纯映射实现：
  - `app/src/main/java/com/example/courseschedule/BackupExport.kt`
  - `app/src/main/java/com/example/courseschedule/BackupImportPlan.kt`
  - `app/src/main/java/com/example/courseschedule/BackupPreferencesReader.kt`
  - `app/src/main/java/com/example/courseschedule/BackupAssetSourceReader.kt`
  - `app/src/main/java/com/example/courseschedule/BackupExportService.kt`
  - `app/src/main/java/com/example/courseschedule/BackupRoomRestore.kt`
  - `app/src/main/java/com/example/courseschedule/BackupPrivateAssetRestorer.kt`
  - `app/src/main/java/com/example/courseschedule/BackupRestoreJournal.kt`
  - `app/src/main/java/com/example/courseschedule/BackupRestoreService.kt`
  - `app/src/main/java/com/example/courseschedule/BackupPreferencesApplier.kt`
  - `app/src/main/java/com/example/courseschedule/BackupPreview.kt`
  - `app/src/main/java/com/example/courseschedule/BackupRestoreSettingsUi.kt`
  - `app/src/test/java/com/example/courseschedule/BackupExportImportPlanTest.kt`
  - `app/src/test/java/com/example/courseschedule/BackupRoomRestoreTest.kt`
  - `app/src/test/java/com/example/courseschedule/BackupPreviewReportTest.kt`
- 补齐 `aiImportHistoryRetentionDays` 偏好映射、原子偏好应用、导出目标截断写入和恢复完成后的默认资源/提醒/小组件刷新。
- 设置页备份文案改为用户可读说明；选择备份后进入独立“恢复预览”二级页面，成功预览不再重复显示进度状态，只有一个继续恢复入口。
- 新增 `BackupSettingsCompletenessTest`，覆盖 9 张 Room 表、ScheduleConfig/小组件外观字段和全非默认设置 round-trip。

## 关键结论

- Room 是结构化主库，但 `ScheduleRepository.snapshot()` 不是全量备份 API。
- 壁纸、组件壁纸、Agent 图片附件和 AI 导入历史进度 JSON 必须按资产处理，不能复制原始 URI。
- API key、教务 Cookie、Keystore 密钥、WebView 登录态、系统权限、闹钟和真实 widget ID 不进入普通备份。
- 现有 Room 历史迁移和开库前结构修复必须保持；1.1.5 备份协议应独立于 Room 文件和 Android Auto Backup。

## Phase B/C 决策记录

- `.sleepdown` 是独立 ZIP 协议，固定 `manifest.json`、`data.json`、`preferences.json`、`checksums.json` 和 manifest-listed assets；不复制 Room DB、SharedPreferences XML、URI、WebView/Cookie、Keystore 或系统状态。
- stable ID 使用 `prefix_UUID`，且在整个 archive 全局唯一；period/period-scheme time 使用父对象内的自然 key，不把 Room auto ID 带入协议。
- asset manifest 记录 category/purpose/path/MIME/byteLength/SHA-256/present/optional/owner/missingReason；已知缺失保留为 warning，不伪造默认资源。
- codec 限制原始 ZIP 128 MiB、单 JSON 8 MiB、单 asset 32 MiB、解压总量 256 MiB、asset 256 个、entry 1024 个和已知压缩比 100:1；未知版本/entry、重复/path traversal、checksum/MIME/secret/引用错误均 fail-fast。
- 恢复边界冻结为 `READ_ONLY → VALIDATED → STAGED → DB_COMMITTED → PREFS_COMMITTED → FINALIZED`。当前 Phase C 只负责 codec 和 operation staging；Room Replace、SharedPreferences marker apply、新私有 URI、cleanup/resume 留在 Phase D。
- Phase D 当前边界：`BackupRoomSnapshotReader` 在一个 Room transaction 内读取 9 张表；导出 mapper 将所有 Room auto ID remap 为 archive stable ID；ImportPlan 为 Replace 生成无碰撞目标 ID，并对缺失资源和真实 widget 实例生成 warning；`BackupExportService` 只读编排资源；`BackupRoomReplaceTransaction` 在一个 Room transaction 内 Replace；资源先落新私有 URI；`BackupRestoreService` 用 payload+marker 处理 DB commit 前后窗口，非敏感 Preferences 在 DB commit 后可重入 apply。
- Phase E 当前边界：设置页使用 SAF `CreateDocument`/`OpenDocument`；导入严格经过 read/decode/validate/Preview，再由 `LiquidAlertDialog` 要求显式确认 Replace；导出直接写入 SAF OutputStream，不写 Room；真实 content URI、进程重建和 Android transaction fault injection 仍未验证。

## 当前验证状态

- 已核验官方 v1.1.4 Release APK SHA-256：`A0D83A3BA9B61737E263817D38A71F48A64449FA36637AAB9FA2EF26982309BB`。
- 已核验官方与当前本地候选证书 SHA-256：`9ac88e98f545cbedc8b91b3d45163229ceb5f3289bd4bf1d7f7bfab5fa8e27dc`。
- Phase C `BackupCodecTest` 已运行 23 项：全部通过；覆盖空 App/round-trip、fixed entry order、missing asset/entry、重复 entry、checksum、未知版本/entry、坏 JSON/CRC、截断 ZIP/EOCD、Zip Slip、JSON/asset 大小声明限制、压缩比、secret key、全局 stable ID、MIME/owner、资源 staging 目录边界、幂等冲突和输入流生命周期。
- Phase C 专项命令：`.\gradlew.bat testDebugUnitTest --tests com.example.courseschedule.BackupCodecTest --console=plain`，`BUILD SUCCESSFUL`。
- Phase D `BackupExportImportPlanTest` 已运行 5 项，`BackupRoomRestoreTest` 4 项，`BackupPreviewReportTest` 1 项：全部通过，覆盖非连续 Room ID、全量关系、Agent marker 重建、缺失资源、active fallback、Preview、marker payload 和非法 operationId。
- `compileDebugKotlin` 成功；全量 JVM 命令：`.\gradlew.bat testDebugUnitTest --console=plain`，42 个 suite、220 项通过，`failures=0`、`errors=0`。
- 真机导出生成 8,329,256 字节 `.sleepdown`：13 个 ZIP entry、9 个资源全部存在且 SHA-256 全部匹配；备份包含 4 份课表、69 门课程、54 个节次、4 套作息和 5 份小组件外观，偏好包含 AI 导入历史保留天数，未发现 API Key 等秘密字段。
- 最终 SAF/UI、关于应用与 Custom Tab 改动通过 `compileDebugKotlin` 和 Release/R8/资源压缩/lintVital 构建；关于应用保留 MIUIX 卡片行样式，浅色页面按参考图改为高明度低饱和的淡紫粉背景与近乳白卡片；Hero 与内容处于同一 LazyColumn，快速缩至 `72%` 并在卡片接近文字前的 `36%–52%` 滚动区间完全淡出，不再使用会产生硬边的独立背景层；产品图卡改用 MIUIX Squircle 连续曲率圆角且渐变模糊降至 `7dp`；首页与全部 MIUIX 设置顶栏渐变模糊由 `18dp` 统一降至 `12dp`，普通玻璃与壁纸模糊未改；设置首页应用名称与图标间距减少 `6dp`。“下载新版”直达 GitHub Releases。Release APK 为 8,838,890 字节，SHA-256 `A518127D2C73F99F97B5D389F57B2C590DAC573DDCB678D6217AFA489883C2E0`；已通过 `adb install -r` 覆盖安装到 `192.168.1.4:39803`，package/version 为 `com.example.courseschedule` / `1.1.4 / 24`，`firstInstallTime=2026-07-20 03:31:37` 未变化。按用户要求未执行最终 Replace，也未继续代操作界面；Android Room/Preferences transaction crash-window 和 1.1.4→1.1.5 覆盖升级仍未运行。

## 下一步

1. 完成 Phase C 自审：决定是否补跑 128 MiB/256 MiB 级压力样本；保持版本和 Room schema 不变。
2. 在 Android/instrumented 环境验证已接入的 SAF select → validate → Preview → explicit Replace confirm 流程，以及 Room rollback、marker crash-window、真实 URI 读取/新 URI 恢复和进程重建。
3. 在真实设备验证空目标恢复、非空目标 Replace、取消、资源/偏好边界和失败后 resume；保持版本和 Room schema 不变。
4. 完成 UI、安全、真实设备和 1.1.4→1.1.5 覆盖升级验证后，才把版本改为 1.1.5 / versionCode 25，并重新走签名门槛。
