# SleepDown 设计系统

SleepDown 设计系统是业务页面与 Miuix、Backdrop、Compose 实现之间的稳定 UI 语言。它的目标不是让每个界面长得一模一样，而是让不同页面在层级、留白、材质、前景色和交互反馈上属于同一个产品。

规范分为两层：

- **不变量**保证家族感、可读性和技术安全；
- **场景风格**允许设置页、内容页、沉浸任务页按信息密度和任务目标做不同布局。

换句话说，新页面先选择最接近的“句式”，再写自己的内容，不复制另一个页面的全部外观。

## 设计 DNA

SleepDown 的界面由五个共同特征形成，而不是由单个圆角或某一种玻璃决定：

1. **层级清楚**：页面背景、内容分组、临时浮层和最终决策各占一层，不用多层同权卡片堆叠。
2. **留白连续**：页面边缘、分组之间、组内行和控件内部形成从疏到密的节奏。
3. **材质有来源**：玻璃必须知道采样哪个 Backdrop domain；无玻璃降级仍保留相同几何和语义。
4. **前景色成套**：同一表面内的标题、正文、字段、图标、箭头和禁用态来自同一对比度体系。
5. **运动有来处**：页面从真实锚点进入并回到真实锚点；稳定终态不长期持有转场 clip、快照或离屏壳。

只要这五点一致，设置表单可以规整，AI 对话可以更自由，附件预览也可以完全沉浸，而不会脱离 SleepDown 风格。

## 代码入口

```text
core/ui/designsystem/
├── SleepDownDesignTokens.kt    # 稳定尺寸、间距、圆角与前景色来源
├── SleepDownSecondaryPage.kt   # 设置/内容/沉浸三种可覆盖的内容节奏
├── SleepDownDialog.kt          # Dialog/Alert/Overlay、标题栏、按钮、输入字段
└── SleepDownQuickSheet.kt      # 选择器外壳、QuickSheet 表面与操作按钮
```

页面级 Backdrop 和顶栏仍由既有 `DetailActivityScaffold` / `GlassMiuixDetailActivityScaffold` 管理。设计系统提供其下方的内容节奏，不接管 Activity、导航、Morph 或业务状态。

| 使用场景 | 统一入口 |
| --- | --- |
| 二级页页面壳 | `DetailActivityScaffold` |
| 设置型、内容型或沉浸型列表节奏 | `SleepDownSecondaryPageList` |
| 标准或紧凑居中玻璃表单 | `CenterLiquidDialog` / `LiquidDialogSurface` |
| 表单标题、取消、完成 | `LiquidDialogHeader` |
| 一至三个确认操作 | `LiquidAlertDialog` / `LiquidAlertActions` |
| 必须采样同窗口底层的提醒 | `LiquidAlertOverlay` |
| 日期、时间、数字或小型编辑器 | `SleepDownPickerDialog` |
| 胶囊按钮与输入框 | `DialogLiquidButton` / `DialogCapsuleField` |
| 改造版 Miuix 居中弹窗表面 | `centeredDialogBackdropModifier`（业务优先调用上层组件） |
| 底部或内层 QuickSheet 表面 | `quickSheetBackdropModifier` |
| QuickSheet 操作 | `QuickSheetLiquidAction` |

## 二级页面的三种风格

### 1. 设置型 `Settings`

适合开关、选择、说明和表单项，例如通用设置、AI 设置、今日助手设置、课表详细设置。

- 页面优先使用 Miuix 大标题或既有设置顶栏；进入具体任务时可改为紧凑顶栏。
- 内容按“分类标题 → `SettingsGroup` → 行/分隔线”组织。
- 页面水平边距为 16dp，一级分组间距为 14dp。
- 一个设置组表达一个概念域，不为每一行再套独立卡片。
- 保存方式属于业务会话：即时保存、离页提交和显式完成都可以，但不能藏进设计系统组件。

### 2. 内容型 `Content`

适合历史列表、处理进度、阅读详情和课程管理等以内容为主的页面。

- 通常使用 58dp 紧凑顶栏；标题可左对齐，也可在左右操作对称时居中。
- 页面水平边距通常仍为 16dp，内容块间距采用稍紧的 12dp。
- 卡片用于表达对象或阶段，不把每一段正文都做成玻璃卡。
- 主操作跟随内容落点；跨列表的全局操作才放顶栏。

### 3. 沉浸型 `Immersive`

适合 WebView、附件全屏预览、截图选取、Morph 运动中的任务壳等需要自行控制坐标的页面。

