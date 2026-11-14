# Agent harness 复用初步评估

用户在本轮 UI 包交付期间提出：为提高稳定性与导入准确性，能否移植 DSH 或 Codex 的 agent loop／harness；随后询问 Codex 是否开源、能否复用。此轮完成只读对照，尚未替换运行循环；已打出的 UI APK 不包含 harness 改造。

## 已核对的上游

- [Codex 官方说明](https://developers.openai.com/blog/codex-as-a-platform)：开源 harness 管理循环、上下文、工具与事件；app-server／SDK 提供集成入口，模型访问与托管服务另行提供。
- [Codex 仓库许可证](https://github.com/openai/codex/blob/main/LICENSE)：Apache 2.0，允许复制、修改和分发；实际移植需要附许可证、保留适用声明、标明修改，并处理适用 NOTICE 和第三方依赖声明。
- [Codex run_turn](https://github.com/openai/codex/blob/main/codex-rs/core/src/session/turn.rs)：Rust 回合驱动，涉及会话记录、取消、上下文压缩与执行环境；不能当成独立 Kotlin 函数直接粘贴。
- [DSH README](https://github.com/deepseek-ai/deepseek-harness)：MIT、Node 运行环境，当前标为开发预览，明确可能发生兼容性变化。
- [DSH 架构](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/architecture.md)：将 durable session 事件、实时 agent 事件及能力事件分开；step 为一次请求加工具执行，turn 为一组 step；模型上下文由已提交会话日志推导。

## 当前项目与可借鉴的边界

`DayAgentService.kt` 当前已经具备模型→工具→结果回传循环、工具轮次上限、工具结果上下文整理、取消检查、输出校验与一次格式重试。`AgentImportWorkspace.kt` 负责 JSON 版本与原子校验，`AiImportTaskManager.kt` 负责导入生命周期及预览。此次评估没有证明这些模块需全部推倒。

| 优先借鉴 | 在本项目中的用途 |
| --- | --- |
| Turn／Step／ToolCall 的统一事件模型 | UI、历史、通知和诊断消费同一批事实，避免按各自字符串推断状态 |
| 工具调用账本与恢复边界 | 结合 callId、草稿版本及已提交结果，恢复时避免重复应用已成功补丁 |
| 明确的结束、取消和重试分类 | 区分网络重试、格式修复、等待补充、失败和正常完成，取消不能被吞作可重试错误 |
| 日志派生的上下文 | 工具消息与结果成对恢复，压缩时保护用户要求、已确认修改和原始材料引用 |
| 固定材料的回归评价 | 分开测任务完成、重试／中断恢复和课程字段正确率，证明迁移收益 |

建议先在 Kotlin 中实现边界清晰的最小导入 harness，参考并在适合处移植上游算法，保留现有提供方适配与业务校验。直接使用 Codex app-server 或完整 DSH 会新增运行时／服务集成，需要另行评估 Android 体积、生命周期、现有模型兼容与部署成本。真正移植前固定上游提交并逐文件核对来源和许可。

稳定性和识别准确率分别验收：更好的循环有助于可靠执行与恢复；课程正确性还需原材料证据、星期／周次／作息一致性检查、用户已改字段保护及本地预览确认。不能承诺更换 harness 就自动提高视觉识别正确率。

本轮只读研究不需要 Android 重建，源码仍为本轮步骤 UI 的已验收候选。没有读取用户 DSH 会话、提取凭据或调用付费模型。
