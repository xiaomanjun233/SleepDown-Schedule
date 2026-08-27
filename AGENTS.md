# SleepDown 课程表：开发交接

## 项目约束

- Android 课程表，核心特色是 Miuix 风格、液态玻璃视觉和 AI/Agent 融合。
- 保留现有功能、视觉、动画、模糊、折射、色散及第三方开源库引用；除非用户明确要求，性能优化不得靠删除既有效果完成。
- 数据安全优先。数据库、配置、备份恢复和升级必须提供迁移路径，不得主动清除或覆盖用户数据。
- UI 按窗口、安全区、字体比例和当前数据自适应，不为单一 DPI、分辨率或设备写死特例。
- 工作树可能包含用户素材和临时证据；禁止使用 `git reset --hard`、`git checkout --`，不得删除或整理任务外文件。
- 未经明确同意，不推送、不打标签、不发布 Release。
- 验证按风险选择最小充分集合；只有相关源码、构建配置或验收条件变化，或用户明确要求时，才重跑完整测试。
- 未指定构建变体时只构建 GitHub Release。开发阶段可用 `-Psleepdown.skipReleaseResourceShrink=true` 跳过资源压缩，但正式发布候选包必须开启资源压缩并保留 R8、lintVital、打包和签名。
- 大面积玻璃优化已进入正式配置并保持常开；已停止的液态动效实验及其构建开关已从生产代码删除。交付包构建签名 Release 并覆盖安装到已连接设备；安装后不自动启动，除非用户要求。

## 仓库、版本与构建

- 工作目录：`D:\Android studio\CourseSchedule`；集成分支：`main`；远端：`origin` / `gitee`。
- Java：`D:\Android studio\JDK`；Gradle 用户目录：`C:\Users\23085\.gradle`。
- 已发布冻结版：`v1.1.5`，旧包名 `com.example.courseschedule`，`versionCode=25`，Room 36。
- 当前开发版：`1.2.1` / `versionCode=27`，正式包名 `com.xiaomanjun.sleepdownschedule`，Room 38，含 `github/store` 渠道和 `.debug` 隔离。1.2.0 保持已发布冻结状态。
- 旧包升级通过 v1.1.5 导出 `.sleepdown` 后由新包恢复；`BackupFormatV1` 必须继续兼容。迁移审计见 `docs/migration/`。
- Release 签名位于仓库外的长期 keystore；禁止将路径、密码或服务端凭据写入仓库。
- 构建命令：

  ```powershell
  $env:JAVA_HOME='D:\Android studio\JDK'
  $env:GRADLE_USER_HOME='C:\Users\23085\.gradle'
  .\gradlew.bat assembleGithubRelease --console=plain --no-parallel --max-workers=2
  ```

- APK：`app/build/outputs/apk/github/release/app-github-release.apk`。设备只覆盖安装签名 Release；安装前运行 `adb devices -l`。
- 2026-08-26 最近已安装验收基线：大玻璃 `true`、液态动效 `false`，APK SHA-256 `6F052C5B5F3FC17DB4E9E31F038A5B618E62DBC5F721006DF6F77895CC286113`，已覆盖安装到 PLJ110 `3B15AE023YL00000`，未由 Codex 启动。

## 长期视觉与交互规则

- 动态壁纸上的玻璃弹窗、表单、选择器和卡片必须统一使用实际玻璃前景色/反色来源；不得在同一卡片混用 `MaterialTheme`、`appPanelForegroundColor` 与写死黑白色。新增或改造后同时验收明亮与暗色壁纸。
- 现有成熟 Morph 必须复用真实锚点、轨迹、时序、背景深度和内容交接。运动阶段的 clip、RenderEffect 与 Offscreen 到稳定终点必须释放；不得另写近似动画或返回假锚点。
- 跨 Activity 页面使用 `transition/` 的统一路线、单向 Backdrop 和既有 Morph。多课表快速设置的“详细设置”保存草稿后走 `QuickSheetToSettingsDetail`；原 QuickSheet 留在首页下层，目标 Activity 直接从真实按钮 bounds 展开，不使用 Bitmap 快照或页内第二套 Overlay。
- Backdrop producer 只能包住完整业务 underlay；Popup/Dialog host 必须作为后绘制的同级兄弟节点。页面、TopBar 和低层 Overlay 都应进入 producer，最高层弹窗不得进入自己的采样树。禁止以 draw-mask 或页面特例规避 RenderNode 自递归。
- 首页三点菜单保持已验收的 194×317dp、30dp 外壳和 11dp 同心间距；课程管理、教务导入等目的页复用统一首页目的页 Morph。
- 首页课程编辑器的星期、节次、周次和单双周入口使用箭头行；静止态不常驻灰底。首页点击后打开居中 Picker；课程管理详情的周次才允许原地折叠。
- 周视图长按移动与右下角缩放互斥。拖拽、缩放阈值、落地、冲突退回、保存交接和邻卡余震继续使用既有弹簧与阻尼路线，不得新增生硬补间。
- 首页 Dock 的玻璃明暗在“壁纸自适应首页”和“应用主题设置页”之间切换时，点击或滑动确认后必须以 Kyant 内部叠化切换材质、选中层和前景色，不能瞬切或同时绘制两个完整 Dock。

