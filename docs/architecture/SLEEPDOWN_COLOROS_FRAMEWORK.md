# SleepDown ColorOS 组件框架

本框架提供主应用的“凝光视效”可选材质，实现位于 `platform/coloros/`。`app/ui/ProvideVisualEffects` 读取偏好和运行时能力，通过 `ProvideColorOsMaterials` 接入公共控件。默认保留液态玻璃；GitHub 渠道、ColorOS 17 及以上且 COUI 能力探测成功时开放凝光选项。课程管理 Activity 的类名保留，避免改变 Manifest、Intent 和转场路由。

模式选择位于“通用设置 → 外观”。凝光生效时隐藏液态玻璃调整入口，旧页面恢复时也不创建预览与滑条；切回液态玻璃后恢复入口和已保存的模糊参数。超级性能模式仍隐藏玻璃调整，但不阻止可用设备切换视效模式。

## 装配和边界

- `MainActivity.kt` 的公共 `CourseScheduleTheme` 安装框架；`ControlMaterialRenderers` 统一提供 Dialog、Button、Card、Surface、值控件和菜单渲染能力，`HomeMaterialControls` 提供 Dock、图标按钮、搜索和命名弹窗入口。业务页面不引用厂商实现，厂商实现不引用 `app` / `feature`。
- `ColorOsNativeUi` 管理系统 APK、类加载器、资源、材质状态与跟手光；`ColorOsContext` 负责配对的主题 Context。
- `ColorOsCapsuleButton`、`ColorOsDialogs`、`ColorOsSearchField` 是共用组件；Dock、首页按钮、菜单及课程卡适配也集中在框架中。
- 不复制或打包系统 COUI 实现；运行时读取已安装、经过系统应用标志检查的 Settings / Contacts / FileManager。当前能力还受渠道、设备厂商和系统 API 支持约束，缺少组件时返回共用 Compose 实现。
- `SLEEPDOWN_EXPERIMENTAL_FEATURES` 仍是发行渠道开关；框架提取不意味着这些私有厂商接口在所有 Android 上可用。

## 胶囊按钮

既有 `LiquidButton` 是兼容入口：可点击按钮优先交给 `ColorOsCapsuleButton`，成功后直接返回，之前的 Kyant 按压、拖拽形变与高光状态不会创建。`DialogLiquidButton` 和 Alert 操作按钮直接使用同一个渲染接口，包括没有 Backdrop 的场景。

原生 `COUIButton` 负责材质、圆角、触摸、滑动取消、跟手光和触感；Compose 保留图标、文字、加载内容及原有尺寸。外层合并按钮语义并处理键盘激活，原生空文本 View 不重复暴露给无障碍。业务显式禁用状态传递给原生控件。

Compose 内容直接绘制在 COUIButton 上方，保持文字、图标和加载状态的正常更新。`ColorOsCapsuleMotionBinding` 订阅 COUIButton 自身的 `COUITouchMotionAnimator`，从同一个 `ContributeSink` 取得位移与缩放；Row 只在 graphicsLayer 块中读取这些值，不在组合阶段读取，也不创建第二套弹簧。销毁时解除回调。旧的 ViewOverlay 跨 Canvas 录制方案已因实机文字消失撤回。

原生 View 实例因主次按钮样式改变而重建时，`AndroidView` 以该实例为 key 同步重建宿主。仅替换 remember 的对象不会重新调用 factory，会留下已经解绑点击和运动监听的旧 View。菜单行、材质表面与数值控件遵循相同的实例归属。

普通按钮保留系统 secondary 交互，模糊与透明颜色层改用返回按钮的 `TYPE_FRAMEWORK_TOP_BAR_BLUR`。彩色操作通过 `accentButtonBlurParams` 与加号共用颜色层写法：基于 `TYPE_FRAMEWORK_FAB`，第一层直接使用操作颜色，保留系统第二层和混合模式；总透明度 255。普通胶囊和启用的着色胶囊都使用返回键在渐变背景上的模糊半径（`TYPE_FRAMEWORK_TOP_BAR_BLUR × 0.35`）；仅 FAB 自身保留其 0.85 倍半径。禁用按钮继续使用系统 disabled-primary 预设；确认按钮保持壁纸主题色与白字，删除等动作保留语义色。跟手光沿用系统默认光色和当前 2× 强度。

