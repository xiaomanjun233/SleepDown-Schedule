# 液态玻璃 2.0 统一框架与同 Activity 动画

更新时间：2026-08-28

## 当前结论

- Backdrop 已在独立提交 `eab3059` 从 `2.0.0-alpha03` 升级到正式版 `2.0.0`，`shapes` 保持 `1.2.0`。Kotlin、Compose 和 Serialization 插件无需联动升级。
- 源码渲染后端仍可按单个不合格场景回退到 `KyantReference`。现有 blur、lens、色散、tint、highlight、shadow、inner shadow、Shape、内容绘制顺序和交互参数未删减；大面积玻璃优化已进入正式配置并保持常开。
- 全项目不再由业务代码直接创建、组合或挂载 `LayerBackdrop`，也不再散落调用 `drawBackdrop` / `drawPlainBackdrop`；这些调用集中在 `glass/SleepDownGlassSurface.kt`。`ScaledBackdrop` 仍是 Backdrop 坐标转换接口实现，不是额外消费者。
- 首页仍保留 `Background`、`Content`、`PickerScene` 三个真实采样域；`ChromeCombined` 只组合前两个，Android Dialog 通过 `DialogBridge` 和既有屏幕坐标补偿采样，未合成错误的全局 Backdrop。
- 大玻璃 allowlist 已包含三个首页菜单目的页、大屏个性化渐进模糊/Backdrop aura，以及稳定周视图课程卡。原 Gradle 总门控已移除，正式构建固定启用。阶段三液态动效实验及其构建开关已从生产代码删除。
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

## 大玻璃后端（按场景保留 Reference 回退）

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
- 正式构建固定启用该策略；单个场景不满足包络、形状或采样约束时仍自动回退 Reference，不以全局开关隐藏问题。

### `GlassGroup`

- 只合并同一采样域、同一材质、互不重叠且位于 viewport 内的稳定课程卡；每个紧边界 RenderTarget 最多 8 张，重叠或材质不同会自动拆组。
- 一组只保留一条昂贵 backdrop → blur → lens 链。官方 2.0.0 单 Shape lens 不能表达多个独立圆角矩形，本地 `glassGroupLens` 因此严格沿用官方 rounded-rect lens 方程，为每个成员计算独立 SDF，并在同一 Shader 中取 union；这不是缩掉 lens。
- 第一版把多卡 union 作为 `Outline.Generic` 交给 `drawBackdrop`，达到合批条件后会触发 Backdrop 2.0 的 lens shape 校验并抛出 `UnsupportedOperationException`。当前改为官方支持的 `CornerBasedShape` 矩形宿主，再在 `onDrawBackdrop` 按成员圆角 union 裁切；blur 只落在各卡内部，卡间空隙不受影响，真实折射边界继续由同一多 SDF Shader 决定。
- 第一轮宿主修复后真机仍会在多卡场景抛出同一异常。新包的 R8 mapping 将第二个入口精确定位到逐卡 `sleepDownGlassSurface`：13 张以上启用降采样，但重叠、冲突或单成员组回退逐卡时，旧 `DensityScaledShape : Shape` 包装仍会被官方 lens 拒绝。当前周卡直接提供具体类型不变的 `RoundedRectangle(cardCorner * sampleScale)`；框架通过 `referenceLensSampleScale` 保证没有受支持等价形状的调用一律留在 `1.0x`，不再把通用 Shape 包装送入 lens。
- 稳定周视图不再把“星期列”作为最终合批边界：每个 Pager 页面先汇总整页候选，再按紧边界填充率至少 `0.34`、单层面积不超过页面 `0.58`、每层最多 8 张的约束做空间分块。稀疏、遥远或重叠卡片自动拆开，避免为了减少 consumer 数而分配大面积空纹理；相邻周页面仍各自持有稳定分块，横向拖动首帧不改 RenderTarget 尺寸。
- 每卡 tint、Screen overlay、高光、外阴影和内阴影仍由全分辨率独立 decoration 节点绘制，避免合批后局部渐变坐标变化或相邻卡阴影串色。文字、点击、长按、语义和布局从未进入合批层。
- 编辑、长按拖拽、冲突叠放、显式出场层、启动低质量阶段或拓扑不合格时自动回退逐卡 Kyant；普通 Pager 手势继续使用页面已有稳定分块。没有 `groupedSceneAllowlist` 时同样回退。
- 该实现不是 SDF 融合，不产生玻璃颈部或融合/分裂轮廓。

### 课程卡自适应采样

