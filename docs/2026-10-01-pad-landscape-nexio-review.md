# SleepDown Pad 与横屏适配调研报告

建议将下一轮适配集中在三件事：统一按当前窗口和内容区决定布局、大屏采用可折叠导航与管理面板、为低高度横屏设计紧凑界面。Nexio 的侧栏和内嵌管理流程值得参考；SleepDown 已有的日视图双栏、今日助手、周视图和公共弹层可以继续作为实现基础。

调研日期：2026 年 10 月 1 日，时间均按北京时间。范围包含 Nexio 当前源码、SleepDown 本地实现、窗口变化与弹层边界。本轮仅输出调查与建议，应用实现尚未开始。

## 参考版本与证据范围

| 来源 | 本次核对的基线 | 使用方式 |
| --- | --- | --- |
| Nexio Gitee 主仓库 | `master`，`7cbcfab898f918517a7da0bec9cf29654ef22e4b`，提交时间 2026 年 10 月 1 日 22:08，提交说明 `v1.6.0beta39` | 本报告的主要参考。仓库介绍明确称其为主仓库；通过公开 API 读取固定提交的文件 |
| Nexio GitHub | `9ea4e54d093086578e574bc3d9247c3257fac9b6`，提交时间 2026 年 10 月 1 日 18:51 | 通过 Codex GitHub plugin 核对提交和核心文件，作为交叉验证 |
| 本地 Nexio 参考仓库 | 工作区仍在 7 月的旧提交；已有 `origin/master` 对象为 9 月 28 日的 `291e8b9` | 仅用于定位来源与辅助阅读，没有切换或覆盖参考工作区 |
| SleepDown | 本地提交 `784df55749d4089b790e3af6c0b0735a03fa7083`，`versionName=1.2.7_beta1`，`targetSdk=36` | 以当前工作区代码为事实来源；没有切换分支或拉取并覆盖当前基线 |

Gitee 的提交时间晚于本次核对的 GitHub 最新提交，后续跟踪适配方案应优先查看 Gitee。不过，逐文件核对发现，两个指定提交的 `MainActivity.kt`、`TabletNavSideBar.kt`、`TabletSettingsScreen.kt`、`TabletCourseManagePane.kt` 和 `TabletSwitchSchedulePane.kt` 的 Git blob SHA 完全相同。因此，这五个核心文件当前在两边提供同一套 Pad 布局，不能仅根据仓库更新时间判断它们的适配方案不同。[Gitee 主仓库][g-repo]、[Gitee 固定提交][g-commit]、[GitHub 固定提交][h-commit]。

版本名称也需要区分：Gitee 提交说明写着 `v1.6.0beta39`，该提交的构建配置仍是 `versionName=1.6.0.2-0928`、`versionCode=158`。本报告引用的是该提交的源码，未验证某个已发布 APK 是否包含全部相同实现。[Nexio 构建配置][g-build]。

本次没有运行 Nexio 或 SleepDown 的设备验收，没有测量帧率、GPU 时间或键盘弹出后的实际布局。下文明确分开描述源码事实、由几何规则推导出的风险以及建议方案。仓库里的 SleepDown 平板图片是历史展示图，仅辅助理解现有信息结构。

## Nexio 当前怎样适配大屏

### 导航侧栏

Nexio 用当前配置的 `screenWidthDp >= 600` 切换大屏路线。普通课表模式的侧栏包含今日、课程表、我的、课程管理、切换课表五个目的地；其中课程管理和切换课表属于可收起的数据管理分组。它把管理入口放进主界面，而不是要求用户每次从菜单进入另一页。[主界面判定][n-main]、[导航侧栏][n-nav]。

侧栏折叠时的玻璃面板宽度为 84dp，左边距为 12dp，内容避让合计 96dp；展开时总占位约为窗口宽度的 22%。竖屏首次进入默认折叠，展开时覆盖内容并压暗背景，内容仍按折叠轨避让；横屏展开时内容让位给侧栏。这里最值得参考的是“空间有限时覆盖，空间充足时并排”的交互分工。[侧栏宽度与内容避让][n-nav]。

动画进度主要在布局、绘制和 `graphicsLayer` 阶段读取，内容按目标宽度测量后通过位移衔接动画。这体现了控制整树重组的设计意图，但本次没有性能数据证明其帧率收益。同一实现仍包含侧栏变化后的采样刷新处理，不能直接照搬到 SleepDown 的玻璃生命周期中。[侧栏动画][n-nav]、[主界面采样交接][n-main]。

### 今日页面

