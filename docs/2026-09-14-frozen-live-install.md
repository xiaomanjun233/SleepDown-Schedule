# 冻结渲染与通知修复安装记录

- 源码：`d821f59`，包含 `9811875` 的背景冻结渲染优化和 `d821f59` 的系统倒计时/课程边界刷新修复。
- 构建：`assembleGithubRelease` 成功，保留 R8、资源压缩、lintVital 与正式签名；APK v2 签名验证通过。
- 产物：`app/build/outputs/apk/github/release/app-github-release.apk`，7,127,399 bytes。
- SHA-256：`d9ec579436cfe9754146387110e89521808107bba920300165e24195a1498f7c`。
- 设备：PLJ110，Android 17。`adb install -r` 返回 `Success`，设备记录更新时间为 `2026-09-14 12:06:33`。
- 版本标识仍为 `1.2.5_beta6` / 31；这是包含后续修复的本地修订包，未替换公开发布的 beta6 附件。
- 覆盖安装保留用户数据，未主动启动应用。安装成功不代表已经完成实际帧率、锁屏通知或 Doze 验证。
