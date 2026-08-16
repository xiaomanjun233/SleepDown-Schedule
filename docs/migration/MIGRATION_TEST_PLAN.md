# SleepDown 1.1.5 迁移测试计划

状态：Phase B 计划冻结；Phase C codec/assets 已有专项实现与测试，Phase D 已完成 DAO/导出映射/ImportPlan、导出编排、Preview、Room Replace、资源新 URI 和 marker/resume，Phase E 已接入 SAF/UI 入口；剩余真实 Android/发布边界按 Phase D～H 分层推进。
原则：测试迁移语义、真实文件边界和失败路径，不把 mock happy path 当作完成证明。

## 1. 交付门槛和状态定义

| 状态 | 含义 |
| --- | --- |
| `VERIFIED` | 已运行测试/构建并保存结果 |
| `NOT VERIFIED` | 已设计但尚未运行，不能对外声称完成 |
| `PARTIAL` | 已有实现/专项证据，但计划中的完整边界或后续阶段尚未完成 |
| `BLOCKED` | 依赖用户秘密、外部设备状态或真实发布 APK，当前不能安全完成 |

当前 Phase B 设计：`VERIFIED`（文档自审，未声称 codec/upgrade 已完成）。
当前 Phase C codec/asset implementation：`PARTIAL`；`BackupCodecTest` 23 项通过，单 asset/JSON/压缩比/EOCD/CRC 等边界已有证据；累计解压量和原始输入上限仍以实现保护为主，未运行 128 MiB/256 MiB 级压力样本。
1.1.4 → 1.1.5 真实覆盖升级：`NOT VERIFIED`，留在 Phase H。
签名兼容性：官方 v1.1.4 与 1.1.5 正式 APK 证书已 `VERIFIED`；两者证书 SHA-256 一致。

## 2. 现有回归基线

1.1.5 不得破坏已有测试和历史迁移：

- `DatabaseMigrationCoverageTest`：每个支持 Room 版本都能到 v34，迁移只前进。
- `AppDatabaseMigrationTest`：32→34 用户数据、26/27→34 作息方案、部分 v28 repair。
- `ScheduleRepositoryDataBoundaryTest`：多课表、active/manager 状态、stale editor/Agent plan、全局配置继承。
- `ScheduleConvertersTest`、`PeriodSchemesTest`、`ScheduleConfigChangeMergeTest`：配置/方案/转换和当前设置合并逻辑。
- `WallpaperCachePolicyTest`、`AgentAttachmentCleanupTest`、`DayAgentWeatherCacheTest`、`AppIconModeTest`、`WidgetCustomizationLogicTest`、`WidgetRefreshActionTest`：资源、Agent、图标和 widget 生命周期边界。

Phase C 不应修改 Room schema 或历史 migration；如果编译/测试暴露出既有问题，应先区分回归与迁移功能问题，不得顺手清理旧兼容代码。

## 3. Phase B：协议/模型测试

| ID | 场景 | 断言 |
| --- | --- | --- |
| B-01 | 空 App | v1 DTO 可编码；schedules/assets/counts 为 0；decode 等价 |
| B-02 | 单课表 | profile/config/course/period/scheme 关系只通过 stable ID/嵌套关系表达 |
| B-03 | 多课表 | active 只有一个；每个 schedule 的课程、节次、方案不串表 |
| B-04 | stable ID 生成 | 所有 ID 符合 prefix+UUID；不出现 source Room ID 作为协议关系 |
| B-05 | ID remap fixture | source IDs 非连续、重排、从 7 开始时，逻辑关系仍保持 |
| B-06 | 完整 config | 学期、当前周、提醒、暗色、玻璃、卡片、横竖屏裁剪、作息计数全部 round-trip |
| B-07 | scheme topology | 多个 scheme、active scheme、specialBreaks/overrides/times 均保留 |
| B-08 | Agent | session/message/status/date/provider/model 和 attachment asset 引用保留 |
| B-09 | widget | default logical appearance 保留；不出现 `appWidgetId` 字段或真实系统 ID 依赖 |
| B-10 | 非敏感 AI | provider/model/endpoint/capabilities/user choices 保留；API key 字段不存在 |
| B-11 | optional field | 允许增加明确 optional 字段；required 字段缺失拒绝 |
| B-12 | enum evolution | 不认识的 enum 不静默替换为默认值；错误进入明确 report |

