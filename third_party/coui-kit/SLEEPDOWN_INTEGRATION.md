# SleepDown 的 COUI 源码接入

来源：https://github.com/ItosEO/COUI-Kit

固定提交：`af952eb6b2288a60652277cba2fb64a54a68aeb7`，远端分支 `codex/demo-component-coverage-20261104`。按官方源码模块接入方式保留核心源码和 MPL 2.0、第三方及图标来源声明；这是 SleepDown 的兼容接入副本，不代表上游支持 Android 8。

宿主保留 minSdk 26。只在本应用已接入的设置路径支持旧系统：Android 12 以下不录制或采样材质背景，顶栏、分组和控件保留 COUI 几何及排版；需要更高系统的浮动按钮由宿主按版本提供同尺寸普通表面。未接入组件仍遵循源码的 RequiresApi，不得因为模块 minSdk 降低而无条件调用。

液态玻璃模式使用同一排版，背景材质由原有 glass 入口提供，关闭 COUI 材质采样。Miuix 依赖与旧页面路径保留，构建时 `-Psleepdown.couiSettings=false` 可回到原设置实现；移除旧实现须等待用户另行指令。

本地改动：宿主构建插件/依赖声明；`LocalCouiMaterialEnabled`；Scaffold 在关闭材质时不录制正文；TopAppBarSurface 在关闭材质时只布局内容；Android 8 使用平台已有的 Typeface 样式接口，Android 9 起保留上游精确字重接口；Group 支持由已有宿主承担外边距和组间距，默认用法不变。上游核心其他组件与效果保持原实现。现有系统类调用暂时保留；液态玻璃的开关、滑条等控件不替换。

保留上游 Compose BOM 2026.08.01 与 DynamicAnimation strictly 1.0.0 的约束，不使用 force、exclude 或 overrideLibrary。
