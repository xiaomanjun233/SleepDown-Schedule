# 版本基线与发布关系

本文说明版本与渠道规则。发布事实以 Git 标签、构建配置和对应 Release 附件为准；以下快照核对于 **2026-10-20**。接手时应刷新实际引用，未完成任务见 [AI_HANDOFF.md](AI_HANDOFF.md)。

## 当前基线

| 项目 | 当前事实 | 核对入口 |
| --- | --- | --- |
| 持续开发入口 | `main`，快照为 `9b6acbb2`，实际版本 `1.2.7.1` / 35 | 上游 main 与该提交的 `app/build.gradle.kts` |
| 最新正式版 | `v1.2.7.1`，源码 `bdc380ee`，双平台正式发布；r1 已撤下，旧标签保留。发布源码已通过 #78 回流 main | [发布与更新兼容核验](reports/2026-10-20-update-revision-compatibility.md)、[架构任务基线](reports/2026-10-20-architecture-review.md) |
| Android 主应用 | `com.xiaomanjun.sleepdownschedule`，正式版 `1.2.7.1` / `35` | `app/build.gradle.kts` 与发布记录 |
| 发行渠道 | `github` 与 `store` 两种 flavor；厂商实验入口由 `SLEEPDOWN_EXPERIMENTAL_FEATURES` 按渠道控制 | `app/build.gradle.kts`、`app/src/github/`、`app/src/store/` |
| 课程组件 | 独立包 `com.suda.yzune.wakeupschedule`；构建版本 `6.0.19` / `259` | `coloros-wakeup-proxy/build.gradle.kts` |
| 历史实验线 | `exp` 与 `v1.2.6-exp*` 保留作为已发布历史，停止作为后续功能基线 | Git 历史与旧 Release |
| 历史集成线 | `develop` 不再是新功能或外部 PR 的默认目标 | 当前维护流程 |

已发布普通应用包含按设备展示的 OPPO/一加/realme 流体云、荣耀 YOYO 建议和小米超级岛课程通知实验入口。这里的“实验功能”是**同一应用中的功能状态**，不是继续发行 `-expN` 独立版本。GitHub flavor 启用入口，商店 flavor 关闭相应实验能力；实际可用性还受设备、系统、授权和组件状态限制。详见[功能地图](FEATURE_MAP.md)和[历史实验线记录](EXP_BRANCH_AND_RELEASES.md)。

