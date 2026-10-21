# ColorOS HD 组件与横屏滑选事件分发审计

用户反馈上一包仍不能拖动，要求先查 ColorOS 是否有适用于 HD 设备的组件。本轮先审计本地系统 APK 的完整 DEX 类定义和目标实现，没有继续从少量 smali 片段推断，也没有操作应用界面。

## 本地组件结果

检查对象是本地缓存的 ColorOS 17 Settings、Contacts、FileManager APK。用户选择先查本地组件，当前没有在线设备用于重新核对已安装系统包版本。下面结论限定于这些 APK；反编译代码仅保留在忽略的 `tmp/coui-hd-audit/`，不提交或上传系统源码。

| 组件 | 查到的来源 | 实际职责 |
| --- | --- | --- |
| `COUISideNavigationBar` | Contacts、FileManager | 基于 DrawerLayout 的侧边抽屉，提供 fixed/temporary 模式，使用 ResponsiveUIModel 和屏幕尺寸分类切换布局 |
| `COUINavigationRailView` | Contacts | 基于 NavigationRailView 的竖向导航，负责菜单、图标和提示，不是顶部胶囊拖选器 |
| `COUISidePaneLayout` | Contacts | 双栏/侧栏展开、收起和面板滑动，回调为 PanelSlideListener |
| `COUITabLayout` | Settings、Contacts、FileManager | 横向滚动标签栏，提供 fixed/scrollable 模式及 ViewPager 联动，滑动标签带不等同于直接拖动胶囊选中项 |
| `COUISegmentButtonLayout` | Settings、Contacts | 当前横屏胶囊采用的原生控件，dispatchTouchEvent 已实现从选中项开始拖动、MOVE 选择、rubber band、触觉和 UP 收尾 |

当前框架首先从 Settings 加载成对的类与主题资源；该 APK 没有完整的 COUI SideNavigationBar、NavigationRailView、SidePaneLayout 实现。未来若引入 HD 侧栏，需要单独选择具备能力的系统来源，并保持该 View 的类和资源来自同一包。本次顶部胶囊继续使用具有对应交互的 Segment 控件，未替换整个侧栏布局。

## 已确认的桥接错误

完整 `COUISegmentButtonLayout.initializeSegmentButton` 给内部每个 TextView 设置 clickable、focusable 和无障碍 delegate。`dispatchTouchEvent` 在 DOWN 建立内部拖动，MOVE 改变原生选中项，最终调用父类分发给子树。

上轮在 COUI 容器安装 OnTouchListener，错误地假定它会收到整棵子树的 DOWN/UP。子 TextView 消费事件时，这个监听不会建立 `touching`，选中回调因而仍直接执行页面导航，随后页面动画将轨道禁用。增加容器 clickable 不能解决子项消费事件造成的监听缺失。

另核对了本机 Android SDK 36.1 的 ViewGroup/View 源码：存在 `mFirstTouchTarget` 时 ViewGroup 将事件继续分发给子目标；没有子目标时才以 child=null 进入自身 View 的分发，OnTouchListener 在该 View 分发中执行。这与 COUI 内部可点击子项导致容器监听缺失的路径一致。

本轮改为透明 FrameLayout host：入口先观察 DOWN，然后仅通过一次 `super.dispatchTouchEvent` 交给完整 COUI 子树，返回后处理 UP/CANCEL。这样拖动回调会进入待提交状态，原生完成 UP 选中后再统一导航。保留上一轮对取消、禁用、程序回写、按钮独立触控以及标题字号的调整。宿主不绘制材质、不截获子项点击、不另建滑动识别器。

## 验证与交付

- 修正代码提交 `955236e6`，继续原草稿 PR #78，未替换整个 HD 布局或上传系统组件源码。
- 完整 `assembleGithubRelease --console=plain --no-parallel --max-workers=2` 通过，耗时 5m32s；159 个任务中 21 个执行、138 个复用，保留 R8、资源压缩、lintVital 和正式签名。
- APK：`app/build/outputs/apk/github/release/app-github-release.apk`，7,574,871 字节，SHA-256 `11b826b0c0e30d8c3e85d1a533c8dbdb6c7b6079a51bfa738899c3abdaac15ac`。包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7` / `35`。
- 本轮另存交付副本 `tmp/review-apks/SleepDown-1.2.7-COUI-gesture-dispatch-20261019.apk`，摘要与上述构建产物一致，便于区别旧包；APK 不入库。
- `apksigner verify --verbose --print-certs` 通过，v2 签名有效，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与现有正式签名一致。
- 用户明确选择“先交付 APK”，本轮未安装、未启动应用；没有运行额外单元、截图、自动 UI 或性能测试。静态审计及构建通过不等于实机滑选已验证。
