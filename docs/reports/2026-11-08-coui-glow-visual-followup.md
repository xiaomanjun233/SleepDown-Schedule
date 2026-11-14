# 凝光材质、动效与入口复查

接续上一轮签名包 `54f6fbac` 与 `codex/couikit-dialog` 的迁移工作。保留私有实现用于对照，运行路线仍使用 Kit 和公开 Android API。

| 场景 | 证据与修复 |
| --- | --- |
| 课程卡与公共描边 | E 盘 `work/src1/sources/com/coui/appcompat/COUIMaterialStrokeEffect.java` 的 switch 将课程胶囊映射到类型 1、内容圆形到类型 2。两者线宽均为 2.5 dp；胶囊亮／暗描边为 .25／.8，内晕为 .10／.03；圆形为 .25／.30。独立轮廓原来把光乘以透明底色，整圈输出为零。改用对应的 Kit 材质几何，并单独输出光贡献。 |
| 底部泛光 | 课程卡继续使用系统 Level 4 的 `#42FFFFFF`、4.899994 dp 和 10.399994 dp。原独立光层的卡内部分被后画的模糊及染色覆盖。现在在卡底色之后、文字之前合成，并按边界缓存焦散 RenderEffect。真实背景亮度、边缘衰减还需 ColorOS 实机逐帧对照。 |
| 模糊、跟手光、快捷菜单 | 课程编辑器从原卡片偏好过渡到 22 dp，个性化沿用其后备路径的 22 dp，助理日卡改为 18 dp。调低普通控件跟手光；课程卡恢复明暗差异；三点与 shortcut 单独提高跟手光，暗色用 PLUS 提供可见亮度。居中课程列按实际安全区的中点分到左右两侧，菜单动作仍立即提交一次。菜单亮度属于宿主调校，需要实机验收。 |
| 课程编辑器和内层弹窗 | 编辑器旧路径有降采样，新路径漏传。恢复背景纹理每轴半分辨率（原先约四分之一像素数，不计取整），保留物理模糊半径和原交接。去掉同形状的重复离屏裁剪。Dialog 沿动画逆变换取样，开场保持同一套 Kawase 网格，避免按缩放重建九轮纹理。仅凭代码无法把首次融球的所有帧耗时归到上述工作。 |
| 两种色板、分享 | 保持 Kit 标题和底部动作，色板正文在窗口较短时可滚动。两个分享入口明确使用 Kit 正文留白及等宽动作。 |
| 教务浏览页 | 独立 WebView 下层继续作为采样源。顶栏即使隔离 WebView 也可使用 Kit 返回键；标题与返回图标根据网页顶部采样色切换黑白。岛有按压反馈，可从展开面板向上滑收回；WebView 的原生触点观察竖滑但不拦截网页输入。普通导入与自动刷新登录复用这条链路。 |
| 小组件设置分页 | 原入口漏传已存在的设置页下层，导致 Kit 指示器拒绝接管。现在传入该源，保留分页的点击、拖动和无障碍调整。桌面 RemoteViews 独立绘制。 |

签名 GitHub Release 和商店渠道 Kotlin 编译均通过（`tmp/coui-glow-followup-release.log`）。测试包为 `SleepDown-1.2.8-beta2-COUIKit-visual-20261108-2819ae84.apk`，7,836,988 字节，SHA-256 `2819AE845ED797C7D83C464E58F1357FADD96FF8D59922DA67842A38366BBF87`。已校验 APK V2 签名，最低 API 26；扫描 DEX 未发现所核对的私有凝光材质类引用。

定向回归通过：Kit 13 项、应用 42 项（`tmp/coui-glow-followup-tests.log`）。包括菜单按压及取消、快捷菜单和锚点、课程色板和星期布局、Dialog 关闭反向、分页点拖动及教务窗口动效。

Pixel 10 Pro AVD（Android API 36，SwiftShader）覆盖安装通过。旧包和新版都用相同凝光偏好、内置壁纸与周表课程，均能打开编辑器和内层星期选择；新版的周一到周日保持同一行。以 `dumpsys gfxinfo framestats` 对两种首次打开操作各采一轮，每组仅 3 个有效样本：编辑器 P95 约 1436→1706 ms，星期弹窗约 1934→2351 ms。这组软件渲染数据不能证明掉帧改善，也不足以判断真机回归；首次融球和稳定帧耗时保留真机验收。

