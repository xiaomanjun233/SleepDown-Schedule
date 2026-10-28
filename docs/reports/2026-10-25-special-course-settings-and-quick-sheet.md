# 特殊课程管理与快速设置调整

## 用户目标与基线

用户要求特殊课程使用既有选择器、标准 Miuix 排版，点击添加卡片直接进入新增；在特殊课程二级页面内管理新增、编辑与删除。同时要求液态玻璃快速设置增加文字与边缘的距离，将当前周状态放在右边，移除开关的解释文字。

继续 `codex/sharedviewkit-ai-history`（本地 HEAD `f0d0f8ba`）及已交付候选的未提交改动，不切换到主目录的后端任务基线。用户此前的 Drive 传包授权沿用；不含安装、提交、推送或发布。

## 实现

- 详细设置保留标准 `SettingsGroup` / `SettingsNavigationRow` 入口，进入特殊课程二级管理页。列表中的每条特殊课程和添加卡片均可整行点击，没有独立新增按钮或悬浮加号。
- 编辑页沿用 `SettingsTextFieldRow`、`SettingsPickerValueRow`，完整复用普通课程时间选择器的“按节次／自定义时间／按课时”三个页签，以及既有星期网格／位置滚轮。按用户追加要求，时间段会自动匹配并限定显示位置：课间内只提供所在间隙，覆盖节次时只提供覆盖区间整体的前／后边界，不允许放在覆盖区间中间或无关时段。
- 特殊课程 JSON 增加带默认值的可选 `periodIndices` / `customPeriodTimes`，复用普通课程逐节时间协议。按节次跟随当前作息，精确时间和按课时保留自己的时间；模式与逐节时间保存后重开可恢复。已删除节次保留原快照供查看，重新保存必须选择有效节次。周视图行轴与设置列表共用解析结果。
- 保存、取消与删除走既有公共按钮／确认框。返回未保存的编辑先确认；保存期间阻止重复提交和退出。名称、时间、星期和位置草稿可恢复。
- 特殊课程持久化按单条记录在 Room 事务内更新；保留同期新增的其他时段和所有无关设置。原记录变更或被删除时拒绝陈旧编辑，旧数据中等价的星期顺序不会误报冲突。未改变 Room 版本或 BackupFormatV1 外层格式，新增 JSON 字段可缺省，旧时段数据兼容。
- 父课表页不再持有整段特殊课程草稿；有其他未保存修改时沿用作息管理的先保存／放弃规则，子页返回后重读父页。
- 快速设置的液态玻璃内容分组增加左右 12 dp 内边距（加上紧凑行已有的 4 dp），当前周状态置于右侧；三个开关移除解释文字。公共数值行的新行为由显式参数启用。
- 首页占位胶囊、实时模糊、金标门控和连续打断曲线沿用上一候选，不新增采样或动画资源。

## 验证与交付

5 类 73 项定向测试通过：`TimeSlotCoursesTest` 14、`ScheduleConfigChangeMergeTest` 19、`WeekTimeSlotGridTest` 5、`BackupCodecTest` 30、`BackupSettingsCompletenessTest` 5，失败／错误／跳过均为 0。实际 JUnit XML 摘要与哈希保存在主目录 `tmp/special-course-settings-tests-20261025.json`。

最后将公共选择器的 `renderInRootScaffold` 对齐设置页既有的 CompositionLocal 策略，并让包含已删节次的旧选择在确认时确实写入当前有效节次。两项是选择器接入参数，未重复不涉及它们的数据测试，重新执行完整 `assembleGithubRelease`。最终结果、产物校验与 Drive 回读完成后补记。首次使用的 `testGithubReleaseUnitTest` 在本项目未开放，已改用现有 `testGithubDebugUnitTest`；期间按用户追加的显示位置／三种时间方式要求中断过两次过时构建，均未传包。

没有实机 UI、触摸和帧率验收，不能将编译通过当作手机观感通过。

最终完整 Release 于 5m58s 构建成功，保留 R8、资源压缩、lintVital 与正式签名。最终 104 个生产文件哈希未变化；实际 APK 的两项金标开关、平台金标 API 门控、原曲线调用、快速设置原生详细页路线及本轮二级管理页标记均通过核验。Room 仍为 49。

- 文件：主目录 `tmp/review-apks/SleepDown-special-course-settings-quick-sheet-gold-20261025.apk`，7,657,023 字节，`1.2.7.1` / 35。
- SHA-256：`194cd402561fe9b82a6ed4cc7a0d46ab895434561283ee5c04788be1c06665eb`。
- 已作为新文件上传原本人私有文件夹「SleepDown 测试包」，回读核对大小、归属、私有与可下载：最终 APK（私有地址未入库）。旧包未覆盖。
- 证明：主目录 `tmp/verify-special-course-settings-final-20261025.log`、`tmp/special-course-settings-package-verification-20261025.json`、`tmp/special-course-settings-drive-readback-20261025.json`；最新交付清单 `tmp/review-apks/remote-delivery-20261023.json`。
- 构建缓存仍位于主目录 `tmp/build-cache/gradle`，构建与验包临时文件使用 `D:/sd-tmp`。未安装、提交、推送或发布。
