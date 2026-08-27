# SleepDown 项目结构

本文档定义 Android 主工程的目录边界与渐进式重构规则。结构调整不得改变业务行为、持久化协议、视觉参数、动画时序或 Android 组件语义。

## 仓库区域

- `app/`：Android 应用主模块。
- `benchmark/`：Macrobenchmark 与 Baseline Profile，不承载业务实现。
- `sleepdown-site/`：独立静态官网，不参与 Android Gradle 构建。
- `docs/`：架构、迁移、性能与交接记录。
- `patches/`：第三方依赖补丁。
- `release-notes/`：面向用户的版本说明。
- `SleepDown-Server/`：本地私有后端，始终保持 Git 忽略，不属于公开 Android 工程。

## Android 包边界

`com.xiaomanjun.sleepdownschedule` 根包只保留稳定 Android 入口、Application 和必要兼容门面。实现按以下方向组织：

```text
app -> feature -> domain/model
 |       |          ^
 |       +-------> data
 +---------------> core
feature/ui ------> glass
app -------------> transition
```

- `app/`：应用装配、导航与顶层状态协调。
- `model/`：跨功能共享的当前业务/持久化模型；重构阶段不额外复制一套 domain model。
- `data/`：Room、数据库修复、迁移和 Repository。
- `domain/`：无 Android UI 副作用的课表规则与计算。
- `core/`：通用 UI、远程配置、应用身份、壁纸与性能基础设施。
- `feature/`：按用户功能纵向组织的页面、状态和工作流。
- `glass/`：液态玻璃统一框架，保持既有采样域和材质边界。
- `transition/`：跨 Activity 转场统一框架；Oplus 暂缓代码不得在结构重构中修改行为。
- `com.kyant.backdrop.catalog/`：SleepDown 修改过的第三方组件，保持原包名和许可说明。

## 文件规则

1. 一个 Manifest Activity、Service、Receiver 或 Widget Provider 独占一个文件。
2. 文件名对应主要声明；不再新增 `Data.kt`、`Logic.kt`、`Ui.kt` 这类跨职责聚合文件。
3. 页面根 Composable、可复用组件、状态持有者和纯计算分别存放。
4. 大文件按职责拆分，不按固定行数机械切割；超过 800 行必须说明保持聚合的原因。
5. `core`、`data` 和 `domain` 不依赖具体 `feature` 页面。
6. 功能之间通过共享模型、接口或 `app` 层协调，不直接读取另一功能的私有 UI 状态。
7. 测试包镜像生产包；迁移文件移动时同步移动对应测试。
8. Android 资源继续遵循系统资源目录，新增资源使用功能前缀，既有资源不做无意义批量重命名。

## 安全迁移规则

- 拆文件与改逻辑分批进行；机械移动批次不顺手重写实现。
- Manifest 组件 FQCN、`TransitionRouteCatalog` 和 Intent wire id 必须保持一致，或由稳定根包门面兼容。
- Room schema version、表名、字段、迁移顺序和 SQL 在结构批次中不得改变。
- `BackupFormatV1`、SleepDown 口令、ICS、Widget、通知和 AI 历史兼容格式不得改变。
- Home、周视图、Morph 与玻璃代码拆分时保持函数体、Modifier 顺序、几何和时间参数不变。
- 每批变更使用独立回退点，并执行与风险相称的最小充分验证。

## 当前落地结构（2026-08-24）

```text
com/xiaomanjun/sleepdownschedule/
├── *.kt                              # 稳定 Android 入口、Application 与兼容门面
├── app/{config,startup,state,ui}/
├── model/
├── data/local/
├── data/repository/
├── domain/{course,schedule}/
├── core/
│   ├── {analytics,identity,performance,remoteconfig,wallpaper}/
│   └── ui/{designsystem,interaction,settings,text}/
├── feature/
│   ├── home/{day,week,overlay}/
│   ├── course/{editor,management}/
│   ├── schedule/{manager,picker}/
│   ├── importing/{history,progress}/
│   ├── agent/background/
│   ├── backup/
│   ├── reminder/
│   ├── settings/
│   ├── update/
│   └── widget/providers/
├── glass/ui/
└── transition/legacy/
```

