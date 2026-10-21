# 1.2.6 Beta8 与 exp3 发布验收

2026-09-21 按用户要求发布普通版 `1.2.6_beta8` 与实验版 `1.2.6-exp3`，GitHub、Gitee 均标记为预发布。公共改动先进入 `main`，再合入 `exp`；历史 Beta 与实验版日志保留，不覆盖旧标签或附件。

## 日志与源码

- Beta8：标签 `v1.2.6_beta8`，构建与标签提交 `53def18e0e3a894dde498d18410f8b723356c4ad`。
- exp3：构建提交 `845e36c1ae483ca18f18f356fd52dcca40eeecb9`，标签 `v1.2.6-exp3` 指向 `20cf63485e5f2242f2e8ceb2d527c9ad5804c9f3`。两者只差发布说明中的旧 Beta 升级提示，应用代码和构建配置相同。
- 两版应用内日志、发布日期和独立发布说明均已更新。Beta8 包含通用课程编辑、平板小组件、作息间隔、日视图、文字可读性及更新渠道修复；exp3 同步 Beta8，并加入课程组件更新、缓存及后台恢复改进。
- 发布审查发现旧普通 Beta 更新器未排除实验标签，补充普通渠道筛选、版本解析与缓存保护。Beta8 已修复；旧客户端无法通过发布元数据获得此代码修复，exp3 说明提醒早期普通 Beta 用户先手动升级 Beta8。

## 构建与测试

使用 JDK、外置正式签名与现有 Gradle 环境，单 worker、4 GB Gradle 堆，顺序执行两条渠道构建，未跳过资源压缩、R8 或 Lint。

| 渠道 | 验证 | 结果 |
| --- | --- | --- |
| Beta8 | `:app:testGithubDebugUnitTest :app:assembleGithubRelease` | 构建成功，8 分 29 秒；定向更新器测试 7 项通过 |
| exp3 | `:app:testGithubDebugUnitTest :coloros-wakeup-proxy:testDebugUnitTest :app:assembleGithubExp :coloros-wakeup-proxy:assembleRelease` | 构建成功，7 分 19 秒；应用定向测试 18 项通过 |
| 课程组件 | Provider 与快照存储测试 | 本轮 Gradle 判定 `UP-TO-DATE`，沿用相同输入的 6 项通过结果；未声称重新执行 |

应用测试通过临时 init script 限定范围。exp3 的 18 项包括更新器 10 项、Provider 契约 5 项、课程映射 3 项。以上不是全量测试结果；之前的视觉算法验证见 [文字可读性报告](2026-09-21-home-text-readability.md)。

最初组件测试任务名误写为不存在的 `testReleaseUnitTest`，在任务选择阶段失败；改用项目实际提供的 `testDebugUnitTest` 后完成上述验证。

## 产物核对

三个 APK 均通过 `apksigner verify`，证书 SHA-256 一致：`0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。

| 产物 | 包名 | 版本 / versionCode | 最低 / 目标 SDK | 字节数 |
| --- | --- | --- | --- | --- |
| `SleepDown-Schedule_v1.2.6_beta8.apk` | `com.xiaomanjun.sleepdownschedule` | `1.2.6_beta8` / 33 | 26 / 36 | 6,344,678 |
| `SleepDown-Schedule_v1.2.6-exp3.apk` | `com.xiaomanjun.sleepdownschedule` | `1.2.6-exp3` / 33 | 26 / 36 | 6,377,638 |
| `SleepDown-ColorOS-Course-Component.apk` | `com.suda.yzune.wakeupschedule` | `6.0.17` / 257 | 33 / 36 | 2,185,394 |

SHA-256：

- Beta8：`82bee53973d16bf101a31390f9798ec8dc8552a6e3174f7393399ec8d18489b7`
- exp3：`e389aa80a35479a5089bf2f8a76ebadb3d857897d0298e3457b51e224f69ee52`
- 课程组件：`d0ecbca62e9fbbb5fbd3f132b43c2552f623272a644841558fa89055cfc75ad2`

直接读取 APK Manifest，确认普通版没有实验课程 Provider；exp3 包含导出 Provider，组件包含开机及覆盖更新恢复 Receiver。组件的 `aapt dump badging` 因系统图标引用报错，改用 `aapt dump xmltree` 成功读取完整版本和 SDK 字段；签名验证通过。

## 发布回读

- Beta8：[GitHub](https://github.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.6_beta8)、[Gitee](https://gitee.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.6_beta8)。
- exp3：[GitHub](https://github.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.6-exp3)、[Gitee](https://gitee.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.6-exp3)，均附主应用和 6.0.17 组件。
- 两个平台的发布说明、目标提交与预发布状态回读一致；从公开地址下载全部 APK，大小与 SHA-256 均匹配本地产物。GitHub 的 exp3 下载校验遇到 Python TLS 连接错误后，使用系统下载工具完成同一公开附件的校验，未关闭 TLS 验证。
- GitHub 元数据通过 Codex GitHub plugin 回读；当前插件只提供读取能力，Release 写入沿用项目已有本地发布脚本。没有使用 GitHub CLI。
- 发布后 GitHub 正式版 `latest` 仍为 `v1.2.5`。

## 实机范围与限制

本轮发布没有安装新 Beta8 / exp3，也没有清除数据、自动启动应用或重启设备。此前视觉修改的本地实验包安装记录见文字可读性报告，不能视为本次 Release 的实机验收。

发布前在 PLJ110（Android 17 / ColorOS V17.0.0，已装本地 exp2 修复包及 6.0.17 组件）进行只读检查：初始化、课表列表和今天课程入口曾返回有效响应，随后明天课程入口报找不到 Provider，重试仍失败。本轮未重新执行强行停止、开机解锁及测试课程创建/删除的全套边界验证，因此不能宣称流体云后台问题完全解决。发布说明保留系统冻结、强行停止与后台限制仍可能影响读取的提示，历史调查见实验分支的后台观察报告。
