# 上课通知倒计时与状态刷新

日期：2026-09-14。用户反馈两种现象：上课/下课后仍显示旧状态，以及剩余分钟数不更新或跳变。

后续实机反馈：用户指出系统倒计时在 ColorOS 上会被吞掉。当前实现已撤回原生计时器，恢复显式“X分钟”文字；以下首次实现仅保留调查过程，不再代表当前显示方案。

## 官方说明与现有差异

- [Live Updates 文档](https://developer.android.com/develop/ui/views/notifications/live-update#when-time)说明可以用 `when` 和 Chronometer 展示系统倒计时；[Notification.Builder API](https://developer.android.com/reference/android/app/Notification.Builder#setShortCriticalText(java.lang.String))明确：非空 `shortCriticalText` 优先于计时器，null 才会回退到时间。`setUsesChronometer` 可自动更新分钟与秒。
- 原实现关闭 `showWhen`，把静态“X分钟”写入短文本和正文，依赖应用服务每分钟重新 `notify`。服务不被调度时，文字自然停留在上一份内容。把倒计时交给系统后，具体格式由系统决定，不再强制“X分钟”字样。
- [Doze 文档](https://developer.android.com/training/monitoring-device-state/doze-standby#adapt-your-app-to-doze)说明允许空闲时触发的闹钟仍受每应用频率限制；[闹钟调度文档](https://developer.android.com/develop/background-work/services/alarms)也区分精确闹钟与会延迟的非精确闹钟。现有提醒后 1、3、5 分钟的重复闹钟没有新状态，可能挤占真正上课/课间边界的额度。这是结合文档与代码的推断，未用设备日志确认本次延迟由该限制触发。

## 首次实现（系统倒计时部分已撤回）

- 课程通知把下一个阶段边界写入 `when`，启用 `setShowWhen`、`setUsesChronometer`、`setChronometerCountDown`。倒计时模式不再设置短文本；上课前地点/课程名模式保留短文本，展开通知仍有系统计时器。明日提醒不启用课程倒计时。
- 通知正文保留状态、明确的目标时刻、课程时间及地点，移除会变旧的手算剩余分钟数。上课和课间仍使用原进度通知模板；进度数值是应用按分钟刷新，不声称它由系统自动计算。
- 服务根据真正的课程/课间/结束/到期边界等待；只有进度条需要额外的分钟更新。自定义到秒的边界优先于下一个整分钟。若构建通知跨过边界，立即补一次新状态；开始服务后不再立刻重复发送同一份通知。
- 移除提醒后 1、3、5 分钟重试，保留提醒和真正状态边界，提前量为零时合并同一时刻的提醒/上课事件。调度签名升为 v4，原有闹钟会在下一次刷新时取消并按新规则登记。
- 保留精确闹钟权限判断、既有边界 Receiver、重新读取当前课表后选择课程的逻辑、亮屏/解锁修复，以及低频闹钟恢复。没有增加持续唤醒锁，也没有把课程通知伪装成系统闹钟。

系统计时器不会替应用更换“上课中/课间中”等业务状态；状态变更仍需要应用的边界回调。精确闹钟权限缺失、Doze 和厂商后台限制下不能保证绝对准时，需实机对照闹钟接收与通知提交时间进一步判断。

## ColorOS 修正

- `NotificationScheduler` 明确关闭 `showWhen`、`usesChronometer`、`chronometerCountDown`，不再写入未来的 `when`；胶囊恢复纯文字短文本，通知卡片恢复剩余分钟数。保留地点/课程名的显示偏好。
- `LiveUpdatePayload.nextRefreshAtMillis` 在下一个分钟文字变化、课程阶段边界和到期时间中取最早者。分钟刷新对齐目标时刻的秒数，与 `ceil` 的剩余分钟算法一致，不再对齐墙上时钟的整分钟；应用恢复执行时直接按当前时间计算，不补发已经错过的旧数字。进度条随同一次通知更新重算。
- 保留上一轮课程边界调度、重复闹钟去重和通知发送跨边界时立即补刷的修复。未恢复 1/3/5 分钟重试，也未增加持续唤醒锁或系统闹钟伪装。
- 这次修正处理 ColorOS 显示兼容性和应用可运行时的刷新时机；设备休眠或厂商冻结造成的调度延迟仍需实机取证。

## 首次实现验证

- `compileGithubReleaseKotlin` 通过，`LiveUpdatePayloadTest` 11 项通过。新增检查覆盖课前不再依赖分钟重发、自定义到秒的上课/短课间边界、到期优先和结束后停止调度。只运行该组时间规则测试。
- `git diff --check` 通过。当前未连接手机，没有锁屏、Doze 或厂商通知界面的实际验证记录；未安装或发布新版本。

## ColorOS 修正验证

- 源码提交 `2278b53`；`LiveUpdatePayloadTest` 11 项通过，包括目标秒数对齐、迟到回调跳过旧数字、课程边界和到期清理。
- `assembleGithubRelease` 成功，包含 Kotlin、R8、资源压缩、lintVital、打包和正式签名。APK v2 验证通过，大小 7,127,399 bytes，SHA-256 为 `36cb53bb66f9e3aeea9580fa4e66f49710e741965dc4f423bbd0c45912ff2ac0`。
- PLJ110（Android 17）覆盖安装返回 `Success`；设备更新时间为 `2026-09-14 12:18:30`，版本仍为 `1.2.5_beta6` / 31 的本地修订包。保留用户数据，未主动启动应用，未重发公开 Release。
- ColorOS 原生倒计时不可见的证据来自用户实机反馈；本次未另行操作通知界面或测量锁屏刷新时延。
