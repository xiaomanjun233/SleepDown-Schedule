# 本地备份导出零字节调查

## 已确认的证据

- 用户确认问题发生在普通包 `com.xiaomanjun.sleepdownschedule` 的 ChatGPT 修复版，页面提示“备份已保存”。不是并存的独立 ColorOS 包。
- 通过项目 ADB 通道读取文件元数据：两份近期导出的 `.sleepdown` 实际长度均为 0 字节；“0M”不是大小显示取整。未复制或记录备份原始内容。
- 设备现有 `SleepDownBackup` 日志没有保留失败堆栈。后续通过 ADB 读取用户保存的最新报错截图，确认失败发生在快照映射阶段：`schedule config 引用了不存在的 schedule profile`。不能把早期缺少日志误判为文件提供器本身出错。
- 旧流程先打开系统选择器创建目标文件，返回后才生成备份并直接写入 URI。写入和关闭没有抛异常就显示成功，没有回读文件内容；生成失败也可能留下已创建的空文件。

## 修复范围

- `BackupDocumentExport` 先在应用缓存目录生成完整的 v1 文件，再用既有恢复解码器校验；通过后才打开保存位置选择器。
- 使用同一份已校验文件写入目标，关闭输出流后重新打开目标 URI，核对实际字节数及 SHA-256。空文件、截断、内容变化和关闭时失败均不能进入成功提示。
- 生成、压缩、写入及回读在 IO 调度器运行。保存提示显示已验证的文件大小，失败提示包含具体原因。
- 选择器等待期间只保存临时文件名，以支持 Activity 重建。取消或结束后清理本次私有临时文件；失败时尝试删除本次 `CreateDocument` 新建的目标，不触及既有备份。
- 日志增加准备、保存和验证阶段，只增加提供器标识及文件字节数，不记录课程、备份内容或目标文件路径。
- 未更改数据库 schema、BackupFormatV1、恢复确认行为或 WebDAV 传输。

