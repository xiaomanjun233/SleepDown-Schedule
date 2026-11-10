# 多作息与跨课表复用整改方案

2026 年 11 月 11 日核对，Nexio 的 GitHub 与 Gitee 公开主线同为 `d6744fbb`。一张课表绑定一份时间配置，节次结构放在配置上，各套作息各自保存名称、铃声和生效日。作息列表标出当前生效项。它的跨表复制会覆盖目标的整份时间配置，节数有变化时还会映射目标课程。SleepDown 适合借用课表归属和生效状态，跨表日常操作采用只复制选中作息。 [作息列表](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/CourseTimeSettingsScreen.kt#L186-L324) · [作息模型](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/core/src/commonMain/kotlin/com/haooz/chedule/data/model/TimeConfig.kt#L235-L291) · [跨表复制实现](https://github.com/HaoZai000/NexioSchedule/blob/d6744fbb4375d1919a9287c6684c0c6aacff9a58/core/src/commonMain/kotlin/com/haooz/chedule/data/repo/CourseRepository.kt#L2372-L2387)

目前公共作息既是可复用来源，也是多张课表和日期规则实际引用的记录。从课表内改时间会生成公共副本，从库里改原件会更新所有引用。当前课表切换公共作息又会解除日期作息绑定。三条路径对同一个“作息”给出了三种影响范围。 [选择与保存代码](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/data/repository/ScheduleRepository.kt#L91) · [课表内保存](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/data/repository/ScheduleRepository.kt#L209)

## 一张课表怎样管理多套作息

- 「当前课表设置 → 本课表作息」列出这张表拥有的常规、夏令和冬令等作息。常规作息是没有日期安排时使用的那套，今天实际使用哪一套单独标为「生效中」，并显示生效日期。点任何一套都进入现有时间轴编辑器。
- 新增作息默认复制本表常规时刻，可以先存为候选，再设为常规或安排日期。「设为常规」只替换回退时刻，日期安排和其余作息一起保留。日期安排直接选本表的一套，支持单日或日期区间。沿用完整日期、首条命中和缺节沿用常规时刻的规则，重叠时在列表展示优先顺序。Nexio 的按月日循环用于参考生效状态的呈现。 [SleepDown 日期解析](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/domain/schedule/CourseTimeResolver.kt#L13)
- 节次骨架属于课表，几套作息记录各自的铃声。现有时间轴继续负责逐节、分段和课间编辑。若从时间轴发起节次增删，先展示会影响的作息和课程，再走原有重映射确认。切换铃声、选作息生效日期和复制时刻都按原节次编号。删除常规作息前先指定替代项，其他作息被日期安排引用时先预览并处理这些安排。 [节次映射](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/domain/schedule/PeriodTopologyMapping.kt#L70)

## 课表之间怎样复用

- 「从其他课表复制」先选源课表，再选其中一套作息。预览匹配节次、缺项和冲突后加入本表列表，得到可独立编辑的作息。缺项沿用本表，源多出的节次只在预览提示。例子是 B 表从 A 表复制冬令作息，把首节改为 08:40 后，仅 B 表保存这个变化，再为 B 表指定生效日期。
- 公共作息库收进「更多 → 模板」，作为复制来源。新建课表或开启新学期可整套复用作息。日期区间随预览单独选择并确认。已有的共享关系标注来源，保留显式的共享模板管理，并提供「转为本表副本」供逐张课表迁移。从本课表首次编辑共享时刻时，保存的也是本表副本。

## 实施与验收

1. 先收拢现有入口。按课表显示已绑定的常规作息和日期作息，使用同一个时间轴。为「设为常规」提供独立保存路径。更换基准时校验每套日期作息叠加后的时刻，再于同一事务重绑组合并保留规则顺序。继续让日周视图、提醒、小组件和 ICS 读取同一日期解析结果。 [保存与日期规则](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/data/repository/ScheduleRepository.kt#L852)
2. 再实现可持久化的课表专属候选及复制。建议增加显式的课表归属字段，让新私有作息可在未被选用时仍留在本表。现有 `period_schemes.scheduleId` 记录历史来源，先按实际绑定读取存量。旧共享仍可查看，用户转副本时同事务更新目标课表的基准和日期引用，并保留其他课表及独立课程引用。新归属与备份 ID 一并设计和验证，确认格式兼容后再交付存储升级。 [Room 模型](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/model/ScheduleModels.kt#L179) · [备份映射](../../app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/backup/BackupExport.kt#L158) · [时间兼容契约](../architecture/COURSE_TIME_MODEL.md)
3. 用两张节数不同的完整合成课表验收。覆盖普通、连堂、自定义、特殊课程与重叠日期规则。验证复制后双方独立、设为常规仍按日期切换、节次冲突可取消、删除前提示引用、旧备份恢复及新归属往返。对照日期区间前后日周视图、提醒、小组件和 ICS 的实际时间。
