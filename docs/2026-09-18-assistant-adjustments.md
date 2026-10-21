# AI助理与调休课表（2026-09-18）

## 本次修改

- 周视图助手的玻璃层改为覆盖胶囊、展开卡片和全屏的固定分配区域。运动帧只更新绘制边界和圆角，避免材质层随外壳尺寸反复分配；进入全屏期间停止大面积折射，结束后去掉描边和圆角。
- 助手背景材质与对话文字分别记录，再由顶栏渐变模糊和右上角按钮共同采样，补齐黑色背景和边缘。采样源与消费者保持前后同级关系。
- 输入支持最多四行，按文字实际高度使用自定义贝塞尔曲线伸展；流式回复按列表实际高度逐步展开，达到卡片上限后滚动。发送键向右移动，使用 Compose 图标，禁用态整个图标变灰。按压反馈的坐标与范围按固定材质区域和实际胶囊大小计算。
- 教务岛恢复原来的 LiquidButton 材质、按压伸展与指针光影反馈，保留统一的渐变外壳、固定文字排版以及收回岛上时隐藏状态栏的行为。
- 当前功能名称统一为“AI助理”，新增“周视图AI助理”开关。历史版本日志、组件标识、消息历史及已发布协议不改名。
- 通知设置、课表详细设置复用公共小标题样式。

主要实现入口：[DayAgentUi.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/agent/DayAgentUi.kt)、[TopAssistantSurface.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/glass/ui/TopAssistantSurface.kt)、[EduImportBrowserUi.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/importing/EduImportBrowserUi.kt)。

## 调休规则与确认

入口为课表详细设置中的“调休课表”。使用公共液态弹窗、分组、日期选择器和确认按钮，日历将已设置的日期标黄。可自定义停课日，或指定某一天补原课表中的哪一天。

