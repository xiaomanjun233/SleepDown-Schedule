# SleepDown 课程表：开发交接

## 项目约束

- Android 课程表，核心特色是 Miuix 风格、液态玻璃视觉和 AI/Agent 融合。
- 保留现有功能、视觉、动画、模糊/折射/色散效果及第三方开源库引用；性能优化不得靠删除既有效果完成，除非用户明确要求。
- 数据安全优先。数据库、配置、备份恢复和升级必须提供迁移路径，不得主动清除或覆盖用户数据。
- UI 按窗口和安全区自适应，不为单一 DPI、分辨率或设备写死特例。
- 工作树可能包含用户素材和临时证据；不要使用 `git reset --hard`、`git checkout --`，不要删除或顺手整理不在任务范围内的文件。
- 未经明确同意，不推送、不打标签、不发布 Release。

## 必须持续遵守的视觉与交互验收规则

- 所有覆盖动态壁纸的液态玻璃弹窗、表单、选择器和卡片，标题、字段名、输入文字、摘要、箭头、图标及禁用态必须使用同一套玻璃前景色/反色来源；不得在同一卡片内混用 `MaterialTheme`、`appPanelForegroundColor` 与写死黑白色，导致部分文字随应用主题、部分文字随壁纸。新增或改造玻璃表单时必须同时验收明亮与暗色壁纸。
- 已有成熟 Morph 必须直接复用其轨迹几何、时序、背景深度、内容交接和返回锚点。首页三点菜单进入课程管理采用“首页三点菜单 → 教务导入”同款链路：从真实一级菜单 bounds 出发，返回真实三点按钮，不得另写一套近似动画，也不得返回一级菜单假锚点。
- 手动导入进入历史、AI 历史列表及历史详情的既有 Morph 不得被课程管理或其他新页面的公共样式改动污染；过渡壳的 clip、RenderEffect 和 Offscreen 合成仅在运动阶段持有，到达稳定全屏终点必须释放，防止真实页面持续被裁切。
- 跨 Activity 的二级/三级页只能使用项目已有的单向 Backdrop 与快照交接结构；弹窗必须挂到完成内容录制后的根层，不能让消费者采样自身所在 producer，不能在 Activity 首帧先显示空页面再切真实页面，也不能因 Morph 的临时 clip 裁切终态标题、卡片或安全区。
- 首页课程编辑器的星期、节次、周次、单双周入口使用箭头行，圆角只裁切按下/选择时的灰态反馈，静止状态不得常驻圆角灰底；折叠后的视觉高度必须与旧胶囊一致。首页仍点击箭头行打开原居中选择弹窗；课程管理详情页的周次入口才允许原地折叠展开，且展开状态必须真实驱动内容测量、点击和动画。
- 视觉与性能优化不得改变已经验收的尺寸、圆角、排版、玻璃采样、折射、色散、模糊强度或开关行为；若采用低分辨率 GPU 模糊、GraphicsLayer 预热或缓存，只能替换等效渲染负载，Open 终态仍必须是实时可交互的真实内容。
- 周视图长按移动与右下角缩放是互斥手势：卡片被长按抬起后必须立即屏蔽源卡缩放角标触控；缩放按连续像素高度跟随并只在越过明确节次阈值时量化，不能因角标自身移动重复累加位移。松手飞行、冲突退回和合法落点必须复用同一套已验收的弹簧轨迹，只改变终点，禁止为保存交接另写生硬的补间动画。
- 邻卡余震必须使用带加速度的连续阻尼曲线，首尾速度趋近 0；不得用线性剩余时间截断振荡，导致弹两次后无减速地突然停住。

## 仓库与构建

- 工作目录：`D:\Android studio\CourseSchedule`
- 集成分支：`main`
- GitHub：`origin`；Gitee：`gitee`
- Java：`D:\Android studio\JDK`
- Gradle 用户目录：`C:\Users\23085\.gradle`
- Release 构建：

  ```powershell
  $env:JAVA_HOME='D:\Android studio\JDK'
  $env:GRADLE_USER_HOME='C:\Users\23085\.gradle'
  .\gradlew.bat assembleRelease --console=plain
  ```

- GitHub Release APK：`app\build\outputs\apk\github\release\app-github-release.apk`
- 设备验证只安装签名 Release，不安装 Debug。安装前运行 `adb devices -l`；无线地址会变化。
- 可以代为覆盖安装 Release；在用户明确要求时可以启动应用、操作界面并执行真机 UI 自动化。

