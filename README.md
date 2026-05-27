# SleepDown 课程表

可能是安卓首个液态玻璃课程表 APP。

SleepDown 课程表是一款注重视觉体验和日常效率的本地课程表应用。它不仅能管理课程、提醒上课，还加入了液态玻璃界面、自定义壁纸、实时活动、桌面小组件、AI 辅助导入和教务系统导入等功能，让课程表更好看，也更顺手，最重要的是无广告。

## 特色功能

- **液态玻璃界面** — 应用内 Dock、按钮、弹窗、课程卡片等元素采用 LiquidGlass 风格设计，支持浅色、深色和壁纸环境下的自适应玻璃效果（液态玻璃来自 [@kyant](https://github.com/kyant) 的开源项目）
- **喊你上课岛** — 在支持安卓实时活动 API 的系统上（原生 Android 16、Xiaomi HyperOS 3.0.300 以上、ColorOS 16），可将即将上课的信息上岛提醒，并支持取消本次提醒、开启或关闭勿扰模式
- **自定义岛上缩略态** — 实时活动缩略态可选择显示上课地点、剩余时间、短标或自动模式
- **AI 手动导入** — 可将课表 PDF 交给任意 AI 整理，再把返回文本复制进应用解析导入
- **教务系统导入** — 支持通过学校教务系统网页导入课程，并可手动修改网址（教务系统导入来自 [@拾光开发者](https://github.com/xingheyuzhuan) 的开源项目）
- **个性化课程卡片** — 支持调整课程卡片颜色、透明度、液态玻璃效果和周视图卡片高度
- **桌面小组件** — 可在桌面查看当日课程
- **Beta 诊断日志** — 内置日志导出功能，方便定位实时活动、闪退和导入问题

## 基础使用

首次打开应用后，建议先进入"设置 > 课表设置"，配置总周数、当前周、学期开始日期和节次时间（导入课表后将自动设置部分信息）。开启"自动计算当前周"后，应用会根据学期开始日期自动判断当前是第几周。

点击首页右上角加号，可以添加单节课或进行手动导入。编辑已有课程时，可选择只修改当前课程，或同步修改同类课程。

## 课表导入

手动导入时，将你的课表 PDF 连同应用提供的提示词一起发给任意 AI，然后将 AI 返回的文本复制到输入框内，点击解析并预览，确认无误后导入。

教务系统导入可从学校列表进入。如果默认网址无法访问，可以在顶部网址栏手动修改教务系统地址。

## 提醒与实时活动

在"设置 > 通知设置"中可开启课程提醒，设置提前提醒分钟数，并选择普通通知或实时活动。实时活动目前仅支持原生安卓系统、ColorOS 16、HyperOS 3.0.300 以上版本。为保证稳定通知，需要允许通知，并打开允许后台运行（或锁定后台）、关闭电池优化或允许自启动（这些设置都可能增加手机的耗电）。

## 个性化

首页个性化菜单中可以设置壁纸、壁纸模糊、亮度、课程卡片颜色、课程卡片玻璃效果和 Dock 栏位置。

## 数据说明

SleepDown 课程表主要数据保存在本机，不依赖云端同步。卸载应用可能会清除课表和设置数据，重要课表建议保留原始导入文本或自行备份。

## 项目结构

```
CourseSchedule/
├── app/
│   ├── build.gradle.kts                          # 应用构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── shiguang_warehouse-main/          # 教务适配资源（来自上游仓库）
│       ├── java/com/example/courseschedule/
│       │   ├── MainActivity.kt                   # 主 Activity，包含全部 UI 组件与页面
│       │   ├── Data.kt                           # Room 实体、DAO、Repository、ViewModel
│       │   ├── GlassUi.kt                        # 液态玻璃效果组件
│       │   ├── ScheduleLogic.kt                  # 课表解析、导入导出、通知调度
│       │   ├── EduImport.kt                      # 教务导入 WebView 适配
│       │   └── TodayCoursesWidgetProvider.kt     # 桌面小组件（Glance）
│       ├── java/com/kyant/
│       │   └── backdrop/catalog/                 # backdrop 库内嵌 UI 组件
│       └── res/
│           ├── drawable/                         # 图标资源
│           ├── drawable-nodpi/                   # 默认壁纸与捐赠二维码
│           ├── mipmap/                           # 启动图标
│           ├── values/                           # 字符串、颜色、主题
│           ├── layout/                           # 通知与小组件布局
│           └── xml/                              # 小组件配置
├── gradle/wrapper/
├── build.gradle.kts                              # 根构建配置
├── settings.gradle.kts                           # 项目设置
├── gradle.properties                             # Gradle 属性
└── gradlew / gradlew.bat                         # Gradle Wrapper
```

## 技术栈

| 类别 | 库 |
|------|-----|
| UI 框架 | Jetpack Compose + Material 3 |
| 数据库 | Room + KSP |
| 序列化 | Kotlin Serialization |
| 玻璃渲染 | kyant/backdrop + 自定义 drawBackdrop 管线 |
| 教务适配 | shiguang_warehouse（assets 内嵌） |
| 浏览器 | AndroidX Browser（Custom Tabs） |
| 桌面小组件 | Glance |

## 构建

```bash
./gradlew assembleDebug
```

| 配置 | 值 |
|------|-----|
| compileSdk | 36 |
| minSdk | 26 |
| targetSdk | 36 |
| JDK | 17+ |

## 引用与修改说明

### [kyant/backdrop](https://github.com/kyant/backdrop) — 液态玻璃渲染引擎

- `io.github.kyant0:backdrop:2.0.0-alpha03` 提供 `Backdrop` 和 `drawBackdrop` 渲染管线
- `com.kyant.backdrop.catalog.*` 内嵌了上游 UI 组件（LiquidButton、LiquidPanel、LiquidSlider、LiquidToggle、LiquidBottomTab/Tabs）及交互工具类
- 在此基础上扩展了 `GlassSurface`、`GlassPill`、`GlassLens`、`GlassDialogSurface`、`CourseGlassCard` 等自定义玻璃组件
- `GlassTokens` 封装了 Pill / Dialog / CourseCard 三种场景的模糊、透镜、表面透明度和边框透明度参数预设
- `CourseGlassCard` 额外应用了 `vibrancy()` 效果和配置驱动的动态模糊/透镜参数

### [shiguang_warehouse](https://github.com/xingheyuzhuan/shiguang_warehouse) — 教务适配资源

- `assets/shiguang_warehouse-main/` 内嵌了学校教务适配资源（YAML 配置 + JS 适配脚本）
- `EduImport.kt` 通过 WebView 加载学校教务登录页，注入 JS 适配脚本自动抓取课表数据
- 支持通过 Custom Tabs 进行 CAS/OAuth 登录流程
- 适配器注册表由 `index/root_index.yaml` 索引，各校适配脚本位于 `resources/<学校代码>/`

### 自定义动画体系

- **周视图甩尾动画** — `Animatable` + `spring()` 驱动 `graphicsLayer.translationX`，按行计算滞后系数实现分层拖尾
- **课程卡片展开** — `drawWithContent` + `clipPath` + `RoundRect` 实现从卡片形状到弹窗全尺寸的裁剪遮罩过渡，跨分辨率坐标变换
- **加号菜单变形** — `updateTransition` + `keyframes` 控制七属性同步变形（width/height/sinkOffset/radius/iconAlpha/contentAlpha/dynamicBlur）
- **启动页圆形展开** — `Path` + `PathFillType.EvenOdd` 构造环形遮罩，从屏幕中心向外扩散至对角线消失

## 开源引用

本项目使用了以下开源项目：

| 项目 | 作者 | 协议 | 用途 |
|------|------|------|------|
| [backdrop](https://github.com/kyant/backdrop) | Kyant | Apache 2.0 | 液态玻璃渲染引擎 |
| [shiguang_warehouse](https://github.com/xingheyuzhuan/shiguang_warehouse) | 星河欲转 / 拾光开发者 | MIT | 教务系统适配资源 |
| [AndroidX / Jetpack](https://developer.android.com/jetpack) | Google | Apache 2.0 | UI 框架、数据库、生命周期 |
| [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization) | JetBrains | Apache 2.0 | JSON 序列化 |
| [Material 3](https://m3.material.io) | Google | Apache 2.0 | 设计系统组件 |

## 许可证

本项目仅供学习与参考。代码中嵌入的 `kyant/backdrop` 组件和 `shiguang_warehouse` 资源分别遵循其原项目的 Apache 2.0 和 MIT 协议。
