# 动效维护入口

金标跨 Activity 动画的能力、路线门控、SDK 边界与资源释放见 [TRANSITION_FRAMEWORK.md](../TRANSITION_FRAMEWORK.md)。参数由 SDK 管理；应用内 Morph 仍使用下述维护入口。

通用主题判断位于 `core/ui/theme/AppTheme.kt`，纯色板/编解码/色差位于 `model/CourseCardPalette.kt`；玻璃计数与逐帧诊断位于 `glass/GlassPerformanceDiagnostics.kt`，通用帧统计、动画 Trace 和壁纸缓存仍位于 `core/performance/Performance.kt`。这些入口的归位不改变颜色、日志标签、帧计算或视觉参数。

动效按实际路线维护：公共交互放 `core/ui`，首页与课程编辑保留功能自己的曲线和帧计算，跨 Activity 放 `transition`，材质渲染与临时资源协议放 `glass`。下面的入口用于定位修改范围，不提供一套覆盖所有路线的全局曲线。

## 从界面找到入口

以下源码路径相对 `app/src/main/java/com/xiaomanjun/sleepdownschedule/`。

| 要修改的效果 | 参数与计算入口 | 播放、状态与释放入口 |
| --- | --- | --- |
| 横屏侧栏中的页面缩放、模糊和淡入淡出 | `core/ui/interaction/PaneTransitions.kt` 的 `LandscapePageMotion` | 同文件 `LandscapePageTransition`；功能页只传目标和内容 |
| 设置中的竖屏详情推进与返回 | `feature/settings/SettingsNavigationMotion.kt` 的 `settingsDetailTransition` | `SettingsScreen.kt` 提供方向；导航栈与 Saver 留在 `SettingsNavigationState.kt` |
| 课程卡片到编辑器、背景缩放、表单逐行入场 | `feature/course/editor/CourseEditorMotion.kt` 的 `CourseEditorMotion` 与 `legacyCourseEditorMorphSpec` | `CourseEditorContainerOverlay.kt` 保留时钟、预热、内容交接和卸载；app 延迟移除也引用同一关闭时长 |
| 首页切换、卡片跟随与中断反向 | `feature/home/HomeSwitchMotion.kt` 顶部的页面、弹簧与错峰参数 | 同文件 `HomeSwitchMotion`，包括速度续接、密集页面策略及坐标冻结 |
| 三点/加号菜单液滴体积和内容显隐 | `feature/home/HomeDropletMotion.kt`；附加参数在 `overlay/HomeMorphGeometry.kt` 的 `ThreeDotMenuMotion` | `overlay/HomeAnchoredMorphOverlay.kt` 与 `HomeLiquidMorphSpecs.kt` |
| 个性化弹层、首页锚点轨迹 | `feature/home/overlay/HomeMorphGeometry.kt`；共享轨迹在 `transition/legacy/LegacySharedObjectGeometry.kt` | `HomeAnchoredMorphOverlay.kt`，材质适配在 `HomeLiquidMorphSpecs.kt` |
| 菜单到目标表单/教务页 | `transition/legacy/LegacyMenuDestinationGeometry.kt` | 首页 `HomeMenuDestinationOverlay.kt` 与跨 Activity 的 `AnchoredDetailActivityMorph.kt` 各自播放 |
| AI 助理卡片展开为会话 | `feature/agent/DayAgentMorphGeometry.kt` | `DayAgentConversationDialog.kt` |
| 跨 Activity 详情、设置、课程管理卡片 | `transition/legacy/DetailMorphOverlay.kt` 与 `AnchoredDetailActivityMorph.kt` 的对应路线 | `ActivityTransitionCoordinator.kt`、`CrossActivityTransitionHost.kt` 及 Legacy host；业务从 `TransitionRoute` 接入 |
| 中心 Dialog、Alert、Picker 与常规 Popup | 公共入口在 `core/ui/designsystem/SleepDownDialog.kt`、`core/ui/settings/GlassMiuixPopup.kt`；底层使用配置中的本地 Miuix 来源 | 公共 host 保留退出内容和 underlay；来源与补丁见 `settings.gradle.kts`、`patches/README.md` |
| 横屏表单飞入 | `core/ui/designsystem/LandscapeMenu.kt` | 同文件 `LandscapeMenuOverlay`，由表单明确接入 |
| QuickSheet 内容切换与尺寸变化 | `core/ui/designsystem/SleepDownQuickSheet.kt` | 同文件公共 host |
| 通用弹出缩放/淡入 | `core/ui/interaction/OverlayTransitions.kt` 的 `popEnterTransition` / `popExitTransition` | 各调用者的 AnimatedVisibility |
| 周视图页尾与课程移除 | `feature/home/week/WeekPageTailMotion.kt`、`overlay/CourseRemovalMotion.kt` | 对应周视图/首页 host |
| 凝光原生按钮/菜单按压形变 | `platform/coloros/ColorOsCapsuleMotion.kt`、`ColorOsMenuMotion.kt` | 订阅现有 COUI motion sink，生命周期由各 binding 释放 |