Alert 的原生窗口仅按操作槽位等结构创建，外观和标签变化不重建窗口。明暗变化更新原主题 Context 并刷新原生模糊；按钮着色按外观变化更新，避免每次内容重组都重装材质。

`clickTargetEnabled = false` 的弹层外壳、输入区域和共享手势表面不是普通按钮，不会被该入口变成可点击胶囊。它们继续使用各自的几何、采样与手势宿主。

覆盖共用入口后，现有设置、导入、AI 助理、教务浏览、课表管理、多课表、课程编辑及部分快捷操作中的按钮无需分别维护 COUI 生命周期。

## 弹窗操作顺序

`LiquidAlertActionStyle.Cancel` 表达取消、稍后、返回修改和继续编辑语义，不按显示文本判断。原生纵向操作按 Primary → Secondary → Destructive → Cancel 排列，同类动作保留声明顺序。COUI 三排的物理顺序为 neutral → positive → negative，因此 Primary 映射到 neutral、取消映射到 negative；两操作使用 positive → negative，横向时确认在右、取消在左。Compose 后备布局保留既有声明顺序。回调始终按原始 action 索引绑定。

原生按钮栏的 recommend ID 同步到实际 Primary 按钮；没有 Primary 时明确清除推荐 ID，防止破坏性动作在材质刷新后变为主题色主按钮。新弹窗按下面规则声明，不依赖声明顺序或原生槽位默认色：

| 语义 | 样式 | 例子 |
| --- | --- | --- |
| 推荐的非破坏性操作 | Primary，主题色白字 | 保存、完成、创建新课表、分享口令、授权 |
| 其他可选操作 | Secondary，普通前景 | 分享 ICS、备用下载、切换识别方式 |
| 删除或丢弃已有内容 | Destructive，红字 | 仅删除本次、删除全部、覆盖当前课表、不保存、替换恢复 |
| 关闭/返回而不执行当前决策 | Cancel，普通前景 | 取消、稍后、继续编辑、返回检查 |

恢复到空应用没有覆盖已有数据，使用 Primary；恢复报告确认存在旧数据时使用 Destructive。选择器的选中项不是确认按钮，不按此规则重排。表单标题栏仍为取消在左、完成在右。

按钮类型和文字颜色明确按业务语义设置，不继承位置默认色。分享 ICS 是 Secondary，使用普通字色；只有 Destructive 使用错误色。课程 shortcut 的删除图标和文字继续使用红色，不受弹窗排序影响。

窗口位置、材质、进入退出动画仍由 COUI 默认弹窗负责。关闭后的动作在系统退出完成后派发；表单的脏数据保护、校验与保存语义保持在业务层。

共用 `SleepDownPickerDialog` 也接入同一原生窗口入口，保留已有滚轮、色板、内容切换和确认取消逻辑；标题操作及底部操作仍可由业务提供。选择器先发送关闭请求，业务将 visible 设为 false 后由窗口退出，退出完成再卸载本次宿主。原生路径不叠加 Compose 中心玻璃层。`QuickSheetLiquidAction` 在没有 Backdrop 时也可使用原生胶囊按钮。

选择器标题及操作按钮通过 `ColorOsPickerStyle` 从当前系统对话框读取样式和尺寸，由局部样式契约传入公共选择器。未展示的测量窗口不加入 WindowManager；字体、主题变化会重新读取，其他材质保留自身尺寸。快速编辑的外部手势光效通过 `NativeFollowLightRenderer` 提供，原生宿主只绘制光、不接管手势或建立全屏模糊；课程编辑器另设聚焦光效半径，避免展开后沿用整张大表面对角线的光斑范围。