- Backdrop 2.0 没有 `sampleScale`/降采样质量参数。官方 `drawBackdrop` 的 `layerBlock` 会通过 `LayerBackdrop` 对消费者变换做逆向坐标补偿，因此本地把昂贵 consumer 分配为小尺寸纹理，再按左上原点放大到真实卡片尺寸，采样坐标仍对齐原 Backdrop。
- 0–7 张课程卡使用 `1.0x`；8–11 张使用 `0.75x`（纹理面积约 56%）；12 张及以上使用 `0.5x`（纹理面积 25%）。此前用户的 12 卡测试仍落在旧 1× 档，因此看不到降采样收益；新阈值让该页静止与 Pager 滑动期间保持同一 0.5× RenderTarget，避免手势开始时动态换尺寸。
- blur 半径、lens height/amount、绝对 dp 圆角和多 Shape SDF 同步按采样比例缩放；最终放大后几何与视觉参数回到原物理尺寸。文字与 decoration 不参与缩放。
- 合批不成立时，逐卡 Kyant consumer 仍可使用同一采样比例；因此高负载不会因一张冲突卡导致整页退回 N 条全分辨率效果链。

### 课程卡视口材质生命周期

- 大玻璃性能开关开启时，周视图每张课程卡用真实 `boundsInWindow` 与当前 DecorView 分辨率判断纵向可见性。卡片完全离开窗口上下边界后只移除 Backdrop surface 与全分辨率 decoration 材质节点；文字、布局、点击、长按、语义和 Composition 状态继续存在。
- 预热距离按窗口短边的 12% 计算，并限制在 `72dp–160dp`。纵向离屏卡只有在距离缩小、即将返回时才在该带内重建材质。真机反馈表明横向相邻页在第一次拖动时才按方向预热，会把整页 shader 重建挤到手势首帧；因此 Pager 保留的相邻周改为常驻预热，不再做横向材质卸载。
- `GlassGroup` 用同一纵向窗口判定过滤真实可见/方向性预热成员，只有成员集合跨越上下窗口或预热边界时才重算计划，普通逐像素滚动不触发 Compose 状态变化。每卡原先逐帧把绝对 bounds 写入 Compose State 的路径也改为非观察型最新锚点引用，仅真实宽度变化才更新 State；点击、长按和余震绘制仍读取最新 bounds。整页空间分块已取代列级边界，双页同时可见时不再天然形成最多 14 条列级效果链；实际 consumer 数与 RenderTarget 面积仍需用诊断/Perfetto 做量化核对。
- 启动飞入、课程长按编辑/拖拽、冲突交接及活动 overlay 期间关闭该视口门控，避免源卡锚点或编辑态材质被错误回收；全局缓存遮挡生命周期仍由独立 `CourseGlassOcclusionPhase` 控制。

### 缓存遮挡生命周期

- 只有确实形成大范围遮挡的个性化面板、菜单目的页和课程编辑器进入该路线；194×317dp 的右上角三点菜单不构成实质覆盖，始终保留课程卡材质渲染。
- 实质遮挡路线使用独立 generation 的 `Live → Preparing → Suspended → PostCloseRestore → Revealing → Live`。Preparing 先得到与当前 frame key 完全匹配的实时玻璃 Home GPU 缓存，切换 `Offscreen` 并确认一个已提交帧；随后进入 `Suspended`，只移除课程 Backdrop surface、全分辨率 decoration、折射与阴影节点，卡片改画裁切后的课程纯色半透明 fallback，原布局、文字、自定义时间标签、点击、手势与语义继续 Composition。允许 Opening 前，同一 Home GPU layer 原地额外录制一次该无课程 shader 的轻量端点；两个闸门中任一失败则整次会话回到 Live。
- Opening、Open、Closing 全程只重放纯色 Home 缓存，课程玻璃 consumer draw、效果链重建和运动期额外整树录制应为 0；旧 Closing progress `0.88…0.28` 的 8 波恢复与 8 次隐藏整树录制已删除。关闭请求仍立即启动原 Closing，不等待预热；原 32 档 Closing blur、42% 模糊处的冻结纹理分辨率交接、Morph 轨迹和时长均不变。个性化滑块 preview 保持硬绕过 staged blur。
- Closing 完全结束后立即取消缓存接管，像素一致的真实纯色课程层直接接管。`PostCloseRestore` 复用稳定 group 规划；课程编辑保存改变卡片集合时只在此处发布一次新规划。当前周按几何中心向两侧优先、Pager 相邻周随后，最多每 `16.666667ms` 挂载一个 group，因此 120Hz 自动隔帧推进；纯色 fallback 持续遮住 alpha 0 的新材质。全部 group 就绪后进入 `Revealing`，用 200ms 只将玻璃采样、tint、高光和阴影渐入，并按互补 alpha 将纯色 fallback 渐出；文字、布局、触控和整层缩放不参与动画。`FinalCommit`、PostClose GPU 快照叠化、frame-commit 等待、隐藏完整 Home 录制和 CPU Bitmap/readback 均不存在。课表/周次/窗口变化或快速重入会取消 generation 并安全回到 Live。
- `RefreshCadenceTracker` 只在动画活跃时用连续 frame timestamp 的中位间隔和确认滞回识别 60/90/120Hz，档位未变化时不更新状态。Perfetto counter 为 `SleepDown.RefreshRateBucket`、`CourseGlass.LiveDrawsDuringMorph`、`CourseGlass.FullTreeRecordsDuringMorph`、`CourseGlass.GroupTopologyChanges` 与 `CourseGlass.PostCloseRestoreFrames`；成功会话的运动期 live draw 与额外整树录制必须为 0，group 拓扑只允许课程编辑结果在 PostClose 发布一次。
- 不创建 Bitmap/ImageBitmap 截图，不改变背景 blur/zoom、Morph 时间线或终态页面。用户在旧实现的 6 卡同屏场景中已主观观察到掉帧减少，但尚无 Macrobenchmark/Perfetto 数据；当前仍不宣称量化收益。

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

