# SleepDown 跨 Activity 转场框架

## 职责与入口

业务入口声明目标、源截图及可重新解析的返回锚点，由 `ActivityTransitionCoordinator` 选择 SharedViewKit 或该路线原有 Legacy。路由、载荷和会话只保留一套：

| 入口 | 职责 |
| --- | --- |
| `transition/TransitionRoute.kt` | 稳定 wireName、Legacy 效果、允许的原生候选与真实目的 Activity |
| `transition/TransitionSession.kt` | 状态边、callback generation 与父子会话 |
| `transition/TransitionPayload.kt` | 进程内 Bitmap、锚点解析、源交接与幂等释放 |
| `transition/ActivityTransitionCoordinator.kt` | 导航、窗口装配及后端分派 |
| `transition/CrossActivityTransitionHost.kt` | 原生稳定内容与 Legacy renderer 的仲裁、原有保存退出握手 |
| `transition/SharedViewKitBackend.kt` | Kit 握手、回调、返回更新、坐标转换和有界超时清理 |
| `transition/SharedViewKitCapabilities.kt` | 标准 metadata 的坐标与返回类型解析 |
| `transition/SharedViewKitApi.kt` | 唯一 SDK 适配契约，用于渠道隔离和握手测试 |
| `src/github/.../transition/PlatformSharedViewKitApi.kt` | 官方公开 SDK 的直接类型调用 |
| `src/store/.../transition/PlatformSharedViewKitApi.kt` | 不含 SDK 的不可用实现 |
| `transition/LegacyTransitionBackend.kt`、`transition/legacy/` | 既有应用内轨迹、快照和绘制 |

原 Oplus 私有 ViewSeamless 后端、反射/回调适配、版本门槛、源 View 桥、leash 参数、调试入口及旧 keep 规则已删除。系统原生路径统一查询金标能力，不按厂商品牌分叉。四个旧 Oplus Activity 名在 Manifest 中仅作为不导出的恢复 alias，指向实际页面；不包含旧类或旧动画。候选路线与真实 opaque 目的页以 `TransitionRouteCatalog` 为准，不使用 alias 改变窗口分类。特殊课程 `special_course_to_editor` 使用 `feature.settings.NativeSpecialCourseEditorActivity`，不具备金标能力时进入普通 `SpecialCourseEditorActivity`，按设置页资源滑入／滑出；该路线的 Legacy profile 为资源动画，其他成熟锚点路线保持原 Morph。

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

Intent 只承载 route/session 身份；Bitmap、返回解析器与临时资源保存在 `TransitionPayloadStore`。进程重建找不到会话时直接显示实际页面，沿用平台/Legacy 返回，不重建已经失效的系统动画。

课程管理按当前组身份和已附着布局重新解析返回位置。删除、改组、回收或裁切的卡片不作为可靠返回目标。Legacy host 也在关闭请求时刷新落点，不缓存第一次 Compose 时的位置。

已知实体表面优先显式传 `TransitionAnchorFrame.surfaceColor`，与实际绘制共用颜色函数并合成页面衬底；不要从卡片内部的课程色、文字或胶囊推断外壳底色。课程管理开场和动态返回共用这一契约。只有没有实体底色的来源才使用通用快照估色。

## 路线门控

当前 Kit 候选为 `course_management_to_detail`、`ai_history_to_detail`、`ai_progress_to_history`、`manual_import_to_history`、`home_to_edu_import`、`home_to_course_management`、`quick_sheet_to_settings_detail` 与 `special_course_to_editor`。快速设置保留真实按钮及动态返回位置，详细设置仍先保存、后退出。特殊课程只捕获点击卡片，返回重新解析实时位置，源被删除则使用 SDK 安全返回类型；编辑页保留保存／放弃确认。GitHub 版具备 SDK，商店版不依赖/打包 SDK。普通发布默认使用 Legacy；全局开关与逐路线白名单都满足才尝试 Kit，例如只开放首条：

```json
{
  "transitions": {
    "sharedViewKitEnabled": true,
    "sharedViewKitRouteAllowlist": ["course_management_to_detail"]
  }
}
```

旧 Oplus 远程字段不再生效；远程解析器继续忽略未知字段，兼容旧 bootstrap/cache。配置不能把非候选路线升级为原生路线。