- 可以不使用统一列表边距，但必须独立处理 `safeDrawing`、状态栏和导航栏。
- 可以隐藏标准顶栏，但必须保留明确且可返回的真实控件。
- 稳定 Open 终态应释放运动阶段的 clip、RenderEffect 和 Offscreen 合成。
- 沉浸不是忽略规范：前景色来源、触控尺寸、降级材质和返回语义仍必须一致。

`SleepDownSecondaryPageStyle` 是起点，不是限制。页面确有理由时可覆盖水平边距和分组间距；覆盖值应描述信息密度，而不是复制另一个页面的偶然数字。

## 二级页结构

一个普通二级页从外到内按以下顺序组织：

```text
Activity / Morph host
└── DetailActivityScaffold              # 背景、Backdrop、顶栏、安全区
    └── SleepDownSecondaryPageList      # 页面节奏与内容边距
        ├── 分类标题（可选）
        ├── 主分组 / 主对象
        ├── 次分组 / 补充信息
        └── 页面级操作或安全底部留白
```

### 顶栏选择

| 页面任务 | 推荐顶栏 |
| --- | --- |
| 多组设置、需要滚动理解层级 | Miuix 大标题 |
| 列表、详情、单一任务 | 紧凑顶栏，标题左对齐 |
| 左右操作对称、标题是稳定页面身份 | 紧凑顶栏，标题居中 |
| 全屏预览或网页任务 | 沉浸顶栏或临时控制层 |

居中标题必须为返回按钮和右侧操作保留相同视觉空间；如果右侧操作数量会变化，优先左对齐，避免标题看似居中但实际偏移。

### 页面留白

- 顶部位置由 `detailContentTopPadding()` 或 Scaffold 提供的真实 inner padding 决定，业务页面不重复叠加状态栏高度。
- 底部留白根据页面宿主决定：带首页 Dock 的页面保留 Dock 清空区；独立 Activity 只需要安全区和操作区。
- 平板详情 Pane 可增加外层内容 inset，但组内行高、文字层级和操作尺寸不随意放大。
- 新页面不得为单一分辨率写固定屏幕宽高；窄屏、横屏和字体缩放依靠约束自适应。

## 表面与内容层级

### 页面背景

设置与常规二级页使用 `settingsPageBackground(settingsVisualConfig(config))`。页面背景负责稳定明暗，不能把壁纸前景色直接带进设置卡片。

### 分组表面

`SettingsGroup` 表达同一概念下的一组操作。组内使用行、分隔线和必要的内联说明；除非存在独立点击对象或视觉状态，不再嵌套同等重量的玻璃卡。

### 内联展开

轻量、上下文强且展开后仍能清楚归属当前对象的内容，优先原地展开，例如课程管理详情中的周次。原地展开必须真实参与测量、点击和动画，不能只画一层视觉覆盖。

### 临时浮层

选择器、确认和短编辑属于临时层。它们必须使用正确的同窗口 Backdrop，且不能让 consumer 采样自己所在的 producer。跨 Activity 页面继续使用已有单向 Backdrop 与快照交接结构。

## 弹窗选择

先按任务选择弹窗家族，再决定内容；不要先复制一个长得接近的弹窗。

| 任务 | 组件 | 特征 |
| --- | --- | --- |
| 不可逆确认、错误、权限解释 | `LiquidAlertDialog` | 短标题、短正文、1–3 个明确动作 |
| 同一 Compose 根层且必须采样下层 | `LiquidAlertOverlay` | 与 Alert 同语义，挂在已录制内容之后 |
| 日期、时间、数字、多列选择、小型编辑 | `SleepDownPickerDialog` | 稳定居中 QuickSheet，正文布局可自由组合 |
| 多字段创建/编辑 | `CenterLiquidDialog` | 独立标题栏、可滚动正文、明确完成动作 |
| 与真实卡片/按钮连续变形的复杂编辑 | 既有 Morph 外壳 + 设计系统内容 | Morph 管轨迹，设计系统管内容 |
| 强上下文单选且内容很短 | 下拉或原地展开 | 不为一项选择打开全尺寸表单 |

### 弹窗正文节奏

- Picker 默认内容间距 12dp，密集数字控件可以保留自己的列宽算法。
- 表单使用“标题栏 → 可滚动正文 → 固定操作区”，键盘出现时只允许一个层级负责 IME 位移。
- Alert 正文过长时内部滚动，动作区保持可见；不要把说明和主按钮拆成两个不同材质来源。
- 二操作通常横排；三个操作仅在标签能完整显示时横排，否则按组件既有纵向规则。
- 破坏性操作使用红色语义，但不能用红色填满整个普通设置组。

### 居中弹窗材质

