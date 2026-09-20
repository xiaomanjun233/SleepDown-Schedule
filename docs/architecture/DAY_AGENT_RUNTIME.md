# Day Agent 工具与提示词运行规范

本文记录今日助手的请求阶段、工具生命周期和提示词边界，目标是减少无意义的模型轮次和重复上下文，同时保持模型自主选择工具、组合操作与迁移自检的能力。

## 请求阶段与连续追问

1. `ChatSystem`：只放稳定身份、任务边界、事实/信任边界和高层规划约束。
2. `ToolDecisionStage`：只负责本轮工具选择，要求把当前可判断且互相独立的读取放在同一响应并行发出。
3. `FinalAnswerStage`：工具阶段结束后才附带完整 `<agent_actions>` 写入协议和 JSON 示例。

普通本地工具决策最多进行 3 轮：第一轮并行读取独立事实，第二轮补充依赖事实，第三轮收敛并进入最终回答。若新一轮没有新增事实版本、查询或工具结果，立即结束循环。MiMo `web_search` 的一次强制重试属于同一供应商搜索请求，不占用本地工具轮次。

没有可复用事实时沿用上述阶段。连续追问存在版本有效的本地事实时，使用 `CachedFactsStage`，事实充分可在当前请求直接给出最终回答；缺少事实时仍可调用其余工具。该路径附带操作协议，允许直接产生待确认计划，避免额外的最终回答请求。缓存只保存读取结果，不保存模型决策或执行授权。实际 token 和延迟以供应商 usage 与遥测为准。

## 工具生命周期

| 类型 | 工具 | 本轮策略 |
| --- | --- | --- |
| 一次性事实快照 | `GET_CURRENT_OVERVIEW`、`GET_WEEK_SCHEDULE`、`GET_SEMESTER_SCHEDULE`、`GET_PERIODS`、`GET_SETTINGS`、`GET_SCHEDULE_ADJUSTMENTS`、`GET_SCHEDULES` | 成功调用后从下一轮工具 schema 移除，结果继续保留在上下文 |
| 可重复检索 | `SEARCH_COURSES` | 允许用不同关键词重复；同参数结果走本轮缓存 |
| 低频写入 | `UPDATE_MEMORY` | 最多提供一次；仍受每日授权和频率 gate 控制 |
| 外部公开事实 | MiMo `web_search` | 仅官方支持端点提供，不替代本地课表事实 |

`GET_PERIODS` 是逐节精确时间的唯一来源；`GET_SETTINGS` 只输出设置键与结构化事实，并指向 `GET_PERIODS`，避免同一条计划依赖两份可能不同步的节次事实。`GET_SCHEDULE_ADJUSTMENTS` 提供调休整表，`GET_SCHEDULES` 提供多课表 ID/名称/当前使用状态。询问调休日期时直接使用调休结果或已核对版本的缓存回答；没有保存安排时说明未配置。只有明确要求进入页面时才提出导航，不能用 `OPEN_SETTINGS` 代替查询答案。

## 写入原语

`<agent_actions>` 支持的 type：

- 课程：`ADD_COURSE`、`UPDATE_COURSE`（局部补丁）、`REPLACE_COURSE`（完整字段替换）、`DELETE_COURSE`
- 导航：`OPEN_SETTINGS`、`OPEN_IMPORT`
- 设置：`SET_SETTING`、`SET_PERIOD_SETTINGS`、`SET_ADJUSTMENTS`（调休整表替换）
- 课表管理：`CREATE_SCHEDULE`、`ACTIVATE_SCHEDULE`、`DELETE_SCHEDULE`

课程修改语义：`UPDATE_COURSE` 省略字段即保留，清空 `teacher`/`location`/`note`/自定义时间必须在 `clearFields` 中显式列出（白名单 `teacher`、`location`、`note`、`customTime`，大小写不敏感），`note` 仍可用空串清空。`REPLACE_COURSE` 把补丁当作完整新状态，未提供的 `teacher`/`location`/`note` 会被置空；自定义时间仍需成对出现，`customColorArgb` 为 null 表示沿用原颜色。

`SET_ADJUSTMENTS` 是整表替换，校验复用持久层的 `encodeScheduleAdjustments`（同日唯一、sourceDate≠date、label≤80、条目上限），因此 Agent 无法写入设置界面会拒绝的调休表。

课程与设置可在同一数组组合：相邻的课程/设置动作在一个 Room 事务内保存，节次拓扑与显式课程迁移不会重复执行。导航、导入和课表管理可接在同一工作流中按顺序执行；失败即停止后续步骤，结果保留已经完成的步骤，不宣称跨页面工作流整体回滚。课表管理通过数据库回读确认；删除不存在的课表或最后一个课表返回可读错误。`CREATE_SCHEDULE` 不自动切换，也不能凭空引用尚未返回的新 ID。切换后必须读取目标课表事实才能修改；计划携带本地 `sourceScheduleId`，旧课表计划不能写入新课表。导航读取执行时的最新配置。

