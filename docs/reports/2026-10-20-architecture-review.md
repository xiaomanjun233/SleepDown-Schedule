# 全项目架构审查与首批渐进重构

此页记录首批审查和实现。首页导入状态、通知编解码及用户追加的课程显示/自动静音修复见[同日续作报告](2026-10-20-architecture-followup-and-course-fixes.md)，当前状态以交接和 PR head 为准。

## 基线与范围

用户要求全面审查后直接实施收益明确的一批重构，并补充“先合并”。因此先处理 #78，再从合并后的 main `9b6acbb23512e48d0af24afec0813ecadf27573e` 开始本任务，分支为 `codex/architecture-boundaries`，PR 为 [#79](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/79)，工作树为仓库忽略目录 `tmp/architecture-refactor/`。主工作目录切回并快进到 main，其他工作树、stash 和私有后端保留。

#78 使用 merge commit，保留 `bdc380ee` 等正式发布提交的祖先关系。合并前云端 Release Kotlin 编译通过，回归发现三个公共作息测试夹具问题；`d9b6f3a6` 初始化夹具的派生学期状态，并增加旧记录只规范化一次的回归。受影响测试 27 项、本地与 CI 相同的完整集合 624 项通过，0 失败/错误/跳过。云端重跑 [39360361325](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39360361325) 随后全部通过，包含 Release 编译、回归与 Debug 打包；与本次重构的验证分开记录。

审查覆盖生产 Kotlin/Java 的包引用图，以及应用装配、ViewModel、Repository、AI 执行与后台任务、教务 adapter/bridge、通知和 Widget 的关键入口。引用图包含 main、github、store 等生产 source set，排除测试、构建产物和私有后端。声明依赖图中的通配 import 可能包含未使用引用；它不是编译器解析后的调用图，也不代表逐行审阅了所有功能实现。

## 主要问题与优先级

| 范围 | 实际问题与证据 | 维护收益与处理顺序 |
| --- | --- | --- |
| `app/state/ScheduleViewModel.kt` | 基线 888 行；AI 设置的校验、节次准备、Room 提交、偏好写入、回读、回滚与撤销全部内嵌在总 ViewModel。提示状态公开为 MutableStateFlow | 最高优先：把完整已确认计划交给 Agent 功能内的执行器；ViewModel 负责作用域与刷新装配，提示只读公开。本次实施 |
| AI 偏好回滚 | `DAY_AGENT_WEEK_ENABLED` 可写，但偏好快照遗漏该值；撤销或后续偏好写入失败时不能恢复周视图助理开关。旧撤销仅检查 Room，未检查已发生后续变化的助理偏好 | 两项独立逻辑修复与真实 Room/偏好回归，不混进机械迁移提交。本次实施 |
| 架构检查 | 原检查只覆盖 main Kotlin 的部分反向依赖；没有 feature 循环、feature→app、渠道 Java/Kotlin 和既有债务收敛检查 | 高优先：扩大检查范围，精确记录既有文件依赖与循环边；禁止新增违规和新增循环，解决后删除豁免。本次实施 |
| `app/ui/ScheduleAppUi.kt` | 4524 行的一个根 Composable，同时装配导航、导入交接、个性化预览、课程编辑和多套冻结/采样状态 | 高收益但视觉风险高：后续围绕一个真实会话逐步提取状态，而非把渲染树按行数切开。本次保留函数体、绘制顺序和动画参数 |
| `data/repository/ScheduleRepository.kt` | 作息公共库、课程变更、导入、Agent 事务及快照读写集中；同一事务中的过期检查、写入和验证有明确数据一致性价值 | 后续先提取可独立验证的纯计划准备。继续保留统一事务入口，不先拆成多套 Repository 或把事务分散到多个服务 |
| Agent ↔ importing ↔ backup | AI 设置/传输/历史和备份 DTO 在功能间互相引用；还存在迁移遗留通配 import，使真实依赖难定位 | 后续逐组清理未使用引用，再把确属跨功能的协议值归入现有 model/data/core；当前不移动正在使用的 transport、OAuth 或导入状态机 |
| app ↔ feature | RemoteConfig 同时选择 AI provider、驱动配置体验；多个功能直接引用它，部分 feature Activity 直接创建总 ViewModel；还有仅用于 KDoc 或未使用的 app import | 本次只清理确认无用的反向 import。实际依赖逐个改为构造参数、功能状态或共享值，不引入统一导航 Manager/依赖注入框架 |
| glass / transition ↔ feature | 材质与转场消费首页冻结/可读性状态，部分文件还带无用页面 import；core/ui 与 glass 也有双向边 | 本次删除确认无用的引用并登记剩余边。后续先确定采样与冻结状态的真正所有者，保留 #78 的全部运行行为 |
| model ↔ domain | AgentActions 引用调休值，CourseEntity 的非 Room 投影引用 EffectiveCourseArrangement；domain 同时消费 model | 后续将共享不可变值与算法分开。涉及序列化、瞬态投影和数据写回语义，不能通过复制 DTO 或删除投影来消除表面循环 |
| 通知调度 | NotificationScheduler 1496 行，包含闹钟计划、payload 编解码、通知渲染、实时服务和旧勿扰兼容；时间规则已有 domain 入口 | 后续优先隔离 payload 编解码和通知渲染；保持 NotificationScheduler 门面、Intent wire id、requestCode 和系统恢复行为，旧勿扰迁移不视为无用代码 |
| 教务导入 | adapter→bridge→UI 路线已存在，#78 也已分开脚本执行、请求拦截和 POST transport；主要复杂度仍在 WebView/Activity 交接 | 后续围绕 WebView 会话销毁与回调释放做单独整理，不重新维护学校协议。本次不改 adapter、bridge 和学校脚本 |
| 进程状态与生命周期 | DayAgentRunCoordinator 与 AiEduImportProgressSession 负责跨页面后台任务；Application 的全局设置队列需在设置页退出后继续保存 | 这些全局状态有明确生命周期用途，不能一律改成页面 remember/ViewModel。后续审查终态回调释放、generation 与取消边界，不混同于无用单例 |

