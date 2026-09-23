# SleepDown Schedule 开发指南

本文件保留长期有效的协作原则、数据边界和交付方式。开始任务时完整阅读本文件，其余文档按任务查阅，入口见 [docs/README.md](docs/README.md)。单次调查、实验结论和验收记录留在对应报告中，不累积成全局禁令。

## 判断与执行

优先级：用户数据正确 → 功能稳定 → 性能与真实体验 → 代码美观。

- 先看工作树状态、相关代码和已有实现，再定位根因。修改范围由证据和用户目标决定；跨文件修复是正常选择，无需为日常实现反复请示。
- 用户当前明确要求可以调整已有功能、视觉参数和文档基线。“保持现有行为”约束的是任务外改动与纯结构重构，不能阻止本次要修的问题。
- 会话中已有的授权持续有效。“开 PR”包含为该 PR 提交、推送及后续更新；“打包安装”包含构建和覆盖安装。不要重复询问已经明确的事项。
- 只有缺少必要信息、超出授权、可能丢失用户数据或实质改变目标时才请求决定。一般实现风险说明后继续处理。
- 选择足以解决根因的简单方案。避免无关重构、机械拆文件、猜测性 fallback、无界重试和无决策价值的测试；确有必要的错误处理、抽象和验证由实际场景决定。
- 调试使用真实堆栈、状态、输入输出摘要与设备证据。实验尽量一次改变一个核心变量，错误在合适边界暴露，不以默认值掩盖失败。
- 复用已确认的结论，使用 `rg` / `rg --files` 定位，避免重复读取大文件。只有用户或更高层指令允许时才使用协作 Agent，并分配独立文件与清晰边界。

## 文档与事实来源

- 代码、构建配置、Manifest、数据库迁移和实际设备行为是当前实现的事实来源。文档中的历史版本、数量、路径和实验参数需要对照代码。
- 专项文档说明对应模块的约定；带日期的报告、worklog、archive 和 Release Notes 记录当时状态，不自动成为当前任务的额外限制。
- 文档冲突先检查适用范围和证据，在授权范围内更新过时说明。不要为满足旧清单扩大本次修改。
- 目录与依赖：[PROJECT_STRUCTURE.md](docs/architecture/PROJECT_STRUCTURE.md)。`app` 负责装配，`feature` 负责功能，`domain/model` 负责规则与模型，`data` 负责持久化，`core` 负责通用能力；`core/data/domain` 不依赖具体功能页面。
- `glass/` 和 `transition/` 分别维护玻璃与跨 Activity 转场。独立官网 `sleepdown-site/` 不参与 Android 构建；本地 Miuix 来源以 `settings.gradle.kts` 为准。

## 数据、隐私与兼容性

- 不主动清除或覆盖用户数据。数据库变更必须属于任务范围，并具备连续迁移、备份兼容与升级验证，不用清库绕过。
- 保持 Room、BackupFormatV1、SleepDown 口令、ICS、Widget、通知、AI 历史及已发布导入/恢复格式的兼容。确需改变协议时明确迁移方案。
- v1.1.5 的旧身份 `com.example.courseschedule` 通过导出 `.sleepdown` 后在新包恢复迁移；保留该链路。当前包名、版本、渠道、签名和 Room 版本读取实际配置。
- Android 组件 FQCN、Intent wire id、TransitionRoute 和兼容门面变更必须考虑已发布客户端与系统引用。
- `SleepDown-Server/` 是本地私有后端，其源码、产物、数据库、环境与管理配置不得暂存、提交、推送或上传。
- 密钥、签名配置、服务端凭据和可恢复的用户原始数据不进入仓库、日志或报告。诊断只保留必要摘要。

## 界面与性能

- 复用 [公共设计系统](docs/architecture/SLEEPDOWN_DESIGN_SYSTEM.md) 的 Dialog、Alert、Picker、QuickSheet 和 Popup。需要新的交互时扩展已有入口，按当前需求选择布局和动效。
- 适配窗口、安全区、字体比例、明暗主题与壁纸；不写单设备尺寸特例。视觉改动保留可访问性、可读性和完整交互。
- 页面完整 underlay 是 Backdrop producer，弹层 host 是后绘制的同级消费者，避免自采样、循环与域错配。业务组件通过 `glass/` 入口使用材质，不直接创建 `LayerBackdrop` 或调用 `drawBackdrop`。
- 有真实锚点的成熟 Morph 复用现有路线；无锚点弹层可使用公共中心动画。退出内容保留到动画完成，运动期临时 clip、RenderEffect 和 Offscreen 资源在稳定后释放。
- 周视图移动与缩放互斥，冲突退回和保存交接保持正确；课程编辑和多课表快速设置沿用各自保存语义。用户要求调整的外观和动画在对应路线内修改。
- 性能优化先定位 CPU、GPU、重组、采样、生命周期或缓存瓶颈。以同场景证据验证收益，避免无依据叠加缓存、模糊层或删除视觉效果。
- 玻璃实现与历史调查见 [LIQUID_GLASS_FRAMEWORK.md](docs/performance/LIQUID_GLASS_FRAMEWORK.md)，当前路径以代码为准。

