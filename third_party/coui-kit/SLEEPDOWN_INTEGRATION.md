# SleepDown 的 COUI 源码接入

来源：https://github.com/ItosEO/COUI-Kit

当前上游基线：`520478b9305780b078bed1096ce68f727c594f14`，2026-11-07 从 GitHub `main` 获取。以原基线 `af952eb6b2288a60652277cba2fb64a54a68aeb7` 做三方同步，保留 SleepDown 的构建兼容、材质开关、Mesh 保护及 Dock/Button 扩展；没有使用 E 盘工作区替代上游。按官方源码模块接入方式保留核心源码和 MPL 2.0、第三方及图标来源声明；这是 SleepDown 的兼容接入副本，不代表上游支持 Android 8。

宿主保留 minSdk 26。只在本应用已接入的设置路径支持旧系统：Android 12 以下不录制或采样材质背景，顶栏、分组和控件保留 COUI 几何及排版；需要更高系统的浮动按钮由宿主按版本提供同尺寸普通表面。未接入组件仍遵循源码的 RequiresApi，不得因为模块 minSdk 降低而无条件调用。

液态玻璃模式使用同一设置排版，背景材质由原有 glass 入口提供，关闭 COUI 材质采样。设置页直接使用 COUI Scaffold 与 Item；Dialog 和选择器按用户选择的视效分发，不能以设置作用域或私有 ColorOS 能力替代模式选择。Miuix 仍供液态弹窗及既有业务使用；构建开关不再切回旧设置页面。业务功能图标继续使用原 Rounded 图标。控件强调色来自宿主应用主题，中性页面及卡片角色保留 COUI 色板。

本地改动：宿主构建插件/依赖声明；`LocalCouiMaterialEnabled`；Scaffold 在关闭材质时不录制正文；TopAppBarSurface 在关闭材质时只布局内容；Android 8 使用平台已有的 Typeface 样式接口，Android 9 起保留上游精确字重接口；Group 支持由已有宿主承担外边距和组间距，默认用法不变。公开 `ContentDialog` 门面委托上游 `CouiDialogWindow`，支持标题、固定 footer／操作区及实际退场完成回调；窗口清除模糊使用 API 31 门控，兼容宿主 minSdk 26。凝光选择器使用 WheelPicker／CalendarPicker；液态保留原选择器、开关和滑条材质。

## 2026-11-07 Dialog 与分段控件扩展

- `ProvideCouiMenuBackdrop` 在同一个 Window 内复用完整窗口源；独立窗口建立带 parent 的新源。宿主整窗源显式 `recordWhenIdle=false`，空闲直接绘制、不录制整页；消费期间保留父链。`isolateContent=false` 在消费时也保留 Auto 指令源，采样材质隔离自己的区域，避免开窗再分配额外整屏纹理；默认隔离入口不变。
- Dialog 背景使用上游 Kawase 等级（`200` 不是像素半径），沿用混色与轮廓。按用户后续要求恢复上游 DialogButton，不再录制额外纯表面源或为内部按钮追加采样；完整窗口源仅供下一层弹窗。
- `couiControlMaterialEffect` 统一浮动表面的采样亮度、Gaussian 模糊和两层混色顺序；DialogButton 保持上游自己的尺寸、文本与反馈。`DialogHeading`／`DialogActionBar` 暴露原排版供特殊表单复用。
- `CouiFabDefaults.translucentMaterial()` 为宿主强调按钮和 Dock 加号提供可选半透明染色与 25px 采样模糊；默认 FAB 和 Dialog 主操作仍沿用 Demo 材质。凝光设置页只录制 Scaffold 所需的 COUI 正文源，液态保留原 Kyant 正文源。
- Dialog／InputDialog／ContentDialog 公开实际 `onDismissFinished`，反向入场会取消未完成的退出；不使用固定延时猜测动画结束。输入窗口沿用 Kit IME 避让。
- 受控分段选择器使用下述本地 Kit 新增的 `SegmentedControl`；`CouiSegmentedControl` 只保留宿主尺寸、所属表面和仅指示器装配门面。位置、宽度、高度、橡皮筋使用同一套 Kit 弹簧，不再维护第二套输入与运动。公开轻触反馈不能视为 OEM 302 波形复刻。
- 上游同步采用固定 main 提交做三方差异核对，再重放上述宿主扩展；下文 2026-11-06 的哈希是历史接入证据。

