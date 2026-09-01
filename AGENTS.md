# AGENTS.md - SleepDown Schedule Development Guidelines

本文件是 SleepDown Schedule 仓库的长期开发规范。所有 Agent 在开始任务前必须完整阅读本文件；任务临时状态、单次验收清单和调查流水账不得长期堆积在此，应记录到对应 docs、分支说明或任务报告。

## 1. 核心开发哲学

SleepDown Schedule 是长期维护项目。决策优先级如下：

1. 用户数据正确；
2. 功能稳定；
3. 性能；
4. 真实用户体验；
5. 代码美观。

始终遵循：

- 稳定链路 > 架构洁癖；
- 正确性 > 代码漂亮；
- 真实用户体验 > 理论设计；
- 最小修改 > 大规模重构。

每次修改前必须能回答：

1. 为什么需要修改？
2. 当前问题的根因是什么？
3. 为什么所选方案能解决根因？
4. 是否会影响已有成熟链路？

禁止为了“未来可能需要”提前设计复杂架构。

## 2. 开始任务前：先理解，不要猜

任何任务都必须先：

1. 阅读相关模块代码；
2. 理解当前架构、数据流和生命周期；
3. 查找项目已有成熟实现；
4. 确认问题位置和根因；
5. 再决定最小修改范围。

禁止：

- 根据文件名猜测架构或代码行为；
- 未阅读上下文直接重构；
- 用个人经验替代项目实际证据；
- 为了“看起来更优雅”大规模重写。

参考实现优先级：

1. 项目已有成熟链路；
2. Android 官方实现和官方最佳实践；
3. 已采用的上游项目实现。

涉及拾光教务时，优先对齐 shiguangschedule 和 shiguang_warehouse，不重新设计已有协议。

## 3. 最小变更原则

默认策略：

> 用最小修改解决明确问题。

禁止在一个任务中顺手修改无关的：

- 模块；
- UI；
- 数据库；
- 架构；
- API；
- 配置；
- 命名和格式。

如果问题只在 A 文件，不要无依据扩散到 B/C/D。只有证据表明根因跨模块时，才扩大范围，并在报告中说明原因和影响。

禁止：

- 修改稳定功能；
- 引入不必要的抽象层；
- 进行无关 cleanup；
- 把机械移动与业务逻辑重写混在同一批变更中。

## 4. 错误处理与调试

### 4.1 禁止防御性编程泛滥

禁止为了“保险”添加：

- 大量 fallback；
- 无限 retry；
- 多层无意义 try/catch；
- 隐藏失败的默认值；
- 无实际场景的 mock；
- 无决策价值的单元测试；
- 只增加复杂度的抽象层。

错误处理应遵循：

错误 → 记录真实原因 → 定位根因 → 修复根因。

不要采用：

错误 → 猜测 → 增加更多代码 → 掩盖真实错误。

错误必须在合适边界暴露、记录或转换成用户可理解的信息；不得吞掉会影响正确性的异常。

### 4.2 Debug 优先于猜测

复杂问题先增加可验证信息，优先检查：

- 真实堆栈；
- 状态日志；
- 时间线；
- 输入输出摘要；
- 生命周期状态；
- 系统和设备状态。

一个实验只改变一个核心变量。不要同时更换 endpoint、模型、prompt、transport 和解析器，否则无法得出可靠结论。

日志只记录诊断所需摘要，不记录凭据、完整隐私数据或可恢复的用户原始内容。

## 5. Agent 协作、额度与上下文

工作时必须考虑 token 消耗、上下文长度和请求次数。

禁止：

- 重复读取无关文件；
- 重复上传大文件；
- 对同一事实反复搜索；
- 用高成本推理处理简单、确定、机械的任务；
- 为了展示过程输出没有决策价值的长总结。

优先使用 rg 和 rg --files 定位相关内容，只读取完成任务所需的文件与文档。已有结论应复用，不重复进行已经完成且仍然有效的调查。

只有在用户或更高层指令允许协作 Agent 时才拆分任务。允许时：

