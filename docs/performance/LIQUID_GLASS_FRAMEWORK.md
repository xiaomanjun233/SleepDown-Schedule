# 液态玻璃 2.0 统一框架与同 Activity 动画

更新时间：2026-08-23

## 当前结论

- Backdrop 已在独立提交 `eab3059` 从 `2.0.0-alpha03` 升级到正式版 `2.0.0`，`shapes` 保持 `1.2.0`。Kotlin、Compose 和 Serialization 插件无需联动升级。
- 源码默认渲染后端仍可回退到 `KyantReference`。现有 blur、lens、色散、tint、highlight、shadow、inner shadow、Shape、内容绘制顺序和交互参数未删减；当前性能验收包按用户要求显式开启阶段二真实开关，不再只保留默认关闭代码。
- 全项目不再由业务代码直接创建、组合或挂载 `LayerBackdrop`，也不再散落调用 `drawBackdrop` / `drawPlainBackdrop`；这些调用集中在 `glass/SleepDownGlassSurface.kt`。`ScaledBackdrop` 仍是 Backdrop 坐标转换接口实现，不是额外消费者。
- 首页仍保留 `Background`、`Content`、`PickerScene` 三个真实采样域；`ChromeCombined` 只组合前两个，Android Dialog 通过 `DialogBridge` 和既有屏幕坐标补偿采样，未合成错误的全局 Backdrop。
- 阶段二 allowlist 已包含三个首页菜单目的页、大屏个性化渐进模糊/Backdrop aura，以及稳定周视图课程卡。总门控仍是 Gradle 属性 `sleepdown.enableLargeGlassExperiment`；自 2026-08-23 起，每批性能改动必须用该真实开关开启的签名 Release 覆盖安装。阶段三 `sleepdown.enableLiquidMotionExperiment` 已被用户停止，当前包保持 `false`。
- 本轮没有修改 Oplus callback、Bundle、系统 leash、返回时序、能力开关或逐路线 allowlist。

## 上游约束与本地决策