GitHub 渠道的 Debug 和签名 Release 默认启用已适配路线的金标动画，不依赖后台配置；对照诊断可显式使用 `-Psleepdown.sharedViewKitTest=false`。商店版的金标开关固定关闭，继续受 `SLEEPDOWN_EXPERIMENTAL_FEATURES=false` 排除，不打包 SDK，不增加产品设置项。签名 Release 用于相同包名/签名的实机验证，日志保留系统协议查询与回退原因。开启构建开关不等于 ROM 已接受：仍需能力、真实锚点、安全返回和 SDK options 均通过。其他路线必须先完成自身截图、源交接和真实返回验证，再调整路由候选及白名单。

## SDK 与绘制契约

- SDK 固定为已核对的 `gsai.sdk:sharedview-kit:1.0.0.4`；Nexus 仅解析 `gsai.sdk`。升级时重新核对官方发布、接口、产物校验和 Release。
- `isViewAnimationSupported() > 0` 才继续，不能将 Oplus 的旧版本阈值移植到标准 Kit。
- Compose 路线使用已有局部 Bitmap 和 px 圆角，不伪造 AndroidView，不传整个 ComposeView。
- 标准 metadata 明确 WINDOW 时使用项目窗口锚点；SCREEN/缺省时增加同一根 View 的屏幕/窗口位置差。打开与关闭共用转换，不硬编码状态栏高度。
- 首批要求设备声明可写的安全返回策略；BACK/FALLBACK 的支持集合分别解析。未声明的扩展参数不传入。
- 源卡片保留至 OPEN 接管；稳定目的内容由系统转场使用，不能再叠加 Legacy Morph、应用背景缩放或第二层模糊。
- 返回前在退出的目标 Activity 调用 `overrideViewAnimationTransition`，之后由原 host 执行 `finish`。目标丢失使用声明的普通/淡出/无动画返回，避免向旧位置收缩。
- Kit 未公开自定义时长、曲线或逐帧进度；它不接管项目全部 Compose 动效，也不等于所有设备已经支持。

## 生命周期与回退

SDK 回调进入主线程，以既有 generation 和资源身份过滤旧事件。一个会话的延迟 OPEN 事件不能重新打开正在关闭的目的页。全局 `cancelCurrentViewAnimation` 仅由当前运动的所属会话调用，旧源 Activity 销毁不能取消另一条路线。

打开/返回各有一次 3 秒回调 watchdog，正常完成即移除。未启动前拒绝交给原 Legacy；目的页已经启动后取消或缺少 OPEN 回调，只在该目的 host 切回 Legacy，不能再次 startActivity。BACK 结束、取消或 watchdog 完成后幂等清理，Activity 销毁亦走同一资源入口，不依赖早到的 onResume 猜测结束。

BACK 结束时先恢复业务源，再由来源窗口的下一次 pre-draw 撤下短暂桥接。桥接不与已恢复的半透明源控件重叠额外帧；窗口分离或后台未重绘会有界释放。桥接副本不在可能仍被 RenderNode 使用时手动 recycle，Kit 自己的资源仍按原会话清理。

横屏沿用既有局部切页策略，协调器不保留源锚点/背景快照；首批 Kit 排除多窗/PiP。此为项目试接范围，不是联盟 SDK 的全部能力声明。原表单、Popup 与保存语义维持自己的入口。

## 验证与发布边界

`SharedViewKitBackendTest` 覆盖不支持、空 options、晚回调、快速返回、动态/删除落点、打开/关闭超时、会话隔离、坐标差及旧组件 alias。`SharedViewKitCapabilitiesTest` 覆盖 metadata。`Transition*Test` 继续保护路由、状态及载荷，CI 同时执行新握手回归。资源、Manifest 和依赖变更按 [AGENTS.md](../AGENTS.md) 完整打包，核对 R8 后的公开 API、delegate/回调、SDK 渠道隔离与旧私有 SDK 移除。

SDK POM 标明 ITGSA 专有授权；Demo MIT 不替代 SDK 协议。GitHub 产物包含 SDK，构建开关不改变许可要求，发布前仍需确认使用资格。当前官方 Nexus 是 HTTP，固定版本并记录已核验产物，不能用动态 `+` 作为正式候选。

首次调查与官方链接见[接入调查](reports/2026-10-21-sharedviewkit-integration-audit.md)。构建/回归不等于目标设备支持、实机视觉或金标认证通过；扩大路线前先验证真实开合、取消、窗口、主题与数据保存。
