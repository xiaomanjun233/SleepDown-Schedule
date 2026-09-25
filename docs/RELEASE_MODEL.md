# 版本基线与发布关系

本文说明当前维护方式；历史发布事实以 Git 标签、构建配置和对应 Release 附件为准。基线快照核对于 **2026-09-25**。

## 当前基线

| 项目 | 当前事实 | 核对入口 |
| --- | --- | --- |
| 持续开发分支 | `main`；当前已发布正式版为 `v1.2.6` | Git 标签与 `app/build.gradle.kts` |
| Android 主应用 | `com.xiaomanjun.sleepdownschedule`，`versionName=1.2.6`，`versionCode=33` | `app/build.gradle.kts` |
| 发行渠道 | `github` 与 `store` 两种 flavor；厂商实验入口由 `SLEEPDOWN_EXPERIMENTAL_FEATURES` 按渠道控制 | `app/build.gradle.kts`、`app/src/github/`、`app/src/store/` |
| 课程组件 | 独立包 `com.suda.yzune.wakeupschedule`；构建版本 `6.0.18` / `258` | `coloros-wakeup-proxy/build.gradle.kts` |
| 历史实验线 | `exp` 与 `v1.2.6-exp*` 保留作为已发布历史，停止作为后续功能基线 | Git 历史与旧 Release |
| 历史集成线 | `develop` 不再是新功能或外部 PR 的默认目标 | 当前维护流程 |

`main` 的 1.2.6 已包含按设备展示的 OPPO/一加/realme 流体云、荣耀 YOYO 建议和小米超级岛课程通知实验入口。这里的“实验功能”是**同一应用中的功能状态**，不是继续发行 `-expN` 独立版本。GitHub flavor 启用入口，商店 flavor 关闭相应实验能力；实际可用性还受设备、系统、授权和组件状态限制。详见[功能地图](FEATURE_MAP.md)和[历史实验线记录](EXP_BRANCH_AND_RELEASES.md)。

## 正式版、Beta 与实验功能

项目早期曾连续记录 `1.01 beta` 至 `1.10 beta`。这是 2026 年 5—7 月的**历史版本序列**，并非当前 `1.2.6_beta1` 至 `beta10`。逐版内容和能确认的时间锚点见[最初十个 Beta](EARLY_BETA_HISTORY.md)。

| 类型 | 命名和标签 | 代码来源 | 更新日志与发布页 |
| --- | --- | --- | --- |
| 正式版 | `1.2.6` / `v1.2.6` | `main` 上经过发布验证的提交 | 将本轮有效 Beta 内容归并为一篇正式日志，去重并删除已撤回项目；非预发布 |
| 普通 Beta | `1.2.6_betaN` / `v1.2.6_betaN` | 同一 `main` 基线的发布候选 | 每个 Beta 保留独立应用内记录、仓库说明和预发布附件，不改写旧标签 |
| 历史 `exp` | `1.2.6-expN` / `v1.2.6-expN` | 当时的 `exp` 分支 | 只作为历史预发布保留，不再用于新的普通功能、当前更新通道或下一版号 |
| 厂商实验功能 | 无独立版本后缀 | 当前 `main` 的隔离实现 | 随对应正式版或 Beta 交付，在说明中标明实验状态与设备条件 |

同一 `1.2.6` 轮次的正式、Beta 与历史实验 APK 使用 `versionCode=33`。覆盖安装仍须核对**相同 applicationId、签名、版本代码及渠道**；不能仅凭版本名称判断。下一主版本的 `versionCode` 必须满足 Android 升级要求。旧身份 `com.example.courseschedule` 不能直接覆盖安装当前包，需先在旧版导出 `.sleepdown` 再恢复，见[迁移说明](migration/1_2_0_PACKAGE_MIGRATION.md)。

## 一轮发布怎样收口

1. 日常功能和修复从新的 `main` 派生短期分支，验证后进入 `main`；不从历史 `develop` 或 `exp` 派生新普通功能。
2. 每个 Beta 独立记录本次实际交付的用户可感知变化、标签、构建与验收。预发布不会取代最新正式版。
3. 正式版将这一轮仍然有效的 Beta 内容合并为一篇面向用户的日志；应用内正式日志只显示正式版的合并结果，Beta 独立记录仍留在历史发布页与仓库文件中。
4. 发布前检查 Git 提交与标签、`versionName`/`versionCode`、包名、签名、渠道开关、主 APK 与组件 APK 身份；上传后从公开地址回读大小及 SHA-256。
5. 已发布标签、附件和说明不通过覆盖来“修好旧版本”；修订使用新 Beta、正式补丁版或明确的新标签。

构建、安装与专项验收入口见根目录 [AGENTS.md](../AGENTS.md)。Git 工作方式见[开发工作流](DEVELOPMENT_WORKFLOW.md)。具体历史记录在 `release-notes/` 与带日期的 `docs/` 报告中；历史记录不自动定义当前功能状态。
