# Day Agent 工具与提示词运行规范

本文记录今日助手的请求阶段、工具生命周期和提示词边界，目标是减少无意义的模型轮次和重复上下文，同时保持模型自主选择工具、组合操作与迁移自检的能力。

## 请求阶段与连续追问

1. `ChatSystem`：只放稳定身份、任务边界、事实/信任边界和高层规划约束。
2. `TaskStage`：每轮提供可用工具与简短功能目录；模型按需读取事实和 `GET_ACTION_GUIDE` 分区说明，再通过 `PROPOSE_ACTIONS` 提交完整计划。首次会话与缓存追问使用同一流程。
3. `FinalAnswerStage`：轮次用尽、重复读取不再取得新事实或输出修复仍失败时的收敛入口，只基于已有事实回答；不足时说明缺口，不编造计划。

任务循环最多 6 轮，为组合任务留下连续依赖查询的空间；独立读取同轮并行提出，事实充分立即完成，重复相同查询且没有新事实时结束。空正文、旧占位符或内部协议允许一次带原生工具的重试，不能把格式错误当成“查询已完成”。MiMo `web_search` 的一次强制重试属于同一供应商搜索请求，不占用本地工具轮次。

不预设任务属于今日课程，也不强制读取概览。每个回合只预载核对版本后仍有效的缓存事实，其余查询由模型自主选择。`GET_CURRENT_OVERVIEW` 和周课表的课程行包含真实 ID、完整可编辑字段、实际日期以及原教学周，任一入口定位的对象都可用于准备修改。缓存只保存读取结果，不保存模型决策或执行授权。实际 token 和延迟以供应商 usage 与遥测为准。

## 工具生命周期

| 类型 | 工具 | 本轮策略 |
| --- | --- | --- |
| 一次性事实快照 | `GET_CURRENT_OVERVIEW`、`GET_WEEK_SCHEDULE`、`GET_SEMESTER_SCHEDULE`、`GET_PERIODS`、`GET_SETTINGS`、`GET_SCHEDULE_ADJUSTMENTS`、`GET_SCHEDULES` | 成功调用后从下一轮工具 schema 移除，结果继续保留在上下文 |
| 可重复检索 | `SEARCH_COURSES` | 一次组合关键词、记录 ID、精确名称/教师/地点、星期、节次与教学周；同参数结果走本轮缓存 |
| 功能说明 | `GET_ACTION_GUIDE` | 按课程、作息、设置、调休、多课表、导航六个分区加载格式；可与必要事实同轮查询 |
| 计划自检 | `PROPOSE_ACTIONS` | 单独提交完整数组；失败返回字段与原因供模型修正，成功直接结束本轮并展示确认卡 |
| 低频写入 | `UPDATE_MEMORY` | 最多提供一次；仍受每日授权和频率 gate 控制 |
| 外部公开事实 | MiMo `web_search` | 仅官方支持端点提供，不替代本地课表事实 |

`GET_PERIODS` 是逐节精确时间的唯一来源；`GET_SETTINGS` 只输出设置键与结构化事实，并指向 `GET_PERIODS`，避免同一条计划依赖两份可能不同步的节次事实。`GET_SCHEDULE_ADJUSTMENTS` 提供调休整表，`GET_SCHEDULES` 提供多课表 ID/名称/当前使用状态。询问调休日期时直接使用调休结果或已核对版本的缓存回答；没有保存安排时说明未配置。只有明确要求进入页面时才提出导航，不能用 `OPEN_SETTINGS` 代替查询答案。

## 写入原语

### 生成、自检与修正

原生工具名及参数统一由 `AgentToolProtocol` 解码，两条传输都兼容字符串/对象参数、大小写、常见驼峰和函数命名空间。未知工具、无效 JSON、未知字段和缺少必要参数会形成对应调用的失败结果返回模型；不会丢失工具结果配对，也不会将坏参数当成空参数执行。明确动作类型与工具名的区别，避免模型把 `UPDATE_COURSE` 当作函数。

生成计划后，本地先检查 JSON/schema、真实对象、原始周次/节次、目标范围、重复和互斥操作，再预演完整结果。失败返回 `local_plan_validation` JSON，含从零开始的 `actionIndex`（能定位时）、`field`、`message` 和 `executed=false`；模型在同一回合检查刚生成的内容并修正完整计划，不要求用户手动修 JSON。正文中的旧 `<agent_actions>` 计划也必须经过这一步，不能绕过原生工具自检。

修正受最多六轮任务循环约束；最终收敛输出失败时再允许一次修正，仍失败则只说明未保存，不生成确认按钮。相同调用（包括失败调用）复用结果，不反复执行；参数真正修正后允许再次校验。预览中的时间冲突作为可见警告，不擅自删除课程。自检通过不代表执行成功：用户确认后仍走事务、过期检查和数据库回读。

### 可组合操作

`<agent_actions>` 支持的 type：

