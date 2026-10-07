# Popup 轮廓、日视图源卡片与主题预览

## 修改

- 公共 Miuix Popup 的形变外壳、内容裁切、高光描边和平台阴影使用同一个 Miuix 连续圆角路径。浅色模式扩大低透明度环境阴影，帮助白底弹层与白色设置卡片分离；保留 10dp 背景模糊、原有开合曲线和按场景区分的触发行反馈。
- 日视图点击时传递实际展示的课程、置灰状态、角标、字号比例和时间/周次显示选项。形变文字共用原卡片内容组件，不再只从“停”字推断材质。编辑表单仍编辑原课程，显示用的调休投影不会替换保存对象。角标跟随外壳的投影形变和实时圆角，保持在卡片裁切外侧。
- 深浅色模式预览改为首页的简化矢量图：日视图包含日期栏、摘要、时间胶囊、宽课程卡片和导航；周视图包含星期表头、节次、彩色课程网格和导航。两套各有亮/暗版，读取通用设置草稿的 `defaultHomeMode`，修改默认首页视图后立即切换；兼容旧 `TWO_DAY` 值为日视图。
- SVG 原稿位于 `docs/images/theme-previews/`，应用使用同路径数据的 Android VectorDrawable，不增加运行时 SVG 解码依赖。四张图已渲染检查，SVG 与对应 VectorDrawable 的路径数量及数据一致。
- 本地 Miuix 修改已导出到 `patches/miuix-popup-slide-feedback.patch`，补丁正向和反向应用检查通过。

## 验证

- `git diff --check`、Miuix 补丁正向/反向应用检查通过。
- `assembleGithubRelease --no-daemon --console=plain --no-parallel --max-workers=1` 成功，耗时 7m27s，包含 Kotlin、资源编译、R8、lintVital、资源压缩、打包与签名。未额外添加机械样式测试。
- APK：`app/build/outputs/apk/github/release/app-github-release.apk`，`1.2.7_beta3`（34），7,025,523 bytes，生成时间 2026-10-07 22:55:52，SHA-256 `83464E3A75DC635A228DA53316D3CBACD71345AAF2CDADB96EEEA39D34E3C7E9`。
- ADB 5038 服务已核对为本机 Android SDK。mDNS 发现既有 PLJ110，直接连接一度成功；之后 TLS 读取中断、设备转为 offline，重连广播端点超时。构建后的安装流程在连接检查处停止，未执行 `install -r`，本轮尚未覆盖安装。已向用户询问手机当前显示的无线端点，没有改动网络或配对配置。
- 尚未进行实机动画逐帧或帧耗时验收；源卡片修复依据代码中的样式差异，不能据编译成功断言实机衔接已经完全消除。
