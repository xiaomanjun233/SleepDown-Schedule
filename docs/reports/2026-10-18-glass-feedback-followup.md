# 课程展开与横屏凝光控件反馈修复

用户确认上一包左右切周有明显改善，同时反馈：液态玻璃模式点击课程展开详情/编辑变卡；凝光模式横屏侧栏无框按钮出现胶囊框，顶部导航的滑动选中条旁出现白边；个性化分区标题离首个选项过远，标题层级不清晰。

## 修改与依据

- `LayerBackdropModifier` 上轮新增的 `contentVersion++` 在 DrawModifier 的观察范围内读写同一 Snapshot 状态，使 producer 订阅自己的版本更新。没有完整录制 key 的实时 producer 会继续请求重绘，冻结场景引用的子 RenderNode 因而可能不断变脏。改用 `Snapshot.withoutReadObservation` 发布版本；共享模糊前缀的版本发布同步处理。普通课程的全分辨率共享前缀、Lens、切周与编辑器动画路线保留。这里确认的是代码中的自订阅路径，未采集帧率，不声称已实测解决全部卡顿。
- `HomeSidebarIconButton` 的液态玻璃分支原本无材质，凝光分支却创建完整 `COUIButton` 胶囊。现在由 Compose 保留点击、禁用和无障碍语义，凝光仅提供 light-only 跟手光。顶部导航外壳与分段控件原先各自创建一层材质；新增 `NativeSegmentRequest.containerSurfaceEnabled`，默认保留独立控件行为，横屏组合导航关闭内部容器，以 COUI Canvas 模式保留原生选中条、文字、触摸和弹簧。依据本机 COUI 实现公开的 `setIsDrawMaterialEffect`、`setStrokeEffectType` 与 `SegmentButtonDrawDelegate` 进行拆分，未修改用户已确认无关的数值滑杆。
- `PersonalizePanel` 大分区标题改为 `titleLarge`，小分组为 `titleMedium`，选项沿用 `bodyMedium`。删除分区/分组标题的 48/40dp 最小高度，收紧标题上下留白和分区内部垂直 padding，保留选项及滑杆的交互尺寸与滚动。
- 修正上轮后续改写第三方 patch 时只留下最后一段增量的问题，重新从精确 Kyant 2.0.0 基线生成完整生产源码 patch。已检查能应用到干净上游副本、能从当前生产源码反向应用；临时上游副本与生成脚本均不提交。

## 交付

代码分为 `e84de71b`（背景自订阅与完整 patch）、`f8d6b951`（凝光组合导航）、`21714a60`（个性化排版）三个可独立回退的提交，继续更新草稿 PR #78。

- `assembleGithubRelease --console=plain --no-parallel --max-workers=2` 完整通过，耗时 7m16s；159 个任务中 27 个执行、132 个复用，包含 Kotlin 编译、R8、资源压缩、lintVital、打包与正式签名，未跳过资源压缩。
- APK：`app/build/outputs/apk/github/release/app-github-release.apk`，7,574,871 字节，SHA-256 `40627fe5f9be8227633add2ef49781fa53182978883942b29333b5aac374b95e`。包名 `com.xiaomanjun.sleepdownschedule`，版本仍为 `1.2.7` / `35`。
- `apksigner verify --verbose --print-certs` 通过，v2 签名有效，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与原安装包一致。
- 通过项目 SDK ADB 的 5038 通道重新确认设备后，向 USB PLJ110（Android 17 / ColorOS V17.0.0）执行 `install -r`，返回 `Success`，未启动应用，未清除数据。
- 按用户要求没有运行单元、截图、自动 UI 或性能测试。课程展开的流畅度、横屏凝光外观和个性化排版由用户安装后自行验收；构建成功不能替代真实体验结论。