## 专项链路（相关任务才查阅）

- 教务导入复用拾光官方 adapter → bridge → SleepDown UI，不重复维护学校协议。保留协议未知字段兼容、上游来源与许可证；当前使用的协议与索引读取代码和上游基线。
- WakeUp、星链和 SleepDown 口令共用既有入口及预览确认流程。教务缓存按实际失效需要更新。
- AI 导入保持 AI → ImportDraft → 本地校验 → Preview → 用户确认 → 数据库。后台 transport / Service 与解析规则分层；权限提醒不能阻止用户开始导入。
- 文件处理见 [AI_FILE_IMPORT.md](docs/ai/AI_FILE_IMPORT.md)。PDF 先提取文字，无文字才按模型能力转图片。Repair 复用 provider/endpoint，只修格式及 schema，不重传文件、不重新 OCR，最多 3 次并逐次校验。
- 后台问题区分 Android 生命周期与 OEM 冻结策略；先确认 Service、socket、UID 和网络状态再改 transport。长期任务使用符合语义的 FGS 与通知，避免假保活、无限 wakelock 和无意义轮询。
- 课程勿扰优先沿用应用自有 AutomaticZenRule，无权限时引导系统授权。Launcher 切换以 Manifest 与 `AppIconManager` 的已有 alias 映射为准，最终仅启用一个目标，图标数量不在文档写死。
- Widget 按 host 尺寸生成完整 RemoteViews，背景与文字使用同一坐标系。节次分段只调整现有边界，不隐式改变课程编号、总节数或用户时间。
- Oplus ViewSeamless 实验保持关闭，用户明确恢复前不扩散。官网沿用既有部署链路，收费资源需明确授权。

## 工作树与 Git

- 默认 PowerShell，源码修改使用 `apply_patch`。只暂存本次文件，保留用户改动和任务外素材；不使用 `git reset --hard` 或 `git checkout --` 丢弃工作树。
- 代码在独立分支完成，默认 `codex/` 前缀。围绕用户目标组织 PR；同轮追加的问题保留可独立回退的提交边界，避免混入无关整理。
- 普通版以 `main` 为唯一基线；厂商实验功能集中在隔离目录，普通 GitHub 版启用，商店版由 `SLEEPDOWN_EXPERIMENTAL_FEATURES` 关闭。暂不维护历史 `exp` 分支或独立实验更新通道。版本、组件和发布规则见 [普通版实验功能规范](docs/EXP_BRANCH_AND_RELEASES.md)。
- GitHub 元数据操作使用 Codex 自带 GitHub plugin，不使用 GitHub CLI。远端推送、PR、标签、发布和部署须在用户授权范围内；PR 授权不包含合并或 Release。
- 每次 Beta 发布同步更新应用内日志与发布页，保留本轮各 Beta 的独立日志；正式版发布时再归并本轮 Beta 内容，去重并移除已撤回的改动。日志使用产品语言，用户指定的文案优先。
- 不提交 `.gradle-user-home/`、`tmp/`、`sleepdown-promo/`、`ui.xml`、设备截图和临时验收图，除非用户明确指定。
- 删除或移动前确认精确目标；破坏性操作必须有授权，不删除任务外文件。

## 验证与安装

根据修改风险执行最小充分验证，不为局部改动无意义地重复全套任务：

- 默认按下表完成编译或打包；只有数据、时间规则、解析、状态机等存在实际回归风险时才运行相关测试。样式、文案和可逆的局部交互调整不追加机械测试，也不顺带运行无关套件。
- 删除已退役实现时清理对应旧测试；仍保护当前数据兼容或业务规则的测试应保留。过时测试引用不能成为每次开发反复扩大验证范围的理由。

| 修改类型 | 验证入口 |
| --- | --- |
| Kotlin / Compose | `compileGithubReleaseKotlin` |
| 资源、Manifest、依赖、打包或用户要求安装 | `assembleGithubRelease`（包含 Kotlin 编译） |
| 时间规则、解析、迁移、状态机 | 对应定向测试或真实场景验证 |
| 纯文档 | 检查内容与 diff，无需构建 |

默认环境及正式构建：

```powershell
$env:JAVA_HOME='D:\Android studio\JDK'
$env:GRADLE_USER_HOME='C:\Users\23085\.gradle'
.\gradlew.bat assembleGithubRelease --console=plain --no-parallel --max-workers=2
```

按机器内存减少 worker；避免同时运行多个高内存构建。开发可使用 `-Psleepdown.skipReleaseResourceShrink=true`，正式候选保留资源压缩、R8、lintVital、打包与签名。Release 签名位于仓库外。

用户要求安装时先运行 `adb devices -l`，确认目标后覆盖安装签名 Release：`app/build/outputs/apk/github/release/app-github-release.apk`。默认不自动启动，除非用户要求。

只报告实际执行的验证；实机记录设备、系统、场景和结果。未执行项说明原因。完成回复简洁交代修改及原因、主要文件与影响、构建/测试和安装结果；面向用户的更新日志使用产品语言。
