# SleepDown 课程表：开发交接

## 项目约束

- Android 课程表，核心特色是 Miuix 风格、液态玻璃视觉和 AI/Agent 融合。
- 保留现有功能、视觉、动画、模糊/折射/色散效果及第三方开源库引用；性能优化不得靠删除既有效果完成，除非用户明确要求。
- 数据安全优先。数据库、配置、备份恢复和升级必须提供迁移路径，不得主动清除或覆盖用户数据。
- UI 按窗口和安全区自适应，不为单一 DPI、分辨率或设备写死特例。
- 工作树可能包含用户素材和临时证据；不要使用 `git reset --hard`、`git checkout --`，不要删除或顺手整理不在任务范围内的文件。
- 未经明确同意，不推送、不打标签、不发布 Release。
- 验证必须按改动风险选择最小充分集合；不得进行大量耗时、无意义或与已通过检查重复的防御性测试。只有相关源码、构建配置或验收条件发生变化，或用户明确要求时，才重跑对应完整测试矩阵。
- 用户未明确要求构建变体时，只构建 Release；不要默认附带构建 Debug、benchmark 或其他渠道/变体。
- 开发与测试阶段构建 Release 时，为节省时间和资源可以跳过资源压缩；只有发布前最终验收、用户明确要求，或改动本身涉及资源压缩/R8 时，才执行带资源压缩的完整 Release 构建。
- 自 2026-08-23 起，液态玻璃性能优化每完成一批实际修改，都必须开启该批对应的真实实验开关，构建签名 Release 并覆盖安装到已连接设备；不能只提交默认关闭的死代码。已被用户停止的液态动效开关不得随性能包重新开启。除非用户另行要求，安装后不自动启动或执行额外真机测试。

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

- 开发/测试阶段需要跳过 Release 资源压缩时，使用 `-Psleepdown.skipReleaseResourceShrink=true`；该开关不关闭 R8 代码压缩，且未显式传入时仍保持正式 Release 的完整资源压缩。

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
- 周视图打开个性化、菜单目的页或课程编辑器时，首页中性场景按课表与帧状态键复用 GPU `GraphicsLayer`，缓存层位于原缩放/模糊深度效果之下；仅在缓存实际替代周视图绘制期间把该背景层切为 `Offscreen`，结束后恢复 `Auto`，使课程卡 Kyant 子层不参与运动阶段的逐帧重放。开启大玻璃性能实验时，实质遮挡路线从 Opening 起使用 0.5×、四分之一像素面积的低开销冻结层；背景预建 32 个 RenderEffect，但 Opening/实时预览保持原 12 档节奏并在前 38% 达到原有 12dp 最大值，只有 Closing 使用全部 32 档；不用清晰帧与模糊帧做双层透明叠画。首个完全匹配且已接管的缓存帧后即可暂停周课程卡的昂贵材质节点。右上角 194×317dp 三点菜单不构成实质覆盖，禁止卸载课程卡材质。卡片内容、布局、点击、语义和 Composition 始终保留。关闭请求立即开始原 Closing，不等待预热；Opening 不变，Closing 使用端点平滑的 `smootherstep(remainingProgress^0.55)` 从中段卸载并在终点归零。课程卡材质在剩余 40%、背景仍约有 69% 模糊遮挡时重新挂载并录制一次。Closing 不再等模糊归零时同时从 0.5× 冻结纹理切回全分辨率；在仍有 42% 模糊强度（约 5dp）时提前切回已录制的全分辨率单层，再用 32 档降到 0，从而把采样分辨率交接藏在模糊下。个性化滑块 preview 必须硬绕过 staged blur。运动中若因源按钮/课程交接改变帧键，完成新帧录制后必须当帧直接重放并保持 `Offscreen`，不得再额外完整绘制一次周视图或往返切换合成策略。周视图 Pager 始终保留相邻页。原最大模糊强度、独立缩放层、弹窗挂载/预热、backdrop 传递及全部视觉参数保持不变；课程编辑器表单在目标尺寸布局完成并连续录制两帧后才进入 Opening，Opening/Closing 复用该预热层，Open 仍为真实可交互内容；底部 `ProjectPagerIndicator` 不进入表单录制层，改由同一目标尺寸/变换容器实时绘制，以保留 `BottomCenter` 父布局定位；关闭末段的日卡克隆复用真实日卡文字内容，周卡克隆同步真实周卡的个性化/大屏字体缩放，避免切回真实卡片时排版跳变。不得把这些优化改回固定尺寸外壳、截图缓存或逐帧新建 RenderEffect。
- 个性化滑块支持快速拖动隐藏面板、吸附点、逐帧预览合并和局部 override。液态玻璃设置映射为 0%–50%=0x–1x、50%–75%=1x–2x、75%–100%=2x–4x，UI 中点仍为 50%。
- 首页日期区总高 42dp：日期 21sp 加粗，周次 14sp 次级灰色。首页系统状态栏图标按壁纸顶部实际可见亮度自适应，离开首页恢复跟随应用主题。
- “跳转周数”中心弹窗的标题、说明和 NumberPicker 前景色按 `quickSheetBackdropModifier` 实际卡片明暗反色，不跟随首页壁纸文字明暗；操作按钮仍沿用原 QuickSheet 材质与主色。
- AI 教务导入会话输入框会识别窗口是否已被 IME resize，只补一次底部 inset；AI 导入附件全屏 Morph 到达 Open 后圆角归零并取消外壳裁切/离屏合成。
- 手机周视图节次栏为 56dp，课程主体保留 8dp 右余量；相邻页滑动内容可延伸进该区域。大屏保留原 tablet 布局。
- 官网源码位于 `sleepdown-site/`，线上仍由已预付杭州 ECS 直出；不要在未授权时创建 OSS Bucket、CDN 域名或其他按量资源。
- `SleepDown-Server/` 是私有后端源码，不属于公开 Android 仓库或任何 GitHub Release 的发布范围；不得暂存、提交、推送或上传其中的源码、构建产物、数据库、环境文件、管理配置、API Key、加密密钥或其他服务端凭据。

