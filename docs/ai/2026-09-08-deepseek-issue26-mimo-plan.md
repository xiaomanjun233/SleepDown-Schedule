# DeepSeek issue 26 与托管 MiMo Token Plan

## 证据

- GitHub issue [26](https://github.com/xiaomanjun233/SleepDown-Schedule/issues/26) 的维护者评论明确记录 `400 Thinking mode does not support this tool_choice`。当前源码首次导入和复核第二轮均强制指定函数，Chat 与 Responses 均存在。
- [DeepSeek 官方兼容说明](https://api-docs.deepseek.com/quick_start/agent_integrations/oh_my_pi/) 指出 Chat 思考模式不接受 tool_choice，并要求工具历史保留 reasoning_content。现有复核已原样回传 assistantMessage，本次不改动它。
- [MiMo Token Plan 官方接入文档](https://mimo.mi.com/docs/zh-CN/tokenplan/Token%20Plan/quick-access) 列出 cn/sgp/ams，托管配置的 api-key 已正确。用户提供 DNS/TCP/HTTPS 成功但 JSON 非数组被误报网络失败的诊断；这支持调查响应解析而非网络改动。官方 [Chat 文档](https://mimo.mi.com/docs/zh-CN/api/chat/openai-api) 的示例包含 `tool_calls:null`；当前流式解析对此直接 `.jsonArray` 会抛异常。没有原始响应及对应 APK 的精确混淆表，不能单凭 pt1 确认具体字段。
- 用户进一步要求调用 Responses，且明确协议、地址、模型全部由后端管理平台控制。[Responses 官方文档](https://mimo.mi.com/docs/zh-CN/api/chat/responses) 限定 reasoning.effort 为 none/low/medium/high，tool_choice 为 auto，终态包括 response.completed 和 response.incomplete。

## 修改

1. `AiToolChoicePolicy.kt`：DeepSeek 思考模式下，Chat 不发送 tool_choice（有 tools 时默认为 auto），Responses 使用 auto；关闭思考及其他供应商保留原指定函数行为。`AiProviders.kt` 首次导入和复核第二轮共用该策略，复核首轮 Chat 同样省略不兼容字段。保留工具 schema、结果解析、预览及用户确认入库。
2. `MimoRequestPolicy.kt`：托管服务只对精确匹配的 MiMo 官方域名应用参数兼容规则；cn/sgp/ams 不跨区域替换。最终实现不追加后缀、不转换 Chat/Responses 地址、不替换托管模型名，后端密文及解密关联数据不变。`SleepDownRemoteConfig.kt`、`AiModels.kt` 本轮无最终差异。
3. `AiProviders.kt`、`AiImport.kt` 让托管 MiMo 使用官方 Chat 输出字段，Responses 使用合法推理档位、auto 工具选择；今日助手 `AgentOpenAiResponses.kt` 同步推理档位映射。保留后端的 endpoint_style 决定实际协议。无后端配置写入、凭据获取或付费调用。
4. `AiHttp.kt` 区分缺失/null 与错误类型，流式 tool_calls:null/usage-only 分片不抛强制转换异常，错误类型仍明确报响应错误。Responses 的可空 summary/content 正确处理，incomplete 保留真实终态。`AiScheduleTools.kt` 处理普通回答的空工具数组，允许已有正文解析路径继续。格式错误不再一概归为无法连接。

已收到用户错误并修复能由官方空值样例复现的解析缺陷；实际付费请求未复测。用户需要在后端设置完整 Responses 地址及对应 endpoint_style，客户端遵从配置，不自行改写。若返回服务端错误，继续依据实际响应定位，不自动切换域名或密钥。

## 验证

新增 `AiManagedProtocolRegressionTest` 的 10 项用例，覆盖区域地址与后端协议保持、代理不改写、伪造域名不识别、两种协议/思考开关工具选择、空数组字段、工具参数分片拼接、Responses 合法推理档位和 incomplete 终态。与 8 项既有归一化及 36 项玻璃测试共同通过，共 54 项、0 失败；使用 `tmp/ai-managed-tests.init.gradle` 筛选，非全仓测试。`compileGithubReleaseKotlin` 通过。未实机调用模型，未公开提交 issue 评论。
