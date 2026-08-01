# SleepDown 课程表

> 一款本地优先、无广告的 Android 课程表。把日常课表、提醒、导入、桌面组件和液态玻璃界面放在同一个顺手的应用里。

> [!IMPORTANT]
> 本仓库是**源码可见项目，并非 OSI 定义的开源项目**。允许个人、非商业地克隆、编译和修改；分发修改版源码或 APK/AAB 时，必须在发布页面和 App 内显著注明原作者 `xiaomanjun233`、原项目链接及“非官方修改版”，不得冒充原创或官方版本。完整条款见 [SleepDown 署名非商业许可 1.0](LICENSE.md)。

SleepDown 课程表当前版本为 **1.1.1**。应用使用 Jetpack Compose 构建，最低支持 Android 8.0（API 26）。课表和偏好默认只保存在设备本地；仅在你主动使用 AI 导入、今日助手天气或版本更新时才会访问相应的网络服务。

## 功能一览

### 课表与日常使用

- 日视图与周视图：按当前教学周展示课程，支持自定义学期起始日、总周数、当前周和节次时间。
- 自动周次：开启后按学期开始日期自动推算当前周，并正确处理开学前、教学中和学期结束后的边界。
- 灵活编辑：支持新增、编辑、删除课程；可单独修改某一周，也可同步修改同类课程。
- 冲突提醒与处理：变更星期、节次或周次时会提示新增冲突；可跳转到冲突周，并把课程移动到最近的空闲位置。
- 导入预览：所有导入结果都先经过本地校验并显示预览，确认前不会覆盖现有课表。
- ICS 支持：可通过文件打开或分享方式导入 `.ics` 日历课表。

### 导入方式

- 手动导入：粘贴课程文本或按界面逐项录入。
- AI 课表导入：可解析 TXT、CSV、图片和 PDF；PDF 会优先提取文本，必要时再由支持视觉输入的模型识别页面图像。
- 教务系统导入：内置学校适配资源，支持登录教务网页、抓取课表并导入。默认适配无法覆盖时，可在确认后将页面文本或截图交给 AI 解析。
- 多 AI 服务：内置 OpenAI、DeepSeek、小米 MiMo 和多组自定义 OpenAI 兼容接口；扩展列表还包含多家预设服务。不同模型是否支持图片、PDF 原文件、流式回复和工具调用，以应用设置页的能力提示及服务商实际限制为准。

### 提醒、组件与更新

- 课程提醒：可设置提前提醒时间，支持普通通知或系统支持时的实时活动样式；实时活动内可提供勿扰和取消本次提醒等操作。
- 今日课程组件：提供 4×2 和 2×2 两种今日课程组件。
- 今日助手组件：在桌面展示课程、天气与预警摘要。
- 组件个性化：三种组件均可分别设置背景图、取景、缩放、模糊和亮度，并在保存前预览。
- 应用更新：应用可检查并下载 Gitee Release 中的最新 APK；是否自动检查可在设置中控制。

### 今日智能助手

- 按需读取当前日期、教学周、当日/本周课表、节次、应用设置及可选天气信息，生成日程建议。
- 支持流式回复、图片附件和可开关、查看、编辑的本地长期记忆；当前思考或工具状态会以扫光动效提示执行进度。
- 涉及修改课程或设置时，先展示执行计划，须经确认后才会写入本地数据。

### 外观与体验

- 自适应液态玻璃：Dock、按钮、弹窗和课程卡片使用基于 backdrop 的玻璃渲染，并适配浅色、深色与壁纸背景。
- 个性化：首页壁纸支持独立横竖屏取景、缩放、模糊和亮度；可调整课程卡片配色、透明度、玻璃效果、周视图卡片高度、Dock 位置和浅色/深色启动图标。
- 动画与性能：课程卡片、详细设置、编辑弹窗、加号菜单及导入页使用连续转场和动态模糊；项目提供 Macrobenchmark 和 Baseline Profile 配置用于启动与交互性能验证。
- Beta 诊断日志：可导出日志，便于定位实时活动、导入和闪退问题。

## 开始使用

1. 首次进入应用后，打开“设置 → 课表设置”，配置总周数、学期开始日期和节次时间。
2. 在首页右上角添加课程，或选择手动导入、AI 导入、教务系统导入、ICS 文件导入。
3. 在“设置 → 通知设置”开启课程提醒。为了可靠到达，Android 设备通常还需要允许通知、后台运行/自启动，并按系统情况关闭电池优化。
4. 若启用 AI，请在设置中选定服务商、模型和输入能力后填写自己的 API Key。密钥保存在本机应用私有存储中，课表内容会按你确认的请求发送给所选服务商。