## 液态玻璃 2.0 统一框架（2026-08-23）

- 液态玻璃 2.0 统一框架与性能改动已于 2026-08-24 通过本地合并提交 `d88ced5` 合入 `main`；原开发分支 `codex/liquid-glass-framework` 保留在 `dcce5ee` 作为合并前可回退指针。Oplus 半成品仍由本地分支 `codex/archive/oplus-transition-wip-20260823`、提交 `70c2d51` 封存；玻璃分支此前通过 `3e56624` 对齐 `origin/main` 历史。所有提交仍仅在本地，未推送、未打标签、未发布。
- Backdrop 已在独立提交 `eab3059` 升级到正式版 `2.0.0`，`shapes=1.2.0` 不变；Kotlin/Compose 无需联动升级。依赖升级与框架改造保持可独立回退。
- `app/src/main/java/com/xiaomanjun/sleepdownschedule/glass/` 统一管理采样域、材质 token、场景阶段、provider/consumer、诊断、稳定 envelope、课程卡合批原型和 `LiquidMorphController/Spec`。业务代码不再直接创建/组合/挂载 `LayerBackdrop` 或调用 `drawBackdrop`/`drawPlainBackdrop`；`ScaledBackdrop` 只保留必要的坐标变换接口实现。
- 首页 `Background`、`Content`、`PickerScene` 三个域继续独立；`ChromeCombined` 只组合前两者，Dialog 继续使用屏幕坐标补偿。Debug/benchmark 首页拓扑会拒绝自采样、域错配和循环。
- 三点菜单、个性化、菜单目的页和课程编辑器已接入 Legacy Morph spec/controller；原轨迹、时序、圆角和内容交接保持不变。源码默认仍可回退到 `ReferenceOnly`；当前性能验收包按用户要求实际开启 `sleepdown.enableLargeGlassExperiment`，而独立弹簧和速度/加速度形变保持关闭。
- 阶段二第一批为首页三点菜单进入“添加课程 / 手动导入 / 教务导入”接入固定 RenderTarget 的稳定 envelope 实验。它只替换原本逐帧改变尺寸的外层 clip，内部 Kyant surface、lens SDF、材质参数、内容真实尺寸和 Legacy 330/350ms 几何不变；非全屏 Open 只保留目标尺寸圆角 clip，教务导入全屏 Open 释放转场 layer。
- 阶段二第二批为大屏个性化面板的渐进 blur 与 Backdrop aura 增加独立固定 RenderTarget。`GlassInsetLens` 按 Backdrop 2.0 正式版 rounded-rect Shader 语义实现 envelope 内动态 rect/radius SDF，不再用逐帧 Modifier `.size()` 驱动折射；原动态渐变、边框、内容、alpha、feather mask、材质参数和采样域不变。主面板/aura envelope 面积上限为最终目标的 `1.65x` / `1.45x`，超限逐通道回退 Reference。手机个性化 `LiquidPanel` 与课程编辑器仍保持 Reference。
- 阶段二第三批已接入稳定周视图课程卡：同一采样域、同材质且不重叠的课程卡最多 8 张共享一条 blur/lens 效果链，使用本地多 rounded-rect SDF 保留每卡折射边界；每卡 tint、高光、外阴影、内阴影、内容、手势和语义仍为全分辨率独立节点。高负载时只降低昂贵采样层：0–7 张为 `1.0x`，8–11 张为 `0.75x`，12 张及以上为 `0.5x`；纹理面积分别约为 100%/56%/25%。12 卡测试旧包实际仍处于 1×，新阈值使其从静止到 Pager 滑动始终保持稳定 0.5×，不在手势首帧换 RenderTarget。Kyant 2.0 没有直接 sample-scale API，本地通过小尺寸 consumer layer 与官方 `layerBlock` 坐标反补偿实现。编辑、拖拽、冲突、运动中或不满足合批条件时回退逐卡 Kyant，但仍可应用同一采样阈值。多卡闪退包含两个独立入口：合批 union 曾把 `Outline.Generic` 交给 lens；逐卡高负载回退又曾把通用 `DensityScaledShape` 包装交给官方 lens。当前合批使用受支持的 `CornerBasedShape` 宿主并在 `onDrawBackdrop` 做真实多圆角 union 裁切；周卡降采样直接构造保持具体类型的 `RoundedRectangle(cardCorner * scale)`，任何不能提供受支持等价形状的调用强制回退 `1.0x`，禁止再以通用 Shape 包装进入 Backdrop 2.0 lens。
- 大玻璃性能开关按真实窗口分辨率管理周课程卡材质：卡片完全离开窗口上下边界后只卸载 Backdrop surface/decoration，布局、文字、手势和语义继续 Composition；纵向预热带为窗口短边 12%，限制 `72dp–160dp`，仅在距离缩小、即将返回时重建。真机反馈横向相邻页在第一次拖动时才预热会把 shader 重建挤到手势首帧，因此 Pager 保留的相邻周改为常驻材质，只保留纵向离屏卸载；横向进一步优化需整页空间合批。`GlassGroup` 成员只在跨越上下窗口/预热边界时更新。每卡绝对 bounds 使用非观察型最新引用，仅真实宽度变化才写 Compose State；点击、长按锚点和余震绘制仍读最新值。启动、编辑/拖拽、冲突交接与活动 overlay 期间禁用门控。实质遮挡 Closing 不再一次性重挂全部课程玻璃：从进度 0.88 起分 8 波恢复，当前周按列从中央向两侧占前 4 波，Pager 相邻周占后 4 波；每波在隐藏 `GraphicsLayer` 中预热，画面继续重放原精确缓存，全部完成后才把完整预热层接入正常缓存，最终交接帧不再承担 Kyant 节点集中分配。
- 壁纸课程取色先按 Palette 实际人口筛选，剔除低占比暗色与偏离主色族的低占比离群色；课程色明度保持在可读区间，补色只在主色相附近 `±28°` 内生成，不再用黄金角跳到与壁纸无关的绿/紫色。4×2 小组件普通预览不再把自定义背景/玻璃框与 RemoteViews 文字拆成两个独立缩放层，平板下统一由同一 RemoteViews 像素坐标系绘制；透明裁剪编辑仍保留专用无背景路线。
- 课表详细设置把“上午/下午”合为基础分段开关；启用时至少同时存在上午和下午，关闭时只重分配现有节次数量、不删除课程或自定义时间。中午、晚上改为互不回撤的独立可选段，基础分段关闭时二者禁用并灰显；重新启用会优先恢复本次编辑会话内最近一次有效分布。
- 阶段二早期实验包覆盖安装后，用户肉眼观察“好像还没有什么帧数变化”；当时没有课程卡合批、降采样或遮挡生命周期。第三批首包在 6 卡同屏时用户已主观观察到掉帧减少，但没有 Macrobenchmark/Perfetto 数据，不得描述为量化收益；同包暴露的多卡闪退、三点菜单错误卸载和首次返回预热等待已在后续源码修正，新包仍待用户观察。
- 阶段三三点菜单动效实验已由用户明确停止。第一版轻微速度/加速度 outline 实现仍保留在源码和本地归档分支 `codex/archive/three-dot-outline-motion-wip-20260823`，但 `sleepdown.enableLiquidMotionExperiment` 保持关闭且不再继续调参；第二版精确 Issue #70 尺寸/圆角弹簧曾位于 `a0976d5`，随后通过 `c958c75`、`69b0e56` 两个本地 revert 完整撤销，当前 tracked 文件树与实验前 `1e86605` 一致。后续优先性能，不再改三点菜单轨迹、曲线或内容交接，除非用户重新明确授权。
- 诊断 counter、实验边界、测试与后续启用门槛见 `docs/performance/LIQUID_GLASS_FRAMEWORK.md`。当前设备已覆盖安装开启阶段二大玻璃性能开关、关闭阶段三液态动效开关的受控 Release；该包包含合批与逐卡两条 shape 崩溃修复、自适应采样、Opening 遮挡卸载、前置逐级模糊和 Closing 末段材质恢复，未由 Codex 启动或操作。
- 本轮不修改或恢复 Oplus 调查；`TODO(OPLUS_DEFERRED_20260823)`、callback、Bundle、leash、fallback 和远程 allowlist 继续保持暂停状态。