原生选择器切换内容时只保留当前控件树，通过 `animateContentSize` 执行 260 ms 单调尺寸过渡，不同时挂载新旧两套 AndroidView，也不叠加淡入淡出。窗口实例随本次打开保留，切换模式不重建窗口；非原生路径保留原过渡。

原生弹窗通过 `LocalNativeDialogHostSize` 传递外层页面的可用尺寸。壁纸取色预览与嵌套弹窗的高度限制使用这个尺寸，不能使用正在动画的 wrap-content 弹窗高度反算内容高度，否则会形成重复缩放与取色循环。

## 首页色彩和采样

- 加号保留主题色底，图标固定白色。形变起点匹配原生 FAB 的不透明主题层，向中性菜单表面连续插值。
- 加号展开与稳定态沿用右上角菜单的材质选择：明亮壁纸时使用同款中性颜色层，其余使用 COUI Popup 颜色层。原生背景贯穿形变，避免交接跳色。
- 三点菜单和课程 shortcut 通过原生 `PopupListItem.setForceTint(0)` 保留逐项图标色，通过 `setTitleColorList` 指定逐项字色。ListView 与 RecyclerView 两条适配器路线都会在重绑时读取这些属性；不再靠遍历 AdapterView 子树找行号补色。垃圾桶图标和删除文字保留 `#FF453A`。
- 加号展开菜单的行显式接收外层算出的前景色，避免行内按应用主题重新取色，覆盖壁纸下的反色结果。
- Dock 和加号仍位于完整首页快照内；框架迁移不把它们移到根页面上方。完整采样、临时变换和快照的归属仍由页面宿主管理。
- 壁纸主题色分为按钮原始色与 Dock 强调色：按钮保留原 HSV 饱和度/亮度范围，只有 Dock 结合底部实际可见背景提高对比度。颜色在后台从壁纸源图独立提取，允许暗色 swatch；不复用会过滤暗色的课程色板。黑白等没有有效色相的壁纸仍回退默认蓝色。形变起点与实际加号底色一致。
- Dock 跟手光保持 2× 原始强度，颜色与 Dock 强调色一致；重复更新材质不叠加强度。
- 首页判为深色壁纸时，Dock、按钮及菜单恢复原生深色材质；浅色壁纸使用中性天气材质，无壁纸时使用应用明暗主题。前景继续与首页文字反色保持一致。
- 原生 Dock 的后备内容通过稳定 State 传入，避免未使用的 Compose 后备函数捕获背景状态后改变原生宿主参数。shortcut 的整页模糊保留，动画进度只由绘制层读取；这一隔离不等同于实机重组次数已验证。
- 普通和并列 Dock 在应用内明暗变化时保留 Context 与绑定的原生控件，通过 `updateTheme` / `refreshAppearance` 原地刷新材质，保留融合动画状态。其他控件默认随明暗重建。并列 Dock 首次创建时显式设置展开或收起，不以缓存布尔值代替原生初始化。
- 课程共享模糊采样通过可跟踪的 `compositionLocalOf` 提供。个性化滑杆替换采样器时只更新课程消费者，不再让 static provider 刷新整页和原生 Dock。
- 凝光模式通过稳定的 State 持有开关、着色和模糊值，只有读取对应值的消费者订阅变化，框架 provider 不随拖动重新创建模式。表面着色变化仅 invalidate；只有模糊、描边、明暗或可见性变化才重新安装原生效果。
- 个性化实时整页模糊与冻结路径一样，先录制完整页面，再模糊录制层；不把变化的 RenderEffect 直接挂到原生 Dock 的祖先层。Dock 仍在页面快照及整页模糊范围内。桌面重组测试不等同于实机 GPU 材质已验收。
- 个性化预览保留原来的缩放与模糊释放。公共 Slider/开关在 ColorOS 可用时直接使用原生控件，不再创建 Kyant 控件的轨道/滑块采样器或形变节点。半屏课表设置通过 `SleepDownFloatingSheet` 使用独立的 COUI 窗口采样完整父页面；能力不可用时保留本地 Miuix 宿主。

