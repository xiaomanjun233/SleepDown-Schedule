# 凝光系统菜单与课表详细设置布局

接续 `4e7a30c5`，仍在 `codex/couikit-dialog`。用户要求接入凝光模式的课程长按 shortcut 和右上角三点菜单，明确不用 COUIKit Demo 菜单样式，按系统原样式补齐；凝光与液态行为不同。另反馈课表设置的“详细设置”大按钮布局异常。继续本地实现、提交与签名 APK／精确 mapping 交付，不改版本，不自动安装、推送或发布。

## 系统依据

只读本机 Settings 17 反编译的 `poplist/PopupMenuLocateHelper`、`COUIPopupListWindow`、`RoundFrameLayout`、`BasePopupMenuAnimationController` 与对应资源；不提交反编译源码、资源原件或厂商二进制。

- 系统有图标菜单196dp、最大256dp；外框28dp／weight3，正文上下8dp，最小行高40dp；图标20dp，图标与文字间12dp，行水平20dp，文字16sp regular。分隔线0.5dp，有图标时起点52dp、末端20dp；按压共享遮罩水平内缩8dp。
- 普通工具栏菜单按锚点水平居中后避让；课程 shortcut 沿逻辑起始边对齐，RTL 镜像。锚点上下间距8dp，优先下方、空间不足翻至上方，矮窗口与安全区共同约束滚动高度。
- 背景沿用系统普通模式4、Kawase160的基础材质、轮廓与阴影；位移、缩放、透明度分别走已核对的系统弹簧。独立 Popup 采样下层完整窗口，条目内容保持清晰，退出后释放源。

## 装配与行为

`SystemPopupMenu` 单独装配系统行与定位，不调用 Kit 的 `Menu`／Demo 行布局；只复用底层材质、投影、触摸运动与开合弹簧。宿主 `GlowSystemMenus` 处理按钮锚点、动作和实际退场交接。公共 `HomeMaterialControls` 仅增加菜单按钮槽，业务不直接创建材质源。

三点菜单关闭完成后才执行动作，阻止重复提交；课程菜单仍通过原 controller 的退出屏障恢复课程源卡，再交接编辑／复制／移除。滑动、CANCEL、外部点击、返回、禁用语义、键盘／无障碍入口均在当前窗口处理。液态的液滴、位置与 Morph 路线保持独立。

三点菜单打开时冻结本次动作列表，后台停止时清除未提交动作与窗口；大屏保留原有完整动作集。菜单向宿主回报真实窗口矩形，再转换成页面坐标供既有目标路线使用，不伪造返回锚点。可滚动时沿用系统滚动条基础能力。

课表设置按钮异常已用真实“详细设置”按钮复现：上一轮用例隐藏了该入口，遗漏按钮贴外框的情况。本轮让正文承担唯一滚动、详细设置独立留在底部，两侧采用Kit操作区16dp留白；仍从真实按钮坐标进入详细设置，顶部取消／完成不变。

## 特殊选择器与分段续查

用户后续指出课程编辑器、星期选择器、调色板间距异常，并明确月历不要标题；继续按此前确认的胶囊分段控件核对系统。

- 根因是上轮移除旧外壳padding后，自定义正文失去了水平留白，而星期/节次等内部还保留2dp微边距。公共Picker增加显式 `couiContentPadding`，自定义表单统一采用 `CouiPickerFormPadding`：水平24dp、顶12dp、底16dp，Wheel／Calendar保留自身几何。课程星期／节次、两处调色板、作息自定义页、调休详情及记忆编辑复用它，不重复叠加旧微边距。
- 星期网格默认维持一行七项，使用44dp行高和4dp间隔；大字体按实际宽度换行，不强行裁掉末项。课程调色板的自动按钮采用44dp，色板、预览与操作区对齐；液态保留原布局。
- 所有完整月历入口去掉凝光外层标题，包括共享日期、课表开始日期和调休日期页；保留月历自己的年月导航。调休日期页也不重复添加一行日期标题。
- 系统Segment regular高度40dp、Tiny高度32dp；普通表单采用regular40dp，首页复合导航继续传自身尺寸。再次核对独立位置/宽高弹簧、按压缩小和橡皮筋；补齐运动中的指示器命中，非拖动态跨标签只释放前一标签缩放，已选标签不提供重复的无障碍点击。即时提交／抬手提交和取消语义保持。

本地回退边界：`58e7b911` 详细设置布局／课表月历标题，`ee16de53` 系统菜单，`f2a13030` 特殊选择器留白／其他月历标题，`82015f8e` 分段对齐与迁移回归，`cd25da11` 菜单外观独立于Demo默认值，`5d7f7235` 主编辑器布局与星期交接核对。

## 验证与交付

