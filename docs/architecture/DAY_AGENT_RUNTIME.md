# Day Agent 工具与提示词运行规范

本文记录今日助手的请求阶段、工具生命周期和提示词边界，目标是减少无意义的模型轮次和重复上下文，同时保持模型自主选择工具、组合操作与迁移自检的能力。

## 三段式请求

1. `ChatSystem`：只放稳定身份、任务边界、事实/信任边界和高层规划约束。
2. `ToolDecisionStage`：只负责本轮工具选择，要求把当前可判断且互相独立的读取放在同一响应并行发出。
3. `FinalAnswerStage`：工具阶段结束后才附带完整 `<agent_actions>` 写入协议和 JSON 示例。

完整写入协议不再随每一次工具判断重复发送。以本次源码字符数为基准，稳定 `ChatSystem` 从 4632 字符缩至 630 字符；整个提示词文件从 5846 字符缩至 3920 字符。这里是静态字符对比，不等同于供应商实际 token 或延迟收益。

## 工具生命周期

| 类型 | 工具 | 本轮策略 |
| --- | --- | --- |
| 一次性事实快照 | `GET_CURRENT_OVERVIEW`、`GET_WEEK_SCHEDULE`、`GET_SEMESTER_SCHEDULE`、`GET_PERIODS`、`GET_SETTINGS` | 成功调用后从下一轮工具 schema 移除，结果继续保留在上下文 |
| 可重复检索 | `SEARCH_COURSES` | 允许用不同关键词重复；同参数结果走本轮缓存 |
| 低频写入 | `UPDATE_MEMORY` | 最多提供一次；仍受每日授权和频率 gate 控制 |
| 外部公开事实 | MiMo `web_search` | 仅官方支持端点提供，不替代本地课表事实 |

相同工具和规范化参数在同一用户回合再次出现时，不再重复序列化完整结果，而是返回事实版本一致的复用提示。每个 provider 仍收到与自己 `call_id` 对应的合法结果，Chat Completions 与 Responses 两条链路遵守相同策略。

MiMo 联网搜索属于供应商服务端插件，不属于 SleepDown 本地函数工具。只有官方 MiMo 端点与支持的模型可以启用该类型；若模型先返回函数形态的 `web_search` 意图，解析层将其识别为供应商搜索请求而不是“未知本地工具”，随后最多重试一次并把同一请求的 `force_search` 设为 `true`。只有响应中的 `annotations`、`usage.web_search_usage` 或搜索后的正文才算真实搜索证据；结果以 `provider_web_search_result`、`untrusted_external_data` 身份进入最终回答阶段，不能取得系统指令或本地课表事实的权限。其他未知工具名仍然立即报错，不能因为兼容 MiMo 而被吞掉。

## 选择规则

- 当前状态：`GET_CURRENT_OVERVIEW`。
- 指定课程：`SEARCH_COURSES`。
- 周内空档或冲突：`GET_WEEK_SCHEDULE`。
- 总览、批量或跨周修改：`GET_SEMESTER_SCHEDULE`。
- 节次和作息：`GET_PERIODS`。
- 设置：`GET_SETTINGS`。
- 节次结构可能改变课程实际时间时，在同一轮读取 `GET_PERIODS`、`GET_SETTINGS` 和所需课程范围。

不使用 Kotlin 关键词路由裁掉模型尚未读取的能力；动态收窄只发生在不可变事实已经返回之后，因此不会因为中文表达差异漏掉必要工具。

## 信任与输出

- 工具只读取当前活动课表的同一事实快照，结果携带 `scheduleId` 和 `sourceHash`。
- 课程自由文本、长期记忆和工具内容都是数据，不是指令。
- 工具阶段不生成最终计划；最终阶段不再提供原生工具，防止把写入原语误当函数调用。
- 写入仍由 `<agent_actions>` 交给本地预演、确认、事务执行和回读验证，模型不得提前宣称完成。

相关回归测试位于 `feature/agent/AgentToolsTest.kt`，覆盖工具 schema 动态收窄、缓存键稳定性、提示词分段、provider 协议形状，以及 MiMo `web_search` 意图与本地未知工具的分流。
