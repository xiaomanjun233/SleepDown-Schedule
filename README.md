# SleepDown 课程表

基于 Jetpack Compose 的液态玻璃风格课程表 Android 应用。

## 项目结构

```
CourseSchedule/
├── app/
│   ├── build.gradle.kts                          # 应用构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── shiguang_warehouse-main/          # 教务系统适配资源（子模块）
│       ├── java/com/example/courseschedule/
│       │   ├── MainActivity.kt                   # 主 Activity，包含全部 UI 组件与页面
│       │   ├── Data.kt                           # Room 实体、DAO、Repository、ViewModel
│       │   ├── GlassUi.kt                        # 液态玻璃效果组件
│       │   ├── ScheduleLogic.kt                  # 课表解析、导入导出、通知调度
│       │   ├── EduImport.kt                      # 教务导入 WebView 适配
│       │   └── TodayCoursesWidgetProvider.kt     # 桌面小组件
│       └── res/
│           ├── drawable/                         # 图标与图片资源
│           ├── mipmap/                           # 启动图标
│           ├── values/                           # 字符串、颜色、主题
│           └── xml/                              # 小组件配置
├── gradle/
│   └── libs.versions.toml                        # 版本目录
├── build.gradle.kts                              # 根构建配置
├── settings.gradle.kts                           # 项目设置
├── gradle.properties                             # Gradle 属性
├── gradlew / gradlew.bat                         # Gradle Wrapper
└── make_icons.py                                 # 图标生成脚本
```

## 技术栈

| 类别 | 库 / 工具 |
|------|-----------|
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room |
| 序列化 | Kotlin Serialization |
| 玻璃效果 | [kyant/backdrop](https://github.com/kyant/backdrop) — 自定义 Backdrop 渲染管线 |
| 教务适配 | [shiguang_warehouse](https://github.com/xingheyuzhuan/shiguang_warehouse) — 学校适配资源子模块 |
| 构建 | Gradle KTS + KSP |
| 桌面小组件 | Glance |

## 构建

```bash
./gradlew assembleDebug
```

- `compileSdk`: 36
- `minSdk`: 26
- `targetSdk`: 36
- JDK: 17+

## 引用与修改说明

### kyant/backdrop（液态玻璃渲染）

- 使用 `Backdrop` 组件作为玻璃效果的渲染基础
- 扩展了 `drawBackdrop` 的 `effects`、`highlight`、`shadow`、`innerShadow`、`onDrawSurface` 参数实现自定义玻璃卡片
- `CourseGlassCard` 在此基础上增加了 `vibrancy()` 效果和动态模糊/透镜参数
- `GlassTokens` 封装了不同场景（Pill、Dialog、CourseCard）的玻璃参数预设

### shiguang_warehouse（教务适配）

- `assets/shiguang_warehouse-main/` 作为子模块引入学校适配资源
- `EduImport.kt` 通过 WebView 加载学校登录页，注入 JS 适配脚本抓取课表数据
- 支持通过 Custom Tabs 进行 CAS/OAuth 登录流程

### Jetpack Compose 动画

- 周视图甩尾动画：`Animatable` + `spring()` 驱动 `graphicsLayer.translationX`，按行计算滞后系数
- 课程卡片展开：`drawWithContent` + `clipPath` + `RoundRect` 实现从卡片形状到弹窗全尺寸的裁剪过渡
- 加号菜单展开：`updateTransition` + `keyframes` 控制尺寸/偏移/圆角的多属性同步变形
- 启动页圆形展开：`Path` + `PathFillType.EvenOdd` 实现从屏幕中心向外扩散的遮罩动画

## 特性

<!-- TODO: 填写功能特性 -->
