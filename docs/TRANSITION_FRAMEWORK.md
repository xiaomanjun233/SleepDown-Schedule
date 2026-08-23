# SleepDown 跨 Activity 转场框架

## 目标

跨 Activity 的业务入口只声明“去哪里”和“锚点是什么”，由统一协调器选择既有 Legacy 动画或可选的系统原生转场。现有 Morph 的几何、时序、缓动、玻璃、Backdrop、模糊与首尾帧参数不在框架内重新定义。

同 Activity 的首页弹层、历史详情、课程编辑器、周视图拖拽和 `CustomizeUiState` 仍由原状态机管理。

## 主要入口

- 路线表：`transition/TransitionRoute.kt`
- 会话与状态机：`transition/TransitionSession.kt`
- 会话载荷：`transition/TransitionPayload.kt`
- 统一协调器：`transition/ActivityTransitionCoordinator.kt`
- 目的页容器：`transition/CrossActivityTransitionHost.kt`
- Legacy 后端：`transition/LegacyTransitionBackend.kt`
- Oplus 后端：`transition/OplusSeamlessBackend.kt`
- 厂商回调隔离层：`transition/OplusVendorCallbackFactory.kt`

## 业务接入

打开页面时，先创建可选的 `TransitionPayload`，再调用统一入口：

```kotlin
ActivityTransitionCoordinator.open(
    activity = activity,
    routeId = TransitionRouteId.CourseManagementToDetail,
    targetIntent = intent,
    payload = payload,
)
```

目的 Activity 在 `super.onCreate` 前调用：

```kotlin
ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this, intent)
```

页面内容由以下容器承载：

```kotlin
CrossActivityTransitionHost(activity = this, intent = intent) {
    DestinationContent()
}
```

关闭页面统一调用：

```kotlin
ActivityTransitionCoordinator.requestClose(this, intent)
```

业务代码不得直接选择 `AnchoredDetailActivityMorph`、深度动画 helper 或 Oplus API。

## 会话约束

- Intent 只传 `routeId`、`sessionId` 和可选 `parentSessionId`。
- Bitmap、bounds、返回锚点解析器和临时 View 只存放在进程内的 `TransitionPayloadStore`。
- 第一批详情路线使用 manifest 中真实声明的 opaque Activity；同一正式页面由 `values` / `values-night` 的同名主题自动匹配明暗模式，Legacy 仍进入原透明 Activity。
- 每次跳转拥有独立状态机和 callback generation；迟到的厂商回调不会影响其他会话。
- 进程重建找不到 payload 时，目的页直接显示稳定真实内容，返回使用平台行为。
- Home → 课程管理允许打开锚点为一级菜单外壳、返回锚点为真实三点按钮。

## Oplus 启用策略

生产环境默认全部使用 Legacy。只有全局开关和逐路线 allowlist 同时开启，且运行时能力、窗口形态、源锚点、软件快照和 opaque 目标窗口均通过检查时，才尝试 ViewSeamless。

远程配置示例：

```json
{
  "transitions": {
    "oplusViewSeamlessEnabled": true,
    "oplusRouteAllowlist": ["course_management_to_detail"]
  }
}
```

任何配置缺失、注册拒绝、异常、超时或无效返回锚点都会回落到该路线原有 Legacy 动画。路线只有在正式页面完成打开、返回、残留和连续往返真机验收后才能加入 allowlist。

## Debug 二分入口

Oplus 渲染层二分代码只存在于 `app/src/debug`。安装 Debug 包后可启动：

```powershell
adb shell am start -n com.xiaomanjun.sleepdownschedule.debug/com.xiaomanjun.sleepdownschedule.OplusTransitionDebugSourceActivity
```

该入口依次覆盖空 Compose、普通 Compose、`graphicsLayer`、`RenderEffect` 和 Morph 容器；不会进入 Release manifest，也不使用生产远程开关。
