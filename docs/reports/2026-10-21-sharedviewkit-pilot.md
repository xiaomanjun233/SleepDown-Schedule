# 金标 SharedViewKit 首批接入与 Oplus 退役

日期：2026-10-21。沿用 #79，用户在调查后要求逐步接入，并明确“oplus 残留动画直接去除，死代码也是，oplus 兼容金标”。本批删除旧私有动画实现，保留成熟 Legacy 与功能行为，不调整应用内曲线/时长。

## 实际变化

- 删除 `OplusSeamlessBackend`、能力策略、反射回调工厂、Debug 探针及退役测试，移除旧 `compileOnly` SDK 与 keep 规则。四个旧 Activity 类删除，Manifest 以未导出 alias 保留原 FQCN 的系统恢复入口。ColorOS 材质、控制和课程通知继续使用原实现。
- 现有 Coordinator 只装配 SharedViewKit 与 Legacy 两个后端，不新增全局动画框架。SDK 固定 `1.0.0.4`；直接类型调用仅在 github source set，store 提供无 SDK 实现。解析仓库仅服务 `gsai.sdk`。
- 首批仅课程管理卡片→详情：真实局部 Bitmap、px 圆角、动态返回锚点。能力查询、逐路线门控、默认关闭；Debug 可用 `-Psleepdown.sharedViewKitTest=true`。其他路线沿用 Legacy，不批量接入系统动画。
- metadata 负责 WINDOW/SCREEN 坐标与返回策略能力。返回时重新检查课程、卡片是否仍挂载且可见；丢失落点用设备声明的安全返回。未启动即拒绝时启动原 Legacy；已启动后超时只在同一目的 host 切回 Legacy，避免重复 Activity。
- OPEN/BACK 回调均按 generation 和资源身份过滤；迟到 OPEN 不干扰已经关闭/稳定的会话。3 秒 watchdog 有界收口，结束/取消/销毁共用既有 PayloadStore 释放。只有当前运动所属会话可调用 SDK 的全局 cancel。

## 实际验证

- `compileGithubReleaseKotlin` 与相关 `testGithubDebugUnitTest`：112 项通过，0 失败/错误/跳过，4m43s。覆盖能力元数据、握手/超时/重复与迟到回调、会话隔离、坐标、动态/丢失落点、四个 alias、旧远程缓存字段、路由/载荷及原几何。初次回归因 Robolectric 误启动正式 Application 失败，夹具明确指定普通 Application 后重跑通过，未修改生产初始化。
- `assembleGithubRelease compileStoreReleaseKotlin`：完整成功，6m11s，167 个任务，保留 R8、资源压缩、lintVital、打包和签名。APK 身份仍为 `com.xiaomanjun.sleepdownschedule`、`1.2.7.1` / 35，签名校验成功；未安装、发布。该验证产物 SHA-256 为 `421dc14b6361ced39cf9af16eb378f3a85f7320a92c94d9fb333e66ebfba7f83`。
- R8 mapping 中标准 SDK 公开 API、delegate、回调及新宿主保留，旧 `com.oplus.animation` 不存在；合并 Manifest 保留四个 alias。商店 `storeReleaseRuntimeClasspath` 无标准 SDK 与旧私有 SDK，渠道编译通过。
- Gradle 实际解析的 AAR SHA-256 为 `977c98709bbc93521142ff0dae6b589d5494656c5e383e3fd2fa95513011a56a`，与官方 Demo/Maven 调查一致。
- 架构检查仍为 561 个生产文件、0 违规文件依赖、44 条已登记声明循环；没有新增历史豁免。检查器 13 项回归通过，diff 空白检查通过。

## 边界与后续

测试使用 fake SDK/Robolectric，不代表真实 ROM、视觉、流畅度或金标认证已验收。扩大 allowlist 前，需在支持设备核对打开、返回、取消、切后台、配置变化、删除课程、卡片回收与数据保存；本轮未执行这些实机场景。SDK 未公开应用原曲线的参数，不把本次结构整理作为性能提升证据。

SDK POM 为 ITGSA 专有授权，Demo MIT 不能替代 SDK 许可；正式分发前需确认使用资格。官方 Nexus 仍为 HTTP；固定版本和实测 hash 不等于已经配置构建级校验。未复制私有框架或提供已发布 APK。首次官方调查保留于[接入调查](2026-10-21-sharedviewkit-integration-audit.md)，当前维护入口见[转场框架](../TRANSITION_FRAMEWORK.md)。