自动获取使用 [提莫节假日 API](https://timor.tech/api/holiday) 的 HTTPS 年度接口，不需要密钥。实际检查 2026 年返回 39 个日期，其中 33 个休息日、6 个调休工作日。获取只产生预览，不直接保存；补课日初始不勾选，必须选择原课程日期，避免把法定工作日误当学校的具体补课安排。

确认预览将安排写入详细设置草稿，保存详细设置后才持久化。确认页面显示每个日期、停课/补课类型和原课程预览，明确说明同日安排会被替换；取消不会写入。

`ScheduleAdjustment(date, sourceDate, label)` 以实际日期映射到原课程日期；`sourceDate=null` 表示停课。原课程日期始终读取未经调休映射的课程，不递归追踪映射。课程本体的星期、周次、单双周不修改。来源日期需要在学期内，自动周次模式下目标日期也必须在学期内；手动周次模式以当前周对应的周一计算来源周次。

日/周视图、按日期查询的小组件、通知和 AI 当日事实均读取同一安排。周视图将补课课程显示在实际日期，课程详情使用原课程及原教学周；调整日期的课程不进入拖动编辑，普通日期也不能拖动课程到调整日期。已设置调休的周末保留可见，调休日当天的周视图胶囊使用黄色。

AI 事实分别记录实际日历教学周和今日补课来源周。读取本周安排仍按实际日期呈现；修改今日补课课程落到其原教学周，本周其他课程沿用实际教学周，避免全局替换当前周次。

主要实现入口：[ScheduleAdjustments.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/domain/schedule/ScheduleAdjustments.kt)、[ScheduleAdjustmentsUi.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/settings/ScheduleAdjustmentsUi.kt)、[HolidayApi.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/settings/HolidayApi.kt)。

## 升级与备份

Room 40→41 为 `schedule_config` 增加 `scheduleAdjustmentsJson TEXT NOT NULL DEFAULT ''`，保留旧课程、课表及其余配置。旧数据库修复路径也创建和复制此列，迁移带列存在检查，避免修复后再次添加。

SleepDown 备份增加可选的调休字段和周视图助手偏好；旧备份分别默认无调整和周视图助手开启，原有主开关继续生效。格式版本、导入入口和历史消息存储保持兼容。配置合并加入新字段，避免其他设置保存覆盖调休安排。

## 验证记录

- 首次 `compileGithubReleaseKotlin` 成功。
- 最终 `testGithubDebugUnitTest` 的 90 项定向单元测试全部通过（8 个测试类）。涵盖调休周次/单双周、无课周末可见性、AI 事实与动作来源周、异步配置合并、API 解析、旧备份兼容、迁移路径和助手提醒策略。测试范围通过临时 Gradle init 脚本选择，未修改项目默认测试配置。
- 首轮 `assembleGithubRelease` 成功（5m14s），包含 R8、资源压缩与 lintVital。APK 的 v2 签名校验通过。
- 首轮 APK 已通过 `adb install -r` 覆盖安装，结果 `Success`；设备记录更新时间为 2026-09-18 18:25:50，原安装时间仍为 2026-08-16。未清除正式应用数据。
- 首轮 APK SHA-256：`1DA25F5E743A67AF8DFC98F13EF904213A918A409B352309BA90F8A328300A2A`。
- SQLite 40→41 升级验证通过：从实际 40 版 schema 建库、复用迁移测试的旧课表/课程种子、执行实际新增列 SQL，并逐列比对 41 版 schema；旧周次、课程与单双周保持，调休字段默认空值。
- 实机迁移验证首次被任务外 `ScheduleRepositoryDataBoundaryTest.kt` 的类型引用缺失挡住；临时 init 脚本只选择 `AppDatabaseMigrationTest` 后测试包编译成功。Gradle 设备启动阶段未下发测试包，改为 ADB 直接安装与 instrumentation；OPPO 锁屏后的测试包安装确认未完成，最终安装返回 `Failure [-99]`。测试应用未安装，实机测试未执行，不记为通过。未修改默认测试配置。
- 已连接 OPPO Find X9（PLJ110），Android 17。
- 本次性能定位来自布局、采样和动画读值路径；尚未取得实机帧率或视觉验收结果，不将编译通过视为流畅度验收。

仅构建本地测试包，不修改版本号或重新发布已发布的 Beta5。

## 二级设置页跟进

- 调休课表改为独立二级页面，复用设置页的大标题、返回按钮和渐变模糊顶栏。页面接收对应课表的详细设置草稿；点“完成”只回传调休安排，最后仍由详细设置统一保存。
- 新页面注册到公共转场路线，普通设置跳转支持 Activity Result launcher；延续原有平台动画与非 OEM 路线策略。
- 自定义、月份可视化、已确认安排和节假日预览保留；离开有修改的页面时提供完成、放弃和继续编辑，避免无提示丢失草稿。
- 通知设置的课前提醒、课程实时活动、明日课程，以及详细设置的学期、作息和详细节次，改为标题与卡片共同分组，移除标题与卡片之间额外的列表间距。
- “测试实时活动”改为底部居中的液态玻璃悬浮胶囊，随页面固定显示，留出导航栏和键盘空间；采样独立的列表内容层及完整页面底色，避免控件自采样。

入口：[ScheduleAdjustmentsActivity.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/settings/ScheduleAdjustmentsActivity.kt)、[ScheduleAdjustmentsUi.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/settings/ScheduleAdjustmentsUi.kt)、[ScheduleSettingsContentUi.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/settings/ScheduleSettingsContentUi.kt)、[PeriodSchemeEditorUi.kt](../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/settings/PeriodSchemeEditorUi.kt)。

跟进验证：`TransitionRouteCatalogTest` 的 5 项与 `TransitionFeaturePolicyTest` 的 3 项测试全部通过；仅通过临时 init 脚本选择相关测试，默认配置不变。Release 打包与 Debug 定向测试组合构建成功（10m53s），其中 Debug 编译已包含补齐通知列表采样底色的修复；交付包另外执行完整 Release 构建。

最终交付：完整 `assembleGithubRelease` 成功（5m8s），包含 R8、资源压缩与 lintVital；v2 签名验证通过。`adb install -r` 返回 `Success`，Find X9 的安装更新时间为 2026-09-18 19:30:01，原安装时间仍为 2026-08-16 01:20:27。新二级页面组件在设备上解析成功。未清除数据，也未自动启动应用；新页面的实机视觉与点击流程尚未验收。

交付 APK SHA-256：`40FD441141DB22875F6429129A343183B5431EE9E588E6DE659D67614FBA58D2`。版本保持 `1.2.6_beta5` / 33，本次没有远端发布。