2026-10-20 用户明确要求“先合并”，[#78](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/78) 已通过 merge commit `9b6acbb2` 合入 main，包含此前已发布的 1.2.7 与 1.2.7.1 源码及修复。已核对 `bdc380ee` 是远端 main 的祖先，发布基线回流已收口；本次合并没有创建新版本或 Release。后续独立任务从该更新后的 main 开始，具体状态见交接页。

## 正式版、Beta 与实验功能

下表定义后续发布约定；已发布但未回流 main 的历史例外以本页快照和对应发布记录为准。

项目早期曾连续记录 `1.01 beta` 至 `1.10 beta`。这是 2026 年 5—7 月的**历史版本序列**，并非当前 `1.2.6_beta1` 至 `beta10`。逐版内容和能确认的时间锚点见[最初十个 Beta](EARLY_BETA_HISTORY.md)。

| 类型 | 命名和标签 | 代码来源 | 更新日志与发布页 |
| --- | --- | --- | --- |
| 正式版 | `MAJOR.MINOR.PATCH` / `vMAJOR.MINOR.PATCH`，如 `1.2.7` / `v1.2.7`；修复版本为 `MAJOR.MINOR.PATCH-rN` / `vMAJOR.MINOR.PATCH-rN`，如 `1.2.7-r1` | 默认来自已进入 main 的验证提交；历史未回流例外需单独记录，不能据此宣称 main 已更新 | 将本轮有效 Beta 内容归并为一篇正式日志，去重并移除已撤回项目；非预发布。修复版本只写本次修复内容 |
| 普通 Beta | 每轮按下一正式版号命名为 `N.N.N_betaN` / `vN.N.N_betaN` | 同一 `main` 基线的发布候选 | 每个 Beta 保留独立仓库说明和预发布附件，不改写旧标签；正式版应用内只显示归并后的正式日志 |
| 历史 `exp` | `1.2.6-expN` / `v1.2.6-expN` | 当时的 `exp` 分支 | 只作为历史预发布保留，不再用于新的普通功能、当前更新通道或下一版号 |
| 厂商实验功能 | 无独立版本后缀 | 当前 `main` 的隔离实现 | 随对应正式版或 Beta 交付，在说明中标明实验状态与设备条件 |

1.2.7 Beta1 至 Beta5 使用 `versionCode=34`，正式版使用 `versionCode=35`，使 Beta 用户可直接升级正式版。覆盖安装仍须核对**相同 applicationId、签名、版本代码及渠道**；不能仅凭版本名称判断。旧身份 `com.example.courseschedule` 不能直接覆盖安装当前包，需先在旧版导出 `.sleepdown` 再恢复，见[迁移说明](migration/1_2_0_PACKAGE_MIGRATION.md)。

`1.2.7_beta5` 与修订标签 `v1.2.7_beta5-r1` 是本轮最后的预发布，沿用 `versionCode=34`；正式版 `v1.2.7` 使用 `versionCode=35`。Beta1 至 Beta5 的独立发布说明继续保留在仓库和历史 Release 中。课程组件为 `6.0.19` / `259`。

2026-10-10 的首份 Beta5 发布包已按用户要求撤回；周视图“默认”布局入口恢复后，修复包完成构建及覆盖安装，并以新标签 `v1.2.7_beta5-r1` 在 GitHub 与 Gitee 重发为预发布。撤回前 Beta5 用户需手动重新下载覆盖安装；详见[发布与核验记录](2026-10-10-beta5-1.2.7-release.md)。

2026-10-15 正式版 `v1.2.7` 已在 GitHub 与 Gitee 发布，`prerelease=false`，GitHub `latest` 随之由 `v1.2.6` 更新为 `v1.2.7`。发布对象为此前已构建、签名核验的产物，源码提交为 `cfe8776f`，未合并入 `main`；详见[正式版发布记录](2026-10-15-stable-1.2.7-release.md)。

2026-10-19 发布 `v1.2.7-r1`，是 `1.2.7` 的**修复版本，属于正式发布而非预发布**（`prerelease=false`），同样使用 `versionCode=35`，`1.2.7` 用户可直接覆盖升级。该修订沿用既有 `-r1` 后缀约定（对比 `v1.2.7_beta5-r1`），但不再带 Beta 段，因此不会被更新比较逻辑判为预发布；源码为本轮已构建并签名核验的提交，未合并入 `main`。详见[发布与核验记录](2026-10-19-stable-1.2.7-r1-release.md)。

已发布的 `1.2.7` 与 `1.2.7-r1` 客户端忽略 `-rN` 修订段：两者解析结果相同，`isVersionNewer` 返回 false，两个修订号之间也无法排序，`versionCode` 相同并非此次更新检查失败的原因。2026-10-20 按用户选择以四段数字版本 `1.2.7.1` / 35 正式补发，旧客户端可识别第四段数字；同时修复比较规则，依次按数字版本、发布阶段、阶段序号、修订号排序，构建元数据不参与比较。GitHub r1 已转草稿，Gitee r1 Release 已移除，原发布标签与历史保留。原版更新函数对真实 Gitee 列表验证通过，正式与 Beta 设置均选中本版。详见[本次修复与发布记录](reports/2026-10-20-update-revision-compatibility.md)。

## 一轮发布怎样收口

1. 日常功能和修复从新的 `main` 派生短期分支，验证后进入 `main`；不从历史 `develop` 或 `exp` 派生新普通功能。
2. 每个 Beta 独立记录本次实际交付的用户可感知变化、标签、构建与验收。预发布不会取代最新正式版。
3. 正式版将这一轮仍然有效的 Beta 内容合并为一篇面向用户的日志；应用内正式日志只显示正式版的合并结果，Beta 独立记录仍留在历史发布页与仓库文件中。
4. 发布前检查 Git 提交与标签、`versionName`/`versionCode`、包名、签名、渠道开关、主 APK 与组件 APK 身份；上传后从公开地址回读大小及 SHA-256。
5. 已发布标签、附件和说明不通过覆盖来“修好旧版本”；修订使用新 Beta、正式补丁版或明确的新标签。
6. 核对发布源码可从 main 到达；若按已授权例外先发布，记录源码 SHA、差异和回流 PR。回流时保留已发布提交的祖先关系，不能只更新版本字符串便宣称 main 与 Release 对齐。合并与发布的授权分别处理。

构建、安装与专项验收入口见根目录 [AGENTS.md](../AGENTS.md)。Git 工作方式见[开发工作流](DEVELOPMENT_WORKFLOW.md)。具体历史记录在 `release-notes/` 与带日期的 `docs/` 报告中；历史记录不自动定义当前功能状态。
