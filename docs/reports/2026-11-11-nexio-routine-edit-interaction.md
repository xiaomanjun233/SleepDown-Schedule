# Nexio 作息编辑与保存交互

2026 年 11 月 11 日按 Nexio Android 的中文界面源码梳理。GitHub 与 Gitee 的公开基线均为 `d6744fbb`。入口为「课程表 → 上课时间」，打开后页面标题为「课程时间管理」。 [设置入口](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/ScheduleSettingsScreen.kt#L219-L255) · [标题文案](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/i18n/ChromeStr.kt#L143)

## 打开作息

- 列表显示作息名。只有一套时副标题是「始终生效」。多套时各行显示从几月几日起生效，正在用的那行带「生效中」标记。点行打开「编辑作息」。 [作息列表](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/CourseTimeSettingsScreen.kt#L197-L259)
- 新增时弹出名称和月日，日期默认今天，名称留空时使用默认名。已有相同月日时提示换日期。点「添加」就用当前生效作息的时刻创建一套，弹窗关闭，新项出现在列表，随后可点开编辑。 [新增弹窗](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/CourseTimeSettingsScreen.kt#L337-L417) · [新增时刻来源](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/core/src/commonMain/kotlin/com/haooz/chedule/data/model/TimeConfig.kt#L492-L522)

## 编辑和提交

| 操作 | 结果 |
| --- | --- |
| 改作息名称，或点「生效日期」选月日 | 名称直接填在页内。日期弹窗确认后回填当前编辑页，已有相同生效日时留在弹窗。只有一套时日期显示「始终生效」且为只读。 [名称和日期](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L663-L697) · [日期确认](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L1935-L1963) |
| 点上午、下午或晚上任一节 | 在弹窗内改起止时间和节次名称，点「确定」后回到编辑页，更新该行。 [逐节编辑](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L1740-L1801) |
| 展开「快捷设置时间」并点「应用」 | 按时段起点、课时和课间设置重算编辑页里的节次时间，可接着逐节调整。 [快捷应用](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L931-L980) |
| 点右上角勾号「保存并关闭」 | 提交整页改动。普通节次互相重叠时提示调整并留在编辑页。涉及特殊课程的重叠时可选「返回修改」或「仍然保存」。正常保存后回列表并刷新。 [顶栏](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L583-L606) · [保存分支](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L485-L499) · [重叠弹窗](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L1806-L1888) |
| 点左上角叉号，或从编辑页返回 | 放弃这次编辑，回到列表。 [取消](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L583-L595) · [系统返回](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/effects/motion/MorphTransition.kt#L276-L288) |

页面底部的「删除作息」仅在有多套时出现。确认删除会立即执行并回列表，剩余作息按日期重新确定生效项。 [删除入口](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L1170-L1182) · [删除确认](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/screens/TimeConfigEditScreen.kt#L1968-L2006)

## 从其他课表复制

回到「课程时间管理」的「更多」，打开「从其他课表复制」。弹窗选择源课表，每个候选有上午、下午、晚上节数和作息套数摘要。点「确定复制」后，源课表的节数与所有作息替换当前课表的时间配置，弹窗关闭，列表刷新。 [复制交互](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/CourseTimeSettingsScreen.kt#L425-L514) · [复制范围文案](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/i18n/TimeCfgStr.kt#L43-L52)
