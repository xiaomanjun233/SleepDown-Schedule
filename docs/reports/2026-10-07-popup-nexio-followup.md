# Popup 采样与首页性能跟进

## 本轮范围

修正公共 Popup 展开中的采样漂移和选中反馈；通用设置从导航模式起拆成独立卡片；应用图标深浅色改为离开应用后更新桌面入口；统一 AI 助理 / AI 设置的空格；设置首页右侧箭头统一内收 8dp；加号菜单及其中心弹窗在返回途中交接回按钮。

## Nexio 当前链路核对

2026-10-07 通过 Codex GitHub 插件读取 `HaoZai000/NexioSchedule` 的 master，最新提交为
[`2daf4c3` / v1.6.4 beta15](https://github.com/HaoZai000/NexioSchedule/commit/2daf4c387ecf298cc78df85004db88bb8d68fb7e)，提交时间 2026-10-06。
本轮对照的是该提交的完整实现，未依据旧报告推测最新版，也未复制其 AGPL 业务实现。

| 环节 | 上游实际实现 | SleepDown 本轮判断与处理 |
| --- | --- | --- |
| 壁纸 producer | `MainScheduleScreen` 稳定 onDraw 身份，sourceKey 标记壁纸内容，0.48 倍共享模糊 | 已有同类共享前缀和稳定 provider，继续复用 |
| 每卡采样 | `DrawBackdropModifier` 用来源、内容版本、相对位置和 buffer 尺寸判断录制；无效果可直接绘制共享层 | 补齐普通课程卡的共享采样指纹复用；保留现有 lens、裁切和独立 shader，不扩展无折射直绘路径 |
| 可见性与合成层 | 屏外跳过采样，静止课程卡不挂额外 graphicsLayer，滚动位置使用普通 holder | 现有视口判断、按需 card/tail transform 和坐标 holder 已覆盖这些路径 |
| 效果图 | remember 稳定 effects，按大小、形状、采样比例复用 RenderEffect | 为课程材质和 Popup 提供完整 effect key；Popup 的 0–8dp 内容模糊改为图层阶段按需缓存 8 档，端点释放效果 |
| 菜单反馈 | 高光夹在玻璃与内容之间；光晕大小受限；松手时光留在原地淡出，形变回弹 | 公共 Popup 调整同样的层次关系，缩小光晕；已选项使用蓝色底，滑过项使用明暗适配的中性底 |
| 返回交接 | 收起过程中提前显示触发图标，菜单内容先消失 | 本项目沿用自己的真实锚点路径，提前交接加号并同步退出旧表面与内容 |

已阅读的主要文件：

- [MainScheduleScreen](https://github.com/HaoZai000/NexioSchedule/blob/2daf4c387ecf298cc78df85004db88bb8d68fb7e/app/src/main/java/com/haooz/chedule/ui/screens/MainScheduleScreen.kt)、[MainActivity](https://github.com/HaoZai000/NexioSchedule/blob/2daf4c387ecf298cc78df85004db88bb8d68fb7e/app/src/main/java/com/haooz/chedule/ui/activities/MainActivity.kt)
- [CourseCard](https://github.com/HaoZai000/NexioSchedule/blob/2daf4c387ecf298cc78df85004db88bb8d68fb7e/app/src/main/java/com/haooz/chedule/ui/components/CourseCard.kt)、[DrawBackdropModifier](https://github.com/HaoZai000/NexioSchedule/blob/2daf4c387ecf298cc78df85004db88bb8d68fb7e/app/src/main/java/com/kyant/backdrop/DrawBackdropModifier.kt)、[SharedBlurBackdrop](https://github.com/HaoZai000/NexioSchedule/blob/2daf4c387ecf298cc78df85004db88bb8d68fb7e/app/src/main/java/com/kyant/backdrop/backdrops/SharedBlurBackdrop.kt)
- [LiquidGlassDropdownMenu](https://github.com/HaoZai000/NexioSchedule/blob/2daf4c387ecf298cc78df85004db88bb8d68fb7e/app/src/main/java/com/haooz/chedule/ui/basic/LiquidGlassDropdownMenu.kt)、[ListPopup](https://github.com/HaoZai000/NexioSchedule/blob/2daf4c387ecf298cc78df85004db88bb8d68fb7e/app/src/main/java/top/yukonga/miuix/kmp/basic/ListPopup.kt)

## 具体根因和修复

1. Popup 材质原先通过 LayerBackdrop 的位移路径采样。Miuix 展开和 Kyant 手势形变都在其祖先图层缩放，只有位移补偿会拉伸并移动底图。本轮为 Popup 的 LayerBackdrop 增加完整坐标变换；跨窗口时通过屏幕坐标建立仿射基底。方法语义已对照 [Compose LayoutCoordinates](https://developer.android.com/reference/kotlin/androidx/compose/ui/layout/LayoutCoordinates)。其他已有 Morph 的专用坐标包装不变。
2. 前一轮使用 `highlight.foregroundModifier`，Plus 混合叠在选中框和文字上。本轮改为内容前绘制，略增加玻璃底色支撑，区分已选与滑过状态，不再压暗页面或触发行。
3. 普通课程卡此前仅在冻结场景复用样本。现在对明确使用共享壁纸且没有自定义采样变换的课程卡，比较源版本、相对位置、尺寸和密度后复用。移动和壁纸变化仍重新录制，不增设 bitmap 或额外 GPU 层。
4. 上轮为中心弹窗加入了 `GraphicsLayer.toImageBitmap()`，打开和关闭均发生读回，并在关闭前增加两帧等待。本轮撤销这一改动，继续复用已录制 GraphicsLayer；为冻结中的表单补齐非空场景 key，使其子玻璃采样真正能复用。打开曲线仍为既有中心弹窗曲线。
5. 加号菜单返回时，旧玻璃壳与按钮 clone 此前会同时存在到接近终点。本轮提前显示按钮并退去旧壳；中心弹窗的内容透明度也随按钮接管归零，避免末尾叠着一张缩小表单。
6. 图标 mode/style 入口原来立即调用 `applyStoredMode`，可触发厂商 Launcher 对前台任务的刷新。本轮只持久化选择，复用已有进程 ON_STOP 更新 alias 的路线，未加入应用重启调用。

## 验证记录

- 首次编译发现新增 Spacer 缺少导入，补齐后完整 `assembleGithubRelease :kyant-backdrop:testAndroidHostTest` 成功（6m11s），包含 R8、资源压缩、lintVital 和签名。
- 玻璃模块 6 项定向测试全部通过：录制缓存 3、动态轮廓 1、采样几何 2。本轮样式和文案未追加机械测试。
- Miuix 补丁在前四份补丁的基线上正向检查、在本机源码上反向检查均通过。Kyant 补丁在确切上游文件上应用后，13 个文件与本机源码一致，并通过反向检查；补丁固定 LF，避免跨平台换行破坏应用。
- 最终 `assembleGithubRelease --no-daemon --console=plain --no-parallel --max-workers=1` 成功（8m39s），包含最后的顶部版本卡箭头对齐与按需模糊缓存调整。
- 通过项目 ADB 5038 的 USB 通道覆盖安装到 PLJ110 / Android 17，安装返回 Success；包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7_beta3` / code 34，设备更新时间 `2026-10-07 18:18:33`。未自动启动应用。

实机逐帧采样暂不执行，遵循用户上一轮“稍后体验”的选择；本报告不把录制次数减少或编译通过当作帧率结论。
