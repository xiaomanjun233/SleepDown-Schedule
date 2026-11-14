# SleepDown 跨 Activity 转场框架

## 职责与入口

业务入口声明目标、源 View 或截图，以及可重新解析的返回锚点。`ActivityTransitionCoordinator` 根据路线选择 SharedViewKit 或 Legacy。

| 入口 | 职责 |
| --- | --- |
| `transition/TransitionRoute.kt` | 稳定 wireName、Legacy 效果、允许的原生候选与真实目的 Activity |
| `transition/TransitionSession.kt` | 状态边、callback generation 与父子会话 |
| `transition/TransitionPayload.kt` | 进程内源 View、Bitmap、锚点解析与释放回调 |
| `transition/ActivityTransitionCoordinator.kt` | 导航、窗口装配及后端分派 |
| `transition/CrossActivityTransitionHost.kt` | 原生稳定内容与 Legacy renderer 的仲裁、原有保存退出握手 |
| `transition/SharedViewKitBackend.kt` | Kit 握手、回调、返回更新、坐标转换和有界超时清理 |
| `transition/SharedViewKitCapabilities.kt` | 标准 metadata 的坐标与返回类型解析 |
| `transition/SharedViewKitApi.kt` | 唯一 SDK 适配契约，用于渠道隔离和握手测试 |
| `src/github/.../transition/PlatformSharedViewKitApi.kt` | 官方公开 SDK 的直接类型调用 |
| `src/store/.../transition/PlatformSharedViewKitApi.kt` | 不含 SDK 的不可用实现 |
| `transition/LegacyTransitionBackend.kt`、`transition/legacy/` | 应用内锚点过渡、快照和绘制 |

系统原生路径统一查询金标能力。四个旧 Oplus Activity 名作为不导出的恢复 alias 保留。候选路线与真实 opaque 目的页以 `TransitionRouteCatalog` 为准。特殊课程和新增反馈都用独立的原生与 Legacy 目的页，Legacy 从实际卡片或加号的像素锚点展开。

