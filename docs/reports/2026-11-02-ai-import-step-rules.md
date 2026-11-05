# AI 导入步骤显示规则与预览精简

## 范围与根因

用户反馈工具展开标记像文字、按压效果没有胶囊裁剪、预览中的“课表选项”冗余、连续周次全部枚举，并要求统一混乱的步骤显示。

原界面同时消费结构化 `activityReports`、旧 `steps`／`reasoningOutput` 和实时 activity，容易在时间线、处理详情及底部重复展示。同一事件有两个来源时缺少统一的显示去重，工具展开标记则使用文本字符。

## 统一规则

| 内容 | 处理方式 |
| --- | --- |
| 完整的公开模型进度与摘要 | 按发生顺序保留，使用 13sp／20sp 次色 Markdown，与 16sp／26sp 最终正文区分 |
| 真实工具与本地处理事件 | 连续事件在原位置合为“执行步骤 · 数量”，12sp／18sp 等宽弱色；只有最新运行组默认展开，完成后默认折叠，保留手动选择 |
| 相邻重复事件 | 相同文字只展示一次，跨来源时优先保留模型消息；隔着其他事件发生的再次操作仍保留，不修改持久化报告 |
| 旧历史字段 | 只有对应结构化消息渠道缺失时才补充，复用相同组件，不另开“处理详情” |
| 实时底部 | 只显示尚未提交的公开片段和本次导入总用时；已提交段落、传输参数字数与重复本地状态不再补一遍 |
| 提问、失败、最终结果 | 问题和错误直接显示；停止等待补充使用普通提示色；最终回复后保留属于该轮的课表预览 |

折叠箭头和操作标记改用矢量资源，展开箭头旋转，点击反馈在胶囊形状内裁剪。状态、组标题和工具内容共享 16dp 标记列及 8dp 间距。

预览删除阶段选单与 JSON 编辑入口，只保留结果和新建／覆盖。旧历史协议和后台阶段 API 兼容保留，旧轮次快照仍可独立导入；继续对话使用最新可见结果，并解除旧选择锁定对新请求的隐式影响。

导入课程周次复用编辑器的连续区间规则，抽到 `domain/schedule/ScheduleLabels.kt`。例如 `1,2,3,4,6,7,8` 显示“第1–4、6–8周”；单双周不补成连续周，不更改保存数据。任务外首页课程展示不变。

## 实现入口

- `feature/importing/progress/AiImportTranscript.kt`：事件分组、相邻去重、统一展开行与旧字段补充。
- `feature/importing/progress/AiImportReasoningPanel.kt`、`AiImportActivityReports.kt`：未提交片段与结构化记录隔离。
- `feature/importing/progress/AiEduImportProgressUi.kt`：精简预览入口、继续对话使用最新可见课表。
- 删除仅承载旧选单的 `AiImportCheckpointPanel.kt`；保留数据与历史兼容层。
- `feature/home/day/DayCourseCard.kt`、`domain/schedule/ScheduleLabels.kt` 与编辑器包装函数：共用连续周次显示规则。
- `ic_chevron_right.xml`、`ic_import_step.xml`：矢量图标。

## 验证与交付

六类 42 项定向回归通过（5m12s，0 失败／错误／跳过）：步骤分组与公开片段 6 项、真实 Compose 跨轮预览 1 项、贴底／键盘 2 项、阶段兼容 26 项、周次显示 4 项及编辑器周次选择 3 项。日志：`tmp/ai-import-step-rules-regression-20261102.log`。真实页面同时核对 Markdown、工具展开／收起、移除的选单、连续周次、旧快照导入和跨轮预览顺序；模拟场景不调用付费模型。

```powershell
$env:JAVA_HOME='D:\Android studio\JDK'
.\scripts\Invoke-AndroidBuild.ps1 testGithubDebugUnitTest --tests '*AiImportActivityReportsTest' --tests '*AiImportConversationPreviewUiTest' --tests '*AiImportTranscriptTest' --tests '*ScheduleLabelsTest' --tests '*CourseEditorWeekSelectionTest' --tests '*AiImportCheckpointTest' '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8' --console=plain --no-parallel --max-workers=1
.\scripts\Invoke-AndroidBuild.ps1 assembleGithubRelease '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8' --console=plain --no-parallel --max-workers=1
```

完整签名 `assembleGithubRelease` 通过（5m23s，159 个任务），保留 R8、资源压缩、lintVital 和默认金标 true。日志：`tmp/ai-import-step-rules-package-20261102.log`。架构边界检查 605 个生产文件通过，0 违规依赖，41 条既有环边；`git diff --check` 通过。

APK：`com.xiaomanjun.sleepdownschedule`，`1.2.8_beta2 / 36`，7,759,239 字节，SHA-256 `da209591eb6d9713a4331d2b16022c651fd653f974c1a74dcf65706135e8a136`。V2 签名验证通过，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a` 与前批一致。保全：`tmp/review-apks/SleepDown-1.2.8-beta2-ai-step-rules-20261102-da209591.apk`。

沿用此前做完打包／网盘交付要求，已上传既有测试包目录：[下载本轮 APK](https://drive.google.com/file/d/1TlkdhOSA1Zu_l8tYzMX-lQD-3SW-aVnR/view?usp=drivesdk)。插件前两次本地文件传输遇到连接错误；检查无同名文件后有界重试，第三次成功，已回读名称、7,759,239 字节和父目录。浏览器备用通道超时，最终仍由 Drive 插件完成上传；没有改变共享权限。插件不返回远端哈希，不声称已核验远端 SHA-256。

未做手机视觉验收，不将构建或 Compose 测试当作厂商 ROM 上的实际观感证据。没有安装、推送或公开发布。