阶段三曾尝试为首页三点菜单增加独立轮廓弹簧，但未进入正式路线。该实验的开关、allowlist、轮廓实现、独立弹簧/形变实现与对应测试已在 1.2.1 准备阶段从生产文件树删除；历史提交与下方构建记录只用于追溯，不得作为当前实现恢复。

## 后续启用门槛

阶段二第二批旧实验包覆盖安装后，用户曾肉眼比较并反馈“好像没有什么帧数变化”；该包尚不包含课程卡合批、自适应采样或缓存遮挡生命周期，也没有对应 Macrobenchmark/Perfetto 数据，因此不能外推到当前第三批。当前按用户新约束实际开启阶段二并覆盖安装；后续按以下顺序做最小充分验收：

1. 小米平板固定 120Hz、壁纸与配置，执行两组五轮基线/改后测试；
2. 三点菜单、中心弹窗、个性化、课程编辑器和 1/8/16/32 卡片场景分别抓取 JankStats 与 Perfetto；
3. P90 改善须超过重复波动，P50/P95 不得有超过 5% 的无解释回退；
4. 明暗壁纸、手机/平板/自由窗、立即返回/旋转/连续往返执行像素和交互验收；
5. 只有像素一致与对应 GPU 热点同时达标，才为单一路线加入 allowlist。

## 本轮验证状态