进入侧栏路线后，今日页采用双栏：左侧是日期、摘要与今日助手，右侧是课程列表。两栏均使用 `weight(1f)`，内容区横向边距为 24dp，栏间距为 24dp；右侧课程可以独立滚动。[今日页面][n-today]。

这是信息职责上的拆分。迁移到 SleepDown 时应考虑今日助手还承载完整对话，不能直接把 Nexio 的等宽比例当成最终尺寸。

### 设置和管理页面

| 页面 | 源码中的组织方式 | 对 SleepDown 的参考价值 |
| --- | --- | --- |
| 设置 | 将多级入口按功能分组放在左侧，右侧直接呈现内容；左右栏各有内容采样和滚动处理 | 减少在大屏上重复进入页面，统一面板标题与操作区。[设置面板][n-settings] |
| 课程管理 | 左侧课程分组，右侧内嵌课程编辑；复用已有编辑页面，并给它传入面板自身的尺寸 | 可以边看课程列表边修改，降低跨 Activity 的切换次数。[课程管理面板][n-courses] |
| 切换课表 | 左侧课表列表，右侧显示当前课表的周网格预览 | 用户在选择课表时能同时检查内容；实际切换和保存语义仍需由 SleepDown 自己的流程决定。[课表管理面板][n-schedules] |

这三类页面的左栏都取窗口宽度的 39%，右栏取经过导航避让后的剩余内容宽度减去左栏宽度。比例基于窗口宽度，而不是重新按内容区等比分配。[设置宽度][n-settings]、[课程管理宽度][n-courses]、[课表管理宽度][n-schedules]。

### 周视图与弹层

Nexio 周视图仍然是主内容网格。大屏路线将节次列从 36dp 增至 56dp，并增加左右边距；课程行高继续使用外观配置传入的值。它没有为大屏另外定义一套课程编号或时间规则。[周视图][n-week]。

添加课程在宽度达到 600dp 时使用专门的 `BlurBottomSheetTablet`。该组件默认宽度为 560dp，默认最大高度为窗口高度的 80%，通常居中显示；其材质和弹层交接也有自己的实现。这说明大屏表单有独立的容器策略，但不代表应把该组件原样引入 SleepDown。[添加课程入口][n-add]、[平板弹层][n-sheet]。

## Nexio 方案的适用边界

以下是代码与几何推导，不是已经复现的设备故障：

- **600dp 宽的判定没有同时检查高度。** 例如 800×360dp 的手机横屏窗口也会进入侧栏路线，并启用今日双栏。对低高度窗口，顶栏、侧栏条目和表单可能争夺有限空间，应另设紧凑布局。[判定入口][n-main]。
- **导航与内容左栏会共同消耗宽度。** 在 600dp 宽的横向窗口中，展开导航占约 132dp，设置左栏占约 234dp，右栏剩余约 234dp，尚未计入分割线和内容内距。这一推导说明需要检查面板的最小可用宽度，而非只检查窗口是否超过 600dp。[导航占位][n-nav]、[设置分栏][n-settings]。
- **横竖屏默认值和当前选择状态分散在全局对象及页面状态中。** 这对现有单窗口路径有实现便利；SleepDown 的旋转、跨断点和编辑草稿应由自己的状态持有者统一管理。不能凭声明了大量 `configChanges` 就认定状态恢复完整。[导航状态][n-nav]、[课程选择状态][n-courses]、[Manifest][n-manifest]。

因此，建议借鉴 Nexio 的侧栏交互、信息分工和管理面板，按 SleepDown 的内容最小尺寸重新确定布局条件。Android 官方也将宽度与高度分别分类，并明确提示：宽度达到中等、但高度紧凑的手机横屏，不适合直接套用双栏。[窗口尺寸分类][a-window]。

## SleepDown 当前已有的基础与差距