- 首轮9项菜单／快速设置回归通过（4m15s），核对真实三点按钮与模式分发后的10项通过（4m34s）。
- 追加特殊选择器与分段后，应用19项、Kit7项，共26项全部通过，0失败／错误／跳过（4m24s）。日志 `tmp/glow-popup-picker-alignment-tests-20261107.log`。覆盖星期、调色板布局、月历无外层标题、分段运动中拖动、RTL／取消、三点实际按钮及退出交接、液态模式回退、矮窗口与课表保存。
- 主编辑器／星期／调色板3项补充检查通过（1m40s），日志 `tmp/coui-course-editor-layout-final-tests-20261107.log`。已查看生成的系统菜单、真实详细设置、星期、调色板及编辑器主表单图：主表单字段未重叠、固定标题栏正常，并能打开真实星期子选择器。
- 最终源码 `5d7f7235` 的独立菜单／编辑器6项回归通过（1m34s），0失败／错误／跳过，日志 `tmp/glow-system-menu-editor-final-tests-20261107.log`。菜单不再读取Demo样式默认值，基础色彩和材质协议保持一致。
- 正式签名 `assembleGithubRelease --console=plain --no-parallel --max-workers=1 --no-daemon` 通过（5m52s，195项任务中35项执行、160项复用），保留R8、资源压缩、lintVital和正式V2签名。日志 `tmp/glow-system-popup-picker-release-20261107.log`。未合并Debug和Release进同一Gradle进程。
- Robolectric仅检查布局与状态，系统动态像素、背景模糊、OEM触觉及真机性能仍需设备验收。

## 本地交付

最终APK与mapping均从上述同次构建复制，逐文件SHA-256与原输出一致，临时产物不入库：

| 文件（`tmp/review-apks/`） | 字节数 | SHA-256 |
| --- | ---: | --- |
| `SleepDown-1.2.8-beta2-glow-popup-pickers-20261107-031cf109.apk` | 7,857,748 | `031CF109840BBC8319D44F598FFC93CAE4D3EAEDF75630BEF804219F1C09D08C` |
| `SleepDown-1.2.8-beta2-glow-popup-pickers-20261107-031cf109-mapping.txt` | 162,147,478 | `0CA22E87BD10EDFA98624CF231615765025E4F8F451015D4752E2C286CB8C9BC` |

`apksigner verify` 通过，V2证书SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。Manifest为 `com.xiaomanjun.sleepdownschedule`、`1.2.8_beta2 / 36`、minSdk26／targetSdk36；内嵌源码提交 `5d7f7235d705f33877a3d1183230ddc9e773ecbc`。后续文档提交不改变此包源码归属。前轮 `dad316ce` 与 `2a481549` 包保留为历史产物，不能替代本轮包。未安装、推送或发布。

## 2026-11-07 续：按 Gallery／Weather 复核

> 此节12dp结论已被后续有效分支调查纠正：`RoundFrameLayout`构造覆盖XML，凝光最终为28dp／2.5，见[修正报告](2026-11-07-coui-toolbar-popup-correction.md)。其余未变项目保留为历史依据。

用户要求两个弹出菜单按 ColorOS 原版行为修复，并指定从相册或天气右上角菜单取目标。实际只读检查了 `OppoGallery2.apk` 与 `OppoWeather2.apk` 内的 `com.coui.appcompat.poplist.COUIPopupListWindow`、`COUIPopupWindow`、`COUIPopupMenuRootView`、`PopupMenuLocateHelper`、`COUIActionMenuView` 及资源。

- 目标窗口是 `COUIPopupListWindow`：全窗口 Popup、focusable、outsideTouchable、非 touchModal；菜单宽 196dp（带图标）、最小 168dp、最大 256dp；顶栏屏障 12dp、底部导航屏障 32dp、锚点垂直间距 8dp。
- 目标面板圆角取 `couiRoundCornerM`／`coui_round_corner_m` 12dp（API 35 布局用 9dp 半径变体），不是此前 Compose 菜单的 28dp。条目本身仍是 40dp 最小高度、上下 8dp、左右 20dp、图标 20dp + 12dp 间距、正文 16sp regular；条目遮罩圆角改为 8dp。
- 条目分隔线仍为 0.5dp，带图标起点 52dp、末端 20dp；本次没有把组间距 12dp 误加到普通条目。
- 行为上，课程 shortcut 的动作改为和 `COUIPopupListWindow`／overflow 一样，在主菜单实际退出后执行，不再点击瞬间提交；溢出菜单取消选中触觉，保持系统菜单的视觉/触觉反馈。
- 变更文件：`third_party/coui-kit/.../menu/SystemPopupMenu.kt`、`platform/coloros/GlowSystemMenus.kt`。提交 `e43ab2ee`。
- `GlowSystemMenusTest` 4 项通过（含 shortcut 退出交接与 overflow 点击后退出提交），截图 `tmp/coui-visual-review-20261106/system-glow-overflow.png` 已查看：196dp 面板、12dp 圆角、条目分隔与锚点避让正常。
- 正式 `assembleGithubRelease` 通过（8m31s）。APK `tmp/review-apks/SleepDown-1.2.8-beta2-couikit-menu-topbar-20261107-e5d9e435.apk`，7,841,364 字节，SHA-256 `E5D9E435F2B2972298A150106679F173E4E6F95F814A47E511DE16542382C330`；mapping 同目录，162,254,434 字节，SHA-256 `0134D29343C63AC6EEC0F238A707E747B24838B6BFAD556E7389C9BE6AB6CC8F`；内嵌源码 `e43ab2ee`，V2 证书 `0f2b50df…`。
- 桌面只保留 APK：`D:\个人资料\桌面\SleepDown-1.2.8-beta2-couikit-menu-topbar-20261107-e5d9e435.apk`；上一套 `e60f0bee` 已删除，mapping 不复制到桌面。
- 仍未真机验证动态模糊、系统像素、OEM 触觉与帧耗时。