- 主 Agent 负责架构判断、关键决策、风险控制和最终整合；
- 低成本 Agent 负责格式转换、简单检查和确定性修改；
- 子任务必须边界清晰、可以独立验证；
- 不得让多个 Agent 同时改同一文件。

## 6. 仓库结构与架构边界

### 6.1 仓库区域

- app/：Android 应用主模块。
- benchmark/：Macrobenchmark 与 Baseline Profile，不承载业务实现。
- docs/：架构、迁移、AI、性能与交接文档。
- patches/：第三方依赖补丁。
- release-notes/：面向用户的版本说明。
- sleepdown-site/：独立官网，不参与 Android Gradle 构建。
- SleepDown-Server/：本地私有后端，不属于公开 Android 工程。
- miuix-local/ 或配置的 Miuix source path：本地 Miuix 源码参考/组合构建，实际来源以 settings.gradle.kts 为准。

更完整的目录边界见 docs/architecture/PROJECT_STRUCTURE.md。

### 6.2 Android 包边界

com.xiaomanjun.sleepdownschedule 根包只保留稳定 Android 入口、Application 和必要兼容门面。实现遵循：

- app：应用装配、导航与顶层状态协调；
- model：跨功能共享的当前业务/持久化模型；
- data：Room、数据库修复、迁移与 Repository；
- domain：无 Android UI 副作用的规则与计算；
- core：通用 UI、配置、身份、壁纸和性能基础设施；
- feature：按用户功能组织的页面、状态和工作流；
- glass：液态玻璃统一框架；
- transition：跨 Activity 转场统一框架。

依赖方向和文件拆分规则以 docs/architecture/PROJECT_STRUCTURE.md 为准。不得仅为目录整齐增加 Gradle 模块或复制第二套 domain model。

Manifest Activity、Service、Receiver、Widget Provider 的稳定 FQCN、Intent wire id、TransitionRoute 和兼容门面不得在无迁移方案时改变。

## 7. 数据与兼容性保护

数据安全优先。数据库、配置、备份恢复和升级必须有迁移路径，不得主动清除或覆盖用户数据。

以下成熟协议和流程不得在无明确任务与兼容方案时改变：

- Room schema、表名、字段、迁移顺序和 SQL；
- BackupFormatV1；
- SleepDown 口令；
- ICS；
- Widget；
- 通知；
- AI 历史；
- 已发布版本的导入与恢复格式。

已发布冻结版 v1.1.5 使用旧包名 com.example.courseschedule；新包升级依赖 v1.1.5 导出 .sleepdown 后恢复。该兼容链路必须保留。当前版本号、包名、Room 版本和渠道以 app/build.gradle.kts、Manifest 与迁移代码为唯一事实来源，不凭 AGENTS.md 中的历史数字猜测。

除非任务明确要求数据库变更，否则禁止修改 schema。确需修改时必须提供连续迁移、备份兼容和真实升级验证，不得清库绕过。

## 8. Git、分支与提交

代码修改必须：

- 在独立分支完成，默认使用 codex/ 前缀；
- 一个分支和一个 PR 只处理一个主题；
- 提交信息清晰；
- 保留可独立回退的提交边界。

禁止：

- 直接在 main 上进行业务修改；
- 一个 PR 混合 UI、后端、大重构和无关 cleanup；
- 使用 git reset --hard 或 git checkout -- 丢弃工作树；
- 删除、整理或覆盖用户已有改动与任务外文件；
- 未经明确授权推送、打标签、发布 Release 或创建远端资源。

正常交付路径是 PR；推送和创建远端 PR 仍需用户明确授权。工作树可能包含用户素材和临时证据，必须先检查状态并只触碰任务范围内文件。

SleepDown-Server/ 的源码、产物、数据库、环境文件、管理配置、API Key 和密钥禁止暂存、提交、推送或上传。

## 9. 构建、测试与交付

### 9.1 构建基线

当前工程包含 github/store 渠道和 debug 隔离。版本与签名配置以 app/build.gradle.kts 为准。默认 Java 为 D:\Android studio\JDK，Gradle 用户目录为 C:\Users\23085\.gradle。