## 4. Phase C：codec / ZIP / asset 测试

### 4.1 正常包和 round-trip

| ID | 场景 | 断言 |
| --- | --- | --- |
| C-01 | `encode/decode` | manifest/data/preferences/checksums/assets 全部可逆 |
| C-02 | ZIP entry 顺序 | 输出固定四个 JSON entry，asset path 来自 manifest；重复运行的 fixture 内容稳定 |
| C-03 | manifest asset | present asset 的 length/hash/path 与 bytes 一致 |
| C-04 | missing asset | `present=false` + `missingReason` 可进入 Preview；不会生成虚假 fallback |
| C-05 | checksums | data/preferences/manifest/每个 present asset 验证通过；checksums 自身不要求自引用 |
| C-06 | media types | wallpaper/widget/agent/AI history asset 的 purpose/category 正确 |
| C-07 | empty/small/large valid asset | 在限制内成功；边界值不整数溢出 |

### 4.2 不可信 ZIP 输入

| ID | 场景 | 断言 |
| --- | --- | --- |
| C-08 | unknown formatVersion | fail-fast，不尝试兼容解析，不写入任何目标数据 |
| C-09 | missing required entry | 缺 manifest/data/preferences/checksums 时拒绝 |
| C-10 | corrupt JSON | JSON 根类型、字段类型、required 字段损坏时拒绝 |
| C-11 | corrupt ZIP | 截断 ZIP、无效 central directory、entry CRC/stream 错误时拒绝 |
| C-12 | duplicate entry | 重复固定 entry 或重复 asset entry 时拒绝 |
| C-13 | Zip Slip | `../`、绝对路径、盘符、反斜杠、NUL、目录 entry 时拒绝 |
| C-14 | unknown entry | 未列入协议的关键 entry 或未列入 manifest 的 asset entry 时拒绝 |
| C-15 | checksum mismatch | data/preferences/manifest/asset 任意 hash 不匹配时拒绝 |
| C-16 | oversized JSON | 单 JSON > 8 MiB 时拒绝 |
| C-17 | oversized asset/archive | 单 asset、累计解压或原始输入超过限制时拒绝 |
| C-18 | compression bomb | 已知压缩大小下解压比 > 100:1 时拒绝 |
| C-19 | invalid path/ID/MIME | 非法字符、超长字段、重复 asset ID/path 时拒绝 |
| C-20 | secret field | preferences JSON 出现 apiKey/token/password/cookie/secret 等禁止 key 时拒绝 |

## 5. Phase D：Repository import/export 测试

这些测试在 codec `VERIFIED` 后实现 Repository adapter 时执行；当前已有只读 snapshot/mapper/ImportPlan、偏好/历史/资源 adapter、Preview、Replace service 和 SAF/UI 入口，真实 Android 事务故障注入与 content URI 验证仍未完成：

| ID | 场景 | 断言 |
| --- | --- | --- |
| D-01 | export all schedules | 不使用 active-only `AppState`；所有 profile/config/period/course/scheme 都在包中 |
| D-02 | replace import | 用户确认后当前普通数据由包替换；不实现隐式 merge |
| D-03 | source ID remap | 目标 Room 新 ID 可任意分配，所有关系仍正确 |
| D-04 | current schedule | active 标记唯一且与 Preview/恢复结果一致 |
| D-05 | Agent history | session/message 全量读取 API 正确；消息图片引用与 asset 对齐 |
| D-06 | wallpaper | file/content URI bytes 变成新私有文件 URI；裁剪/亮度/模糊保持 |
| D-07 | missing resource | 缺壁纸/附件只产生 warning，不使非关联课表丢失 |
| D-08 | widget appearance | default/逻辑外观恢复；真实 instance 需要新建/重配提示 |
| D-09 | preferences | 图标/Agent/非敏感 AI 偏好恢复；secret/运行态保持不变 |
| D-10 | empty target | 空 App 恢复完整；不生成额外空默认课表 |
| D-11 | non-empty target | Preview 明确 Replace；取消时当前数据 byte/row 语义不变 |
| D-12 | database rollback | 任意 Room insert/update failure 后 rows 与恢复前一致 |