## 跨 Activity Transition 统一框架（2026-08-22 重建）

目的：业务层只声明路线、Intent 和锚点，由统一控制层选择现有 Legacy renderer 或按路线开放的 ColorOS ViewSeamless；不得在页面中直接选择动画后端。现有 Morph 的几何、时序、easing、glass、Backdrop、快照和首尾帧参数仍留在原 renderer。

- `app/src/main/java/com/xiaomanjun/sleepdownschedule/transition/`
  - `TransitionRoute.kt`：集中路线表；每条路线声明目标 Activity、`Anchored` / `Depth` / `PlatformDefault` / `TaskReturn` Legacy profile、窗口策略和 native policy。
  - `TransitionSession.kt`：每次跳转独立 `TransitionSessionId` 与状态机 `Created → SourceReady → NativeRegistered → NativeRunning | LegacyRunning → Open → Closing → Finished/Cancelled/Failed`；支持 `parentSessionId` 和 callback generation。
  - `TransitionPayload.kt`：按 session 保存打开/返回锚点、独立软件快照、背景快照、真实 source 恢复回调与 exactly-once 清理句柄；Intent 只传 route/session 标识。
  - `ActivityTransitionCoordinator.kt`：统一 `open`、`openImmediate`、目的页预处理和 close；同步拒绝、异常、无效锚点、`onAnimationStart` 120ms 超时或 end-before-start 均回到同一路线的 Legacy profile。
  - `CrossActivityTransitionHost.kt`：`NativeRegistered` 时保持真实 Legacy 起始帧；仅同 session 的 `onAnimationStart` 可释放临时 Morph 壳并显示已预组合的真实页面。
  - `OplusSeamlessBackend.kt`：按 session 管理带真实圆角 outline 的临时 source View、回调、返回 bridge、纵向拖动即时清理和 source-resume watchdog；不存在全局 `activeSession`。能力 gate 只看运行时类/API/版本/feature、窗口形态、锚点/软件位图和 opaque 目标策略，不看 manufacturer、brand、ROM 属性或动画等级。
  - `OplusVendorCallbackFactory.kt`：唯一直接引用厂商 callback 类的隔离适配层；通用代码只反射加载，R8 keep 位于 `app/proguard-rules.pro`。
