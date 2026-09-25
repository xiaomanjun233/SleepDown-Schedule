# 功能地图与设计入口

本页用产品视角说明当前 `main` 的主要功能，并给出实现入口。它是导航页，不是固定功能数量清单；行为以当前代码和设备验收为准。

| 用户任务 | 当前能力 | 主要实现与深入文档 |
| --- | --- | --- |
| 看课表 | 日、周视图；自动教学周、无界周视图、天气、桌面与平板布局；按课程实际时间显示状态 | `feature/home/day/`、`feature/home/week/`、`domain/schedule/`、[节次方案](PERIOD_SCHEMES_GUIDE.md) |
| 管课程与调休 | 新建、编辑、复制课程；逐周与批量调整；调休、停课、补课预览及确认 | `feature/course/`、`feature/schedule/`、`feature/settings/ScheduleAdjustments*` |
| 导入课表 | 手动、教务、WakeUp/星链口令、ICS、文件与 AI 导入；本地预览后确认写入 | `feature/importing/`、[AI 文件导入](ai/AI_FILE_IMPORT.md) |
| 教务刷新 | 学校适配器、已登录会话、手动与周期刷新；失效时重新连接 | `feature/importing/` 的教务入口与刷新实现；学校协议以拾光 adapter 为准 |
| 今日助手 | 利用课表与时间上下文回答和规划；修改课程或设置前展示计划并确认 | `feature/agent/`、[运行链路](architecture/DAY_AGENT_RUNTIME.md) |
| 提醒与厂商能力 | 普通通知、课程实时活动及按设备出现的流体云、YOYO 建议、小米超级岛实验能力 | `feature/reminder/`、`feature/experimental/`、`feature/coloros/`、[版本与渠道](RELEASE_MODEL.md) |
| 桌面组件 | 今日课程与今日助手组件；背景、取景、缩放、模糊和亮度可调 | `feature/widget/`、`feature/widget/providers/` |
| 外观与交互 | 壁纸取景、课程配色、玻璃材质、日周转场、Dialog/Picker/QuickSheet | `core/ui/designsystem/`、`glass/`、`transition/`、[设计系统](architecture/SLEEPDOWN_DESIGN_SYSTEM.md) |
| 数据与升级 | `.sleepdown` 备份恢复、旧包身份迁移、应用内版本检查 | `feature/backup/`、`feature/update/`、[备份格式](migration/BACKUP_FORMAT_V1.md) |

## 设计系统如何落到功能里

页面先按任务选择设置型、内容型或沉浸型布局，再使用公共 Dialog、Alert、Picker 和 QuickSheet。玻璃层必须知道采样的页面 Backdrop；卡片颜色和文字要在实际壁纸上保持可读。锚定页面沿用已有 Morph 路线，稳定页面不持续持有动画用的裁切和离屏资源。上述是当前代码的共同语言，尺寸与具体入口以[设计系统](architecture/SLEEPDOWN_DESIGN_SYSTEM.md)和[玻璃框架](performance/LIQUID_GLASS_FRAMEWORK.md)为准。

功能可以按用户目标继续变化。涉及备份、Room、导入协议、通知和 Android 组件身份时，先核对已发布格式与迁移链路；视觉更新则同时检查手机、平板、明暗主题、壁纸、字体比例及无玻璃降级。
