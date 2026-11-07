# COUI 上游同步、采样审查与 Dialog 迁移

基线 `61246c07`，分支 `codex/couikit-dialog`。用户要求先同步 GitHub 最新 main，再迁移公共 Dialog、凝光滚轮与月历，并依据反编译补齐胶囊分段选择器；液态保留自己的外壳和选择器。追加要求包括审查已迁移控件的采样与维护边界，修复通知悬浮按钮、按钮配色／背景模糊，以及课程管理／导入历史深色卡片。

## 上游与本地边界

- 上游固定为 `ItosEO/COUI-Kit@520478b9305780b078bed1096ce68f727c594f14`，2026-11-07 使用 Git 获取源码；未用 E 盘开发工作区代替 main，未进行 GitHub 元数据写入。
- 以原上游 `af952eb6` 三方同步完整运行源码、资源与许可。上游实际 API 仍是 `Dialog`、`InputDialog`、`WheelPicker`、`CalendarPicker`，与 E 盘开发分支的命名不同。
- 保留 minSdk 26 兼容、材质开关、Mesh 保护和 Dock/Button 扩展。API 34 的凝光分发不依赖品牌；库更高版本能力仍受宿主门控。
- 同步提交 `a815ad9b` 单独通过 Release Kotlin 编译。没有修改 E 盘库、引入 Demo／发行配置、OEM 二进制或私有 SDK。

## 采样与维护审查

| 组件 | 当前源与消费者 | 结论／修复 |
| --- | --- | --- |
| Dock + FAB | 完整页面源包含壁纸和业务正文；Dock 在该源闭合后的同级节点绘制，仍进入外层整页快照 | 未发现自采样；两个真实前景层交给 pair renderer，不是空内容层；保留已验收参数 |
| Button | 原迁移仅填色，没有传递调用方 Backdrop | 普通按钮传递原背景域与形状；Dialog 内改用同窗口纯表面源。glass 中对采样层应用 COUI 混色和 7px 模糊，文字不进入滤镜，不读取包含按钮的窗口祖先 |
| Switch / Slider | 上游自绘轨道、thumb、阴影与输入，不消费页面 Backdrop | 不增加背景层；保留事务、snap、compact 与滚动让位 |
| Dialog / Picker | 独立窗口消费宿主完整源；自身完整窗口提供下一层源；纯表面 sibling 源供当前按钮与分段控件使用 | 同窗口嵌套 Scaffold 复用源；新 Window 才建立 parent 源；平台 DIM 不录入 Compose 源；表面 producer 与前景消费者分离 |
| SegmentedControl | 完整外壳使用调用方背景域，仅指示器模式不加第二个外壳 | 复用公共形状、轮廓、运动与混色；无 OEM 反射依赖 |

窗口源空闲使用 Auto，材质弹窗存续时由 Kit retain 恢复隔离并保留 parent 链。普通按钮继续消费已隔离的业务背景域；Dialog 内通过 `LocalCouiSurfaceBackdrop` 消费背景材质与轮廓组成的纯表面，文字、按钮、分段指示器均为其后绘制的同级消费者。该源不继承整窗源，避免重叠采样；完整 Dialog 窗口源仅供下一层窗口使用。浮动表面与按钮复用 `couiControlMaterialEffect`，保持原两层混色与亮度处理顺序。投光、卡片光以及自定义命中区域的复合输入按钮保留既有原生／液态能力分发；Kit 普通 clickable 不接管其输入区。

Dialog 原先另有一份 Button 绘制／运动实现，现改为薄配色适配并委托通用 Button。窗口和采样扩展集中在 vendored Kit；应用只传主题、草稿、操作与回调。后续同步仍以固定上游提交做三方核对，不能整目录覆盖宿主扩展。

## 行为与追加修复

- 设置页取消强制 COUI Dialog 分支。Alert、Picker、普通表单和命名窗口统一按视效装配；实际退场结束后交接，反向取消不提交。嵌套确认和未保存拦截继续由业务持有。
- 命名／重命名草稿移到业务入口持有，渲染器只上报草稿；凝光与液态之间切换不提交，也不恢复成旧名称。原生兼容字段不因每个字符的草稿更新而重建。
- 标题与正文留白来自 Kit。标准操作通过声明式列表进入固定操作区，特殊操作保留 footer 槽；不将确认按钮放在正文滚动区。液态继续原路线。
- 凝光数值选择消费 `WheelPicker`，完整日期消费 `CalendarPicker`，保留各入口年限、时间边界及确认保存语义；翻月不提交。设置字段旧 Android 日期入口也按凝光模式接入月历。
- `COUISegmentButtonLayout` 原厂依据为本机 Settings 17 反编译：宽度分配、位置／宽度／高度／橡皮筋弹簧、指示器颜色、CAPSULE_10 描边和渐变投影分别核对。宿主保留即时选择／抬手提交、取消不新增提交、仅指示器及程序回写语义。OEM 302 振动使用公开轻触反馈承载，不能声称 OEM 波形完全等价。
- 通知悬浮按钮带徽标时高度为自适应；通用 Button 不再无条件 `fillMaxHeight`，保留原底部定位和内容高度。
- 主按钮优先显式主题色并恢复不透明 accent；中性按钮使用相应明暗材质，避免把黑色“课表设置”按钮替换成浅色次级填色。长按空白处的管理按钮恢复对原背景域的采样。
- 课表管理“设为”／加号及首页强调按钮显式声明主色角色，灰色主题也不再依赖饱和度推断；这是最终核对后的局部追加修复。
- COUI 设置的透明 container 先合成到页面底色再映射为不透明 M3 surface 角色；课程管理下游修改 alpha 不再把白色透明叠层提亮。导入历史使用真实卡片表面角色。