代码或构建配置变更在提交或声明完成前，至少执行与风险匹配的验证：

- Kotlin/Compose 源码：compileGithubReleaseKotlin；
- 影响资源、Manifest、依赖、R8、打包或发布链路：assembleGithubRelease；
- 数据迁移、解析器、状态机等：补充对应定向测试；
- 纯文档修改：不要求编译或打包。

正式 GitHub Release 构建命令：

    $env:JAVA_HOME='D:\Android studio\JDK'
    $env:GRADLE_USER_HOME='C:\Users\23085\.gradle'
    .\gradlew.bat assembleGithubRelease --console=plain --no-parallel --max-workers=2

开发阶段可在明确需要时使用 -Psleepdown.skipReleaseResourceShrink=true 缩短构建；正式发布候选必须开启资源压缩，并保留 R8、lintVital、打包和签名。

Release 签名位于仓库外。禁止把路径、密码或服务端凭据写入仓库。

### 9.2 测试纪律

测试必须记录：

- 设备；
- 系统版本；
- 场景；
- 输入；
- 实际结果。

复杂问题必须保留修改前证据和修改后证据。禁止用“感觉修好了”代替验证，也禁止伪造或推测测试结果。

验证遵循最小充分原则，不为低风险文档或局部纯逻辑修改无意义地跑全套任务。用户明确要求某项验证或明确免除打包/安装时，按用户要求执行并如实报告。

### 9.3 安装与发布

默认 APK 路径为 app/build/outputs/apk/github/release/app-github-release.apk。只有用户明确要求时才安装到设备；安装前先运行 adb devices -l，只覆盖安装签名 Release，安装后不自动启动，除非用户要求。

未经明确授权，不发布、不推送、不打标签、不创建按量计费资源。

## 10. 上游项目同步

依赖第三方项目时优先学习并复用官方实现，不重新发明协议。

教务导入优先链路：

官方 adapter → 官方 bridge → SleepDown UI。

禁止由 SleepDown 另行维护一套重复的学校协议或学校逻辑。

第三方源码、补丁、版本和许可证必须保持可追溯。SleepDown 修改过的第三方组件保留原包名和许可说明。

## 11. 教务导入与 AI

### 11.1 教务适配

- 教务导入使用拾光仓库 2.0；
- 优先读取 school_index.pb，protocol_version=2；
- YAML 仅作为私有/开发兼容回退；
- 未知字段安全跳过；
- 上游基线记录与 GLOBAL_TOOLS/test_page.html 必须保留；
- 不重新设计拾光已有协议。

拾光官方 WakeUp 与星链课表口令归入 AI 手动导入，与 SleepDown 口令共用输入入口。先尝试 SleepDown，本地失败后按特征复用 GLOBAL_TOOLS/wake_up.js 或 starlink.js，并进入同一预览/确认链路；不得另建独立页面或重复入口。

适配列表等远程数据应使用明确缓存与失效策略，避免每次进入页面重复刷新；具体时效以对应实现和任务要求为准。

### 11.2 AI Import 分层

后台执行层负责：

- AI 请求；
- Responses/SSE transport；
- 文件准备；
- Service 与任务生命周期。

数据处理层负责：

- JSON；
- Schema；
- ImportDraft；
- 本地解析、校验和 repair。

后台权限提示、系统 transport 和 AI 业务逻辑不得耦合成“无权限不能导入”。权限提醒只提示风险，不作为导入硬门槛。

成熟数据链路必须保持：

AI → ImportDraft → Preview → 用户确认 → 数据库。

禁止为了“减少一步”跳过预览或用户确认。

### 11.3 文件与隐私

AI 手动导入支持矩阵与处理规则见 docs/ai/AI_FILE_IMPORT.md。PDF 优先提取文字；无文字时再转图片，且只交给支持图片的模型。

日志和 repair prompt 不得包含完整隐私数据。只传完成修复所需的结构化错误和必要输出摘要。

### 11.4 AI 输出失败与 repair

AI 输出不是一次性可信结果。流程必须是：

AI 输出 → 本地解析 → Schema 校验 → 结构化错误 → repair 请求 → 重新校验。

