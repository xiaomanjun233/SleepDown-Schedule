# 2026-10-18 课程 Kyant 渲染对齐

## 基线、范围与授权

- 参考 [Nexio `99f14cbc`](https://github.com/HaoZai000/NexioSchedule/tree/99f14cbcadd6d2e6cbfba4e73c2d001f83673264)，没有使用旧版 app 单模块路径。
- SleepDown 最新远端 main 仍为 `a320f6a8`；最新正式 Release `v1.2.7` 为其后代 `cfe8776f`。指定任务分支从 main 建立后快进到正式版，保留全部 64 个已发布提交。目录归档和原修改保存方式见[整理记录](2026-10-18-workspace-release-baseline.md)。未改动远端 main、发布标签或版本号。
- 读取了 Nexio 的 DrawBackdrop、SharedBlurBackdrop、LayerBackdrop/Modifier、EffectScope、Lens、ShapeProvider、RuntimeShader/Cache、InverseLayerScope、LayerRecorder、Blur、CombinedBackdrop，以及 CourseCard、MainScheduleScreen。
- Nexio 根许可证为 AGPL-3.0，此提交中未发现定制 backdrop 模块的单独许可证。本轮在现有 Apache-2.0 Kyant 2.0.0 上独立实现行为，不直接复制 Nexio 定制源码。其圆角 Lens AGSL 文件与 Kyant 上游的 Git blob 相同，保留原有 Apache 来源和声明。
- 没有新增材质选择、A/B 开关、分组 Shader 或自动降档；数据库、备份、课程交互、Pager、日周布局、COUI、已保存三档材质与用户参数未改动。GlassGroup 无生产调用者，本轮保留原处。

## 实际影响执行的差异

| 项目 | 修改前 | 本轮 |
| --- | --- | --- |
| 共享前缀 | 单 rawLayer 带滤镜，源与消费者为 0.48 | 全分辨率 blurLayer → 无滤镜 bakedLayer |
| 共享栅格边界 | 消费端直接引用带滤镜节点 | published 节点明确 Offscreen，持有一份共享栅格表面 |
| 普通卡片录制 | 共享直接采样、比例匹配、位置缓存等分支 | 一个通用采样入口；实际有 Lens 时才持有局部 GraphicsLayer |
| 无局部效果 | 已有共享特例直绘，但提前创建采样层 | 通用直绘，不创建采样层；效果移除时释放旧层 |
| 屏外相邻页 | 材质节点保留，但可继续录制 | 节点继续保留，绘制阶段仅跳过完全位于窗口外的采样 |
| 阴影 | 每卡 Kyant Shadow 与 InnerShadow 各自 Offscreen | 普通课程卡改为 Compose 缓存遮罩绘制，保留阴影参数 |
| 着色 | 每次绘制可新建渐变，另叠白色 Screen 和黑色 | 按高度与 tint 复用 Brush，三个填充合成一个 |

普通课程卡在旧实现中已经使用 `placementLayer=false`，周卡变换层也已按运动需求挂载。这些是本轮保留的正确行为，不算新增优化。通用控件、编辑器、固定 Morph 的 placement/Offscreen 兼容接口继续保留。

## 为什么不能把 record 称为像素烘焙

Compose GraphicsLayer 的 Android 实现使用 RenderNode 的 beginRecording/endRecording，drawLayer 继续记录或绘制 RenderNode 引用。`record { drawLayer(filtered) }` 不会在调用返回时自动完成 GPU 光栅化。

进一步核查 [AndroidX GraphicsLayerV29](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-graphics/src/androidMain/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayerV29.android.kt)：Offscreen 对应 `setUseCompositingLayer(true)`；Auto 且没有滤镜/混合属性的 wrapper 不强制该边界。[AOSP RenderNodeDrawable](https://android.googlesource.com/platform/frameworks/base/+/master/libs/hwui/pipeline/skia/RenderNodeDrawable.cpp) 的 RenderEffect 缓存可以因设备配置关闭，开启时也会把 clipBounds 交给缓存。[RenderNode 的 updateSnapshotIfRequired](https://android.googlesource.com/platform/frameworks/base/+/9c10535b71621c93d9b78924e4b320fce46e2b42/libs/hwui/RenderNode.cpp) 同时比较滤镜、裁切范围和表面 generation。

因此旧实现并非“只要 drawLayer 就必定重新模糊”，Nexio 的无效果 Auto wrapper 也不能仅凭双层结构证明“逐卡模糊为零”。不同卡片的裁切可能让滤镜缓存失效。

本轮保留参考的双层结构，但对 published 节点**明确设置 Offscreen**。这是有依据的一处结构差异：HWUI 在共享表面变脏时栅格化其内容，课程局部滤镜随后采样该表面；卡片裁切不再穿透这个边界成为内层模糊的不同 clip。没有 CPU 回读、Bitmap 壁纸缓存或额外 RenderTarget 管理器。首次显示、源变化、尺寸/密度变化仍需实际 GPU 工作。实际执行时间、OEM 行为及掉帧改善程度交由用户实机确认。

## 当前绘制与失效

1. 独立的 courseWallpaperBackdrop 录制壁纸；已有内容指纹继续覆盖图片、裁切、亮度等实际源状态，并发布 producer contentVersion。
2. SharedBlurBackdrop 在完整指纹、源版本、尺寸、密度、字体比例或方向变化时更新源录制；半径变化只更新滤镜及 published 录制。两层随 source 保持稳定，detach 时一并释放。
3. 普通课程卡固定 1.0 采样。共享前缀 ready 后，局部链仅为 Lens，随后绘制 SleepDown tint、轮廓光与文字，不再加 Blur。初始源未就绪时使用已有原始源处理，ready 状态更新会刷新消费者。
4. 原始源与共享源复用同一个全分辨率坐标转换，保留 inverse layer transform 和跨根 window 坐标处理。Lens Shader、uniform 顺序和 RenderEffect 构造沿用 Kyant；普通卡 effectOffset 为零。折射滑条仍使用原有映射。
5. 无局部效果直接裁切绘制。有 Lens 才分配局部采样层；普通卡使用 draw-time Outline/Path，完整 RoundRect 变化才更新圆角裁切 Path。
6. 背景源和材质回调维持稳定身份，effectKey 只包含实际滤镜输入。视口检查使用变换后的 window bounds，只跳过完全屏外的背景采样，不改变布局、文字、点击或材质节点生命周期。
7. 普通卡保留外阴影半径 24dp、偏移 4dp、黑色 0.1 的基色及材质 alpha，内部阴影保留半径、偏移和黑色 0.15 基色。外阴影不覆盖半透明卡片内部。两者改为 Compose 遮罩后光栅边缘仍需用户实机检查。
8. 保留编辑器 shell 的原有半分辨率兼容参数，改名为 editorSampleScale，普通课程卡不会使用它。Morph 的包络、临时 clip、分配边界、导出背景及既有冻结样本缓存未删除。

## 删除与保留

- 删除共享 0.48 比例、普通课程卡按数量选择 0.75/0.5 的策略及 sampledShape 传递。
- 删除 SharedSampleKey、相对位置浮点去噪、live shared sample cache 和专门的缩放直接采样分支。
- 普通课程卡不再挂 Kyant 的外/内阴影离屏层，保留预设高光、加色轮廓网格、渐变、亮度和文本样式。
- 保留 GlassSceneState 诊断、冻结场景录制缓存，以及其他控件实际使用的 scale、allocation、export 和 decoration 接口。
- 完整重生成第三方生产源码补丁，已对干净 Kyant `bebb11a` 副本应用并逐文件比较 Android/common 源码。

## 验证与交付

- 第一轮 `compileGithubReleaseKotlin` 通过，耗时 6m34s。其后的最终变更由正式 Release 打包再次编译。
- 初次选择 Release 单测入口时发现项目没有该任务；改用 Debug 后，用户明确要求停止测试，已中止，后续不再运行单元、截图或性能测试。不报告定向测试通过。
- 最终 `assembleGithubRelease` 完整通过，耗时 8m36s，159 个任务中 27 个执行、132 个复用，包含 Kotlin/Java、R8、资源压缩、lintVital、打包和正式签名。未使用跳过资源压缩参数。首次打包因已取消构建留下的 JAR 文件句柄失败；确认唯一 Gradle daemon 空闲后停止它，再完成上述构建。
- APK：`app/build/outputs/apk/github/release/app-github-release.apk`，7,574,871 字节，SHA-256 `15763c38ce1c2fd88dd40ba46ea0a1e849aebea095f8a700765b4189ca2bb27e`。包名 `com.xiaomanjun.sleepdownschedule`，版本仍为 `1.2.7` / `35`。
- `apksigner verify --verbose --print-certs` 通过，v2 签名有效，证书 SHA-256 为 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与正式版一致。
- 用户确认 USB 连接后，通过项目 SDK ADB 的 5038 通道向 PLJ110（Android 17 / ColorOS V17.0.0）执行 `install -r`，返回 `Success`。未启动应用、未操作课程或用户配置；由用户自行测试。
- 未运行真机 UI 操作、帧率采集或性能对照。日周视图、横竖屏、手机/平板、按压/拖动/编辑、Morph、参数边界、阴影与色彩观感由用户在安装后测试。构建成功不代表 FPS 已改善。
- 独立草稿 [PR #78](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/78) 目标为 main；代码提交 `9474495f`，正式版整理提交 `9f34ea08`。未合并或发布新 Release。

## 同日实机反馈后续

用户确认切周改善后，继续修复课程展开时背景自订阅重绘、横屏凝光组合控件的重复边框，并调整个性化排版。后续安装包、源码 patch 修正及交付结果见[本轮反馈记录](2026-10-18-glass-feedback-followup.md)；上节 APK 信息保留为首轮历史记录。
