# 根节点续作与开发流程审查

日期：2026-10-21。接续 #74/#78/#79 的成果，不调整 UI 样式、动画曲线和时长，不把代码整理视为性能证据。本轮先核对真实 Git/PR，再分批整理；main/develop 流程变更放在独立 [#80](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/80)，不混入本 PR 的代码提交。

## 审查结论与顺序

1. #74/#78 已合并到 main，#79 仍开放，检查时 head `bc69a3ef`，完整 CI [39549747482](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39549747482) 成功。沿用原 head 和提交历史，新增批次独立提交，不重做已完成的总 ViewModel、共享值、事务准备、导入交接或材质/动效迁移。
2. 旧 Oplus 私有转场近千行后端、反射回调和 Debug 探针已有明确删除授权，优先退役。`143eddb3` 以标准 Kit 的单路线试点接替，具体默认门控、渠道隔离及验证见[首批报告](2026-10-21-sharedviewkit-pilot.md)。其他活跃 ColorOS 功能不属于退役范围。
3. 根节点剩余约 4500 行确实仍混合会话、导航、更新及帧捕获。优先解决有重复实现、所有权明确的更新流程和生命周期观察，而不是把主函数按行数切成更多函数。
4. 明确的 feature 反向引用先清理；core/glass 的 renderer/控件依赖需另一个有实机证据的批次，不通过 typealias、通配导入或新增豁免隐藏。

## 本批代码变化

- `feature/update/AppUpdateSession` 持有单个页面的弹窗状态、已下载 APK 和检查/下载/安装授权返回。首页与设置页复用同一实现，各自保持 Composition 会话和作用域；后台下载继续由原 `GiteeAppUpdater`/Service 负责，没有全局 UI 状态、新 ViewModel、接口框架或模块。
- 自动检查仍保留 loaded、渠道、用户设置、每日检查与缓存顺序；手动检查仍标记每日状态，首页错误重试不额外标记。原下载、系统安装授权和错误流程沿用。备用入口与外链由宿主装配，保持首页回设置、设置页打开备用网站的区别。
- 更新弹窗从 settings 移到 update，文本、材质、操作及布局经规范化对照完全一致。根节点由 4493 行到 4406 行，设置根页 330 行到 266 行。行数只记录结果，收益是两套流程变为同一所有者，后续更新行为不再需要两处同步修改。
- `app/ui/HomeResumeEffect` 单独拥有 Lifecycle observer 的订阅/释放，根节点只装配导入消费、通知刷新和课表编辑返回。observer 用当前回调，避免重组后继续引用初次捕获的页面状态；保留首次 ON_START 和已 STARTED 时附加的原语义，不改变课表预览的 generation、绘制等待和保存逻辑。
- 移除公共控件已迁入 core 后遗留的 settings 通配/失效 title 导入，删除 backup/course/home→settings 三条已解决的声明循环豁免。没有迁移真正使用 SettingsPage 的调度管理或 DonationThanksPanel 调用来刷指标。

## 剩余复杂区域

| 优先级 | 区域及当前事实 | 后续可独立审查的边界 |
| --- | --- | --- |
| 高 | `ScheduleAppUi.kt` 4406 行，picker 快照任务、详情捕获和多个叠层仍相互交接 | 将 picker 的捕获/预热/取消按同一会话所有权收拢，保留原 generation、源冻结、返回帧等待；不要把 Bitmap 和 Activity 引入总 ViewModel |
| 高 | core↔glass；core 的控件/性能/壁纸入口引用 glass，glass 的文字、壁纸与质量输入引用 core | 基础值/协议归现有 core，具体 renderer 和 glass 控件归 glass；逐入口梳理 producer/consumer，不靠重命名或复制 Local 消除图上的边 |
| 中 | agent↔importing、home↔course、settings 与多功能的装配依赖 | 先区分业务协议、通用 UI 与页面装配；共享纯数据进既有 model/domain，页面内容槽由 app 装配，避免让 core 接管功能业务 |
| 中 | 教务浏览器 1806、AI 进度 1621、课程管理 1580、助手对话 1557 行 | 围绕 WebView/Service/编辑会话及渲染边界处理；保留 bridge 失效、工具权限和预览确认，不以行数驱动拆分 |
| 较低 | Repository 1224、Room 连续迁移 1331、周 Widget renderer 1289 行 | 事务与迁移账本、同一 RemoteViews 像素输出保持内聚；有具体规则或数据操作风险才拆并补对应回归 |

当前检查器为 **563 个生产 Kotlin/Java 文件、0 违规文件依赖、41 条历史声明循环边**。它覆盖显式引用与根别名链，不能冒充完整类型/反射调用图。三条本批减少项属于已迁移控件后的失效引用清理，不能说所有功能的业务循环已解决。总 ViewModel 已在前轮降到 457 行，本轮不再为缩短它制造新接口。

## 验证与兼容边界

本批 `compileGithubReleaseKotlin` 与定向回归通过，4m32s；实际执行 `HomeResumeEffectTest` 2 项、`GiteeAppUpdaterTest` 12 项、`SharedViewKitBackendTest` 11 项、`SharedViewKitCapabilitiesTest` 3 项，共 **28 项，0 失败/错误/跳过**。生命周期覆盖首次/后续启动、重组后的当前回调、订阅释放和已启动 owner 附加。检查器 13 项回归、563 文件依赖检查和 diff 空白检查通过，弹窗内容对照一致。CI 增加生命周期与现有版本选择回归。

Room schema、迁移、DAO、BackupFormatV1、课程时间、AI 导入确认和 Widget 输出未改变；前批 Kit 的 Manifest alias、Intent wire id 与渠道隔离保留。未执行设备视觉、OEM 静音、真实 ChatGPT 账号、金标 ROM/认证或性能验收；这些不能以单元测试替代。前批签名 APK 对应 `143eddb3`，不称其包含本批根节点改动。本轮没有安装、合并、正式版本变更或发布。

## 分支同步与 AGENTS

develop `848ac5a4` 是 main `9b6acbb2` 的祖先，独有 0、落后 610；#80 从 main 准备保留历史的同步与流程规范，目标 develop。main/develop 均未直接更新，不重置、不强推。#79 暂保留 main，待 #80 经授权合并、核对祖先后再调整 base，并复查完整 diff 与 CI。新普通任务随后从最新 develop 派生；release/hotfix 合入 main 后及时回流 develop。

#80 的流程实现 `a5df31a3` 已通过完整云端 [39555564064](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39555564064)，含范围/祖先回归、Release 编译、既有定向测试与 Debug 打包；不是 skipped。后续文档更新应按实际 head 回读状态，不能把该结果记为尚未执行的新提交验证。

AGENTS.md 已公开跟踪，#80 更新公共分支规则，保留文件与历史。可在后续明确任务中提取本机配置，不自行 untrack 或清理历史。完整保护细则读取返回 403；未修改设置，不断言审批与管理员豁免详情。正式发布须另行授权，并核对实际 main 源码、versionName/versionCode、applicationId、渠道、签名、APK、Tag 和 Release。