解析失败不得直接把开发错误展示给用户。错误类型应能区分 JSON 格式、Schema 校验和字段位置，并保留可诊断日志。

Repair 最多 3 次，且必须：

- 复用当前 provider 和 endpoint；
- 使用短 prompt；
- 只修复 JSON 格式、字段缺失或 Schema 不匹配；
- 不重新上传原文件；
- 不重新 OCR；
- 不进行大规模重新推理；
- 每次重新进行本地解析与 Schema 校验。

超过上限后停止请求并展示用户可理解的失败信息，不得无限消耗额度。

## 12. Android 后台任务与 Foreground Service

遇到后台问题必须区分：

- Android Framework：Service、FGS、process、lifecycle；
- OEM 策略：ColorOS、MIUI、HANS、后台网络与冻结策略。

看到 socket error 不得直接修改网络代码。必须先确认：

- Service 是否存活；
- socket 是否被系统关闭；
- UID 或进程是否被冻结；
- 网络与后台权限状态；
- 生命周期和系统限制。

长期任务使用 ForegroundService、与任务语义匹配的 foregroundServiceType 和明确通知。禁止：

- 假保活；
- 空 Activity；
- 隐藏窗口；
- 无限 wakelock；
- 无意义轮询；
- 通过违规手段绕过系统策略。

已有 AI 后台 transport、SSE、Service、applicationScope、wake lock 和通知链路属于成熟实现，除非任务明确指向该链路且有证据，否则不得顺手改动。

## 13. UI、设计系统与液态玻璃

### 13.1 总体原则

保持 SleepDown 的 Liquid Glass、Miuix、Material 3 和流畅动画风格，但功能正确和交互稳定优先。

禁止为了视觉效果：

- 增加没有收益证据的巨大性能开销；
- 破坏交互或可访问性；
- 引入不可维护 shader；
- 删除已验收的模糊、折射、色散、阴影和动画来换取简单性能数字；
- 为单一 DPI、分辨率或设备写死特例。

UI 必须按窗口、安全区、字体比例和当前数据自适应，并同时考虑明亮与暗色壁纸。

### 13.2 公共设计系统

core/ui/designsystem/ 是二级页节奏、Dialog、Alert、Picker、QuickSheet、标题栏、按钮与输入胶囊的公共入口，规范见 docs/architecture/SLEEPDOWN_DESIGN_SYSTEM.md。

新增或改造弹窗、Picker、Dropdown、Cascading Popup 时必须复用公共实现，不另写近似外观。普通 Dropdown 与级联 Popup 使用 SleepDownLiquidDropdownPreference / SleepDownLiquidCascadingPopup 及现有 Nexio/Miuix 材质链。

### 13.3 Backdrop 与 Morph

- 业务页面 underlay 必须完整进入 Backdrop producer；
- Popup/Dialog host 位于其后，作为同级消费者；
- 最高层弹窗不得进入自己的采样树；
- 禁止自采样、域错配、循环和页面特例；
- 业务代码不得直接新建 LayerBackdrop 或调用 drawBackdrop；
- 跨 Activity 使用 transition/TransitionRoute、ActivityTransitionCoordinator 与 CrossActivityTransitionHost；
- 现有成熟 Morph 必须复用真实锚点、轨迹、时序、背景深度和内容交接；
- 运动期临时 clip、RenderEffect 与 Offscreen 到稳定终点必须释放。

液态玻璃框架、采样域、课程卡合批和遮挡生命周期以 glass/ 与 docs/performance/LIQUID_GLASS_FRAMEWORK.md 为准。结构整理不得改变已验收的视觉参数、Modifier 顺序、动画时序或 renderer 所有权。

### 13.4 已有交互基线

