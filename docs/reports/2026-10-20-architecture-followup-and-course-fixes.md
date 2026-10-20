# 架构续作与课程显示、自动静音修复

此页保留课程修复阶段的证据。此后架构收口、ChatGPT 和模型列表修复见[最新报告](2026-10-20-architecture-completion-and-ai-fixes.md)，最终代码与 CI 以 #79 head 为准。

本轮接续 [首批架构审查](2026-10-20-architecture-review.md)。开始前通过 GitHub plugin 核对 #79 仍开放、无评审线程，fetch 后 main 仍为 `9b6acbb2`；复用独立工作树 `tmp/architecture-refactor/` 与 `codex/architecture-boundaries`，不从旧分支重做。用户随后补充两项问题，并确认自动静音应覆盖铃声、通知和媒体，下课恢复。

## 首页导入交接的状态所有权

`b45607ab` 将首页的提交状态、来源画面、预览挂载状态与课程就绪条件收进 `feature/home/HomeImportHandoffState`。旧课程 ID 和预期数量作为同一份期待值保存，确认防重、失败释放和完成清理由状态对象负责。

应用根 Composable 继续装配入口关闭、预览卸载等待、首页数据等待及原有 300ms 淡入；状态仍属于当前 Composition，未增加 scope、单例或生命周期。AI 最终请求继续由原导入会话认领，本地确认才使用预览提交标记。没有把导入数据库事务或后台 transport 移到 UI 状态对象。

Release Kotlin 编译与 4 类 17 项定向回归通过，0 失败/错误/跳过（5m39s）。新状态对象的 8 项覆盖新建、覆盖、旧 ID 尚未替换、元数据晚到、预览仍挂载、重复确认、失败重试、空课表及 Activity 导入。

## 自动静音实际调低媒体音量

`98589c4a` 修复了实现与用户预期的缺口：原先仅调用 `AudioManager.ringerMode`，媒体音量没有变化。现在继续通过铃声模式控制铃声/通知，同时使用 `setStreamVolume(STREAM_MUSIC, 0, 0)` 静音媒体。

媒体原音量在勿扰可能影响读数前捕获，改变音量前先持久化恢复所有权。下课或关闭自动声音时，只恢复仍与应用所设值一致的媒体音量；检测到用户已调整则保留。失败恢复保留快照供下一次有效调度重试，进程重启后可恢复。运行时 JSON 新字段具有空默认值，旧恢复记录可直接解码，原设置键与备份格式不变。授权页面返回后重新计算当前课程和下一边界，避免继续停留在无权限时的调度结果。

所需权限已经声明：`MODIFY_AUDIO_SETTINGS` 是普通权限；可能切换勿扰的铃声/音量操作需要用户授予 `ACCESS_NOTIFICATION_POLICY` 对应的勿扰访问。继续使用现有系统授权入口和应用自有 AutomaticZenRule，不追加修改系统设置或其他无关授权。依据：[AudioManager](https://developer.android.com/reference/android/media/AudioManager)、[权限定义](https://developer.android.com/reference/android/Manifest.permission#MODIFY_AUDIO_SETTINGS)。

Release Kotlin 编译与 `CourseQuietControllerTest`（21）、`CourseQuietHoursTest`（8）、`LegacyDndSessionTest`（6）共 35 项通过，0 失败/错误/跳过（4m38s）。新增覆盖媒体恢复、进程重启、手动调音量、独立勿扰、震动模式切换、恢复失败、系统拒绝调整、旧 JSON 和勿扰遮蔽读数。

## 自定义课时显示与时间标注

`a2eb95f4`：连续的自定义课时原先复用了提醒分段：每个精确课时成为独立 fragment，造成同一门课显示成多张卡。显示现在使用 `courseWeekDisplayFragments`，连续教学行中的普通/自定义课时可以合成同一张卡；缺失的教学行仍分开。原 `courseAlignmentFragments` 继续服务提醒，每课时的真实时间保留在瞬态投影，编辑写回使用完整来源，未修改数据库或导入内容。

时间标注原先分别避让卡片，忽略了相邻标注，也排除了相同课程 ID 的其他 fragment。现在按视觉顺序为整列分配标注空间，包含其他 fragment 和非本周参考卡片，并共享测量与绘制样式。按实际字体高度安排边界，空间不足时合并为完整时间区间或隐藏，避免文字叠加。未改动玻璃材质、采样 producer、Morph 参数或编辑手势所有权。

## 通知数据编解码边界

`d6779eeb`：Intent 的课程通知 payload 与 Service 重启用的偏好恢复原先各自维护枚举默认值、时间段格式和字段映射。现在归入功能内的无状态 `LiveUpdatePayloadCodec`，时间段编解码只保留一份。NotificationScheduler 的现有门面、常量名称和值保持兼容；Service 仍负责自身 scope、启动/停止、清除恢复状态与前台通知。

固定旧键名的回归分别保护已调度 Intent、旧别名、字段优先级、未知枚举、损坏时间段、重启偏好及缺少必需字段时的原有处理。没有借这次整理修改闹钟 requestCode、通知样式、channel 或勿扰兼容逻辑。

## 最终验证与边界

联合验证代码 `d6779eeb` 的 `compileGithubReleaseKotlin testGithubDebugUnitTest` 通过（5m16s）：16 类共 140 项，0 失败/错误/跳过，包括首页交接、课程静音、显示分组与标注、真实课程时间、冲突、通知和 Widget。编译与执行使用单 worker、临时 4 GiB JVM 堆；构建配置不变。

收尾检查新增两项失败回归：系统不抛异常却拒绝恢复时，旧代码会丢失恢复快照；系统只完成部分媒体音量调整时，旧代码没有记录实际值。`3302c9d2` 在释放自身勿扰规则后回读恢复音量，验证成功前保留所有权，并记录部分失败后的实际值。两项新测试在修复前均失败；修复后 Release Kotlin 编译及静音相关 3 类 37 项通过，0 失败/错误/跳过（5m07s）。这是最终运行时代码；其余显示、交接和协议代码相对上述联合验证未再修改。

架构检查覆盖 546 个生产 Kotlin/Java 文件，9 项检查器回归通过；仍登记 44 项文件依赖、109 条循环边，没有增加豁免。Service 启停、刷新循环和清除恢复状态的代码与迁移前比对一致，只有 payload 映射改为委托。只传节次、不传完整时钟的 Widget 分组路径有专门回归，保持既有布局。

首轮周视图联合验证已完成编译，但在下载缺失的 Robolectric Android 11 运行时处等待，因此结束该测试 worker；该轮不计为通过。从 Maven Central 补齐公开 JAR 并与已有 SHA-512 校验一致后，上述最终集合完整通过，包含旧系统 Widget 测试，没有移除测试或更改镜像/依赖配置。

本次新增回归已进入原 CI 选择集合。最终 PR head 的完整云端结果以 [#79 Checks](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/79/checks) 为准，不用旧 head 的通过结果替代。

当前 5038 ADB 通道没有连接设备；没有实机视觉/OEM 音量行为验收，没有安装、启动或发布应用。没有修改版本、Manifest、Room schema/迁移、DAO、Repository、BackupFormatV1、ICS、SleepDown 口令、AI 历史和教务协议。通知 wire 键由固定字面值回归保护。

剩余高收益工作包括个性化草稿/待保存状态所有权、app 与 feature 的真实配置依赖、model/domain 共享不可变值、Repository 的纯计划准备，以及教务 WebView 会话释放。当前没有实施 model/domain 值迁移，也没有宣称循环已经消除；继续沿用首批检查基线，禁止新增反向依赖与循环。
