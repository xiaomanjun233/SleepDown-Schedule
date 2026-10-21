# 多作息课程显示、附件配色与快速设置反馈

继续草稿 PR #78。用户要求将多校区、楼宇或连堂课程这类情况标准化处理，覆盖升级前已导入的记录；同时修复周视图时间截断、上传菜单反色、预览“熊猫字”、液态 Dock 加号间距/振动和快速设置文字布局。

## 实现与数据边界

- `844ba11d`：复用 `CoursePeriodAlignment` 的精确匹配规则，只有起止边界完整对应连续作息行时才按节次呈现。允许真实时间连续占据多个参考节次；偏移、部分覆盖和无匹配保留独立时间。课程编辑器和课程管理不再仅凭保存了 custom 字段就选中“自定义时间”。未改变选择的确认不清空原始时间或源节次。
- Repository 已在读取 `AppState` 时投影现有课程，本轮沿用该链路，新增用例覆盖无原始快照的旧记录、多种时钟并存、连堂源节次与显示节次不同及写回恢复。没有增加数据库迁移、学校名判断或重新导入要求；既有 Room、备份和导出字段不变。
- 周视图改由 `WeekCourseTimeLabel` 测量完整字符串，使用等宽数字并按实际列宽收缩，覆盖卡片边缘、下方完整区间以及补充行；不截掉分钟末位。
- 调查只读了另一工作树当天 GDCP adapter 的时间保留方式，其所有课程携带真实起止时间，部分建筑作息偏移、部分课段连堂。这是场景证据，未复制其协议或改动该工作树；正式实现面向所有导入来源和手动课程。
- `1b111ad8`：附件菜单恢复公共面板主题前景色，收回的按钮仍保留输入栏图标色。预览使用设置页无壁纸配置和公共 `CourseCardText`，标题与说明共享课程文字规则和软阴影，取消逐卡原始色种导致的黑白切换。
- `e7312a99`：本地 COUI 实现的 `mSideViewSpacing` 取资源 `coui_bottom_floating_navigation_bar_Minimum_spacing_with_slide_view`，值为 12dp。液态并列 Dock 按同一边缘间距排布，窄窗口同时缩小 Dock；液态浮动加号恢复点击轻触反馈。原生代码仅在忽略目录审计，不入库。
- `90a0fb3e`：快速设置统一紧凑边距，数字字段从固定 170dp 改为 64dp，状态和日期采用标题下方的说明布局，当前周仍自动计算。日期子 Sheet、半屏宿主、保存和进入详细设置流程保留。
- `e52d9918`：当前作息暂不显示的课程继续保留原始节次摘要，不将空显示列表误写成第 1 节。
- `c3bb034c`：原生快速设置标题此前没有单行约束，在两侧操作按钮挤压后可自行折行。现按按钮之间的实际宽度测量标题并适配字号，保持单行居中；不修改已验证的时间逻辑。

## 验证与交付

`e52d9918` 的 `testGithubDebugUnitTest --tests '*CoursePeriodAlignmentTest' --console=plain --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8'` 通过，含编译 2m50s；JUnit XML 确认 19 项，失败、错误和跳过均为 0。包含旧导入混合作息重新读取、完整精确匹配、部分匹配保留时钟、连堂课程与源编号、切换作息及元数据写回保留等场景。

- 最终 `c3bb034c` 的 `assembleGithubRelease --console=plain --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8'` 通过，3m30s，159 个任务中 21 个执行、138 个复用；保留 R8、资源压缩、lintVital 和正式签名。标题排版补充未改时间逻辑，因此没有重跑已通过的时间用例。
- APK：`tmp/review-apks/SleepDown-1.2.7-standard-clock-ui-20261019.apk`，7,591,255 字节，SHA-256 `0152a70d49cb537dd084eddeaa30f418a0fd392f37b055d5ac0f751240a6f8b5`，与标准构建输出一致；包名 `com.xiaomanjun.sleepdownschedule`，`1.2.7` / 35。
- `apksigner verify --verbose --print-certs` 通过，正式 v2 证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与此前正式版一致。
- 项目 ADB 5038 确认 USB 设备 PLJ110 在线后，执行 `install -r` 返回 Success。系统核对 `1.2.7` / 35，Android 17，更新时间 2026-10-19 23:04:03；保留用户数据，未自动启动。
- 未运行自动 UI、性能或无关测试套件。界面、字体与触觉效果等待用户实机验收；没有宣称多校区数据已在用户手机上逐门核对。

构建过程中先发现本项目没有 Release 单元测试任务，改用已有 `testGithubDebugUnitTest`；随后修正 `ArrowPreference.endActions` 的非空回调类型。混合两个变体的构建触发默认 2GB 堆的 GC thrashing，最终改为单 worker、临时 3GB 堆并分开执行，没有修改仓库的构建内存配置。
