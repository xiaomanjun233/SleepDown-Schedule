# Popup 新版动画与设置返回周视图（2026-10-07）

## 上游核对

通过 Codex GitHub 插件核对 `HaoZai000/NexioSchedule` 的 master，当前最新仍为 [2daf4c3 / v1.6.4 beta15](https://github.com/HaoZai000/NexioSchedule/commit/2daf4c387ecf298cc78df85004db88bb8d68fb7e)，提交时间 2026-10-06。

读取该提交的 `ListPopup.kt`、`ListPopupLayout.kt`、`LiquidGlassDropdownMenu.kt` 和 `MainActivity.kt`。不能只看 `ListPopupDefaults` 的旧兼容参数：实际 `ListPopupLayout` 使用独立尺寸与锚点进度，进入分别是 0.77/220 与 0.77/420，退出分别是 0.85/650 与 340ms `(0,0,0,1)`。

## Popup

- 公共普通下拉启用可选 Morph 路径，使用上述实际参数，保留原 Miuix 触发、定位、点击外部关闭、选择和预测性返回入口。
- 首帧锚点在同一测量/放置 pass 中确定，不等待上一帧尺寸通过 State 回报。适配上方展开、左右对齐和可用窗口边界。
- 面板宽高独立增长、锚点先迁移；文字独立等比缩放和显隐。进入显隐区间为 0.22–0.62，退出为 0.30–0.78；瞬态文字模糊最大 6dp，两端无 RenderEffect。
- 采样画布固定，动态轮廓裁切，描边跟随实际面板尺寸。弹簧过冲由整个已展开面板承担，避免在画布外揭露空白。保留 SleepDown 的 10dp 玻璃模糊、中性对勾、上下高光描边、跟手光与形变，不压暗页面或触发行。
- 关闭锚点淡出时，同时停止原来仍在后台运行并导致触发行重组的 alpha 动画与 fraction 回调。
- 这是在本地 Miuix 入口中的独立适配实现，未复制 Nexio 的业务文件。二级级联菜单保留既有父子锚点 Morph，首页菜单转中心弹窗的曲线不属于本次普通下拉修改。

## 设置返回周视图

代码确认两个额外成本：

1. 共享课程模糊的 source 原为 `backgroundBackdrop`，其内容已经包含整页位移、圆角和设置页背景。每帧 `pageSampleKey` 改变都会使整屏共享模糊及全部课程样本失效。
2. `sharedCourseBackdropExpected` 依赖 `rootPageMotion.retains(false)`。设置页停稳后，预渲染节点被卸载、共享源消失；已保留的周视图卡片换回普通 source，返回首帧再重建共享源与采样效果。

现在通过 `glass/` 增加壁纸自身坐标域的 producer，置于页面变换内部，只记录壁纸与既有采样色调。课程共享模糊不再把切页位移/页面圆角作为内容变化；课程卡依然根据相对壁纸的位置实时采样，保留分组甩尾和折射。背景/顶底栏仍取完整页面源。

壁纸和共享预渲染节点随首页保留，设置页停稳时暂停其绘制。由此既避免返回时切换课程材质源，也不会在后台持续预渲染；壁纸、亮度、尺寸、密度或材质变化仍按原缓存规则更新。未冻结课程运动、删除效果或增加 bitmap 读回。

## 验证

- 首次编译发现绘制作用域需要显式 `this@onDrawWithContent`，已修正。
- Miuix 第五补丁已导出；前四补丁基线正向检查、本地实现反向检查通过。
- 完整 `assembleGithubRelease --no-daemon --console=plain --no-parallel --max-workers=1` 成功（6m54s），包含 Kotlin 编译、R8、资源压缩、lintVital 与签名。`git diff --check` 通过。
- 构建期间再次使用项目脚本检查 ADB 5038，`devices -l` 和 `mdns services` 均为空，因此未覆盖安装，尚未执行同机视觉与帧耗时对照；代码层面的重复工作消除不等同于实测帧率结论。
