# 横屏导航滑选、侧栏切换触控与个性化标题修订

继续草稿 PR #78，用户反馈横屏导航不能用滑动方式选择，侧栏/标签栏切换按钮仍与相邻触控冲突；个性化一级标题过大，要求与二级同字号。本轮没有修改课程渲染、课程数据或保存格式。

## 修改依据与实现

- 首轮从部分 smali 判断程序化创建的容器未启用 clickable，增加该标志并用容器 OnTouchListener 跟踪手势。用户随后确认仍不能拖动；完整组件审计发现内部 TextView 子项本身可点击，容器监听并不能接收子项处理的事件，该判断不足以解释或解决问题。修正依据与后续产物见[COUI HD 与分发审计](2026-10-19-coui-hd-dispatch-audit.md)。本报告 APK 为该失败尝试的历史记录，不作为滑选已修复的证据。
- 原生 MOVE 会实时改变选中项，旧回调立即导航，而 `HomeSidebar.navigationEnabled` 在页面切换动画时关闭，导致后续拖动被禁用；Compose 仍持有旧页码时还会将选中条拉回。横屏导航新增 `commitOnRelease=true`：保留原生拖动、弹簧、跟手光和触觉，抬手后提交最终页码；取消则还原，禁用/销毁释放手势。其他分段控件默认仍即时提交，键盘等无触摸选择保持即时回调。外部选中同步暂时解绑 listener，避免程序回写被当成用户选择。
- 原按钮宽 36dp，触控扩展会进入第一项；液态玻璃选中条按压后也会越过其布局边界。切换按钮改为独立的后绘制层、48dp 点击区域，并在 native/Kyant 两条路线中预留相同的按钮空间与间距。Kyant 原 leadingContent 只保留空白，按钮不再嵌入选中条所在的输入树。
- `PersonalizePanel` 的“首页壁纸”和大分区标题统一改为 `titleMedium`，与小分组同字号，一级保留 SemiBold、二级保留 Medium；沿用上一轮已收紧的间距。

代码提交：`f82767ae`（横屏手势及按钮触控）、`7b073d50`（标题字号）。原任务外 stash 与其他工作树保持原处。

## 构建与安装

- 完整 `assembleGithubRelease --console=plain --no-parallel --max-workers=2` 通过，耗时 7m54s；159 个任务中 21 个执行、138 个复用，包含 Kotlin 编译、R8、资源压缩、lintVital、打包与签名，没有使用跳过资源压缩参数。
- APK：`app/build/outputs/apk/github/release/app-github-release.apk`，7,574,871 字节，SHA-256 `d09152ef9e09dfbce710c64472bba103f3c4c67aaa7b055e890b5206b4faa74d`。包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7` / `35`。
- `apksigner verify --verbose --print-certs` 通过，v2 签名有效；证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与原安装包一致。
- 用户确认重新连接 USB 后，通过项目 SDK ADB 5038 通道重新列出设备，向 USB PLJ110（Android 17）执行 `install -r`，返回 `Success`。没有清除数据或启动应用。
- 按用户要求没有运行额外单元、截图、自动 UI 或性能测试。横屏滑选、按钮边界和标题观感由用户自行验收，不能将编译或安装通过写成实机交互已验证。
