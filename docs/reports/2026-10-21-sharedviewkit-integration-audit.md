# 2026-10-21 金标联盟 SharedViewKit 接入调查

## 结论与范围

优先验证“课程管理卡片 → 课程详情”，其次“AI 历史记录 → 详情”。它们与官方示例的卡片到独立 Activity 模型接近，且已有截图、锚点、路由和会话。通过现有 `transition` 增加一个 SharedViewKit 后端即可试接，不需要新模块或另一套动画状态机。

首页内部课程编辑、个性化、三点/加号液滴、AI 会话、页面切换及周视图操作不适合直接换成此 Kit：公开入口围绕 `ActivityOptions`、`startActivity` 和 `finish`，未提供独立 Composable/弹层播放接口、自定义 easing/时长或逐帧进度。系统转场的节奏归 ROM，项目仍维护自己的 Compose 动效。

本轮仅调查和记录，没有接入 SDK、修改应用代码/依赖/Manifest、启用原 Oplus 实验或执行安装。报告及交接暂存本地工作树，尚未提交/推送，不触发上一轮 PR 的新 CI。

## 核对的事实来源

- 日期：2026-10-21，Asia/Shanghai。
- SleepDown：开放的 [PR #79](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/79)，head `bc69a3efe0344af466957267feffff9b3ae5dbe5`；main `9b6acbb2`。本轮在该独立工作树调查，主目录学校适配及其文档不动。
- 官方示例：[SharedViewDemo](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo)，`master` 提交 `4ed95e70877e019a286f4c9bb041732582bf406f`，提交时间 2026-10-19 18:11:22 +08:00。网页读取工具不可访问，已通过 Git 获取并直接检查 README、示例源码、构建与许可。
- [官方 README 固定版本](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo/blob/4ed95e70877e019a286f4c9bb041732582bf406f/README.md)：运行能力、两种源输入、返回更新、参数类型与坐标语义。
- [官方 Maven 元数据](http://nexus.itgsa.com:5566/repository/sharedViewKit/gsai/sdk/sharedview-kit/maven-metadata.xml)：本次实际读取的 latest/release 均为 `1.0.0.4`。不把这个调查值视为未来始终最新。
- 官方 Maven AAR 与 Demo `app/libs/sharedview-kit-1.0.0.4.aar` 字节一致，11,121 字节，SHA-256 `977c98709bbc93521142ff0dae6b589d5494656c5e383e3fd2fa95513011a56a`。只解包读取 Manifest、consumer rules，并用 `javap` 核对公开接口，未构建/运行示例。

## SDK 能提供什么

| 能力 | 已核对的契约 | 对 SleepDown 的意义 |
| --- | --- | --- |
| 设备支持 | `isViewAnimationSupported() > 0`；0 不支持 | 按运行时协议判断，不按品牌、Android 版本或原 Oplus 的 37000 阈值判断 |
| View 源 | `makeViewAnimationOptions(View, Bundle, listener)` | 适合真正独立、已 attach、可定位的 Android View；不能把整个 ComposeView 当成一张课程卡片 |
| Bitmap 源 | `makeViewAnimationOptions(Bitmap, Rect, float, Bundle, listener)` | 最适合当前 Compose 卡片已有的局部截图；bounds 和圆角显式传入，Bitmap 不能提前回收 |
| 返回刷新 | `overrideViewAnimationTransition(destinationActivity, bundle)` | 在退出目标 Activity 的 `onCreate` 后、`finish` 前刷新真实落点；不是在源 Activity 调用 |
| 扩展效果 | `getViewAnimationExtras()` 声明各 key 的支持和语义 | 模糊、景深、背景缩放等按声明传入，不能无条件叠加现有应用模糊 |
| 生命周期 | listener 只有开始、取消、结束，参数区分 OPEN/BACK | 不能用于同步每帧玻璃形变，也不能从回调得知实际采用的动画类型 |
| 动画配置边界 | Config 公开方法仅有 color/radius/bounds/backAnimType；另有声明的 extras | 没有公开 duration/easing/进度接口，不能拿它集中调整现有 Compose 曲线 |
| 最低版本及权限 | AAR Manifest 为 minSdk 26，没有 `uses-permission` | 与项目 minSdk 26 匹配，不需要照抄 Demo 的 minSdk 34；没有新增授权弹窗入口 |

运行时仍依赖 ROM 的 SharedView 协议实现；引入 11 KB 的客户端 Kit 不会让不支持的设备获得这套系统动画。只核对了接口及产物，没有实机调用能力查询，不能给出已支持设备清单或认证结论。

## 哪些路线可以接入

以下“可接入”是基于代码结构的工程判断，尚未经过系统动画实测。

| 优先级 | 场景 / 路由 | 现有基础 | 接入前需补齐 |
| --- | --- | --- | --- |
| P1 首个验证 | 课程管理卡片 → 课程详情，`CourseManagementToDetail` | `CourseManagementActivity.kt` 已传局部 Bitmap、20dp 换算圆角、窗口 bounds、源交接/释放回调 | 返回按课程/组稳定身份重新找当前落点；卡片删除、改组、滚出视口时降级；验证源隐藏和系统接管时序 |
| P1 第二条 | AI 历史记录 → 详情，`AiHistoryToDetail` | `AiImportHistoryUi.kt` 已传行截图、18dp 圆角和 Activity 路由 | 当前返回为静态锚点，改为按 entry ID 重新解析；目标当前透明，需独立验证普通 opaque 宿主的视觉及返回 |
| P2 | 手动导入/进度 → 历史，`ManualImportToHistory` / `AiProgressToHistory` | 有按钮快照、历史 Activity 和源保留回调 | 来源可能为局部弹层；原 Oplus 路线有白帧调查记录，不能照搬其隐藏源/占位 View 逻辑视为已解决 |
| P2 | 首页菜单 → 课程管理/教务选择，`HomeToCourseManagement` / `HomeToEduImport` | 有菜单/按钮开场及返回两套锚点、真实 Activity 目的页 | 处理菜单收回、源释放、按钮当前位置与不同开/关锚点；原路线 nativeClosePolicy 为 LegacyOnly，不能默认新 Kit 关闭已适用 |
| P3 | 多课表快速设置 → 详细设置，`QuickSheetToSettingsDetail` | 有真实按钮锚点、专用透明宿主及保存退出握手 | 透明窗口、弹层层级、背景快照与保存语义耦合较多，首批不改 |
| 暂不接入 | 首页今日/周视图/设置切换、横屏侧栏切页 | 同 Activity 的 Compose 目标切换 | Kit 没有对应的局部切页 API；保留 HomeSwitchMotion / PaneTransitions |
| 暂不接入 | 课程卡片编辑、个性化、液滴菜单、AI 会话、Dialog/Popup/QuickSheet、周视图翻页/移动/缩放 | 同 Activity 状态机与局部玻璃/几何 | 保留已有 Morph 与公共设计系统，不为使用 Kit 改造成 Activity |
| 首批排除 | 普通无锚点设置跳转、Widget/通知外部唤起、横屏/多窗/PiP | 部分没有应用内源卡片；横屏现有逻辑主动清除锚点并使用局部页面 | 不制造假锚点；不把项目当前排除策略描述成 SDK 明确不支持横屏/多窗 |

源码入口均相对 `app/src/main/java/com/xiaomanjun/sleepdownschedule/`。课程管理卡片、历史条目、首页菜单分别由自己的功能提供源事实，Kit 不读取 Repository 或业务页面状态。

## 如何接入既有架构

1. **依赖与能力入口**：确认 SDK 使用资格后，按 `implementation` 引入 Kit，保留现有转场门控并增加独立的 SharedViewKit 路线白名单。官方 README 推荐动态最新版本，实际实施时先核对最新产物，再固定已验收版本和校验值，保持构建可复现。依赖仓库仅匹配 `gsai.sdk`，不将它作为所有依赖的兜底仓库。
2. **复用后端协议**：新增一个 `SharedViewKitBackend : TransitionBackend`，在 `ActivityTransitionCoordinator` 里选择“本次允许且支持的 Kit → 当前 Legacy”。保留现有 route wireName、`TransitionPayloadStore`、`TransitionSession` 和 generation。Oplus 私有后端继续保持关闭，不把新 Kit 的接入等同于恢复它，也不套用 Oplus 版本/私有 Bundle 判据。
3. **记录真实后端归属**：当前 `requestNativeClose()` 直接调 Oplus；需要让同一 session 的打开、关闭、超时、取消、Activity 销毁分派到实际选择的后端。`CrossActivityTransitionHost` 的原生阶段展示稳定目的内容，避免叠加 Legacy Morph。先沿用已有会话状态，验证 Kit 回调时序与注册状态衔接，不创建平行动画控制器。
4. **Compose 使用 Bitmap 输入**：复用现有局部截图、有效 bounds、圆角和源释放回调；首批不增加伪造 AndroidView。源内容保留到系统确认接管，Bitmap/占位资源按会话释放。Kit 返回 null/不支持时才走原 Legacy；已经启动目标 Activity 后发生取消不能再次启动目标页。
5. **关闭更新**：在目标页已有保存/返回握手之后，重新解析业务身份对应的当前源卡片，按 Kit 坐标声明构建返回 Bundle，调用 `overrideViewAnimationTransition(this, bundle)` 后 `finish()`。源不存在或不可见时，只使用该设备声明支持的跟随系统/淡出/中心收缩策略；没有声明对应 key 时不假定可以强制该效果。

## 必须处理的适配差异

- **坐标**：当前 `TransitionAnchorFrame` 记录 `boundsInWindow`。Kit `EXTRA_BOUNDS` metadata 明确 WINDOW 时直接使用；SCREEN 或旧式缺省时转成屏幕坐标。可用同一 Android 根 View 的 `locationOnScreen - locationInWindow` 作为偏移；打开与返回共用转换，不能硬加状态栏高度。分屏、窗口移动、IME 和旋转后重新采集，不复用旧屏幕偏移。
- **半遮卡片与懒列表**：官方滚动入视辅助是 RecyclerView 专用，且 Demo 不对 Bitmap 输入自动启用。Compose Lazy 列表需按业务 ID 自己解析/最小滚动，布局完成后在 CLOSE 开始前刷新落点；首个验证也可先约束完全可见卡片，其他情况走现有降级。当前位置不能靠首次点击的静态 Rect 保证。
- **材质与底色**：Kit 的 `color` 是回到源页时外扩 mask 的底色，不是卡片色或目的页色。SleepDown 的壁纸/渐变没有稳定单色时按官方建议省略，不能估算合成色。系统声明可用的 blur/backgroundScale 与应用自绘效果要明确由谁播放，防止重复缩放/模糊。
- **透明窗口**：官方 Demo 使用普通 Activity，未公开承诺透明窗口、Compose backdrop 或跨窗玻璃的完整兼容。项目已有透明宿主和 Oplus opaque 候选，但 Oplus 的 opaque 要求不能推断成联盟 SDK 的硬约束。首个试验使用真实 opaque Activity 承载同一业务内容，单独核对主题、首帧、系统栏和玻璃；不通过全局主题修改解决。
- **参数和降级集合**：`EXTRA_SHADOW` 为 float，`EXTRA_EDGE_FILL_MODE` 为 int；圆角单位为 px。仅写设备声明的 extras；BACK 与 FALLBACK 的支持集合分别解析，不互相推断。Bitmap 重载已经显式传 Bitmap，不再重复塞 `EXTRA_BITMAP`。
- **生命周期**：`cancelCurrentViewAnimation()` 是全局入口，只能在仍拥有当前原生动画的 session 调用，不能用每个页面的 onDispose 无条件取消；旧回调必须被 generation 拦截。保留快速返回、嵌套跳转、取消、旋转、后台和进程重建后的 Legacy/普通页面恢复。
- **R8**：AAR 自带 consumer rules，保留公共 API、协议 delegate 和 listener adapter。验收使用压缩 Release，核对最终 DEX/回调及规则合入，不能只以 Debug 正常为依据。优先修复真实缺失的规则，不添加全包 keep 掩盖问题。
- **示例不能逐字搬用**：当前 `HomePresenter` 仍使用 View drawingCache 和屏幕坐标；README 的新规范则要求能力声明选择坐标。SleepDown 应复用自己的 Compose 截图并遵守协议定义。示例构建脚本的 kitRepo 分支当前被注释，实际直接引用 Maven，不能只按注释认定传 local 参数即可切换。

## 发布与验证前提

**许可**：[Demo LICENSE](https://gitee.com/itgsa_sharedviewkit/SharedViewDemo/blob/4ed95e70877e019a286f4c9bb041732582bf406f/LICENSE) 为 MIT；[官方 SDK POM](http://nexus.itgsa.com:5566/repository/sharedViewKit/gsai/sdk/sharedview-kit/1.0.0.4/sharedview-kit-1.0.0.4.pom) 明确标为 `Proprietary - ITGSA`，注明提供给获授权合作伙伴并适用相应协议。公开下载和 Demo MIT 不足以认定 SDK 可直接随本应用发布；实施前需要核实项目使用资格与分发条款。本轮没有接入产物。

**交付来源**：本次实际访问的联盟 Nexus 仍是 HTTP，README 也称为内测部署。正式构建需要明确已核验的依赖来源和固定校验值；目前没有读取到官方 HTTPS 升级完成的证据。

**首批验证顺序**：

1. 确认许可与目标设备的 Kit 支持值、extras 元数据；不支持即验证 Legacy 正常。
2. 只试 `CourseManagementToDetail`：普通开合、打开中返回、连续点击、返回目标变化/删除/滚出视口，确认没有白帧、双卡、重复 Activity、资源泄漏和重复保存。
3. 验证深浅主题/壁纸、字体与窗口尺寸、系统返回、后台恢复及真实 opaque 宿主表现；观察与原 Legacy 同场景的实际体验。
4. 通过 Release 编译/打包、R8 产物与现有转场状态/载荷回归后，再扩到历史详情。采用系统动画不等于已经改善帧率，不据接口或 Demo 宣称认证与性能验收完成。

本轮未改 Kotlin、资源、依赖或 Manifest，因此没有重跑 Android 构建；已核对公开 Git HEAD、Maven metadata/POM/AAR、AAR Manifest/公开 API/consumer rules，以及项目现有路由/会话/源截图代码。真正接入、ROM 能力和视觉结果仍待后续验证。