- 独立的 Backdrop `2.0.0` 迁移提交在框架改造前已通过既有 378/378 JVM 单测及 GitHub/Store Debug、benchmark、Release/R8 本地构建。
- 阶段一基线的完整 `testGithubDebugUnitTest` 为 397/397；其后新增包络、合批、紧边界、多 SDF、采样阈值/几何、遮挡门控和整页空间分块源码级测试。项目任务列表确认没有生成 Release unit-test task，且按用户约束没有改跑 Debug，因此这些新增测试尚未执行，不能计入 397/397 基线。
- 历史验收包曾跳过资源裁剪以缩短迭代时间；1.2.1 发布候选包按最新要求必须使用默认资源压缩，并保留 Kotlin、R8、lintVital、打包和签名。
- 新 APK 的 SHA-256 为 `C5E4F2487D2CFB1746868B55BAB92E1D554076CC986D091D5329E652581F535E`，签名校验通过，并成功覆盖安装到 PLJ110 `3B15AE023YL00000`；没有启动或操作应用。
- 阶段二第二批使用 `assembleGithubRelease -Psleepdown.skipReleaseResourceShrink=true -Psleepdown.enableLargeGlassExperiment=true --no-parallel --max-workers=2` 构建通过 Kotlin、R8、lintVital、打包与签名；确认生成的 Release `BuildConfig` 实验值为 `true`。实验 APK SHA-256 为 `D47506F3E42A2177EC0482D6D14CCEA0AFC96D829623670186E9634BE0C12B87`，大小 `6,429,734` bytes，已于 2026-08-23 覆盖安装到 PLJ110 `3B15AE023YL00000`；用户随后肉眼观察未发现明显帧率变化，未采集量化 trace。
- 阶段三使用 `assembleGithubRelease -Psleepdown.skipReleaseResourceShrink=true -Psleepdown.enableLiquidMotionExperiment=true --no-parallel --max-workers=2` 构建通过 Kotlin、R8、lintVital、打包与签名；生成的 Release 已确认 `SLEEPDOWN_LARGE_GLASS_EXPERIMENT=false`、`SLEEPDOWN_LIQUID_MOTION_EXPERIMENT=true`。APK SHA-256 为 `880BD142F469DEB46F2CDD0887FB3BD2350263E9D0821F5AA3F87E00D235070A`，大小 `6,429,734` bytes，未安装、未启动。
- 随后按用户要求同时传入 `-Psleepdown.enableLargeGlassExperiment=true` 与 `-Psleepdown.enableLiquidMotionExperiment=true`，仍使用 `--no-parallel --max-workers=2` 和跳过资源压缩的签名 GitHub Release 构建。生成的两个 `BuildConfig` 值均已核对为 `true`；APK SHA-256 为 `CB0B395697DE0D714BBCD4A8BF9ED6B5BD53AEEEBE2B31A4F5A1660E099F81ED`，大小 `6,429,734` bytes，已于 2026-08-23 覆盖安装到 PLJ110 `3B15AE023YL00000`，未启动或操作应用。
- 停止阶段三后的旧恢复包 SHA-256 为 `DB58D5E9ADF55B51E05B2AA4E1779D4BDDBD6A1416E6AD95C83324A250FF8580`，现已被第三批性能包覆盖。
- 第三批性能提交为 `83384f2`（稳定周课程卡合批）、`044f397`（高负载自适应采样）和 `7187f3a`（缓存遮挡生命周期）。先以同开关完成 `compileGithubReleaseKotlin`，再执行 `assembleGithubRelease -Psleepdown.skipReleaseResourceShrink=true -Psleepdown.enableLargeGlassExperiment=true --no-parallel --max-workers=2`；Kotlin、R8、lintVital、打包和签名均通过。生成的 Release 已核对 `SLEEPDOWN_LARGE_GLASS_EXPERIMENT=true`、`SLEEPDOWN_LIQUID_MOTION_EXPERIMENT=false`，APK SHA-256 为 `44A9BE692609CF48C563F694C1B6A7534FF2C36FFC35CFA363BD1F6804BA32F4`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，未启动或操作应用。
- 用户运行第三批首包后确认 6 卡同屏掉帧主观减少，同时报告多卡合批直接闪退、三点菜单不应卸载课程卡材质、首次返回等待预热延迟明显。崩溃堆栈已确认是 Backdrop 2.0 拒绝 `Outline.Generic` lens shape，而非 OOM。
- 第一轮修复包使用同一 Release 开关构建，APK 大小 `6,446,118` bytes，SHA-256 `572BECCA2D12BCE1BA942AA8ACA03702457DCC9E3D7C3B93380592CD63F95424`；用户真机确认多卡仍闪退，随后才通过新 mapping 找到逐卡 `DensityScaledShape` 漏点。
- 最终包合并合批/逐卡两条 shape 修复、前置 12 档模糊与 Closing 末段恢复，只构建 `assembleGithubRelease`，使用 `--no-parallel --max-workers=2`、跳过资源压缩但保留 Kotlin、R8、lintVital 与签名。生成值核对为 `SLEEPDOWN_LARGE_GLASS_EXPERIMENT=true`、`SLEEPDOWN_LIQUID_MOTION_EXPERIMENT=false`；APK 大小 `6,446,118` bytes，SHA-256 `3A1D4408F6453F12B30C1956FFE4CF47FE583BE8B9252BC3FF7DB18F6B1FFF26`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，未由 Codex 启动或操作，等待用户实际观察。
- 用户确认该包总体掉帧明显减少，同时指出 Closing 集中卸载模糊不自然、个性化滑块预览被错误垫上全局模糊，并说明 12 卡 Pager 滑动仍掉帧。后续包保持 Opening 不变，修正 Closing 曲线和 preview 隔离，并将采样阈值调整为 8/12。
- 新包只构建 `assembleGithubRelease`，使用 `--no-parallel --max-workers=2`、跳过资源压缩但保留 Kotlin、R8、lintVital 与签名；双开关核对为大玻璃性能 `true`、液态动效 `false`。APK 大小 `6,446,118` bytes，SHA-256 `2C5DFE997D447C7A857325829497E3B2D4B9B856BA8824BF98B9AFAE2CB6B8E4`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，未由 Codex 启动或操作。
- 本轮继续前移 Closing 材质恢复/模糊释放，并新增双轴视口材质生命周期、合批成员过滤和滚动 bounds 非观察型锚点。只构建同开关的 `assembleGithubRelease`，`--no-parallel --max-workers=2`、跳过资源压缩，Kotlin、R8、lintVital、打包和签名均通过；实际值仍为大玻璃性能 `true`、液态动效 `false`。最终 APK 大小 `6,446,118` bytes，SHA-256 `EA6C78A588DF912D5E98DCB2F59159E7706619DB777A2CDBDDAF1441F279CC0D`。设备重新连接后已于 2026-08-24 覆盖安装到 PLJ110 `3B15AE023YL00000`，安装结果为 `Success`；Codex 未启动或操作应用，真机视觉与性能仍待用户观察。
- 用户反馈 `EA6C78A5...` 包横向 Pager 仍卡顿，Closing 的主要问题是模糊卸载点跳变。本轮将相邻周恢复为常驻预热、保留纵向卸载，并把 Closing 背景改为 32 档和 42% 模糊处的提前全分辨率单层交接；Opening 保留原 12 档节奏。对应 Release 大小 `6,446,118` bytes，SHA-256 `38F38439D90F5744DE52129450517115BE50461EE21703A6C69D853DD1D7483C`；设备随后恢复连接并已覆盖安装到 PLJ110 `3B15AE023YL00000`，结果为 `Success`，未由 Codex 启动或操作。
- 最新提交 `daf3eb7` 把 Closing 课程玻璃改为 8 波空间恢复；同一 Release 还包含课程色主色族筛选（`6878792`）、平板 4×2 小组件预览同层绘制（`fe236b8`）和时间段开关重组（`430388c`）。只构建 `assembleGithubRelease`，使用 `--no-parallel --max-workers=2`、跳过资源压缩但保留 Kotlin、R8、lintVital、打包和签名；实际值为大玻璃性能 `true`、液态动效 `false`。APK 大小 `6,446,118` bytes，SHA-256 `5746F742A6A5C4D7773685EBF45314FEBBA7BC71C3F1246E65681B1D72370349`。设备恢复连接后已于 2026-08-24 覆盖安装到 PLJ110 `3B15AE023YL00000`，安装结果为 `Success`；未由 Codex 启动或操作，按长期约束也未运行 Debug、benchmark 或重复性防御测试。
- 2026-08-24 第一批续做把列级课程卡合批提升为带填充率/面积上限的整页空间分块。首个 `5D17FD4E...` 包还把遮挡阶段改为材质常驻，用户真机确认仍卡且已验证有效的 Opening 遮挡剔除消失；该取舍判定为回退。纠正包删除常驻阶段、恢复 `Live → Suspended → Prewarming`，整页空间分块保持不变。使用显式大玻璃性能 `true`、液态动效 `false` 构建，Kotlin、R8、lintVital、打包和 v2 签名均通过；APK 大小 `6,446,118` bytes，SHA-256 `D74A237736E98F948DDDD01A6EACE412923E9DBDDA75F61B9B2A4880DAB717E4`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，结果为 `Success`；未启动或操作应用。
- 2026-08-25 高刷遮挡 V2 删除 Closing 内的 8 波恢复和逐波整树录制，接入三条 Opening 提交闸门、generation 状态机、稳定完整 group registry、16.666667ms PostCloseRestore、单次 FinalCommit 与 60/90/120Hz cadence Trace；两个动画期零工作 counter 也会在意外 live draw/整树重录时真实递增，不是固定写零。项目没有 Release unit-test task，按约束未构建 Debug；新增状态/排序/cadence 测试源码，生产代码先通过 `compileGithubReleaseKotlin`，再通过开启大玻璃实验、关闭液态动效的 `assembleGithubRelease`（Kotlin、R8、lintVital、打包、v2 签名）。生成值核对为 `true/false`；APK 大小 `6,462,502` bytes，SHA-256 `D77F2E13C4B080E07F328192E0EF1B85FB55F38AC4B759DE0993E3CE61908795`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，结果 `Success`；未启动或操作应用，60/120Hz 主观与 Perfetto 验收待用户执行。
- 用户随后确认高刷遮挡 V2 “确实更流畅”，同时指出 FinalCommit 重载 Backdrop 的瞬间跳变。后续包改为 A/B 两张独立 FinalCommit `GraphicsLayer` 交替录制，避免可见缓存与待重载层共享同一 RenderNode；新层只完整录制一次并在独立 Offscreen 缓冲中完成，Closing 结束后用 90ms 缓出 alpha 淡入覆盖旧精确缓存，完成后再更新缓存引用并恢复 Live。淡入期间没有 live 课程玻璃、额外完整 Home 录制、Morph 几何或 blur 参数变化；快速重开、超时和 identity 变化会取消淡入并把临时层恢复 `Auto`。生产源码两次通过 `compileGithubReleaseKotlin`，随后开启大玻璃实验、关闭液态动效的 `assembleGithubRelease` 通过 Kotlin、R8、lintVital、打包与 v2 签名；BuildConfig 为 `true/false`，APK 大小 `6,462,502` bytes，SHA-256 `768808E17528A7F415B98484EF65CE71745105FBBEC407CC6E1E7D6761C66DDE`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，结果 `Success`，未启动或操作应用。
- 用户实测上述“新层从透明到不透明”的 90ms 方案仍有跳变，要求 Closing 改为快照叠化。当前实现把顺序反转：FinalCommit 的新 Home/Backdrop 始终以 100% 不透明画在旧精确 GPU 快照下方，Android 10+ 注册根 View frame-commit callback 并主动请求新帧，确认该隐藏帧提交后才以 120ms 缓出曲线淡出上层旧快照；Android 8/9 或异常 observer 使用两个 Choreographer 帧有界回退。这样 Backdrop 的第一次分配与光栅化仍被旧像素完全挡住，叠化阶段只修改旧快照 alpha；没有 CPU Bitmap/readback，也没有恢复 Closing 内课程玻璃或增加完整 Home 录制。`compileGithubReleaseKotlin` 与开启大玻璃、关闭液态动效的 `assembleGithubRelease` 均通过，Kotlin、R8、lintVital、打包和 v2 签名有效；BuildConfig 为 `true/false`，APK 大小 `6,462,502` bytes，SHA-256 `EFF84DF97C2331413B407153D45DEAE0653E5D0794F7E26C19AC17460A949CD7`。PLJ110 `3B15AE023YL00000` 重新连接后已覆盖安装，结果 `Success`；未由 Codex 启动或操作应用。
- 完整玻璃框架与上述性能改动已通过本地非快进合并提交 `d88ced5` 合入 `main`；合并无冲突，原 `codex/liquid-glass-framework` 指针保留在 `dcce5ee`。该合并未推送、未打标签、未发布，也未因纯 Git 集成重复构建。
- 用户确认 `EFF84DF9...` PostClose 快照叠化“没有任何作用”，随后要求取消快照、在 Opening 同时卸载课程文字并在 Closing 后以 180ms、`0.975×` 放大淡入整层课程卡。该中间实现删除 frame-commit/快照叠化并通过 `compileGithubReleaseKotlin`、R8、lintVital、打包和 v2 签名；BuildConfig 为 `true/false`，APK 大小 `6,462,502` bytes，SHA-256 `DE3114D28E7289F1996A83E543A4DAD529A7E97458C25AFF3E32D283891224E6`，覆盖安装到 PLJ110 `3B15AE023YL00000` 后又被用户明确否决，不能作为当前设计恢复。
- 当前方案按用户新要求改为“卸载玻璃时使用纯色半透明，Closing 后渐变回玻璃”。Preparing 先确认实时玻璃缓存，再进入 `Suspended` 卸载课程 Backdrop/decoration，同时保留纯色 fallback、文字、时间标签、触控与语义；允许 Opening 前把同一 Home GPU layer 原地更新一次为无课程 shader 的纯色端点，Opening/Open/Closing 只重放该层。Closing 结束后真实纯色页直接接管，全部 group 按 16.666667ms 恢复到 alpha 0，再用 200ms 交叉渐变材质与 fallback；没有 `FinalCommit`、PostClose 快照、frame-commit、整层 alpha 或缩放动画。生产代码两次通过 `compileGithubReleaseKotlin`，随后开启大玻璃实验、关闭液态动效的 `assembleGithubRelease` 通过 Kotlin、R8、lintVital、打包和 v2 签名；BuildConfig 为 `true/false`，APK 大小 `6,462,502` bytes，SHA-256 `7FD52019BF43D9DB53ED7BDB0D9AC3F8F7AD985EEBF4B85F640DFE42B7745071`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，结果 `Success`；未由 Codex 启动或操作。
