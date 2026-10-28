# 2026-10-28 金标源 View 与圆角

用户要求独立建立金标动画优化 PR，重点处理系统采样缺失圆角与裁切，并正确传递 View 源。分支 `codex/gold-source-corners` 基于已收束的 main `e72643f2`；与时间修复分支独立。

## 实现

所有既有 SharedViewKit 路线经同一 SDK 边界使用官方 `makeViewAnimationOptions(View, Bundle, listener)`。Compose 的已捕获局部像素投影到会话持有的单个 Android View，放在源窗口 decor overlay 的完整 bounds；它不是整页 ComposeView，也不向系统传一个被 Lazy 列表祖先裁切的子树。

源 View 提供 px 圆角 Outline、clipToOutline 及软件采样圆角路径。按根 View 的窗口坐标偏移布局，不缩放被裁掉的像素来冒充完整来源；像素尺寸不符、越出窗口、未 attach 时拒绝原生注册，复用现有降级。OPEN 接管后隐藏源投影，保持 attach 到会话结束；注册失败、取消、超时、Activity 销毁及正常返回统一释放。

返回仍使用当前业务锚点、坐标域、真实表面色和受能力门控的返回像素；开合桥接快照也按锚点圆角绘制，避免最后一帧回到矩形边角。原 payload 像素继续由原有链路持有，拒绝原生注册时不会破坏 Legacy 所需来源。

官方接口与圆角行为核对来源：本地官方示例审计副本及固定版本 [README](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo/blob/4ed95e70877e019a286f4c9bb041732582bf406f/README.md)，以及项目实际依赖的 `sharedview-kit:1.0.0.4` 公开接口。没有变更依赖版本或增加反射后端。

## 验证

`e4f337ef` 的 GitHub 与商店 Release Kotlin 编译及三类定向回归均通过，7m23s：SharedViewKitBackendTest 19、SharedViewKitCapabilitiesTest 3、ReturnCoverHandoffTest 4，共 26 项，0 失败/错误。握手测试新增检查完整源尺寸、窗口位置、Outline、接管后隐藏与最终释放，同时保留拒绝注册、过期回调、动态/删除返回落点等既有覆盖。架构检查 600 个生产文件、0 违规文件依赖，diff --check 通过。

命令使用 `compileGithubReleaseKotlin compileStoreReleaseKotlin testGithubDebugUnitTest`，过滤上述三类，单 worker、3GB 堆；最后仅更新文档记录，不重复未变化代码的验证。

本轮没有安装、发布或设备视觉验收；PR 保持 Draft，不能把 Kotlin/单元测试通过写成 ROM 动画圆角观感已验收。版本、Room、路线 wire id、能力门控及商店无 SDK 边界保持原样。

## 2026-10-29 实机反馈后续修正

用户反馈 View 源仍未正确裁圆角。`e51ff0df` 在 View 握手 Bundle 中显式提供 `sharedView.radius`，与本地官方 HomePresenter 示例的 `ViewAnimationConfig.setRadius(px)` 一致；同时先把开场、返回和交接用软件位图本身裁成透明圆角，避免采样器忽略 View Outline 时仍读到矩形像素。原 payload 保持原样，资源生命周期不变。

Release Kotlin 编译、原生图形像素及会话定向回归通过，27 项、0 失败/错误/跳过，1m32s。覆盖源位图四角透明、边缘和中心像素保留、原图不被改写，以及 View attach/Outline、显式半径、返回图生命周期和失败释放。旧握手测试也改用 Robolectric 原生图形执行，避免其默认模拟 Canvas 不绘制导致错误的全透明结果。已同步 main 的收束文档。联合构建和设备安装记录由 #88 分支维护，实际 ROM 动画观感仍须实机确认。