`transition/legacy/SharedViewFallbackMotion.kt` 按锚点类型计算尺寸、纵向位置和内容交接。普通卡片开启为 540／500 ms，圆形按钮开启为 440／405 ms，菜单为 490／450 ms。欢迎页沿实际「继续」按钮向全屏展开，开启动画为 420／400 ms。源与页面分别显隐，共用同一个不透明外壳。返回重新读取当前锚点，缺失时采用居中缩放和淡出。稳定后释放裁切、快照背景及临时绘制层。节奏参考 [Nexio 的卡片转场](https://github.com/HaoZai000/NexioSchedule/blob/28a99a3b23458a0d39741b9841c9755f5374fba2/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/effects/motion/MorphTransition.kt#L46-L78)与[启动页开合](https://github.com/HaoZai000/NexioSchedule/blob/28a99a3b23458a0d39741b9841c9755f5374fba2/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/effects/motion/OobeMotion.kt#L31-L69)。

首页编辑器、液滴、个性化、AI 会话、周视图交互和横屏页面切换仍由原功能管理。参数、播放及释放入口见[动效维护入口](architecture/MOTION_MAINTENANCE.md)。共享 Legacy 几何仍在 `LegacySharedObjectGeometry.kt` / `LegacyMenuDestinationGeometry.kt`，首页专属适配在 `feature/home/overlay/HomeLiquidMorphSpecs.kt`。

## 业务调用

业务使用统一入口，不直接引用 SDK：

```kotlin
ActivityTransitionCoordinator.open(
    activity = activity,
    routeId = TransitionRouteId.CourseManagementToDetail,
    intent = intent,
    payload = payload
)
```

目的 Activity 在 `super.onCreate` 前调用 `prepareDestinationBeforeOnCreate(this)`，之后安装窗口背景，并在 `CrossActivityTransitionHost` 中呈现原业务内容。关闭沿用 host 提供的 `requestClose`，在必要的保存/校验后执行。

Intent 承载 route/session 身份。源 View、Bitmap、返回解析器与临时资源保存在 `TransitionPayloadStore`。进程重建后如果找不到会话，直接显示目标页面并使用平台或 Legacy 返回。

课程管理按当前组身份和已附着布局重新解析返回位置。删除、改组、回收或裁切的卡片不作为可靠返回目标。Legacy host 也在关闭请求时刷新落点，不缓存第一次 Compose 时的位置。

已知实体表面优先显式传 `TransitionAnchorFrame.surfaceColor`，与实际绘制共用颜色函数并合成页面衬底；不要从卡片内部的课程色、文字或胶囊推断外壳底色。课程管理开场和动态返回共用这一契约。只有没有实体底色的来源才使用通用快照估色。

## 路线门控

当前 Kit 候选为 `course_management_to_detail`、`ai_history_to_detail`、`ai_progress_to_history`、`manual_import_to_history`、`home_dock_to_import`、`home_to_edu_import`、`home_to_course_management`、`quick_sheet_to_settings_detail`、`special_course_to_editor`、`feedback_to_editor` 与 `welcome_to_home`。快速设置保留真实按钮和动态返回位置，详细设置先保存再退出。特殊课程捕获点击卡片并在返回时重新解析位置。新增反馈使用真实加号 View，成功提交后刷新列表。欢迎页用真实 COUI 按钮启动主 Activity，开场结束后释放会话并结束欢迎页。

允许使用 SharedViewKit 的渠道也支持远程全局开关和逐路线白名单。以下配置开放课程管理到详情。

```json
{
  "transitions": {
    "sharedViewKitEnabled": true,
    "sharedViewKitRouteAllowlist": ["course_management_to_detail"]
  }
}
```

旧 Oplus 远程字段不再生效；远程解析器继续忽略未知字段，兼容旧 bootstrap/cache。配置不能把非候选路线升级为原生路线。

GitHub 渠道的 Debug 和签名 Release 默认启用已适配路线。用 `-Psleepdown.disableSharedViewKit=true` 构建时，所有路线的 Kit 入口都被关闭，优先于远程配置，用于实机对照降级动画。只设置 `-Psleepdown.sharedViewKitTest=false` 时仍可通过后台白名单启用。商店版固定关闭金标并排除 SDK。原生转场仍需通过设备能力、真实锚点、安全返回和 SDK options 检查。

## SDK 与绘制契约

- SDK 固定为已核对的 `gsai.sdk:sharedview-kit:1.0.0.4`；Nexus 仅解析 `gsai.sdk`。升级时重新核对官方发布、接口、产物校验和 Release。
- `isViewAnimationSupported() > 0` 才继续，不能将 Oplus 的旧版本阈值移植到标准 Kit。
- 使用截图的 Compose 路线把局部像素投影到会话持有的独立源 View，按完整窗口坐标挂到 decor overlay，并用像素圆角提供 Outline。软件采样保留透明圆角。系统接管后隐藏源 View，会话结束时移除，返回桥接采用相同圆角。源尺寸和像素不匹配、越出窗口或未挂载时走 Legacy。
- 加号、导入历史、快速设置的详细设置和特殊课程入口把持续挂载并绘制原控件的 View 传给 Kit。系统从该 View 取开场位置及像素圆角。触摸按下时先取该 View 在窗口中的静止画面，再交给按钮处理按压。开合的软件绘制沿用这个画面，条件不满足时按实际可用的 View 画面处理。整页 Legacy 画面在原生路径不可用或被拒绝后才准备。
- 按钮的影子与外扩高光跟随 SDK 对原 View 的显隐，目的页在原生注册时直接绘制稳定内容。已合成的材质取实际按钮底色并走系统实色转场，确实透底的源才在支持时声明 `EXTRA_VIEW_WITH_ALPHA`。返回读取仍附着的源 View，被目的窗口盖住也可以成为落点。未移动的按钮沿用开场注册，移动且尺寸一致时按支持项刷新位置。按钮被隐藏、回收或尺寸改变时使用声明支持的返回降级。
- 标准 metadata 明确 WINDOW 时使用项目窗口锚点；SCREEN/缺省时增加同一根 View 的屏幕/窗口位置差。打开与关闭共用转换，不硬编码状态栏高度。
- 双向路线要求设备声明可写的安全返回策略。单向欢迎页在开场结束时释放会话。BACK/FALLBACK 的支持集合分别解析，未声明的扩展参数不传入。
- 源卡片保留至 OPEN 接管；稳定目的内容由系统转场使用，不能再叠加 Legacy Morph、应用背景缩放或第二层模糊。
- 需要刷新位置或指定返回降级时，在退出的目标 Activity 调用 `overrideViewAnimationTransition`，之后由原 host 执行 `finish`。目标丢失使用声明的普通/淡出/无动画返回，避免向旧位置收缩。
- Kit 未公开自定义时长、曲线或逐帧进度；它不接管项目全部 Compose 动效，也不等于所有设备已经支持。

## 生命周期与回退

SDK 回调进入主线程，以既有 generation 和资源身份过滤旧事件。一个会话的延迟 OPEN 事件不能重新打开正在关闭的目的页。全局 `cancelCurrentViewAnimation` 仅由当前运动的所属会话调用，旧源 Activity 销毁不能取消另一条路线。

打开/返回各有一次 3 秒回调 watchdog，正常完成即移除。未启动前拒绝交给原 Legacy；目的页已经启动后取消或缺少 OPEN 回调，只在该目的 host 切回 Legacy，不能再次 startActivity。BACK 结束、取消或 watchdog 完成后幂等清理，Activity 销毁亦走同一资源入口，不依赖早到的 onResume 猜测结束。

BACK 结束时先恢复业务源，再由来源窗口的下一次 pre-draw 撤下短暂桥接。桥接不与已恢复的半透明源控件重叠额外帧；窗口分离或后台未重绘会有界释放。桥接副本不在可能仍被 RenderNode 使用时手动 recycle，Kit 自己的资源仍按原会话清理。

横屏沿用既有局部切页策略，协调器不保留源锚点/背景快照；首批 Kit 排除多窗/PiP。此为项目试接范围，不是联盟 SDK 的全部能力声明。原表单、Popup 与保存语义维持自己的入口。

## 验证与发布边界

`SharedViewKitBackendTest` 覆盖不支持、空 options、晚回调、快速返回、动态/删除落点、打开/关闭超时、会话隔离、坐标差及旧组件 alias。`SharedViewKitCapabilitiesTest` 覆盖 metadata。`Transition*Test` 继续保护路由、状态及载荷，CI 同时执行新握手回归。资源、Manifest 和依赖变更按 [AGENTS.md](../AGENTS.md) 完整打包，核对 R8 后的公开 API、delegate/回调、SDK 渠道隔离与旧私有 SDK 移除。

SDK POM 标明 ITGSA 专有授权；Demo MIT 不替代 SDK 协议。GitHub 产物包含 SDK，构建开关不改变许可要求，发布前仍需确认使用资格。当前官方 Nexus 是 HTTP，固定版本并记录已核验产物，不能用动态 `+` 作为正式候选。

按钮接入按 [官方 SharedViewDemo 文档](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo/blob/b449a426a340f1827d942592bddf0a416eae3ac4/README.md) 的 View 源、坐标系、透明参数和返回刷新契约实现。首次调查见[接入调查](reports/2026-10-21-sharedviewkit-integration-audit.md)。扩大路线前先验证真实开合、取消、窗口、主题与数据保存。
