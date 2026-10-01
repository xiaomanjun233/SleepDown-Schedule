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

安装后未自动启动应用。新版界面观感及编辑、删除完整操作仍待实机验收；Android 14 及以下勿扰恢复未进行实机验证。API 26–28 的动态接收器只能在进程存活期间观察手动状态变更，进程退出期间的切换存在观察限制。

## 个性化弹窗追加修复

`ScheduleAppUi.kt` 的新增周视图卡片内容标题、地点开关、教师开关和文字排布行补齐逐行入场动画，并接入滑块预览时的内容隐藏。后续行的延迟顺延，周视图总入场时长从 580 ms 增至 640 ms，保证末行完整显示；日视图时长不变。代码提交为 `7192cd6`。

文字排布改用设置页同款 `SleepDownLiquidDropdownPreference`，沿用标准弹层和打开时的振动反馈。面板内入口行在常规字体下压至 48 dp，保留随系统字体放大的空间，并沿用面板文字颜色。

本次局部界面修改没有追加单元测试。完整 `assembleGithubRelease` 构建及 v2 签名校验通过，已对同一台 OPPO Find X9 执行 `adb install -r`，返回 `Success`。未清除应用数据，也未自动启动；入场观感及振动体感尚未进行实机交互验收。

新验收包仍为 `1.2.7_beta1` / `versionCode=34`，大小 `6,810,283` 字节，SHA-256 为 `E9D0B510DAE893E54450592E33C2C662CF64A26F61BC25D237805C98CDB22F87`。独立副本保存在 `tmp/review-apks/SleepDown-1.2.7_beta1-20261001-personalization.apk`，未纳入 Git。

## 个性化紧凑排版与同卡字色追加修复

第一轮将个性化面板的文字排布入口、选中值和菜单项统一为 `bodyMedium`，常规入口高 44dp，菜单条目最小高 44dp、垂直内距 6dp，并为灰态增加 `Capsule` 裁切。设置页同款弹层动画与振动反馈继续沿用。代码提交为 `a50f652`；后续截图确认入口零水平内距会裁掉文字，且新增行与原有开关的 `labelLarge` 排版仍不一致，更正见下文。

紧凑条目样式通过共用下拉控件和增量补丁 `patches/miuix-compact-dropdown.patch` 提供，仅在个性化面板启用；其余设置行保留默认排版。补丁在变更前的两份源码副本上通过正向 `git apply --check`，在当前本地 Miuix 源码上通过逆向检查。根 README 已补齐第四份补丁的应用顺序。

截图反馈确认，同卡不同字色来自逐个文本块按局部背景选择明暗字色。该路径已移除：日视图、周视图及拖动预览共用课程级字色，地点、课名、教师与其他信息保持同一不透明颜色；保留课程色相、饱和度和整体提亮。局部采样只调整软阴影强度，彩色阴影半径改为 3–5.5dp，非彩色继续使用页面统一前景色与原有低透明度阴影。代码提交为 `88fc451`。

同时移除旧逐文本反色算法及其 8 项旧测试，保留课程色相和软阴影的 4 项检查。独立运行 `CourseTextContrastTest` 后，JUnit 结果为 4 项通过、失败 0、错误 0、跳过 0。

组合验证曾遇到 Gradle 2GB 堆耗尽；后续将 Release 构建与单测拆成单 worker、3GB 堆的独立进程。最终完整 `assembleGithubRelease` 构建在 6m33s 内成功，包含 Kotlin、R8、资源压缩、lintVital 和打包；v2 签名校验通过。

21:39:53 已对同一台 OPPO Find X9 / PLJ110 覆盖安装，`adb install -r` 返回 `Success`，包管理器确认更新。未清除应用数据，也未自动启动。新版界面观感、灰态裁切与振动体感尚未进行实机交互验收。

该轮验收包仍为 `1.2.7_beta1` / `versionCode=34`，大小 `6,810,283` 字节，SHA-256 为 `9A85EDA94B853FD02E540884A7571C8A413068FD8CF621B6730C14455C70568B`，副本为 `tmp/review-apks/SleepDown-1.2.7_beta1-20261001-unified-card-text.apk`，未纳入 Git。

## 胶囊裁切与面板字体对齐

21:45 的用户截图显示，“文字排布”的首字被胶囊左侧曲线裁掉。原因是入口直接裁切整个原生行，水平内距却为零。修正让胶囊向分组现有的左右留白各延伸 12dp，再给内部文字和操作各留 12dp 内距，使裁切曲线远离文字，并保持与其他控件的左右对齐。

地点、教师和文字排布三行改为原有开关使用的 `labelLarge` 字号、字重和行距，常规行高统一为 48dp；文字排布菜单也沿用该文字样式，但保留条目最小高 44dp 的紧凑排版。入口随系统字体增高，逐行入场、预览隐藏、原生弹层和振动路径继续沿用。代码提交为 `02a0912`。

这次局部界面调整未重复运行无关测试。完整 `assembleGithubRelease` 构建在 7m55s 内成功，包含 Kotlin、R8、资源压缩、lintVital、打包和签名；v2 签名校验通过。包标识仍为 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7_beta1` / `versionCode=34`。

新版包大小 `6,810,283` 字节，SHA-256 为 `C40817499FD3A1D14609516D0B0778219DF6125EB7163D6E1D61B40AB90CBDA1`，独立副本为 `tmp/review-apks/SleepDown-1.2.7_beta1-20261001-personalization-alignment.apk`，未纳入 Git。

收到新的无线调试截图后，已重新配对并连接同一台 OPPO Find X9 / PLJ110。安装前核对 APK 的 SHA-256 与已校验签名的包一致，`adb install -r` 返回 `Success`。首次安装后的版本查询遇到连接关闭，补查包管理器成功，确认应用包名及 `versionCode=34`。未清除应用数据，也未自动启动；新版界面尚未进行实机交互验收。