## 当前基线

- 已发布正式版为 `v1.1.5`，旧包名 `com.example.courseschedule`，`versionCode=25`，Room v36；该版本已冻结。
- 开发版为 `1.2.0` / `versionCode=26`，正式包名 `com.xiaomanjun.sleepdownschedule`，Room v37，含 `github/store` 渠道和 `.debug` 隔离。
- Release 签名保存在仓库外的长期 keystore，用户级 Gradle properties 已配置；不要把证书路径或密码写入仓库。
- 旧包升级通过 v1.1.5 导出 `.sleepdown`、新包恢复完成；`BackupFormatV1` 保持兼容。迁移与持久化审计见 `docs/migration/`。

## 当前实现状态

- Home 动画保留三个独立的渲染/内容 domain。一级三点菜单继续使用已验收的 `homeThreeDotMenuTrajectoryGeometry()` 右缘锚定 choreography：440ms Opening 前 28% 从 pressed button footprint 保持中心 X 下落并收成 18dp 液滴（36–72dp 自适应落距），随后沿 Quadratic Bézier 扩展，扩展进度 40% 处叠加 12dp 纵向回弹，末段保留 0.8% 尺寸脉冲；动态 shape 只在 Opening/Closing 负责 morph clip，内部 Kyant surface 始终使用真实静态 30dp shape，进入 Open 后释放外层 clip，由 Kyant 自己完整绘制高光描边和阴影。个性化面板重新由专用 `homePersonalizationTrajectoryGeometry()` 固定复用 `HomeMorphEasingStyle.Legacy`，时序为 430ms / 310ms；只恢复旧 Morph 轨迹与内容交接，不回退当前官方 `BlurEffect`、GraphicsLayer 预录制、Preparing 预热和 Open 稳态真实内容链路。`HomeMenuDestination` 恢复 1.1.5 的独立 direct geometry，时序为 330ms / 350ms：Opening 从真实一级菜单 bounds 直接长成目标，Closing 直接收回真实三点按钮，不再经过 72dp 中液滴、一级菜单 waypoint、Opening rebound、Closing sink 或 440ms / 285ms 一级菜单时序。教务导入与独立课程管理 Activity 共同调用同一个 `homeMenuDestinationTrajectoryGeometry()`；两者退出全屏的第一帧立即恢复 46dp 动态壳圆角，再连续收敛至真实三点按钮的 21dp 圆角，Open 终态继续释放 clip。课程管理跨 Activity 的稳定内容坐标按真实窗口 bounds 补偿，背景快照支持教务导入同款 1.08x/12dp 景深，并通过只录制背景的单向 Backdrop 向同参数 `LiquidPanel` 供样；不得再各写近似 renderer。外层 Kyant `LiquidPanel` 仍在 Preparing 阶段常驻预热，lens 限制为 12dp height / 16dp amount，个性化和二级页原有的内容预热、GraphicsLayer 缓存、动态壁纸/全屏终态圆角逻辑保持不变。
- 三点菜单当前六操作外壳为 194×317dp、30dp 圆角；选中胶囊有效圆角 19dp，左/右/底同心间距均为 11dp，使最下行选中胶囊与外壳圆弧同轴对齐；玻璃 surface tint 为浅色 0.28、深色 0.40。
- 个性化面板和三个弹窗式菜单目的页保留真实液态玻璃、动态壁纸采样及开关动画。运动阶段避免根 detail layer 重录，内容使用 GraphicsLayer 缓存；不得恢复 Bitmap/ImageBitmap/RenderNode 截图路线。
- 三点菜单新增“课程管理”入口，关闭菜单后通过现有二级页深度转场进入独立 `CourseManagementActivity`，不嵌在三点菜单弹窗中。Activity 按课程名归并全部安排并用课程色卡片展示，卡片经现有 `AnchoredDetailActivityMorph` 进入全屏课程详情；详情可统一改名、仅在彩色模式显示课程级配色、添加安排、编辑星期/节次或四列自定义时间/周次/教师/地点/备注，并支持左滑删除安排。自定义时间保存为 `customStartTime/customEndTime`，周视图按实际时间比例定位，起止时间在卡片上下边缘水平居中叠加且不参与网格测量，卡片可直接跨越节次格；长按编辑时禁止节次拖拽和缩放，冲突检测按真实分钟区间。课程级颜色 `customColorArgb` 在单色模式下不显示且不覆盖全局单色。Room 36→37 迁移、`.sleepdown`、SleepDown 口令、ICS、通知、今日助手和组件链路均保留这些字段。
- 首页周视图长按编辑使用独立悬浮玻璃卡路线：编辑态为 Pager 保留 12dp 上方跨轴绘制带，首排删除键可覆盖表头且退出动画不被裁切；删除键和缩放角标使用错峰弹出/缩回。卡片从 0.965× 弹起至 1.07×，位移和高度用阻尼弹簧追随手指原始目标，松手速度提供有上限的惯性投影；指尖与卡片分别复用 Kyant `InteractiveHighlight` 弥散光，Android 13 以下使用局部径向渐变回退。落地使用低阻尼回弹、局部加色涟漪和邻卡纯 GraphicsLayer 位移/缩放/旋转波动；保存后悬浮卡与 Room 返回的真实卡片做 135ms 互补交接，不先卸载悬浮卡，避免闪回原位。取消拖拽同样先弹回源位再交回真卡。
- 周视图打开个性化、菜单目的页或课程编辑器时，首页中性场景按课表与帧状态键复用 GPU `GraphicsLayer`，缓存层位于原缩放/模糊深度效果之下；仅在缓存实际替代周视图绘制期间把该背景层切为 `Offscreen`，结束后恢复 `Auto`，使课程卡 Kyant 子层不参与运动阶段的逐帧重放，但不卸载其 Composition 状态。运动中若因源按钮/课程交接改变帧键，完成新帧录制后必须当帧直接重放并保持 `Offscreen`，不得再额外完整绘制一次周视图或往返切换合成策略。周视图 Pager 始终保留相邻页，个性化逐帧预览自动绕过缓存。原连续 RenderEffect 模糊、独立缩放层、弹窗挂载/预热、backdrop 传递及全部视觉参数保持不变；课程编辑器表单在目标尺寸布局完成并连续录制两帧后才进入 Opening，Opening/Closing 复用该预热层，Open 仍为真实可交互内容；底部 `ProjectPagerIndicator` 不进入表单录制层，改由同一目标尺寸/变换容器实时绘制，以保留 `BottomCenter` 父布局定位；关闭末段的日卡克隆复用真实日卡文字内容，周卡克隆同步真实周卡的个性化/大屏字体缩放，避免切回真实卡片时排版跳变。不得把这些优化改回固定尺寸外壳或截图缓存，也不得用固定模糊层混合替代连续模糊。
- 个性化滑块支持快速拖动隐藏面板、吸附点、逐帧预览合并和局部 override。液态玻璃设置映射为 0%–50%=0x–1x、50%–75%=1x–2x、75%–100%=2x–4x，UI 中点仍为 50%。
- 首页日期区总高 42dp：日期 21sp 加粗，周次 14sp 次级灰色。首页系统状态栏图标按壁纸顶部实际可见亮度自适应，离开首页恢复跟随应用主题。
- “跳转周数”中心弹窗的标题、说明和 NumberPicker 前景色按 `quickSheetBackdropModifier` 实际卡片明暗反色，不跟随首页壁纸文字明暗；操作按钮仍沿用原 QuickSheet 材质与主色。
- AI 教务导入会话输入框会识别窗口是否已被 IME resize，只补一次底部 inset；AI 导入附件全屏 Morph 到达 Open 后圆角归零并取消外壳裁切/离屏合成。
- 手机周视图节次栏为 56dp，课程主体保留 8dp 右余量；相邻页滑动内容可延伸进该区域。大屏保留原 tablet 布局。
- 官网源码位于 `sleepdown-site/`，线上仍由已预付杭州 ECS 直出；不要在未授权时创建 OSS Bucket、CDN 域名或其他按量资源。
- `SleepDown-Server/` 是私有后端源码，不属于公开 Android 仓库或任何 GitHub Release 的发布范围；不得暂存、提交、推送或上传其中的源码、构建产物、数据库、环境文件、管理配置、API Key、加密密钥或其他服务端凭据。

