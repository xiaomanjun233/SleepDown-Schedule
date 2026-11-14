# AI 导入信息精简、原图续接与柔性反馈

## 范围与行为

用户要求减少导入页冗余信息，恢复浅蓝用户气泡，取消预览上方的“第 X 次／阶段”卡片，并在每轮继续对话时带上原图；随后明确历史记录也保留图片，做完打包，并追加输入框柔性反馈及日视图今日助手关闭末尾闪烁。

- 默认展示用户消息、模型公开进度、回答、需要回答的问题、错误和经过校验的课表预览。执行日志与推理摘要放入默认折叠的“处理详情”；取消重复的“AI”、摘要标题、“已完成”、重复实时状态及预览说明。
- 每份预览的“课表选项”提供从该课表继续、查看／编辑 JSON。旧预览选择与当前回复产生的结果分离，选择旧课表不会移动后续预览；继续下一轮时归档本轮真实最新结果，导入按钮始终绑定所在预览的不可变快照。
- 视觉模型的每轮请求携带全部可用原图一次，保留页面顺序；同轮工具循环继续使用同一份输入，历史对话不层层叠加图片。原始文字用于核对；提示明确要求保留之前修改，不因旧图与当前 JSON 不同而还原用户修改。文本模型仍可按 JSON 编辑，格式修复不附原图。

## 历史与备份

`AiImportHistoryImages` 将压缩图片存入应用私有历史目录，以所属记录和内容摘要命名，重复保存不会反复创建同图。文字 JSON 只保存引用，并为引用预留 2 MiB 限额中的预算；原子写入上下文后才更新索引。历史列表不读图片，打开详情／从通知恢复时只在 IO 线程加载对应记录的图片。

后续仅更新文字时保留图片引用；删除、到期及十条上限淘汰时一起清理。旧内联图片仍可读取，但旧版本已经丢弃的截图无法补回。

备份使用 V1 已有的 `ai_import_screenshot` 资源类型，关联 history stable ID，保留页号和 MIME；导出不携带本机路径。恢复文件按所属记录落地，读取时解析 asset ID；偏好提交不改写已准备的 context asset，支持资源准备和偏好应用重复执行。没有修改 Room、备份版本、API key 或远端附件状态。

## 输入与关闭交接

AI 导入输入框复用 `AgentInputLiquidCapsule`，接入既有玻璃弹性与凝光 `elasticTouchFeedback`。公共入口新增按内容增高选项，原调用方保持固定高度默认值；多行文本和附件提示仍参与输入框真实占位测量。

日视图源卡原生材质随源卡隐藏一起关闭，关闭末尾不在弹层还存在时提前显示源卡；完整退出后同帧恢复。弹层使用与源卡相同的连续圆角，避免最后一帧材质外壳和轮廓跳变。未进行手机 ROM 的视觉验收。

## 验证与交付

最终完整 `assembleGithubRelease` 与十类 63 项回归同轮通过，5m24s，0 失败／错误／跳过，192 个 Gradle 任务。保留 R8、资源压缩、lintVital、默认金标 true 和 Release 签名；架构检查 606 个生产文件、0 违规依赖，`git diff --check` 通过。日志：`tmp/ai-import-history-feedback-delivery.log`。

- `AgentImportSourceImagesTest` 用真实 Agent 请求链路验证 Chat／Responses 两轮分别修改、每轮从磁盘历史加载原图、同轮工具请求保留两张原图且不重复堆叠；文本模型和无图旧快照仍保持文字输入。
- `AiImportHistoryImagesTest` 验证大于 2 MiB 的原图独立保存、元数据更新保留图片、目录引用边界、删除和到期清理，以及备份 codec、资源落地、偏好恢复、重复执行与恢复后的再次保存。
- 真实 Compose 页面验证技术日志默认折叠、公开进度保留、取消阶段卡片、短页面与跨轮次的预览顺序、选择旧课表后的后续结果保留，以及旧快照导入。贴底／键盘回归、工作区原子校验、历史防过期覆盖与备份引用回归同时通过。

测试使用本机模拟接口及 Robolectric 私有目录，不调用付费模型或真实账号，不向报告写入用户图片。前序测试中的括号／Robolectric API 调用、无实质修改的第二轮样例及短页面顺序断言已修正；最终结果覆盖最新全部源码。输入框柔性反馈与今日助手关闭交接已进入 APK，未进行手机 ROM 视觉验收。

```powershell
$env:JAVA_HOME='D:\Android studio\JDK'
.\scripts\Invoke-AndroidBuild.ps1 assembleGithubRelease testGithubDebugUnitTest --tests '*AgentImportSourceImagesTest' --tests '*AgentImportRuntimeTest' --tests '*AgentImportWorkspaceTest' --tests '*AiImportConversationPreviewUiTest' --tests '*AiImportConversationTest' --tests '*AiImportTranscriptTest' --tests '*AiImportHistoryImagesTest' --tests '*AiImportCheckpointHistoryTest' --tests '*AiImportCheckpointTest' --tests '*BackupExportImportPlanTest' '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8' --console=plain --no-parallel --max-workers=1
```

交付 APK 为 `com.xiaomanjun.sleepdownschedule`，`1.2.8_beta2 / 36`，7,774,531 字节；SHA-256 `af9592df816d4320d05d8ff7d59ffd0b9b4f6b6451903f2d3d276ce2e3199246`。V2 签名验证通过，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a` 与原候选一致。R8 映射包含新历史图片实现。本地保全：`tmp/review-apks/SleepDown-1.2.8-beta2-ai-history-feedback-20261101-af9592df.apk`。

已上传既有“SleepDown 测试包”目录：[下载本轮 APK](https://drive.google.com/file/d/1HYEvlCoA0lt7_74EMB8SW0tHRxd3np9q/view?usp=drivesdk)。已回读文件名、大小、父目录；没有修改共享权限。插件不返回远端哈希，不声称核验了远端 SHA-256。没有安装、公开发布、提交或 Git 推送。
