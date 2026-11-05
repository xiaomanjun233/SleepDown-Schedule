# 2026-11-01 AI 导入字体层级与模型选单动画

用户要求按 Codex 的对话层级梳理字体、字号、字重、颜色，并追加修复模型选单 Popup，确认主要问题是弹出位置或缩放方向不对。以下是适配 Android 后采用的本项目参数，不声称为 Codex 官方字体规格。

## 字体与文字层级

普通内容使用 Android 系统默认字体，中文随设备字库和用户字体设置；执行信息与 JSON 使用系统等宽字体，中文仍由系统字形补足。字号和行高统一为 sp，保留系统字体缩放。只在 AI 导入进度页建立局部主题，其他页面不替换字体。

| 内容 | 字体 | 字号 / 行高 | 字重 | 色彩角色 |
| --- | --- | --- | --- | --- |
| 用户消息、AI 正式回复、补充问题 | 系统默认 | 16 / 26sp | Regular 400，Markdown 强调保留 700 | 主文字 |
| 输入文字与占位 | 系统默认 | 16 / 24sp | Regular 400 | 输入为主文字，占位为弱文字 |
| 中间模型进度、模型摘要 | 系统默认 | 14 / 22sp | Regular 400 | 次文字 |
| 执行记录、处理过程、JSON 编辑内容 | 系统等宽 | 13 / 20sp | Regular 400 | 次文字，JSON 内容为主文字 |
| 说明、阶段描述、预览引导 | 系统默认 | 13 / 20sp | Regular 400 | 次文字 |
| 归档状态、AI 角色标记、模型胶囊 | 系统默认 | 12 / 18sp | Medium 500 | 弱文字；模型胶囊沿控件前景 |
| 计数、附件页数、等待脚注 | 系统默认 | 12 / 18sp | Regular 400 | 弱文字 |
| 页面标题、导入预览标题 | 系统默认 | 18 / 26sp | SemiBold 600 | 主文字 |
| 分组标题、预览课程名 | 系统默认 | 16 / 24sp | SemiBold 600 | 主文字；课程名保留课程色 |
| 附件名称 | 系统默认 | 14 / 22sp | SemiBold 600 | 主文字 |
| 预览课程详情、模型菜单条目 | 系统默认 | 14 / 22sp | Regular 400 | 主题主文字；菜单以实际弹层前景为准 |

主文字、次文字、弱文字使用明确的不透明灰色，替代散落的 0.43–0.88 文字 alpha：

| 角色 | 浅色 | 深色 |
| --- | --- | --- |
| 主文字 | `#1F1F1F` | `#ECECEC` |
| 次文字 | `#5F5F5F` | `#B5B5B5` |
| 弱文字 | `#666666` | `#9A9A9A` |

文字色会按实际设置页底色校验至少 4.5:1 对比度；预览课程的彩色标题继续使用既有可读性处理，错误保留主题错误色。处理过程的图标与文字采用中性层级，活动高光也使用主文字色。共享 `AgentRunTrace` 新增可选外观参数，默认仍保持其他页面原有样式。

集中定义：`feature/importing/progress/AiImportTypography.kt`。接入进度页、公开消息、中间状态、阶段/JSON、附件、输入框和预览。模型 Popup 通过公共菜单可选 `itemTextStyle` 接口统一条目排版，未复制菜单实现。

## Popup 动画与锚点

- 原代码将 `triggerTransferred` 直接同步到 `expanded`：打开时 Popup 尚未测量就隐藏触发胶囊，关闭请求发出时又在退出动画未结束前显示。现以 Popup 的折叠内容实际测量为接管点，完整退出回调才归还；页面暂停或组件卸载时主动释放源状态，防止胶囊一直隐藏。
- 选择新模型会立即改变触发文字和宽度，导致返回落点在动画中改变。现从打开到完整退出保留锚点名称和尺寸，结束后再显示新模型名称；中途反向打开延续同一锚点，迟到退出回调不释放新会话。
- 当前本地 Miuix 的级联 Popup 会在关闭时保留 `parentBounds`、`primarySize` 和 `hostMeasured`。重开时的进入等待可能被上次测量满足，先使用旧坐标启动；键盘或窗口移动后尤其容易出现错误位置/方向。现仅在一次完整关闭后的下一次打开重建该 Popup 的测量会话，等待当前源和 host 定位；关闭过程中反向打开保留原会话及在途弹簧。没有修改忽略目录中的 Miuix 源码，也未写设备尺寸特例。

主要实现：`AiRuntimePicker.kt`、`core/ui/settings/GlassMiuixPopup.kt`。模型、供应商、思考强度的保存和选择语义保持不变，不新增网络调用。

## 验证状态

最终 `compileGithubReleaseKotlin` 与 4 类 8 项定向测试同轮通过，4m49s，0 失败/错误/跳过。架构检查通过：605 个生产文件、0 违规文件依赖、41 条既有循环边界，`git diff --check` 通过。测试只使用本机测试目录、空 API key 与模拟模型，不访问真实模型服务。

新增 `AiRuntimePickerStateTest` 4 项验证源接管/归还、返回期间固定尺寸、完整关闭后新建测量会话、快速重开保留同一会话、迟到回调和 host 丢失恢复；`AiRuntimePickerAnimationTest` 使用真实 Miuix Popup 验证底部向上/顶部移位后向下的展开方向、退出过程中源状态保持及 Activity 暂停释放。既有对话预览 1 项与贴底/键盘 2 项同时复验。未做真实手机上的视觉验收。

```powershell
$env:JAVA_HOME='D:\Android studio\JDK'
.\scripts\Invoke-AndroidBuild.ps1 compileGithubReleaseKotlin testGithubDebugUnitTest --tests '*AiRuntimePickerStateTest' --tests '*AiRuntimePickerAnimationTest' --tests '*AiImportConversationPreviewUiTest' --tests '*AiImportTranscriptTest' '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8' --console=plain --no-parallel --max-workers=1
```

## 签名打包与云盘交付

用户随后明确要求“打包上传”。从上述已验证工作树完成完整 `assembleGithubRelease`，2m23s 成功，159 个任务；保留 R8、资源压缩、lintVital、Release 签名与默认金标 true，没有改版本号。新包包含字体层级、模型 Popup、圆形箭头、预览所属轮次及此前全部救援改动。

- 包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.8_beta2 / 36`。
- APK 7,758,147 字节，SHA-256 `3bec5084c56d854513bb61a191a422cd67c02fc3f72a497e652e069c62f844e6`。
- V2 签名验证通过，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与此前候选一致。
- 本地保全：`tmp/review-apks/SleepDown-1.2.8-beta2-ai-typography-picker-20261101-3bec5084.apk`。R8 映射包含新的 `AiImportTextStyles`、`AiImportTypographyKt`、`popupSession` 和 `retainedAnchorLabel`，确认最新实现进入本次构建。
- 已通过 Google Drive plugin 上传既有“SleepDown 测试包”目录：[下载最新 APK](https://drive.google.com/file/d/1WVVoSEdGqC-2FSgCeMmK4KG-4aXMdpB6/view?usp=drivesdk)。已回读文件名、字节数和父目录，未更改共享权限；元数据不返回远端校验和，不声称完成远端哈希核对。

没有安装、公开发布、创建 PR 或 Git 推送。手机上的实际动画观感仍待用户验收。