## 已验证与待验收

- 最新独立 Morph、缓存、课程管理、自定义时间与周视图长按编辑策略通过 46 项 `HomeAnchoredMorphGeometryTest`、9 项 `HomeMotionPerformancePolicyTest`、11 项 `WeekEditMotionTest`、5 项 `DetailMorphBlurTest` 和 2 项 `CourseManagementTest`；完整 `testGithubDebugUnitTest` 为 344/344。本轮通过 `assembleRelease`（GitHub/Store Kotlin、R8、资源优化、lintVital、签名打包），APK 使用既有 Xiaomanjun RSA-4096 证书和 v2 scheme；benchmark Kotlin 编译仍沿用上一个基线结果。
- 2026-08-19 14:33 构建并覆盖安装了 Rebase 前的签名 GitHub 预发布校验包到无线 PLJ110（`1.2.0` / `versionCode=26`，SHA-256 `DEB87CEE2EF1E9B68A469A937D245D0C239B998EA011D8CE29BC2A9901463A81`）；安装结果为 `Success`，助手未启动或操作应用。最终 GitHub Release 附件以 `v1.2.0` 发布页记录的哈希为准。
- 2026-08-18 17:50 构建了包含退出圆角、课程管理同构壳层与托管 AI 密钥配置的签名 GitHub Release（`1.2.0` / `versionCode=26`，SHA-256 `84529B2D40F380B2B813908B379E2DD4F1633753699DCD8C4CC397DC37DF96C3`）；Release `BuildConfig` 已确认远端 AI 开关启用且密钥非空。遵照当前要求未安装、未启动或操作应用。
- 2026-08-18 03:20 构建了当前签名 GitHub Release（`1.2.0` / `versionCode=26`，SHA-256 `A2442309BA562DD36927E84855658B5FB302734D1998CC1048BB921B4E26CE81`）。当时 `adb devices -l` 与 mDNS 均未发现设备，最后已知 PLJ110 地址 `192.168.1.2:36749` 明确拒绝连接，因此该包尚未安装；没有启动或操作应用。
- 最新 Release 已于 2026-08-18 01:11 覆盖安装到 PLJ110（`1.2.0` / `versionCode=26`，SHA-256 `CD806EEDF3CBD614601CB7475B110558B951D91750FA5197C64F69C53E9B418F`）；助手未启动或操作应用。
- 2026-08-17 15:26 另构建了包含长按编辑动效的 Release（SHA-256 `395965712A7C7A82DFCFAC232217D2A5DA2542176479A90A31E4DAC1AAFA7F56`），遵照用户要求未安装。
- 用户仍需优先验收：周视图长按编辑的首排删除键层级、按钮/角标弹出、1.07× 抬起、阻尼跟手/惯性/缩放、指尖与卡片弥散光、落地回弹/邻卡涟漪及保存交接；从三点菜单进入独立课程管理 Activity、课程卡全屏详情及左滑删除、四列自定义时间和周视图真实比例/拖拽锁定/上下边缘居中时间标注、新增课程冲突提示、课程编辑器反色、三点菜单 11dp 三边同心间距，以及既有个性化/二级页/课程编辑器无缝动画与页码提示符定位。
- 完整 backup、四变体、benchmark compile、真实 v1.1.5 恢复、Store 权限与新包 Widget 首装仍待发布前补跑。
- 性能 benchmark 路线按当前任务需要启用；已有诊断代码、trace 结论和失败方案记录在 `docs/performance/UI_PERFORMANCE_BENCHMARK_HANDOFF.md`。

