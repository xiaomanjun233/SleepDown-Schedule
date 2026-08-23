# SleepDown 课程表：无缝转场 Morph 效果完整报告

> 调研日：2026-08-21
> 范围：`app/src/main/java/com/xiaomanjun/sleepdownschedule/`
> 说明：`tmp/` 下的三个历史 worktree（`identity-stage1-publish-worktree` / `release-1.1.5-publish`）仅作历史参考，不计入当前实现。

项目内所有「无缝转场 Morph」按运行域可分为两大类：

1. **同 Activity 覆盖层转场** —— 不启动新 Activity，在某个 Activity 的根 Compose Stack 内按 `zIndex` 叠放一个「运动壳 / 覆盖层」，从源元素长成目标面板（或反向收起），全程一个窗口。
2. **跨 Activity 转场** —— 用 `AnchoredDetailActivityMorph` 引擎包裹目标 Activity 首帧，从源元素快照长成全屏页面，并由 `transition/` 抽象层在 **SleepDown Legacy Morph** 与 **ColorOS ViewSeamless** 两个后端之间选择。

下面是完整梳理。代码引用全部指向当前主干。

---

## 0. 一句话架构

```
                    轨迹几何(trajectory geometry) —— 被两类 Morph 复用
                                    │
          ┌─────────────────────────┴─────────────────────────┐
          │                                                   │
   同 Activity 覆盖层                                   跨 Activity 转场
   Origin: 源元素发生在当前窗口                        Origin: 源元素在另一个 Activity 窗口
   ┌──────────────────────────────────────┐        ┌──────────────────────────────────┐
   │ HomeAnchoredMorphOverlayHost          │        │ AnchoredDetailActivityMorph(引擎) │
   │ HomeMenuDestinationOverlayHost        │───────▶│   ├─ Liquid / DetailSettings      │
   │ AiImportHistoryDetailMorphOverlay     │  复用   │   ├─ Parabolic / CourseDetail    │
   │ DetailScheduleMorphOverlay            │  轨迹   │   └─ HomeMenuDestination         │
   │ WeekEditOverlayHost                   │  几何   │ 快照/背景: MorphSnapshotBackground│
   └──────────────────────────────────────┘        └──────────────────────────────────┘
```

两类 Morph 的底层几何都来自 `homeAnchoredMorphGeometry()` 及一组液滴轨迹族，只是宿主方式不同。

---

## 1. 共用基础（两类 Morph 都依赖）

### 1.1 轨迹几何层

出处：[HomeAnchoredMorphOverlay.kt](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt)