- 生产 Oplus 开关由 `RemoteTransitionConfig` 的全局开关与逐路线 allowlist 双重控制，缺失配置默认 Legacy；当前 allowlist 为空，所有正式路线仍保持原效果。
- Legacy 组件继续使用 `AppTheme.TranslucentMorph`；只有路线 native 注册成功后才改写 Intent 到独立的 opaque 宿主组件（当前为课程详情和 AI 历史），日夜模式分别提供匹配背景。注册拒绝时仍启动原 Legacy 组件。
- 旧 Probe Activity、`ForceOplusDetailTheme`、硬编码 `FullOpaqueNoGlass` 路由和生产临时日志已移出 main；二分入口只在 `app/src/debug/.../OplusTransitionDebugHarness.kt`。
- 同 Activity 的首页弹层、历史详情、课程编辑器、周视图拖拽和定制多课表 `CustomizeUiState` 未迁移。详细接入说明见 `docs/TRANSITION_FRAMEWORK.md`。

## ColorOS ViewSeamless（Oplus 无缝转场）专题

### 依赖与接入方式

- `compileOnly("com.oplus.animation:viewseamless:1.0.0@aar")`（官方文档 13771 指定作用域）。AAR 内为 no-op stub（`setSeamlessView=false`、`getVersion=-1`），真实能力由 ColorOS 系统实现提供；全部通过反射访问，缺类/版本/方法差异/异常均安全回退 `AnchoredDetailActivityMorph`。
- public API：`OplusViewSeamless.setSeamlessView(View, Context, Bundle, AnimationCallback)`、`finishCurrentAnimation()`、`skipBackAnim(Activity)`、`setSkipViewSeamless(Activity)`、`setForceLeashAlphaOut(Activity, boolean)`、`getVersion()`、`AnimationCallback` 三个回调；常量含 `VIEW_SEAMLESS_OPEN/CLOSE`、`BUNDLE_COLOR/RADIUS/RECT/BITMAP/FORCE_LEASH_ALPHA_OUT/ALPHA_OUT_ON_POSITION_CHANGE/VIEW_VISIBLE/LIST_COVER/VIEW_WITH_ALPHA`、`OS_16_0_BASE=37000`/`OS_16_1_BASE=38000`/`OS_17_0_BASE=40000`。
- 官方条件：ColorOS 16.1+、动效等级 B+ 及以上；受浮窗、兼容模式、平行视窗、分屏等场景限制。官方示例以 `getVersion()>OS_16_0_BASE` 且 `setSeamlessView()` 返回 true 后再 `startActivity(intent, bundle)`。
- SDK 1.0.0 无独立 return-target update API；返回复用 `setSeamlessView` + `VIEW_SEAMLESS_CLOSE` 与源快照/按钮 bridge。

