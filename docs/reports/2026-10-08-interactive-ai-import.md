# AI 导入流式对话与导入后引导

## 本轮行为

- 模型输出置于对话底部并跟随新内容；向上拖动阅读时暂停跟随。输出单独订阅，最多每秒更新十次。
- 完整课表经过本地校验后，摘要和执行步骤自动折叠成“处理过程”，保留完整预览、新建与覆盖操作，过程可再次展开。
- 没有原生推理摘要时，要求模型按识别版面、提取课程、核对节次/周次等阶段返回简短公开摘要；长材料按页或约五门课程补充。只有完整的 `<import_progress>` 块进入进度展示，课程工具参数独立解析。实际返回频率由模型决定。
- 缺少排课必需信息时使用 `ASK_IMPORT_DETAILS`，最多三个问题；教师、教室缺失不打断。问题优先于同一响应中的推测课表，不触发 JSON 修复，不写库。
- 运行时可停止解析，也可直接补充要求并重新解析。保留当前进程内的原材料；取消旧连接与任务，每次尝试使用独立任务 ID。进度与有效预览在同一锁内检查任务身份，拒绝旧请求迟到的结果。
- 新建或覆盖保存成功均打开导入课表的周视图，并进入已有快速设置，确认开学日期/当前周。保存失败回到可重试的确认入口。保存请求保持一次性消费，防止 Activity 重建重复建表。

## 主要实现

- `feature/importing/AiImportSummaries.kt`：明确标记的阶段摘要、用户补充、最多一次仅摘要续接。不会把摘要误送 JSON repair。
- `feature/importing/AiImportInteraction.kt`：连接取消、用户提问工具、问题优先级。
- `AiHttp.kt`、`AiProviders.kt`：Chat/Responses 流式摘要兼容、工具选择、当前托管 GPT 模型的 `reasoning.summary=auto`。MiMo/DeepSeek 不附加该摘要字段。
- `AiImportTaskManager.kt`、`AiEduImportProgressSession.kt`：暂停/继续状态交接；每个任务独立持有 wakelock；修复阶段复用同一取消控制，仍不重新上传原材料。
- `progress/AiEduImportProgressUi.kt`、`AiImportReasoningPanel.kt`：底部输出、最终过程折叠、补充要求输入、任务标识恢复。
- `app/ui/ScheduleAppUi.kt`、`app/state/ScheduleViewModel.kt`：导入成功导航与已有开学周设置入口、保存错误提示。

保留 `AI → ImportDraft → 本地校验 → Preview → 用户确认 → 数据库`。没有数据库或已发布导入格式变更。未完成材料只在当前进程中保留，杀进程后不承诺恢复。

## 验证

定向测试覆盖分片摘要、工具 JSON 隔离、原生摘要优先、等长流式替换、Chat/Responses 两种仅摘要续接、最多两次模型响应、追问优先、取消连接及旧任务写入隔离，以及已有服务协议和最多三次 JSON 修复。

首轮测试发现仅摘要续接中的 `Map.put()` 返回旧值为 null，导致 Elvis 分支又删除了新写入的 `tool_choice`。已改为明确的 if/else，避免续接参数丢失。

当前云配置版本 25，用虚构材料“高等数学，周一”进行 Responses SSE 验证，未发送用户文件：HTTP 200、completed；第二次探测约 3.58 秒开始返回文本、6.20 秒完成，收到一个带标记的摘要块及 `ASK_IMPORT_DETAILS` 的两个问题。返回时序仅为这条合成请求的观察值，不代表长文件处理速度或固定摘要周期。凭据仅在内存解密使用，未记录原文。

`testGithubDebugUnitTest` 的五组定向测试共 **33 项通过，0 失败、0 错误**：`AiInteractiveImportTest`（11）、`AiLiveReasoningTest`（3）、`AiManagedProtocolRegressionTest`（13）、`AiEduImportProgressSessionTest`（3）、`AiImportRepairManagerTest`（3）。

`assembleGithubRelease` 成功，包含 Kotlin、R8、资源压缩、lintVital 与签名；与上述定向测试同一次顺序构建，总耗时 10m 46s。

- APK：`app/build/outputs/apk/github/release/app-github-release.apk`，7,025,763 bytes。
- SHA-256：`1B8C2F06192C80B34F8D1C3FFF6EA313443B1B63085B74A373FD8979A4DBE764`。
- 设备：PLJ110，Android 17；项目 SDK ADB 5038，无线端点按当次 mDNS 发现。
- `install -r` 返回 `Success`；包 `com.xiaomanjun.sleepdownschedule`，`1.2.7_beta3` / 34，设备 `lastUpdateTime=2026-10-08 01:09:01`。
- 未自动启动应用。用户随后提及玻璃错位，但确认暂未复现并要求不再处理，本轮未因此修改玻璃实现。

源码提交：`5bd188a`（流式对话、摘要、追问与暂停）、`33d3749`（导入后导航及开学周设置）。

## 范围与限制

未自动启动手机应用，也未替用户导入或覆盖真实课表。对话自动跟随、手动上翻、实际文件追问、暂停补充及导入后的页面衔接仍需实机体验确认。第三方模型可能忽略阶段摘要提示；客户端不生成虚假的定时进度。

接口依据：[OpenAI reasoning](https://developers.openai.com/api/docs/guides/reasoning)、[Responses streaming events](https://developers.openai.com/api/reference/resources/responses/streaming-events)。
