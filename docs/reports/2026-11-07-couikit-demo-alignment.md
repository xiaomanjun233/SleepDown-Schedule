# COUI Demo 对齐与动态控件开销修复

接续 `977e82f2`，仍在 `codex/couikit-dialog`。本地源码提交为 `a881c2fb`（分段回弹与逐帧开销）、`3287ea8b`（Demo 弹窗／选择器、按需采样与按钮材质）。用户要求 Dialog 尺寸、模糊、行为及选择器装配以 COUI Demo 为基准；普通选择器直接采用标准装配，特殊选择器保留业务约束但统一外壳和操作区。同时反馈导入弹窗按钮采样、导航区遮挡、胶囊分段还原度，以及 Dock 融合、搜索框收放、Switch 切换卡顿；要求课表设置行反馈改为胶囊、加号等着色按钮改为半透明模糊。

## 基准与实现

以已固定上游 `520478b9305780b078bed1096ce68f727c594f14` 的 `DemoDialogSamples.kt`、`DemoPickerDialog.kt`、`DemoPickerSamples.kt`、`DemoWheelPresets.kt`、`DemoApp.kt` 和 `DemoPreferenceRows.kt` 对照。没有重新取 E 盘开发分支当上游，也没有更改版本或数据格式。

- 普通 Picker 直接使用上游 `Dialog`；自定义标题动作／特殊表单保留 `ContentDialog`，标题与操作区均委托同一 Kit 实现。窗口宽高上限、45dp 外轮廓、默认边距、Kawase 200、明暗混色、DIM 与进退场曲线沿用 Kit。
- 恢复上游 `CouiDialogButton`，移除上轮加入的按钮二次采样与 Gaussian 模糊；清除纯表面 producer。弹窗已模糊其真实下层窗口，内部普通操作只绘制 Demo 的填色／轮廓／反馈，不再误用调用方的页面 Backdrop。
- 普通数字滚轮按 Demo 居中 120dp，时分采用两个 62dp 列、共享选中线及 12dp 上下留白；完整日期采用 CalendarPicker。耦合的课程时间范围组合两组标准时分选择器，范围与校验保持业务所有。
- 课程时钟、颜色、头像裁切、记忆编辑，以及作息创建／时间块等特殊选择器的确认取消移入固定操作区。特殊操作区复用 Kit 的按钮排列与尺寸，不再沿用 50dp 的液态按钮行。课表快速设置的日期窗口声明操作元数据并去掉旧高度限制；课表设置本体按用户后续明确要求保留顶部左取消／右完成，凝光使用 44dp 按钮并由正文承担唯一滚动。
- 导入表单采用同一 Kit 标题与底部取消／主操作，移除重复的顶部确认；保留导入历史真实按钮、返回锚点和回调。液态路径继续原窗口与动作。
- 普通选择器不再无条件启用全屏 IME 策略。全屏输入窗口按导航区和当前 IME 交集计算底部留白，避免重复扣除导航高度；下层设置页遵守 Demo 的焦点判断，不跟随弹窗键盘重新布局。
- 课表快速设置作用域内，行反馈由公共设置组件裁剪为胶囊；其他页面默认形状不变。恢复顶部操作后的布局图发现按钮贴边，已补回 Kit 标题顶部24dp、操作水平16dp及底部16dp留白；两种模式继续原位置，液态顶部按钮保留38dp。
- 独立强调按钮与 Dock 加号采用可选的半透明染色材质：浅色 0.55、深色 0.50，25px 模糊；Kit 默认 FAB 和标准 Dialog 主操作不改为该可选样式。形变与文字／图标保持独立。

## 性能与分段审查

Demo 的 Switch、搜索框和导航均复用现有 Kit 组件与运动；本轮没有删除 SDF、融合、轮廓或跟手光，也没有依据主观感觉降低其质量。

额外核对了宿主 `CouiDockBar` 与上游 `NavigationBar` 的浮动组合装配：都使用默认的完整背景合成分支，没有证据支持把它们统一改为 `foregroundComposition` 或改写公共 SDF renderer。修复范围保持在已确认的宿主重复录制与动画状态读取，后续仍须以同设备同场景帧耗时比较 Demo 和应用。