## 数值与选项控件

`NativeValueControls` 是业务协议，`ColorOsValueControls` 负责加载系统 COUI：数值使用 `COUISeekBar`，开关使用 `COUISwitch`，导入类型等 `LiquidOptionTabs` 使用 `COUISegmentButtonLayout`。旧入口保留给调用方，优先交给原生 renderer；能力不可用时才进入原实现。原生控件负责滑块、文字、拖动、弹簧、触觉与无障碍，不再将 Compose 文本独立贴在原生分段滑块上。

横屏紧凑导航沿用液态玻璃的组合边界：外壳提供一次材质，分段控件以 `containerSurfaceEnabled=false` 只保留文字、选中条和原生运动。COUI Canvas 材质模式关闭 View 整体模糊/描边，以透明背景画笔和无背景描边预设去掉内层轨道的重复外框。侧栏展开/收起按钮保留 Compose 点击及语义，只叠加无材质的原生跟手光，不经过完整胶囊按钮 renderer。

原生分段控件包含可点击、可聚焦的 TextView 子项，容器 OnTouchListener 不能观察被子项处理的完整序列。透明 FrameLayout host 在 `dispatchTouchEvent` 入口记录 DOWN，再通过一次 `super.dispatchTouchEvent` 交给 COUI 子树处理，返回后处理 UP/CANCEL；子项保留原生点击、无障碍、弹簧、光效和触觉。横屏导航设置 `commitOnRelease=true`，拖动期间不把 Compose 的旧页码回写给原生选中条，抬手后再提交页面选择；取消时还原，禁用或销毁时释放手势。程序同步暂时解绑选择回调。侧栏切换按钮独立后绘制，使用 48dp 点击区域，与标签轨道留出间距，避免最小触控扩展和选中条按压溢出相互争抢。

Slider 的值范围与预览/提交协议独立于厂商：拖动开始打开预览，值变化只预览，正常结束先提交再结束预览；取消恢复开始值而不提交；键盘/无障碍变化无需制造触摸预览。禁用状态传入原生 View。课程模糊使用上文百分比映射。

点按轨道时，COUI 的 click spring 每帧更新内部 progress，公共值回调只在终点报告。桥接层监听同一弹簧的更新以逐帧预览背景，中间帧不吸附默认值、不提交；终点仍经原值回调吸附并提交一次。卸载控件时移除监听。

拖动接近默认值（全范围的 1.5% 内）时，同时同步 COUI 的原生 progress 和预览值，原生回传的程序更新不再次提交。离开吸附区继续跟手；键盘/无障碍的小步调整不吸附，避免无法从默认值移开。自定义时间与按节次的模式切换使用同一 `LiquidOptionTabs` / COUI 分段滑块，保留两侧未确认的选择状态。

独立使用的 `COUISwitch` 显式启用 clickable / focusable；Settings 的 preference 样式默认依赖外部行处理点击。所有页面的开关选中轨道、滑条进度与强调按钮使用公共 `CourseScheduleTheme` 的壁纸主题色；小图解码与提色按壁纸缓存，无有效色相时回退蓝色。原生 Slider 不套旧 `SliderWithSnapMarker`：默认值标记读取 COUI 的 `getProgressStart`、`getSeekBarLength` 和 `getSeekBarCenterY`，与系统轨道内边距、RTL 保持一致，不修改原生弹性形变。

## 菜单与教务面板

横屏顶部导航的 Segment 保留主题 Context 与 View，设置/课表页的明暗变化通过原地 `refresh` 更新外观，再恢复无外框协议。抬手提交的导航由宿主锁定触摸、键盘和无障碍输入，不通过禁用原生 View 打断指示器弹簧；其他分段控件继续使用正常 disabled 语义。

