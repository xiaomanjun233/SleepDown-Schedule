# AI 导入状态排版与连续消息

用户反馈实时状态的 `**` 未渲染、中间信息无法留在下方、正文／状态／工具字层级不清、等待读秒应改为总导入用时、工具文字缩进不一致。

根因：实时状态使用纯 `Text`；普通助手正文只在带 `import_progress` 标签时进入流式记录；报告保存与历史读取将内容压为 180 字单行；UI 把执行报告统一抽走到末尾。计时依据最后一次网络／工具活动，收到新活动便重置。

修改：

- 实时状态复用 Markdown 渲染。公开模型报告保留段落、加粗与换行，单条上限 4096 字符；执行文字仍保留 180 字符限额，原总历史预算不变。
- Chat 普通公开段落在空行或消息完成时入记录；显式公开摘要通道的完整段落也保存下来，Responses 保留消息／摘要分段边界，完成回放去重。标签摘要沿用原处理；课程 JSON、工具参数、私有标签与不透明字段继续过滤，Responses 的原始 reasoning_text 不进入公开记录。
- 对话顺序保留模型段落与执行组；连续执行事件就地分组，运行中的最新组默认展开，完成后默认折叠。历史也按相同顺序恢复，不重复铺陈旧步骤。
- 正文 16sp／26sp，状态 13sp／20sp，工具 12sp／18sp 等宽。状态和工具共用 24dp 标记／文字间距；预览元数据维持 14sp。实时 Markdown 增加局部 streaming 参数，未闭合的加粗／行内代码立即按其样式呈现，不露出临时标记；其他页面默认渲染保持原值。
- 新增任务级单调时钟起点，首次响应、工具往返与回调重绑不重置；叶节点每秒显示“正在导入中”的累计用时。历史任务不伪装为实时导入。

最终七类 77 项定向回归通过（2m56s，0 失败／错误／跳过），日志 `tmp/ai-import-status-regression-20261102.log`。其中流式活动 32 项覆盖 Chat／Responses 普通正文、显式原生摘要段落、完成回放去重、消息边界和私有／协议内容排除；真实 Compose 页面验证完整与尚未闭合的加粗、执行组折叠、总计时标签、预览顺序和旧快照导入。预览顺序通过 LazyList 的真实 item key 索引核对，不把无障碍滚动估值当成像素坐标。历史格式与图片资产恢复、任务时钟及贴底／键盘回归同时通过。

随后从同一最终源码完整 `assembleGithubRelease` 成功（3m54s，159 个任务），保留 R8、资源压缩、lintVital、Release 签名及默认金标 true，日志 `tmp/ai-import-status-package-20261102.log`。架构检查 606 个生产文件、0 违规依赖，`git diff --check` 通过。模拟数据不包含真实课表或凭据，未调用付费模型或真实账号；没有手机视觉验收。

```powershell
$env:JAVA_HOME='D:\Android studio\JDK'
.\scripts\Invoke-AndroidBuild.ps1 testGithubDebugUnitTest --tests '*AiImportActivityReportsTest' --tests '*AiImportStreamActivityTest' --tests '*AgentImportActivityTest' --tests '*AiImportCheckpointTest' --tests '*AiImportConversationPreviewUiTest' --tests '*AiImportTranscriptTest' --tests '*AiImportHistoryImagesTest' '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8' --console=plain --no-parallel --max-workers=1
.\scripts\Invoke-AndroidBuild.ps1 assembleGithubRelease '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8' --console=plain --no-parallel --max-workers=1
```

APK：`com.xiaomanjun.sleepdownschedule`，`1.2.8_beta2 / 36`，7,774,531 字节，SHA-256 `8eaf3cb8c44981268dd257f81a5fd5ef600d9cc2d455422eb44277aebd2d2500`。V2 签名验证通过，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a` 与前批相同。R8 映射包含新记录分组实现。保全：`tmp/review-apks/SleepDown-1.2.8-beta2-ai-status-timeline-20261102-8eaf3cb8.apk`。

沿用此前“做完打包”与网盘交付要求，已上传原“SleepDown 测试包”目录：[下载本轮 APK](https://drive.google.com/file/d/140CBY-xzvKL_JadJ7NCRyZvCBXMuyRKp/view?usp=drivesdk)。已回读名称、大小和父目录；共享权限未更改，插件不返回远端哈希，不声称核验了远端 SHA-256。没有安装、提交、推送或公开发布。
