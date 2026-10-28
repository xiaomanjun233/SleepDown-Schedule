# 首页连续打断、并行课程动画与弹窗续修

接续 `tmp/sharedviewkit-ai-history` / `codex/sharedviewkit-ai-history` / `f0d0f8ba` 与其他助手的未提交修改。用户确认此前加号锚点已经对齐，本轮要求修复液态课程管理返回按钮消失、课程内容交接、个性化内容缩放、弹窗渐变模糊，并实现单卡片快速反向与多卡片连续打断、并行动画，同时注意性能。

## 实现

- **打断不是跳过。** `MorphRetargetState` 保存每帧速度，`LaunchedEffect` 取消后以原速度接续临界阻尼弹簧；目标反向后先制动再反向。课程编辑与个性化/一级菜单的预载路线使用同一进度对应同一几何、透明度与 taper，不在反向瞬间切换开/关曲线。非预载 Legacy 规格保留原行为。
- **每张课程卡片独立持有会话。** `CourseEditorSessions` 保留各自 request、表单和 motion；A 关闭时可立即启动 B，再点 A 复用 A，随后 C 也可立即响应。旧会话只在自身 Idle 时释放，迟到完成不能释放重开的会话。真实源卡片同时按全部飞行会话隐藏，背景缩放/模糊取各会话所需最大值，共用既有完整背景采样。
- **输入链路。** 返回和运动期上滑重定向当前会话；打开途中的运动遮罩通过真实可见课程区域转交卡片点击（按模式与教学周筛选，分离/卸载即移除）。退出内容的 GPU 录制继续在上层绘制，命中树回到首页下层，避免退出表单吞掉下一张卡片的实际触摸。重开仍复用原组成树。
- **性能边界。** 不把退出表单转成 CPU 位图，不复制第二份表单；运动期复用 GPU display list，Open 后直接绘制并释放临时模糊。首次液态源图保留现有一次窗口读取；并行新会话使用已有源内容克隆，避免从被另一外壳遮挡的窗口截错源图。页面仍只持有一层背景模糊，完成的旧会话立即移除。
- **按钮返回。** 首页右上角按钮在可见时录制真实表面；进入课程管理后，不再从已经隐藏按钮的首页快照裁出返回图。既有金标返回位图能力查询与生命周期不改。
- **内容交接。** 液态交接改为外壳实际展开量 24%～62%，源/目标互补叠化并随交接模糊；不再让很小的外壳仅因原始时钟已到而提前显示整份表单。凝光保留原始进度 35%～70% 窗口。开合共用函数。
- **个性化形变。** 明确按目标约束测量表单，在运动尺寸内通过左上角放置层缩放，避免 `requiredSize` 在小约束下的隐式居中；内容同步应用外壳投影，控件无需每帧重排。
- **表单顶栏。** 渐变范围 104dp → 82dp，最大模糊 12dp → 22dp，减小底部尾区。只采样滚动表单 producer，按钮仍用外壳 backdrop；没有新增染色/提亮。冲突处理后切周改为等待对应会话真正 Idle，避免固定旧时长截断新弹簧尾部。

## 验证与交付

`testGithubDebugUnitTest` 定向 7 类 63 项通过（3m09s，0 失败、错误或跳过）：`InterruptibleMorphTest` 2、`RetainedMorphLayerTest` 1、`CourseEditorSessionsTest` 4、`GlassFrameworkTest` 28、`HomePersonalizationGeometryTest` 5、`CourseEditorDataSafetyTest` 11、`CourseEditorFlightGeometryTest` 12。包含真实 Compose 触摸测试：退出的表单不拦截底层卡片，四轮返回/重开只挂载一份内容，未提前卸载；包含时钟相同初始位置和速度、目标反向后先继续原向制动、独立 B 的逐帧轨迹不受 A 反复打断影响、A/B/A/C 会话重用及陈旧完成保护。

最初误用未提供的 Release 单测任务，后改为项目实际 Debug 单测入口；中间一轮发现非预载 Legacy 的旧模糊快照被影响，已将新行为收敛到预载路线，保留旧规格兼容；点击路由新增后缺少的 `getValue` import 已补。上述最终 63 项覆盖修正后的生产源码，后续只清理两处未使用 import 与缩进。

完整 `assembleGithubRelease -Psleepdown.sharedViewKitTest=true --console=plain --no-parallel --max-workers=1 --no-daemon` 5m04s 成功（159 项：21 执行、138 复用），包含 R8、资源压缩、lintVital 和正式签名。63 个变动生产源文件的构建前后哈希一致。生成开关两个均为 true；最终 APK 的实际 `ActivityTransitionCoordinator$open$2` 已核对进入 Kit 能力查询，远程 allowlist 的 `Set.contains` 调用为 0，确认本地金标参数已折叠进实际产物。

主目录候选 `tmp/review-apks/SleepDown-interruptible-home-gold-20261023.apk`，7,624,231 字节，SHA-256 `eaaf6f19ba4b706af8566ee0e09fdf6414eb4f77d8195801986ca611875b5d18`；`com.xiaomanjun.sleepdownschedule`、`1.2.7.1` / 35，正式 v2 证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。已上传本人私有“SleepDown 测试包”，metadata 和目录列表回读确认新文件名、字节数、目录归属、`shared=false` 与 `can_download=true`；准确链接见主目录忽略文件 `tmp/review-apks/remote-delivery-20261023.json`。没有提交、推送、PR、合并或发布；没有覆盖旧云端文件。

单元/宿主验证不等于设备视觉或 GPU 帧率验收。本轮不修改跨 Activity 金标系统弹簧的打断能力；应用内编辑器、个性化和一级菜单由上述可重定向时钟驱动。