折叠后的顶部导航由 `HomeNavigation` 公共材质宿主包住整个胶囊，外壳、标签及左侧按钮共用原生 deformation / rubber collector；跟手光使用 `TYPE_CAPSULE_2` 和原生强度的 0.75 倍，绘制在标签与选中条下面。内轨关闭自身的容器形变和跟手光，保留滑选、触觉和指示器弹簧。背景 delegate 返回含 null 的非空 Paint 数组，跳过内轨背景绘制；透明 Paint 仍会触发 COUI 的 `clipPath`，不能用于避免这层裁剪。外壳只在完整胶囊轮廓裁剪，并在明暗变化时保留 View 与触摸动画绑定。

教务搜索框的入场透明度使用 ModulateAlpha，保留 View 级模糊对窗口前序内容的采样，底部渐变与控件共用入场进度。原生功能按钮文字只在值或外观确需变化时更新，避免逐帧重组重复调用 setter。

加号菜单通过完整 `COUITouchListView` 和 `DefaultAdapter` 处理条目点击、取消及触觉，嵌入页面的外壳使用 `ColorOsMaterialSurface` 的 View 级材质。原宿主仍维护位置、尺寸、源按钮交接和开合动画。同一个原生背景 View 贯穿展开、稳定和收回，不在动画端点替换表面；透明度采用 ModulateAlpha，避免把模糊隔离到空离屏层。原生列表启用时不再叠加 Compose 整面板按压形变。

加号菜单保留 SleepDown 的尺寸、圆角、图标、文字、间距和开合形变。`COUITouchListView` 放在原内容下面，只绘制 COUI mask 并处理触摸、逐项滑动选中、取消与触觉；行高和间隔由原布局提供，不让系统菜单测量值改写目标几何。DefaultAdapter 的偶数位置是数据行、奇数位置是分隔行，回调按数据行映射。外部滑入仍归原 Morph 手势宿主，原生条目显示相同选中反馈。

`RoundFrameLayout` 的 `COUIBackgroundBlurBuilder` 通过 `ViewRootManager` 获取窗口背景模糊，仅留给真正独立的 `COUIPopupListWindow`。页面内部加号菜单仍通过 `COUIMaterialEffect.setViewBlurEffect` 采样当前窗口前序内容。两种宿主共用 `menuBlurParams` 的颜色层和原生菜单 160 px 模糊半径；嵌入菜单使用 RoundFrameLayout 初始化的原生 edge drawable。圆角由实际 Shape 提供；加号外壳不覆盖整面板跟手光，反馈由原生列表条目负责。

普通 popup、二级设置选择列表与级联菜单恢复 SleepDown 的 `OverlayDropdownPreference` / `OverlayCascadingListPopup`，使用原布局、玻璃、定位、滑动选择和子菜单动画。移除通用 COUIPopupListWindow/assignment Dialog 替换入口；右上角原生菜单及课程 shortcut 保留各自的 COUI 实现。

回退到公共 Miuix 的多级菜单通过 `miuix-popup-material-content.patch` 和 `miuix-native-menu-body.patch` 挂载同款 COUI 外框、菜单行及返回标题，由 Miuix 负责进退场、父级冻结、点击和滑动选择。原生行提供 View 测量桥接，支持弹层的 intrinsic 宽度查询；按压状态交给原生行，公共宿主将触摸转发给背景跟手光，坐标按原生 View 的屏幕位置换算。右上角独立原生弹窗与嵌入菜单共用条目着色规则，保留项目图标和删除红色。

COUI 材质、普通 popup 和原生菜单行不因旧的超级性能模式而退回简化绘制。原生开合、形变和逐项触摸动画完整保留；窗口 animationStyle 为 0 仅避免 WindowManager 再叠加一层动画，COUI 根节点自己的弹簧动画仍运行。

ColorOS 能力可用时，首页 `HomeSwitchMotion` 不因课程数超过 10 而改成整页平移，日周和首页/设置切换保留分组追随、回弹；系统动画时长设置继续生效。切周使用原 `WeekPageTailMotion` 完整路径。

