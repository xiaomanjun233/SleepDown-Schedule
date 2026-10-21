# 首页形变源与切换跟进

用户在 Popup / 表头修复后追加反馈：日视图停课卡片的形变层仍不对应原卡，首页仍卡顿，中心弹窗回加号要直接恢复正常形态。

## 定位与修改

- **加号返回内容**：原实现使用打开菜单时截取的 54dp 按钮 Bitmap。截图发生在按压反馈尚未结束时，包含放大、跟手偏移和录制边界裁切。中心弹窗与一级菜单返回时改用同一个 `SleepDownFloatingAddButton` 的非交互版本，保持蓝色玻璃、白色加号及正常尺寸。原有返回轨迹和中途透明度叠化不变；跨 Activity 路线的截图协议未改动。
- **日视图停课卡片**：原卡启用 `expandedOutlineLight`，编辑形变壳未传递该标记，造成停课灰色底部光分布不同。按请求中捕获的日/周来源对齐材质参数。停/补角标原本位于卡片轮廓外侧，形变副本却在壳内被圆角裁切；改为随壳定位、独立淡出的矢量标签，并使用 `ModulateAlpha` 保留圆角外侧的少量溢出，不新增玻璃采样或模糊层。
- **设置返回首页**：`homeSwitchGroup` 之前仅运动时挂 `graphicsLayer`，起止帧会同时为课程、标题等内容增删图层，改变其玻璃采样坐标链。现在复用无裁切、无 RenderEffect 的位移节点，动画仍使用原六组拖尾轨迹，静止时位移归零，不强制申请 Offscreen 纹理。此项减少节点重建，不把它等同于已量化的帧率提升。

## 验证

- `assembleGithubRelease --no-daemon --console=plain --no-parallel --max-workers=1` 成功，耗时 6m30s，包含 Kotlin 编译、R8、资源处理、lintVital 和签名；`git diff --check` 通过。局部可逆的视觉修改未追加机械测试。
- 最终 APK 为 `app/build/outputs/apk/github/release/app-github-release.apk`，7,008,075 bytes，SHA-256 `C140C97A4E835A8752843EA3E06455F52C0DB3C9DDFE51F16F10B29F13E6730C`。
- 优化前基线保留在忽略目录 `tmp/home-source-handoff-before-20261007.apk`，SHA-256 `46AB3652A4F02359B97B0907AD14C12E0D7651BE4BA0CB30DC44B7D422F07307`，用于后续同设备对照，不提交 APK。
- 项目 ADB 5038 没有在线设备；收尾时 mDNS 出现无线服务，但连接遭拒。已向用户请求当前端点，未安装、未启动应用，实机同场景帧耗时和形变画面仍待验证。