| 部分 | 当前代码事实 | 本轮重做建议 |
| --- | --- | --- |
| 窗口信息 | 已使用 `LocalWindowInfo.containerSize` 读取窗口；大屏条件为宽度至少 600dp 且高度至少 480dp，横向平板双栏还要求宽度至少 840dp、高度至少 560dp | 保留真实窗口作为来源，将导航类型、低高度模式和页面能否分栏分别计算。[窗口规则][s-metrics] |
| 全局导航 | 首页与设置共用底部悬浮 Dock；没有按上述大屏分类切换成全局侧栏 | 大屏增加可折叠导航；低高度横屏使用窄侧轨或紧凑导航，减少底部占高。[导航装配][s-dock-host]、[Dock 实现][s-dock] |
| 日视图 | 横向平板双栏左侧为日期与今日助手，右侧为课程；其他大屏布局为最大 760dp 的居中单列 | 复用现有内容，用扣除导航和安全区后的宽度判断能否双栏。[日视图][s-day] |
| 今日助手 | 已有横向平板内嵌对话路线；其他窗口有弹层与首页展开路线 | 保留完整对话能力，统一各路线的面板约束与键盘空间。[助手布局][s-agent] |
| 设置 | 大屏已采用列表与详情双栏，并有详情栈、返回处理和退出保存交接；左栏竖向取 280–328dp、横向取 336–408dp 的范围 | 继续使用这些业务与导航能力，增加内容区最小宽度判定，减少标题和入口的重复层级。[设置装配][s-settings] |
| 课程管理 | 列表固定两列，点击课程进入已有编辑与转场流程 | 大屏增加列表与编辑面板，列表列数按面板宽度决定；保存、冲突检查和删除复用现有流程。[课程列表][s-courses] |
| 课表管理 | 已有课表选择器、预览和快速设置保存交接 | 大屏采用列表与预览并排形式，沿用现有激活和保存语义。[课表管理][s-schedules] |
| 周视图 | 已有随窗口与字体调整的行高、时间轴可读下限、紧凑显示和课程移动缩放处理 | 把宽度计算改为内容区约束，整理低高度顶栏占位，继续保留可读性和滚动能力。[周视图布局][s-week]、[行高规则][s-week-metrics] |
| 课程编辑与个性化 | 已有大屏居中编辑容器和横向靠右的个性化面板，尺寸各自在对应路线中计算 | 给弹层和面板统一的安全内容区；保留已有真实锚点与退出交接。[编辑容器][s-editor]、[个性化面板][s-personalize] |
| 导入界面 | 大屏教务浏览器与学校选择 Dock 已有约半宽的组织方式 | 后续按所在内容区约束宽度，覆盖横屏键盘、窄分屏和长网址。[浏览器 Dock][s-import-dock]、[学校选择][s-school] |

当前分类的几个例子能说明重做重点：

| 窗口尺寸 | 当前分类 | 日视图表现 |
| --- | --- | --- |
| 800×360dp | Phone | 手机单列；已有横向顶部微调，没有独立低高度页面模式 |
| 800×1280dp | Large | 居中单列，最大宽度 760dp |
| 900×500dp | Large | 居中单列，未启用横向平板双栏 |
| 840×559dp | Large | 单列 |
| 840×560dp | TabletLandscape | 日期与助手、课程列表双栏 |
| 1280×800dp | TabletLandscape | 双栏 |

这些是对 `calculateHomeAdaptiveMetrics()` 条件的直接计算，不是设备测量。分类本身没有错误；它把全局导航、主页结构和弹层尺寸都挂在少量配置上，下一轮需要让每种布局按实际内容空间作决定。[分类代码][s-metrics]、[日视图分支][s-day]。

还有两处需要重点验证：主页几何模型目前显式携带顶部与底部安全区，横屏侧边挖孔和侧边系统栏应纳入统一内容区；公共 Dialog 的安全高度有 280dp 下限，在很矮窗口或键盘占用空间时，应确认最终容器仍不超过实际可用高度。[安全内容区][s-content-rect]、[公共 Dialog][s-dialog]。本轮没有将这两项判定为已复现故障。

## 建议的布局方案

### 窗口与内容区规则

建议继续以当前窗口为依据，先扣除安全区和导航占位，再按页面最小内容尺寸决定能否并排。宽度分类采用 600dp、840dp、1200dp 的参考断点；高度低于 480dp 时优先进入紧凑模式。断点用于选布局候选，最终是否双栏还要满足列表、详情、间距的实际宽度需求。[Android 窗口分类][a-window]。

| 场景 | 导航建议 | 内容建议 |
| --- | --- | --- |
| 窄窗口，宽度低于 600dp | 保留底部 Dock，低高度时压缩顶部操作区 | 日视图单列，设置与编辑逐页呈现 |
| 宽且低的窗口，高度低于 480dp | 优先窄侧轨，减少底部占高；不足以容纳侧轨的窄窗口保留紧凑 Dock | 紧凑顶栏，课程占主要空间；管理页按需切换列表和详情 |
| 中等宽度，600–839dp 且高度充足 | 默认折叠图标轨，展开覆盖内容 | 主要使用单内容面板；详情通过导航或临时面板打开 |
| 较宽窗口，840–1199dp 且高度充足 | 折叠轨与可展开侧栏 | 满足内容最小宽度时启用双栏，空间不足时显示单面板 |
| 大窗口，宽度至少 1200dp 且高度充足 | 可默认展开侧栏，并设合理最大宽度 | 日视图、管理与设置并排；超宽内容设置最大阅读宽度 |