“添加单节课”横竖屏表单外壳使用 `HomeDock` 材质，保留描边和跟手光；标签和输入值通过局部前景契约跟随 Dock 的壁纸反色判断。课表快速设置及日期面板使用 `SleepDownFloatingSheet` 的原生窗口，按普通弹窗排布标题、操作和滚动内容，移除设置项外的第二层玻璃框。高度限制、保存与关闭回调保留。非原生路径继续使用浮动四圆角 Miuix 面板与已有同级材质插槽。多课表入口的淡入仍使用 ModulateAlpha。

教务灵动岛是可展开面板，使用 `TopAssistantSurface` 的原生材质和自身动态圆角；撤掉误套在外层的 `COUIButton` 胶囊，避免出现半高圆角白边。原有展开、收回与上划关闭逻辑保留。

`QuickSheetDialogMaterial` 保留给内嵌或后备表面；原生浮动窗口直接由 COUI 设置窗口背景，不在空的 Miuix popup 层内再建立背景采样。

无原生标题栏和按钮栏的自定义弹窗移除 `customPanel` 预留底边，由 Compose 提供平衡的内容 padding。浮动课表设置使用四边 16 dp；设置行内部横向留白缩至 4 dp，紧凑行上下留白 8 dp，优先让说明横向展开、减少换行。浮动窗口使用可用窗口宽度、上限 600 dp；COUI 原生背景已经包含横向透明 inset，不再额外扣除 24 dp。窗口、根布局和 builder 的内容宽度上限保持一致，其他中心选择器保留默认宽度。Compose 表单内容用 `DialogWindowProvider` 暴露实际弹窗窗口，并在 COUI 完成 show 后清除 AlertController 因未识别 Compose 文本框而添加的 ALT_FOCUSABLE_IM。进入详细设置前收起键盘，再从当前布局与 Android View 全局矩阵计算按钮位置；主页与 Dialog 的 PixelCopy 快照及按钮锚点归一到同一根画布原点。Dialog 快照失败时不使用错误的局部锚点。

多课表页进入和长按时的按钮交互锁设置 `preserveMaterialWhenDisabled`，保留原生按钮材质；触摸、键盘、点击回调及无障碍仍遵循 disabled 语义。普通业务禁用按钮继续使用系统禁用样式。COUIButton 的 `setEnabled` 即使值相同也会重置原生模糊/描边，因此仅在状态变化时调用，随后应用本次主题色；动画中的普通重组不调用此 setter。多课表页按钮使用黑色场景外观，强调按钮使用主题色；分享按钮按用户要求固定为原绿色 `#34C759`。

## 课程 shortcut

课程长按菜单复用右上角菜单的 `NativeOverflowMenu` / `COUIPopupListWindow`，由课程位置对应的透明 View 提供锚点。原生窗口负责面板材质、触摸和进入退出动画；背景仍由首页完整快照执行整页模糊，原生 Dock 处在同一快照内。编辑、复制与删除继续走原业务回调，移除项保留红色。

shortcut 的水平位置优先贴齐课程锚点起始边，空间不足时翻到另一边，再限制到原生可用窗口范围；RTL 对称处理。显示前通过原生测量和安全区计算偏移，优先向上、空间不足由系统向下避让，不在第一帧出现后跳位置。这一设置仅作用于课程 shortcut。

关闭时等待原生窗口和首页背景退出均完成，再执行后续操作；reset、页面退出和拖拽接管会取消待执行动作。系统原生能力不可用时使用既有 Compose shortcut。

锚点 View 与菜单使用相同的系统主题 Context。`PopupMenuLocateHelper` 会从 `anchor.context` 创建 `ResponsiveUIModel` 并读取系统栅格尺寸，不能传应用资源 Context。首次显示的反射异常记录到 `SleepDownCOUI` 并释放窗口，回到 Compose shortcut。该修订来自调用链检查，尚不等同于已获得用户手机的崩溃堆栈。