课程作用范围：`CURRENT_WEEK` 仅当前有效教学周，显式传入其他周会被拒绝；`SELECTED_WEEKS` 用 `sourceWeeks` 指定实际存在的原周次，用 `course.weeks` 指定目标周次，省略目标周次表示留在所选周；`ALL_WEEKS` 修改整个记录。未选中的周次保留，选出片段默认使用 `ALL` 奇偶规则，避免单周移至双周后隐藏。同一记录允许多个原周次不相交的动作；重叠修改必须合并。延长学期和迁移至新增周次、节次结构和课程迁移均可组成同一计划。

模型输出按整份计划校验，不静默丢弃其中的无效动作。预演与数据库执行共用周次语义，执行后以完整课程内容及有效周次比较，允许兼容片段合并改变物理 ID，同时检查无关课程未丢失。设置回滚/撤销检查提交后的状态，已有后续修改时拒绝覆盖。

设置键存在相互覆盖的组合（`NOTIFICATION_MODE`↔`REALTIME_ACTIVITY`、`COURSE_CARD_COLOR`/`COURSE_CARD_PALETTE`↔`COURSE_CARD_COLOR_MODE`），同组只保留一个，同一键也不得重复。`FOLLOW_SYSTEM_DARK_MODE` 与 `DARK_MODE` 是独立开关，可以组合。图标风格/模式（`APP_ICON_STYLE`、`APP_ICON_MODE`）存于独立 SharedPreferences，走专用写入与回读校验。`GET_SETTINGS` 使用共享表头与紧凑行输出。

相同工具和规范化参数在同一用户回合再次出现时，不再重复序列化完整结果，而是返回事实版本一致的复用提示。每个 provider 仍收到与自己 `call_id` 对应的合法结果，Chat Completions 与 Responses 两条链路遵守相同策略。

`AgentToolFactCache` 在进程内复用成功读取，最多保留两个课表、每课表 12 项/160000 字符，单项超过 100000 字符不缓存；五分钟到期，当前状态概览在分钟变化时失效。版本包括全学期课程、配置、设置快照、节次、作息方案、日期、天气和课表列表。发送前读取数据库新快照，变更、切换或过期后重新读取；失败结果和记忆写入不缓存。缓存内容仍标记为不可信数据，不提升为指令。

Chat Completions 只保留最近一轮完整的 assistant/tool 协议配对；更早且已经闭合的工具轮压成带 `sourceHash` 的结构化事实块，删除重复规划说明，但不产生孤立的 tool 消息。学期课表共享课表与学期元信息，课程按紧凑行输出，空地点、空教师和默认 `ALL` 单双周不重复占用上下文。

Responses 使用 `store=false` 时继续完整重放模型返回的 output items，包括 opaque reasoning items；不能用 Chat 路径的压缩方式删除这些项目。Responses 工具决策采用供应商支持的 `minimal`，不支持时采用 `low`，最终回答继续使用用户当前 reasoning effort。

## 共享传输层

导入与助手的 HTTP/SSE 骨架统一在 `feature/importing/AiTransport.kt`（连接构造、鉴权头、超时、状态码/错误格式化、`data:` 行循环）。`AiHttp.kt` 保留 `AiImportHttpTrace` 埋点与 `AiServiceResponseException`；agent 侧的 `DayAgentChatTransport`/`AgentOpenAiResponses` 保留各自的 temperature、`store=false`、`tool_choice` 与 `IllegalStateException`。两边差异（reasoning effort、strict 函数、`response_format`）因此保持不变，wire 协议无任何变化。

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
- 普通工具阶段不生成最终计划；最终阶段不再提供原生工具。连续追问的 `CachedFactsStage` 允许直接回答及输出待确认计划，但写入原语仍不能作为函数调用。
- 写入仍由 `<agent_actions>` 交给本地预演、确认、事务执行和回读验证，模型不得提前宣称完成。

## 导入与能力边界

自然语言可产生 `OPEN_IMPORT`，携带用户提供的课表文本；对话图片从本地受控附件目录交接，不接受模型构造的本地路径。文本和图片进入既有 AI 导入入口，继续经过 ImportDraft、本地校验、预览、用户确认后才写入。没有单独增加绕过校验的导入通道。

助手可直接修改课程、作息、调休、多课表和设置目录中的项目。壁纸/备份文件选择、教务登录、API 凭据、Android 系统权限等通过原页面完成；页面已打开不等于用户已完成授权、导入或恢复。能力目录是实际边界，不能声称能静默控制整个 App 的全部功能。

## 轻量遥测

每个用户回合结束时通过 `DayAgentMetrics` 记录决策轮数、每轮工具调用数、总请求数、工具结果字符数、输入/输出 token、缓存输入 token、reasoning token 和最终回答延迟。token 字段仅在供应商响应提供 usage 时累加，不落库，也不记录课程正文或用户问题。

相关回归测试位于 `feature/agent/AgentToolsTest.kt`，覆盖工具 schema 动态收窄、缓存键稳定性、提示词分段、provider 协议形状，以及 MiMo `web_search` 意图与本地未知工具的分流。
