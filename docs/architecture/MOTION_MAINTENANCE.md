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

**先区分时钟和可见进度。** Morph 的 `LinearEasing` 经常只是原始时钟，位置、尺寸、轮廓、内容交接随后分别应用曲线。要改路径节奏，应修改所属路线的纯计算；给时钟再叠一层 easing 会同时改变预热、交接和背景的时间关系。普通 `tween` / `spring` 路线则直接由 Compose 驱动。设置页的 Miuix 曲线包含原有阻尼计算，不用看起来相近的 cubic 替换。

**时长与内容保留是同一个修改范围。** 课程编辑的 Morph、背景与表单有独立时钟，参数集中在 `CourseEditorMotion`，播放仍在原 host。关闭时还要查看 app 中等待关闭时长的课程移除和刷新交接。首页菜单中途返回会按当前进度缩短关闭，不能只检查完整开合。横屏切页的输入屏蔽和 `onMovingChange` 跟随实际 transition 状态，不另设计时器。

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
