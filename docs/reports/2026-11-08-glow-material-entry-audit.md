# 凝光材质入口盘点与共用掉帧路径

接续 `8d77ee47`。用户要求修正右侧课程shortcut对齐、菜单点击等待、无界顶栏模糊范围；下一阶段分别梳理AI输入／发送、教务搜索／底栏／灵动岛、周视图全屏AI及表头。用户随后明确两种视效模式都会掉帧，因此不能继续把COUIKit局部采样当作整体卡顿的已证实主因。

## 本轮源码修正

- shortcut继续消费原课程列产生的物理锚点：左列左对齐、单一中列居中、右列右对齐。原来 `request.pivotX` 在材质协议边界丢失，Kit全部收到AnchorStart。现传递该值，并在Kit Menu增加AnchorEnd；RTL先由课程列产生物理锚点，再映射逻辑对齐，不重复镜像。
- 三点菜单与shortcut选定动作后立即提交一次，退场仅清理窗口。shortcut的业务动作此前还经过Controller的第二次退场等待，现为凝光动作显式启用立即提交；取消仍不提交，液态既有交接保持。
- 无界顶栏的真实内容底边是状态栏＋66dp工具栏＋46dp表头。正文方向渐变尾部由36dp缩为12dp，强模糊从表头末端前8dp开始衰减；表头位置和字体不变。

## 模式分发的实际边界

`ProvideVisualEffects` 的effectiveMode跟随已保存选择，未发现这里把液态偏好强制改回凝光。实际组件却不是只由一个模式开关决定：

- `usesCouiControls` 是凝光＋API34，控制已接入的Kit控件。
- `usesNativeControls` 还要求私有ColorOS能力。`ProvideColorOsMaterials` 目前只在此分支给 `surface` 安装 `ColorOsMaterialSurface`；尚无跨品牌Kit通用Surface实现。
- `NativeMaterialSurfaceRequest` 描述几何、角色、反馈和外部触点，却没有显式背景源。Kit不能照搬原生窗口采样接口，更不能让同窗口表面改采包含自身的完整窗口源。
- 设置页两种模式共用COUI Scaffold／Item骨架，液态只替换对应材质分支。不能用模式切换来证明所有控件、布局及模糊后端均已更换。

## 逐入口盘点