| 函数 | 说明 |
|---|---|
| `homeAnchoredMorphGeometry()` | 最底层受力液滴几何：先收成小液滴（pinch），再沿二次贝塞尔展开到目标；返回值 `HomeAnchoredMorphGeometry{ rect, cornerRadiusPx, sourceScale, sourceAlpha, surfaceAlpha, contentAlpha, pathProgress, expansionProgress }`（[L261-L440](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L261-L440)） |
| `homeThreeDotMenuTrajectoryGeometry()` | 右缘锚定生长（三点菜单的尾部锚定语义）(`homeLiquidSharedObjectTrajectoryGeometry` 实现)（[L763-L795](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L763-L795)） |
| `homeCenteredSharedObjectTrajectoryGeometry()` | 中心生长变体，用于大面板（rect 来自贝塞尔中心 ± 宽高）（[L801-L834](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L801-L834)） |
| `homePersonalizationTrajectoryGeometry()` | 个性化面板专用，固定 `HomeMorphEasingStyle.Legacy` 时序（430ms/310ms）（[L525-L554](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L525-L554)） |
| `homeMenuDestinationTrajectoryGeometry()` | 教务导入 / 课程管理共用轨迹（330ms/350ms），Opening 从一级菜单直接长成目标，Closing 直接收回真实三点按钮（[HomeMenuDestinationOverlay.kt#L235-L264](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeMenuDestinationOverlay.kt#L235-L264)） |

时序/`easing` 常量集中在文件头部（[L107-L173](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L107-L173)）：

- Opening 430ms / Closing 360ms（个性化 440/285ms）
- 背景缩放 `HomeAnchoredMorphBackgroundScale=1.08`，`HomeAnchoredMorphBackgroundDurationMillis=460`，delay 20ms
- `ThreeDotMenuMotion`：Open 440ms / Close 285ms，pinch 0.28、18dp，落距 36–72dp，扩展 40% 处 12dp 纵向回弹，末段 0.8% 脉冲
- `HomeMorphEasingStyle`:`Directional`（通用、线性相位钟+方向立方曲线） vs `Legacy`（恢复旧版 easing 栈：`HomeAnchoredFallEasing`/`OpenPositionEasing`/`OpenSizeEasing`/`CloseEasing`）

辅助函数：`lerpHomeMorph()`、`homeMorphSmoothStep()`（[L2307-L2315](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L2307-L2315)）。

### 1.2 背景快照层

出处：[MorphSnapshotBackground.kt](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/MorphSnapshotBackground.kt)

- `MirroredEdgeSnapshot`：用 `BitmapShader(MIRROR)` 镜像填充被缩放露出的边缘，避免黑边（[L84-L128](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/MorphSnapshotBackground.kt#L84-L128)）。
- `MorphSnapshotBackground`：唯一的「冻结首页背景」，中心图随 `backgroundScaleProvider`（0.92–1.08）缩放+模糊，收缩时用 `EvenOdd` 挖空路径 + 18dp 羽化描边形成景深圆角（[L135-L214](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/MorphSnapshotBackground.kt#L135-L214)）。
- `morphSnapshotDepthProgress()`：把缩放 1.0→0.92/1.08 映射为 0→1 深度进度（[L216-L217](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/MorphSnapshotBackground.kt#L216-L217)）。

---

## 2. 同 Activity 无缝转场 Morph（覆盖层）

共同特征：在单一宿主里叠「运动壳」，经历 **Idle → Preparing → Opening → Open → Closing → Disposing** 六态（枚举见 [HomeAnchoredMorphOverlay.kt#L197-L209](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L197-L209)）；运动阶段掐动态 clip + Offscreen 合成，Open 稳态释放 clip/离屏，内容保持实时可交互。

### 2.1 首页三点菜单 / 个性化面板 `HomeAnchoredMorphOverlayHost`

- 宿主入口：[HomeAnchoredMorphOverlay.kt#L973-L1465](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L973-L1465)
- 接线于 [ScheduleUi.kt#L2517](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/ScheduleUi.kt#L2517-L2540)
- `HomeAnchoredOverlayKind`：`Add`（三点菜单壳）/ `Personalize`（个性化面板）
- 目标矩形：
  - `homeAddMenuTargetRect()`：194×317dp、30dp 圆角壳，19dp 选中胶囊、11dp 同心间距（[L442-L483](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L442-L483)）
  - `homePersonalizeTargetRect()`：手机/平板两套尺寸规则（[L882-L949](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L882-L949)）
- 渲染细节：
  - Add 菜单用 `HomeAddMenuMorphPanel`（`LiquidButton` 玻璃采样，浅色 tint 0.28 / 深色 0.40）（[L2053-L2305](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L2053-L2305)）
  - Personalize 用 `HomePersonalizationAnimatedOverlay` + `DeferredHomePersonalizeMorphPanel`，内容用两个 `GraphicsLayer`（清晰 + 模糊）做 crossfade，大屏带 `DeferredProgressivePersonalizeSurface` 渐进 backdrop 模糊与 `DeferredPersonalizeBackdropAura`（[L1506-L1840](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L1506-L1840)）
  - `DeferredHomeMorphShape`：延迟动态 shape，避免每帧重算圆角（[L1467-L1503](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeAnchoredMorphOverlay.kt#L1467-L1503)）
  - `registerOplusViewSeamlessSource = phase == Open` 时挂 `OplusHomeCourseManagementSourceBridge`（供「课程管理」跨 Activity 打开）

### 2.2 首页菜单目的页 `HomeMenuDestinationOverlayHost`

- 宿主入口：[HomeMenuDestinationOverlay.kt#L304-L831](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeMenuDestinationOverlay.kt#L304-L831)
- 接线于 [ScheduleUi.kt#L2680](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/ScheduleUi.kt#L2680)
- `HomeMenuDestinationKind`：`AddCourse` / `ManualImport` / `EduImport`（[L63](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeMenuDestinationOverlay.kt#L63)）
- 「教务导入」目的地为全屏（`isFullScreen`，目标圆角 0、背景 1.08x/12dp 景深），Open 终态释放 clip/Offscreen
- `HomeMenuDestinationLegacyMotion`：Open 330ms / Close 350ms（[L140-L148](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeMenuDestinationOverlay.kt#L140-L148)）
- `homeMenuDestinationRenderedCornerRadiusPx()`：全屏关闭首帧恢复 46dp 中间圆角，再收敛到真实三点按钮 21dp（[L182-L219](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeMenuDestinationOverlay.kt#L182-L219)）
- 内容用 `destinationContentLayer`（GraphicsLayer）在 Preparing 阶段预录两帧，Opening/Closing 只重放层，Open 走实时真树
- **内嵌**：此宿主任内嵌 `AiImportHistoryDetailMorphOverlay`（手动导入历史条目 → 历史详情），`zIndex(500f)`（[L794-L829](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeMenuDestinationOverlay.kt#L794-L829)）

### 2.3 导入历史详情 `AiImportHistoryDetailMorphOverlay`

- 定义：[AiImportHistoryDetailActivity.kt#L155-L260](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AiImportHistoryDetailActivity.kt#L155-L260)
- 本身是「历史行 → 历史详情」的**同 Activity 覆盖层**，内部复用一个 `AnchoredDetailActivityMorph(motionStyle = DetailSettings)`。
- 两处宿主：
  - `AiImportHistoryActivity`（历史列表页内，跨任务但同窗口内应用）（[AiImportHistoryActivity.kt#L117-L154](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AiImportHistoryActivity.kt#L117-L154)）
  - `HomeMenuDestinationOverlayHost`（首页手动导入的内在历史）（[HomeMenuDestinationOverlay.kt#L796](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/HomeMenuDestinationOverlay.kt#L796)）
- 过程：`onSourceHandoff` 隐藏对应历史行（记录 hiddenEntryId），`onClosed` 恢复；`onImportRequested` 触发最终导入并返回主界面。

### 2.4 课表详细设置 `DetailScheduleMorphOverlay`

- 定义：[DetailMorphOverlay.kt#L136](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/DetailMorphOverlay.kt#L136)
- 接线于 [ScheduleUi.kt#L3011-L3029](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/ScheduleUi.kt#L3011-L3029)
- 快照摄取：[ScheduleUi.kt#L2830 附近](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/ScheduleUi.kt#L2830-L2839) 冻结首页帧 → 生成背景+源卡片 Bitmap
- 时序：`DETAIL_OPEN_DURATION=520` / `DETAIL_SYSTEM_BACK_DURATION=370` / `DETAIL_TOOLBAR_BACK_DURATION=400`；easing `DetailOpenEasing`(quartic out)、`DetailExitEasing`(cubic out)、`BackgroundOpenEasing`/`BackgroundExitEasing`
- 渲染：`DetailMorphValues`（背景 alpha、源卡片 alpha、内容 alpha、translation、scale、clipBottom、progress）+ `DetailMorphClipShape` 联动圆角；`detailMotionBlurRadiusDp` 正弦模糊（8dp 峰值）

### 2.5 周视图长按编辑 `WeekEditOverlayHost`

- 控制器 `rememberWeekEditOverlayController` / `WeekEditOverlayController`：[WeekScheduleUi.kt#L1869-L1920](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/WeekScheduleUi.kt#L1869-L1920)
- 宿主 `WeekEditOverlayHost`：[WeekScheduleUi.kt#L799-L1230](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/WeekScheduleUi.kt#L799-L1230)
- `WeekEditOverlayMode`：`Move` / `Resize`
- 若干 `Animatable`：`overlayX/Y/Height/Scale/Alpha/Reveal/Lift/Rotation`、`realCardLandingAnimation`、`landingImpactAnimation`、`landingRippleAnimation`
- 关键交互约束（AGENTS.md）：
  - 长按移动与右下角缩放**互斥**；卡片被抬起后立即屏蔽源卡缩放角标
  - 0.965× → 1.07× 弹起、弹簧阻尼追随、落地低阻尼回弹 + 邻卡连续阻尼余震（首尾速度趋零）
  - 「悬浮卡」与 Room 返回真卡 135ms 互补交接，不先卸载

---

## 3. 跨 Activity 无缝转场 Morph

### 3.1 引擎 `AnchoredDetailActivityMorph`

出处：[AnchoredDetailActivityMorph.kt#L186-L375](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L186-L375)

- 入参：`sourceBounds/collapseBounds`、`sourceCornerRadius/collapseCornerRadius`、三张快照（`background/source/collapse`）、`motionStyle`、`sourceContent`、`content(requestClose)`、`onFinished/onSourceHandoff/onCloseRequested`
- 提供 `BackHandler`、开闭双向进度 `Animatable`、背景缩放 `backgroundScale`

`AnchoredDetailMotionStyle`（[L86-L92](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L86-L92)）— 五种形态：

| Style | 渲染实现 | 说明 |
|---|---|---|
| `Liquid` | [AnchoredLiquidStyleMorph](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L886-L973) | 经典液滴 pinch→膨胀（`homeAnchoredMorphGeometry` + `Legacy` easing） |
| `DetailSettings` | [AnchoredSettingsStyleMorph](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L975-L1131) | 单移动层缩放 + `AnchoredDetailClipShape` 联动圆角（`parabolic=false`） |
| `Parabolic` | 同 `DetailSettings`，`parabolic=true` | 抛物线弧线 |
| `CourseManagementDetail` | [AnchoredCourseEditorStyleMorph](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L735-L884) | 课程卡双弧线抛物线（`courseManagementDetailTrajectoryGeometry`），独立于首页编辑器 |
| `HomeMenuDestination` | [AnchoredHomeMenuDestinationStyleMorph](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L382-L657) | 跨 Activity 版教务导入轨迹；`destinationFirstOpening` 时内容已装好、源菜单不再回放；Open 终态释放 clip/Offscreen |

通用细节：
- `detailMorphUsesTransientClip(progress, closing)`：`closing || progress < 0.999f` 才维持 transient clip（[L100-L101](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L100-L101)）
- `anchoredStableContentOffsetPx()`：跨 Activity 真实窗口 bounds 补偿（[L179-L184](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L179-L184)）
- Opening 前用两帧 `withFrameNanos` 预组合目标页 + `onSourceHandoff()`（[L260-L305](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L260-L305)）

### 3.2 快照管道

- `AnchoredMorphSnapshots{ background, source, collapse }` + `AnchoredMorphSnapshotStore`（ConcurrentHashMap，上限 6，token=Long）（[L94-L121](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L94-L121)）
- START：`Activity.startActivityWithAnchoredMorph(intent)` → `startActivity` + `overridePendingTransition(0,0)`（抑制系统默认转场）（[L130-L134](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AnchoredDetailActivityMorph.kt#L130-L134)）
- 目的 Activity 在 onCreate 通过 `intent.anchoredMorphSnapshotTokenOrNull()` 取回快照 token

### 3.3 跨 Activity 路由（接线）

| 路由 | 起点 → 终点 | MotionStyle | 文件:行 |
|---|---|---|---|
| 课程管理 | 首页三点菜单 → `CourseManagementActivity` | `HomeMenuDestination` + `destinationFirstOpening` + `suppressOpening`(Oplus 门禁) | [CourseManagementUi.kt#L225-L278](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/CourseManagementUi.kt#L225-L278) |
| 课程详情 | `CourseManagementActivity` 课程卡 → `CourseManagementDetailActivity` | `CourseManagementDetail` | [CourseManagementUi.kt#L472-L509](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/CourseManagementUi.kt#L472-L509) |
| 导入历史 | 首页/教务入口 → `AiImportHistoryActivity` | `Liquid` 或 `Parabolic`(extra) | [AiImportHistoryActivity.kt#L69-L99](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AiImportHistoryActivity.kt#L69-L99) |
| 历史详情(Activity版) | `AiImportHistoryActivity` → `AiImportHistoryDetailActivity` | `DetailSettings` | [AiImportHistoryDetailActivity.kt#L60-L89](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/AiImportHistoryDetailActivity.kt#L60-L89) |

> 说明：即便某些 Morph 内容上是「同窗口覆盖层」（如历史详情），只要它是独立 `ComponentActivity`，打开/返回仍走 `AnchoredDetailActivityMorph` 引擎 + 快照管道，因此归入「跨 Activity」。

### 3.4 Transition 抽象层（后端选择）

出处：[transition/](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/transition)

| 文件 | 作用 |
|---|---|
| [TransitionBackend.kt](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/transition/TransitionBackend.kt) | `TransitionBackend{ open/close/supportsDestination }` + `TransitionResult{ Started/Fallback/Unsupported }` |
| [TransitionSnapshot.kt](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/transition/TransitionSnapshot.kt) | `TransitionSnapshot{ sourceBounds, sourceBitmap, backgroundBitmap, sourceCornerRadiusPx }` + 预留 `TransitionSnapshotProvider` |
| [LegacyMorphBackend.kt](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/transition/LegacyMorphBackend.kt) | 包装 `startActivityWithAnchoredMorph`，Morph 本体/几何/时序/玻璃/快照完全不动 |
| [OplusSeamlessBackend.kt](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/transition/OplusSeamlessBackend.kt) | 包装 `tryStartOplusCourseDetailSeamless`；状态机 `REQUESTING→REGISTERED→RUNNING→FALLBACK`，仅 `RUNNING`（收到 `onAnimationStart`）才旁路 Morph |
| [TransitionController.kt](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/transition/TransitionController.kt) | `openCourseDetail/close`：Oplus 可用 + 目标为 opaque `AppTheme.OplusSeamlessDetail` + 设备支持 → Oplus；否则 Morph；`Fallback` 自动落到 Morph |

选择规则（`shouldUseOplus` / `isOplusDestinationReady`）：目标 Activity 的 manifest theme 必须为 `AppTheme.OplusSeamlessDetail`（opaque window），否则透明窗口会让 ColorOS 系统 `skip anim`。调试开关 `ForceOplusDetailTheme=false`（[TransitionController.kt#L18](file:///d:/Android%20studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/transition/TransitionController.kt#L18)）。

### 3.5 Oplus 源快照 View 生命周期

- `OplusSeamlessBackend.open` 等待真实 source 已隐藏的 source Window frame commit，再向源 decor 挂载 registration-only View。该 View attached、laid out、保留 non-null bitmap background 与真实 rounded outline 供 ColorOS 校验，但覆盖 `draw()`，不会把同一快照再录进应用 Surface；动画像素只通过 `BUNDLE_BITMAP` 交给系统 leash。
- 打开失败/异常立即按同 route 回退 Legacy；成功则 session 独占的 registration View 贯穿 open→return。CLOSE 使用同一 View，并以实时返回 anchor 更新 bounds、bitmap 和 outline。
- CLOSE end、返回后首个纵向拖动与源 `onResume` 的 700ms decor watchdog 竞争同一个 exact-session cleanup；释放 registration View 与恢复真实业务 source 恰好执行一次。
- 手动导入的 180ms source placeholder 已由业务 `windowOverlay` 移入 `LegacyTransitionBackend`，只在 Parabolic fallback 真正启动时挂载，Oplus 路径完全不会提交这层占位。

---

## 4. 已知遗留问题 / 验收项（源自 AGENTS.md）

1. **源位置「漏出一样卡片」**：已确认不是 ColorOS 必然语义。PLJ110 对小红书存在 system hook/RUS/`third_party` 专用接管；public SDK 路径的应用必须自行保证源帧只提交一次。当前以 clean frame-commit barrier + non-drawing registration View 修复，待课程详情、AI 进度→历史和手动导入→历史三条正式路线真机复验。
2. **卡片缩回后残留**：已改为 session-scoped CLOSE end/纵向拖动/700ms watchdog 三路 exactly-once 清理，待长停留返回和首个上下滑复验。
3. **正式详情页**：独立 opaque 正式宿主已可被系统接管，旧 Probe/硬编码二分路线不进入 main 源集；任何正式路线只要仍出现重复源或残留就继续保持 Legacy。

---

## 5. 对应测试

| 测试 | 覆盖 |
|---|---|
| `HomeAnchoredMorphGeometryTest`（44 项） | 轨迹几何 / 时序 / 圆角 / 边界 |
| `DetailMorphBlurTest`（5 项） | 生成 blur 对比基线 |
| `WeekEditMotionTest`（11 项） | 周视图长按编辑 / 缩放弹道 |
| `CourseManagementTest`（2 项） | 课程管理归并 / 详情 |
| 全量 `testGithubDebugUnitTest` | 364/364（含统一 transition 路线、状态机、session、fallback、callback 与能力 gate） |

---

## 6. 文件地图（当前主干）

```
app/src/main/java/com/xiaomanjun/sleepdownschedule/
├── AnchoredDetailActivityMorph.kt      # 跨 Activity Morph 引擎 + 5 种 style + 快照管道
├── DetailMorphOverlay.kt               # 同 Activity 课表详细设置 Morph
├── HomeAnchoredMorphOverlay.kt         # 轨迹几何族 + 三点菜单/个性化覆盖层 Host + LiquidPanel
├── HomeMenuDestinationOverlay.kt       # 教务导入/添加课/手动导入覆盖层 Host + destination 轨迹
├── MorphSnapshotBackground.kt          # 冻结首页背景 / 镜像边缘 / 圆角景深
├── AiImportHistoryActivity.kt          # 跨 Activity 导入历史（Liquid/Parabolic）+ 内嵌历史详情覆盖层
├── AiImportHistoryDetailActivity.kt    # 历史详情 Activity（DetailSettings）+ 同窗口历史详情 Morph
├── CourseManagementUi.kt               # 跨 Activity 课程管理/课程详情接线 + Oplus 门禁
├── WeekScheduleUi.kt                   # 周视图长按编辑覆盖层/控制器（Move/Resize）
├── ScheduleUi.kt                       # 首页接线（HomeAnchored/HomeMenuDestination/DetailMorph）
├── transition/                         # 双后端抽象层（LegacyMorph / OplusSeamless）
│   ├── TransitionBackend.kt  TransitionController.kt  TransitionSnapshot.kt
│   ├── LegacyMorphBackend.kt  OplusSeamlessBackend.kt
└── Oplus*ProbeActivity.kt              # Oplus 二分诊断探针（临时代码，结束即删）
```

---

## 7. 结论

- **同 Activity Morph**（单一窗口内覆盖层）：`HomeAnchoredMorphOverlayHost`（三点菜单/个性化）、`HomeMenuDestinationOverlayHost`（添/导入目的页）、`AiImportHistoryDetailMorphOverlay`、`DetailScheduleMorphOverlay`、`WeekEditOverlayHost`；共享轨迹几何、五态机、GraphicsLayer 预录 + LiquidPanel 玻璃采样。
- **跨 Activity Morph**：唯一的 `AnchoredDetailActivityMorph` 引擎 + 5 种 `AnchoredMotionStyle`，经 4 条 Activity 路由 + `transition/` 抽象层（Morph / ColorOS ViewSeamless 双后端）驱动。
- 两者底层几何同源（`homeAnchoredMorphGeometry` 及液滴轨迹族），差异仅在**是否启动新 Activity** 与**背景/快照的宿主方式**。
