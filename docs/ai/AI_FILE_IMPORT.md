# AI 文件导入与服务能力

更新：2026-08-25

## 统一处理顺序

1. ICS 始终走独立的本地日历解析，不调用 AI。
2. CSV、TSV、TXT、Markdown、JSON、XML、HTML、XLSX、DOCX、PPTX、ODS 在设备本地提取为文字，只把最多 60,000 字符的提取结果交给当前文本模型。
3. PDF 必须先尝试本地文字提取；提取结果满足课表文本判定时只发送文字。
4. PDF 文字不足时才渲染为有序 JPEG 页面，并且只允许发送给当前明确支持图片的模型。
5. 普通图片在本地压缩后以内联 base64 图片发送。应用文件上限为 20 MiB，低于当前已接入服务的内联请求限制。
6. 旧版 XLS、DOC、PPT 等无法可靠本地解析的二进制格式，只在明确支持 `input_file` 的 Responses 路线使用原生文件输入，否则提示用户另存为新版格式或 CSV。

Office/ODS 解包只读取与正文有关的 XML，并限制 ZIP 条目数、单条目展开大小和总展开大小，防止异常压缩包造成内存膨胀。文件确认前不会发起模型请求。

## 教务网页的优先路线（2026-09-27）

网页先在本机检查通用教务所需的 DOM / JS 特征。明确匹配正方、青果、URP、超星时，可直接运行已有拾光工具；有歧义才提供发送少量结构特征的模型识别选项。模型只返回受本地候选限制的路由 JSON，不能返回待执行脚本。执行前复查文档标识，仍经原有导入预览确认后写入。

不适合通用工具时建议文本解析；识屏保留为用户选择的后备。文本抓取按完整内容块去重，保留空单元格及合并行列，不去重真实课程行。实现、限制和验证见[本轮记录](../reports/2026-09-27-badges-ai-edu-routing.md)。

## 历史详情入口

手动导入页的“最近导入”快捷卡与 AI 历史列表的条目统一打开 `AiImportHistoryDetailActivity`，并共同声明 `AiHistoryToDetail` 锚定路线。详情页只保留一套 `AiEduImportProgressPage` 实现；返回分别落回真实快捷卡或历史行，稳定全屏后释放临时 clip 与离屏合成。不得再在手动导入 Activity 内嵌第二套详情 Overlay。

## 对话式导入（2026-10-08）

模型输出在对话底部持续显示，完整结果通过本地校验后，摘要和执行步骤自动折叠进“处理过程”，保留完整课表预览和新建/覆盖操作。用户向上阅读时不强制拉回底部。流式输出单独订阅，每秒最多更新十次，避免每个 token 都重组整页。

支持原生摘要的接口沿用原生流；没有原生摘要时，提示模型在普通文本里发送独立的 `<import_progress>…</import_progress>` 阶段摘要，随后提交 `IMPORT_SCHEDULE`。显示只取已完成的摘要块，最终课程 JSON 单独解析。摘要仅报告已确认的信息、当前阶段和待核对项，不要求内部推理，也不由客户端定时编造进度。实际摘要频率仍取决于模型。仅返回摘要的一轮允许续接一次，仍未提交数据则明确报错，不把摘要当作课程 JSON 进入修复。

初次解析提供 `ASK_IMPORT_DETAILS` 工具：材料缺少星期、节次、周次等排课必需信息时，最多提出三个问题并等待用户回答；缺教师、地点不阻止导入。提问优先于同一响应中的推测课表，不写数据库，也不触发格式修复。

解析途中可以停止，或直接发送补充要求。原材料由应用进程保留，续接时取消旧请求并携带累计要求重新解析；每次尝试使用独立任务标识和取消控制，旧响应不得更新当前进度或预览。此保留仅限当前进程，不承诺杀进程后恢复未完成材料。

用户确认导入后才写库；新建与覆盖成功均切到导入课表的周视图，再打开已有快速设置，引导确认开学日期/当前周。保存失败保留可重试的确认入口，不进入成功引导。格式修复仍只处理前一轮 JSON，沿用原服务，最多三次，不重新上传材料。

## 当前服务能力矩阵

| 服务 | 图片 | 通用文档直传 | SleepDown 路线 |
| --- | --- | --- | --- |
| OpenAI Responses | 支持 | 支持 `input_file` | 可本地提取的格式优先只发文字；其余已知文档格式可走原生文件输入 |
| DeepSeek V4 Flash / Pro | 不支持图片 | Responses 不支持通用文件输入 | 文档本地转文字；扫描 PDF 会提示切换视觉模型 |
| DeepSeek V4 Flash Vision Exp | 支持 JPEG/PNG/GIF/WebP；Chat 与 Responses 均支持 | Files API 可保存图片，但 Responses 仍不接收通用文档 | 小于应用上限的图片直接内联；PDF 文字不足后转图 |
| MiMo / 每日免费 AI | 按具体视觉模型 | 当前接入协议不假设通用文件输入 | 文档本地转文字；图片按模型能力发送 |
| 自定义兼容接口 | 由用户声明 | 仅显式 Responses 文件模式才假定支持 | 默认本地转文字，不把“兼容 OpenAI”误判为支持文件 |
| 百炼、Kimi 等独立文件解析 API | 官方有上传/解析流程 | 需要独立上传、状态管理和远端文件清理 | 当前统一入口优先本地提取，避免产生未清理的服务端文件 |

## 官方依据

- DeepSeek 模型与价格：<https://api-docs.deepseek.com/zh-cn/quick_start/pricing/>
- DeepSeek 图像理解：<https://api-docs.deepseek.com/zh-cn/guides/vision/>
- DeepSeek Responses API：<https://api-docs.deepseek.com/zh-cn/guides/responses_api/>
- OpenAI Responses `input_file`：<https://platform.openai.com/docs/api-reference/responses>
- 阿里云百炼文件上传：<https://help.aliyun.com/zh/model-studio/upload-file-api>

注意：DeepSeek 官方文档在 2026-08-25 已加入 `deepseek-v4-flash-vision-exp`。旧缓存页面仍可能显示“Responses 不支持图片”，实现与验收以当天官方价格页、图像理解页及 Responses 页为准。
