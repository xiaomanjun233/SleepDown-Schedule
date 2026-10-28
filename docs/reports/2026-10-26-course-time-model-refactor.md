# 课程时间与显示重构

## 授权与基线

用户要求先保存当前公开工作区，开一个大 PR，再按拾光最新版制定数据库、导入和显示重构方案；随后确认完整方案并要求“好的 执行吧”。本轮补充要求一并解决 Issue #85 的非本周课程显示建议。

保全 PR 为 [#86](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/86)，基线 `a13199bb`；原开发树、main、其他工作树、stash 和忽略内容保留。重构工作位于 `tmp/course-time-model-room50` / `codex/course-time-model-room50`，依赖 #86。不含合并、安装或发布授权。

2026-10-27：实现及本地验证完成，已提交 [重构 PR #87](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/87)，目标 main，保持 Draft 等待实机验收。核心重构为 `8296564d`，Issue #85 为可独立回退的 `95340850`；以下最终验证对应两笔代码提交合并后的源码，后续交接提交只更新文档。

## 已确认产品语义

- 普通课程跟随课表作息；指定教学楼等独立作息的课程跟随自己的绑定；自定义时间保持明确保存的时刻。
- 一套固定显示基准，特殊课程按真实时刻定位，网格位置不写回课程。
- 公共作息库编辑影响全部引用；课表内部编辑复制为专用作息，显示影响范围。
- 教务、WakeUp、文件、AI 和新 JSON 统一以拾光字段为基础，保留旧 JSON、SDCT1、历史草稿及备份读取。
- 非本周课程以浏览周为准，只在仍有后续有效周次时预告，结课后隐藏；单双周按实际有效周次判断。

## 实施检查点

- [x] 核对已完成的保全 PR #86，并附加到当前聊天。
- [x] Room 49→50：拆分课程周次、节次和时间覆盖，作息节次只读投影，保留一致性回退副本和异常记录。
- [x] 统一按日期解析真实时间，显示结果与原始安排分离；覆盖独立作息、组合作息和调休。
- [x] 对齐拾光 JSON / Bridge / WakeUp，接入预览、确认和事务导入。
- [x] 作息与课程编辑、周视图、Widget、提醒、勿扰及 ICS 使用统一结果。
- [x] 备份版本 2 与版本 1 恢复、旧协议与历史草稿兼容。
- [x] Issue #85 定向回归、迁移/数据边界测试、Release 构建与最终 diff/架构检查。

上游协议基线为拾光 `v2.1.0` / `feabb68f75c4058319b449287980b5f645eb8d61`。实现与本地验证已完成，真机及网络场景验收范围见下文。

## 实现与兼容边界

持久化、交换字段、日期规则优先级和保存语义见 [课程时间模型](../architecture/COURSE_TIME_MODEL.md)。公共 JSON 与 AI 的 IMPORT_SCHEDULE 工具、修复提示共用拾光字段；本地 PATCH 工作区保留旧编辑协议，并保留模板、来源与学分元数据。新分享预先做读取验证，待确认课程显示错误提示，不生成无法恢复的分享内容。

上游 WakeUp 脚本同步自 `ShiGuangSchedule/shiguang_warehouse/resources/GLOBAL_TOOLS/wake_up.js`，已核对 blob `97ee6ac6c8b7365a2bb5dcc5ba8fe8c4800e39b6`，保留 `ownTime` 与第三方来源、许可证。Bridge 支持 `saveComboSchedule` 的调用顺序与不可重复替换基准约束。

架构检查在保全基线和重构树最初均发现 `TimeSlotCourseNavigation.kt` 中 Activity 宿主反向依赖 app。将两个宿主移至 app 装配目录，并保留原组件 FQCN、Manifest 与转场路由；功能层通过稳定组件名称启动。未扩大架构基线；检查现已通过。

## 验证记录

- 初轮扩大回归：38 类 290 项通过，覆盖连续迁移、作息公共引用与专用副本、备份、AI 工作区、旧协议、拾光 JSON、编辑、日期与周视图。
- Windows 原生 SQLite 在长 Robolectric 用例目录中达到路径限制；测试的升级副本目录改为随机短目录，8 项连续迁移通过，生产存储位置不变。
- 追加 WAL 和异常行备份回读后，冻结源码进行最终复验。原始日志在工作树外的本地 `tmp/verify-room50-*.log`，不入库。
- `scripts/check_architecture.py` 通过（599 个生产 Kotlin/Java 文件）；检查器自身 14 项测试通过。
- `node scripts/tests/shiguang-execution.cjs` 与 WakeUp 脚本语法检查通过。

最终冻结源码回归：39 类 **296 项全部通过**（4m29s），包含追加的 WAL 已提交行回读、异常课程导出恢复、组件路由；日志为本机 `tmp/verify-room50-regression-8.log`。

正式 `assembleGithubRelease` **通过**（9m44s），执行 R8、资源压缩、lintVital 与打包，未使用跳过压缩参数。APK V2 签名核验通过：包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7.1` / `35`，7,725,403 字节；SHA-256 为 `b20668c942e2d23b860eabadbdbef31e43d791ad523609483005440667db8de5`。APK 仅在本工作树 `app/build/outputs/apk/github/release/app-github-release.apk`，未入库、上传或安装。构建日志为本机 `tmp/verify-room50-release-final.log`。

构建与测试通过 `scripts/Invoke-AndroidBuild.ps1` 运行，使用 `--console=plain --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8'`。定向测试选择：`CourseTimingMigrationTest`、`CourseTimeResolverTest`、`CoursePeriodAlignmentTest`、`CoursePeriodTimesTest`、`WeekPresentationTest`、`WeekTimeSlotGridTest`、`CanonicalScheduleJsonTest`、`ShiguangImportSessionCustomTimeTest`、`SharedPeriodScheme*Test`、`Backup*Test`、`IcsScheduleCodecTest`、`DatabaseMigrationCoverageTest`、`PeriodSchemeEditorSaveTest`、`AutoRefresh*Test`、`ScheduleRefresh*Test`、`ScheduleImport*Test`、`AgentImportWorkspaceTest`、`CourseEditor*Test`、`AiImportRepairManagerTest`、`TransitionRouteCatalogTest`。

PR 相对 main 包含保全 PR 的祖先；审阅重构增量以 `a13199bb` 为基准。已核对 main 是祖先、没有合并冲突；Draft 的远端检查未作为通过证据。没有执行安装、真实教务/WakeUp 网络导入、真机视觉或性能验收；没有合并或发布。

## 后续覆盖安装

2026-10-27 用户追加“安装”，随后要求 AGENTS.md 同时主动查找有线、无线及现有 ADB 服务。5038 无设备、mDNS 无服务后，在现有 5037 找到唯一 USB 手机 OPPO PLJ110（Android 17 / SDK 37）。核对服务端为 `D:\Oppo Connect\daemon\bin\adb.exe`，使用 Android SDK 客户端显式指定 5037 和目标序列号执行 `install -r`，返回 `Success`；未停止或重启 OPPO 服务。

手机已安装 `base.apk` 的 SHA-256 与本轮产物完全相同（`b20668c9…7db8de5`），版本仍为 1.2.7.1 / 35，设备更新时间 2026-10-27 11:53:38。覆盖安装保留应用数据，未主动启动应用；数据库升级、真实导入和界面效果尚未进行手动实机验收。主目录及本工作树的 AGENTS.md 已同步补充通道查找规则。

## 安装后的界面与构建反馈

用户要求后续默认金标动画，以及详细设置把特殊课程、调休移到作息之前，把当前作息与本课表节次编辑放在一起，公共作息管理移到下方。对应实现将选择器、日期规则、编辑范围说明和节次合到“本课表作息”卡片；公共库独立放在它之后。按节次的两列数字滚轮以圆角胶囊标记中央选中行，滚动、范围和确认语义保持原样。

GitHub flavor 的 `SLEEPDOWN_SHARED_VIEW_KIT_TEST` 默认改为 true，仍保留设备能力与逐路线门控；显式 false 可用于对照。Store flavor 固定 false，并保留原实验功能关闭。AGENTS.md 同步长期构建偏好。

单课编辑弹窗的固定外壳尺寸与保全版及 main 均一致，新增内容仍在内部 LazyColumn 中滚动。用户随后确认只是新增行造成视觉重心变化，无需调整外壳。再次准备安装时，现有 5037 USB 服务与 5038 均无原 OPPO 手机，5038 mDNS 发现并连接另一台无线 25053RP5CC；用户明确仍安装 OPPO PLJ110、稍后重新连接，未安装到另一台设备。

本轮生产代码提交为 `b2c17770`（默认金标）和 `0aeedd59`（排版/选中态）。`compileGithubReleaseKotlin generateStoreReleaseBuildConfig` 通过（2m51s），生成配置核对 GitHub 金标与实验功能均 true、Store 均 false；架构检查 599 文件及 diff 检查通过。此次未改数据/时间规则，未机械重跑此前 296 项数据回归。

不附加任何金标开关运行正式 `assembleGithubRelease` 通过（2m43s，R8、资源压缩、lintVital 和签名齐全），日志在本机 `tmp/verify-room50-ui-gold-release.log`。新 APK 为 `app/build/outputs/apk/github/release/app-github-release.apk`，7,725,403 字节，包名 `com.xiaomanjun.sleepdownschedule`，1.2.7.1 / 35；SHA-256 `13284189a6d91e129aaf3dd691d173fbd4f348718094777d4c80f25d2e4fb84d`。V2 签名证书 SHA-256 仍为 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。新金标包等待指定 OPPO 重新连接后覆盖安装；未进行本轮手动视觉验收，未合并或发布。