- 首页课程编辑器的星期、节次、周次和单双周入口使用箭头行；首页使用居中 Picker，课程管理详情的周次才允许原地折叠；
- 周视图移动与缩放手势互斥，冲突退回、保存交接和邻卡余震沿用现有弹簧路线；
- 周视图自适应行高基准位于滑条中点，按窗口、节次数和可读高度计算；
- 手机周视图节次栏与课程区余量、大屏布局、颜色分配和课程识别条保持既有已验收行为；
- 多课表快速设置继续使用 QuickScheduleSettingsSheets 与既有 Morph 路线；
- 教务页搜索胶囊、字母栏、Web/适配器详情页必须使用真实根层 Backdrop，不以半透明色伪装玻璃；
- AI 历史和进度页沿用唯一详情链路，不复制页面。

具体数值和实现细节以现有代码、设计系统文档与视觉基线为准，不凭记忆重新实现。

## 14. 性能优化

性能问题先定位瓶颈：

- CPU；
- GPU；
- recomposition；
- overdraw；
- shader；
- bitmap；
- 生命周期与资源释放。

禁止看到卡顿就直接增加缓存。Liquid Glass 优先考虑：

- 合理降采样；
- 遮挡剔除；
- 减少采样区域；
- 降低 shader 压力；
- 复用既有分组与生命周期。

禁止无限增加 blur layer，也不得通过删除已验收效果、强制刷新率或长期持有离屏资源掩盖根因。性能修改必须有修改前后证据。

## 15. 平台能力与不可回退边界

- Android 15/17 课程勿扰优先使用应用自有 AutomaticZenRule；无授权时打开系统勿扰访问页；
- Launcher 只允许既有三个动态图标 alias，切换前禁用旧 alias，再启用唯一目标；
- Widget 必须按 host 尺寸生成完整 RemoteViews，背景和文字保持同一坐标系；
- 上午/下午基础分段与中午、晚上开关只重分配现有节次边界，不改变总节数、课程编号、自定义时间和课程；
- Oplus ViewSeamless 全局开关与逐路线 allowlist 保持关闭；用户未明确恢复前，不继续扩散相关实验；
- sleepdown-site/ 继续使用既有部署链路，未经授权不得创建 OSS、CDN 等按量资源。

## 16. 文件与工作树安全

- 源码修改使用 apply_patch；
- 搜索优先 rg / rg --files；
- 默认使用 PowerShell；
- 不删除或整理任务外文件；
- 不把 .gradle-user-home/、tmp/、sleepdown-promo/、ui.xml、根目录设备截图和临时验收图纳入源码提交，除非用户明确指定；
- 不把本地密钥、签名配置、服务端凭据或用户数据写入日志、提交或报告；
- 任何破坏性操作前必须确认精确目标和用户授权。

## 17. 完成报告

完成任务后提供简洁、可验证的报告：

1. 修改内容；
2. 修改原因；
3. 修改文件；
4. 是否影响其他模块；
5. 编译或测试结果；
6. 实机结果。

未执行的验证必须明确写“未执行”及原因。不要输出大量没有决策价值的过程总结。

更新日志与 Release Notes 面向最终用户，避免堆砌 Morph、Backdrop、Room、RenderNode 等内部术语；安装和迁移注意事项放在开头。

## 18. 禁止事项

除非用户明确要求且已有完整迁移或验证方案，否则禁止：

- 未确认的大规模重构；
- 删除成熟功能；
- 修改数据库 schema；
- 添加隐藏 fallback；
- 伪造测试结果；
- 未验证就宣布完成；
- 为不存在的问题提前设计复杂方案；
- 自行扩大任务范围；
- 添加用户未要求的功能；
- 反复询问用户已经明确的信息。

发现普通实现风险时应说明风险并继续安全、在范围内的工作；如果风险涉及数据破坏、缺少授权或会实质改变任务目标，必须停止并请求用户决定。

## 19. Agent 执行纪律

当用户给出明确任务时，直接执行。根据证据做判断，小步修改，逐步验证。

SleepDown 不只是课程表，还包含 AI 导入、教务适配、Liquid Glass 和 Android 系统能力。不同链路必须保持分层和边界，不得因修复一个问题破坏另一条成熟链路。

最终目标：

让 Codex 成为可靠的软件工程 Agent，而不是过度谨慎或过度设计的代码生成器。

核心是：

- 理解项目；
- 定位根因；
- 小步修改；
- 验证结果；
- 保持长期演进能力。
