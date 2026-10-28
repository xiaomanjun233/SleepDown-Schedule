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
