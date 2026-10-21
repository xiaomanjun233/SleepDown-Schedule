# Beta5 表单延后挂载、柔和轮廓光与电脑版视口

基于 `d83a8f6`，按实机反馈调整表单进入时序、课程轮廓光，并接入用户提供的 Nexio 电脑版方案。本记录更新当前实现；上一轮报告中的预载表单、同步形变和三层描边描述属于当时版本。

- 课程编辑/复制的 Preparing 与 Opening 仅包含外壳和课程源内容。先等待外壳与背景两条动画结束，再呈现一帧稳定外壳，然后挂载表单。表单按最终尺寸布局后独立播放 520ms 的逐行进入，不跟随外壳缩放、透视或模糊。关闭立即卸载真实表单，外壳继续原有退出形变。
- 移除预载表单、录制帧计数、移动中表单缩放与揭示裁切的旧分支，以及仅验证旧准备条件的三条过时测试。正常数据更新不重新启动表单进入动画，每次重新打开则重新挂载并播放。
- 课程顶部和两侧轮廓光由三道硬描边改成单次绘制的缓存渐变网格。边缘到内部使用平滑衰减，向底部柔和收束，透明中心不参与绘制；沿现有真实课程轮廓裁切。无新增 Backdrop、模糊纹理、RuntimeShader 或离屏层，保留浅色壁纸的主题色增强和白光减弱。
- 个性化滑块从 32×20dp 小幅增至 34×22dp，保留原来的触摸轨道、端点留白和 1.2 倍按压尺寸。个性化仍在外壳停稳后挂载表单。
- 首页课程编辑、个性化、快捷菜单等共用背景在模糊后绘制最高 8% 的黑色遮罩，使用现有模糊进度同步淡入淡出。遮罩位于缩放/采样缓存之外，覆盖完整背景且不压暗弹层，不增加 Backdrop、RenderEffect 或离屏采样；多个弹层交接使用同一进度上界，避免重复压暗。

电脑版实现位于 `feature/importing/WebCompatDelegate.kt`，通过现有 Compose `AndroidView` 的创建、释放及 `WebViewClient` 回调接入：

- 使用用户指定的 Windows / Chrome 120 桌面 UA，手机版使用 `WebSettings.getDefaultUserAgent`；切换时停止当前加载，先更新脚本注册及 UA，再 reload。主网页和弹出网页保持同一模式。
- 引入 AndroidX WebKit 1.14.0；支持 `DOCUMENT_START_SCRIPT` 时，在首个 `loadUrl` 之前注册文档开始脚本。主网页首次导航等待 WebView 实际布局宽度，避免拿全屏宽度代替分屏/自由窗口宽度。
- `layoutWidth=max(1280, WebView.width/density)`，宽度尚不可用时取当前窗口配置的 `screenWidthDp`；缩放等于实际 CSS 宽度除以布局宽度，使用 `Locale.US` 和四位小数。
- 脚本清理站点的 viewport，并维护唯一的 `data-nexio-desktop-viewport` 标记。head 尚不存在时短暂观察文档，出现后只观察 head；修改自己的属性时断开观察、仅写入变化值，避免循环和无意义的视口重排。DOMContentLoaded / load 再次校正，重复调用更新现有观察器而不叠加。
- 宽度变化同步刷新当前页面的 viewport 与下一文档的注册脚本，不重载正在填写的登录表单。切回手机版删除注册，reload 后使用站点原始 viewport。退出释放注册和布局监听；渲染器退出时只清理本地引用，随后沿既有恢复流程重建 WebView。
- 不支持文档开始脚本的旧 WebView 在 onPageFinished 校正视口；它无法追溯修复页面早先写死的内联尺寸。保留现有 Cookie/第三方 Cookie、拾光桥接与 POST 转发、页面安全设置，以及 SleepDown 的取色和渐变图层挂载。

已在 Node 本地 DOM 模型中执行生产代码里的视口脚本，验证 9 个场景：重复视口、head 延后出现、站点后插视口、修改/删除自有视口、窗口尺寸更新、DOMContentLoaded/load 校正、子 frame 隔离及手机版新文档保留原视口。该检查不等同于真实 WebView 网站验收。

验证与交付：

- 源码提交至 `e909b14`。临时测试源过滤器覆盖 11 个相关测试类，共 67 项定向测试全部通过（0 失败、0 错误），包括视口计算、地区格式、课程编辑/快捷菜单、周视图落地运动、网页取色和通知边界。未运行全仓历史测试。
- 官方 Miuix 0.9.3 加公开补丁的本地工作树完成 `testGithubDebugUnitTest assembleGithubRelease`，耗时 10 分 11 秒。追加背景压暗后再次完成 `assembleGithubRelease`，耗时 7 分 32 秒；保留 R8、资源压缩、lintVital 和正式签名。
- 最终 APK v2 签名验证通过，包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.5_beta5` / 31。OPPO Find X9（PLJ110，Android 17）无线覆盖安装成功，设备 APK 与本地产物 SHA-256 一致。
- 命名产物 `SleepDown-Schedule_v1.2.5_beta5.apk`，7,111,015 字节。SHA-256：`1821e1a693c90734846fad4602f174b569f9044bd5b8b1d3533c16b7f607abfb`。

按用户此前要求，未自动启动应用，未继续实机交互、教务网站显示或 120 FPS 验收。脚本与单元测试不能证明设备上所有站点的布局或动画帧率表现。