- 课程：`ADD_COURSE`、`UPDATE_COURSE`（局部补丁）、`REPLACE_COURSE`（完整字段替换）、`DELETE_COURSE`
- 导航：`OPEN_SETTINGS`、`OPEN_IMPORT`
- 设置：`SET_SETTING`、`SET_PERIOD_SETTINGS`、`SET_ADJUSTMENTS`（调休整表替换）
- 课表管理：`CREATE_SCHEDULE`、`ACTIVATE_SCHEDULE`、`DELETE_SCHEDULE`

课程修改语义：`UPDATE_COURSE` 省略字段即保留，清空 `teacher`/`location`/`note`/自定义时间必须在 `clearFields` 中显式列出（白名单 `teacher`、`location`、`note`、`customTime`，大小写不敏感），`note` 仍可用空串清空。`REPLACE_COURSE` 把补丁当作完整新状态，未提供的 `teacher`/`location`/`note` 会被置空；自定义时间仍需成对出现，`customColorArgb` 为 null 表示沿用原颜色。

`SET_ADJUSTMENTS` 是整表替换，校验复用持久层的 `encodeScheduleAdjustments`（同日唯一、sourceDate≠date、label≤80、条目上限），因此 Agent 无法写入设置界面会拒绝的调休表。

课程与设置可在同一数组组合：相邻的课程/设置动作在一个 Room 事务内保存，节次拓扑与显式课程迁移不会重复执行。导航、导入和课表管理可接在同一工作流中按顺序执行；失败即停止后续步骤，结果保留已经完成的步骤，不宣称跨页面工作流整体回滚。课表管理通过数据库回读确认；删除不存在的课表或最后一个课表返回可读错误。`CREATE_SCHEDULE` 不自动切换，也不能凭空引用尚未返回的新 ID。切换后必须读取目标课表事实才能修改；计划携带本地 `sourceScheduleId`，旧课表计划不能写入新课表。导航读取执行时的最新配置。

课程作用范围：`CURRENT_WEEK` 仅当前有效教学周，显式传入其他周会被拒绝；`SELECTED_WEEKS` 用 `sourceWeeks` 指定实际存在的原周次，用 `course.weeks` 指定目标周次，省略目标周次表示留在所选周；`ALL_WEEKS` 修改整个记录。UPDATE/DELETE 可用 `sourcePeriods` 选择原课程的部分节次，同一记录允许原周次×节次不相交的多个动作。`AgentCourseFragments` 为预演和数据库共用转换：保留未选中的周次及节次，逐节自定义时间随片段保留；只有整体自定义时间、没有逐节铃声时不能猜测拆分时间。选出周片段默认使用 `ALL` 奇偶规则，避免单周移至双周后隐藏。延长学期和迁移至新增周次、节次结构和课程迁移均可组成同一计划。

精确定位后，一条 `UPDATE_COURSE` 可同时修改多个字段，未提及字段保留；完整替换使用 `REPLACE_COURSE`。字段修改范围与生效周次独立：单次安排按实际 teachingWeek 限定，整个记录使用 ALL_WEEKS。相同 ID、原周次及节次的多项变化合并为一条，不逐字段搜索、不删除重建。确认卡逐条展示字段前后差异及生效范围，批量也展示每一项。操作标签使用蓝色移动、红色删除、黄色重命名、绿色新增、紫色其他修改，并保留文字说明。设置、作息、调休和课表管理也有明确预览，已执行按钮禁用。

课程搜索的精确条件同时满足；教学周筛选遵守单双周。缺少条件或条件无效返回失败供模型修正，不扩大查询。超过 24 条时明确给出总数、截断状态与完整学期读取入口，批量目标不能只取前 24 条。原 query 字符串调用保持兼容；严格 schema 的未使用条件以 null 表示，两个传输解析器均移除 null 条件。

模型输出按整份计划校验，不静默丢弃其中的无效动作。截断的数组或包装对象直接返回解析错误，不从中提取个别完整动作组成半份计划；无标签正文也遵守此规则。预演与数据库执行共用周次语义，执行后以完整课程内容及有效周次比较，允许兼容片段合并改变物理 ID，同时检查无关课程未丢失。设置回滚/撤销检查提交后的状态，已有后续修改时拒绝覆盖。

设置键存在相互覆盖的组合（`NOTIFICATION_MODE`↔`REALTIME_ACTIVITY`、`COURSE_CARD_COLOR`/`COURSE_CARD_PALETTE`↔`COURSE_CARD_COLOR_MODE`），同组只保留一个，同一键也不得重复。`FOLLOW_SYSTEM_DARK_MODE` 与 `DARK_MODE` 是独立开关，可以组合。图标风格/模式（`APP_ICON_STYLE`、`APP_ICON_MODE`）存于独立 SharedPreferences，走专用写入与回读校验。`GET_SETTINGS` 使用共享表头与紧凑行输出。

相同工具和规范化参数在同一用户回合再次出现时，不再重复序列化完整结果，而是返回事实版本一致的复用提示。每个 provider 仍收到与自己 `call_id` 对应的合法结果，Chat Completions 与 Responses 两条链路遵守相同策略。