原跨职责聚合文件已完成以下实质拆分：

- `Data.kt` 拆为共享模型、Converters、DAO、Room migrations、Repository 和纯配置变换；`AppDatabase` 的稳定 FQCN 保留。
- `ScheduleLogic.kt` 拆为日历计算、导入编解码、提醒与 Android 入口组件。
- 课程管理、课表管理、首页日/周视图、Home overlay、设置通用组件、AI 历史/进度、Widget provider 和 Agent 后台服务均进入对应功能包。
- 二级页内容节奏、Dialog、Alert、Picker、输入胶囊、操作按钮和 QuickSheet 的稳定壳已收口到 `core/ui/designsystem/`；规范按设置型、内容型和沉浸型保留可组合差异，详见 `SLEEPDOWN_DESIGN_SYSTEM.md`。
- Day Agent 的稳定提示、工具决策和最终写入协议已分段，已读快照工具会在同一回合动态收窄；运行规则见 `DAY_AGENT_RUNTIME.md`。
- Manifest 中已有 Activity、Service、Receiver 和 Widget Provider 的 FQCN 由根包薄入口保持不变；数据库、备份和 Intent 协议不需要迁移。

## 稳定入口与实现映射

| 根包入口 | 实现位置 |
| --- | --- |
| `SettingsDetailActivity`、`EduSchoolSelectActivity`、`EduImportActivity` | `app.ui` 中对应 `*Host` |
| `ScheduleManagerActivity` | `feature.schedule.manager.ScheduleManagerActivityHost` |
| AI 历史、详情与进度 Activity | `feature.importing.history` / `feature.importing.progress` 中对应 `*Host` |
| 四个扩展 Widget Provider | `feature.widget.providers` 中对应 `*Host` |
| `DayAgentForegroundService` | `feature.agent.background.DayAgentForegroundServiceHost` |
| Oplus 课程管理入口 | 根包独立薄文件，继承既有课程管理 Activity |

根包中的 `ScheduleDataCompat.kt`、`ScheduleCalendarCompat.kt`、`PeriodSchemesCompat.kt` 和 `ScheduleLabelsCompat.kt` 是有意保留的源码兼容门面。它们让结构迁移保持机械、可回退，也避免一次性改写全部调用点。

## 有意保留的聚合文件

以下文件仍超过 800 行，但已归入清晰的职责目录。本轮不继续按行数切割，以免把一次结构重构扩大成视觉、手势或协议重写：

| 文件组 | 保持聚合的原因 |
| --- | --- |
| `app/ui/ScheduleAppUi.kt` | 应用级 Compose 装配及多个稳定 Activity host 共享状态；后续应按页面路线单独分批拆分。 |
| `feature/home/day`、`feature/home/week`、`feature/home/overlay` | 包含已验收的周视图手势、缓存、液态玻璃与 Morph 几何/时序，当前按视觉链路保持内聚。 |
| `feature/settings/SettingsUi.kt`、`feature/schedule/{manager,picker}` | 设置和课表编辑存在密集的会话状态、弹层与保存交接，已完成分类，后续可按页面根节点拆分。 |
| `feature/importing`、`feature/agent` | 单文件内是完整导入会话或 Agent 工具调用状态机，不能只按 Composable 数量机械切割。 |
| `feature/widget` | Provider 生命周期、RemoteViews 像素布局、缓存和位图渲染共同决定组件输出，已按 provider 类型分组。 |
| `transition/legacy`、`transition/OplusSeamlessBackend.kt` | 保留既有 renderer 和暂缓的 Oplus 调查边界；结构整理不改动动画所有权或交接时序。 |
| `data/local/AppDatabaseMigrations.kt`、`data/repository/ScheduleRepository.kt` | 前者按 Room 版本形成连续迁移账本，后者维持单一数据入口；拆分前需先定义稳定接口。 |

在包依赖稳定且不存在反向引用后，再评估拆分 `:core:*` 和 `:feature:*` Gradle 模块。目录整理本身不以增加模块数量为目标。