所有改造版 Miuix 居中弹窗属于同一表面家族，包括确认 Alert、日期/时间/数字选择器、多列节次选择器和短编辑器；内容布局可以不同，但外层不得各自复制材质。

- 外轮廓统一使用 Kyant Shapes `RoundedRectangle(..., Continuous)`，即项目的 G2 连续曲率圆角；不得再在其后叠加 `RoundedCornerShape` 二次裁切。
- 弹窗宽度上限为 300dp，外圆角为 34dp，正文内距沿用 SleepDown 二版；Alert 恢复按标题、正文和操作数量进行内容驱动测量，不再用 184dp/304dp 总高或 272×108dp 正文区强行撑满。标准动作胶囊高 48dp；双按钮横排、三按钮纵排，操作间距沿用二版的 10dp。
- 居中大标题与首段正文/控件恢复 SleepDown 二版的内容节奏；正文只在确实过长时进入最大 240dp 的内部滚动，不用空白填充去凑固定高度。
- 动作按钮恢复完整 `Capsule` 胶囊，标准高度 48dp。普通/危险次级操作默认使用轻微灰色中性表面且不画独立描边；危险操作只把文字变红。所有动作按钮关闭按压放大、发光、阴影与动态描边，只使用普通压暗反馈。
- 弹窗外壳恢复 SleepDown 二版的中性 Kyant 材质，只把原 12dp 模糊提升到 16dp；背景层仍只使用一条预构建的 10dp plain blur。两者职责分离：前者塑造弹窗表面，后者以较宽的单次采样覆盖并充分打散二级页大标题，同时维持低成本背景压暗和景深。
- 不再把 Apple 参考稿的整面渐变、强边沿、外投影和多层内阴影用于弹窗外壳；按钮保留自己的现版蓝色/中性玻璃材质。业务页面也不得叠加整面黑/白蒙版，否则会抵消底层染色。
- 弹出与关闭使用 Miuix 原中心缩放/淡入动画。根场景只在中心弹窗存续期间录制完整下层画面：Miuix `Scaffold` 将页面、TopBar 和较低业务 Overlay 放进 `underlayModifier`，Popup/Dialog host 作为后绘制的兄弟节点，结构上不可能进入自己采样的 producer。首页与二级 Activity 都必须让弹窗在拥有完整业务 underlay 的当前 Scaffold host 中渲染；不得把二级页弹窗转交给一个只拥有部分背景的外层 host。首页不得额外手动挂一个可能落回 producer 祖先内的 `MiuixPopupHost`。禁止把 producer 挂到包含 host 的 Scaffold/祖先，也禁止退回“录制时临时隐藏弹窗”的 draw-mask；这种绘制排除无法消除 RenderNode 父子环。背景使用一条预构建的 10dp plain blur 链，显隐进度只改变合成 alpha，并同步加入轻量压暗；不按动画帧重建 RenderEffect，也不挂 lens、vibrancy、阴影或液态材质。退出节点必须保留到进度归零后再卸载。首次以 `show=true` 进入 Composition 的选择器必须先提交 0 进度帧，再启动弹簧，避免首帧直接接近终点而看似没有动画。
- 中心弹窗不使用预测性返回的缩放/位移进度；返回手势只由顶层弹窗消费，完成后走同一关闭动画与背景模糊卸载链路，底层页面不得参与手势形变。
- Android 12 以下或无 Backdrop 时，保留相同 G2 轮廓和内容语义，仅把采样材质替换为稳定实色。
- 新 Picker 优先直接使用 `SleepDownPickerDialog`；确因标题色、根层挂载或内容测量需要直接使用 Miuix `OverlayDialog` 时，必须通过 `rememberCenteredDialogVisuals` 同时接入表面、背景模糊、动画进度与非预测性返回规则，不能只复制 `surfaceModifier`。

### Popup 菜单材质