同一 AVD 上，点击小组件进度点将预览从「今日课程 4×2」切换到「AI 助理 4×2」；多课表管理的分享弹窗显示短口令、JSON 和取消，未触发实际分享。签名 APK 已上传用户的 Google Drive，回读文件名及 7,836,988 字节相符，未上传 mapping。

## 2026-11-09 续修

课程卡模糊与描边对照 E 盘 `erofs/system_ext/priv-app/OplusLauncher/OplusLauncher.apk`。从 `FolderIcon` 沿 `OplusPreviewBackground.initBlurDrawable` 追到 `BlurUtils.BlurMaterialColor` 和 `RenderEffectDrawable.refreshEffectDrawableParam`。常规小文件夹是类型 2，使用均匀的 `#66` 混色层和 `SMALL_FOLDER` 纹理描边。课程卡采用这一混色上限和近远两侧的描边曲线，原滑杆继续控制染色量。系统的模糊级别 120 属于它自己的引擎，应用保留现有课程卡模糊范围。底部泛光单独按当前 Dock 加号实现，复用 `CouiFabDefaults.shadow` 的光学边界、衰减和速度，以课程色计算光色，并在卡体下合成。

课程编辑器和个性化调色盘共用同一套色格、选中标记及紧凑的颜色预览。两处存储均为不透明颜色，因此取下原来显示却无法保存的透明度滑条。壁纸取色改为稳定的竖幅预览，底部使用标准弹窗动作区。返回调色盘会保留当前草稿，确认等待壁纸可采样。带吸色按钮的标题用两边等宽留白对齐整块弹窗的中心。

第一次和第二次融球另用 NVIDIA 硬件加速的 Android 16 AVD 追踪。搜索第一次打开时，主线程的 `postAndWait` 等待约 304 ms，RenderThread 的 `shader_compile` 最长约 301 ms，其中驱动链接约 290 ms。焦点请求约 22 ms。第二次也出现了尚未命中缓存的 shader 组合，最长编译约 306 ms。两次切回周视图的 Dock 追踪没有同类 shader 事件，主要开销是 RenderThread 图层提交，以及第一轮更多的 ART 即时编译。追踪保存在 `tmp/coui-search-gpu.perfetto-trace` 与 `tmp/coui-dock-gpu.perfetto-trace`。

此前双表面为两个材料槽生成不同的 shader 源码。Kit 现用同一套程序，两个槽各自保留参数与几何。这去掉了由材料槽编号造成的程序变体。不同背景与效果链仍可形成不同 GPU 程序，真机冷启动的改善幅度需要同场景对照。

本轮 Kit 13 项与应用 43 项定向测试通过，覆盖课程色板布局、壁纸取色固定底栏与返回行为。GitHub Release 的 R8、资源压缩、lint 和 V2 签名通过。候选 `SleepDown-1.2.8-beta2-COUIKit-visual-20261109-238dfae1.apk` 为 7,820,604 字节，SHA-256 为 `238DFAE1537B713E7370092BBED3A3E17BF7DE80ED429AD4FE8F3E01F22D29FB`。APK 的 DEX 包含共享 shader 与新取色入口，未发现已核对的凝光私有材质类引用。构建与测试日志见 `tmp/coui-glow-release-tuned-20261109.log` 和 `tmp/coui-glow-tests-tuned-20261109.log`。

商店 Kotlin 编译通过。新版覆盖安装到同一台 Android 16 AVD，使用 RTX 4060 宿主 OpenGL 后端。每个入口分别在新进程中执行首次操作与再次操作，观测到用户描述的冷暖差别。

| 入口 | 首次 → 再次帧耗时 P95 | 首次 → 再次最长驱动链接 | 有效帧数 |
| --- | ---: | ---: | ---: |
| 搜索展开 | 456.2 → 56.6 ms | 321.6 → 19.7 ms | 28 / 76 |
| Dock 恢复加号 | 395.5 → 89.2 ms | 289.0 → 6.1 ms | 41 / 63 |

