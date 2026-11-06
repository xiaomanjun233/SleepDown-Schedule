# COUI Dock 迁移与默认凝光（参数、实现与验证记录）

日期：2026-11-06  
基线：`d5176dc5`  
分支：`codex/couikit-dock`

## 1. 范围

- 从已确认的 `d5176dc5` 建立独立分支，保留当时的工作树修改与本地素材。
- 把首页导航胶囊从反射加载的 `COUIBottomFloatingNavigationBar` 迁到 `third_party/coui-kit` 的独立胶囊入口。
- 加号继续使用现有平台实现、菜单、隐藏和返回交接；本轮不绑定融合链。
- Android 14 及以上使用新 Dock；旧系统或 renderer 创建失败时回到既有原生/液态呈现，偏好不回写。
- 本轮同时把未保存偏好的默认值改为凝光，并把模式选择从品牌、ColorOS 版本和厂商实验渠道中拆开。

## 2. 系统底栏参数与来源

### 2.1 加载来源

旧实现通过 `ColorOsNativeUi` 反射加载 `com.coui.appcompat.bottomfloatingnavigationbar.COUIBottomFloatingNavigationBar`，资源、`View` 类和主题来自同一系统 APK，并通过 `OnItemSelectedListener` 接入选中的受控状态。系统类没有打包进应用。

系统反编译参考（只读）：

- `E:\COUI\work\src7\sources\com\coui\appcompat\bottomfloatingnavigationbar\COUIBottomFloatingNavigationBar.java`
- `E:\COUI\work\src7\sources\com\coui\appcompat\bottomfloatingnavigationbar\BaseBottomFloatingMenuView.java`
- `E:\COUI\work\src7\sources\com\coui\appcompat\bottomfloatingtoolbar\COUIBottomFloatingToolbarItemView.java`
- `E:\COUI\work\src1\sources\com\coui\appcompat\COUIMaterialBlurEffect.java`

资源值：

- `E:\COUI\二级页面布局调查\04-完整反编译\Settings-apktool\res\values\dimens.xml`
- `E:\COUI\二级页面布局调查\04-完整反编译\Settings-apktool\res\values\colors.xml`

### 2.2 尺寸、间距与字体

| 项目 | 系统值 | 来源 |
| --- | --- | --- |
| 底栏高度 | 56dp | `coui_bottom_floating_navigation_bar_height` |
| 单项高度 | 48dp | `coui_bottom_floating_navigation_bar_item_height` |
| 单项最大宽度 | 70dp | `coui_bottom_floating_navigation_bar_item_max_width` |
| 菜单内边距（左右） | 12dp | `coui_bottom_floating_navigation_bar_item_padding` |
| 单项间距 | 8dp | `coui_bottom_floating_navigation_bar_item_space` |
| 单项背景横向内缩 | 8dp | `coui_bottom_floating_navigation_bar_item_bg_horizontal_padding` |
| 图标上边距 | 4dp | `coui_bottom_floating_navigation_bar_item_icon_top_margin` |
| 图标与文字纵向间距 | 2dp | `coui_bottom_floating_navigation_bar_item_icon_title_gap_vertical` |
| 图标与文字横向间距 | 4dp | `coui_bottom_floating_navigation_bar_item_icon_title_gap_horizontal` |
| 底栏横向安全边距 | 16dp | `coui_bottom_floating_navigation_bar_padding_horizontal` |
| 无额外上下留白最小高度 | 88dp | `coui_bottom_floating_navigation_bar_min_height_without_padding_vertical` |
| 与滑选视图最小间距 | 12dp | `coui_bottom_floating_navigation_bar_Minimum_spacing_with_slide_view` |
| 触摸光半径 | 40dp | `coui_bottom_floating_navigation_bar_touch_effect_radius` |
| 图标尺寸 | 24dp × 24dp | `coui_floating_toolbar_item_icon_width/height` |
| 标签字号 | 10dp（`setTextSize(0, dp2px(10f))`） | `COUIBottomFloatingToolbarItemView` |
| 标签字体 | `sans-serif-medium` | 系统 item view |

系统底栏在宽屏时把左右附件（FAB）按 56dp 预留，菜单最大 250dp（3 项：3×70 + 2×8 + 2×12）。旧应用宿主在底部加 18dp 横向边距、8dp 底部间距和系统导航区 inset；这些由旧宿主保证，不属于系统类。

标签颜色状态：

- 禁用：`couiColorLabelTertiary`
- 选中 / checked：`couiColorLabelPrimary`
- 按下 / 默认：`couiColorLabelPrimaryVariant`

应用覆盖（旧实现）：`ColorOsHomeComponents.dockIconColors` 把选中图标和文字的 checked/selected 颜色改为墙纸强调色，未选中保留 `couiColorLabelPrimaryVariant`，禁用保留 `couiColorLabelTertiary`。

### 2.3 模糊与颜色层