## 表头与 AI 助手材质

`NativeMaterialSurfaceRenderer` 复用课程凝光的原生背景 View，但不受课程凝光开关控制。周表头、日期状态胶囊、AI 卡片、会话外壳、提醒和输入框通过 glass / 设计系统入口使用该能力；缺少能力时保留原绘制。

原生 View 负责模糊、描边和跟手光，Compose 负责文字、图标、布局和编辑输入。背景不拦截输入、不创建按钮点击行为；动态形状在 draw/layer 中读取并更新原生 outline，保留既有 AI 形变及快照归属。表头的模糊半径为系统通透预设的 60%；日期状态胶囊叠加原配色的纵向渐变，上部着色 30%、底部 50%，不再使用按钮的统一实色层。

AI 外壳使用固定 Morph 容器时，原生背景 View 按容器内当前的真实边界布局，并接收当前圆角。不能仅以 inset path 裁剪铺满容器的默认胶囊。全屏矩形使用半径 0、圆角类别 3，与 COUIButton 的显式圆角路径一致。

成功使用原生材质的内容通过 `LocalNativeMaterialSurfaceActive` 获知这一状态。今日助手不再叠加旧的按压黑色遮罩，沿用原生跟手光；原生能力不可用时保留旧按压反馈。这个局部标志表示实际渲染结果，不靠厂商名猜测是否成功。

日视图 AI 助理卡片及对应卡片外壳使用 `HomeDock` 材质角色，与 Dock 共用明暗判断及模糊参数：常规为 `TYPE_FRAMEWORK_BOTTOM_BAR`，明亮壁纸时为 `TYPE_CONTENT_TRANSPARENT_BUTTON`。取消之前额外的 1.2 倍模糊和着色，不影响课程卡、表头和其他 AI 控件。

## 课程凝光与渐变模糊

课程凝光保留课程渐变色与默认 50% 着色。模糊滑杆显示 0%–100%：0% 对应系统默认半径的 20%，50% 对应系统默认值，100% 对应两倍半径；前后两段线性映射。内部仍保存半径比例，读取旧值时将低于 0.2 的值限制到新的最低强度，其余旧值保留实际效果并换算显示。偏好仅保存在派生版的独立 SharedPreferences，不改变 Room 和备份格式；拖动预览不写盘，提交后保存，预览取消时恢复已保存值。

课程与表头描边基于 COUIFloatingButton 的 `TYPE_FRAMEWORK_CIRCLE_2` 原生边缘/阴影预设。课程卡固定边缘 alpha 0.45、宽度 3 dp，保留原生角度及阴影衰减；表头保留预设。这个预设定义描边 shader，不定义轮廓半径。按 COUIButton 的顺序，先根据实际 Shape、密度、尺寸设置 `OplusMaterialCornerParams`，再安装材质描边。Kyant 连续圆角从 `RoundedRectangularShape.corners` 读取，课程卡保留实际半径，胶囊才使用半高圆角；未知或不均匀路径不强行改成胶囊。课程跟手光独立为 1.2×，按 View 当前尺寸的对角线更新半径范围，保留原生起止半径比例；表头和其他普通表面保持 2×。个性化面板显示开关、着色及模糊滑杆，全部加入行进入动画，不显示说明文字、旧轮廓光开关或折射滑杆。

所有公共 `ProgressiveBackdropBlur` / `progressiveBackdropBlur` 入口通过 `LocalNativeProgressiveBlur` 使用系统 `OplusRenderEffect.createGradientBlurEffect`。模糊效果接入现有 glass 背景采样链，沿用页面半径、方向、透明度包络、快照及坐标归属，速度取系统 `AppBarBlurHelper` 默认参数。顶部、底部和侧边入口共用这一路线，侧边字母栏保留顶部/底部消隐。