## 下一步

1. 等待用户完成上述真机视觉与交互验收，按反馈做小范围修正。
2. 发布前补齐迁移、备份、四变体及 Store/GitHub 渠道验证。
3. 确认 Draft PR、应用商店身份和升级说明后，再由用户决定是否推送、合并远端或发布；当前不要发布。

## 工作方式

- 默认使用 PowerShell 7；搜索优先 `rg` / `rg --files`，源码修改使用 `apply_patch`。
- 先诊断并保留证据，再修复；崩溃优先读取实际堆栈。
- GitHub 仓库、PR、Issue、Review、CI 和 Release 优先使用已连接的 GitHub 能力；本地 `git` 用于工作树、分支、暂存和提交。
- 更新日志与 Release Notes 必须面向最终用户书写，以用户能直接感知的功能、体验、交互和升级注意事项为中心，避免 Morph、GraphicsLayer、Backdrop、Room、applicationId、RenderNode 等实现细节和内部术语。Release Notes 的安装或迁移注意必须写在开头，主体更新内容必须与应用内更新日志一致；应用内日志不写只对开发者有意义的内部说明。
- `.gradle-user-home/`、`tmp/`、`sleepdown-promo/`、`ui.xml`、根目录设备截图和临时验收图片不属于源码提交范围，除非用户单独指定。