| 入口／源码 | 当前装配 | 采样与触控边界 | 下一步处理 |
| --- | --- | --- | --- |
| AI输入壳：`feature/agent/DayAgentComposer.kt` 的AgentInputLiquidCapsule | 先旧NativeMaterialSurface，不能处理时LiquidButton；不是Kit输入框 | 原生请求elasticTouchFeedback；液态有共享InteractiveHighlight、interactionEnabledAt、独立背景兄弟节点。输入选择、光标和IME仍由BasicTextField持有 | 先补显式源及手势区域协议，再独立接材质与触控；保持多行高度、光标不使背景重录、发送区不被外壳抢占 |
| AI发送：同文件AgentCapsuleSendButton／AgentSendLiquidButton；导入页ImportConversationComposer | 紧凑输入的48dp发送是无独立材质的图标命中区；其他40dp发送经LiquidButton，可进入已接入Kit Button | 发送／停止、禁用和业务状态由宿主持有；紧凑壳共享反馈，普通按钮自有反馈 | 分别处理共享壳的前景操作和独立按钮，不给两者强塞同一种FAB；不能重复迁移已走Kit的普通Button |
| AI附件／待处理胶囊：`feature/agent/AgentInputCapsules.kt` | GlassSurface的旧原生材质优先，否则液态 | 上移收缩／LazyRow排布是业务动画；里面编辑、删除、引导另有点击owner | 先作为可选触控的独立小表面处理，不把内部三按钮合成整个胶囊点击 |
| 教务学校搜索：`feature/importing/EduSchoolSelectionUi.kt` | HomeMaterialControls.SearchField目前仍由旧ColorOsSchoolSearchField提供；不具备该能力就走自定义液态分离搜索 | `overlayBackdrop = Activity underlay + edu-school-list-content`，悬浮控件位于源后同级；输入与搜索／取消按钮分离，共享形变 | 作为受控双主体处理。借用Kit材质／触控基础能力，保留键盘触发分离、历史和学校筛选；不能直接换掉业务为设置页SearchBar行为 |
| 教务浏览底栏：`feature/importing/EduBrowserDock.kt` | EduBrowserDockSurface用旧NativeSurface的HomeDock角色；失败后LiquidButton | 浏览页传入buttonBackdrop；地址输入／前后退／导入／更多共用壳，原生成功时已抑制旧液态前景形变 | 保留地址、历史和导航业务，先迁壳材质，再绑定唯一共享运动；不是首页三项Dock导航的直接复用 |
| 教务灵动岛：`feature/importing/EduImportBrowserUi.kt` 的TopAssistantSurface调用 | 旧原生材质或液态TopAssistantSurface；收起的摄像头区纯黑 | 独立TopAssistantMotion、固定内容测量、动态圆角、文本淡入／瞬态模糊；展开点击由外层持有 | 保留黑色收起态和成熟Morph，只接可变形被动材质；不能当普通胶囊按钮或标准Dialog |
| 导入状态胶囊：`feature/importing/progress/AiImportStatusCapsule.kt` | GlassSurface旧原生优先，否则液态 | 时间／速度／缓存变化另有文字动画、animateContentSize和横向滚动 | 与灵动岛分开；保留统计内容，仅替换独立表面及明确的反馈，不接管滚动 |
| 周视图全屏AI：`feature/agent/DayAgentConversationDialog.kt` | 原有Compose弹层、TopAssistantSurface、AgentInputLiquidCapsule及多层组合源 | `homeFinishedBackdrop = home-agent-surface + agentCardContentBackdrop`；全屏稳定后释放临时Morph壳；输入及窗口按钮应采完成后的表面和消息内容 | 最后处理复合窗口，复用输入壳和按钮迁移；保留全屏／回缩／IME／待发送／草稿状态，不能改成Kit标准Dialog |
| 普通周表头：`feature/home/week/WeekScheduleHeaders.kt` 的WeekHeaderPill | GlassSurface(nativeMaterial=true, blurScale=.6)，仍是旧原生优先或液态 | 表头自身为被动背景，文本和周数切换位于源外前景；通过chromeBackdrop取完整页面前缀 | 适合作为第一批纯材质迁移，保持字号、尺寸、日期联动，不附加按钮语义或整壳触控 |
| 今日蓝色胶囊：`glass/ui/GlassSurfaces.kt` 的BlueStatusGlassPill | 旧NativeSurface＋自定义染色；失败时液态采样＋轮廓光 | 染色、外轮廓和清晰文字分别装配；并非普通着色操作按钮 | 作为状态型被动表面，保留主题／调课色与轮廓顺序，不按FAB或普通主按钮替换 |

这些入口尚未完成本轮Kit迁移。上表是当前源码调用链盘点，不是视觉／性能通过清单。

## 两种模式共用的性能调查

已确认需要进入取证范围的代码代价：

1. `third-party/kyant-backdrop/.../LayerBackdropModifier.kt` 的生产源先 `drawContent()`，随后可能再次用 `onDraw` 录制同一子树。默认onDraw也是drawContent。动态源和多层组合可重复遍历；宿主glassBackdropProducer的录制门控还包括denseMaterials，不只按实际消费者需求，因此切换原生表面也不能据此断定Kyant源已停。自定义源可能故意与屏幕内容不同，不能全局删掉其中一路。
2. 同模块DrawBackdropModifier对未冻结的坐标依赖消费者，每次onGloballyPositioned都清采样录制并写neverEqual状态。与上一轮只修Kit坐标不同，这条Kyant路径也会在两种模式的未迁移表面执行。须分别测量真正移动的消费者和位置未变的布局通知。
3. `glass/ui/NexioProgressiveBlur.kt` 的全分辨率后备链包含32点渐进采样和去噪。是否启用取决于native operator、方向和性能策略，不只取决于视效模式。这里是着色器成本线索，不能在没有GPU数据时断言它就是主因，亦不能声称采样次数简单相乘就是设备实际成本。
4. `HomeBackgroundBlurLayer`、首页整页快照、共享转场和TopAssistantMotion也在两种模式共用。需同时检查源录制次数、UI线程、RenderThread、GPU、首帧窗口创建及温度／刷新率。