- 官方 [`DrawBackdropModifier`](https://github.com/Kyant0/AndroidLiquidGlass/blob/2.0.0/backdrop/src/commonMain/kotlin/com/kyant/backdrop/DrawBackdropModifier.kt)会为每个 `drawBackdrop` consumer 建立自己的效果/GraphicsLayer 路径；共享 provider 不等于合并 consumer。大量同时可见玻璃的退化与 [Issue #41](https://github.com/Kyant0/AndroidLiquidGlass/issues/41) 的 32 个对象案例一致，因此本地先统计 consumer layer 和 offscreen pixels，而不是误把 provider 复用当成全部优化。
- 独立 Popup Window 的采样坐标问题仍按 [Issue #91](https://github.com/Kyant0/AndroidLiquidGlass/issues/91) 处理：业务 Popup 保持 Activity 根 overlay/既有屏幕坐标补偿，不新建无法对齐的窗口级 provider。
- 多 shape lens 与稳定 envelope 的限制见下方实验后端。课程卡高负载降采样已获用户明确允许，但只降低 backdrop/blur/lens 纹理；卡片布局、文字、点击、tint、高光、阴影和边缘继续全分辨率。其它玻璃不得顺带降低质量。

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

## 实验后端（源码可回退；当前性能包已开启阶段二）

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

- 只合并同一采样域、同一材质、互不重叠且位于 viewport 内的稳定课程卡；每个紧边界 RenderTarget 最多 8 张，重叠或材质不同会自动拆组。
- 一组只保留一条昂贵 backdrop → blur → lens 链。官方 2.0.0 单 Shape lens 不能表达多个独立圆角矩形，本地 `glassGroupLens` 因此严格沿用官方 rounded-rect lens 方程，为每个成员计算独立 SDF，并在同一 Shader 中取 union；这不是缩掉 lens。
- 每卡 tint、Screen overlay、高光、外阴影和内阴影仍由全分辨率独立 decoration 节点绘制，避免合批后局部渐变坐标变化或相邻卡阴影串色。文字、点击、长按、语义和布局从未进入合批层。
- 编辑、拖拽、冲突叠放、出场层、横向运动、启动低质量阶段或拓扑不合格时自动回退逐卡 Kyant。没有 `groupedSceneAllowlist` 时同样回退。
- 该实现不是 SDF 融合，不产生玻璃颈部或融合/分裂轮廓。

### 课程卡自适应采样

- Backdrop 2.0 没有 `sampleScale`/降采样质量参数。官方 `drawBackdrop` 的 `layerBlock` 会通过 `LayerBackdrop` 对消费者变换做逆向坐标补偿，因此本地把昂贵 consumer 分配为小尺寸纹理，再按左上原点放大到真实卡片尺寸，采样坐标仍对齐原 Backdrop。
- 0–12 张课程卡使用 `1.0x`；13–23 张使用 `0.75x`（纹理面积约 56%）；24 张及以上使用 `0.5x`（纹理面积 25%）。阈值只在阶段二开关与周课程卡 allowlist 同时生效时进入。
- blur 半径、lens height/amount、绝对 dp 圆角和多 Shape SDF 同步按采样比例缩放；最终放大后几何与视觉参数回到原物理尺寸。文字与 decoration 不参与缩放。
- 合批不成立时，逐卡 Kyant consumer 仍可使用同一采样比例；因此高负载不会因一张冲突卡导致整页退回 N 条全分辨率效果链。

### 缓存遮挡生命周期

- Opening/Closing 运动期间原 GPU 中性场景缓存已经阻止课程卡逐帧重放；新路线进一步处理稳定 Open 时仍挂载的昂贵材质节点。
- 仅周视图、阶段二开关开启、个性化逐帧预览关闭、缓存帧 key 完全匹配、`GraphicsLayer` 已实际切到 Offscreen 且弹层处于稳定 Open 时，课程卡进入 `Suspended`。只卸载材质 surface/decoration；内容、布局、点击、手势、语义和 Composition 状态保留。
- 协调关闭会先进入 `Prewarming`：在弹层仍保持 Open、缓存仍完全遮挡的情况下重新挂载课程卡材质，强制重录并重放连续两帧，再启动原 Closing。等待上限 500ms；非协调 Closing 也会立即恢复材质，避免可见阶段保持暂停。
- 不创建 Bitmap/ImageBitmap 截图，不改变背景 blur/zoom、Morph 时间线或终态页面。该路线能否真正释放多少底层 RenderNode/纹理仍需 Perfetto/显存证据确认；当前只宣称减少稳定 Open 的活跃材质节点，不提前宣称量化收益。

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

`IndependentSpringMotionSpec` 与 `KinematicLiquidDeformationSpec` 参考 [Issue #70](https://github.com/Kyant0/AndroidLiquidGlass/issues/70) 的低刚度、低阻尼弹簧语言建立。上游示例由同一个进度逐帧改变 `.size()`、圆角和内容 alpha；本地不复制动态 `.size()`，也不让弹簧进度改写已验收的 Legacy 路径与内容交接。

阶段三第一条实验路线是首页三点菜单：

- Legacy 通道继续独占真实 rect、Quadratic Bézier 轨迹、440/285ms 时间线、源/目标 alpha、blur、点击映射和返回锚点；
- 独立 outline 通道使用 `stiffness=200`、shape `dampingRatio=0.5` 的 Issue #70 风格弹簧，速度/加速度只生成切向拉伸、横向挤压、尾部滞后和回弹；
- 外壳使用独立 Bézier 轮廓和固定 28dp 形变余量，内部 `HomeAddMenuMorphPanel` 仍按最终 194×317dp 真实尺寸测量与居中放置，不缩放文字、按钮、圆角布局或交互坐标；
- 形变只在 Opening/Closing 存在；端点严格为零，Open 立即回到原 Kyant 30dp 静态 surface 并释放运动 outline；
- 普通包保持关闭。受控包需显式传入 `-Psleepdown.enableLiquidMotionExperiment=true`，目前只 allowlist `home-three-dot-menu`。

该阶段三路线现已由用户明确停止：第一版源码仅作为关闭状态的历史原型保留；第二版精确复现 Issue #70 的尺寸/圆角弹簧提交 `a0976d5` 已通过 `c958c75`、`69b0e56` 完整撤销。当前文件树恢复到实验前 `1e86605`，后续不再继续液态曲线工作，性能优化优先。

## 后续启用门槛

阶段二第二批旧实验包覆盖安装后，用户曾肉眼比较并反馈“好像没有什么帧数变化”；该包尚不包含课程卡合批、自适应采样或缓存遮挡生命周期，也没有对应 Macrobenchmark/Perfetto 数据，因此不能外推到当前第三批。当前按用户新约束实际开启阶段二并覆盖安装；后续按以下顺序做最小充分验收：

1. 小米平板固定 120Hz、壁纸与配置，执行两组五轮基线/改后测试；
2. 三点菜单、中心弹窗、个性化、课程编辑器和 1/8/16/32 卡片场景分别抓取 JankStats 与 Perfetto；
3. P90 改善须超过重复波动，P50/P95 不得有超过 5% 的无解释回退；
4. 明暗壁纸、手机/平板/自由窗、立即返回/旋转/连续往返执行像素和交互验收；
5. 只有像素一致与对应 GPU 热点同时达标，才为单一路线加入 allowlist。

## 本轮验证状态

- 独立的 Backdrop `2.0.0` 迁移提交在框架改造前已通过既有 378/378 JVM 单测及 GitHub/Store Debug、benchmark、Release/R8 本地构建。
- 阶段一基线的完整 `testGithubDebugUnitTest` 为 397/397；其后新增包络、合批、紧边界、多 SDF、采样阈值/几何、遮挡门控及已停止 motion 的源码级测试。项目没有生成 Release unit-test task，且按用户约束没有改跑 Debug，因此这些新增测试尚未执行，不能计入 397/397 基线。
- 按用户的长期构建约束，最终只构建 GitHub Release；使用 `-Psleepdown.skipReleaseResourceShrink=true` 跳过测试阶段的资源裁剪，Kotlin、R8、lintVital、打包和签名均通过。正式发布前仍应在用户要求时补一次默认开启资源压缩的 Release。
- 新 APK 的 SHA-256 为 `C5E4F2487D2CFB1746868B55BAB92E1D554076CC986D091D5329E652581F535E`，签名校验通过，并成功覆盖安装到 PLJ110 `3B15AE023YL00000`；没有启动或操作应用。
- 阶段二第二批使用 `assembleGithubRelease -Psleepdown.skipReleaseResourceShrink=true -Psleepdown.enableLargeGlassExperiment=true --no-parallel --max-workers=2` 构建通过 Kotlin、R8、lintVital、打包与签名；确认生成的 Release `BuildConfig` 实验值为 `true`。实验 APK SHA-256 为 `D47506F3E42A2177EC0482D6D14CCEA0AFC96D829623670186E9634BE0C12B87`，大小 `6,429,734` bytes，已于 2026-08-23 覆盖安装到 PLJ110 `3B15AE023YL00000`；用户随后肉眼观察未发现明显帧率变化，未采集量化 trace。
- 阶段三使用 `assembleGithubRelease -Psleepdown.skipReleaseResourceShrink=true -Psleepdown.enableLiquidMotionExperiment=true --no-parallel --max-workers=2` 构建通过 Kotlin、R8、lintVital、打包与签名；生成的 Release 已确认 `SLEEPDOWN_LARGE_GLASS_EXPERIMENT=false`、`SLEEPDOWN_LIQUID_MOTION_EXPERIMENT=true`。APK SHA-256 为 `880BD142F469DEB46F2CDD0887FB3BD2350263E9D0821F5AA3F87E00D235070A`，大小 `6,429,734` bytes，未安装、未启动。
- 随后按用户要求同时传入 `-Psleepdown.enableLargeGlassExperiment=true` 与 `-Psleepdown.enableLiquidMotionExperiment=true`，仍使用 `--no-parallel --max-workers=2` 和跳过资源压缩的签名 GitHub Release 构建。生成的两个 `BuildConfig` 值均已核对为 `true`；APK SHA-256 为 `CB0B395697DE0D714BBCD4A8BF9ED6B5BD53AEEEBE2B31A4F5A1660E099F81ED`，大小 `6,429,734` bytes，已于 2026-08-23 覆盖安装到 PLJ110 `3B15AE023YL00000`，未启动或操作应用。
- 停止阶段三后的旧恢复包 SHA-256 为 `DB58D5E9ADF55B51E05B2AA4E1779D4BDDBD6A1416E6AD95C83324A250FF8580`，现已被第三批性能包覆盖。
- 第三批性能提交为 `83384f2`（稳定周课程卡合批）、`044f397`（高负载自适应采样）和 `7187f3a`（缓存遮挡生命周期）。先以同开关完成 `compileGithubReleaseKotlin`，再执行 `assembleGithubRelease -Psleepdown.skipReleaseResourceShrink=true -Psleepdown.enableLargeGlassExperiment=true --no-parallel --max-workers=2`；Kotlin、R8、lintVital、打包和签名均通过。生成的 Release 已核对 `SLEEPDOWN_LARGE_GLASS_EXPERIMENT=true`、`SLEEPDOWN_LIQUID_MOTION_EXPERIMENT=false`，APK SHA-256 为 `44A9BE692609CF48C563F694C1B6A7534FF2C36FFC35CFA363BD1F6804BA32F4`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，未启动或操作应用。
- 本轮未执行真机 UI、Macrobenchmark 或 Perfetto 采集，因此不宣称量化性能收益。当前安装包已真实开启稳定 envelope、课程卡合批、分级采样与遮挡生命周期；阶段三 motion 保持关闭。