Android 的 `ACTION_CREATE_DOCUMENT` 为新建文件授予读写访问，并不会覆盖现有文件；参见 [Android 文档](https://developer.android.com/training/data-storage/shared/documents-files)。回读使用返回的同一 URI，不依赖可缺失或滞后的大小元数据。

## 验证

- `BackupDocumentExportTest`：8 项通过，包括模拟“丢弃全部写入但不报错”、截断、等长损坏、文件关闭失败、读写打开失败、取消清理与恢复解码。
- `BackupCodecTest`、`BackupExportImportPlanTest`、`SharedPeriodSchemeBackupTest`：51 项通过，保留 v1 格式和共享作息兼容性。
- `compileGithubReleaseKotlin` 通过。
- 真实手机完整导出仍需使用修复包复测，不能把上述模拟测试当作该设备已恢复正常的证据。

本修复单独保留在 `codex/backup-export-empty-20261012`，基于 `main` 的 `9662d103`，不包含学校适配。临时安装候选需要额外保留已安装版本中的 ChatGPT 修复，避免覆盖安装时倒退其他功能。

## 临时安装与待确认项

- 集成测试分支：`codex/backup-chatgpt-test-20261012`，以 ChatGPT 修复 `e8a350e7` 为基础加入备份提交。没有学校适配或私有后端。
- `assembleGithubRelease` 通过，包含 R8、资源压缩与 lintVital。APK 包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7_beta5` / `34`，签名与之前安装包一致。
- APK SHA-256：`cbe86430c61162aeb35e0bfb5ae8df8cf37e94d4b8ab93c230b9e849a48e5678`。
- 已通过 5038 通道在 PLJ110 上 `install -r` 成功，未清除数据，也未自动启动。
- 第一版复测反馈“保存失败，显示具体错误”。00:41 的已保存截图随后证实是无关联课表的配置记录阻止了备份生成；首次安装并没有解决这条数据链路。

## 无关联配置记录的修复

- 导出器此前要求每条 `schedule_config` 都有 `schedule_profiles` 中的父项，而当前界面和导出课表都以 profiles 为准。残留配置会在 ZIP 写入前被拒绝；旧的“先创建目标再生成”流程因此可能留下零字节文件。
- `saveGlobalSettingsWith` 旧实现合并所有配置 ID、所有 profile ID 和事务外读取的 active ID；启动时回退的 ID 1，以及删除/恢复前读取的旧 ID，都可能被写为无父项配置。现在仅在同一事务中更新仍存在的 profiles。
- `ensureScheduleData` 在生成配置或节次前检查 profile 仍存在，拒绝已删除课表的过时编辑回调。
- 第二版曾只容忍无父项配置，58 项测试及 Release 构建通过后安装。APK SHA-256 为 `11fccb91900236797b6f057ea16713e911afab702fb54a0dae77bc43d8c9a55a`。用户 01:03 最新保存截图进一步显示 `period 引用了不存在的 schedule profile`，证明数据库中还存在同类节次残留，单独处理配置不足以解决问题。

## 用户确认的最终读取范围

用户明确选择：“只备份当前课表列表及公共作息、全局设置”。原数据库中的无归属旧记录不删除、不改写。

- `BackupRoomSnapshotReader` 在同一事务中读取所有当前 profiles，包括非活动课表，并仅收集这些课表关联的配置、节次、课程、助手会话及消息。
- 共享作息的 `scheduleId` 是历史来源而非所有权，所以完整保留全部公共作息及时间，包括未被使用、来源课表已删除的公共作息。小组件、非敏感全局偏好和全局 AI 导入历史继续沿用原读取链路。
- Mapper 和 Codec 恢复完整引用校验；不再逐项放宽协议验证，也不凭空恢复旧课表。非法的导出快照仍会被拒绝。
- 新增真实 Room 测试，同时放入无归属配置、节次、课程、助手记录，以及两个有效课表、未使用公共作息和小组件；验证完整 `BackupExportService` → ZIP → 解码的实际链路，并逐表比较导出前后原记录没有变化。
- 最终范围修复的定向测试共 60 项通过：`BackupDocumentExportTest` 8 项、`BackupExportImportPlanTest` 6 项、`BackupRoomSnapshotReaderTest` 3 项、`SharedPeriodSchemeBackupTest` 17 项、`SharedPeriodSchemeRepositoryTest` 26 项。测试使用 `testGithubDebugUnitTest`；当前工程没有 `testGithubReleaseUnitTest`，该入口的首次尝试在任务选择阶段失败后已改用实际入口。
- 最终 `assembleGithubRelease` 通过，包含 R8、资源压缩与 lintVital；签名和包身份已核对。APK 为 `SleepDown-1.2.7_beta5-backup-current-profiles-release.apk`，7,355,766 字节，SHA-256 为 `2582a7a7b1bdc8af1e42f568d854ea125e5d70cce184fee7a410be9e5b24b93e`。
- 已通过 SDK ADB 5038 通道在 PLJ110（Android 17）普通版 `install -r` 成功；未清除应用数据或自动启动。用户随后确认“备份成功，显示非零文件大小”，ADB 元数据确认新文件为 6,327,904 字节。没有读取或复制备份内容。

## ColorOS 版恢复兼容性

- 用户进一步反馈这份备份无法在独立 ColorOS 包恢复，01:21 保存截图只显示通用“无法读取这份备份”提示。已安装 ColorOS 包为 `com.xiaomanjun.sleepdownschedule.coloros`，版本 `1.2.7_beta5` / `34`。
- 对照 ColorOS 仓库基线 `b628164`，`BackupCodec`、`BackupFormat`、`BackupImportPlan` 与普通版文件哈希一致。`AppIdentity` 却只允许当前包名与旧身份 `com.example.courseschedule`，因此普通版来源在 ColorOS 版必定被拒绝；不是本轮修改了备份格式。
- 两个仓库均增加普通版与 ColorOS 正式包名的精确互认，保留旧身份迁移、自身包名及开发包隔离规则。解码、校验和、预览确认和恢复事务保持原实现。
- ColorOS 改动保留在其独立仓库的 `codex/backup-cross-edition-20261012` 工作树，基于当时已安装版本所属的 ColorOS 路线。用户随后要求停止这次独立 ColorOS 构建，未安装，未验证实机恢复预览。

## 2026-10-13 收尾

- 按用户要求先收尾已有改动。普通版分支保留已验证的 ChatGPT 和备份导出修复；精确包名互认单独提交，不混入凝光视效和架构整理。
- 后续凝光合入普通版时保留独立 ColorOS 包的备份来源兼容，以支持用户迁回普通版；不自动导入或覆盖设备上的课表。
- `testGithubDebugUnitTest` 的 `AppIdentityTest` 7 项、`BackupCodecTest` 29 项通过。普通版和 ColorOS 正式包精确互认；相似包名、开发包隔离以及原备份编解码检查保持通过。
- 首次将测试和 Release 放在一次构建中时，2 GB Gradle 堆不足，已停止本轮构建进程。改用单 worker、4 GB 堆分开运行，未更改项目的默认构建配置。
