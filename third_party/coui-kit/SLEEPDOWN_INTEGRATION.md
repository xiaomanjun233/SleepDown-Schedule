# SleepDown 的 COUI 源码接入

来源：https://github.com/ItosEO/COUI-Kit

固定提交：`af952eb6b2288a60652277cba2fb64a54a68aeb7`，远端分支 `codex/demo-component-coverage-20261104`。按官方源码模块接入方式保留核心源码和 MPL 2.0、第三方及图标来源声明；这是 SleepDown 的兼容接入副本，不代表上游支持 Android 8。

宿主保留 minSdk 26。只在本应用已接入的设置路径支持旧系统：Android 12 以下不录制或采样材质背景，顶栏、分组和控件保留 COUI 几何及排版；需要更高系统的浮动按钮由宿主按版本提供同尺寸普通表面。未接入组件仍遵循源码的 RequiresApi，不得因为模块 minSdk 降低而无条件调用。

液态玻璃模式使用同一排版，背景材质由原有 glass 入口提供，关闭 COUI 材质采样。用户于 2026-11-05 明确要求全面换用 COUI，旧 Miuix 设置首页、平板二级页与 Activity 二级页实现已移除，设置页直接使用 COUI Scaffold、Item、Menu 与 Dialog。Miuix 依赖仍由设置页以外的既有功能使用，不能全局删除；构建开关不再切回旧设置页面。业务功能图标继续使用原 Rounded 图标。控件强调色来自宿主应用主题，中性页面及卡片角色保留 COUI 色板。

本地改动：宿主构建插件/依赖声明；`LocalCouiMaterialEnabled`；Scaffold 在关闭材质时不录制正文；TopAppBarSurface 在关闭材质时只布局内容；Android 8 使用平台已有的 Typeface 样式接口，Android 9 起保留上游精确字重接口；Group 支持由已有宿主承担外边距和组间距，默认用法不变。新增公开 `ContentDialog` 小门面，委托上游 `CouiDialogWindow`，不复制窗口和形状算法；窗口清除模糊使用 API 31 门控，兼容宿主 minSdk 26。设置数值滚轮使用 WheelPicker；液态玻璃开关、滑条仍保留原材质。

保留上游 Compose BOM 2026.08.01 与 DynamicAnimation strictly 1.0.0 的约束，不使用 force、exclude 或 overrideLibrary。

用户截图核对后的设置装配：宿主独立使用 Settings 的卡片色 `#1AFFFFFF`（夜间叠黑），不将通用 Dark.container 的 `#333333` 当作设置底色。Group 增加可选 Shape 参数，默认形状不变；宿主普通设置卡片根据 Settings 17 cardlist 消费者选用 24dp/2.5，菜单、搜索等保留自身预设。页面用途决定目录/表单行高；没有替换或全局修改上游圆角算法。

Mesh 阴影初始化的 `IllegalArgumentException` 在内部组件入口记录诊断并关闭本进程重复创建：菜单复用已有普通阴影，浮动组件保留背景、文字与交互。支持 Mesh 的设备继续使用原 renderer。此兼容保护没有修改公开严格构造器或着色器算法；JVM 测试环境也缺少 MeshSpecification native shadow，不能据其降级测试声称实机 GPU 效果已验收。
