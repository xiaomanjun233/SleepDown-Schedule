# SleepDown 公共组件与凝光材质

当前入口为 `app/ui/ProvideVisualEffects`：读取视效偏好，安装公共窗口背景源，并由
`ProvideCouiMaterials` 选择材质。Android 14 以上可选凝光与液态；旧系统使用液态的可用效果，
不改写用户保存的选择。私有系统 APK 反射材质后端已退役，当前凝光使用本地 COUI Kit 与公开 API。

课程管理 Activity、系统状态栏回顶及厂商课程/通知功能有独立职责，不随材质后端清理删除。
实验渠道、Manifest 类名与 Intent 兼容仍按开发指南维护。

## 布局与材质

| 组件 | 布局、输入与状态 | 材质 |
| --- | --- | --- |
| 普通胶囊按钮 | `CapsuleButtonLayout`：尺寸、内容、点击及禁用语义 | `LocalButtonMaterial` 提供装饰；液态保留按压伸缩和高光 |
| Alert / Picker / 输入弹窗 | `ControlDialogs` → Kit Dialog：位置、安全区、IME、退场完成 | 两种模式统一凝光材质，已删除液态皮肤分支 |
| 分段标签 | `ControlSegments`：共用标签布局与选择会话 | 凝光或液态的容器、指示器；API 26–33 使用可用的普通标签输入 |
| 课程卡片 | 业务布局与明确的 `CourseMaterialSampling` 句柄 | 凝光 / 液态共享预模糊纹理及坐标；局部轮廓、折射、焦散保留 |
| 滑条、开关 | 按用户要求，各自保留现行实现 | 保留各自现有反馈与提交/取消语义 |

普通弹窗统一沿用凝光原有底部布局。切换材质不会替换窗口、重建正文草稿或触发确认。
无背景源时仍显示相同布局，使用普通底色。表单校验和保存由业务决定，
窗口只通知请求关闭和实际退出完成，不能用固定延时提前销毁正文。

`LiquidButton`、`LiquidAlertDialog` 是现有业务入口名称，不代表另外一套液态布局。
按钮材质请求没有点击回调或内容槽；材质不能再接管整套按钮。
`RefractionShape` 保留宿主的实际裁剪轮廓，并给液态折射提供明确圆角参数。

真实锚点的课程编辑、菜单 Morph、Dock 融合及现行 Popup 仍保留各自交互路线。
它们不是普通 Dialog 的回退路径。改造时先保留已发布的动作提交、返回和层级语义，
不能因为都使用玻璃就强行替换为同一种窗口动画。

## 采样和生命周期

背景源按场景拥有，不是每个控件自行录制，也不是全应用只保留一个含所有浮层的源：

- 课程只采样壁纸。一次源录制、一次预过滤，普通卡片和浮动副本共用；
  `CourseMaterialPreferences` 保留原凝光着色、模糊偏好键。
- 凝光底栏采样可见壁纸和正文，固定顶栏有独立采样源。工具栏和页内按钮按实际下层内容选择源，整页转场仍记录全部可见内容。
  `rememberCouiGlassBackdrop` 是已有源的适配，不额外截屏。
- 独立弹窗使用父窗口完整源，`rememberGlassWindowBackdrop` 让液态使用同一个源。
  当前窗口另供下一层弹窗采样，不能反过来采样自己。
- 完整窗口源没有消费者时直接绘制。retain/release 贯穿弹窗退出动画，
  关闭完成后释放需求；同窗口 Scaffold 复用现有源。
- 多个消费者共用原始背景不等于全部共用一个模糊结果。只有同源、同配方且有复用价值时
  才共享过滤结果；少量、小面积、不同配方的动态控件保留局部处理。

`CouiMaterialSurface` 保留凝光课程轮廓和静态壁纸焦散；静态壁纸路径不提前创建实时背景适配。
`CouiCardProjection` 和 `CardLightGeometry` 保留实际使用的投光与几何。
`LauncherPagerIndicatorHost` 保留公开 View 的圆点、连续拖动、取消恢复和 RTL。

## 业务与兼容

视效切换不进入数据库事务。原 `coloros_course_material` 偏好与液态设置独立保留；
超级性能档继续按统一策略抑制采样。课程、备份、导入、通知协议与签名不因材质重构改变。

操作按 `LiquidAlertActionStyle` 表达 Primary / Secondary / Destructive / Cancel，
不按文案推断。主操作使用主题色，破坏性操作保留红色语义，取消在原取消位置。
动作回调在所属窗口/业务既定边界执行一次；重新打开或中途反向不能提交旧会话。

## 证据与历史

本轮结构、失效规则和验证见[首页渲染与公共材质重构](../reports/2026-11-11-home-render-architecture.md)。
测试计数说明资源与调用边界，不等同于实机帧率。清系统缓存后的 GPU 编译问题仍需同设备测量。

原厂调查保留在历史报告中，不再作为当前源码装配说明：
[凝光迁移审查](../reports/2026-11-08-coui-glow-migration-review.md)、
[材质入口盘点](../reports/2026-11-08-glow-material-entry-audit.md)、
[卡片投光调查](../reports/2026-11-04-schedule-manager-card-glow.md)、
[页点与照亮进度](../reports/2026-11-05-native-page-indicators-and-card-light.md)。
