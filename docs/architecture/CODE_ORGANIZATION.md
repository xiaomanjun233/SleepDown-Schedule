# 代码组织与拆分约定

## 职责与依赖

| 目录 | 职责 | 不能承担的职责 |
| --- | --- | --- |
| `app` | Application、Activity 宿主、导航、实现选择与依赖装配 | 可复用控件、解析算法、厂商反射实现 |
| `feature/<功能>` | 页面、交互会话、功能状态与流程 | 另一功能的私有状态、底层系统能力实现 |
| `domain` / `model` | 可测试的业务规则与共享模型 | UI 绘制、Activity、具体功能页面；允许既有模型的稳定性注解 |
| `data` | 数据库、偏好、读写与事务 | 功能 UI 类型、控件生命周期 |
| `core/ui` | 公共控件、材质协议与输入协议 | 厂商类名、具体页面与应用导航 |
| `platform/coloros` | ColorOS 能力检测、原生控件适配与资源释放 | 课表保存、页面导航、功能状态 |
| `glass` / `transition` | 采样与材质、既有窗口转场 | 混入页面业务规则 |

装配层选择材质实现，页面调用公共控件，公共控件通过可选渲染接口交给实现。实现不可用时由原控件继续绘制。设置存储保留用户选择，运行时能力判断决定有效模式；两者不能混成同一个值。

## 什么时候拆文件

- 文件同时承担页面装配、公共控件、数据解析或 Android 组件等不同职责时，沿职责拆分。
- 多处使用的 UI 放入所属功能的组件文件；跨功能且没有业务依赖的组件才进入 `core/ui`。
- 状态持有者与纯规则能够独立验证时单独放置；共同维护一个手势或转场生命周期的局部状态与私有辅助函数保留在一起。
- 文件长度用来提示审查，不是自动拆分阈值。不能按行数等分、按“工具类”兜底，也不能为减少文件长度增加相互回调的环。
- 一个 Manifest 组件一个文件；已发布组件的全限定名保持稳定。文件名对应主要类、组件或规则，禁止新增没有边界的 `Utils.kt`、`Common.kt`、`Logic.kt`。
- 移动声明时同步改包名、精确导入、测试与文档，不永久遗留仅为迁移方便的重复实现。确需兼容的公开入口要写明原因。

## 拆分验收

1. 先提交可工作的原状态；一次提交表达一个可说明的职责迁移。
2. 结构提交保留函数体、状态归属、Modifier 顺序、几何、动画时序、保存语义与协议。
3. 逻辑修复另列原因与定向验证，不藏在大范围移动中。
4. 检查依赖方向并编译；涉及状态机、数据规则时执行相应测试。
5. 注释只解释不明显的约束、生命周期与兼容原因；删除阶段汇报、口号、重复翻译代码和已经失效的实验说明。

本地运行 `python scripts/check_architecture.py` 检查生产 Kotlin/Java 的源码引用边界，包含渠道 source set；同一检查及其回归测试在 PR 中执行。检查限制共享层、厂商层、feature、glass 和 transition 的反向引用，并按 feature 检测循环。`scripts/architecture_baseline.json` 精确记录既有违规文件/目标和循环边，阻止新增；解决后同步删除条目，不把扩大基线当作通过检查的方法。当前债务和处理顺序见 [架构审查](../reports/2026-10-20-architecture-review.md)。

检查器只覆盖显式 import 和全限定引用，不解析根兼容门面的传递依赖、字符串模板表达式、反射或外部模块内部引用；通配 import 按声明保守建边。它不代替编译器的类型解析或架构评审。根包兼容门面只允许承接现有类型和稳定 Android 入口，不得用于绕过依赖边界。

不把目录拆分当作性能提升的证据。性能结论需要同设备、同场景的启动、帧耗时、分配或采样数据。Compose 的业务状态按单向数据流组织，滚动、手势与动画状态保留在最近的使用者附近。

功能内的完整执行流程通过普通构造参数接收数据入口、所属作用域与必要回调；应用层负责装配。不要在执行器中创建独立 CoroutineScope 来延长任务寿命。页面退出后仍须保存或继续传输的工作，沿用已有 Application/Service 作用域；ViewModel 对 UI 公开只读状态，写入通过它的操作入口完成。

本轮具体所有权：`feature/home/HomeImportHandoffState` 保存 Composition 内的导入快照、提交标记与数据就绪条件，根 Composable 装配入口卸载和原有揭示动画。`feature/reminder/LiveUpdatePayloadCodec` 集中 Intent payload 与 Service 重启存储的编解码，调度门面及 Service 各自保留系统调度和生命周期。周视图显示分组与提醒分段使用不同入口，二者共用原始课程投影，不能用合并显示后的外包络覆盖逐课时数据。详见[续作报告](../reports/2026-10-20-architecture-followup-and-course-fixes.md)。

个性化会话持有草稿与提交回读状态，公共材质只消费 core 内的预览/可读性协议；转场壳通过内容槽让 app 装配设置页。共享业务值与备份快照位于 model，移动可序列化类型显式保留旧名称。Agent 计划准备属于 domain，Repository 保留整段事务；公共 bootstrap 与具体 AI 激活在应用装配点接线。最新落地、兼容证据与剩余依赖见[收口报告](../reports/2026-10-20-architecture-completion-and-ai-fixes.md)。

## 官方参考

- [Android 架构建议](https://developer.android.com/topic/architecture/recommendations)
- [Compose 状态提升](https://developer.android.com/develop/ui/compose/state-hoisting)
- [模块化模式](https://developer.android.com/topic/modularization/patterns)
- [Compose 性能建议](https://developer.android.com/develop/ui/compose/performance/bestpractices)