### 真机事实（PLJ110 = ColorOS 17 内测 / Android SDK 37）

- `ro.build.version.oplusrom.confidential=V17.0.0`、`PLJ110_17.0.0.64(SP03CN01)`；公开 `oplusrom=V16.0.0`、display `16.0` 是伪装属性，**不得作为能力 gate**。runtime class `com.oplus.animation.OplusViewSeamless` 存在，`getVersion()=40003`、`isFeatureEnabled()=true`；`persist.sys.oplus.anim_level=1`。无 manufacturer/brand 硬编码 gate。
- 早期曾判定"动效等级=1 低于 B+ → skip anim"为根因，**该结论已被后续实验推翻**：同设备、同 source 下多个 opaque destination 均成功接管。真实根因在 destination 内部结构/渲染层（见二分进度）。
- 兼容层处理过的坑：`view rect isEmpty`（source View 必须 attached/laid out/非空 bounds，用 `doOnPreDraw` 就绪 + 点击前验证）、`HARDWARE bitmap`（仅 SDK Bundle 用独立软件 `ARGB_8888` 副本，原 snapshot 不 recycle）、`skip anim`（透明 window 或低动效等级会触发，见下）、源 View 无 background/在 backdrop material 层（`viewBackground null` → skip；用非 null 背景或 alpha=1 避免"hidden"判定）。

### 二分实验进度（已确认）

| 层 | destination 内容 | theme | 结果 |
|---|---|---|---|
| Native / ComposeView | OplusTestDetailActivity 等 | opaque | ✅ startAnimation + springs |
| EmptyOpaque | `Text("test")` | AppTheme.OplusSeamlessDetail | ✅ 接管 |
| ComposeProbe | `Column{Text("课程管理"); Card{Text("课程1")}}` | 同上 | ✅ 接管 |
| MorphEmptyOpaque | `AnchoredDetailActivityMorph` + `Text("test")` | 同上 | ✅ 接管（Morph 本身不是 skip 因素） |
| FullOpaqueNoGlass | 真实 `CourseManagementDetailPage`，去掉 glass/backdrop/Morph | 同上 | ✅ 接管 |
| 正式 Detail | 完整（TranslucentMorph + Morph + LiquidGlass/backdrop） | TranslucentMorph | ❌ skip anim |

结论：source/API/bundle/snapshot/ComposeView/透明 window（单因素）均正常；**正式页面比 FullOpaqueNoGlass 多出的部分是 LiquidGlass / backdrop / RenderEffect / graphicsLayer / snapshot 层——这些是剩余的最大嫌疑**，逐层恢复实验尚未完成（见下一步）。二分实验用 `OplusBisectRoute` 枚举 + `oplusBisectRoute` 开关在三个 probe Activity 间切换，全部实验 Activity 均为临时诊断代码，结束即删。

### 当前遗留问题（2026-08-23 真机未通过，按用户要求暂缓）

> 统一代码标记：`TODO(OPLUS_DEFERRED_20260823)`。这些代码是尚未奏效的调查尝试，保留用于后续对照，不得描述为已修复，也不得在用户未明确恢复该议题前继续扩散到更多路线。