## 2026-11-07 系统菜单与特殊选择器续作

- 课程shortcut与三点动作菜单直接使用Kit `Menu`，保留196dp宽度、原窗口与材质。所有动作项 `dividerBefore=false`。样式显式选择原生白光轮廓与普通动作文字（目标相册APK的 `DefaultAdapter → couiTextBodyL` 为15sp regular，不能沿用旧报告的16sp猜值或Kit的14sp medium）。工具栏使用 `CouiMenuPlacement.AnchorCenter`；课程沿原有列锚点选择Start／Center／End，保留右列右对齐与RTL。按可见窗口、IME定位，上翻保持8dp间隔，长菜单约束到可用高度。设置菜单默认定位及字体不变。原生共享遮罩使用显式 `pressMaskOnForeground`。宿主动作在有效选择时立即提交一次，实际退场回调只负责清理窗口／资源。
- 分段普通高度按系统regular40dp；显式传入尺寸的首页导航不改。指示器运动中的可见矩形也可起拖，跨标签缩放与已选无障碍点击按系统语义补齐。
- 宿主自定义Picker使用独立的内容边距参数，不改Wheel/Calendar自身尺寸；月历不增加外层标题。具体业务布局仍留在宿主，不加入上游Demo或厂商二进制。

## 本地新增分段组件与首页工具栏

用户指定补入本机 Kit 新增的分段适配。来源工作区 HEAD 为 `f8423c351b9096c363001665741c897f6d1666e0`，分段文件当时尚未提交；这是固定 GitHub 基线之外的明确补充，不代表再次同步了远端 main。仅补入三个组件文件及其依赖的 `CouiSpring.setBounds`，未改动来源工作区：

| 原文件 | 来源 SHA-256 |
| --- | --- |
| `SegmentedControl.kt` | `4277A52FA8A35E4D3CBD84425985BFDE7C560894DA205105DE607C27CF45DFA7` |
| `CouiSegmentMotion.kt` | `21A1D34A9125A2E7FAD50E5CCEDD8D84805B3250A3CB0E5BFEA8C268B03B3B84` |
| `CouiSegmentedControlDefaults.kt` | `8C333621C1E345329946FCC0B377508EA284961D8A3B1311826DD0B2B617E054` |

宿主兼容增量：`SegmentedControl` 增加完整外壳／仅指示器与显式表面槽，兼容门面维持原宽高；完整表面需背景源或明确的表面材质。仅指示器不创建浮动 renderer；Dialog 不额外采样旧页面，首页复合导航仍由宿主壳承担材质。提交门在业务回调前结束会话，防止回写重入导致重复提交。

`TopAppBarButton` 增加可选的显式 `backdrop`，默认仍用上游工具栏作用域。首页保留42dp尺寸／20dp图标，使用系统 TOP_BAR_BLUR（20px）及 CIRCLE_1、原工具栏反馈和完整投影；不再用普通填色 Button 替代。宿主 `glass/HomeToolbarBackdrop` 只录制局部背景条，按钮与标题在源外后绘制，不消费包含自身的 Dock 整页源。

公共浮动材质在组合存续期间保留背景源，退出释放；因此使用按需录制的窗口源时也能正确唤醒采样，不依赖源一直录制。

菜单外壳和材质保持Kit标准实现，不再维护第二个系统菜单绘制后端。原生单行遮罩由 `COUITouchRecyclerView` 的最终分支确定：整行高度、水平8dp内缩、weight1胶囊，绘制在文字／图标之后；复用Kit进度、滑动弹簧与受控提交。不得只凭静态XML半径推断该遮罩，普通共享遮罩也不同于行自身的hover／clickable遮罩。

