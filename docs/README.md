# 开发文档入口

先阅读根目录 [AGENTS.md](../AGENTS.md)，再按问题查阅下列文档。无需在每轮工作前遍历所有文档。

| 任务 | 参考文档 |
| --- | --- |
| 目录、依赖与结构调整 | [PROJECT_STRUCTURE.md](architecture/PROJECT_STRUCTURE.md) |
| 页面、弹窗、选择器与公共控件 | [SLEEPDOWN_DESIGN_SYSTEM.md](architecture/SLEEPDOWN_DESIGN_SYSTEM.md) |
| 玻璃采样、渲染和生命周期 | [LIQUID_GLASS_FRAMEWORK.md](performance/LIQUID_GLASS_FRAMEWORK.md) |
| 跨 Activity 转场 | [TRANSITION_FRAMEWORK.md](TRANSITION_FRAMEWORK.md) |
| AI 文件导入 | [AI_FILE_IMPORT.md](ai/AI_FILE_IMPORT.md) |
| AI助理 | [DAY_AGENT_RUNTIME.md](architecture/DAY_AGENT_RUNTIME.md) |
| 备份与升级 | [BACKUP_FORMAT_V1.md](migration/BACKUP_FORMAT_V1.md)、[1_2_0_PACKAGE_MIGRATION.md](migration/1_2_0_PACKAGE_MIGRATION.md) |
| 节次设置 | [PERIOD_SCHEMES_GUIDE.md](PERIOD_SCHEMES_GUIDE.md) |

带日期的验收与性能报告、worklog、archive 和版本说明是历史证据。旧实验参数、测试数量、临时禁令和“本轮不做”的范围只适用于当时任务。引用时核对日期与代码；新的明确需求可以更新相关基线。

专项规范中的“不改动成熟链路”用于控制任务外回归和纯结构迁移，不阻止针对该链路的功能修复、设计调整或有证据的优化。发现过时事实时就地修正文档，避免继续追加相互冲突的补丁式约束。