上述代码大多早于本轮反馈，源码上存在代价不等于已经证明本次回归由它引入。上一轮Kit开销修正不能覆盖这些路径，也不能据其测试通过声称整体掉帧已解决。本轮没有为猜测主因而降低采样清晰度、删除融合／折射或重写成熟Morph。

建议接续顺序：先在同一APK／设备／操作下采集两种模式的真实路由与FrameTimeline；按现有BackdropDiagnostics／GlassPerformanceDiagnostics和Macrobenchmark区分CPU录制与GPU开销。随后先接纯材质表头，再处理AI输入／发送与教务搜索，再处理浏览底栏，最后处理灵动岛／全屏AI复合窗口。每步分别核对材质、反馈owner、业务手势和源层级。

## 当前验证边界

- 菜单修正提交 `0b5fffc4`，无界模糊范围提交 `f118f77a`，可分别回退。
- 差异／引用检查通过。定向测试30项通过（4m25s）：Kit13项、应用17项，0失败／错误／跳过。包括右侧锚点从课程传递到材质协议、RTL／可见区定位、有效选择立即提交且只执行一次、取消／退场屏障及源卡材质恢复。日志 `tmp/coui-shortcut-immediate-tests-20261108.log`。
- 完整签名 `assembleGithubRelease --console=plain --no-parallel --max-workers=1 --no-daemon` 通过（6m12s，195任务，31执行／164复用）。保留R8、资源压缩、lintVital和V2签名。日志 `tmp/coui-shortcut-immediate-release-20261108.log`；上述状态测试不能代替本轮真机排版或帧耗时验收。
- 设备枚举的首次提权调用曾因自动审批额度用尽被拦截，提示05:11后再试。当时继续完成源码与文档，没有绕过审批换缓存或通道。08:31（上海时间）核对时间后重新审核成功，已启动本轮定向构建／测试。
- 本轮重新枚举：5037／5038均为SDK37.0.1服务，没有USB设备或无线mDNS端点，也未发现其他ADB服务。用户明确回复“暂时无法连接，先交付修正和材质梳理”，本轮按此收尾，不追加设备连接／安装要求；整体掉帧根因和真机帧耗时继续保持未验证。

## 本地交付

| 归档文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| `SleepDown-1.2.8-beta2-coui-shortcut-audit-20261108-e4428ad8.apk` | 7,891,084 | `E4428AD8B0399874F3E67846225F3B002D964F1B452E92368DE9D73F4AF70879` |
| 同名 `-mapping.txt` | 163,103,386 | `C3A29CC8D0C29CECC2A05FE40B099BD0A139B074DEF94D3F0F73D54231EFFC51` |

归档位于 `tmp/review-apks`，APK／mapping副本与同次原构建输出哈希一致。APK内嵌源码 `a6163bc895fc6ad94e5f2d7ece2f53a877ded3cd`；包名／版本 `com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / 36`、minSdk26／targetSdk36不变。V2证书SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。元数据 `tmp/coui-shortcut-audit-delivery-20261108.json`。

新包成功核对后，按用户既有清理授权删除上一份 `d5cf727f` 归档mapping（163,103,350字节）；当前归档只保留本包的一份精确mapping。旧APK及 `output/` 保留。未安装、启动、推送或发布。