- 普通下拉、列表和级联 Popup 统一复用 NexioSchedule `4de678c` 的 Miuix 改造链，并由 `SleepDownLiquidDropdownPreference` / `SleepDownLiquidCascadingPopup` 调用。普通 Popup 为 25dp 外壳；条目使用 8dp 外边距、17dp 连续圆角、14dp×10.5dp 内距和 15.6sp 标题。进入从真实锚点以 0.24 倍、0.78/232 弹簧展开，退出使用 0.78/400 弹簧；缩放原点随进度移向中心，同时保留 8dp 瞬态模糊、两行初始揭示、末段阴影以及设置项文字/箭头 180ms 淡出交接。
- 级联折叠菜单同步采用参考实现的 16dp 外框、锚点冻结、父标题/子项顺序和父子层 Morph。SleepDown 只保留根层 Backdrop 防自采样、应用明暗主题、真实 IME 可用窗口和 Overlay 返回优先级；不得在业务页另写一套近似 Popup。材质使用 24dp blur + vibrancy，浅色白色 0.72、深色 `#242424` 0.80，并保留轻量高光边沿；无 Backdrop 时必须保持相同几何。
- 级联 Popup 的返回处理使用 Overlay 优先级，必须先于 IME 消费：第一次返回收二级菜单，第二次返回收一级菜单，第三次才由页面/系统收键盘。空白处点击不逐层回退，而是第一次同时关闭一、二级菜单，并保持输入焦点与 IME；菜单卸载后的第二次空白点击再交给页面的键盘策略。
- Popup host 与页面 producer 同样采用兄弟层拓扑；Popup 只消费该页面显式传入的采样域。找不到 Backdrop 时按相同几何回退稳定表面，不得跨根层寻找 producer。
- 多课表快速设置不属于 Popup：必须保留 `QuickScheduleSettingsSheets` 在 1.2.0 中已验收的半屏 QuickSheet 高度、内容顺序、操作按钮和日期子 Sheet。可以通过公共 QuickSheet token 等价更新材质，不得被 Popup 视觉样式、级联布局或菜单选中态改造污染。
- 多课表快速设置的“详细设置”先保存当前 QuickSheet 草稿，再通过 `QuickSheetToSettingsDetail` 跨 Activity Morph 打开 `SettingsDetailActivity`。目标窗口在 `super.onCreate` 前切换为透明 Morph 窗口，直接使用实时按钮 bounds 展开；QuickSheet 留在原 Activity 下层，返回后原样恢复。不得再复制或裁切 Bitmap、挂局部 Overlay 或写第二套 Morph。

## 色彩与玻璃前景色

同一表面只能选择一种前景色来源：

| 表面 | 前景色来源 |
| --- | --- |
| 动态壁纸仍清晰可见的玻璃表单 | `sleepDownGlassForegroundColor(config)` |
| 有稳定面板底色的 Alert、QuickSheet、设置页 | `sleepDownPanelForegroundColor(config)` / 对应主题 `LocalContentColor` |
| 主操作 | 固定主色表面 + 白色前景 |
| 破坏性操作 | 破坏性色及其配套表面 |

标题、摘要、字段值、占位文字、图标、箭头和禁用态必须沿同一来源降低 alpha，不能在同一卡片内混用壁纸反色、应用主题色和写死黑白。

玻璃不可用、Android 12 以下或 `backdrop == null` 时，同一组件负责不透明降级。降级只替换材质，不改变圆角、布局、点击范围和语义。

## 稳定 Token

以下值来自已经使用的界面，只在独立视觉批次中调整：

| 领域 | 当前值 |
| --- | --- |
| 紧凑二级页顶栏 | 58dp |
| 顶栏返回按钮 | 42dp |
| 二级页水平内容边距 | 16dp |
| 设置型分组间距 | 14dp |
| 内容型内容块间距 | 12dp |
| 标准 Dialog 圆角 | 32dp |
| 改造版 Miuix 居中弹窗尺寸 / 圆角 | 最大宽 300dp、内容驱动高度 / 34dp Kyant Continuous（G2） |
| 居中弹窗标准动作胶囊 | 48dp 高、完整 `Capsule`；中性轻灰、无独立描边/高光/阴影，仅压暗反馈；双按钮横排、三按钮纵排，间距 10dp |
| 居中弹窗正文 | 二版内容节奏；过长正文最大 240dp 内部滚动 |
| 居中弹窗背景模糊 | 单条预构建 10dp plain blur、连续 alpha 与压暗、随弹窗生命周期挂载/卸载 |
| 居中弹窗外壳模糊上限 | 16dp |
| 普通 Popup / 级联 Popup 外壳圆角 | 25dp / 16dp |
| 级联 Popup 最小宽度 | 168dp |
| 内层 QuickSheet 圆角 | 24dp |
| Dialog 标题栏高度 | 70dp |
| Dialog 操作高度 | 50dp |
| Picker 默认正文间距 / 微边距 | 12dp / 2dp |

Token 表示全局语言。单个成熟 Morph 的源/目标圆角、轨迹、时序和弹簧参数继续留在 renderer，不因为数值相似就搬进全局 Token。

## Motion 与页面交接