## 修改时一起看哪些部分

**内容预载与连续打断。** 两种材质的课程编辑和个性化均在 Preparing 完成表单布局，目标尺寸布局通过图层变换跟随外壳。打开侧统一使用 `glass/LiquidMorph.kt` 的 `liquidPreloadedContentAlpha`，按实际展开量 24%～62% 互补叠化；各 Morph spec 的 `earlyOpeningContentHandoff` 在初始路线和打断后重播路线均开启。关闭侧保留原策略：凝光原始时钟 35%～70%，液态实际展开量 24%～62%。预载只决定内容策略，课程、个性化和加号菜单均保留独立的原始关闭轨迹、收缩和投影，不能沿打开曲线倒放代替返回。

`core/ui/interaction/InterruptibleMorph.kt` 的正常运动使用路线自身时长与曲线，不给原始时钟再套弹簧。按用户最新反馈，卡片中途反向由 `MorphCurveContinuity.kt` 捕获当前可见帧，把当前矩形作为原路线的新起点（重开）或展开端点（返回），直接重播所属路线的原始曲线与原始时长。没有继承速度、预先制动、残差空间轨迹或额外分段。新的单一线性时钟只表示原曲线的经过时间；重复打断继续捕获当时实际画面。完整开合逐帧沿用原曲线，透明度从当前值按原交接曲线过渡到目标。此前[速度弧线调查](../reports/2026-10-24-world-velocity-retarget-research.md)保留为历史，已由本次明确需求取代。独立背景深度时钟保留原续接。运动期上滑/返回可反向，稳定后恢复表单滚动。

`CourseEditorSession.kt` 让每张飞行卡片持有自己的表单、时钟与资源；同卡片重开复用原会话，关闭 A 时打开 B 不重置 A。普通 Preparing/Opening/Closing 期间由首张卡片持有输入，点击其他站位不能触发切换；只有该卡片已经被主动中途反向后才允许新的独立会话。新卡片同样拥有自己的输入限制。`SinglePointerTapGate.kt` 在 Initial pass 只观察多指、不消费事件；一次多指手势中的课程点击全部拒绝，保留周视图缩放和编辑手势。

`RetainedMorphLayer.kt` 在凝光退出期复用 GPU 录制绘制视觉，并将表单输入树置于首页下方；回程保留初始源 Rect 的无绘制热区。液态模式直接绘制各自实时外壳，通过外层运动手势路由原站位与其他卡片点击，避免整层录制回放丢失材质/描边。课程开场不再截 Bitmap 或替换源卡片材质，源文字实时布局，与表单共享同一染色/描边外壳。外壳源端使用半分辨率无折射模糊，12%～52% 展开量叠化到已有全屏模糊；稳定大弹窗释放局部采样，局部运动采样不冻结坐标。点击原站位可重开尚未归位的同卡片，不受背景缩放影响。运动期点击先按固定原站位路由，再查可见课表卡片。Open 后不额外录制。背景深度取各会话最大需求，仍共用完整 underlay。完成回调只释放自己的 Idle 会话，切周等后续动作等待真实终态，不能依赖原先固定关闭时长。

彩色课程文字的软阴影在 `core/ui/text/CourseCardText.kt` 使用既有背景采样选择极性：亮背景为白色软阴影，暗背景为黑色软阴影；不增加采样次数或改变运动期冻结策略。普通单色文字仍沿用原逻辑。