> 实时活动的展示能力取决于手机厂商和系统版本。请以设备实际的通知权限、实时活动支持和后台限制为准。

## 数据与隐私

- 课表、设置、壁纸取景和助手记忆使用本地 Room 数据库或应用私有存储保存，不提供云同步。
- 卸载应用可能清除本地数据；重要课表建议保留原始导入文件或自行备份。
- AI 导入、AI 助手和天气功能会把实现该功能所需的数据发送到你选择的服务端；请确认服务商的隐私政策、计费规则和数据处理条款。
- 教务系统登录在 WebView/Custom Tabs 流程中完成；请只在可信的学校站点输入账号与密码。

## 获取项目（仅供学习）

两个代码托管平台保持同一份 `main` 分支代码：

```bash
# GitHub
git clone https://github.com/xiaomanjun233/SleepDown-Schedule.git

# Gitee
git clone https://gitee.com/xiaomanjun233/SleepDown-Schedule.git
```

分发修改版时必须遵守许可证中的显著署名、修改说明和非官方标识要求；商业使用须另行取得书面授权。

- `main`：已验证的最新稳定代码。
- `v<版本号>`：正式发布版本标签，例如 `v1.1.1`。
- `feature/*`、`fix/*`、`release/*`、`codex/*`：短期开发分支，不作为长期下载入口。

## 项目结构

```text
CourseSchedule/
├── app/
│   ├── src/main/java/com/example/courseschedule/
│   │   ├── MainActivity.kt                  # Activity 入口与主题容器
│   │   ├── CourseScheduleApp.kt             # 进程级依赖与生命周期
│   │   ├── Data.kt                          # Room 实体、DAO、迁移和 Repository
│   │   ├── Schedule*.kt                     # 课表状态、逻辑、选择器与刷新协调
│   │   ├── *ScheduleUi.kt                   # 首页、周视图、编辑与设置界面
│   │   ├── AiImport.kt / ImportUi.kt        # AI、文件与手动导入
│   │   ├── EduImport.kt / EduPageCapture.kt # 教务网页导入和页面抓取
│   │   ├── DayAgent*.kt / Agent*.kt         # 今日助手、工具和服务端传输
│   │   ├── *Widget*.kt                      # 桌面组件及其个性化
│   │   └── GlassUi.kt / *Morph*.kt          # 玻璃材质与转场
│   ├── src/main/assets/shiguang_warehouse-main/
│   │                                        # 教务系统适配资源
│   └── src/test/ / src/androidTest/          # 单元测试、迁移和仪器测试
├── benchmark/                                # Macrobenchmark 与 Baseline Profile
├── docs/                                     # 版本说明、性能基线和节次方案文档
├── patches/miuix-0.9.3-sleepdown.patch      # Miuix 组合构建补丁
├── THIRD_PARTY_NOTICES.md                    # 第三方代码与许可声明
└── gradlew / gradlew.bat                     # Gradle Wrapper
```

## 技术栈

| 类别 | 主要实现 |
| --- | --- |
| UI | Jetpack Compose、Material 3、Miuix |
| 数据 | Room、KSP、Kotlin Serialization |
| 图形 | `kyant/backdrop`、自定义玻璃与动态模糊管线 |
| 导入 | Android WebView / Custom Tabs、PDF Renderer、ICS 解析 |
| 通知 | Alarm、Foreground Service、实时活动兼容逻辑 |
| 桌面组件 | RemoteViews |
| 性能 | Macrobenchmark、Baseline Profile |

## 第三方项目与许可证

- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)：液态玻璃目录组件基础，Apache-2.0。
- [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)：设置和教务导入页面组件，Apache-2.0。
- [xingheyuzhuan/shiguang_warehouse](https://github.com/xingheyuzhuan/shiguang_warehouse)：教务系统适配资源，MIT。
- AndroidX、Jetpack Compose、Kotlin Serialization 等依赖遵循各自许可证。

本项目对第三方代码的引用和修改范围见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可证

本项目采用 [SleepDown 署名非商业许可 1.0](LICENSE.md)，**不是 OSI 定义的开源软件**。

- 允许：个人、非商业地查看、克隆、编译、修改，以及在满足许可条件时分发修改版。
- 修改版分发：必须在发布页面和 App 内显著注明“基于 SleepDown 修改”、原作者 `xiaomanjun233`、原项目链接和主要修改内容，并明确其不是官方版本。
- 禁止：移除或弱化署名、冒充原创或官方版本、使用易混淆的名称/包名/图标，以及未经授权的商业使用。
- 其他用途：必须事先取得项目作者的明确书面授权。

仓库中的第三方代码和资源继续遵循其原始许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
