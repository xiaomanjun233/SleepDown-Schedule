# Popup 入口反馈与紧凑开关（2026-10-07）

## 本轮反馈

用户截图中的“隐藏后台卡片”仍出现滑块偏下；上轮调整行内测量未解决该现象。本轮同时要求缩小设置页开关、取消首页个性化 Popup 外部入口行的单点压灰，并把 Popup 模糊改为 10dp、描边改为上下沿较亮且两侧较淡。

## 实现

- `SettingsToggleRow` 统一使用现有紧凑开关，轨道从 64×28dp 改为 52×24dp，滑块从 40×24dp 改为 32×20dp。设置行的文字样式、说明及原有最小行高不变。
- `LiquidToggle` 将轨道和滑块放入固定尺寸的共同坐标系，滑块通过相对布局偏移定位，纵向由轨道与滑块高度差确定，RTL 使用同一相对定位。静止白色滑块直接在本地绘制，按压期间才绘制玻璃采样、折射与阴影；移除滑块内部常驻 Offscreen 裁切层，并跳过零高度的轨道采样变换。该调整针对绘制路径，截图问题是否完全消失仍需首开实机复测。
- Miuix `BasicComponent` 增加默认开启的 `showIndication`。公共玻璃 Dropdown 的入口随 `holdAnchor=false` 关闭普通点击压灰；两种 Dropdown 重载均覆盖，点击语义、展开回调、震动和 Popup 内的滑动选项反馈继续保留。
- 公共 Popup 使用 10dp 模糊，1dp 描边从对角渐变改为上下对称的垂直渐变。仍由 Miuix 的实际动画轮廓绘制描边，选中项不增加常驻底色。
- Miuix 源码修改已同步至 `patches/miuix-popup-slide-feedback.patch`，包括新增涉及的 `Component.kt`。

## 验证记录

- `git diff --check` 通过。
- Miuix 补丁相对前四个补丁基线的正向应用检查通过；对本地最终源码的反向应用检查通过。
- `assembleGithubRelease --no-daemon --console=plain --no-parallel --max-workers=1` 成功，耗时 6 分 18 秒，包含 Miuix / 应用 Kotlin 编译、R8、资源压缩、lintVital 和签名打包。APK 为 `app/build/outputs/apk/github/release/app-github-release.apk`。
- 项目 5038 ADB 通道初次检查无设备，随后 mDNS 发现另一台无线设备，其序列号与此前 PLJ110 不同。已请求用户确认安装目标，未向未知目标安装。尚未覆盖安装或复测首开开关、Popup 单点与拖动。
- 本轮为可逆的局部视觉与点击反馈调整，未追加无关业务测试。
