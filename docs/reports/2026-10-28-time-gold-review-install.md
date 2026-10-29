# 2026-10-28 时间与金标修复联合安装

## 2026-10-29 HD 与手动时间下限联合候选

本地 `codex/beta1-hd-review-install-20261029` / `b5ef16f4` 包含时间/HD #88 的 `2eb22f1e`（代码 `1bef9853` / `1bee8e59`）与金标 #89 的 `56f7dde8`。构建开始于合并提交 `f27dda5b`，随后 `b5ef16f4` 只清理交接文档残留标记，应用源码没有变化；两条 PR head 的祖先关系已核对。

完整 `assembleGithubRelease` 5m57s 成功，159 项任务（22 执行、137 复用），保留 R8、资源压缩、lintVital、打包及正式签名。包名 `com.xiaomanjun.sleepdownschedule`、版本 `1.2.8_beta1` / 36，实验能力与金标开关为 true。V2 签名证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与此前安装一致。APK dex 已包含新的“开始时间不能早于作息起点”校验，旧候选不含此字符串。

- 保全 APK：`tmp/review-apks/SleepDown-1.2.8-beta1-hd-b5ef16f4-30b65f4f.apk`（相对主仓库目录）。
- 文件大小：7,741,787 字节。
- SHA-256：`30b65f4fc711ceb0b4832d412156afa00c72e19dca66199d3bdbb95ef853a7b2`。
- 源码回归：#88 Release Kotlin 与九类 62 项通过；#89 Release Kotlin 与四类 30 项通过。联合架构检查 603 个生产文件、0 违规。显示分支审查与停止性能采集的边界见[HD 与显示核对](2026-10-29-hd-and-display-routing.md)。

构建完成时的首次安装因原设备离线失败，当时核对 5037/5038/5039 的 USB 与 mDNS 均无设备；这次失败未修改手机数据。用户随后再次明确要求“安装”，重新检查现有全部服务及无线发现，SDK 5038 USB 已发现原目标，5037/5039 无设备，全部 mDNS 无无线端点。

**本包已于 2026-10-29 17:07:01（Asia/Shanghai）覆盖安装成功。** 实际客户端与 5038 服务端均为 `D:\Android studio\AndroidSDK\platform-tools\adb.exe` / 37.0.1，服务使用当前用户配对目录；目标为原 OPPO PLJ110 / USB `3B15AE023YL00000` / Android 17 / SDK 37。显式 `-P 5038 -s 3B15AE023YL00000 install -r` 返回 Success；版本回读 `1.2.8_beta1` / 36，安装后的 base.apk SHA-256 完整匹配 `30b65f4fc711ceb0b4832d412156afa00c72e19dca66199d3bdbb95ef853a7b2`。直接使用保全新包，没有重复构建、清除数据或主动启动，没有停止 OPPO 服务。实际 HD 流畅度、文字滚动与金标 ROM 观感仍待用户确认；未发布 Release 或合并两个新 PR。

用户在完成 #88/#89 两个独立 PR 后要求“打包安装”。本轮仅本地合成安装候选，原 PR 与 main 的代码边界保留。

- 本地分支：`codex/beta1-review-install-20261028`，源码 `140d9a34922f7907c8f45fa6d366182e19cbe4ea`。
- 基线 main `2ada28b7`；完整包含时间 PR #88 的 `b7c12f84` 和金标 PR #89 的 `f449ff58`，均核对祖先关系，合成无冲突。
- `assembleGithubRelease` 成功，6m13s、159 项任务（64 执行、95 复用），单 worker、3GB 堆，保留 R8、资源压缩、lintVital 与正式 V2 签名。
- APK 身份：`com.xiaomanjun.sleepdownschedule`，`1.2.8_beta1` / 36，7,741,787 字节；证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。
- APK SHA-256：`bd6211eb231281bf761255451c8fdac45b31a252df858d84b084010b00a4d020`。生成配置的 `SLEEPDOWN_SHARED_VIEW_KIT_TEST=true`；R8 mapping 保留特殊课程显示枚举及新源 View 的实际实现。
- 本机保全副本：忽略目录 `tmp/review-apks/SleepDown-1.2.8-beta1-time-gold-140d9a34-bd6211eb.apk`；APK、日志及签名材料不入库。

## 安装

最初检查 5038/5037 的 USB、无线设备及 mDNS 均无设备，已核对本机仅有这两条 ADB 服务。用户随后确认“USB 已就绪”，5038 发现原目标 OPPO PLJ110；5037 与无线 mDNS 仍无设备。

客户端和实际 5038 服务端均为 `D:\Android studio\AndroidSDK\platform-tools\adb.exe`，版本 37.0.1，配对目录属于当前用户；5037 是 OPPO Connect 36.0.1 服务，未停止或抢占。

通过 USB `3B15AE023YL00000`、5038，向 PLJ110（Android 17 / SDK 37）执行 `install -r`，返回 Success。手机记录更新时间 2026-10-28 21:50:15（Asia/Shanghai），回读版本为 `1.2.8_beta1` / 36，手机 base.apk SHA-256 与本轮 APK 完全一致。保留原数据，未主动启动；实机视觉、交互及 ROM 金标观感尚待用户确认。

没有改版本、标签或公开 Release。安装完成后，时间工作树恢复 `codex/week-time-display`，本地集成分支与保全 APK 留存；后续继续对应独立 PR，不从集成候选派生无关功能。

## 2026-10-29 反馈修复包

用户继续反馈金标源圆角、特殊课程字体与滚动、日期作息选中态、教务返回键安全区，并要求单课不再引用其他作息。实现见[反馈报告](2026-10-29-time-ui-feedback.md)及 #89 圆角报告。

