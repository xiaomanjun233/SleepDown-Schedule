# SleepDown 功能图标替换清单

盘点基线为 `codex/home-render-architecture` 的 `b01475ec`。应用目前接入的是随工程构建的 COUI Kit 图标子集，导出 27 个名字，其中 `Wallpaper` 是 `Personalise` 的别名。用户给出的官网图库有 75 个名字、89 个样式条目，官网矢量使用 `CouiIcons.Filled` 和 `CouiIcons.Outlined`，与本项目的平铺 API 分开标注。[官方图库](https://itoseo.github.io/COUI-Kit/icons/index.html) · [官方接入说明](https://github.com/ItosEO/COUI-Kit/blob/main/docs/components/icons.md) · [工程内置图标](../../third_party/coui-kit/src/main/java/com/itos/coui/icons/CouiIcons.kt)

清单按图标在界面里表达的动作匹配。几个共用资源 ID 在不同入口有不同含义，例如 `ic_edit` 同时出现在首页个性化和课程编辑。以每个入口的操作为准。

用户选择了下表第 1、2、4–15 项。齿轮在底栏以外统一用 `Filled.Setting`，手机底栏未选中用 `Outlined.Setting`，选中用 `Filled.Setting`。日视图采用 `Filled.Certificate`，周视图采用工程内置 `Calendar`。实施源码为 `19fc63d5`。

用户随后指定多课表管理用 `Filled.MultiPhone`，设置里的 AI 助理用 `Filled.Headset`，日视图助手展开后用彩色 `Filled.AI` 替换标题旁的星形。编辑弹窗的选择行复用列表的 `Next`，随弹窗前景着色。实施源码为 `32013731`。

## ColorOS 日历原生资源

从实机读取 `com.coloros.calendar` 17.6.30（17006030）的 APK，SHA-256 为 `1F3ED719CF4D57FCB2F38F455A6F4C35771FCF4F29D04955FC4E4020FF2ECE8D`。资源表中 `ic_bottom_nav_calendar` 根据选中状态切换 `ic_bottom_nav_calendar_normal` 和 `ic_bottom_nav_calendar_selected`，分别为 24dp 线框和实心。周视图选择器的 `ic_view_picker_week` 是 80 × 120dp 的周网格预览，`ic_launcher_os13` 是 APK 的静态桌面图标。SVG 和对照图位于本地 `tmp/material-device-qa/calendar-17630-icons/`。

用户随后指定周视图使用日历底栏的两种状态。`c44afa11` 保留原始路径和填充透明度，使用各入口的前景色着色。凝光、液态和兼容底栏，展开侧栏与菜单视图切换共用 `homeActionIcon` 的选中判断。

| SleepDown 资源 | ColorOS APK 资源 | 原始二进制 XML SHA-256 |
| --- | --- | --- |
| `ic_week_view` | `res/xp1.xml` | `c139c09ef0a4b7ead43544b7772e838e259aa3aa2c6ed3581c93d597aa5b843e` |
| `ic_week_view_selected` | `res/vb1.xml` | `d4739cdd356e5250741484164d7fec6e3636b773f85acdf838831c754bc68b67` |

## 官网图库推荐

表中的 `Outlined.`、`Filled.` 指官网的 `CouiIcons` 样式。加号、返回、关闭也各有工程内置版本可直接引用。

| 界面或操作 | 现有图标 | 推荐候选 | 取舍 |
| --- | --- | --- | --- |
| 顶栏返回、课程卡详情返回、教务和导入进度返回 | `ic_arrow_back`，部分已有 `Back` | `Outlined.Back` | 沿用 RTL 镜像。课表上／下周、浏览器前进／后退各自保持方向。 |
| 关闭弹层、取消队列输入、退出复制放置 | `ic_close`、`ic_close_light`、Material `Close` | `Outlined.Close` | 同一关闭形状随界面着色。系统实时通知的关闭资源另列。 |
| 首页加号、空格新增课程、添加安排、课表新建及其他已有加号 | `ic_add_course`、Material `Add`，部分已有 Kit `Add` | `Outlined.Add` | 把图形接到现有按钮，沿用变形锚点和按钮点击区。 |
| 选校搜索框、独立搜索图标、助手的搜索步骤 | Material `Search`、`ic_agent_search` | `Outlined.Search` | 工程内已有的设置搜索框继续可直接用 `SearchField`。 |
| 首页设置、侧栏设置、通用设置、助手设置步骤 | Material `Settings`、`ic_settings`、`ic_agent_settings` | `Outlined.Setting` | 官网名称是单数 `Setting`，线框适合顶栏和导航，彩色分类行可选 `Filled.Setting`。 |
| 通知设置分类 | Material `Notifications` | `Filled.Bell` | ColorOS 设置的铃铛自带右上圆点，适用于这一分类。 |
| 小组件设置分类 | Material `Widgets` | `Filled.Grid` | 四格贴合组件集合，单独的小组件预览内容由各尺寸负责。 |
| 当前课表设置分类 | Material `Tune` | `Outlined.SliderCustom` | 两条调节线，和通用设置的齿轮容易区分。 |
| 课程管理，包括首页菜单和左侧栏 | Miuix `Notes`、`ic_courses` | `Filled.BookOpen` | 用书本表达课程目录，课表主导航仍使用有时间表结构的图标。 |
| 课程详情和特殊课程的地点 | Material `LocationOn` | `Filled.Location` | 地点图标继续随文字着色。 |
| 课程详情的教师 | Material `PersonOutline` | `Filled.UserCircle` | 单人头像与地点并列，读法明确。 |
| 相册选图、助手／教务图片附件和小组件图片 | `ic_image_attachment` | `Outlined.Photo` | 仅图片入口用山形图标，任意文件的附件仍用回形针。 |
| 备份说明中的壁纸与图片集合 | `ic_material_photo_library` | `Outlined.Album` | 图库图形适用于多张图片的说明卡。 |
| 教务浏览器的安全锁、登录安全提示 | Material `Lock`、`ic_lock_lock` | `Filled.Lock` | 保留安全状态文案，WebDAV 密码显隐单列。 |
| AI 设置入口 | Material `AutoAwesome` | `Filled.AI` | 使用官方多色 AI 图标。需要与周围单色按钮同色时可选 `Outlined.WandSpark`。 |
| 关于页「发现新版本」的标志 | Material `NorthEast` | `Filled.AppUpdate` | 绿色圆底和白色向上箭头表达应用升级。 |
| 备份校验成功 | `ic_material_verified_user` | `Filled.ShieldCheck` | 只放在成功状态，恢复时需要注意的事项各保留自己的提示。 |

## 工程内可直接统一

| 界面或操作 | 现有图标 | 推荐候选 | 取舍 |
| --- | --- | --- | --- |
| 普通删除，课程管理、课程快捷菜单、助手待处理队列等 | `ic_trash`、`ic_delete_history`，部分已有 Kit `Delete` | `Delete` | 滑删行的桶盖动画按下表单独处理。 |
| 选择确认、提交成功的普通对勾 | `ic_check` | `Check` | 选择态对勾沿用当前颜色和尺寸。 |
| 设置行或导入步骤的右向进入箭头 | 已有 Kit `Next`、Miuix `ArrowRight`、`ic_chevron_right` | `Next` | 设置行使用 12 × 24dp。官网仓库也导出 `Outlined.Next`，本次图库数据未列出这一项。 |
| 设置项的弹出选项 | 已有 Kit `Popup` | `Popup` | 保留上下双箭头，更新日志展开按钮的单向箭头仍有独立语义。 |
| 跳转周数、日期提示、导入记录的日期及备份课表摘要 | `ic_material_event`、Material `CalendarToday`／`DateRange` | `Calendar` | 普通日期用途可以统一，首页今日／周视图导航分别保留可区分的图形。 |

## 按位置筛选

| 界面或操作 | 现有图标 | 推荐候选 | 取舍 |
| --- | --- | --- | --- |
| 更多操作 | 更新页已用 Kit `More`，首页与教务还有横三点 | `Outlined.More` | 官网原形是竖三点。横向布局可共用同一矢量并转 90°，沿用原按钮触区。 |
| 首页个性化 | `ic_edit`／Miuix `Edit` | 内置 `Personalise` | 手机与两侧面板表达外观配置，只在「个性化」入口使用，课程编辑沿用铅笔。 |
| 左侧栏展开 | Miuix `Sidebar` | `Filled.PanelEnterLeft` | 左栏和箭头方向匹配。收为顶栏用 `ic_tab_bar`，官网 `LayoutSpliced` 画的是分栏布局。 |
| AI 导入和助手能力入口 | 共用 `ic_ai_import`，部分用闪光／聊天图标 | `Outlined.WandSpark` | 用在明确标注 AI 的入口，普通文件、口令和教务导入继续区分。 |

## 当前缺少同义图标的部分

| 页面或组件 | 保留的图标或形态 | 理由 |
| --- | --- | --- |
| 今日、课程表、多课表管理、回到今天 | `HomeTodayIcon`、Miuix `Weeks`、`Layers`／`ic_schedule_layers`、主题色「今」 | 日期、周网格、多份课表和今天是四个动作。 |
| 课程编辑、课程复制、特殊课程的安排列表 | Miuix `Edit`、`ic_copy`、Material `ViewAgenda` | 个性化手机、书本、日期框各有自己的用途。 |
| 课时、时刻、导入耗时 | Material `Schedule`、`ic_ai_timer` | 内置 `Time` 画的是带铃闹钟，`Countdown` 是沙漏倒计时，课时与已耗时继续用时钟。 |
| 刷新、下载、分享、导入历史 | `ic_refresh`／Material `Sync`、`ic_download`、Miuix `Share`／`ic_share_schedule`、`ic_history` | 官网 `Transfer` 是双向传输，`AppUpdate` 专用于应用升级。 |
| 常规导入、教务导入、文件、网页模式、账号显隐 | Miuix `Import`／`Community`、`ic_school_import`、Material `AttachFile`、`ic_web_mode`、`Visibility`／`VisibilityOff` | 每对图标对应不同功能。官网 `SquareEyeClosed` 是带底板的单个闭眼，缺少配对的睁眼。 |
| 备份与恢复、读取历史备份、恢复提醒 | Material `SettingsBackupRestore`、`ic_material_restore`／`ic_material_settings_backup_restore` | 使用原来的恢复环箭头，避免将本地备份画成网络存储。 |
| 今日助手的发送、停止、全屏、还原、思考、引导、步骤和缓存／速度 | Material `ArrowUpward`、`Stop`、`OpenInFull`、`CloseFullscreen`、`ChatBubble` 与对应 `ic_agent_*`／`ic_ai_*` | 区分互逆操作和运行状态，搜索、设置、图片已在上表处理。 |
| 展开更新日志、复制提示、自动刷新来源 | Material `ExpandMore`、`Info`、Miuix `ContactsCircle` | `Popup` 是双箭头，内置 `Info` 画的是手机。来源是联系人时可按实际语义考虑 `Filled.UserCircle`。 |
| 课程颜色和壁纸选色 | `ic_color_*`、Material `Palette`、取色器 | 保留纯色、渐变、彩色和滴管之间的区别。 |
| 导入历史的滑删动画 | `ic_delete_history` 的桶盖和桶身 | 若以后换桶形，需同步核对桶盖分区与铰点。 |
| GitHub、应用和捐赠图标 | `ic_github`、启动器及应用品牌、`Favorite`／`EmojiEvents` | 官网 `Gift` 表达礼物，`Featured` 表达精选。 |
| 系统通知、勿扰、灵动岛和小组件中的系统资源 | 通知小图标、`ic_live_*`、`ic_moon_light`、RemoteViews 图形 | 系统消费者使用资源 ID 或位图。图标尺寸、透明通道和两套明暗资源要单独对应。 |

主要调用入口：[首页动作图标](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/core/ui/chrome/HomeActionIcons.kt) · [设置分类](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/settings/SettingsRootScreen.kt) · [侧栏](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/HomeSidebar.kt) · [课程管理](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/course/management/CourseManagementUi.kt) · [教务浏览器](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/EduBrowserDock.kt) · [备份恢复](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/backup/BackupRestoreSettingsUi.kt) · [助手工具栏](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/agent/DayAgentComposer.kt)
