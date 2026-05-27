# SleepDown 课程表

基于 Jetpack Compose 的液态玻璃风格课程表 Android 应用，支持手动编辑课表、教务系统一键导入、上课提醒与实时活动、桌面小组件和丰富的个性化外观设置。

## 特性

- **周视图课程表** — 按周次切换，支持单双周筛选，课程卡片按节次和星期排列，隐藏无课周末
- **液态玻璃外观** — 毛玻璃质感 UI，可自定义壁纸、模糊度（课程卡片/全局）、透明度、色调、深色模式/跟随系统
- **无缝过渡动画** — 课程卡片到编辑弹窗的裁剪遮罩展开、加号按钮连贯变形为菜单、周切换甩尾动画、启动页圆形展开
- **教务系统导入** — 内建数十所高校教务适配（基于 shiguang_warehouse），WebView 自动登录 + JS 抓取课表
- **智能提醒** — 课前通知（可选标准通知 / 实时活动），支持荣耀 MagicOS 10 实时活动、自动计算当前周
- **桌面小组件** — 今日课程小组件，深色/浅色自适应
- **手动编辑** — 添加/编辑/删除课程，按单周修改或全局同步，JSON 导入导出课表

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
│       │   ├── MainActivity.kt                   # 主 Activity，包含全部 UI 组件与页面（~7500 行）
│       │   ├── Data.kt                           # Room 实体、DAO、Repository、ViewModel、默认配置
│       │   ├── GlassUi.kt                        # 液态玻璃效果组件（GlassSurface / CourseGlassCard / GlassLens）
│       │   ├── ScheduleLogic.kt                  # 课表解析、导入导出、通知调度、日期计算
│       │   ├── EduImport.kt                      # 教务导入 WebView 适配与管理
│       │   └── TodayCoursesWidgetProvider.kt     # 桌面小组件（Glance）
│       ├── java/com/kyant/
│       │   └── backdrop/catalog/                 # backdrop 库 UI 组件（LiquidButton / LiquidPanel / LiquidSlider 等）
│       └── res/
│           ├── drawable/                         # 图标与图片资源
│           ├── drawable-nodpi/                   # 默认壁纸与捐赠二维码
│           ├── mipmap/                           # 启动图标
│           ├── values/                           # 字符串、颜色、主题
│           ├── layout/                           # 通知与小组件布局
│           └── xml/                              # 小组件配置
├── gradle/
│   └── wrapper/
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
| 玻璃渲染 | kyant/backdrop（内嵌组件）+ 自定义 drawBackdrop 管线 |
| 教务适配 | shiguang_warehouse（assets 内嵌） |
| 浏览器 | AndroidX Browser（Custom Tabs） |
| 桌面小组件 | Glance |
| 构建 | Gradle KTS |

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

- `import io.github.kyant0:backdrop:2.0.0-alpha03` 提供 `Backdrop` 和 `drawBackdrop` 渲染管线
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

- **周视图甩尾动画** — `Animatable` + `spring(dampingRatio=0.68, stiffness=300)` 驱动 `graphicsLayer.translationX`，按行计算滞后系数实现分层拖尾
- **课程卡片展开** — `drawWithContent` + `clipPath` + `RoundRect` + `CornerRadius` 实现从卡片形状到弹窗全尺寸的裁剪遮罩过渡，跨分辨率坐标变换
- **加号菜单变形** — `updateTransition` + `keyframes` 控制 width/height/sinkOffset/radius/iconAlpha/contentAlpha/dynamicBlur 七属性同步变形
- **启动页圆形展开** — `Path` + `PathFillType.EvenOdd` 构造环形遮罩，`Animatable` 驱动从屏幕中心向外扩散至对角线消失

## 开源引用

本项目使用了以下开源项目：

| 项目 | 作者 | 协议 | 用途 |
|------|------|------|------|
| [backdrop](https://github.com/kyant/backdrop) | Kyant | Apache 2.0 | 液态玻璃渲染引擎 |
| [shiguang_warehouse](https://github.com/xingheyuzhuan/shiguang_warehouse) | 星河欲转 | MIT | 教务系统适配资源 |
| [AndroidX / Jetpack](https://developer.android.com/jetpack) | Google | Apache 2.0 | UI 框架、数据库、生命周期 |
| [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization) | JetBrains | Apache 2.0 | JSON 序列化 |
| [Material 3](https://github.com/material-components/material-components-android) | Google | Apache 2.0 | 设计系统组件 |

## 许可证

本项目仅供学习与参考。代码中嵌入的 `kyant/backdrop` 组件和 `shiguang_warehouse` 资源分别遵循其原项目的 Apache 2.0 和 MIT 协议。
