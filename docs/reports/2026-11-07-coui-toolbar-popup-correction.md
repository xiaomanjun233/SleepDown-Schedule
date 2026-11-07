# 顶栏与两个系统菜单的有效参数修正

> 菜单部分已按用户纠正改为直接使用Kit Menu，移除独立外壳和分割线；原生遮罩与Dialog后续修正见[2026-11-08记录](2026-11-08-coui-action-menu-dialog-correction.md)。本页保留当时调查与旧产物事实，旧mapping已按新指令清理。

分支 `codex/couikit-dialog`，起点 `8821f771`。用户明确菜单指课程长按 shortcut 和右上角三点菜单；随后指定保留应用原尺寸，其他样式／行为按系统调整，并接入本地 Kit 新增分段组件。版本保持 `1.2.8_beta2 / 36`、minSdk26；仅本地实现和签名包／精确 mapping 交付。

## 根因与依据

- 首页此前用普通 `CouiCapsuleButton` 代替工具栏按钮，只画固定填色，没有背景采样，工具栏轮廓、反馈和投影也未装配。现在使用 Kit `TopAppBarButton`，尺寸保留42dp／20dp图标；使用 `TYPE_FRAMEWORK_TOP_BAR_BLUR` 的20物理像素、两层原色与混合顺序、CIRCLE_1轮廓、OpaqueSmall2跟手光、工具栏按压反馈和原投影。新增可选显式 backdrop 参数，Kit默认作用域用法保持。
- 前轮菜单只读 XML 的12dp／9dp半径，没有继续查最终容器。此次直接从相册和天气 APK 提取 `RoundFrameLayout`：构造时 `getSmoothRadiusAndWeight` 覆盖 XML，OS版本码>37分支使用 `coui_popup_round_frame_layout_corner_radius=28dp` 与 `weight_16_1_normal=2.5`。两套 APK 的代码和资源结论一致，不能用旧 XML 默认值作为凝光模式最终轮廓。
- 系统菜单材质保留普通模式4／Kawase160与已确认的混色。其最终 `EDGE_STYLE_2` 为白光 SrcOver，线宽浅4dp／深2dp、lineAlpha浅0.9／深0.4；内晕沿SHADOW_STYLE_2的0.6基础值再乘浅0.9／深0.1。独立参数分支避免沿用Demo的背景提亮描边，Demo菜单默认保持。

研究用反编译文件只在忽略目录 `tmp/system-popup-reference`；没有提交厂商源码或二进制。

## 采样与行为

顶栏使用局部 `HomeToolbarBackdrop`：页面背景 → 原壁纸亮度层 → 正文 → 原渐进模糊，顶栏文字、图标与按钮在后绘制同级节点。源只覆盖背景条，不再录制一份整页，不读取包含按钮自身的Dock源。既有顶部渐进模糊曲线和模式回退保持。

浮动材质组合期间显式保留源，退出释放，补齐其消费引用；按需录制的源不再依赖无条件录制来维持画面。

两个菜单保留196dp宽、40dp最小行高、16sp regular文字、原间距与锚点避让。独立窗口仍采样完整下层窗口；取消不提交，点击只在实际退出后交接，课程源卡恢复继续由现有controller退出屏障负责。

## 本地 Kit 分段组件

使用用户指定本地 Kit 的 `SegmentedControl.kt`、`CouiSegmentMotion.kt`、`CouiSegmentedControlDefaults.kt` 及依赖的弹簧边界API，来源HEAD与原文件哈希记在 [接入说明](../../third_party/coui-kit/SLEEPDOWN_INTEGRATION.md)。来源文件当时未提交，不视为远端main同步；此前固定GitHub基线仍为 `520478b9`。

原 `CouiSegmentedControl` 缩为兼容门面，维持宽高、`commitOnRelease`、完整表面／仅指示器装配。新组件统一绘制、独立弹簧、跟手光和手势取消；容器只有一个输入owner，自身消费横拖不会被另一个观察器误判为父容器接管。Dialog沿用明确表面，首页复合导航不增加第二个外壳。业务仍持有唯一选择状态。

## 验证

本地回退边界：`a1c4614f` 顶栏采样／两个系统菜单，`09f0004b` 本地 Kit 分段组件及兼容门面。

- Release Kotlin中间编译通过（3m19s）；分段组件首次编译发现尚需同步 `CouiSpring.setBounds`，补齐后通过。
- 最终定向回归21项通过，0失败／错误／跳过（2m35s）：`GlowSystemMenusTest`4项、`CouiMigrationUiTest`11项、Kit提交门3项、菜单定位3项。日志 `tmp/coui-toolbar-segment-tests-20261107.log`。前一次诊断在触发绘制前读取 `recorded`，补充实际捕获，并补齐浮动材质消费者引用后通过。
- 新生成的顶栏和菜单图已查看；Robolectric可确认菜单行和锚点布局，顶栏浮动GPU表面在该软件截图中未呈现，不能据此宣称系统材质通过视觉验收。采样诊断确认局部源已录制、被浮动按钮保留且发生实际采样调用；这也不等于设备GPU像素验收。
- 独立正式 `assembleGithubRelease --console=plain --no-parallel --max-workers=1 --no-daemon` 通过（6m56s，195任务，35执行／160复用），保留R8、资源压缩、lintVital与V2签名。日志 `tmp/coui-toolbar-segment-release-20261107.log`。没有安装、推送或发布；真机GPU、触觉与帧耗时仍待设备场景验收。

## 本地交付

同次签名构建的APK与精确mapping保存在 `tmp/review-apks`，副本SHA-256与原输出逐项一致；本轮未改动外部桌面旧文件。

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `SleepDown-1.2.8-beta2-coui-toolbar-segment-20261107-cb6af347.apk` | 7,857,748 | `CB6AF34700A07364D9075730D442CBED28EBE49B66589AB6B17B5BCDFAB31F57` |
| `SleepDown-1.2.8-beta2-coui-toolbar-segment-20261107-cb6af347-mapping.txt` | 162,570,398 | `143B931E7BCAAF67F6A13C84AC8CD147AA81551469D2F211386E98A9C7E323F0` |

`apksigner verify`通过，V2证书SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。包身份 `com.xiaomanjun.sleepdownschedule`、`1.2.8_beta2 / 36`、targetSdk36；APK内嵌源码 `888d5baf4ff3163b9290b44a123f8606a3f6a06a`，包含上述两个代码提交。后续仅交付文档提交不改变此包归属。