Dialog继续使用固定上游的Kawase和窗口生命周期。宿主保留嵌套窗口源、安全区与退出完成回调；开合动画值在graphicsLayer／draw阶段读取，避免每帧重组窗口并重复写Window属性，材质RenderEffect的Compose包装随基础effect缓存。动画参数与采样逆变换保持同帧一致。

采样源、浮动材质和Dialog采样锚点通过实际尺寸与屏幕仿射变换更新坐标状态，不能把位置未变的 `onGloballyPositioned` 回调当成材质重建信号。窗口属性按实际值比较后单次提交，背景Drawable仅随Window建立，避免IME动画重组反复分配和写入。

保留上游 Compose BOM 2026.08.01 与 DynamicAnimation strictly 1.0.0 的约束，不使用 force、exclude 或 overrideLibrary。

用户截图核对后的设置装配：宿主独立使用 Settings 的卡片色 `#1AFFFFFF`（夜间叠黑），不将通用 Dark.container 的 `#333333` 当作设置底色。Group 增加可选 Shape 参数，默认形状不变；宿主普通设置卡片根据 Settings 17 cardlist 消费者选用 24dp/2.5，菜单、搜索等保留自身预设。页面用途决定目录/表单行高；没有替换或全局修改上游圆角算法。

Mesh 阴影初始化的 `IllegalArgumentException` 在内部组件入口记录诊断并关闭本进程重复创建：菜单复用已有普通阴影，浮动组件保留背景、文字与交互。支持 Mesh 的设备继续使用原 renderer。此兼容保护没有修改公开严格构造器或着色器算法；JVM 测试环境也缺少 MeshSpecification native shadow，不能据其降级测试声称实机 GPU 效果已验收。

## 2026-11-06 SleepDown 独立导航胶囊

来源仍为 `ItosEO/COUI-Kit` 固定提交 `af952eb6b2288a60652277cba2fb64a54a68aeb7`，远端分支 `codex/demo-component-coverage-20261104`。E 盘本地开发克隆 `E:\COUI\COUI-Kit` 未由本项目修改。

新增 `navigation/CouiDockCapsule.kt`，以 E 盘 `coui/src/main/java/com/itos/coui/navigation/CouiBottomBar.kt` 的中间菜单主体为依据，去掉左右附件、融合节点和窗口安全区，改用 `CouiFloatingRenderer.standalone()` 与 `couiFloatingSurface`。公开入口只接收 modifier、`CouiBackdrop`、独立 renderer、导航项、选中索引、`CouiDockStyle` 和选择回调；宿主负责加号、菜单、返回交接和底部 inset。

材质采用 COUI 17 `TYPE_FRAMEWORK_BOTTOM_BAR` 系统预设，而不是旧 `NavigationBar` 的浅色 `#A3666666/#80F0F0F0`：

- 浅色：25px，`#B3666666 / COLOR_DODGE`，`#99F5F5F5 / LUMINOSITY`
- 深色：25px，`#80666666 / OVERLAY`，`#B3262626 / LUMINOSITY`

`CouiFloatingMaterial` 增加 `sampleBrightness`，只压暗采样输入；颜色层和混合模式保持原值，默认 `1f` 不改变既有调用。SleepDown 宿主在浅色壁纸下传 `0.94`，深色传 `1f`。

`couiNavigationReleaseTarget` 修正为系统 CANCEL 直接返回 `null`；滑选拖回当前项不会重复提交。新增 `src/test` 的 `CouiNavigationReleaseTargetTest` 覆盖点击、滑选和取消。

本轮文件哈希（SHA-256）：