## 液态玻璃 2.0 当前基线

- Backdrop 为 2.0.0，Shapes 为 1.2.0。`glass/` 统一管理采样域、材质 token、provider/consumer、场景阶段、诊断、稳定 envelope、课程卡合批与 `LiquidMorphController/Spec`；业务代码不得直接新建 `LayerBackdrop` 或调用 `drawBackdrop`。
- 首页保留 `Background`、`Content`、`PickerScene` 三个独立采样域；`ChromeCombined` 只组合前两者。拓扑必须拒绝自采样、域错配和循环。
- 稳定周课程卡按整页空间分组，最多 8 卡共享 blur/lens 链；0–7、8–11、12+ 卡采样比例分别为 1.0、0.75、0.5。逐卡 tint、高光、阴影、内容、手势和语义仍保持独立全分辨率。
- 课程卡遮挡生命周期保持 `Live → Preparing → Suspended → PostCloseRestore → Revealing → Live`。Opening/Open/Closing 使用无课程 shader 的纯色缓存；Closing 后按 16.666667ms 恢复 group，再用 200ms 只叠化玻璃材质。禁止恢复 FinalCommit、PostClose 快照、CPU readback、整层课程卡缩放/淡入和 8 波恢复。
- 刷新率变化不得改变采样比例或强制 60Hz；背景继续使用既有 Opening 12 档、Closing 32 档与 42% 处全分辨率单层交接。不得靠降低终态折射、色散、阴影或 decoration 换性能。
- 阶段三三点菜单液态动效实验已从生产代码删除，归档分支仅作证据；未经用户重新授权不得重新引入。性能诊断与历史失败方案见 `docs/performance/`。

## 设计系统、弹窗与 Popup

- `core/ui/designsystem/` 是二级页节奏、Dialog、Alert、Picker、QuickSheet、标题栏、按钮与输入胶囊的公共入口；规范见 `docs/architecture/SLEEPDOWN_DESIGN_SYSTEM.md`。
- 居中 Alert/Picker/短编辑器统一使用 `rememberCenteredDialogVisuals`、300dp 最大宽、连续 G2 外框、内容驱动高度和 48dp Capsule 动作按钮。按钮只使用普通压暗反馈；危险操作只红字，不改变玻璃着色。
- 弹窗壳使用当前 SleepDown 中性 Kyant 材质和中段高光；按钮沿用现版材质。标题与说明使用额外水平内容内缩，不能贴近壳体边缘；按钮仍可保持较宽并与正文形成清晰层级。
- 居中弹窗背景使用预构建低成本 plain blur 和压暗，进度随入退场变化；必须覆盖完整业务 underlay（含二级页大标题），退出节点到进度归零后才卸载。中心弹窗不启用预测性返回形变。
- 普通 Dropdown 与级联 Popup 以 NexioSchedule `4de678c` 的本地 Miuix 改造为基线，业务调用 `SleepDownLiquidDropdownPreference` / `SleepDownLiquidCascadingPopup`。保留应用明暗主题、真实 IME 可用窗口、根层 Backdrop、防自采样和无 Backdrop 同几何降级；不得另写近似 Popup。
- 级联 Popup 返回顺序：二级、一级、键盘；第一次空白点击同时关闭一二级且保留输入焦点，下一次空白点击再由页面收键盘。模型快捷选单二级层冻结并贴住一级真实锚点。
- 多课表快速设置继续使用 `QuickScheduleSettingsSheets` 半屏 QuickSheet 与原生 Kyant lens；Popup 改造不得改变它的高度、布局、按钮、日期子 Sheet或材质几何。

## 周视图、配色与课程管理

- 周视图行高保存设备无关的相对倍率。恢复已验收的“自适应”基准档位：它必须在滑条视觉上始终居中，并按当前窗口、安全区、表头和节次数让全周网格刚好铺满可见页。
- 行高滑条使用围绕中点的非规则/分段映射：中点精确映射自适应值，左右分别提供紧凑和宽松范围；不得为了压缩档位改变已验收的自适应基准，也不得隐藏时间轴文字。字体或节次过多时继续使用既有可读高度下限和纵向滚动。
- 周课程卡 G2 圆角单独保存 0–1 进度，归入字体/玻璃等卡片外观组。配色保持纯色、同色系渐变和彩色三行；候选为无黑框的圆形。渐变深端保持同色相并提高浓度，不得压成黑灰；彩色的四种种子继续交给现有稳定分配算法。
- 课程管理在三种颜色模式都保留识别条；仅彩色模式允许改课程级颜色。自定义时间、冲突检测、小幅回弹滚动及 Room/备份/口令/ICS/通知/组件字段链路保持兼容。
- 手机周视图节次栏为 56dp、课程区保留 8dp 右余量；大屏保持现有 tablet 布局。