**课程光效有自己的绘制边界。** 凝光课程和拖动副本保留内部轮廓及 Dock 焦散，由 `platform/coloros/ColorOsCourseCardGlow.kt` 持有原生参数与开关；`ColorOsCourseCard.kt` 为该原生 View 提供有限的外扩容器，外扩量读取同一套 layout/SDF 资源，仍按真实卡片尺寸采样和布局。焦散不带普通暗色投影，文字单独裁切；不要重新让整个卡片父节点按轮廓裁掉外扩光，也不要移除整页滚动裁切。课程编辑大表单不拥有课程焦散。拖动副本不再接收跟手触摸，独立 halo 继续由 `WeekEditOverlayHost` 定位，`ColorOsFollowLight` 在 Compose 和原生 Canvas 裁切。detach/release、遮挡与系统开关均由原生表面的既有生命周期清理。

**先区分时钟和可见进度。** Morph 的 `LinearEasing` 经常只是原始时钟，位置、尺寸、轮廓、内容交接随后分别应用曲线。要改路径节奏，应修改所属路线的纯计算；给时钟再叠一层 easing 会同时改变预热、交接和背景的时间关系。普通 `tween` / `spring` 路线则直接由 Compose 驱动。设置页的 Miuix 曲线包含原有阻尼计算，不用看起来相近的 cubic 替换。

卡片原曲线重播使用 Compose tween，独立背景的自定义续接按 `MotionDurationScale` 对帧间时间累加；正常与中断运动遵循相同系统倍率，倍率为 0 时完成到目标并清理运动状态。不能用系统墙钟绕过倍率，也不能在倍率变化时用「总经过时间 / 新倍率」重算整个进度。

**时长与内容保留是同一个修改范围。** 课程编辑的 Morph、背景与表单有独立时钟，参数集中在 `CourseEditorMotion`，播放仍在原 host。关闭时还要查看 app 中等待终态的课程移除和刷新交接。首页菜单中途返回重播所属路线原关闭时长，不能只检查完整开合。横屏切页的输入屏蔽和 `onMovingChange` 跟随实际 transition 状态，不另设计时器。

**相似外观不自动共享路线参数。** 课程编辑、课程管理 Activity 与 Agent 的位置/尺寸曲线目前相似，但时长、内容和资源边界不同，保持独立入口。首页与跨 Activity 确实共用的几何则只在 `transition/legacy` 维护一份。首页深度归一化接收装配层提供的编辑器缩放值，不反向读取课程功能常量；修改缩放时保留这些显式调用。

**渲染边界与轨迹分开维护。** `glass/LiquidMorph.kt` 定义帧协议、generation 和清理时机；`GlassTransitionLayer.kt` / `GlassMorphAllocation.kt` 负责材质覆盖与分配，`GlassLiquidMotion.kt` 负责投影。路线通过既有 `LiquidMorphSpec` 提交帧。修改曲线通常不需要增加 Controller、改变 Backdrop producer 或延长 Offscreen 资源寿命。运动期的临时裁剪与模糊在稳定端释放，退出内容保留到原释放点。

## 最小验证

纯参数定位或职责迁移按 [AGENTS.md](../../AGENTS.md) 编译，并对照迁移前的数值、算式与调用时序。调整实际时间规则、状态机或帧计算时，再运行关联的现有回归：

- `GlassFrameworkTest`：Morph 协议、控制器与课程编辑帧。
- `HomeSwitchMotionTest`：切换、反向、速度与内容保留。
- `HomeAnchoredMorphGeometryTest` / `HomePersonalizationGeometryTest`：锚点轨迹与布局边界。
- `HomeMotionPerformancePolicyTest` / `DetailMorphBlurTest`：背景深度、模糊与稳定端。
- `TransitionSessionStateMachineTest`：跨 Activity 会话与资源释放。

实际视觉或流畅度修改再对同一路线验证完整开合、中途返回、旋转、不同窗口/字体和明暗材质；构建与数学回归不能替代这些观察。结构迁移不宣称改善帧率。依赖检查继续由 `scripts/check_architecture.py` 约束，公共交互不读取功能页面状态。
