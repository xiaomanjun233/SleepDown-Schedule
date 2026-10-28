# 2026-10-23 金标候选扩展到首页课程管理

接续 `codex/sharedviewkit-ai-history` / `f0d0f8ba` 与全部未提交改动。用户要求继续接手 Codex 的工作、按文档继续接入金标无缝动画，并说明此前提出的锚点外观问题已解决。本轮不再追查锚点外观，按[转场框架](../TRANSITION_FRAMEWORK.md)的路线顺序补下一条候选。

## 选择该路线的依据

- [接入调查](2026-10-21-sharedviewkit-integration-audit.md) 把“首页菜单 → 课程管理”列为 P2；同批的“首页菜单 → 教务选择”已在上一次接入（`home_to_edu_import`），课程管理是这条链路上剩下的同类入口。
- 两条路线共用同一段首页启动代码：整体菜单快照作为开场源、Dock/加号真实按钮作为返回锚点、`HomeMenuActivitySourceFallback` 作为原生接管前的稳定内容。教务路线已在同一形状上验证过会话、源交接与返回，因此这里只补齐目的窗口与路由声明，不改动画参数。
- “多课表快速设置 → 详细设置”（P3）仍与保存草稿握手、透明窗口和弹层层级强耦合，本轮不接。

## 实际变化

- 新增 `NativeCourseManagementActivity`：直接继承 `CourseManagementActivity`，只作为真实不透明宿主；业务内容、返回握手、课程卡片 → 详情路线都不复制。
- Manifest 声明该 Activity 使用 `AppTheme.NativeDetail`，`parentActivityName` 为首页；原 `CourseManagementActivity` 与四个 Oplus 恢复 alias 不动，已发布组件的 FQCN 与窗口分类保持原样。
- `home_to_course_management` 加入 `SharedViewKitAllowlisted`，`nativeDestinationClassName` 指向新宿主。开场源、返回锚点、`requiresOpeningAnchor` / `requiresReturnAnchor` 沿用原值，Legacy 的 `HomeMenuDestination` 轨迹、时长和圆角不变。
- 门控不变：本地 `-Psleepdown.sharedViewKitTest=true` 才启用全部候选；正式包仍需远程 `sharedViewKitEnabled` 与逐路线白名单同时满足；商店版不打包 SDK；横屏/多窗继续走 Legacy，平板横屏仍使用 `TabletHomeToCourseManagement` 的页面切换。
- 测试同步：候选集合、真实不透明宿主映射、本地试点集合三处断言加入新路线。

## 验证与交付

- 定向回归 3 类 24 项通过，0 失败、错误或跳过（6m27s）：`TransitionRouteCatalogTest` 5、`TransitionFeaturePolicyTest` 4、`SharedViewKitBackendTest` 15。覆盖路由表唯一性、候选集合、宿主映射、Legacy 单路线门控、握手/超时/迟到回调/会话隔离与坐标转换。
- 完整 `assembleGithubRelease "-Psleepdown.sharedViewKitTest=true"`（`--no-parallel --max-workers=2 --no-daemon`）7m59s 成功，159 项中 25 执行、134 复用，含 Kotlin、R8、资源压缩、lintVital 和正式签名。生成的 `BuildConfig` 中 `SLEEPDOWN_EXPERIMENTAL_FEATURES` 与 `SLEEPDOWN_SHARED_VIEW_KIT_TEST` 均为 true。
- 候选 `tmp/review-apks/SleepDown-home-course-management-gold-20261023.apk`（主目录），7,607,847 字节，SHA-256 `33343e2e9665eab6eda7210720beb7e5acc88e33a3d4a5a67b528131d8062a06`，身份 `com.xiaomanjun.sleepdownschedule`、`1.2.7.1` / 35。v2 签名通过，证书 SHA-256 仍为 `0f2b50df…`；打包 Manifest 含 `.NativeCourseManagementActivity`（未导出、`AppTheme.NativeDetail`），`classes.dex` 可检索到该类。

## 边界

- 测试使用 fake SDK 与 Robolectric，不代表 ROM 已接受该路线。首页菜单展开 → 课程管理的实际开场、返回、快速连按、返回后菜单恢复与壁纸/主题表现仍待真机验收。
- 该路线的返回锚点是启动时采集的静态按钮位置；竖屏下首页布局在目的页期间不变，未做“重新解析按钮当前位置”。若后续出现返回落点偏移，应先核对是否需要按会话重新解析，而不是改圆角或时长。
- 本轮没有安装、推送、PR、合并或发布；`HomeToCourseManagement` 之外的其他路线维持原有状态。