不再调用 `setUpGradientBlurBackground(View)`：它会同时添加系统页面底色的 `GradientBackgroundDrawable`，造成首页白色遮罩。当前仅调用原生模糊算子，并保留应用原有显式着色；首页原本为零的着色不会被系统底色覆盖。监听系统材质开关，关闭时不继续添加模糊效果；缺少原生能力时使用原有算法。私有原生 API 的方向及观感仍需 ColorOS 真机验收，桌面测试不能证明系统 GPU 输出。

首页和公共设置/二级页顶栏通过 `LocalToolbarBackdropBlurred` 告知图标按钮底下是否已有渐变模糊。存在渐变层时，按钮自身的第二次背景模糊半径降为对应预设的 0.35 倍；没有渐变层时仍使用完整预设。沿用各自天气/深色顶栏的颜色层和 BlendMode，保留原生材质、描边与跟手光，不开启 drawable-only 模式。此调整减轻两次模糊叠加，并不宣称只剩一次采样。

## 验证入口

日视图课程卡和日/周课程编辑外壳保留跟手光，展开态按实际尺寸计算范围，课程强度为 1.2×；不能通过 expandedOutlineLight 隐式关闭反馈。旧文曾引用 2026-10-13 的弹层与菜单补救报告，当前分支未收录原文，不能据这些名称声称已有本轮验收。

完整原生 popup 路线及其专用测试已撤回；对应 2026-10-13 浮动面板报告未收录于当前分支。`QuickScheduleSheetLifecycleTest` 检查浮动面板反复显示及保存语义，`HomeSwitchMotionTest` 检查密集页面追随、回弹、反向及结束状态。它们是按风险选择的验证入口，不表示每轮均已执行。

UI 改动使用 `assembleGithubRelease` 验证编译、R8、资源和签名。已有定向用例分别覆盖三操作排列（`ColorOsAlertSlotsTest`）、背景边界（`ColorOsDockCompositionTest`）、胶囊运动（`ColorOsCapsuleMotionTest`）、shortcut 交接（`CourseShortcutTest`）、窗口触摸（`NativeDialogTouchGateTest`）、值控件映射（`NativeValueControlsTest`）与圆角（`ColorOsMaterialCornersTest`），按实际风险选择，不机械全跑。桌面测试不加载系统 COUI，相关改动仍需实机验收。旧文引用的 2026-10-12/13 输入和 shortcut 报告当前未收录；本轮实际构建与未验收项见[2026-10-19 修订记录](../reports/2026-10-19-navigation-import-feedback.md)，后续从[当前交接](../AI_HANDOFF.md)查对应证据。

## 菜单触摸与浮动窗口转场补充（2026-10-13）

加号菜单保留原有开合与布局；原生列表负责逐项胶囊选择反馈，整面板接入右上角菜单实际使用的 `COUIPopupTouchMotionAnimator`。按系统 `makeAnimator` 启用 deformation / rubber、关闭额外 press-scale，并使用 MAX 参数；同一 collector 驱动材质和 Compose 内容的图层，关闭时取消触摸，释放时移除监听。

DefaultAdapter 会对普通行调用 `setIsRoundStyle(false)`，嵌入的加号菜单数据行需在绑定时显式开启原生胶囊 mask；分隔行不改。菜单内容与材质为兄弟层时，通过 `NativeMenuTouchSource` 转发直接触摸和从外侧滑入的事件，材质层启用 followTouch，沿用菜单 1.7× 的默认跟手光；关闭时取消光效，原生列表继续独占条目点击和滑选。

原生窗口的 `BackgroundBlurDrawable` 由窗口合成器处理，单窗口 PixelCopy 不能将它当作普通 View 像素。课表设置进入详细设置时分别保留主页面、弹窗前景和原生材质几何；目标页在主页面与弹窗文字之间重新接入同配方 COUI 材质，并与快照一同执行原有深度缩放。材料几何在收键盘后从当前原生 View 获取，窗口销毁时解除登记；不强制开启系统全局模糊、不改变保存及返回逻辑。此链路仍需设备验收。
