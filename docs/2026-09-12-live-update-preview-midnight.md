# 实时活动预览跨午夜立即退出

## 设备证据

- 用户反馈通知设置中的实时活动预览无法显示；连接 OPPO Find X9（PLJ110，Android 17），设备时间为 2026-09-12 23:43 至 23:46，用户确认提前提醒为 30 分钟。
- 已安装 `1.2.5_beta5` / 31。通知权限与 promoted 权限均已授予，课程提醒频道 importance=3，系统允许通知投递。
- 点击预览时日志显示 `promotable=true`、`requested=true`、`promotionAllowed=true`，随后成功启动前台服务，立即进入“没有当前课程或明日提醒”的刷新路径；检查时服务与通知均已消失。

## 根因与修复

- 预览原先用 `LocalTime.now().plusMinutes(...)` 计算时间，再附上单独获取的今天日期。23:46 加 30 分钟变成 00:16，却仍使用 9 月 12 日，导致 `expiresAtMillis` 已经过去；服务首次刷新时 `shouldStop()` 为真，通知随即被撤下。
- 即使开始尚未跨天，45 分钟预览课程的结束时间也可能落到当天凌晨，形成结束早于开始的无效时段，倒计时显示为零。
- 改用一次 `ZonedDateTime` 快照，保留日期和时区完成开始、结束与过期时间计算。提取可传入时间的纯 payload 构造入口，通知发布与前台服务继续沿用原路径。
- 定向回归覆盖开始跨午夜、仅结束跨午夜、跨年和最小提前分钟数；验证当前可见、倒计时正确、到达过期点才停止。

## 验证与交付

- 修复提交 `dfaa2b3`。定向执行 `NotificationSchedulingTest` 9 项、`LiveUpdatePayloadTest` 8 项，全部通过，无失败或错误；新增三项均检查边界时刻的可见性、倒计时和到期停止行为。
- 使用本地 Gradle init script 只纳入上述两个测试类；未重跑存在旧引用问题的全仓测试。
- 定向测试及 `assembleGithubRelease` 联合构建成功，耗时 9 分 10 秒；包含 Kotlin 编译、R8、资源压缩、lintVital 和正式签名，APK v2 签名验证通过。
- 修复包 `SleepDown-Schedule_v1.2.5_beta5-preview-fix.apk`，7,111,015 字节，SHA-256：`6d9c1cb360eedcf6f4f161ea6cdf57789125109466c34b55ead4936eadc3c90f`。
- 23:58 通过无线 ADB 覆盖安装成功，设备 APK 与本地产物哈希一致；未自动启动应用。用户随后点击 30 分钟预览，前台服务持续运行；17 秒后的通知记录仍为 `PROMOTED_ONGOING`，通知 id 为 `20260522`，importance=3。用户确认“已正常显示”。
- 已发布 Beta5 标签与附件保持原来的已发布产物，修复包单独保留。
