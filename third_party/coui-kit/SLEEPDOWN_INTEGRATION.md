# SleepDown 的 COUI 源码接入

来源：https://github.com/ItosEO/COUI-Kit

固定提交：`af952eb6b2288a60652277cba2fb64a54a68aeb7`，远端分支 `codex/demo-component-coverage-20261104`。按官方源码模块接入方式保留核心源码和 MPL 2.0、第三方及图标来源声明；这是 SleepDown 的兼容接入副本，不代表上游支持 Android 8。

宿主保留 minSdk 26。只在本应用已接入的设置路径支持旧系统：Android 12 以下不录制或采样材质背景，顶栏、分组和控件保留 COUI 几何及排版；需要更高系统的浮动按钮由宿主按版本提供同尺寸普通表面。未接入组件仍遵循源码的 RequiresApi，不得因为模块 minSdk 降低而无条件调用。

液态玻璃模式使用同一排版，背景材质由原有 glass 入口提供，关闭 COUI 材质采样。用户于 2026-11-05 明确要求全面换用 COUI，旧 Miuix 设置首页、平板二级页与 Activity 二级页实现已移除，设置页直接使用 COUI Scaffold、Item、Menu 与 Dialog。Miuix 依赖仍由设置页以外的既有功能使用，不能全局删除；构建开关不再切回旧设置页面。业务功能图标继续使用原 Rounded 图标。控件强调色来自宿主应用主题，中性页面及卡片角色保留 COUI 色板。

本地改动：宿主构建插件/依赖声明；`LocalCouiMaterialEnabled`；Scaffold 在关闭材质时不录制正文；TopAppBarSurface 在关闭材质时只布局内容；Android 8 使用平台已有的 Typeface 样式接口，Android 9 起保留上游精确字重接口；Group 支持由已有宿主承担外边距和组间距，默认用法不变。新增公开 `ContentDialog` 小门面，委托上游 `CouiDialogWindow`，不复制窗口和形状算法；窗口清除模糊使用 API 31 门控，兼容宿主 minSdk 26。设置数值滚轮使用 WheelPicker；液态玻璃开关、滑条仍保留原材质。

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

- `navigation/CouiDockBar.kt`：`3E60491654DFB5B467132C7F901B35AEBCFFF1D48FCDF396A9E31F919155DF57`
- `navigation/CouiDockCapsule.kt`（抽取共享菜单后）：`FFBB1BEAF5C09892C1E1BDFE5B96C73C53A855281D1249AC1A60C1A4FA744AC5`
- `navigation/CouiFabExpansion.kt`：`D2724622EC85847B57297693C2DBCC88797872B91F7B02D3C1EB2BF7E601F8E5`
- `effect/CouiFloatingSurfaces.kt`：`B651B53C218642B91EBCC75CBE47BF156CD7E57603798FBB4BB28E04D1897F5B`
- 上游依据 `E:\COUI\COUI-Kit\coui\src\main\java\com\itos\coui\floatingactionbutton\CouiFab.kt`：`66FB3A4C1F2E27E422D0A618A0E844D04B8825C55FD04C662CFD4031ABCC81EE`

`CouiDockBar` 仍是 API 34+ 的可选入口；渲染失败由宿主记录并回退既有原生/液态 Dock。融合、阴影和深色跟手光需真机 GPU 验收。
