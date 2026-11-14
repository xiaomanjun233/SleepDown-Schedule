# 2026-11-01 AI 教务导入对话与预览可读性

用户要求教务 AI 导入更接近 Codex 的连续对话，持续显示中间消息，并改善预览彩色文字在浅色模式下的可读性。沿用 `codex/rescue-import-materials`，保留此前教务、凝光与自动刷新救援改动。

## 对话界面

- 原先中间消息只展示最近 3 条，展开后最多 12 条；现在公开模型进度和实际执行事件分别作为对话列表条目，按原顺序持续显示。用户消息使用轻底色气泡，模型消息使用正文排版，执行信息降低视觉权重。
- 每轮最多保留 128 条已完成消息；继续补充要求时将这些消息连同该轮回复归档，历史 JSON 增加可选的 `activityReports` 字段。保留原有最近 8 轮与 2 MiB 上下文边界，旧记录缺少该字段时正常恢复，不改 Room 或课程交换格式。
- 最新状态改为可换行的区域，最多 4 行，不再水平追逐单行文字。原有公开进度过滤、流式节流和叶节点订阅保持有效，正文不会混入请求参数、原始 JSON 或不透明推理数据。
- 位于底部时跟随新内容；上滑阅读之前的消息后保持位置，提供“回到最新”。完成、报错或进入预览不再强制跳到底部，也不强制折叠用户已经展开的处理过程。
- 附件、停止/继续、历史、阶段课表及最终导入确认仍复用原有入口。

主要实现：`AiImportTranscript.kt`、`AiEduImportProgressUi.kt`、`AiImportReasoningPanel.kt`、`AiEduImportProgressSession.kt`、`AiImportActivityReports.kt`、`AiImportHistory.kt`。

## 预览可读性

原预览复用了首页无壁纸的浅亮彩色文字与阴影策略，浅色卡片上对比度不足。现在预览使用明确的主题表面，混入 4% 课程色，保留圆角与轻边框；课程名和色点保留课程色，按实际底色调整亮度以达到至少 4.5:1 对比度。时间、地点、教师、周次和备注使用高对比正文色。关闭彩色文字后标题同步使用正文色。

调整仅作用于 `ImportPreviewCourseCard`，首页课程卡片不变；明暗模式均按对应预览底色计算。公共设计系统的预览说明同步更新。

主要实现：`DayCourseCard.kt`、`CourseTextContrast.kt`。

## 验证

- `compileGithubReleaseKotlin` 与 7 类共 69 项定向测试同轮通过，2m42s，0 失败、错误或跳过。覆盖消息保留上限、跨轮次和历史往返、旧记录兼容、阶段课表与会话、公开流式事件过滤、明暗背景下的文字对比度、真实 Compose 列表上滑后的位置保持。
- 初次验证发现旧测试仍按 12 条上限断言；更新为新上限后通过，未放宽历史兼容与文本长度检查。
- 随后在同一 Compose 用例补充“停在底部时新增消息自动跟随”的断言，单独复验 `AiImportTranscriptTest` 通过（1m02s），再确认上滑后追加消息不会改变阅读位置。
- 架构检查：604 个生产文件，0 违规文件依赖；既有 41 条声明循环未增加。`git diff --check` 通过。
- 没有实机视觉或真实模型账号验收。本轮未安装、推送、发布；首次源码验收后用户追加的打包交付见文末，此前旧 Google Drive 救援 APK 不包含这些新增界面改动。

定向验证命令：

```powershell
$env:JAVA_HOME='D:\Android studio\JDK'
.\scripts\Invoke-AndroidBuild.ps1 compileGithubReleaseKotlin testGithubDebugUnitTest --tests '*AiImportActivityReportsTest' --tests '*AiImportConversationTest' --tests '*AiImportCheckpointTest' --tests '*AiImportStreamActivityTest' --tests '*AiEduImportProgressSessionTest' --tests '*ReadableAccentColorTest' --tests '*AiImportTranscriptTest' '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8' --console=plain --no-parallel --max-workers=1
```

## 同日追加：输入法避让与凝光 shortcut 闪烁

用户继续反馈：AI 导入内容已经到底时，点击输入框应将内容一起顶起；凝光模式长按 shortcut 闪烁。

键盘问题由列表跟随判定缺少布局边界引起：原先只观察消息条数、末尾条目编号与高度，键盘引起的 composer 底部留白增长或窗口缩小不会触发继续贴底。现在同时观察 viewport 起止边界和实际底部 padding。仅在原来跟随最新内容时继续贴底；已经上滑阅读旧消息时仍保留原位置。不增加固定延迟，也不按键盘或设备写死尺寸。

shortcut 源卡原先只设 Compose alpha=0，原生材质及光效仍启用；现在同一隐藏判定同时关闭源卡的材质可见性，沿用既有节点，关闭后恢复。浮层 host 原先在打开后才创建，并等待首次 `onGloballyPositioned` 才画替代卡片，存在源卡已隐藏而浮层尚未绘制的空档；现将无交互的 host 保留并提前测量，长按后直接使用已知坐标。没有修改系统默认菜单配方或菜单退出等待语义。