1. **源位置"漏出一样卡片"**：此前“ColorOS 固有语义”的结论已推翻。PLJ110 的 `oplus-framework.jar` 显示：`BUNDLE_VIEW_VISIBLE=true` 只会在动画启动后把传入 `setSeamlessView` 的那个 View 设为 alpha 0；小红书另有系统 `View.performClick/startActivity` hook、RUS 和 `view_seamless_third_party=true` 专用链路，不能当作普通 public SDK 的同条件对照。应用原先把软件 bitmap 同时作为临时 bridge 背景画进 source Window 和 `BUNDLE_BITMAP` 系统 leash，且 Compose 隐藏不保证旧 Surface buffer 已提交，因此形成底层静止副本。当前 Native 先等待不含业务 source 的 frame commit，再添加“只提供 attached bounds/background metadata、`draw()` 不输出像素”的注册 View；系统动画仍只使用同一 `BUNDLE_BITMAP`。手动导入的 180ms `windowOverlay` 占位已下沉到 `LegacyTransitionBackend`，仅 Parabolic 真正启动时创建，Native 路径从不挂载。
2. **首页返回仍为中心淡出**：首页→课程管理和首页→教务导入的 OPEN 可被 Oplus 接管，但用户要求 CLOSE 使用完整 `HomeMenuDestination` Legacy Morph 返回真实三点按钮。当前 `LegacyOnly` 路线会执行 Morph，并尝试用 `setSkipViewSeamless` 退出 vendor CLOSE、用 Android 14+ `overrideActivityTransition(..., 0, 0)` 清除平台尾动画；2026-08-23 签名验收包真机仍出现中心淡出，说明 CLOSE 的 WM Shell/remote-transition 所有权并未被这组调用可靠移交。不得把它记为“正常 fallback 已实现”，也不得改用会直接跳过 Morph 的 `skipBackAnim`。
3. **课程详情与 AI 历史仍闪空帧**：课程详情 CLOSE 仍闪；AI 进度→AI 历史及手动导入→AI 历史的 OPEN/CLOSE 仍闪。尝试过在 CLOSE 末段对 registration View 恢复 `draw/alpha/visibility`，以及在 AI OPEN 隐藏真实源前挂载精确源 Bitmap overlay、等 `onAnimationStart` 再交接；同一签名验收包确认均未消除问题。不能再以“忘记恢复 alpha”或“缺少 opening overlay”作为已确认根因。
4. **返回后的 bridge/真实 source 清理仍需与空帧分开判断**：exact-session cleanup、CLOSE end、纵向拖动和 700ms watchdog 的竞争结构仍保留，但这只能说明清理归属，不证明系统 leash 消失前已有可见且提交完成的 source buffer；不得据此宣称返回空帧或中心淡出已经解决。

### Oplus 源快照 View 生命周期（当前实现）

以下仅描述当前代码，不代表 PLJ110 视觉验收通过；带 `TODO(OPLUS_DEFERRED_20260823)` 的交接点均为失败后保留的调查证据。

- `OplusSeamlessBackend.open` 先通过 `registerFrameCommitCallback` 确认隐藏真实 source 的帧已提交，再在 source decor 创建带 rounded outline 的 registration-only View；它保留 non-null `BitmapDrawable` 供 ColorOS 校验颜色/可见性，但覆盖 `draw()` 禁止进入应用 Surface。`NativeSessionResource` 持有其 bitmap、callback 和 source Activity 弱引用。
- 打开失败/异常立即按同路线启动 Legacy；打开成功则 registration View 保留贯穿 open→return。真实动画像素只经 `BUNDLE_BITMAP` 提交；`BUNDLE_RADIUS` 使用真实 px，`BUNDLE_VIEW_VISIBLE=true` 允许系统管理注册 View 的 alpha，`BUNDLE_VIEW_WITH_ALPHA=true` 保留圆角外透明像素。
- CLOSE 复用相同 session/source View，并用实时返回锚点更新 bounds、bitmap 和 outline；失败立即转原 Legacy closing，不调用会硬切动画的 `skipBackAnim`。
- CLOSE end、纵向拖动或 700ms watchdog 竞争同一个 exactly-once `TransitionPayloadStore.remove(sessionId)`；禁止再由业务页 `onResume` 提前显示真实 source。

### 演进历史（压缩流水账，含关键 SHA）

- 2026-08-20 已发布分支 `codex/release-1.2.0`（tag `v1.2.0`，commit `73cea36`）以未提交 `--no-commit --no-ff` 合入 `main`，工作树保留，禁止代为 commit/push。
- Home → CourseManagement 打开经 `tryStartOplusViewSeamless`（整三点菜单外壳 bridge + 三点按钮 return bridge，`BUNDLE_RECT` 用 bridge `getBoundsOnScreen()`）；兼容层反射 + 软件 Bitmap + `doOnPreDraw` 就绪验证；失败细分 `sourceBridgeMissing/Detached/NotLaidOut/ZeroSize/BoundsEmpty`。
- 关键验证包 SHA：`2A3EC77E...`、`F8713D68...`、`F9820320...`、`6AEA3B67...`、`566EC05B...`（Home↔CourseManagement 系列，均覆盖安装到 `3B15AE023YL00000`）。
- 2026-08-21 transition 抽象层建立（见上），并完成课程卡→详情的 Oplus 二分（EmptyOpaque → ComposeProbe → MorphEmptyOpaque → FullOpaqueNoGlass 全部成功）。

## 已验证与待验收

