# Beta5：移除 HDR，收细主题色轮廓光

基于 `7d57990`。用户反馈 HDR 卡顿，要求去掉 HDR，使用 Kyant 方法提亮课程主题色，并进一步收细左右两侧的光。

实现提交：`c84b2b0`。

## 实现

- 本地 Kyant 源码 `third-party/kyant-backdrop/src/commonMain/kotlin/com/kyant/backdrop/highlight/HighlightStyle.kt` 中，`Plain` / `Default` 高光使用 `BlendMode.Plus`。课程网格光与底部彩色渐变统一采用相同的加色混合；输入仍为课程 RGB，白色画笔仅作顶点色的中性乘数。普通显示范围内增加对应颜色通道，不请求屏幕额外亮度。
- 保留 `drawWithCache` 内的渐变网格、原卡片 Shape 裁切与平滑衰减；侧边宽度从顶部的 55% 缩至 35%（顶部可达 20dp 时，侧边从 11dp 缩至 7dp）。日/周视图及玻璃明暗风格共用该路线。
- 删除 `HdrUi.kt` 的能力门控、窗口颜色模式和 headroom 设置、显示监听器，以及独立 HDR 颜色 RuntimeShader。MainActivity 和课程编辑器不再为 HDR 维护状态、回调或背景缓存标识。
- 删除仅覆盖已移除 HDR 功能的 `CourseHdrUiTest`。普通光效继续直接绘制，没有新增 Backdrop consumer、模糊纹理或离屏层。

## 验证与交付

- 已检查 diff 及生产代码残留：没有课程 HDR 开关、窗口请求、shader 或编辑器回调引用。
- 首次编译发现清理通配导入时遗漏 `appUsesDarkTheme` 引用，补回明确导入后，官方 Miuix 0.9.3 加公开补丁工作树完成 `assembleGithubRelease`，耗时 13 分钟，包含 Kotlin 编译、R8、资源压缩、lintVital 与正式签名。
- APK v2 签名校验通过，版本 `1.2.5_beta5` / 31；命名产物 `SleepDown-Schedule_v1.2.5_beta5.apk`，7,111,015 字节。SHA-256：`c5f7389d93696e89f514e3ddd062afab1fc98462688bafbd0c3e93e5dff91a26`。
- OPPO Find X9（PLJ110，Android 17）无线调试途中离线，使用用户提供的新端口重连后覆盖安装成功；设备已安装 APK 的 SHA-256 与上述产物一致。未自动启动应用。
- 按用户要求不追加交互或帧率测试；不将代码开销减少表述为已实测达到 120 FPS。