这是待实现的设计建议。Pad 竖屏、折叠屏展开、分屏和自由窗口共享同一套判断；导航占位变化也要重新计算内容宽度，不能沿用整窗尺寸。

### 页面组织

**大屏主界面：** 侧栏先保留课程与设置两个主目的地，将课程管理、课表管理放在管理分组。课程页内继续切换日视图和周视图。这个组织方式与 SleepDown 当前导航关系一致，能先完成大屏适配；是否将日、周视图拆成两个全局目的地，可在后续设计评审时决定。

**日视图：** 空间允许时左侧承载日期、课程摘要和今日助手，右侧承载课程列表。助手面板的初始宽度可在约 320–400dp 内评估，课程列表获得其余空间；最终下限需要结合当前文字层级和完整对话验收。中等宽度先保留单列，助手通过已有入口展开。

**周视图：** 为网格保留主要宽度，周次和日周切换进入紧凑顶栏。点击课程时，足够宽的窗口可以用详情面板；空间较小时使用现有编辑弹层。时间轴与卡片尺寸按网格自身的宽高计算，保持课程移动、缩放、冲突退回和保存交接。字体放大时允许滚动，保证时间与节次可读。

**课程管理：** 列表与编辑面板并排，列表分组、选中态和列数按所在面板决定。复用当前课程编辑内容与保存链路；切换选中课程、退出页面和窗口变化应保留或明确处理编辑草稿。

**课表管理：** 左侧列表，右侧预览与对应操作。现有课表激活、临时编辑及快速设置的保存交接继续决定写入时机；不能因新布局的列表点选而改变这些语义。

**设置：** 左侧功能入口，右侧详情；标题和工具按钮属于各自面板。沿用已有详情栈与退出保存处理。导航侧栏、设置列表与详情同时出现时，以详情的最小可用宽度决定是否收起其中一栏。

**添加与导入：** 表单采用有最大宽度、可滚动的容器，主要操作持续可达；键盘出现后按真实剩余高度布局。导入预览、用户确认和数据库写入仍沿用现有流程。

### 弹层和状态交接

公共 Dialog、Alert、Picker、QuickSheet、Popup 应继续从 SleepDown 的公共设计系统进入，并消费统一安全内容区。常驻管理面板与临时表单分别选择容器，已有锚点 Morph 保留真实锚点；窗口变化后用所属面板的新坐标重新确定目标。[公共设计系统](<D:/Android studio/CourseSchedule/docs/architecture/SLEEPDOWN_DESIGN_SYSTEM.md>)。

旋转和跨断点需要保留当前课表、日期和教学周、日周视图、列表滚动位置、选中课程、编辑草稿、设置详情栈及助手会话。状态应放在布局分支外的适当持有者中，布局变化本身不触发数据库保存。

键盘位移继续由明确的单一边界负责，避免窗口 resize、`imePadding()` 和手动补偿重复叠加。玻璃采样必须使用面板真实位置；左右栏可有独立内容采样，但完整页面 producer 与后绘制弹层 host 的关系继续沿用现有框架。侧栏运动对采样、命中区域和退出锚点的影响需要一起验收。

## 建议的实施顺序与影响范围

| 顺序 | 独立可审查的改动 | 主要影响位置 |
| --- | --- | --- |
| 1 | 统一窗口分类、安全内容区和导航占位，引入低高度模式与大屏侧栏 | `core/ui/`、现有 `HomeAdaptiveMetrics` 与应用装配 |
| 2 | 日视图、今日助手和周视图接入内容区约束，收敛紧凑顶栏与滚动留白 | `feature/home/day/`、`feature/home/week/`、`feature/agent/` |
| 3 | 设置、课程管理和课表管理形成面板流程，接通选中态与保存交接 | `feature/settings/`、`feature/course/management/`、`feature/schedule/manager/` |
| 4 | 课程编辑、个性化、导入及公共弹层统一安全区与键盘尺寸 | `feature/course/editor/`、`feature/home/overlay/`、`feature/importing/`、公共设计系统 |
| 5 | 对旋转、窗口缩放、玻璃采样与真实设备交互集中验收 | 依据实现后的实际影响执行定向验证 |

这些是后续工作边界，尚未执行。适配预计由 UI 与状态组织完成，不需要改变 Room、备份或导入协议。外观可继续使用现有 Miuix 与 SleepDown 玻璃体系；采用窗口分类不要求更换整个组件库或主题。

## 后续验收建议