- 最新独立 Morph、缓存、课程管理、自定义时间与周视图长按编辑策略继续通过原 344 项测试；统一 Transition 框架新增 20 项路线、状态机、并发 fallback、callback generation、嵌套 session、payload 清理、进程重建、能力 gate 和 kill switch 测试，完整 `testGithubDebugUnitTest` 为 364/364。GitHub/Store Debug、签名 Release（Kotlin、R8、资源优化、lintVital）和两渠道 benchmark app、benchmark 测试 APK 均构建通过。
- 液态玻璃 2.0 统一框架完成后，完整 `testGithubDebugUnitTest` 为 397/397，其中新增框架测试 19 项；GitHub 签名 Release 使用 `-Psleepdown.skipReleaseResourceShrink=true` 通过 Kotlin、R8、lintVital、打包与签名构建，并于 2026-08-23 覆盖安装到 `3B15AE023YL00000`。该包未启动或执行真机验收，不能据此宣称量化性能改善。
- 阶段二在既有 envelope 测试外新增课程卡分组、紧边界 RenderTarget、多 SDF、采样阈值/几何、官方 lens 形状门控、遮挡阶段、模糊时序与预览隔离测试；已停止的阶段三测试仍只保留为关闭路线证据。项目没有 Release unit-test task，且按“只构建 Release”约束未改跑 Debug，故这些新增测试尚未计入 397/397 基线。第一轮仅修合批宿主的包（SHA-256 `572BECCA2D12BCE1BA942AA8ACA03702457DCC9E3D7C3B93380592CD63F95424`）真机仍在逐卡降采样回退路径抛出同一 lens shape 异常；新的 R8 mapping 已将调用定位到 `sleepDownGlassSurface` 默认官方 `lens()`。上一包 `3A1D4408F6453F12B30C1956FFE4CF47FE583BE8B9252BC3FF7DB18F6B1FFF26` 已让用户确认总体掉帧明显减少，但 Closing 模糊卸载不自然且个性化滑块预览被错误套用全局 staged blur。当前最终包明确为 `SLEEPDOWN_LARGE_GLASS_EXPERIMENT=true`、`SLEEPDOWN_LIQUID_MOTION_EXPERIMENT=false`，使用 `--no-parallel --max-workers=2`、跳过资源压缩但保留 Kotlin、R8、lintVital 和签名构建通过；大小 `6,446,118` bytes，SHA-256 `2C5DFE997D447C7A857325829497E3B2D4B9B856BA8824BF98B9AFAE2CB6B8E4`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，未由 Codex 启动或操作。旧 Issue #70、双开关和恢复包均已被该性能包覆盖。
- 最新源码把 Closing 材质恢复从剩余 18% 前移到 40%，模糊改为 `remainingProgress^0.55`，并新增双轴课程卡视口材质生命周期/合批成员过滤、滚动 bounds 非观察型锚点及对应源码测试。只构建启用大玻璃性能、关闭液态动效的 GitHub Release，使用 `--no-parallel --max-workers=2`、跳过资源压缩；Kotlin、R8、lintVital、打包和签名通过，大小 `6,446,118` bytes，SHA-256 `EA6C78A588DF912D5E98DCB2F59159E7706619DB777A2CDBDDAF1441F279CC0D`。设备重新连接后已于 2026-08-24 覆盖安装到 PLJ110 `3B15AE023YL00000`，结果为 `Success`；未由 Codex 启动或操作，真机效果待用户观察。
- 用户确认 `EA6C78A5...` 包横向 Pager 仍卡顿，Closing 主要是模糊卸载点跳变。随后源码将相邻周恢复为常驻材质、只保留纵向视口卸载；背景预建 32 档，Opening 保持原 12 档节奏，Closing 使用全部 32 档并在仍有 42% 模糊强度时从 0.5× 冻结纹理提前切回全分辨率单层，不做双层叠画。对应 GitHub Release 大小 `6,446,118` bytes，SHA-256 `38F38439D90F5744DE52129450517115BE50461EE21703A6C69D853DD1D7483C`；设备随后恢复连接并已覆盖安装到 PLJ110 `3B15AE023YL00000`，结果为 `Success`，未由 Codex 启动或操作。
- 最新源码在上述 Closing 链路上继续加入 8 波空间分组预热，并同时完成课程取色、平板 4×2 小组件预览和时间段开关修正。只使用 `assembleGithubRelease --no-parallel --max-workers=2`，跳过资源压缩但保留 Kotlin、R8、lintVital、打包与签名；生成值为 `SLEEPDOWN_LARGE_GLASS_EXPERIMENT=true`、`SLEEPDOWN_LIQUID_MOTION_EXPERIMENT=false`。APK 大小 `6,446,118` bytes，SHA-256 `5746F742A6A5C4D7773685EBF45314FEBBA7BC71C3F1246E65681B1D72370349`。设备恢复连接后已于 2026-08-24 覆盖安装到 PLJ110 `3B15AE023YL00000`，结果为 `Success`；未由 Codex 启动或操作，也未额外运行 Debug、benchmark 或重复测试。
- 正式 Oplus 全局开关及逐路线 allowlist 当前默认关闭；Release manifest 不含 debug Probe，R8 mapping 保留厂商 callback 隔离层。未完成下述 PLJ110 正式页面验收前不得远程开启。
- PLJ110 已确认完整课程详情的独立 opaque 宿主可由 ColorOS 正常接管；第一批扩展路线同时包含 AI 进度页→AI 历史（Legacy Liquid）和手动导入弹窗→AI 历史（Legacy Parabolic），共用同一个正式 AI 历史页面与独立 opaque 宿主。
- 此前安装到 `3B15AE023YL00000` 的临时强制 Oplus 验收包曾真机确认：首页两条路线 CLOSE 仍中心淡出，课程详情 CLOSE 仍闪空帧，AI 历史 OPEN/CLOSE 仍闪空帧；源码随后恢复远程配置 gate。本轮液态玻璃 Release 已覆盖该临时包，但没有恢复 Oplus 调查或重新验收，因此结论仍是“未修复并暂缓”，不是待用户重复验收。
- 既有其他真机视觉与交互验收项（周视图长按编辑、课程管理、三点菜单 11dp 同心间距、个性化/二级页无缝动画等）不因本轮失败结论而自动失效；后续按具体任务分别验收。
- 完整 backup、四变体、benchmark compile、真实 v1.1.5 恢复、Store 权限与新包 Widget 首装仍待发布前补跑。
- 性能 benchmark 路线按当前任务需要启用；已有诊断代码、trace 结论和失败方案记录在 `docs/performance/UI_PERFORMANCE_BENCHMARK_HANDOFF.md`。

