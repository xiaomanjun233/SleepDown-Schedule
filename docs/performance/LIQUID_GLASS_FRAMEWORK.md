# 液态玻璃 2.0 统一框架与同 Activity 动画

更新时间：2026-08-23

## 当前结论

- Backdrop 已在独立提交 `eab3059` 从 `2.0.0-alpha03` 升级到正式版 `2.0.0`，`shapes` 保持 `1.2.0`。Kotlin、Compose 和 Serialization 插件无需联动升级。
- 默认渲染后端仍是 `KyantReference`。现有 blur、lens、色散、tint、highlight、shadow、inner shadow、Shape、内容绘制顺序和交互参数未删减；阶段二实验只能通过显式 Gradle 属性进入受控 Release。
- 全项目不再由业务代码直接创建、组合或挂载 `LayerBackdrop`，也不再散落调用 `drawBackdrop` / `drawPlainBackdrop`；这些调用集中在 `glass/SleepDownGlassSurface.kt`。`ScaledBackdrop` 仍是 Backdrop 坐标转换接口实现，不是额外消费者。
- 首页仍保留 `Background`、`Content`、`PickerScene` 三个真实采样域；`ChromeCombined` 只组合前两个，Android Dialog 通过 `DialogBridge` 和既有屏幕坐标补偿采样，未合成错误的全局 Backdrop。
- 普通构建仍使用空 allowlist。阶段二稳定 envelope 策略显式列出三个首页菜单目的页，以及大屏个性化的渐进模糊、Backdrop aura 两条独立效果通道，并由默认 `false` 的 `sleepdown.enableLargeGlassExperiment` 再做总门控。阶段三为首页三点菜单单独增加 `sleepdown.enableLiquidMotionExperiment` 与 motion allowlist；两批实验互不隐式开启。课程卡 `GlassGroup` 仍无启用路线。
- 本轮没有修改 Oplus callback、Bundle、系统 leash、返回时序、能力开关或逐路线 allowlist。

## 上游约束与本地决策