## 教务导入与 AI

- 教务导入使用拾光仓库 2.0：优先 `school_index.pb`（`protocol_version=2`），YAML 仅作私有/开发兼容回退；未知字段安全跳过。上游基线记录和 `GLOBAL_TOOLS/test_page.html` 必须保留。
- 教务页的页面内容必须是 Backdrop producer，搜索胶囊、悬浮字母栏等玻璃消费者位于其后的同级 Overlay host，确保真实 blur/lens，不允许仅以半透明色伪装玻璃。
- 学校/工具按类别使用全宽单张 Miuix 卡片，组内不显示分割线。字母索引使用灰色文字和悬浮玻璃胶囊。
- 搜索框采用首页 Agent 同系低高度玻璃胶囊。聚焦时输入区向左收缩，右侧从同一母体分裂出动作胶囊：空文本显示“取消”，有文本显示“搜索”；取消、返回或清空退出搜索后按逆向 Morph 合回。
- 拾光官方 WakeUp 与星链课表口令归入 AI 手动导入，与 SleepDown 自有口令共用同一个输入框；先尝试自有口令，失败后按特征复用 `GLOBAL_TOOLS/wake_up.js` 或 `starlink.js` 并回填同一导入链路。不得进入独立页面，也不得在“添加单节课”或教务学校列表中新增独立入口。
- AI 手动导入文件说明必须列出 PDF、图片、XLSX、CSV、DOCX、PPTX、ODS、TXT、Markdown、JSON、XML、HTML；PDF 先取文字，无文字再转图且只交给支持图片的模型。详细矩阵见 `docs/ai/AI_FILE_IMPORT.md`。
- AI 历史预览和列表统一走 `AiHistoryToDetail` 进入唯一的 `AiImportHistoryDetailActivity/AiEduImportProgressPage`。IME 补偿、附件全屏 Morph 与历史滚动层级保持现有已验收实现。
- Day Agent 提示词与工具协议见 `docs/architecture/DAY_AGENT_RUNTIME.md`。MiMo 官方 `web_search` 是供应商插件，不计入本地未知工具；只有明确搜索证据可进入最终回答。天气使用 ApiZero 坐标接口并保留缓存回退。

## 其他不可回退行为

- 详细设置的上午/下午基础分段、中午和晚上开关只重分配现有节次边界，总节数、课程编号、自定义时间与课程不得改变；开关互不回撤。
- 4×2 小组件按 host 尺寸提供完整 RemoteViews，背景和文字处于同一坐标系；平板最小 460×140dp、手机 250×110dp，字体使用统一自适应放大基线。
- Android 15/17 课程勿扰优先使用应用自有 `AutomaticZenRule`；无授权打开系统勿扰访问页。Launcher 只允许三个动态图标 alias，切换前禁用全部旧 alias，再启用唯一目标。
- `sleepdown-site/` 线上仍由杭州 ECS 直出；未授权不得创建 OSS/CDN 等按量资源。
- `SleepDown-Server/` 为私有后端，不得暂存、提交、推送或上传任何源码、构建产物、数据库、环境文件、管理配置、API Key 或密钥。

## 跨 Activity 与 Oplus 暂停项

- 业务只通过 `transition/TransitionRoute`、`ActivityTransitionCoordinator` 与 `CrossActivityTransitionHost` 声明路线、Intent 和锚点；异常、超时或能力拒绝必须回退同一路线的 Legacy renderer。详细说明见 `docs/TRANSITION_FRAMEWORK.md`。
- Oplus ViewSeamless 全局开关和逐路线 allowlist 继续关闭；`TODO(OPLUS_DEFERRED_20260823)` 与归档分支仅保留调查证据。正式页仍存在 CLOSE 中心淡出或空帧，用户未明确恢复前不得继续调 callback、registration View、vendor Bundle 或扩散路线。
- 发布前仍需补齐迁移、备份、真实 v1.1.5 恢复、Store 权限和新包 Widget 首装验收。

## 当前任务验收（2026-08-27，正在进行）

已完成并应保留：周视图已验收自适应行高基准及中点非规则映射；教务类别卡片已改为无分割线；Popup 公共入口和本地 Miuix 已具备可注入 `visualStyle`；WakeUp/星链已归入 AI 手动导入口令链路。公共 Alert 保持独立 `260dp` 最大宽；一个或两个按钮组成单行按钮区时只轻微收短约 `7–8dp`，按钮尺寸、横向位置与底边距不变。Picker 使用独立紧凑节奏，不沿用 Alert 的大段标题留白。