`COUIMaterialBlurEffect.getBlurParamsByType(TYPE_FRAMEWORK_BOTTOM_BAR)` 的实际预设：

| 模式 | 模糊半径 | Layer 1 | 混合 | Layer 2 | 混合 |
| --- | --- | --- | --- | --- | --- |
| 浅色 | 25px | `#B3666666` | `COLOR_DODGE` | `#99F5F5F5` | `LUMINOSITY` |
| 深色 | 25px | `#80666666` | `OVERLAY` | `#B3262626` | `LUMINOSITY` |

来源：`COUIMaterialBlurEffect.java` 静态初始化中的 `FRAMEWORK_BOTTOM_BAR_PARAMS_LIGHT` / `DARK`。旧应用通过 `dockBlurParams` 读取该预设，未替换颜色层：

- 浅色、非 neutral：只把采样输入亮度乘 `0.94`，随后仍按原顺序应用 Layer 1 / Layer 2。
- 深色：采样亮度保持 `1`。
- 颜色层、混合模式保持系统预设值。

旧 `third_party/coui-kit/.../NavigationBar.kt` 的浅色材质是 `#A3666666 / #80F0F0F0`，不是系统预设。新加的独立胶囊入口改用系统预设 `#B3666666 / #99F5F5F5`；旧 `NavigationBar` 保留原兼容值，未替换。

### 2.4 阴影

- 系统：启用 caustic 时走 `setCausticShadowEffect()`；否则 `ShadowUtils.setElevationToView(2, 12dp, #8a000000)`。
- 旧 Kit `NavigationBar` / 新 `CouiDockStyle.reference`：使用 `CouiFloatingShadow` + caustic shader。中间主体在 contour 开启时为 elevation 24dp、投影 `#23000000`、lightY -320dp、lightRadius 1600dp、layout 4.9dp、sdf 10.4dp、smoothCorner；caustic 为 `lightColor` alpha `0x42/255`。
- 两者不是逐像素等价：系统走 `ShadowUtils` / COUIShadowEdge，Kit 走自有阴影 renderer；本轮保留 Kit 的公开预设，硬件结果需真机验收。

### 2.5 触摸与滑选

- 系统：`COUITouchMotionAnimator` 开启 deformation、rubber、press scale；触摸光类型 `TYPE_OPAQUE_MEDIUM_2`，触摸光半径 40dp；按下/抬起使用 COUI 弹簧；滑选在 UP 时派发 pending item，系统 CANCEL 不应提交。
- 旧应用覆盖：跟手光颜色改为墙纸强调色，强度范围乘 `2`。
- 新 Kit 胶囊：`CouiTouchMotion` + `CouiPressMotion`，`SpotlightPreset.OpaqueSmall2`（与 `OpaqueMedium2` 数值相同），宿主传入 `followLightIntensity = 2f` 和 `spotLightColor = 墙纸强调色`；`couiNavigationFeedback` 提供 hover/focus 蒙层。滑选释放逻辑修正为系统 `ACTION_CANCEL` 直接返回 `null`，拖回当前项不再重复提交。

## 3. 新 Kit 入口

新增：

- `third_party/coui-kit/src/main/java/com/itos/coui/navigation/CouiDockCapsule.kt`
  - `CouiDockItem`：标题 + 图标。
  - `CouiDockStyle`：深浅、选中/未选中颜色、材质、阴影、形状、单项尺寸、字体、间距、跟手光强度/颜色。
  - `CouiDockCapsule(modifier, backdrop, renderer, items, selectedIndex, style, onSelection)`。
  - 只使用 `CouiFloatingRenderer.standalone()`；不创建左右附件、没有融球连接，不处理窗口 inset。
  - 复用 `CouiBackdrop` 采样、`CouiFloatingSurfaces` 材质/阴影、`CouiTouchMotion`/`CouiPressMotion` 和 `rememberCouiSpotlight`。
- `CouiFloatingMaterial.sampleBrightness`：只对采样输入做亮度缩放，颜色层和混合模式保持；默认 `1f`，旧调用不受影响。
- `CouiFabExpansion.couiNavigationReleaseTarget`：系统 CANCEL 返回 `null`，修复滑选取消可能提交 pending 项的问题。

本步不绘制融合连接；`NativeFab` 仍是独立兄弟节点，未调用 `bindExpansionView`。

## 4. 应用接入

### 4.1 Glass Dock 采样入口

`app/src/main/java/com/xiaomanjun/sleepdownschedule/glass/HomeDockBackdrop.kt`：

- `rememberDockPageBackdrop()` 创建 COUI `CouiBackdrop` 源。
- `Modifier.dockPageSource(backdrop)` 挂到完整页面内容。
- 首页页面内容作为单一源；Dock 是后绘制的同级消费者，未放进源子树。
- 源节点位于既有 `screenGraphicsLayer` 录制范围内，因此整页快照、冻结回放和跨 Activity 转场仍读取同一页面画面；未启用 COUI Dock 时不挂载该 Modifier。

