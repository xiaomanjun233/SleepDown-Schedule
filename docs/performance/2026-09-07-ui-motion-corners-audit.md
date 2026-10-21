# 编辑器动画与连续圆角检查

## 修改依据

- `CourseEditorContainerOverlay.kt` 原先在外壳与背景动画的整个 coroutineScope 完成后才进入 Open。现在外壳 animateTo 完成即进入 Open，表单仍只在 Open 时挂载；背景动画继续独立完成。没有提前挂载表单，也没有增加额外等待。
- `LiquidButton.kt` 的 shape、effects、surface 回调按实际输入 remember；`ProgressiveBlur.kt` 的 shape 和 effects 同样稳定。玻璃公共入口原有 rememberUpdatedState 与固定回调保留。未进行帧率测量，不据此声称性能提升幅度。
- `ImportUi.kt` 移除新增的灵动岛轮廓光、对应颜色动画和 WebView 主题色读取，保留原有材质。

## 圆角范围

应用 Compose 控件、Material 主题圆角、手绘课程卡、快照、玻璃合批裁剪及转场裁剪使用 Kyant 连续曲率路径。圆形控件保留圆形。新增 `core/ui/designsystem/ContinuousCorners.kt` 衔接 Material CornerBasedShape 与像素画布，位图 Widget 使用相同路径。

桌面宿主 RemoteViews 的 XML shape 背景仍使用 Android 原生圆角（`widget_*background*.xml` 等）；此类资源不支持 Kyant Shape，本次未重做桌面宿主布局。关闭状态的 Oplus 原生窗口 outline 实验链路也未改动。

## 影响边界

修改涉及 UI 与绘制，不改数据库、备份、导入协议、后台任务和用户数据。编辑器保持此前恢复的尺寸及逐行动画参数。

## 验证

最终构建和安装结果以本任务完成报告为准。未执行自动打开应用或实机视觉验收，未进行哈希校验。