- `navigation/CouiDockCapsule.kt`：`99D1763D1509497F66EB61D81240543925CD0BD3DA9CD0C29703236051CE5F85`
- `navigation/CouiFabExpansion.kt`：`D2724622EC85847B57297693C2DBCC88797872B91F7B02D3C1EB2BF7E601F8E5`
- `effect/CouiFloatingSurfaces.kt`：`B651B53C218642B91EBCC75CBE47BF156CD7E57603798FBB4BB28E04D1897F5B`
- `navigation/NavigationBar.kt`（既有兼容修订，未替换）：`5FB99AC44BDB68251C5FEF788C86BB75FB31F63B5AAD5A3F95924C3B8590B49C`
- 上游依据 `E:\COUI\COUI-Kit\coui\src\main\java\com\itos\coui\navigation\CouiBottomBar.kt`：`FF57711F133B86F1A32E00D8054E870FC01B86197BFD4FEEB41E78A5DDADA5AB`

注意：`CouiDockCapsule` 不包含附件、融合连接或系统安全区；这些仍由 SleepDown 宿主管理。硬件 shader、阴影和触摸最终像素需在真机验收。

## 2026-11-06 底栏 FAB 与双主体融合

在 `navigation/CouiDockBar.kt` 增加胶囊 + FAB 的双主体入口：

- 依据 E 盘 `coui/src/main/java/com/itos/coui/navigation/CouiBottomBar.kt` 的右附件装配和 `coui/src/main/java/com/itos/coui/floatingactionbutton/CouiFab.kt` 的 FAB 材质/触摸；使用 `CouiFloatingRenderer.pair()`、`CouiFloatingSurfaces`、`CouiFabDefaults` 与 `couiFloatingPair`。
- 两个表面 index 分别为 1 和 2，extension 4dp；FAB 颜色取宿主墙纸强调色，图标、描述、浅/深跟手光、形状由 `CouiDockPlusStyle` 提供。
- 菜单内容从 `CouiDockCapsule` 抽取为 `CouiDockMenuContent`，独立胶囊与融合底栏共用；没有复制导航手势和选中提交逻辑。
- 加号槽位、菜单锚点、返回快照和隐藏仍由 SleepDown 宿主管理；库不创建菜单、不读取窗口 inset、不提交业务导航。
- `CouiDockStyle` 的跟手光拆为浅色/深色两个可选覆盖值；null 沿用 COUI 预设。SleepDown 使用菜单 2.0/0.3、FAB 1.2/0.5，修正旧实现把深色 `intensity` 覆盖为 2.0 的问题。
- `CouiDockBar` 的 FAB 材质采样模糊改为与胶囊 `style.material.blurRadius` 相同；FAB 槽位仍由宿主动画，SleepDown 已对齐 `CouiFabExpansion` 的 response 0.55s / damping 0.65 弹簧，并将默认三项菜单从 250dp 收窄到 220dp。

本轮文件哈希（SHA-256）：

- `navigation/CouiDockCapsule.kt`（抽取共享菜单后）：`FFBB1BEAF5C09892C1E1BDFE5B96C73C53A855281D1249AC1A60C1A4FA744AC5`
- `navigation/CouiFabExpansion.kt`：`D2724622EC85847B57297693C2DBCC88797872B91F7B02D3C1EB2BF7E601F8E5`
- `effect/CouiFloatingSurfaces.kt`：`B651B53C218642B91EBCC75CBE47BF156CD7E57603798FBB4BB28E04D1897F5B`
- 上游依据 `E:\COUI\COUI-Kit\coui\src\main\java\com\itos\coui\floatingactionbutton\CouiFab.kt`：`66FB3A4C1F2E27E422D0A618A0E844D04B8825C55FD04C662CFD4031ABCC81EE`

`CouiDockBar` 仍是 API 34+ 的可选入口；渲染失败由宿主记录并回退既有原生/液态 Dock。融合、阴影和深色跟手光需真机 GPU 验收。

## 2026-11-06 组合底栏与 Button 控件

