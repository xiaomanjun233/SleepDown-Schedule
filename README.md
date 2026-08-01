# SleepDown 课程表

> 把课表做得更顺眼，也更顺手。

> [!IMPORTANT]
> 本仓库是**源码可见项目，并非 OSI 定义的开源项目**。允许个人、非商业地克隆、编译和修改；分发修改版源码或 APK/AAB 时，必须在发布页面和 App 内显著注明原作者 `xiaomanjun233`、原项目链接及“非官方修改版”，不得冒充原创或官方版本。完整条款见 [SleepDown 署名非商业许可 1.0](LICENSE.md)。

SleepDown 是我按照自己的日常使用习惯做的一款 Android 课程表。它没有广告，也不要求登录账号；课表、设置和壁纸默认都留在手机里。

我希望它不只是“能看课表”，而是真的好用：导入课表不折腾，提醒不误事，桌面组件够直观，换一张喜欢的壁纸后也能保持舒服的观感。界面以 MIUIX 和液态玻璃为主，但不会为了效果牺牲基本的可读性和操作效率。

当前版本为 **1.1.1**，最低支持 Android 8.0（API 26）。安装包可以在 [GitHub Releases](https://github.com/xiaomanjun233/SleepDown-Schedule/releases) 或 [Gitee 发行版](https://gitee.com/xiaomanjun233/SleepDown-Schedule/releases) 下载。

## 它能做什么

### 课表

首页提供日视图和周视图，学期日期、总周数与节次时间都能按照学校的实际安排调整。课程既可以只改某一周，也可以一次修改所有同类课程；如果新的时间与已有课程冲突，应用会先提醒你处理，不会直接覆盖。当前周还可以跟随开学日期自动变化，省去每周手动切换。

### 导入

除了逐项手动录入，SleepDown 也可以从教务系统、ICS 文件、文字、表格、图片和 PDF 导入课表。教务导入已经适配武汉科技大学，并保留通用教务页面作为补充；格式比较特殊时，还可以交给 OpenAI、DeepSeek、小米 MiMo 或自定义兼容接口解析。无论使用哪种导入方式，结果都会先展示预览，只有确认以后才会写入课表。

### 提醒和桌面组件

课程提醒可以按需要提前一段时间出现；系统支持时，也能用实时活动样式查看上下课倒计时。桌面上可以放置今日课程或今日助手组件，每个组件的背景、取景、缩放、模糊和亮度都可以单独调整。应用会按设置检查新版本，但不会在后台强制下载，自动检查也可以随时关闭。

### 今日助手

今日助手可以结合当天课程、教学周和可选的天气信息回答问题，也能帮忙调整课程和部分设置。涉及数据变更时，它会先说明准备做什么，得到确认后才真正执行。对话记录和长期记忆保存在本机，可以随时查看、修改或关闭。

### 外观

首页壁纸可以为横屏和竖屏分别取景，课程卡片的颜色、透明度、模糊和玻璃效果也留出了足够的调整空间。手机和平板会使用不同的布局，大屏下的日视图、周视图、设置页和助手不会只是简单拉伸。启动图标则可以固定为浅色或深色，也可以跟随系统深色模式自动切换。

## 第一次使用

先在“设置 → 课表设置”里填好开学日期和节次时间，然后从首页右上角添加或导入课程。需要课程提醒的话，再到通知设置里开启，并按照手机系统的要求授予通知和后台运行权限。

AI 功能不是必需的。需要时，选择服务商和模型，填入自己的 API Key 即可；不用 AI 也不影响普通课表功能。

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