1. 从根层修复设置二级页 Popup：完整页面 underlay（TopBar、大标题、页面内容及低层 Overlay）全部进入同一个 Backdrop producer；Popup host 在其后作为同级兄弟，一级与二级 Popup 都消费该根 Backdrop，不能只采到内容卡片，也不能把 Popup 自身挂入采样树。
2. 模型快捷选单必须统一调用 `SleepDownLiquidCascadingPopup`，使用与设置 Dropdown 完全相同的 Nexio/Miuix 材质、圆角、明暗色、动画和根 Backdrop；删除或绕开业务层独立菜单外观，二级菜单继续贴住一级真实锚点。
3. 居中 Picker、调色盘与壁纸取色使用较紧凑的标题和内容间距；壁纸预览按窗口余量自适应，底部按钮不得被裁切。Alert 的单行按钮区只做轻微高度收缩，三按钮纵向布局保留独立节奏；外壳/按钮保持 G2 同心关系和既有底部间距。
4. Nexio Popup 的高光方向改为从上向下衰减，降低模糊并提高折射；一级和二级均使用同一公共材质链。模型快捷选单必须真正统一调用该链路，不能显示旧 Miuix 普通样式；触发文字的反色跟随输入胶囊当前前景色。
5. 从根层修复所有二级页的完整采样：页面 TopBar、大标题、分类标题、卡片与低层 Overlay 都进入 producer，中心弹窗和 Miuix Popup host 在其后消费；禁止页面特例和 RenderNode 自采样。
6. 教务页搜索胶囊和字母栏与返回键消费同一层 backdrop，降低模糊、提高折射。搜索框保持底部单手区，IME 顶起和动作胶囊分裂必须连续动画，不能点击瞬移。字母栏显示蓝色当前位置/滑动进度，每次滚动出现，停止后先收缩再淡出，交互与隐藏均有过渡。
7. 教务 Web/适配器详情页建立完整 producer/consumer 层级，修复所有玻璃降级为半透明；快捷历史网页入口的文字反色改为跟随其真实玻璃材质。
8. AI 手动导入原口令输入框同时接受 SleepDown、WakeUp 与星链课表口令。先走 SleepDown，本地失败后按特征选择 `wake_up.js` 或 `starlink.js` 的拾光流程，解析结果直接进入同一预览/确认链路；不得新增独立页面或课程编辑器入口。
9. 代码合入 `main` 后等待用户确认；确认后才构建签名 `assembleGithubRelease`，必须开启资源压缩。核对版本 `1.2.1 (27)`、大玻璃常开配置和签名后覆盖安装到 PLJ110 `3B15AE023YL00000`，不自动启动应用。

本轮已完成的层级修复（2026-08-27）：多课表 QuickSheet 的详细设置 Morph 按钮改用窗口坐标并按首页根坐标归一后再从“首页 underlay + 半屏 Popup”合成帧裁剪；截帧只在捕获阶段冻结首页 Popup host，交接后立即恢复实时绘制。详细设置内的 Miuix Dropdown/Cascading Popup 跟随当前 Scaffold 的 `renderInRootScaffold`，不再落到首页冻结 host 后方。教务搜索胶囊提升为整页根级悬浮 Overlay，底部位置通过内部偏移保持不变，扩大渲染包络并在稳定态卸载零半径 Offscreen RenderEffect，避免父级 padding、44dp 容器或矩形离屏边界裁切玻璃阴影和按压扩展。

当前定位证据：本地 Miuix `Scaffold` 的 `underlayModifier` 已明确只包业务层并把 popup host 留为后绘制兄弟；仍需核对三个设置 Scaffold 的生产范围。模型入口在 `feature/importing/AiRuntimePicker.kt`；教务搜索、字母栏、Web 内页与 AI 手动导入均在 `feature/importing/ImportUi.kt`；拾光脚本位于 `app/src/main/assets/shiguang_warehouse-main/resources/GLOBAL_TOOLS/wake_up.js` 与 `starlink.js`。

## 工作方式

- 默认使用 PowerShell；搜索优先 `rg` / `rg --files`，源码修改使用 `apply_patch`。
- 先诊断并保留证据，再修复；崩溃优先读取真实堆栈。
- GitHub 相关操作优先使用已连接的 GitHub 能力；本地 Git 只用于工作树、分支、暂存和提交。
- 更新日志与 Release Notes 面向最终用户，避免 Morph、Backdrop、Room、RenderNode 等内部术语；安装/迁移注意写在开头，应用内日志不写开发流水账。
- `.gradle-user-home/`、`tmp/`、`sleepdown-promo/`、`ui.xml`、根目录设备截图和临时验收图不属于源码提交范围，除非用户单独指定。