首次触发期间，主线程等待 RenderThread 最长约 350 ms（搜索）及 340 ms（Dock）。RenderThread 的 `shader_compile` 中有 `cache_miss` 和 `driver_link_program`，因此预先构造 `RuntimeShader` 只能覆盖一部分工作。当前共享程序减少了一个源码变量，但两个入口仍会在第一次真正绘制时编译新组合。Perfetto 能定位到 HWUI 的绘制和链接，未给出可反推具体某个 AGSL 的程序标识。这一轮只能确认冷路径和缓存命中差别，无法从两轮不同的缓存状态推导新旧版本的提速幅度。新版追踪为 `tmp/coui-search-visual1109.perfetto-trace` 与 `tmp/coui-dock-visual1109.perfetto-trace`。

个性化彩色调色盘及壁纸取色在同一 AVD 上点验通过。壁纸预览显示四个取色点，底部返回和确认都可见，返回进入原调色盘。测试截图为 `tmp/coui-personalization-1109.png` 与 `tmp/coui-wallpaper-1109.png`。签名 APK 上传 Google Drive 后回读核对了文件名和字节数，未上传 mapping。[本轮签名 APK](https://drive.google.com/file/d/1HeGrFyKWwox4tgD4XoIIRHt20ca7ZZCE/view?usp=drivesdk)

教务 WebView 用本地测试页面覆盖深浅顶栏、点击灵动岛及上滑收回。深色页面顶栏为 `#182238`，标题采样为白色；浅色页面顶栏为 `#FFF6DE`，标题采样为黑色。点击后显示展开的标题和面板，上滑后恢复收起状态，距离首次点击约 2.4 秒，早于 5 秒自动收回。截图见 `tmp/coui-edu-{dark,expanded,swiped,light}-1109.png`。

## 11-09 搜索与 Dock 首次融球、首页弹窗和教务长列表

- 搜索与 Dock 的改动落在项目内置的 `third_party/coui-kit`。`CouiFloatingSurfaces.kt` 复用实际的双主体材质 RenderEffect，在控件空闲时以 96×48 像素离屏目标执行一次，每帧最多一个材质槽。`SearchBar.kt` 与 `CouiDockBar.kt` 启用该路线。Dock 的动画宽度和内容 alpha/scale 分别在布局和绘制时读取，减少组件重组。预绘标记发生在首次点击前，追踪时长约 26–141 ms，包含逐帧等待和读回。首触发尚有较慢的帧，后台 shader 缓存状态未做严格 A/B 隔离。
- 首页范围按用户澄清限定为开弹窗与打断动画。课程编辑器的 22 dp 稳定态模糊保持原值，过渡限定为四档，反向复用已创建的效果。Kit Dialog 的反向从当前位置和透明度恢复，只在初次挂载时等待首帧。Kawase 的九轮效果共用同一个 shader builder。模拟器实测打开课程编辑器、打开「跳转周数」、动画中返回并立即重开课程编辑器。
- 旧包的「北京交通大学教学支撑平台」学校行触摸范围到 y=2764，搜索框从 y=2580 开始，二者重叠。新版将列表视口和侧边索引限制在搜索框上方。滚动后最后一行「北京工商大学」的底边为 y=2580，搜索框顶边为 y=2592，点选成功打开对应教务浏览器。「安徽财经大学」打开校内和 WebVPN 两个适配器选项。

Android 16 硬件加速 AVD 单次采样，旧包 `238dfae1` 与新包 `c5d3312b` 对照。模拟器结果用于定位，ColorOS 及其他品牌真机的帧耗时和 GPU 观感待复核。

| 场景 | 旧包 | 新包 |
| --- | ---: | ---: |
| 搜索首开／再次 P95 | 456.2／56.6 ms | 122.4／40.5 ms |
| Dock 首次恢复加号／再次 P95 | 395.5／89.2 ms | 58.5／27.8 ms |
| 首页课程编辑器首开 P95 | 216.2 ms | 160.9 ms |
| 首页周数弹窗首开 P95 | 179.3 ms | 145.3 ms |
| 首页编辑器打断重开，主线程最长 RenderThread 等待 | 90.5 ms | 41.9 ms |
| 编辑器内节次弹窗首开 P95／最慢帧 | 440.4／440.4 ms | 126.2／250.4 ms |

首次触发时 RenderThread 最长 `driver_link_program`，搜索由 321.6 降为 9.0 ms，Dock 由 289.0 降为 7.3 ms。首页编辑器首开窗口内 shader 编译总时长为 126.7／130.7 ms，最长驱动链接 21.9／3.5 ms，主线程最长 RenderThread 等待 88.7／58.1 ms。耗时尖峰下降，编译总量仍待进一步验证。原始追踪位于 `tmp/coui-{search,dock,home-dialog,home-week}-motion1109.perfetto-trace`。

节次弹窗对照样本为 12／22 帧，两个包各覆盖安装后在新进程中测试。新版首个窗口 `setSurface` 约 70.8 ms，其中 `dequeueBuffer` 约 60.9 ms，同期主界面仍在提交全屏图层。首次卡顿还包含原生弹窗建窗和缓冲分配。再次打开的 P95 为 167.6／136.5 ms，仍有慢帧。追踪见 `tmp/coui-nested-{homebase,motion1109}.perfetto-trace`。

签名 Release 候选 `c5d3312b` 已上传 Drive，回读核对文件名、MIME 和 7,820,604 字节。Kit 13 项及应用 43 项定向测试全部通过，商店 Release Kotlin 编译通过。仅上传 APK。[签名 APK](https://drive.google.com/file/d/1RzPzMi0MLLqpk-DUDj8SO0Qn0yiEew-q/view?usp=drivesdk)

## 11-09 双主体、打断交接与助理续修

- 搜索和 Dock 在 Kit 共用新的双主体 shader。第三个主体恒为空的路线去掉第三球 SDF 和另外两组 `blendPair`，仍用同一 program 和 `MATERIAL_INDEX` 区分两个材质槽。三主体路线继续使用原有计算。搜索投影与取消文字的透明度在绘制阶段读取，零透明投影不录制，取消文字复用有限的模糊档位。
- 两种材质的课程编辑器和个性化面板统一使用原液态的几何进度交接，按实际展开比例切换源文字和表单文字。编辑器保留源课卡文字的过渡模糊，复用效果，在无模糊时使用 `ModulateAlpha`。动态尺寸分支由卡片材质和正文分别裁剪，共同的外裁剪只用于固定离屏包络。
- 日视图助理展开的 Dock 材质此前遗漏 18 dp 模糊参数，现与收起卡片共用。保留展开的背景采样、触摸和返回交接。
- 锥光复核 `s1 → DirectionAwareSeekBar → COUIIntentSeekBar → IntentConicProgressRenderer / ConicLightEffectRenderer`。亮边阈值为轨道 20%，独立使用响应 0.5 的临界弹簧。形状、透明度用原厂响应 0.9 / 0.3，退出均为 0.4。低进度调制在 OFF 到 PLAYING 参数插值后应用，避免小进度头尾形状提前放大。继续使用 SCREEN 和原轨道宽度相关的白色亮边。
- 卡片焦散沿用加号的颜色和 SDF，左右光区由各内收 4 dp 调整为各外放 2 dp，底部外延从 11.2 dp 收到 7.5 dp。仅调整课程卡焦散范围。

中间包 `80ed9d3c` 的同次模拟器测试中，搜索首次／再次 P95 为 253.0／72.6 ms，Dock 为 243.3／262.7 ms。搜索首次 `driver_link_program` 最长 170.6 ms，Dock 首次与再次分别为 153.8／166.2 ms。这一组是在双主体精简后取得，旧的小尺寸纯色预绘仍无法覆盖实际图层的全部组合。新一轮 Kit 预绘改用可用视口内的真实控件尺寸和下层采样，加入焦散链，保留不超过 96×48 像素的外层读回。控件忙时中止剩余预绘。

同一包的凝光和液态玻璃均通过编辑器打断重开的功能点验。该次最长 RenderThread 等待分别为 108.1 和 74.2 ms，凝光仍慢于液态。两组帧样本不多，且等待发生在整窗绘制期间。记录保留原始追踪，供区分普通 GPU 提交与 shader 冷编译。

针对差异进一步检查 `ScheduleAppUi`。液态编辑器直接绘制，凝光还由 `RetainedMorphLayer` 录制并回放整个动效层。Kit 编辑器已没有旧厂商 View 的这层要求，改为同一直接绘制路径。动画中的点击仍由 `morphDismissSwipe` 和原会话注册器处理，课卡锚点与保存交接共用既有实现。

最新候选 `f0cde239` 在 Android 16 硬件加速 AVD 上点验。两种模式都能打开编辑器、动画中返回并重开。同设备不同构建和进程的缓存状态可能有差异，下面记录单次追踪，不外推到真机。

| 场景 | 中间包 `80ed9d3c` | 最新包 `f0cde239` |
| --- | ---: | ---: |
| 搜索首次／再次 P95 | 253.0／72.6 ms | 95.9／39.1 ms |
| Dock 首次／再次 P95 | 243.3／262.7 ms | 67.7／45.7 ms |
| 凝光编辑器首开 P95 | 265.3 ms | 188.6 ms |
| 凝光编辑器打断重开最长 RenderThread 等待 | 108.1 ms | 27.0 ms |
| 液态编辑器打断重开最长 RenderThread 等待 | 74.2 ms | 41.7 ms |

搜索及 Dock 首次触发内最长驱动链接约为 6.4／6.6 ms，旧候选分别为 170.6／153.8 ms。新版已有部分预绘工作与输入时间相交，搜索首开仍有 173.3 ms 的个别慢帧，不能据此判定融球完全流畅。追踪位于 `tmp/coui-{search-livewarm1109,dock-livewarm1109,home-dialog-glowlivewarm,home-dialog-liquidlivewarm}.perfetto-trace`。

模拟器使用本地测试课 `GlowQA`，在第 1 节进行中点验进度条亮边，展开助理后看到背景模糊、对话正文、附件和输入框。返回先收起 IME，再回到课时卡片。截图位于 `tmp/coui-day-livewarm.png` 与 `tmp/coui-day-expanded-livewarm.png`。ColorOS 实机下的锥光扩散及课程卡泛光待目测对照。

GitHub Release Kotlin、完整签名 Release、商店 Release Kotlin 与 Kit 13 项测试均通过。签名 APK 仍为版本 `1.2.8_beta2`、versionCode 36、minSdk 26、targetSdk 36，V2 签名通过。DEX 未检出已核对的 ColorOS 私有材质标记。Drive 回读核对 `f0cde239` 的名称、MIME 和 7,820,604 字节，仅上传 APK。[最新签名 APK](https://drive.google.com/file/d/1hVHmQ9R7BicoxojkrDzLaD92N9EBKT3q/view?usp=drivesdk)

## 11-09 复查整窗绘制与动画背景

接到性能仍不合格的反馈后，从 `f0cde239` 重测。搜索首开慢帧主要出现在绘制提交和 RenderThread 同步，已有的驱动链接预绘不足以改善这些开销。`perfbase1` 中搜索首次 1.25 秒窗口包含 42 次 `1280×2856` 离屏层绘制，Dock 首次 57 次。新代码让 Dock 页面源和不透明设置根正文使用录制指令，材质仍在各自采样区隔离。设置窗口只在弹层采样时保留窗口源，顶栏渐进 Gaussian 的权重用同一公式递推，避免每个采样点重复算指数。用 32 位浮点模拟半径 2 至 180，归一化权重误差上限对应约 0.031 个 8 位色阶。

首页冻结动效让清晰和模糊背景引用同一次页面录制，模糊纹理每轴从二分之一缩到三分之一，物理模糊半径仍为 22 dp，清晰端仍按原分辨率绘制。融球已完成的预绘按窗口和材质配方复用，跳页时释放预绘的记录引用。

第一版 `3eb8bf0b` 同一 AVD 开机周期按候选、旧包、候选顺序复测。搜索与 Dock 首次 1.25 秒均未再记录到 `1280×2856` 离屏层绘制。相对旧包首次追踪，搜索的 RenderThread `flush layers` 累计 395.3→60.2 ms，Dock 516.8→93.6 ms。单次帧 P95 波动明显，尤其重装后第一次启动。编辑器首开 P50 从同次开机旧包的 114.9 ms 降至候选两次的 37.7／62.4 ms。一次候选追踪中，首开仍有 48.0 ms 的 Compose 测量与布局、46.5 ms 的单次着色器编译和最长 96.6 ms 的 RenderThread 同步等待。紧接的打断重开最长同步等待为 25.2 ms。

周数弹窗在第一版仍用独立 Android 窗口。打开追踪记录一次 55.2 ms 的窗口缓冲分配及 149.9 ms 的界面遍历，候选两次首开 P95 仍为 234.1／221.2 ms。随后试验使用首页已有弹层宿主，点验外部点击、返回、取消和确认均通过。同一开机周期按同窗、独立窗口、同窗顺序复测，P95 为 127.1／135.5／130.5 ms，P50 为 99.1／47.5／90.8 ms。FrameTimeline 分开看弹窗和已存在的加号菜单后，独立弹窗 P50 为 32.5 ms，同窗主界面为 58.8／54.2 ms。独立窗口的 27.2 ms 缓冲分配在同窗样本消失，但同窗的持续帧耗时上升，故撤回并保留独立窗口。

同窗试验包的搜索追踪捕获一次 195.1 ms 的 `SearchBar` 即时编译，并用同次构建的混淆映射确认了 Kit 方法身份。首次搜索 1.25 秒窗口中，同 APK 从 ART `verify` 改为全量 `speed`，JIT 编译记录从 117 降至 12，累计 318.7→36.7 ms，首开 P50 24.4→15.7 ms、P95 80.7→65.5 ms，复开 P95 73.3→81.1 ms。全量预编译仅用于诊断。Kit 加入三条 `HP` 基线规则，覆盖追踪中的 `SearchBar`、`CouiShadowRenderer.draw` 和 `rememberCouiSpotlight`，复用 Android 的安装配置处理。

Kit 13 项和应用 32 项定向测试通过。覆盖设置搜索兼容、背景冻结策略、Dialog 反向和 Dock 锚点，零失败、错误及跳过。日志位于 `tmp/coui-perfhost-tests-20261109.log`。实验追踪使用同一硬件加速 Pixel 10 Pro AVD，场景采样来自本地测试课表。设备切换、横屏大字体、ColorOS GPU 观感和物理帧耗时仍需验收。

商店 Release Kotlin 与 GitHub 完整签名 Release 均通过。最终 APK `45d09ec3` 使用与 `f0cde239` 一致的 V2 签名证书，版本 `1.2.8_beta2 / 36`，minSdk 26、targetSdk 36，7,820,604 字节。R8 输出仍含三条目标方法的热配置，APK 内含 `baseline.prof` 和 `baseline.profm`。扫描 DEX 未检出已核对的旧凝光私有材质标记。

最终签名包在同一 AVD 开机周期先强制 ART `verify`，再通过 ProfileInstaller 安装包内配置并触发 `speed-profile`。安装广播返回 1，系统报告编译状态为 `speed-profile`。搜索首开 P50 从 36.2 降至 20.9 ms，P95 从 94.7 降至 54.8 ms。首次 1.25 秒内 JIT 记录从 150 次降至 32 次，累计 449.9→145.8 ms。搜索栏与跟手光各从 1 次变为 0 次，阴影方法两次均未在此窗口被 JIT。复开 P95 为 38.0／96.3 ms，个别慢帧仍在。此处为手动触发系统编译的对照，侧载后的生效时机取决于设备何时处理安装配置。

最终包同次复测，Dock 首次与复开 P95 为 43.8／20.6 ms。搜索与 Dock 首次仍无 `1280×2856` 离屏层绘制，`flush layers` 分别累计 64.8／92.5 ms。编辑器能在关闭中打断重开，最长 RenderThread 同步等待 19.3 ms。编辑器和周数弹窗首开 P95 分别为 132.6／129.7 ms，未达到流畅验收。编辑器首开仍有 65.5 ms 的 RenderThread 同步等待与 22.3 ms 的单次着色器编译。最终测试追踪见 `tmp/coui-{search-perfprofile-verify,search-perfprofile-installed,dock-perfprofile-installed,home-dialog-perfprofile-installed,home-week-perfprofile-installed}.perfetto-trace`。

已将签名 APK 上传到用户的 Drive 并回读核对名称、MIME 和大小。SHA-256 `45D09EC35D4A0F373ACD549D520986AE5F55AC3A5167F6B206573F313A54E280`。[签名 APK](https://drive.google.com/file/d/1G5WvI1eH-B9lr5YeAB-_hqbY8Ti0fVus/view?usp=drivesdk)

## 11-09 上游同步、编辑器页点和卡片材质

用户反馈实际卡顿仍在，并指出编辑器进度点和课程卡渐变遗漏。GitHub 的 `main` 从 `520478b9` 前进 22 个提交至 `261658dc`。在临时克隆对 14 个有变动或新增的核心 Kotlin 文件做三方同步，保留宿主新增的采样及动画改动。变化包括关闭系统动画时的滑条松手恢复、DynamicAnimation 1.1.0、常驻搜索按主题染色、日夜色板和独立搜索图标，以及顶部标题和 FAB／底栏颜色参数。可选图标模块、网站和 Demo 不属于已接入的核心模块。[上游提交对比](https://github.com/ItosEO/COUI-Kit/compare/520478b9305780b078bed1096ce68f727c594f14...261658dc200c143c5ea883591662479344d25ba1)

编辑器的直接绘制和窗口内入口把表单下层 Backdrop 传到公共页点，补齐原先因空采样源退回旧圆点的路线。课程卡原来把同一课程色平铺，现在复用已有底部渐变，顶部采用 55% 的染色强度，底部保持滑杆决定的峰值；材质和文字仍分层。日视图助理收起态的凝光模糊从 18 dp 调为 14 dp，展开保持 18 dp。

编辑器上一包的首开追踪有 14 次 1280×2856 的整窗离屏绘制、最长 65.5 ms 渲染同步等待。凝光编辑器试用已有中性 Home 录制指令，让局部采样层和降采样景深各自隔离。搜索复开上一包最长 88.3 ms 出现在 EGL 缓冲交换。设置根页把 IME 避让移到结果视口，使上方融球和采样宿主不随键盘缩短。保留独立窗口的周数和内层选择器。

在同一 Pixel 10 Pro AVD 开机周期按新版、旧包 `45d09ec3`、仅含页点和绘制改动的 `3c413ddb`、新版顺序测量。四次都使用 ART `verify`。编辑器首开 P95 分别为 147.0／157.4／145.7／185.2 ms。新版第一次追踪中整窗离屏绘制从旧包 14 次变为 0，但帧耗时未呈现稳定改善。搜索首开 P95 为 122.6／77.7／84.1／86.6 ms，复开为 77.4／56.3／99.6／72.5 ms。新旧 Dock 首开为 95.2／93.3 ms。编辑器内节次弹窗首次 P95 245.5 ms，复开 147.6 ms，首次发生 33.7 ms 的窗口缓冲分配、19 次着色器编译。AVD 显示模式为 60 Hz，用户确认实机刷新率正常且动画掉帧。

核对上游 Demo 的 `DemoApp`、`MainActivity` 和 `DemoDialogSamples`。Demo 直接装配 Kit 的 `CollapsingTopAppBar`、`SearchBar`、`NavigationBar`、`Fab` 和 `Dialog`。课表的设置搜索和普通 Dialog 也直接使用 Kit。课表 Dock 使用定制的 Kit `CouiDockBar`，还处理真实课表场景采样和加号打开菜单前的区域截图。课程编辑器使用项目自己的全页冻结景深、课程材质、卡片变形及表单，Demo 没有该组合。仅凭调用组件名称无法判断 GPU 帧耗时。

签名包 `abc7ea77` 的版本为 `1.2.8_beta2 / 36`，minSdk 26、targetSdk 36，7,836,988 字节。V2 证书与上一包一致，包内 DynamicAnimation 版本为 1.1.0，已核对旧凝光私有材质标记未出现在 DEX 中。Kit 13 项及应用 26 项定向测试全部通过，商店 Kotlin 编译通过。新增编辑器用例校验两页时实际由 Kit 绘制指示器，并能选到第二页。