## 下一步

1. **暂停 Oplus 空帧与首页 CLOSE fallback 调查**；用户未明确恢复前，不再调整 callback 时序、overlay、registration View、Morph 几何或 vendor Bundle，也不要求用户重复测试。
2. 若用户以后恢复调查，先固定一个最后已知“不闪空帧”的可复现版本/录像作为对照，同时抓取 WM Shell transition、ActivityTaskManager、ViewSeamless callback 与 SurfaceFlinger/帧提交证据，确定系统 leash、opaque destination 和 source buffer 的真实交接顺序；禁止继续凭视觉猜测叠加延时或快照层。
3. 只有首页 Legacy CLOSE 真正回到三点按钮且所有正式详情/AI 历史 OPEN/CLOSE 无空帧后，才重新做立即返回、长停留、20 次往返、源移动/消失与 fallback 零变化验收；此前全局开关及逐路线远程 allowlist 保持关闭。
4. 发布前补齐迁移、备份、真实 v1.1.5 恢复、Store 权限与新包 Widget 首装；确认 Draft PR、应用商店身份和升级说明后，由用户决定是否推送、合并远端或发布。Oplus 调查分支保持封存；当前液态玻璃任务只允许按既定计划创建本地提交，不得推送、打标签或发布。
5. 液态玻璃下一步只推进性能：冻结阶段三动效；`5746F742...` 包已覆盖安装，由用户重点检查各实质遮挡路线的 Closing 末段是否仍在 Backdrop 恢复瞬间卡顿或抽搐，同时验收课程取色、平板 4×2 小组件背景预览对齐和新的时间段开关。若横向仍明显掉帧，下一步不再做横向卸载/恢复，而是把按星期列的局部合批提升为带面积效率约束的整页空间分块合批，避免双页同时可见时出现 14 条列级效果链；之后用诊断核对消费者层数、Offscreen 像素与 RenderTarget 尺寸。每批实际修改都必须开启对应开关构建并覆盖安装；设备离线时明确记录“构建完成、安装待办”。

## 工作方式

- 默认使用 PowerShell 7；搜索优先 `rg` / `rg --files`，源码修改使用 `apply_patch`。
- 先诊断并保留证据，再修复；崩溃优先读取实际堆栈。
- GitHub 仓库、PR、Issue、Review、CI 和 Release 优先使用已连接的 GitHub 插件；本地 `git` 用于工作树、分支、暂存和提交。
- 更新日志与 Release Notes 必须面向最终用户书写，以用户能直接感知的功能、体验、交互和升级注意事项为中心，避免 Morph、GraphicsLayer、Backdrop、Room、applicationId、RenderNode 等实现细节和内部术语。Release Notes 的安装或迁移注意必须写在开头，主体更新内容必须与应用内更新日志一致；应用内日志不写只对开发者有意义的内部说明。
- `.gradle-user-home/`、`tmp/`、`sleepdown-promo/`、`ui.xml`、根目录设备截图和临时验收图片不属于源码提交范围，除非用户单独指定。