## 验证与交付状态

- 上游同步的 `compileGithubReleaseKotlin` 通过；Dialog 接入中间版本也通过 Release Kotlin 编译。
- 定向回归通过：Kit `SegmentSelectionSessionTest` 3 项，应用 `ControlMaterialProviderTest` 1 项、`NativePickerLifecycleTest` 2 项、`CourseManagementTest` 6 项、`CouiValueControlsRouteTest` 1 项，以及迁移 UI 回归 6 项，共 19 项，0 失败／错误／跳过。日志 `tmp/coui-dialog-tests-final-20261107.log`（4m22s）。RTL 图像断言曾发现 Box 的 Start 定位与物理偏移叠加，已以绝对左起点修正后复跑通过。
- 命名草稿修复后单独重跑 `CouiMigrationUiTest`，7 项通过（含新增跨模式草稿用例），0 失败／错误／跳过；日志 `tmp/coui-dialog-name-tests-20261107.log`（3m45s）。两轮合计覆盖 20 个不同用例；没有无关全套测试。
- 回归覆盖按钮自适应高度与底部定位、灰色主题、调用方背景采样、深色卡片角色、同窗口源去重、纯表面与完整窗口源分离、闰日与翻月、Picker 退出反向、RTL 指示器及 CANCEL／抬手提交。通知按钮测试图已查看：`tmp/coui-visual-review-20261106/coui-notification-floating-button.png`，底部位置与内容高度正常。该目录名来自既有测试辅助函数，不表示旧图冒充新结果。
- 中间版本 `3013f7b1` 的完整签名 `assembleGithubRelease` 通过（6m49s，195 项中 37 执行、158 复用）。最终 `e240e16d` 的首次串行验证在应用测试前因 Gradle daemon JVM 内存抖动中止（`tmp/coui-dialog-final-validation-20261107.log`），不是代码编译失败。随后仅重新执行正式构建：`--no-daemon --max-workers=1`，5m52s 通过（195 项中 28 执行、167 复用）。
- 最终 APK：`tmp/review-apks/SleepDown-1.2.8-beta2-couikut-dialog-20261107-2a481549.apk`，7,808,596 字节，SHA-256 `2A48154923EDADA0E1BEE72BDE13AB577200D48875B397E96F601CB2ABD49C8D`；V2 签名通过，证书 SHA-256 `0f2b50df…`。精确 mapping 同目录同名 `-mapping.txt`，160,979,778 字节，SHA-256 `1865F2C845510C1C83E07F34625BDD71800006356A32B4C62D67E3357C779302`。构建内嵌 revision 为 `e240e16d8f7a132ebaa0e9e9628eea459e94e560`。
- 最终 `e240e16d` 的 `CouiMigrationUiTest` 7 项通过（含纵向手势交还父容器的新增用例），0 失败／错误／跳过；日志 `tmp/couikit-dialog-final-ui-test-20261107.log`（3m52s）。
- 续作时对同一最终源码再次运行上述完整定向集合：应用 17 项、Kit 3 项，共 20 项全部通过，0 失败／错误／跳过，耗时 59s；日志 `tmp/coui-dialog-final-tests-20261107.log`。只读回验 APK 内嵌源码、版本、V2 签名及两份交付文件哈希，均与最终构建一致；mapping 与该次 Release 的原输出逐文件哈希匹配，无需重复打包。
- 单元环境不代表实机 GPU 模糊、动态像素、OEM 振动或帧耗时已验收。此前 Dock/Button/Switch/Slider 视觉按用户已验收处理；本轮新视觉和修复仍需实际设备核对。
- 不改版本、数据库或发布格式，不自动安装、推送、PR、标签或发布。APK、精确 mapping、临时图和日志仅本地保留。

本地回退边界：`a815ad9b` 上游同步 → `5a43a77a` 分段组件 → `f730e802` 按钮采样／颜色与卡片 → `3013f7b1` Dialog／Picker 与宿主接入 → `c6e62a5c` 首页及课表强调按钮的显式主题色角色 → `e240e16d` 分段纵向手势让位。接入说明和设计系统随代码更新。

实机验收待办：两种模式下设置页、首页及二级 Activity 的 Alert／输入／Picker／表单和嵌套弹窗；长文与三按钮、大字体、横屏和 IME；GPU 背景与文字清晰度、动态背景、卡片深浅色、分段反向拖动及父容器接管。未连接设备进行这些检查，不能用 Robolectric 结果替代。