- 本地候选：`codex/beta1-feedback-install-20261029` / `c5dfb5ef79e6e22fc1354b699a5b1c25e3016a84`，基于 main `2ada28b7`；已核对 #88 `33b0dbf0` 和 #89 `d6f8c33b` 都是其祖先。合成仅有交接文档冲突，保留双方记录后完成；无运行时代码冲突。
- 完整 `assembleGithubRelease` 4m52s 成功，159 项任务（20 执行、139 复用），包含 R8、资源压缩、lintVital 与正式 V2 签名。联合架构检查 600 个生产文件、0 违规文件依赖。
- 包身份仍为 `com.xiaomanjun.sleepdownschedule` / `1.2.8_beta1` / 36，7,741,787 字节；证书 SHA-256 仍为 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，金标生成配置为 true。
- APK SHA-256：`b29fa88da63e1904d1ee0d9742ae028225be47f5304a74f9409372bf2afa13b2`。本地保全于忽略目录 `tmp/review-apks/SleepDown-1.2.8-beta1-feedback-c5dfb5ef-b29fa88d.apk`。
- SDK 客户端仍是 `D:\Android studio\AndroidSDK\platform-tools\adb.exe` / 37.0.1。本轮首选 5038（SDK 服务、当前用户密钥）无设备；5037 找到原 USB `3B15AE023YL00000` / PLJ110，Android 17 / SDK 37。两个服务均检查 mDNS，无无线端点。
- 实际安装使用显式 `-P 5037 -s 3B15AE023YL00000 install -r`；5037 服务来源为 `D:\Oppo Connect\daemon\bin\adb.exe` / 36.0.1，密钥目录为系统服务用户。沿用该可用通道，没有抢占 USB 或停止 OPPO Connect。
- 安装返回 Success，手机记录更新时间为 2026-10-29 01:26:36（Asia/Shanghai）；回读版本及手机 base.apk SHA-256 与候选完全一致。保留数据，未主动启动。

时间与金标定向回归分别 57、27 项通过。字体、自动滚动、安全区与 ROM 动画实际观感仍待用户验收，不能由构建和哈希代替。两个 PR 已更新，未合并或公开发布；安装后恢复时间 PR 分支，本地联合分支和 APK 保留。

## 2026-10-29 当天表头、居中与返回衔接候选

用户确认先看的特殊时间 JSON 后，要求开始修改，并补充创建安排列表每行的按压胶囊、特殊课程显示方式 Popup、自定义时间居中，以及时间不变色、异常上课数字标变黄。代码与定向回归见[本轮续修报告](2026-10-29-date-rail-and-popup.md)。

- 联合分支 `codex/beta1-date-rail-install-20261029`，源码 `cb02b47db9b37337c068efff3d1e2a4ca5b064b5`；已核对 #88 `c4d2a33e`、#89 `f36b9c15` 的祖先关系。无运行时代码冲突，交接冲突保留双方记录。
- 完整 `assembleGithubRelease` 4m58s 成功，159 项（20 执行、139 复用）；包含 R8、资源压缩、lintVital 和正式 V2 签名。联合架构 600 个生产文件、0 违规文件依赖；定向回归分别 33、29 项通过。
- `com.xiaomanjun.sleepdownschedule` / `1.2.8_beta1` / 36，7,741,787 字节，金标配置 true；正式证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。
- APK SHA-256 `83777845f1e42374044735681b746c2a25b28cd0567cd29781d6eeb2a2cf40e6`，保全副本 `tmp/review-apks/SleepDown-1.2.8-beta1-daily-rail-cb02b47d-83777845.apk`。
- 构建前后均检查 SDK 5038、OPPO 5037 的 USB 和无线 mDNS，并枚举全部 ADB 进程与监听端口，仅这两条服务且均无设备。SDK 服务为 37.0.1、当前用户密钥；OPPO 服务为 36.0.1、系统服务用户密钥。没有抢占 USB、停止服务或尝试历史无线地址。
- 已向用户提示连接原 OPPO PLJ110，包完成后仍离线，**本次新包没有安装**。此前 `b29fa88d` 的安装属于上一候选，不能代替此包的设备验收。手机接通后使用实际可用端口和原序列号覆盖安装，并回读新哈希；默认不启动。

### 后续安装已完成

用户随后明确要求“安装”。重新检查 5038/5037 的 USB、无线 mDNS：SDK 5038 发现原 USB `3B15AE023YL00000`，5037 无设备、两条 mDNS 无无线端点。核对目标 PLJ110 / Android 17 / SDK 37；客户端与实际 5038 服务端均为 `D:\Android studio\AndroidSDK\platform-tools\adb.exe` / 37.0.1，使用当前用户配对目录。OPPO 5037 服务保持原样，没有抢占 USB 或停止服务。

已通过显式 `-P 5038 -s 3B15AE023YL00000 install -r` 覆盖安装上述 `83777845…cf40e6` APK，返回 Success；手机记录更新时间 2026-10-29 10:41:30（Asia/Shanghai），版本回读为 `1.2.8_beta1` / 36，手机 base.apk SHA-256 完整匹配 `83777845f1e42374044735681b746c2a25b28cd0567cd29781d6eeb2a2cf40e6`。沿用已验证构建，没有重新打包；保留数据，未主动启动。上方“未安装”记录的是构建结束时状态，此次安装事实以本段为准；实际界面、数字标和金标观感待用户验收。

本轮没有公开 Release 或合并 PR；原时间分支恢复、源码工作树干净，联合分支及 APK 保留。最新包的实际居中、列表反馈、Popup、颜色与金标末帧观感待设备确认。