- 同类入口直接复用已经验收的 Morph spec/controller，不重新拼一条“差不多”的轨迹。
- 设计系统组件不持有导航 session、Activity snapshot、打开/关闭 generation 或最终清理句柄。
- Opening/Closing 的临时 clip、模糊和缓存只在运动阶段存在；Open 页面必须是完整、可交互的真实内容。
- 弹窗挂载在完成背景录制后的根 host；producer 与 host 必须是兄弟层，不能只靠 draw-time 排除避免自采样。
- 减少动效时保留空间关系和源/目标语义，不能以无锚点中心淡入替代成熟返回链路。

## IME 与输入区

输入页必须先明确谁拥有键盘位移：Window resize、`imePadding()` 或手动 inset 补偿三者只能有一个最终负责人。

- Activity 使用 `ADJUST_NOTHING` 时，底部输入区可以按 IME inset 手动移动，但要扣除系统已经通过 resize/pan 应用的物理底边位移。
- OEM 可能同时 resize 和 pan，不能只取两者较大值；应以窗口根布局底边的真实移动量判断。
- IME 关闭动画可能先归零 inset、后恢复根布局，隐藏态 baseline 要等待布局稳定后再提交。
- 列表底部 padding 只用于保证最后一项可滚到输入区上方，不再独立补一次 IME 高度。
- 切换附件预览、历史页、窗口尺寸或横竖屏时要重新建立会话基线，不能沿用旧窗口坐标。

## 示例

### 设置型二级页

```kotlin
DetailActivityScaffold(
    title = "通知设置",
    config = state.config,
    onBack = onBack
) { backdrop ->
    SleepDownSecondaryPageList(
        contentTopPadding = detailContentTopPadding(),
        contentBottomPadding = pageBottomPadding
    ) {
        item {
            GlassPreferenceSection("课程提醒") {
                SettingsGroup(backdrop, state.config) {
                    SettingsToggleRow(/* ... */)
                }
            }
        }
    }
}
```

### 可自定义正文的 Picker

```kotlin
SleepDownPickerDialog(
    show = showPicker,
    title = "选择时间",
    onDismissRequest = { showPicker = false },
    backdrop = popupBackdrop,
    config = state.config,
    contentPadding = PaddingValues(SleepDownDesignTokens.QuickSheet.PickerContentPadding)
) {
    TimePickerContent(/* ... */)
    PickerActions(/* ... */)
}
```

## 新页面决策顺序

1. 明确页面任务：设置、内容、沉浸，还是锚定 Morph 详情。
2. 选择既有页面壳和顶栏策略，不在业务文件新建 Backdrop 拓扑。
3. 选择内容节奏；只有真实信息密度不同才覆盖边距或间距。
4. 判断内容应放分组、原地展开、Picker、Alert 还是完整表单。
5. 统一表面内全部前景色来源，并实现同几何的无玻璃降级。
6. 明确保存语义、返回语义和 IME 位移所有者。
7. 验收窄屏、平板/横屏、字体缩放、明暗主题、明暗壁纸和无 Backdrop。
8. 若涉及 Morph，再验收 Opening、Open、Closing、快速返回和真实锚点恢复。

## 允许的差异与禁止的差异

允许：

- 因任务不同选择大标题、紧凑顶栏或沉浸控制层；
- 因信息密度调整内容间距、列数和卡片内部排版；
- 因主次关系选择内联操作、底部操作或顶栏操作；
- 成熟页面保留自己的 Morph 几何和专用视觉细节。

禁止：

- 在业务页面复制一套 Dialog window、QuickSheet material 或无玻璃 fallback；
- 同一表面混用不同前景色来源；
- 为单设备写死安全区或屏幕尺寸；
- 把业务保存、数据库写入或转场 session 放进设计系统；
- 为追求“统一”改写已验收的课程编辑器、首页菜单、个性化面板或跨 Activity Morph。

## 当前迁移状态

- 通用设置、AI 设置和今日助手设置已等价接入 `SleepDownSecondaryPageList(Settings)`；原 16dp 边距、14dp 分组间距和 Dock 底部空间不变。
- 助手记忆、日期和时间选择器已接入 `SleepDownPickerDialog`；各自正文密度、控件和操作仍独立。
- 现有确认 Alert、跳转周数、课程编辑选择器，以及详细设置内的节次/时段/时间/课间 Picker 已统一接入 `centeredDialogBackdropModifier`；窗口行为和正文结构不变。
- 其余成熟 Picker 的窗口壳继续按“结构完全等价再迁移”的原则渐进收口，但所有居中表面已经共享同一材质入口。
- `SettingsGroup`、`SettingsToggleRow` 等设置组件在迁移前仍需分离业务会话状态，不能把保存逻辑塞进设计系统。
- 课程编辑器、首页菜单、个性化面板和跨 Activity 转场外壳不因本规范存在而改写。