## 实际改动与回退边界

1. **执行所有权**：新增功能内 `AgentSettingPlanExecutor`，集中完整的设置计划执行与撤销。机械迁移阶段逐段比对原函数体，除作用域和刷新回调接线外保持一致。ViewModel 只委托执行，保留原作用域、回调时机和通知/Widget 刷新入口；提示状态改为只读 StateFlow。
2. **独立正确性修复**：偏好快照增加周视图助理开关，回滚与撤销按原值恢复；现有偏好文件和键保持不变。另在撤销开始前检查已应用的助理偏好快照，后续偏好或授权决定变化时拒绝撤销，在数据库写回前返回明确原因，避免旧快照覆盖新操作。
3. **依赖防退化**：检查显式 import 和全限定引用，按 feature 名称建立依赖图并检测循环边。已有债务写入 `scripts/architecture_baseline.json`；新增违规文件、扩大目标或新增循环会失败，已解决条目未移除也会失败。删除 7 处确认无用的反向 import，声明违规依赖由 51 项降到 44 项，循环边由 110 项降到 109 项；剩余债务逐项登记，不宣称架构已无循环。补充检查器回归并接入 PR scope job，新执行器测试加入 Android CI 集合。
4. **文档**：更新架构入口、main/发布祖先关系与交接，记录可直接接续的后续顺序。

没有新增 Gradle 模块、接口层或框架，没有把通用队列、通知或导入流程统一成新的全局调度器。迁移、两项逻辑修复和依赖检查各有独立提交。

## 检查能力的边界

检查器不替代 Kotlin/Java 类型解析。它忽略注释与字符串，不能解析字符串模板中的表达式、反射、未限定的同包调用、根包兼容门面的传递依赖或外部 Gradle 模块内部依赖。通配 import 会按声明保守建立边。根兼容门面和稳定 Android 组件仍需在代码评审时检查，不能用它们绕过层级约定。

基线只用于逐步归还历史债务；本次新增的执行器没有新增反向依赖豁免。清理一条声明依赖不是性能改善证据，本次没有宣称帧率或内存收益。

## 数据、视觉与验证

本次不修改版本、Manifest、Room schema/迁移、DAO、Repository、BackupFormatV1、ICS、SleepDown 口令、Widget 布局、通知 wire id、AI 历史或导入协议。玻璃和首页相关文件若有改动，仅删除未使用 import；没有改变 Composable 函数体、Modifier、采样域、光效和时序。没有安装、启动、发布应用或合并本次重构 PR。

| 检查点 | 实际验证 |
| --- | --- |
| `027427aa` 纯执行所有权迁移 | `compileGithubReleaseKotlin` 通过（2m10s）；执行器、Agent 计划/动作、domain 执行和公共作息仓库 5 类共 83 项通过，0 失败/错误/跳过（3m11s）。迁移的执行、节次准备和偏好函数体与 main 对比一致，仅替换接线 |
| 周视图偏好缺口复现 | 新增的撤销、写入失败回滚两项测试在修复前均按预期失败，证实缺口；不是用修改断言绕过失败 |
| `bd6ddb32` 周视图偏好恢复 | Release Kotlin 编译和同一批 5 类共 85 项测试通过，0 失败/错误/跳过（4m38s） |
| `5c848e35` / `4bc78a43` 最终运行时与依赖清理 | `compileGithubReleaseKotlin testGithubDebugUnitTest` 通过（4m52s），相关 5 类共 87 项、0 失败/错误/跳过，包含新增的后续偏好/授权变更保护；7 处清理编译通过且除 import/注释外代码一致 |
| 架构检查 | 9 项回归通过；覆盖 544 个生产 Kotlin/Java 文件。新增执行器未新增反向依赖豁免，清理后基线只减少 |

本地首次新工作树构建曾误指定未打补丁的 Miuix 目录，已改回项目配置的 `miuix-local`；2 GiB 堆的双变体全量编译出现堆不足，随后按内存情况使用单 worker、临时 `-Dorg.gradle.jvmargs="-Xmx4096m -Dfile.encoding=UTF-8"`，没有改动项目构建配置。完整命令采用 `--console=plain --no-daemon --no-parallel --max-workers=1`，定向类为 `AgentSettingPlanExecutorTest`、`AgentPlanSafetyTest`、`DayAgentActionsTest`、`AgentExecutionTest`、`SharedPeriodSchemeRepositoryTest`。#79 转可评审后运行其最终 head 的完整 CI，结果见 PR Checks；没有把草稿 skipped 当作通过。单元测试不替代实机视觉、性能或 OEM 后台行为验收。
