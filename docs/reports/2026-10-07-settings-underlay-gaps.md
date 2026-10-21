# 设置页卡片间隙的背景采样修复（2026-10-07）

## 问题与根因

AI 设置等分组卡片页上，Popup、中心弹窗及其背景模糊会在卡片间隙出现明显的横向断层。

`GlassMiuixDetailActivityScaffold` 的 underlay 链原为 `background → centeredDialogSceneProducer`。页面底色显示正常，但录制节点只能捕获自己内部的绘制内容，不能捕获前置的背景，因此采样中的卡片间隙和页面边距是透明像素。模糊透明像素再叠回原页时，原本未模糊的边界会透出。独立的 `backgroundBackdrop` 也采用了同样的错误顺序，录到的空 Box 不包含底色。

## 修改与覆盖范围

- 将详细设置页的完整场景链改为 `centeredDialogSceneProducer → background → content`，同一次录制包含底色、卡片、间隙、标题和顶栏。
- 将纯背景链同步改为 `glassBackdropProducer → background`，为页内玻璃提供实际底色。
- Popup 从 `LocalSettingsPopupBackdrop` 取源，中心弹窗表面和整页背景模糊从 `LocalCenteredDialogSceneBackdrop` 取源；该 Scaffold 已把两者指向同一完整场景，所以无需分别增加采样层。
- 根设置页和平板详情页的 underlay 已使用正确顺序，保持现有渐变背景支持。
- 弹层 host 继续位于采样内容之后的同级节点，避免把弹层自身录回背景。保留现有模糊半径、材质、选项反馈和开合动画。

## 验证

- `git diff --check` 通过，已核对 Popup、中心弹窗及背景模糊的共同数据来源。
- `assembleGithubRelease --no-daemon --console=plain --no-parallel --max-workers=1` 成功（6m45s），包含 R8、资源压缩、lintVital 和签名。
- 属于局部绘制顺序修复，未增加无关业务测试；尚未执行实机视觉复测。