- `CouiDockBar` 改为 Demo 式的组合控件：宿主只传 `plusVisible`、`plusEnabled`、`menuWidth` 和回调；FAB 槽位、宽度、淡出、0.5 隐藏缩放以及 0.55s/0.65 弹簧都在组件内部完成。`CouiDockDefaults` 提供 232dp 菜单、56dp FAB、12dp 间距和 56dp 高度。
- 新增 `button/CouiButton.kt`，来源为 E 盘 `coui/src/main/java/com/itos/coui/button/CouiButton.kt`。保留 `CouiButton` 文本入口，并增加 `CouiCapsuleButton` 内容槽入口；二者共用 `CouiButtonDefaults`、`couiButtonSurface`、轮廓、按压和跟手光。`couiButtonSurface` 增加 `surfaceSheen` 绘制。
- `text/Text.kt` 增加 `CouiTextKind.Button`（16sp medium，1.35 字体缩放上限，1.2/1.1579 行距），供文本按钮使用。
- SleepDown 的 `CouiCapsuleButtonRenderer` 将现有 `NativeCapsuleButtonRequest` 映射到 COUI 按钮：主色 -> Primary，中性 -> Secondary(material=true)；高度、内边距、形状、禁用材质和 sheen 沿用请求。`lightBackdrop`/`cardLightShadow` 请求返回 false，继续走 ColorOS 原生或液态实现。
- `ProvideColorOsMaterials` 在凝光 + Android 14+ 时先尝试 COUI 按钮，再回退原生；未启用 COUI 时保持原分发。

文件哈希（SHA-256）：

- `navigation/CouiDockBar.kt`：`5F10279B8A3B5D54D61A71BE584689A89D9F5BC98CBDBE76C8199A42D0088373`
- `button/CouiButton.kt`：`119C459261D6489B7FBF804E0A6E8B5B081D9B1C0A2CD404DC0F3CFE8E56B878`
- `text/Text.kt`：`42947776320FE670A402A5245E8A87D31E5E165468FBF397B35A0CCDE4A5673E`
- 上游依据 `E:\COUI\COUI-Kit\coui\src\main\java\com\itos\coui\button\CouiButton.kt`：`66FB3A4C1F2E27E422D0A618A0E844D04B8825C55FD04C662CFD4031ABCC81EE`

Button 组件的 RuntimeShader 轮廓在 API 33+ 启用，旧平台保留普通填色、状态和运动；失败按 Kit 契约向上传播。硬件轮廓、跟手光和内容槽布局需真机验收。

## 2026-11-06 Switch 与 Slider 接入

- 复用现有 `switch/Switch.kt` 与 `slider/Slider.kt`，不复制轨道、thumb、拖动或 snap 算法。
- SleepDown 的 `CouiValueControls.kt` 将 `NativeSwitchRequest` / `NativeSliderRequest` 映射到 Kit：
  - Switch：44×24 视觉、18dp thumb、48dp 最小点击区；宿主强调色；compact 0.8 缩放。
  - Slider：宿主 preview/commit/cancel 事务、1.5% snapValue 吸附、14dp 轨道 inset 的 2.5dp marker、垂直滚动让位。
- `ProvideColorOsMaterials` 的分发为 COUI → ColorOS 原生 → 液态；未启用 COUI 时保持原分发。

本轮使用文件哈希（SHA-256）：

- `switch/Switch.kt`：`897FAEBC8D70DAABA060076451883AF06B8C95D169FAF6AB29D5CFABD78E0623`
- `slider/Slider.kt`：`8136E732A31FF51D9C3AACFEB2EAB483A440755C816A3206A376C03FCBE4FCCF`
- `slider/CouiSliderDefaults.kt`：`A754A74D400F65102F5801B8ED88249AE3A524C39C00AA6377DF4448EDE25E88`
- `slider/CouiSliderRenderer.kt`：`FF85375C8A55A524C97B1A3FF3DAE60FE3B864B73C1B5BA1CA3B3CAEB38D9C86`

Switch/Slider 的实际拖动、fling、吸附和紧凑尺寸需真机验收。