## 6. 两阶段恢复和 crash-window 测试

使用可注入的 staging/commit seam，不 mock Room 核心事务本身：

| ID | 注入点 | 期望 |
| --- | --- | --- |
| T-01 | ZIP/JSON 校验前失败 | current DB/files/prefs 无变化 |
| T-02 | asset staging 中断 | staging 可清理；current data 无变化 |
| T-03 | final asset 准备后、DB transaction 前崩溃 | marker 判定未提交；清理新 orphan，旧数据保持 |
| T-04 | Room transaction 中抛错 | transaction rollback；不报告成功 |
| T-05 | DB commit 后 prefs apply 前停止 | 下次启动按 marker resume；不猜测回滚成混合状态 |
| T-06 | prefs apply 后 cleanup 中断 | 数据可用；下次启动继续 cleanup，旧文件不被提前删除 |
| T-07 | 用户在 Preview 取消 | staging 删除，current DB/files/prefs 不变 |
| T-08 | 重复 resume 同一 operation | 幂等，不重复 rows/assets，不生成第二套引用 |
| T-09 | stale/foreign marker | 不自动恢复未知 operation；只安全报告/清理未引用 staging |

## 7. Phase E/F/G：UI、安全和全量回归

- 导出使用 SAF `ACTION_CREATE_DOCUMENT`，默认名 `SleepDown-Backup-YYYYMMDD-HHmm.sleepdown`；不申请 `MANAGE_EXTERNAL_STORAGE`。
- 导入必须经过 select → read → validate → parse → Preview → confirm → restore；选择文件后不能直接写库。
- Preview 显示来源版本/package/时间、课表/课程/方案/Agent/widget/asset 数量、缺失资源、不可迁移项、需要重新配置的 secret/权限。
- SAF URI 读取失败、权限撤回、用户取消、进程重建都不能损坏当前数据。
- 安全测试覆盖 Zip Slip、超大文件、未知 entry、重复 entry、checksum mismatch、secret key、恶意 JSON、未来 version。
- 运行既有 unit tests、androidTest、`lintVitalRelease` 和 Release build；不使用 Debug APK 做设备升级结论。

## 8. Phase H：1.1.4 → 1.1.5 覆盖升级

这是最高优先级真实验证：

1. 在已有 1.1.4 安装和用户数据的平板上，先运行 `adb devices -l`。
2. 构建 1.1.5 Release，确认 package/applicationId/namespace 未变、versionCode=25、versionName=1.1.5。
3. `apksigner verify --print-certs`：证书 SHA-256 必须与官方 v1.1.4 审计值一致。
4. `adb install -r` 覆盖安装，不卸载、不清数据。
5. 启动/退出/重启应用，确认 Room migration、课表、设置、壁纸、Agent、widget appearance 均仍可用。
6. 导出 `.sleepdown`，清空/准备独立测试状态后恢复，完成 round-trip 和 Replace/rollback 验证。

任何数据丢失、签名变化、启动 crash 或 migration error 都是 BLOCKER；不能以重新安装、默认值或“看起来能打开”绕过。

## 9. 当前执行状态

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Phase A 审计/官方签名 | `VERIFIED` | 见两份已确认审计文档 |
| Phase B DTO/协议/事务设计 | `VERIFIED` | 本文档与 `BACKUP_FORMAT_V1.md`，实现仍需测试 |
| Phase C codec/assets | `PARTIAL` | codec、manifest、checksum、Zip Slip、EOCD、limits、secret、stable ID 和 operation staging 已实现并有 23 项专项测试；累计解压量/原始输入的极限压力样本尚未运行 |
| Phase D Repository | `PARTIAL` | 已有全量 Room read snapshot、stable ID export mapper、ImportPlan、导出编排、Preview、Replace transaction、URI 恢复和 marker/resume；真实 Android Room rollback 与 content URI 尚未验证 |
| Phase E UI | `PARTIAL` | 已接入 SAF select → validate → Preview → explicit Replace confirm；真实文件选择器、进程重建和设备 UI 尚未验证 |
| Phase F/G 安全/全量测试 | `NOT VERIFIED` | 按本计划执行 |
| Phase H 覆盖升级 | `NOT VERIFIED` | 需真实 1.1.4 设备数据 |
