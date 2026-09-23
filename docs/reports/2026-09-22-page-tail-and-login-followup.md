# 切页绘制、切周跟随与教务入口后续调整

基于普通版 main 的 Beta9 后续工作树；不改 exp，不覆盖已发布附件。

## Nexio 对照与性能边界

2026-09-22 通过 GitHub connector 核对最新 master 为 `faa52668b6fba6d573820557ca9625d7f5e7ff6b`。只参考性能组织方式，未替换已确认的首页/设置、日/周位移曲线、分组回弹、圆角、纯黑底色与玻璃外观。

- [MainActivity.kt](https://github.com/HaoZai000/NexioSchedule/blob/faa52668b6fba6d573820557ca9625d7f5e7ff6b/app/src/main/java/com/haooz/chedule/ui/activities/MainActivity.kt)：主页面保留组合；可见性经 derivedStateOf，逐帧位移在 graphicsLayer 读取；可见内容运动时仍录制玻璃来源。
- [MainScheduleScreen.kt](https://github.com/HaoZai000/NexioSchedule/blob/faa52668b6fba6d573820557ca9625d7f5e7ff6b/app/src/main/java/com/haooz/chedule/ui/screens/MainScheduleScreen.kt)：预留相邻周，普通坐标 holder 避免卡片坐标逐帧触发重组。本项目已有普通坐标 holder，未重复新增一层。

本次 HomeSwitchPane 在空闲后保留首页及日周内容，隐藏时停止绘制、玻璃采样、分钟时钟和活动状态回调，排除触摸和无障碍内容。切回时复用已挂载节点。可见页面继续实时采样，未以冻结壁纸或卡片玻璃替代动画。

设置与课表互切时，日、周 Pager 均仅绘制 settledPage 对应的卡片；预留相邻日期/周不因整页回弹出现，也不重新记录玻璃。真正切周时，按各跟随分组的位移包络判断可见周，直到最后一组离开才停止绘制旧周。日视图在真正切天时显示参与滑动的页面，隐藏日期暂停分钟时钟。

## 切周

- 按钮改为驱动同一个 Pager，移除额外创建旧课表及 220 ms 强制销毁旧层的路线。
- 六组按距触点的距离依次延迟，每级 24 ms；点击从顶部组开始。组内的卡片与自定义时间文字一起移动。
- 使用有界时间轨迹并在帧时钟中推进，手指停在半途仍追赶；反向不清空历史，长距离 Pager 跳转清空旧轨迹，系统关闭动画时直接跟齐。
- 位移读取在 graphicsLayer，采样标识共享 derivedStateOf。切周期间暂停文字背景对比度计算，停稳后更新；原有编辑、复制、落点涟漪保留。

## 更新日志

恢复连续的完整面板和版本间分隔线。保留条目 List 与 rememberSaveable 展开状态；整块长面板改用 Canvas 路径裁剪，避免为展开后的整个高度建立圆角裁剪层。每个版本的展开/收起仍沿用原有动画，没有新增逐条独立卡片。

## 学校列表与密码服务

- 普通教务导入和自动刷新选择器统一使用 ShiguangWarehouse.loadVisibleAdapters，以及同一官方索引缓存和七天检查策略。
- 已审阅的接口能力仍按学校 ID + 入口 ID 区分，包含借助 HTML 获取元信息、通过接口取得课程的入口。其他入口显示“可能需要手动刷新”；学校有多个入口时，选择弹窗逐项显示提示。
- 未确认可后台取得完整学期的入口仍可选，进入已有教务浏览器与导入预览，避免把页面或月课表直接用于后台覆盖整个学期。接口入口继续沿用已验证的登录态读取、脚本校验与自动刷新流程。列表加载不再逐个读取全部脚本，脚本在实际执行时继续校验。
- 教务导入取得有效草稿、自动刷新验证登录成功后，在原可见 WebView 释放前调用 AutofillManager.commit()。已有主窗口和弹出窗口的原生 WebView 自动填充设置保留；不通过 JS 读取或存储密码，不伪造站点域名。是否显示保存/更新界面由用户启用的系统服务决定，参见 [Android Autofill 文档](https://developer.android.com/identity/autofill/autofill-optimize#finish-autofill-context)。

## 验证

- 首轮 Release Kotlin 编译通过，耗时 1 分 56 秒。
- `testGithubDebugUnitTest -I tmp/motion-followup-tests.init.gradle --console=plain --no-parallel --max-workers=2` 通过，耗时 2 分 23 秒。19 项定向测试、0 失败、0 错误：甩尾时间轨迹 5、官方索引刷新策略 2、通用登录路由 3、接口目录/脚本及入口能力 4、SWU 路由 5。项目不提供 Release 单测 task，测试使用实际存在的 Debug 单测入口；未运行或声称全量套件通过。
- 定向测试后补充日视图相邻页绘制门控；它与周视图使用相同的“整页切换仅当前页、实际切天才显示相邻页”边界，由最终 Release 编译打包检查。
- 最终 `assembleGithubRelease --console=plain --no-parallel --max-workers=2` 通过，耗时 4 分 19 秒，包含最终日视图门控；保留 R8、资源压缩、lintVital 和正式签名。代码提交 `f711260`（本轮动画 `1cd5148`、日志 `28a0a59`、学校列表 `4727de4`、密码提交 `f711260`）。
- APK：`SleepDown-Schedule_v1.2.6_beta9_motion-f711260.apk`，6,679,368 字节；包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.6_beta9` / 33。SHA-256：`3f3655d6efcf99d54f6d275e330f37f972ea1647aeade4f780b50deedca2ef26`。
- `apksigner verify --verbose --print-certs` 通过，APK v2 签名有效；证书 SHA-256：`0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与既有普通版一致。未推送、未发布，exp 未修改。
- `git diff --check` 通过。ADB 未连接设备，未进行实机帧时间、手势、长日志展开及密码管理器界面的验收，也不能据源码优化宣称已达到 Nexio 的实机帧率。
