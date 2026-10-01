# 2026-10-01 课程数据安全与首页可读性验收包

基线为 `origin/main` 的 `d02389d`，本地分支为 `codex/course-data-safety`。本次安装包保留 `1.2.7_beta1` / `versionCode=34`，用于本地验收。

## 课程与设置

- 同类课程分组增加星期边界，并保留点入课程的身份。单次编辑、删除绑定该记录及指定周次；两个编辑入口均展示操作范围，批量确认列出星期、周次与日期。
- 已有课程有效周次为空时不会初始化为整个学期；只修改名称等字段时保留原周次及单双周设置。缩短学期前列出受影响课程，确认后保留原排课数据。
- 课程编辑有草稿修改时提供保存当前页、放弃修改和继续编辑；外层返回、空白处关闭及系统返回接入相同处理。
- Android 14 及以下手动勿扰保存开启前状态，只在状态仍归应用控制且启动次数一致时恢复；已观察到用户手动覆盖后停止恢复。

主要入口为 `CourseEditorUi.kt`、`ScheduleAppUi.kt`、`ScheduleRepository.kt`、`ScheduleConfigScreenUi.kt` 和 `NotificationScheduler.kt`。本轮没有 Room schema 或备份协议变更。

## PR #61 与可读性

[PR #61](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/61) 的头提交为 `ccd39a9`，复核时仍为未合并草稿。该提交的作息编辑、课程映射预览及阻止危险保存逻辑已纳入本地分支；组合保存时继续传递已确认的学期缩短状态。

首页日视图、周视图的彩色文字原先只使用页面级提亮色，没有接入已有的局部背景颜色解析。本轮传入课程原色，按缓存壁纸、卡片染色和模糊估计调整字色亮度、饱和度，并保持课程色相。彩色教师、地点信息使用不透明的对比度结果，由字号和排布区分层级。

非彩色文字继续沿用页面统一前景色。软阴影只在对比度不足时提供辅助：非彩色字使用低透明度、较弥散的阴影，彩色字使用较小半径；滚动和转场继续冻结颜色解析，停止运动后再采样。

可读性修改位于 `CourseCardText.kt`、`HomeScheduleUi.kt`、`WeekScheduleUi.kt`，提交为 `011e964`。

## 实际验证

| 项目 | 结果 |
| --- | --- |
| PR #61 云端检查 | PR scope、Android compile、PR checks 全部通过；CI 编译 Release Kotlin，没有运行其 93 项测试 |
| 本地定向单元测试 | 141 项通过，0 失败、0 错误；包含作息相关 93 项、课程及勿扰相关 36 项、文字对比度相关 12 项 |
| 完整构建 | `assembleGithubRelease` 成功，包含 Release Kotlin、R8、资源压缩、lintVital 和打包 |
| APK 签名 | `apksigner verify` 通过，v2 签名有效 |
| 设备安装 | OPPO Find X9 / PLJ110，设备自报 Android 17 / API 37；`adb install -r` 返回 `Success`，未清除应用数据 |

单元测试通过临时 init script 选择上述 12 个测试类，未编译或运行无关测试集。最后一次仅调整阴影透明度和半径后执行完整 Release 构建。

APK 包名为 `com.xiaomanjun.sleepdownschedule`，大小为 `6,810,283` 字节，SHA-256 为 `5479249409DD6E6DC6A62E7CD79B96CFE2C96F5B3EC055C858EF90DFF94885E3`。

验收副本保存在 `tmp/review-apks/SleepDown-1.2.7_beta1-20261001-course-safety-readability.apk`，未纳入 Git。

安装后未自动启动应用。新版界面观感及编辑、删除完整操作仍待实机验收；Android 14 及以下勿扰恢复未进行实机验证。Android 26–28 的动态接收器只能在进程存活期间观察手动状态变更，进程退出期间的切换存在观察限制。
