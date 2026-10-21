# 2026-10-21 动效维护边界整理

## 目标与基线

继续 #79 的架构重构，基于本分支 `80e1c370`，本批运行时代码提交 `69c99b07`。用户补充“不是调整，就是看看怎么让改动效的时候更好维护”，因此本批只调整职责与定位入口，保留原曲线、时长、帧算式、播放状态机及视觉表现。没有安装、合并或发布授权。

本轮已核对 #79 仍开放、目标 main `9b6acbb2`、远端 head 与本地一致，且没有评审线程。主工作目录的独立学校任务与未提交交接不属于本次范围。

## 原先的问题与实际改动

- 通用横屏切页放在 `feature/home`，课程管理、设置和 app 必须引用首页。现归 `core/ui/interaction/PaneTransitions.kt`，同文件 `LandscapePageMotion` 集中原来的缩放、模糊与淡入淡出规格；目标、内容、输入屏蔽、回调和稳定端释放仍由原调用链处理。
- 设置的阻尼曲线混在导航枚举/Saver 中，推进和返回规格又在页面中展开。现归 `SettingsNavigationMotion.kt`，页面只传方向，导航栈、标题和恢复状态留在原文件。原计算与默认参数逐字对照一致。
- 课程编辑外壳同时持有轨迹、交接、背景缩放、表单错峰、布局及绘制。现将路线参数和纯帧计算归 `CourseEditorMotion.kt`，外壳从 1266 行降为 1053 行，保留原 Animatable、并发播放、generation、预热及卸载。app 的关闭等待引用同一规格；没有创建新的控制器或通用抽象。
- 首页背景深度规则直接读取课程功能中的缩放常量。现由调用方显式提供编辑器缩放值，仍使用原归一化算式。补充不同编辑器缩放值的现有回归断言，防止后续改参数后仍使用旧范围。
- 新增[动效维护入口](../architecture/MOTION_MAINTENANCE.md)，从实际界面定位参数、纯计算、播放及释放，并由文档入口、设计系统和跨 Activity 文档引用。区分原始时钟和可见曲线，记录独立路线、退出内容和材质资源的关联修改范围。

课程管理跨 Activity、Agent 与首页编辑器保持独立路线；相似曲线没有强行合并。共享的首页/跨 Activity 几何继续沿用前一批归入 transition 的实现。没有改 #78 的材质绘制、COUI 原生绑定、Miuix 补丁、依赖或版本。

## 验证

- 自动对照迁移前后源码：课程编辑参数及纯帧计算在限定名称/可见性映射后完全一致；设置阻尼计算及默认参数一致。
- `compileGithubReleaseKotlin` 及 8 类 120 项现有定向回归通过，4m31s，0 失败/错误/跳过。包含 GlassFramework 26、HomeSwitchMotion 5、HomeMotionPerformancePolicy 11、HomeAnchoredMorphGeometry 45、HomePersonalizationGeometry 5、DetailMorphBlur 5、TransitionSessionStateMachine 5、ScheduleConfigChangeMerge 18。沿用本地 Miuix、单 worker、临时 4 GiB 堆；没有修改构建配置。日志在本机忽略目录 `tmp/architecture-motion-maintenance-20261021.log`。
- 架构检查：563 个生产 Kotlin/Java 文件、0 项违规文件依赖、44 条既有声明循环；13 项检查器回归通过。没有改变基线或新增豁免；本批移除了具体反向引用，剩余同层聚合边仍如实记录。
- 动效索引所指源码及相对链接存在，`git diff --check` 通过。最终 head 的完整云端检查见 #79，不用上一轮 CI 代替。
- 未进行实机视觉、帧率、OEM 音量或真实 AI 账号验证，没有安装。本批不宣称改善曲线或流畅度。

Room、DAO、备份 schema、已发布组件/Intent wire、版本及用户数据路径未变。此前课程/静音与 AI 修复仍由其原报告说明；构建和源码对照不替代真实账号或设备验收。