以下矩阵是实施完成后需要覆盖的窗口场景，本轮尚未执行：

| 场景 | 示例窗口尺寸 | 核心检查 |
| --- | --- | --- |
| 手机竖屏 | 360×800dp | 原有课程、设置与弹层完整可用 |
| 手机横屏 | 800×360dp | 顶栏占高、侧轨条目、表单滚动、键盘与保存按钮 |
| Pad 竖屏 | 800×1280dp | 折叠导航、覆盖展开、阅读宽度与详情进入 |
| 较小横向窗口 | 840×560dp | 扣除导航后的双栏最小宽度 |
| Pad 横屏 | 1280×800dp | 课程和助手并排、管理编辑、双栏设置 |
| 窄分屏 | 500×700dp、650×700dp | 跨断点后选择、滚动与草稿保持 |
| 宽且低的自由窗口 | 1200×450dp | 高度优先的紧凑布局与弹层上界 |
| 连续调整窗口 | 宽度经过 599/600、839/840dp，高度经过 479/480dp | 导航与内容重排、选中态、焦点和采样位置 |

同时覆盖较大字体、明暗主题与壁纸、无玻璃、左右侧挖孔、手势及三键导航。关键交接场景是编辑课程过程中旋转、助手输入时缩放窗口、带草稿切换设置入口，以及侧栏展开时打开课程详情。性能验证使用相同内容和相同窗口的滚动、侧栏与弹层场景，检查实际 CPU/GPU 和采样行为后再决定优化。

本轮实际完成的是项目规范阅读、源代码核对、GitHub 与 Gitee 版本及五个核心文件的一致性比较、布局条件推导和报告检查。没有修改应用代码、构建、安装、启动应用或提交推送。

[g-repo]: https://gitee.com/com_haooz_account/hyper_schedule
[g-commit]: https://gitee.com/com_haooz_account/hyper_schedule/commit/7cbcfab898f918517a7da0bec9cf29654ef22e4b
[h-commit]: https://github.com/HaoZai000/NexioSchedule/commit/9ea4e54d093086578e574bc3d9247c3257fac9b6
[g-build]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/build.gradle.kts
[n-main]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/java/com/haooz/chedule/ui/activities/MainActivity.kt#L1180
[n-nav]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/java/com/haooz/chedule/ui/components/TabletNavSideBar.kt#L85
[n-today]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/java/com/haooz/chedule/ui/screens/TodayScreen.kt#L717
[n-settings]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/java/com/haooz/chedule/ui/screens/TabletSettingsScreen.kt#L124
[n-courses]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/java/com/haooz/chedule/ui/screens/TabletCourseManagePane.kt#L99
[n-schedules]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/java/com/haooz/chedule/ui/screens/TabletSwitchSchedulePane.kt#L88
[n-week]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/java/com/haooz/chedule/ui/screens/MainScheduleScreen.kt#L1795
[n-add]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/java/com/haooz/chedule/ui/screens/AddCourseDialog.kt#L323
[n-sheet]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/java/top/yukonga/miuix/kmp/overlay/BlurBottomSheetTablet.kt#L108
[n-manifest]: https://gitee.com/com_haooz_account/hyper_schedule/blob/7cbcfab898f918517a7da0bec9cf29654ef22e4b/app/src/main/AndroidManifest.xml
[a-window]: https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
[s-metrics]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/HomeAdaptiveMetrics.kt:242>
[s-dock-host]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/app/ui/ScheduleAppUi.kt:2880>
[s-dock]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/app/ui/ScheduleAppUi.kt:5207>
[s-day]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/day/HomeScheduleUi.kt:1930>
[s-agent]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/agent/DayAgentUi.kt:604>
[s-settings]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/app/ui/ScheduleAppUi.kt:7818>
[s-courses]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/course/management/CourseManagementUi.kt:254>
[s-schedules]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/schedule/manager/ScheduleManagerUi.kt:352>
[s-week]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/week/WeekScheduleUi.kt:426>
[s-week-metrics]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/HomeAdaptiveMetrics.kt:65>
[s-editor]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/course/editor/CourseEditorContainerOverlay.kt:440>
[s-personalize]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/overlay/HomeAnchoredMorphOverlay.kt:951>
[s-import-dock]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/EduBrowserDock.kt:247>
[s-school]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/EduSchoolSelectionUi.kt:1120>
[s-content-rect]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/HomeAdaptiveMetrics.kt:339>
[s-dialog]: <D:/Android studio/CourseSchedule/app/src/main/java/com/xiaomanjun/sleepdownschedule/core/ui/designsystem/SleepDownDialog.kt:98>
