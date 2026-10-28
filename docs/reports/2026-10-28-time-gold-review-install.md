# 2026-10-28 时间与金标修复联合安装

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

本轮没有公开 Release 或合并 PR；原时间分支恢复、源码工作树干净，联合分支及 APK 保留。最新包的实际居中、列表反馈、Popup、颜色与金标末帧观感待设备确认。
