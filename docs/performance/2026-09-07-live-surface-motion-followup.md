# 实时活动与首页弹窗性能修正

## 现场证据与边界

- 用户提供的 PLJ110 卡片截图显示：白色 tracker 位于进度条上方，正文重复时间/地点，并有两个操作按钮。前一版将 Bitmap tracker 换为矢量资源仍未解决布局，不能再将问题归因于图片密度。
- 设备通过 adb 读取为 Android 17。调查时停在锁屏；`dumpsys gfxinfo com.xiaomanjun.sleepdownschedule` 返回 `Failure while dumping the app`。没有取得可靠的修改前帧率，以下性能依据是代码工作量，不是实测 FPS。
- 不改数据库、提醒边界、后台刷新频率、学校适配或课程数据。

## 实时活动

- 按 Android `Notification.ProgressStyle` 区分 overlay tracker 和条内 Point：移除 tracker icon，使用位于当前进度值的原生白色 Point。系统负责条内布局，应用不自行补偿偏移；Point 的最终形状由厂商 SystemUI 控制，需要问题设备验收。
- 进度型课程通知不添加取消/勿扰操作；正文只显示距下课或距上课的分钟数。课前与明日提醒的操作保持原行为。
- 课程、下载及 AI 实时活动均使用 App 图标。进度条 Segment 和通知颜色使用系统默认值，移除固定蓝色。
- 参考：https://developer.android.com/reference/android/app/Notification.ProgressStyle

## 动画工作量

| 路径 | 修改前证据 | 修改后 |
| --- | --- | --- |
| 详细设置背景羽化 | `MorphSnapshotBackground` 每帧一次外轮廓 + 十次羽化均生成相同连续曲率路径 | 生成一次路径，复用到外轮廓和十次绘制；保留十层羽化及视觉参数 |
| 课程编辑器启动 | 表单已推迟至 Open，但仍沿用 Pager 两次录制闸门 | 等待一次有效空壳录制和原有背景安全闸门 |
| 课程编辑器 Open | 每次绘制先录制完整表单，然后再次 drawContent；关闭实际录制的是空壳 | Open 仅绘制实时表单，Preparing/Closing 仍录制空壳 |
| 个性化 | Preparing 挂载完整表单、等待录制、创建原始及模糊两层，移动时回放 | Opening/Closing 只保留原轨迹与玻璃空壳；Open 挂载表单并淡入 220ms |
| 玻璃恢复 | 每组至少 16,666,667ns，全部组结束才显现；60Hz 微小时间戳误差可能多等一帧 | 每批最多两组，保留约 60Hz 工作节奏并容忍 10% 时间戳误差；保留原材质渐入 |

路径构造由每帧 11 次降为 1 次、录制次数减少以及恢复批数减少可以从实现直接验证；这些计数不能换算成实际帧率收益。

## 为什么保留玻璃分组

`WeekScheduleUi` 在成员数大于组数且 eligibility 通过时启用分组，并关闭对应逐卡 surface。`sleepDownGlassGroupSurface` 为每组执行一次采样/模糊/折射，用 remember 的成员并集路径裁剪；不是只做分组标记。单组最多八张，空间规划限制空白覆盖面积。其复杂 SDF 有成本，但没有实测证据表明删除比保留更快，因此只调整恢复节奏，不拆除分组或既有玻璃效果。

## 其他交互

详细设置退出确认顺序为：保存并退出（蓝色）、不保存（红色）、继续编辑。

## 验证方式

- `LiveUpdatePayloadTest` 覆盖上课/课间进度边界。
- `LiveSurfaceMotionTest` 覆盖空壳首次录制、布局闸门以及 60/90/120Hz 恢复节奏。
- 既有全量测试有过时符号引用；使用 `tmp/live-activity-tests.init.gradle` 限定相关测试源集，不修改生产构建配置。
- Release 开启 R8、资源压缩与 lintVital。最终构建结果及是否安装以任务交付报告为准。
- 实机待验收：同一密集课表下编辑器启动、关闭恢复、个性化开关、详细设置往返的帧时间；通知圆点中心线、系统配色和 App 图标。
