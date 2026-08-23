# 液态玻璃 2.0 统一框架与同 Activity 动画

更新时间：2026-08-23

## 当前结论

- Backdrop 已在独立提交 `eab3059` 从 `2.0.0-alpha03` 升级到正式版 `2.0.0`，`shapes` 保持 `1.2.0`。Kotlin、Compose 和 Serialization 插件无需联动升级。
- 正式渲染后端仍是 `KyantReference`。现有 blur、lens、色散、tint、highlight、shadow、inner shadow、Shape、内容绘制顺序和交互参数未删减。
- 全项目不再由业务代码直接创建、组合或挂载 `LayerBackdrop`，也不再散落调用 `drawBackdrop` / `drawPlainBackdrop`；这些调用集中在 `glass/SleepDownGlassSurface.kt`。`ScaledBackdrop` 仍是 Backdrop 坐标转换接口实现，不是额外消费者。
- 首页仍保留 `Background`、`Content`、`PickerScene` 三个真实采样域；`ChromeCombined` 只组合前两个，Android Dialog 通过 `DialogBridge` 和既有屏幕坐标补偿采样，未合成错误的全局 Backdrop。
- 尚未得到真机视觉与 Perfetto 证据的后端全部保持空 allowlist：稳定 envelope、课程卡 `GlassGroup`、独立弹簧与速度形变均不会改变生产画面。
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

- 先计算覆盖完整运动轨迹与效果边距的固定 envelope。
- GraphicsLayer 目标尺寸在整个动画中不变；玻璃 rect、圆角和采样坐标在 envelope 内移动。
- 内容保持真实测量尺寸，不用非等比缩放终态页面或圆角。
- Backdrop 2.0 的 [rounded-rect lens 实现](https://github.com/Kyant0/AndroidLiquidGlass/blob/2.0.0/backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/Lens.kt)仍按完整 Modifier 尺寸计算，不能直接表达 envelope 内移动的 inset rect；因此当前只交付固定尺寸 host 与 clip 几何，尚未把它误接成生产 lens renderer。
- 只有路线同时通过像素一致性和 RenderTarget 尺寸稳定性检查后，才能加入 `stableEnvelopeRouteAllowlist`。

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

`IndependentSpringMotionSpec` 与 `KinematicLiquidDeformationSpec` 是参考 [Issue #70](https://github.com/Kyant0/AndroidLiquidGlass/issues/70) 后建立的远期 motion 数据模型：轨迹弹簧和形状弹簧相互独立，速度/加速度只形变运动中的玻璃轮廓，内容布局保持真实尺寸；没有复制该示例逐帧改变布局 `.size()` 的路线。`newMotionRouteAllowlist` 当前为空。

## 后续启用门槛

用户先要求不执行真机测试，随后只授权构建并覆盖安装；本轮仍未启动应用或执行真机性能/视觉测试。后续经用户明确允许后，按以下顺序做最小充分验收，但不能自动开启实验后端：

1. 小米平板固定 120Hz、壁纸与配置，执行两组五轮基线/改后测试；
2. 三点菜单、中心弹窗、个性化、课程编辑器和 1/8/16/32 卡片场景分别抓取 JankStats 与 Perfetto；
3. P90 改善须超过重复波动，P50/P95 不得有超过 5% 的无解释回退；
4. 明暗壁纸、手机/平板/自由窗、立即返回/旋转/连续往返执行像素和交互验收；
5. 只有像素一致与对应 GPU 热点同时达标，才为单一路线加入 allowlist。

## 本轮验证状态

- 独立的 Backdrop `2.0.0` 迁移提交在框架改造前已通过既有 378/378 JVM 单测及 GitHub/Store Debug、benchmark、Release/R8 本地构建。
- 最终工作树的完整 `testGithubDebugUnitTest` 为 397/397；`GlassFrameworkTest` 共 19 项，覆盖材质/域拓扑、循环采样、阶段释放、旧 Morph 几何、取消/立即返回/配置替换、合批 lens 门禁和资源清理失败隔离。
- 按用户的长期构建约束，最终只构建 GitHub Release；使用 `-Psleepdown.skipReleaseResourceShrink=true` 跳过测试阶段的资源裁剪，Kotlin、R8、lintVital、打包和签名均通过。正式发布前仍应在用户要求时补一次默认开启资源压缩的 Release。
- 新 APK 的 SHA-256 为 `C5E4F2487D2CFB1746868B55BAB92E1D554076CC986D091D5329E652581F535E`，签名校验通过，并成功覆盖安装到 PLJ110 `3B15AE023YL00000`；没有启动或操作应用。
- 本轮未执行真机 UI、Macrobenchmark 或 Perfetto 采集，因此没有宣称新的量化性能收益，也没有开启稳定 envelope、`GlassGroup` 或新 motion spec。