`AgentToolFactCache` 在进程内复用成功读取，最多保留两个课表、每课表 12 项/160000 字符，单项超过 100000 字符不缓存；五分钟到期，当前状态概览在分钟变化时失效。版本包括全学期课程、配置、设置快照、节次、作息方案、日期、天气和课表列表。发送前读取数据库新快照，变更、切换或过期后重新读取；失败结果和记忆写入不缓存。缓存内容仍标记为不可信数据，不提升为指令。

跨回合预载总量最多12000字符、单项最多6000字符；较大的有效快照仍在本地缓存，只在模型实际查询时发送。分区说明和计划不放入跨回合事实缓存。功能目录代替常驻的完整操作说明，原生计划通过后直接返回，省去再生成同一份最终计划的请求；具体 token/延迟收益需真实模型遥测验证。

Chat Completions 只保留最近一轮完整的 assistant/tool 协议配对；更早且已经闭合的工具轮压成带 `sourceHash` 的结构化事实块，删除重复规划说明，但不产生孤立的 tool 消息。学期课表共享课表与学期元信息，课程按紧凑行输出，空地点、空教师和默认 `ALL` 单双周不重复占用上下文。

Responses 使用 `store=false` 时继续完整重放模型返回的 output items，包括 opaque reasoning items，空输出重试也保留这些项目；不能用 Chat 路径的压缩方式删除它们。每轮都可能承担完整规划与回答，因此使用用户当前 reasoning effort。

## 共享传输层

导入与助手的 HTTP/SSE 骨架统一在 `feature/importing/AiTransport.kt`（连接构造、鉴权头、超时、状态码/错误格式化、`data:` 行循环）。`AiHttp.kt` 保留 `AiImportHttpTrace` 埋点与 `AiServiceResponseException`；agent 侧的 `DayAgentChatTransport`/`AgentOpenAiResponses` 保留各自的 temperature、`store=false`、`tool_choice` 与 `IllegalStateException`。两边差异（reasoning effort、strict 函数、`response_format`）因此保持不变，wire 协议无任何变化。

MiMo 联网搜索属于供应商服务端插件，不属于 SleepDown 本地函数工具。只有官方 MiMo 端点与支持的模型可以启用该类型；若模型先返回函数形态的 `web_search` 意图，解析层将其识别为供应商搜索请求而不是“未知本地工具”，随后最多重试一次并把同一请求的 `force_search` 设为 `true`。只有响应中的 `annotations`、`usage.web_search_usage` 或搜索后的正文才算真实搜索证据。有效正文可直接作为本轮答复；若还带有本地工具调用则随模型消息继续规划，不能提升为系统指令或替代本地课表事实。其他未知工具名以失败结果回传模型修正，不能因为兼容 MiMo 而被吞掉。

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
- `TaskStage` 从第一轮起允许查询、直接回答及输出待确认计划；最终收敛阶段不再提供原生工具。课程数据库写入原语不能直接调用，`PROPOSE_ACTIONS` 只做自检和生成预览。
- 写入仍由 `<agent_actions>` 交给本地预演、确认、事务执行和回读验证，模型不得提前宣称完成。

## 导入与能力边界

自然语言可产生 `OPEN_IMPORT`，携带用户提供的课表文本；对话图片从本地受控附件目录交接，不接受模型构造的本地路径。文本和图片进入既有 AI 导入入口，继续经过 ImportDraft、本地校验、预览、用户确认后才写入。没有单独增加绕过校验的导入通道。

首次导入和后续草稿对话均读取已启用的助手记忆，作为独立 `user_memory_context` 数据；格式修复不使用记忆。草稿工作区不开放记忆写入。普通助手继续支持 `UPDATE_MEMORY` 修改既有条目，提示词要求逐条核对原文、谨慎调整并保留未涉及内容，不用本轮摘要覆盖全部记忆。保存时核对请求开始时的旧文本并拒绝超长结果，避免并发覆盖和静默截断。

助手可直接修改课程、作息、调休、多课表和设置目录中的项目。壁纸/备份文件选择、教务登录、API 凭据、Android 系统权限等通过原页面完成；页面已打开不等于用户已完成授权、导入或恢复。能力目录是实际边界，不能声称能静默控制整个 App 的全部功能。

## 轻量遥测

每个用户回合结束时通过 `DayAgentMetrics` 记录决策轮数、每轮工具调用数、总请求数、工具结果字符数、输入/输出 token、缓存输入 token、reasoning token 和最终回答延迟。token 字段仅在供应商响应提供 usage 时累加，不落库，也不记录课程正文或用户问题。

相关回归测试位于 `feature/agent/AgentToolsTest.kt`、`AgentTaskLoopTest.kt`、`AgentToolProtocolTest.kt`、`AgentFineAdjustmentTest.kt` 等，覆盖组合条件定位、缓存失效、部分/完整修改、精细节次选择和传输续接。任务循环测试使用本地模拟 HTTP 服务，验证错误回传后修正、重复查询只执行一次、旧正文计划自检和修正上限，以及 reasoning 重放；不代表真实模型或实机界面验收。