- 官方 [`DrawBackdropModifier`](https://github.com/Kyant0/AndroidLiquidGlass/blob/2.0.0/backdrop/src/commonMain/kotlin/com/kyant/backdrop/DrawBackdropModifier.kt)会为每个 `drawBackdrop` consumer 建立自己的效果/GraphicsLayer 路径；共享 provider 不等于合并 consumer。大量同时可见玻璃的退化与 [Issue #41](https://github.com/Kyant0/AndroidLiquidGlass/issues/41) 的 32 个对象案例一致，因此本地先统计 consumer layer 和 offscreen pixels，而不是误把 provider 复用当成全部优化。
- 独立 Popup Window 的采样坐标问题仍按 [Issue #91](https://github.com/Kyant0/AndroidLiquidGlass/issues/91) 处理：业务 Popup 保持 Activity 根 overlay/既有屏幕坐标补偿，不新建无法对齐的窗口级 provider。
- 多 shape lens 与稳定 envelope 的限制见下方实验后端；在官方 API 无法表达等价 SDF 时，宁可保持 `KyantReference`，不通过关闭 lens、降低分辨率或改变视觉参数伪装成优化。

## 结构

| 层 | 责任 |
|---|---|
| `GlassBackdropDomain` | 声明真实采样所有权；禁止把背景、内容、选择器和独立 Activity 场景误当成同一纹理 |
| `GlassMaterialSpec` / `GlassMaterialRole` | 保存 pill、dialog、course card、popup、editor、morph shell、control 等不可变材质 token |
| `GlassEffectFrame` | 保存逐帧动态效果值，不承载布局尺寸 |
| `GlassSceneState` | 管理 `Preparing → Moving → Live → Closing → Released`、缓存代次、坐标补偿、临时资源和诊断计数 |
| `rememberGlassLayerBackdrop` | 为每个真实 provider 保持稳定 GraphicsLayer/onDraw 身份，并生成实例级诊断 ID |
| `sleepDownGlassSurface` | 统一 Backdrop 2.0 `drawBackdrop` 生命周期；支持完整材质和旧复杂控件的逐参数透传 |
| `sleepDownPlainGlassSurface` | 统一渐进模糊/预热等有意使用 `drawPlainBackdrop` 的轻量路径，不将其升级为完整液态 Offscreen |
| `GlassSceneTopology` | 在 Debug/benchmark 检查重复节点、自采样、域错配和循环；首页拓扑已声明并启用 fail-fast |

本地复制的 `LiquidPanel`、`LiquidButton`、`LiquidSlider`、`LiquidToggle`、`LiquidBottomTabs` 已接入同一入口。复杂控件仍逐字保留 Ambient Highlight、自定义阴影颜色/半径、拖动速度形变、内容着色和内部轨道 Backdrop 变换。

## 已落地的低风险性能改动

1. 每个真实采样源实例只创建一个稳定 provider，消费者复用其 Backdrop；首页 Background/Content/PickerScene 各自保持单一主 provider，缓存周视图和控件内部轨道等派生源仍按其真实所有权独立存在。同一组件实例不因普通重组更换 provider、Shape 或效果回调身份。
2. 动态参数通过 `rememberUpdatedState` 在已有 Modifier node 中读取，避免无关重组重新构造完整 Kyant 效果链。
3. 首页既有周视图 GPU 缓存、课程编辑器两帧预热、Preparing 预热和 Open 稳态真实内容继续保留，并纳入统一场景阶段。
4. Debug/benchmark 在每个完成帧重置一次统计区间，并把以下值写入 JankStats state 和 Perfetto counter；动画标签切换时另输出区间日志。Release 不进入逐帧计数分支：
   - `SleepDown.Glass.ProviderRecords`
   - `SleepDown.Glass.ProviderInstances`
   - `SleepDown.Glass.ConsumerDraws`
   - `SleepDown.Glass.ConsumerLayers`
   - `SleepDown.Glass.OffscreenPixels`
   - `SleepDown.Glass.EffectEvaluations`
   - `SleepDown.Glass.EffectChainRebuilds`
   - `SleepDown.Glass.LayerSizeChanges`
   - `SleepDown.Glass.PrewarmHits`
   - `SleepDown.Glass.StableResourceLeaks`
5. `Live`/`Released` 阶段若仍持有临时 clip、RenderEffect 或 Offscreen 资源，诊断会报告稳定态泄漏数。

这些改动减少的是无意义的对象/节点/效果链失效风险；在完成同设备、同场景 Perfetto 对照前，不把它们描述为已经量化的帧率提升。

## 实验后端（默认关闭）

### `GlassTransitionLayer`

- 以 256 段采样覆盖 Legacy Opening/Closing 完整运动轨迹，按旧实现的 `offset(round) + size(round)` 对齐像素，得到一次分配的固定 envelope。
- GraphicsLayer 目标尺寸在整个动画中不变；玻璃 rect、圆角和采样坐标在 envelope 内移动。
- 内容保持真实测量尺寸，不用非等比缩放终态页面或圆角。
- 第一批接入首页三点菜单进入“添加课程 / 手动导入 / 教务导入”的大面板：这些路线的内部 Kyant `LiquidPanel` 原本就始终按终态尺寸预热和绘制，因此只替换外层动态尺寸 clip，不改变官方 lens 的 SDF 尺寸、效果参数或内容坐标。
- 非全屏面板到达 Open 后移除运动 envelope，仅保留与旧实现等价的目标尺寸圆角 clip；教务导入全屏终态不保留任何转场 clip/GraphicsLayer。Closing 再登记临时资源，结束或移除时 exactly-once 释放。
- 第二批从 Backdrop 2.0 正式版的 [rounded-rect lens](https://github.com/Kyant0/AndroidLiquidGlass/blob/2.0.0/backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/Lens.kt) 与对应 Shader 原式派生 `insetRoundedRectLens`：保持官方 padding、折射方向、amount 符号、depth 与色散语义，只把 `size` 改为固定 envelope 内逐帧更新的真实 rect/radius uniform。它不再靠 Modifier `.size()` 驱动折射 SDF。
- Backdrop 2.0 的 `ShapeProvider` 会在 Modifier size 与 Shape 相等时复用 outline；固定 host 因此返回按 rect/radius 取值相等的不可变几何快照 Shape，保证 inset outline 随帧更新，同时不改变 GraphicsLayer/RenderTarget 尺寸。第一批菜单 envelope 也统一使用该规则。
- 该动态 rect 后端目前只用于大屏个性化的两个高负载通道：主面板渐进 blur，以及矩形 aura 的 vibrancy + blur + 16dp/24dp lens。旧有动态 shape 渐变、边框、表单内容、alpha、feather mask、采样域和时序仍在原坐标层绘制；手机 `LiquidPanel` 和课程编辑器继续使用 `KyantReference`。
- envelope 必须覆盖 Opening/Closing 全轨迹，且相对最终主面板/aura 的面积比分别不超过 `1.65x` / `1.45x`；任一条件不满足即逐通道回退 Reference，避免以超大稳定纹理换取表面上的尺寸稳定。
- 普通构建不会启用该策略。受控包需显式传入 `-Psleepdown.enableLargeGlassExperiment=true`；只有像素一致与 Perfetto RenderTarget 证据同时通过后，才能考虑调整默认策略。

### `GlassGroup`

- 只合并同一采样域、同一材质、互不重叠且位于 viewport 内的可见卡片。
- 合批层只绘制共享材质；每张课程卡的内容、点击、手势和语义仍由独立兄弟节点持有。
- 重叠或材质不同的卡片自动拆组；没有 `groupedSceneAllowlist` 时强制回退逐卡 Kyant 后端。
- 官方 2.0.0 `lens` 只支持单个 `RoundedRectangularShape` / `CornerBasedShape`；多卡 union 是 `Outline.Generic`，无法在一条官方 lens 链中保留每卡独立 SDF。当前实现会把带 lens 的课程卡判定为 `LensRequiresPerShapeSdf`，即使误加 allowlist 也不会作为等价后端接入；后续等待 [Issue #104](https://github.com/Kyant0/AndroidLiquidGlass/issues/104) 所讨论的多玻璃容器能力，或另行批准真正的多 shape SDF 后端。
- 该实现不是 SDF 融合，不产生玻璃颈部或融合/分裂轮廓。

## 同 Activity Morph

`LiquidMorphController` 统一管理 generation 与 exactly-once 清理，覆盖 Preparing、Opening、Open、Closing、Released、立即返回、取消、旋转替换和陈旧 callback。资源分为 `Movement` 与 `Session`：前者在到达稳定 Open 时立即释放，并可在 Closing 重新登记；后者在关闭、取消或配置替换时释放。单个清理钩子异常不会阻断其余 clip/layer 的释放。`LiquidMorphSpec` 将以下维度拆开：

- trajectory：锚点和路径；
- shape：尺寸、连续圆角与轮廓；
- motion：时间、速度与加速度；
- content handoff：源/目标 alpha、blur、挂载和可交互时机；
- backdrop depth：背景缩放、模糊和缓存；
- layer lifecycle：预热、运动 clip/Offscreen 和稳定态释放；
- deformation：切向拉伸、横向挤压、尾部滞后和回弹。

三点菜单、个性化、菜单目的页和课程编辑器首先使用 `Legacy` spec。它们继续委托给已验收的原几何/时序；课程编辑器已直接消费 spec 的几何、圆角、内容交接和 blur 字段。测试在 Opening/Closing 多个关键进度点锁定原输出。

`IndependentSpringMotionSpec` 与 `KinematicLiquidDeformationSpec` 是阶段三第一版的速度/加速度轮廓原型；它已完整保存在本地分支 `codex/archive/three-dot-outline-motion-wip-20260823`（`1e86605`），没有从当前源码删除。该原型只产生轻微切向形变，并没有复现 [Issue #70](https://github.com/Kyant0/AndroidLiquidGlass/issues/70) 的主要观感，因此不再作为当前三点菜单实验的渲染入口。

阶段三第二版仍只改首页三点菜单：

- 菜单中心继续完全跟随已验收的 Legacy Quadratic Bézier 轨迹，打开方向、关闭方向、真实三点按钮返回锚点和最终 194×317dp 菜单位置不变；Legacy 的 440/285ms 路径时间线也不变；
- 玻璃外壳的宽、高和圆角改由 Compose 实际 `spring(dampingRatio=0.75, stiffness=200)` 驱动，从按压后的按钮 footprint 生长到目标菜单，并允许与上游示例相同语言的轻微越界回弹；内容 alpha 也按上游 `progress 0.2→1.0` 的交接区间驱动；
- 不复制上游逐帧 `.size()`。外层在 Opening/Closing 使用覆盖 Legacy 全路径及弹簧安全越界范围的固定 RenderTarget，动态 rect/radius 只写入 envelope 内的 outline；内部 `HomeAddMenuMorphPanel` 始终按最终 194×317dp 真实尺寸测量并居中，不非等比缩放文字、按钮或交互坐标；
- Open 终态回到原 Kyant 30dp 静态 surface 并释放固定转场 envelope；旧 Legacy 与第一版轻微轮廓形变均可分别通过关闭开关或归档分支回退；
- 普通包保持关闭。受控包需显式传入 `-Psleepdown.enableLiquidMotionExperiment=true`，目前只 allowlist `home-three-dot-menu`。

## 后续启用门槛

阶段二第二批实验包覆盖安装后，用户已肉眼比较并反馈“好像没有什么帧数变化”；没有对应 Macrobenchmark/Perfetto 数据，因此该结论只记为主观无明显改善，不能记成量化无收益或量化回退。阶段二继续默认关闭，不扩大 allowlist。后续经用户明确允许后，按以下顺序做最小充分验收，但不能自动开启实验后端：

1. 小米平板固定 120Hz、壁纸与配置，执行两组五轮基线/改后测试；
2. 三点菜单、中心弹窗、个性化、课程编辑器和 1/8/16/32 卡片场景分别抓取 JankStats 与 Perfetto；
3. P90 改善须超过重复波动，P50/P95 不得有超过 5% 的无解释回退；
4. 明暗壁纸、手机/平板/自由窗、立即返回/旋转/连续往返执行像素和交互验收；
5. 只有像素一致与对应 GPU 热点同时达标，才为单一路线加入 allowlist。

## 本轮验证状态

- 独立的 Backdrop `2.0.0` 迁移提交在框架改造前已通过既有 378/378 JVM 单测及 GitHub/Store Debug、benchmark、Release/R8 本地构建。
- 阶段一基线的完整 `testGithubDebugUnitTest` 为 397/397；当时 `GlassFrameworkTest` 共 19 项。阶段二累计新增 4 项包络像素定位、路线门控、面积上限和 aura 几何测试，阶段三累计新增 3 项门控、旧轮廓端点和 Issue #70 精确弹簧/Legacy 路径中心不变测试；项目没有生成 Release unit-test task，且按用户约束没有改跑 Debug，因此这 7 项尚未执行，不能计入已通过数量。
- 按用户的长期构建约束，最终只构建 GitHub Release；使用 `-Psleepdown.skipReleaseResourceShrink=true` 跳过测试阶段的资源裁剪，Kotlin、R8、lintVital、打包和签名均通过。正式发布前仍应在用户要求时补一次默认开启资源压缩的 Release。
- 新 APK 的 SHA-256 为 `C5E4F2487D2CFB1746868B55BAB92E1D554076CC986D091D5329E652581F535E`，签名校验通过，并成功覆盖安装到 PLJ110 `3B15AE023YL00000`；没有启动或操作应用。
- 阶段二第二批使用 `assembleGithubRelease -Psleepdown.skipReleaseResourceShrink=true -Psleepdown.enableLargeGlassExperiment=true --no-parallel --max-workers=2` 构建通过 Kotlin、R8、lintVital、打包与签名；确认生成的 Release `BuildConfig` 实验值为 `true`。实验 APK SHA-256 为 `D47506F3E42A2177EC0482D6D14CCEA0AFC96D829623670186E9634BE0C12B87`，大小 `6,429,734` bytes，已于 2026-08-23 覆盖安装到 PLJ110 `3B15AE023YL00000`；用户随后肉眼观察未发现明显帧率变化，未采集量化 trace。
- 阶段三使用 `assembleGithubRelease -Psleepdown.skipReleaseResourceShrink=true -Psleepdown.enableLiquidMotionExperiment=true --no-parallel --max-workers=2` 构建通过 Kotlin、R8、lintVital、打包与签名；生成的 Release 已确认 `SLEEPDOWN_LARGE_GLASS_EXPERIMENT=false`、`SLEEPDOWN_LIQUID_MOTION_EXPERIMENT=true`。APK SHA-256 为 `880BD142F469DEB46F2CDD0887FB3BD2350263E9D0821F5AA3F87E00D235070A`，大小 `6,429,734` bytes，未安装、未启动。
- 随后按用户要求同时传入 `-Psleepdown.enableLargeGlassExperiment=true` 与 `-Psleepdown.enableLiquidMotionExperiment=true`，仍使用 `--no-parallel --max-workers=2` 和跳过资源压缩的签名 GitHub Release 构建。生成的两个 `BuildConfig` 值均已核对为 `true`；APK SHA-256 为 `CB0B395697DE0D714BBCD4A8BF9ED6B5BD53AEEEBE2B31A4F5A1660E099F81ED`，大小 `6,429,734` bytes，已于 2026-08-23 覆盖安装到 PLJ110 `3B15AE023YL00000`，未启动或操作应用。
- 当前精确 Issue #70 三点菜单版本继续只开启 `SLEEPDOWN_LIQUID_MOTION_EXPERIMENT`，明确保持 `SLEEPDOWN_LARGE_GLASS_EXPERIMENT=false`；低并发、跳过资源压缩但保留 R8/lintVital/签名的 GitHub Release 构建通过。APK SHA-256 为 `5995D781E910B007BF1E2AF821C9EBAA289BAA3EFBCD818EDE717AE01ABA68FA`，大小 `6,429,734` bytes，已覆盖安装到 PLJ110 `3B15AE023YL00000`，未启动或操作应用。
- 本轮未执行真机 UI、Macrobenchmark 或 Perfetto 采集，因此不宣称液态自然度或量化性能收益；稳定 envelope、阶段三 motion 与 `GlassGroup` 在普通构建中仍保持关闭。