### 4.2 Dock 宿主与加号

`app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/CouiHomeDockChrome.kt`：

- `CouiHomeDock` 创建独立 `CouiFloatingRenderer.standalone()`；创建失败记录 `SleepDownCOUI` 并返回 `false`。
- 导航项固定为今日、课程表、设置，图标复用首页原有 `homeActionIcon` 映射。
- 选中颜色使用墙纸强调色；未选中使用当前明暗前景；浅色壁纸采样亮度 `0.94`，深色 `1`。
- 加号：ColorOS 原生 FAB 可用时继续创建现有 `NativeFab`；否则回退现有 `SleepDownFloatingAddButton`。点击仍获取加号区域返回快照并交给现有菜单 host。
- 设置页收起：加号槽位从 68dp 动画到 0，Dock 同步居中；菜单打开时加号淡出并禁用点击，但槽位保留，避免锚点漂移。
- 底部系统导航区只计算一次；IME 使用现有 `dockImeCompensationPx`。胶囊自身不处理安全区。

`NativeHomeDockChrome` 路由：

1. Android 14+ 且凝光、页面源可用：尝试 `CouiHomeDock`。
2. 失败：尝试原有 `ParallelDock`（ColorOS 原生底栏）。
3. 仍不可用：原有 `ParallelHomeDock` 液态呈现。

### 4.3 视效状态与默认值

`VisualEffectsState` 现在拆出：

- `glowEnabled`：凝光是否启用（由所选模式决定）。
- `couiKitAvailable`：Android 14+，可创建 COUI Dock。
- `colorOsAvailable`：私有 ColorOS 系统控件能力，继续由 `ColorOsNativeUi.source` 独立检查。
- `usesNativeControls = glowEnabled && colorOsAvailable`：未迁移控件继续走既有能力分发。
- `usesCouiDock = glowEnabled && couiKitAvailable`：本轮迁移的 Dock。

`VisualEffectMode.fromId(null)` 现在返回 `ColorOsGlow`；已保存的 `liquid_glass` / `coloros_glow` 原值不变，偏好键仍是 `visual_effects` 的 `mode`。模式选择不再检查品牌、ColorOS 版本或 `SLEEPDOWN_EXPERIMENTAL_FEATURES`；私有系统控件仍单独检查。

设置项说明改为“凝光材质与触摸反馈”。

## 5. 验证

已执行：

- `compileGithubReleaseKotlin`：通过。
- `:sleepdown-coui:testDebugUnitTest`：通过（新增 `CouiNavigationReleaseTargetTest`，覆盖点击、滑选、CANCEL、拖回原项）。
- `testGithubDebugUnitTest` 定向：
  - `VisualEffectsPreferencesTest`：未保存默认凝光，已保存液态玻璃/凝光保持。
  - `VisualEffectsCapabilityTest`：跨品牌无私有控件、旧系统、液态模式路由。
  - `VisualEffectModeTest`：默认值与私有控件能力。
  - `ColorOsDockCompositionTest`：既有 Dock 组合边界、后台变化不重复提交、Android 13 回退。
  - `SettingsHomeVisualTest` / `AboutSettingsUiTest` / `ScheduleDetailPageUiTest`：通过。
- `assembleGithubRelease`：通过（最终增量构建 1m57s，R8、资源压缩、lintVital、打包与签名均执行）。APK `tmp/review-apks/SleepDown-1.2.8-beta2-couikut-dock-20261106-dbcd182c.apk`，7,676,200 字节，SHA-256 `DBCD182C9432E29B93E59F4F5580EAC0425F7CE3BDF8712BD8D3EF4B4903C7C6`；V2 签名通过，证书 SHA-256 仍为 `0f2b50df…`。精确 mapping 同目录同名 `-mapping.txt`，SHA-256 `3119CF778BF941AE4F3C61DD15F062C055DC7567D1D6BFAEE88F5E01393378CA`。`com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / versionCode 36`，minSdk 26，targetSdk 36。

未执行 / 未证实：

- 未在真机验证 COUI shader、硬件模糊、阴影和 2× 跟手光的最终像素结果。
- Robolectric 不能替代 ColorOS 私有 View 的 GPU 绘制。
- 融合连接按本轮范围未实现；加号与胶囊保持独立兄弟关系。

## 6. 回滚

计划两个可独立回退的提交：

1. `Dock 迁移`：新增 Kit 胶囊、glass 采样入口、Dock 宿主与路由；保留原默认值和原模式选择门槛。
2. `默认模式调整`：未保存偏好默认凝光、模式选择脱离品牌/版本/实验渠道、设置说明更新。

按相反顺序回滚：先回退默认模式调整，回到旧默认/选择门槛；再回退 Dock 迁移，恢复反射系统底栏。课程数据、数据库和现有偏好键均未修改。
