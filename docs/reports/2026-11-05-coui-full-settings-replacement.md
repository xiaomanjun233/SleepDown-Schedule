# 设置页面全面改用 COUI 与详情崩溃排查

> 用户已提供截图否定本报告对应 `51121cf6` 包的视觉，不能把本报告的编译/测试通过视为视觉验收；后续纠正见 [截图核对](2026-11-05-settings-screenshot-correction.md)。

## 任务与来源

用户否定上一轮 `be9e2fa2` APK 的布局和小组件光效，明确要求移除旧 Miuix 二级页面，并报告点击“当前课表详细设置”闪退，要求先从代码排查。继续保留业务功能、保存语义、首页悬浮搜索与原 Rounded 功能图标。追加要求：液态设置搜索使用教务搜索样式、位置不变，COUI 控件跟随应用主题色。

沿用此前已核对的 E 盘上游最新 Demo 基线 `af952eb6b2288a60652277cba2fb64a54a68aeb7`。实际装配参考 `secondary-pages.md`、`DemoSettingsPage.kt`、`DemoPreferenceControls.kt` 与组件公开入口。E 盘克隆及用户工作区未修改。

## 本轮实现

- 删除旧 Miuix 设置首页、平板详情和 Activity 详情的页面实现；`DetailActivityScaffold` 与首页直接调用 COUI Scaffold。移除用于兼容旧下拉的嵌套 Miuix Scaffold。
- 设置行由 COUI Item 测量，分组、分类标题、行内距、安全区和滚动使用 COUI 公共参数；继续保留 Rounded 业务图标与 COUI 返回图标。作息正文补上 Insets 消费与 COUI 滚动。
- 设置下拉使用真实行锚点及 COUI Menu 独立窗口。常用 Alert/Picker 通过 COUI Dialog 装配，数值滚轮使用 WheelPicker，不再依赖旧设置页面的弹层宿主。窗口退出完成后继续执行原业务交接回调。
- COUI 的 accent、accentOnSurface、checkboxAccent、menuCheckboxAccent 来自宿主主题；中性背景与卡片保留原生色板。设置液态开关也跟随主题强调色与应用明暗模式，设置外默认行为保留。
- 设置与教务搜索共用 `LiquidSearchStyle`：44dp 表面、5dp 模糊、14/24dp 透镜、原透明度与阴影参数；编辑前景与表面分开绘制。设置搜索继续留在原折叠顶栏的栏位。
- 小组件光效此前虽然传入按钮行边界，但 native host 实际仍按卡片宽度的 1.2 倍裁切。现在投射宽度与裁切范围都覆盖实际接光行和侧缘渐隐区，小方形组件不再被自身窄宽度限制。保留系统 COE 光效，未改成 Canvas 模拟光。

`third_party/coui-kit` 的本地增量记录于其 `SLEEPDOWN_INTEGRATION.md`：公开 ContentDialog 门面复用上游窗口，API 31 以下不调用窗口模糊接口。Miuix 仍被设置以外的既有功能使用，因此保留依赖及兼容主题；没有全局删除业务控件、导航或数据接口。

## 崩溃证据与边界

此前仅 SDK 26、空作息库、空 backdrop 的正文测试通过，没有覆盖真实材质与已加载选项。扩展后，SDK 35 的返回按钮初始化及打开作息菜单都抛出 `IllegalArgumentException: MeshSpecification construction failed`，入口为 `CouiShadowRenderer` 构造函数。切换 Robolectric Native Graphics 后仍复现；本机 Robolectric 4.16.1 缺少 MeshSpecification 的原生 shadow，因此该证据不能直接证明手机闪退与此同源。

该异常确实可以越过组件边界导致整个页面失败。本轮给内部阴影创建增加定向保护：只处理初始化的 `IllegalArgumentException`，记录明确诊断并停止该进程内的重复创建；Menu 复用已有普通投影，浮动按钮保留材质、内容与交互。支持 Mesh 的设备仍运行原始阴影实现，不改着色器、网格算法或颜色。没有用默认课表数据掩盖数据加载异常。

完整设备堆栈尚不可得。此前已检查 USB、5038/5037 现有服务和 mDNS，均无目标；用户随后要求先看代码，本轮没有继续等待设备或要求重新连接。

## 验证与交付

- `compileGithubReleaseKotlin` 与 14 项定向回归通过（8m10s，`tmp/coui-shadow-guard-verification-20261105.log`）：COUI 兼容/主题/几何 3 项、详情页 3 项（SDK 26 和 35 的液态路径、SDK 35 的原生路径，包含滚动、返回按钮、非空作息菜单选择、日期弹窗提交）、系统投光生命周期及接光范围 8 项，0 失败/错误/跳过。
- SDK 35 的测试图形后端不支持 MeshSpecification，此处通过验证的是初始化失败时页面仍可操作，不能当成原生阴影像素验收。
- 最后的弹窗高度参数传递、输入光标主题色、捐赠页文字角色和未使用导入收口是局部呈现修订，完整签名打包会重新编译；没有机械重跑无关业务套件。
- 最终 `assembleGithubRelease` 完整通过（8m04s，195 个任务中 34 执行、161 复用），保留 R8、资源压缩、lintVital、GitHub 金标与既有正式签名；日志 `tmp/coui-full-release-package-20261105.log`。
- 新包 `tmp/review-apks/SleepDown-1.2.8-beta2-coui-full-20261105-51121cf6.apk`，8,230,175 字节，SHA-256 `51121cf6be116aef0c544fdb6a67cc5bae63d6de0ed213e1caabb78650faffde`。包名 `com.xiaomanjun.sleepdownschedule`、`1.2.8_beta2 / 36`、minSdk 26；V2 签名验证通过，证书 SHA-256 与既有 Release 一致（`0f2b50df…`）。复制后完整哈希一致，摘要 `tmp/coui-full-release-delivery-20261105.json`。
- 构建基于当前完整工作树，包含此前活动功能；不是独立 PR 验证包。没有安装、启动、上传、发布、暂存或推送。手机闪退根因与实际光效、视觉仍需实机确认。