已确认应用侧额外开销：整窗源空闲仍录制整页、凝光设置页同时录制未使用的 Kyant 正文源，以及 Dialog 的额外表面源与按钮滤镜。整窗源现在可显式 `recordWhenIdle=false`，仅有窗口消费者时录制并保留父链；局部控件动画不再驱动这份无消费者的整页录制。设置页凝光使用 Scaffold 自身正文源，液态保留原 Kyant 源。

分段控件继续根据本机 Settings 17 的 `COUISegmentButtonLayout` 行为核对，未纳入反编译源码：位置／宽度／高度／橡皮筋保持独立弹簧；补回高度回弹，按压后可用橡皮筋距离按实际指示器缩小量与中间项 5dp 切换距离计算；切项完成后重新取拖动原点。指示器矩形在缩放后按容器边距裁剪，标签随触点缩放。每帧弹簧只触发布局／绘制，不再重组标签和重新测字。

## 验证状态

- 中间 `compileGithubReleaseKotlin` 通过（3m11s）。首轮定向测试23项通过，0失败／错误／跳过，日志 `tmp/coui-demo-alignment-tests-20261107.log`（5m20s）；覆盖分段提交／取消、窗口留白、按钮背景域、空闲源停止录制、日期／Picker生命周期和课表设置交接。
- 恢复顶部操作后，`CouiMigrationUiTest` 10项及 `QuickScheduleSheetLifecycleTest` 3项全部通过，日志 `tmp/coui-demo-alignment-final-tests-20261107.log`（4m07s）。诊断关闭方法的错误引用在编译时发现并修正为既有 `reset()`，未进入交付产物。
- 最后补齐顶部留白后，最终源码 `3287ea8b` 的迁移UI嵌套月历用例1项、课表设置保存生命周期3项再次全部通过，日志 `tmp/coui-quick-settings-header-final-tests-20261107.log`（3m03s）。新布局图已查看，顶部按钮位置与留白正常。布局图保存在 `tmp/coui-visual-review-20261106/`，目录日期来自测试辅助函数，结果按文件生成时间核对。
- 正式签名 `assembleGithubRelease --console=plain --no-parallel --max-workers=1 --no-daemon` 通过，5m55s，195项任务中34项执行、161项复用；保留 R8、资源压缩、lintVital 与正式V2签名。日志 `tmp/coui-demo-alignment-release-20261107.log`。Debug回归与Release分别使用独立Gradle进程，避免此前合并构建的堆内存问题。
- 设备探测：SDK ADB 的 5038／5037 均无设备，已配对 mDNS 也无端点。未安装、未采集真机帧耗时；不能声称全部卡顿已经实测消除。
- 保留本地 `output/`、其他工作树与 stash。不自动推送、PR、发布或安装；最终 APK 与精确 mapping 仅本地交付。

## 本地交付

APK 与 mapping 均来自上述同次 Release 原输出，副本逐文件 SHA-256 核对一致：

| 文件（`tmp/review-apks/`） | 字节数 | SHA-256 |
| --- | ---: | --- |
| `SleepDown-1.2.8-beta2-couikit-demo-20261107-dad316ce.apk` | 7,824,980 | `DAD316CE5FDC7F0247D5F5611E9D38EE236425A6DF5C0DBD6C5EA1B732A2CD2A` |
| `SleepDown-1.2.8-beta2-couikit-demo-20261107-dad316ce-mapping.txt` | 161,026,929 | `BF4D87956C536AAD8C90EAA2E02F86590D54A8AD776E3384C9565CC83D823A5C` |

`apksigner verify` 通过，V2证书 SHA-256 为 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。Manifest为 `com.xiaomanjun.sleepdownschedule`、`1.2.8_beta2 / 36`、minSdk26／targetSdk36；APK内嵌源码提交为 `3287ea8bd9dc525e28535c916def1d318668e1bf`。后续文档提交不改变该包源码归属。首轮 `2a481549` 的旧APK继续保留，不作为本次结果。