涉及 `AiImportTranscript.kt`、`WeekCourseBlock.kt`、`CourseShortcutOverlay.kt`。验证：

- 新增键盘布局回归在修复前明确失败于“Latest message must clear the animated composer inset”，修复后通过。
- `compileGithubReleaseKotlin` 和 4 类 16 项定向测试同轮通过，4m54s，0 失败/错误/跳过。键盘用例模拟逐步增加留白、窗口缩小以及阅读旧消息时的位置保持；shortcut 用例调用实际 `WeekCourseBlock` 的长按语义与真实 overlay/controller，替代不可用的厂商 renderer，连续开关两轮核对源材质停止、恢复及节点未重建。既有 shortcut 退出屏障和拖动回归通过。
- 架构检查仍为 604 个生产文件、0 违规依赖、41 条既有声明循环；diff 检查通过。
- 此阶段未安装，没有真实 ROM 渲染和键盘动画验收；测试验证的是生产布局与材质交接状态。用户随后追加的打包上传见下节。

本批定向验证使用同一构建入口、单 worker 和 3GB 堆，测试筛选为 `*AiImportTranscriptTest`、`*CourseShortcutTest`、`*CourseShortcutMaterialTest`、`*WeekDragPagingTest`。

## 最新签名包与 Google Drive 交付

用户再次确认“内容要被输入框顶起，是的话就打包放云盘”。已确认到底时内容随输入框的实际占位向上移动，并从当前工作树完成完整 `assembleGithubRelease`，2m20s 成功，159 个任务。保留 R8、资源压缩、lintVital、Release 签名和默认金标 true；不修改版本号。包包含本报告的连续对话、预览可读性、键盘顶起与 shortcut 修复，并保留前批教务/凝光/自动刷新救援。

- 包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.8_beta2 / 36`。
- APK 7,758,147 字节，SHA-256 `06b416e18d2944569a634a5cdba867befc6ec30e5fe9c99dd0b7a7550bc48257`。
- V2 签名验证通过；证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与此前安装候选一致。
- 本地保全：`tmp/review-apks/SleepDown-1.2.8-beta2-ai-dialog-shortcut-20261101-06b416e1.apk`。R8 映射包含新的 `TranscriptLayout`，APK DEX 包含“回到最新”操作，确认新增界面进入候选。
- 通过 Google Drive plugin 上传既有“SleepDown 测试包”目录，并回读文件名、大小、父目录：[下载新版 APK](https://drive.google.com/file/d/1hLSe4pKGkM6v0rPSGGKVWEZViVqj9RnC/view?usp=drivesdk)。没有更改共享权限；元数据未返回远端校验和，不声称完成远端哈希核对。

没有安装、推送、创建 PR 或公开发布。真实 ROM 上的凝光闪烁和键盘动画仍待实际使用确认。

## 后续源码：居中圆形箭头与预览所属轮次

用户反馈“回到最新”应为 Codex 类似的居中圆形向下箭头，并要求导入预览不一直跟在对话末尾，下一句话应从预览下方继续。

- 复用 `DialogLiquidButton`，改为输入框上方居中的 44dp 圆形，使用现有返回箭头旋转至向下，仅保留图标与“回到最新”无障碍标签。按钮仍受最新跟随状态控制，键盘避让沿用实际 composer 占位。
- 原尾部预览绑定当前所选 checkpoint，新一轮继承基线后会继续在尾部显示旧结果。现在依据归档的不可变 `artifactPayload`，将每个课表快照只显示在首次产生它的轮次后；用户下一句话、处理状态和回复从其下方继续。相同结果不重复追加，新结果才生成新的预览。
- 阶段选择和 JSON 编辑入口跟随最近的一张预览；回复与是否产生新课表分开呈现，避免纯文字回复被预览条件隐藏。每张旧预览的导入动作捕获其自身经过本地校验的快照，不误用当前最新课表。
- 不新增持久化字段，不改课程数据、历史格式、模型请求或最终确认链路；旧记录的快照通过原有校验解析入口恢复，错误会在对应轮次明确展示。

改动集中在 `AiEduImportProgressUi.kt`；公共设计系统说明同步更新。验证：`compileGithubReleaseKotlin` 与 5 类 38 项测试同轮通过（5m37s，0 失败/错误/跳过），包含真实 `AiEduImportProgressPage` 的长预览、下一句话位置、旧结果不重复、新结果追加、历史往返、圆形箭头回到底部、点击旧预览导入正确旧快照；既有贴底/键盘、checkpoint、历史与对话回归通过。架构 604 文件、0 违规依赖、41 条既有循环边界保持，diff 检查通过。

本批测试筛选：`*AiImportConversationPreviewUiTest`、`*AiImportTranscriptTest`、`*AiImportCheckpointTest`、`*AiImportCheckpointHistoryTest`、`*AiImportConversationTest`，沿用单 worker / 3GB 堆构建入口。没有本批新 APK 打包、上传或实机验收；上方 `06b416e1` APK 为上一批交付。

最后将 UI 夹具改为与产品一致的已校验快照作为下一轮基线，单独复验 `AiImportConversationPreviewUiTest` 通过（1m32s，0 失败/错误/跳过）。
