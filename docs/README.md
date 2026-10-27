# 开发文档入口

外部贡献从 [CONTRIBUTING.md](../CONTRIBUTING.md)开始。维护者和 AI 接手时先完整阅读 [AGENTS.md](../AGENTS.md)，接续工作再读 [AI_HANDOFF.md](AI_HANDOFF.md)，核对实际 Git 与 PR；分支、提交和合并统一按 [DEVELOPMENT_WORKFLOW.md](DEVELOPMENT_WORKFLOW.md)执行。新任务按需查模块文档，无需遍历全部历史报告。

## 文档职责

| 入口 | 负责内容 | 何时更新 |
| --- | --- | --- |
| [根 README](../README.md) | 产品介绍、获取源码与构建入口 | 用户可见功能或获取方式变化 |
| [AGENTS.md](../AGENTS.md) | 人与各类助手共用的长期边界、验证和本机交付方式 | 长期协作约定变化 |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | 外部贡献者的 Fork、构建、PR 与评审入口 | 对外贡献流程变化 |
| [DEVELOPMENT_WORKFLOW.md](DEVELOPMENT_WORKFLOW.md) | 分支、提交、PR、CI、合并、清理和交接的统一流程 | 开发流程变化 |
| [RELEASE_MODEL.md](RELEASE_MODEL.md) | 版本、渠道、发布规则与注明日期的基线快照 | 发布或 main 回流关系变化 |
| [AI_HANDOFF.md](AI_HANDOFF.md) | 当前目标、分支/PR、已有授权来源、验证、产物和下一步 | 阶段交付、更换助手或任务结束 |
| 专项规范 | 对应模块的设计与实现约定 | 模块契约或实现路线变化 |
| 日期报告、`reports/`、`archive/` | 当时的调查、验收和暂停实验记录 | 形成可追溯的阶段结论；后续纠正需注明时间与新证据 |
| 根目录 `release-notes/` | 每次 Beta 与正式版的产品更新说明 | 对应发布时 |

[CLAUDE.md](../CLAUDE.md) 与 [GEMINI.md](../GEMINI.md) 只指向上述入口，不各自复制规则。其他助手同样可从 `AGENTS.md` 与交接页开始。交接页是当前状态的摘要，代码、构建配置、实际 Git 和平台状态才是事实来源；用户授权须能追溯到原指令。

## 按任务查阅

| 任务 | 参考文档 |
| --- | --- |
| 当前版本、Beta / 正式版与历史实验线 | [RELEASE_MODEL.md](RELEASE_MODEL.md)、[EXP_BRANCH_AND_RELEASES.md](EXP_BRANCH_AND_RELEASES.md) |
| 换助手、续做与当前待验收事项 | [AI_HANDOFF.md](AI_HANDOFF.md) |
| 最初十个 Beta 的功能与时间证据 | [EARLY_BETA_HISTORY.md](EARLY_BETA_HISTORY.md) |
| 每轮开发、PR、收尾与工作树整理 | [DEVELOPMENT_WORKFLOW.md](DEVELOPMENT_WORKFLOW.md) |
| 2026-10-19 的旧 PR、Actions 通知与回流调查 | [仓库整理报告](reports/2026-10-19-repository-pr-actions-cleanup.md) |
| 1.2.7-r1 修复版发布 | [发布与核验记录](2026-10-19-stable-1.2.7-r1-release.md) |
| 1.2.7.1 兼容补发与 r1 下架 | [更新识别修复、发布与核验记录](reports/2026-10-20-update-revision-compatibility.md) |
| 按用户任务查功能与实现入口 | [FEATURE_MAP.md](FEATURE_MAP.md) |
| 目录、依赖与结构调整 | [PROJECT_STRUCTURE.md](architecture/PROJECT_STRUCTURE.md)、[CODE_ORGANIZATION.md](architecture/CODE_ORGANIZATION.md) |
| 课程时间、独立与日期作息、Room 50、拾光 JSON 和备份兼容 | [COURSE_TIME_MODEL.md](architecture/COURSE_TIME_MODEL.md) |
| 全项目架构审查、首批重构与后续顺序 | [2026-10-20 架构审查](reports/2026-10-20-architecture-review.md) |
| 首页导入状态、通知编解码、自定义课时显示与自动静音续作 | [2026-10-20 续作与课程修复](reports/2026-10-20-architecture-followup-and-course-fixes.md) |
| 个性化、共享值、事务准备、教务/通知/材质边界及 ChatGPT/模型列表修复 | [2026-10-20 架构收口与 AI 修复](reports/2026-10-20-architecture-completion-and-ai-fixes.md) |
| 功能内课表状态、共享转场计算与根包别名检查 | [2026-10-21 状态与转场边界续作](reports/2026-10-21-schedule-state-and-transition-boundaries.md) |
| 动效参数、帧计算及播放职责整理的验证 | [2026-10-21 动效维护整理](reports/2026-10-21-motion-maintainability.md) |
| 根节点更新会话、生命周期、剩余循环与分支迁移 | [2026-10-21 根节点续作](reports/2026-10-21-root-and-workflow-followup.md) |
| 金标官方能力调查及旧 Oplus 动画退役 | [接入调查](reports/2026-10-21-sharedviewkit-integration-audit.md)、[首批实现与验证](reports/2026-10-21-sharedviewkit-pilot.md) |
| 页面、弹窗、选择器与公共控件 | [SLEEPDOWN_DESIGN_SYSTEM.md](architecture/SLEEPDOWN_DESIGN_SYSTEM.md) |
| 玻璃采样、渲染和生命周期 | [LIQUID_GLASS_FRAMEWORK.md](performance/LIQUID_GLASS_FRAMEWORK.md) |
| 可选凝光材质、公共控件协议与运行时门控 | [SLEEPDOWN_COLOROS_FRAMEWORK.md](architecture/SLEEPDOWN_COLOROS_FRAMEWORK.md) |
| 跨 Activity 转场 | [TRANSITION_FRAMEWORK.md](TRANSITION_FRAMEWORK.md) |
| 定位动效参数、播放与资源释放边界 | [动效维护入口](architecture/MOTION_MAINTENANCE.md) |
| AI 文件导入 | [AI_FILE_IMPORT.md](ai/AI_FILE_IMPORT.md) |
| AI助理 | [DAY_AGENT_RUNTIME.md](architecture/DAY_AGENT_RUNTIME.md) |
| 备份与升级 | [BACKUP_FORMAT_V1.md](migration/BACKUP_FORMAT_V1.md)、[1_2_0_PACKAGE_MIGRATION.md](migration/1_2_0_PACKAGE_MIGRATION.md) |
| 节次设置 | [PERIOD_SCHEMES_GUIDE.md](PERIOD_SCHEMES_GUIDE.md) |
| 厂商实验功能的历史与隔离边界 | [EXP_BRANCH_AND_RELEASES.md](EXP_BRANCH_AND_RELEASES.md) |

## 维护方式

长期规则在所属规范维护，其他入口引用；当前状态更新交接页，完整经过引用报告。不要将一次失败、旧测试数量、临时禁令或“本轮不做”追加成全局要求；专项文档也不能阻止用户明确要求修复的功能。

历史工作空间盘点见 [2026-09-25](2026-09-25-workspace-audit.md)和 [2026-10-19](reports/2026-10-19-repository-pr-actions-cleanup.md)，它们不表示今天所有工作树都可清理。Release Notes 是当时交付记录，不自动定义当前代码状态。引用历史结论时核对日期、代码与适用范围。
