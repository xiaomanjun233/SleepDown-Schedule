# 凝光可选模式与架构整理

## 范围和起点

用户要求将独立 ColorOS 目录的凝光视效作为 ColorOS 17 下的可选模式，保留液态玻璃；梳理项目结构、厂商边界、大文件、无效代码和临时产物。纯结构改动不得改变既有功能与视效，每批大改动独立提交。

- 主线起点：`origin/main` 的 `9662d103`。
- 先完成已有工作：`1150d141` 保存普通版与独立 ColorOS 包的备份互认，36 项定向测试通过。此前的 ChatGPT 与备份导出修复保留在其祖先提交。
- 工作分支：`codex/coloros-visual-architecture`，从上述已提交基线开始。原备份分支保留，可独立回退。
- 凝光来源：`D:/Android studio/sleepdown for ColorOS`，分支 `codex/coloros-menu-rescue`，已提交基线 `af843d3`，另有待整合的工作树改动。迁入前核对实际文件；不改动来源工作树、不迁入其独立 applicationId、ADB 策略、分析材料或临时产物。
- `.codex-remote-attachments/`、`edu-adapters/` 以及其他工作树中的未完成工作需要保留。私有后端、签名和用户数据不在整理范围。

## Google 官方资料与本项目的适用方式

核对时间：2026-10-13。

| 官方资料 | 适用方式 |
| --- | --- |
| [Android 架构建议](https://developer.android.com/topic/architecture/recommendations) | UI 通过状态持有者和数据入口工作，使用单向数据流；按生命周期收集 Flow。保留既有 Manifest 入口和转场协议，不以本轮整理强行替换所有 Activity。 |
| [Compose 状态提升](https://developer.android.com/develop/ui/compose/state-hoisting) | 业务状态由 ViewModel/数据层负责；动画、滚动和临时界面状态放在使用者附近。不能把所有局部状态都塞进一个 ViewModel。 |
| [模块化模式](https://developer.android.com/topic/modularization/patterns) | 按职责和依赖划界，公共设计系统定义渲染协议，厂商实现由装配层接入。先消除反向依赖，再决定是否值得增加 Gradle 模块。 |
| [Compose 性能建议](https://developer.android.com/develop/ui/compose/performance/bestpractices) | 根据实际热点延后状态读取、避免重复计算，保留稳定列表标识。文件拆分本身不证明帧率或启动速度提升。 |

Google 没有适合所有应用的单一“最佳性能架构”。本项目的 CPU、GPU、原生 View 与 Compose 混合绘制、壁纸采样、Morph 和生命周期需要分别验证。代码可维护性结论与实机性能结论分开记录。

## 初始代码核对

- `ScheduleAppUi.kt` 10,328 行：混有首页装配、设置页面、多个 Activity host、个性化编辑、公共控件和 WebView 下载。
- `WeekScheduleUi.kt` 4,163 行，`DayAgentUi.kt` 3,482 行，`HomeAnchoredMorphOverlay.kt` 2,828 行，`HomeScheduleUi.kt` 2,405 行：按状态、纯计算、组件和页面职责逐项评估；不按固定行数切碎同一手势状态机。
- 数据层 `ScheduleRepository` 依赖 `feature.agent` 的操作计划；`core.identity` 依赖备份功能的图标偏好 DTO；公共设置控件从 `app.ui` 获取控件。这些依赖与文档约定方向不一致。
- 凝光来源已提供一组公共原生渲染接口，但首页、课表选择与学校搜索仍直接依赖 ColorOS 实现。可选模式需要统一门控和可卸载的资源归属。
- 来源修改了公共主题色和部分非原生后备路径，不能直接把目录覆盖到主线。默认液态玻璃的色值、几何、手势与保存语义需要逐项保留。

## 交付记录

后续每批记录实际提交、修改边界、检查结果及尚未完成的设备验收。未执行的实机测试不记为通过。

### 可选模式接入

提交：`3d7738b1`。

- 默认模式保留液态玻璃；凝光选择仅在 GitHub 渠道、ColorOS 17 及以上并且系统 COUI 能力探测成功时开放。保存偏好与当前有效模式分离，未知偏好或缺失能力均回到液态玻璃。
- 统一提供按钮、滑块、开关、课程卡片、材质表面、菜单和弹窗渲染入口；切换会撤销原生 provider，使 Compose 释放其 AndroidView/窗口资源。
- 修正来源分支影响普通模式的主题色、弹窗操作顺序、快速设置密度/字体和后备按钮额外触觉反馈。原生 QuickSheet 保留退出内容及异步保存边界。
- `compileGithubReleaseKotlin` 通过；19 项模式、Picker/QuickSheet 生命周期、首页切换与转场目录测试通过。`assembleGithubRelease` 含 R8、资源压缩、lintVital 和签名通过（3m52s）；未安装，尚未进行两种模式的实机视觉验收。
- 干净依赖重放发现旧 Miuix 第五份增量补丁无法应用；改为相对官方 `v0.9.3` 的单一完整补丁。重新解包官方基线并应用后，20 个运行时源码文件与本地依赖一致（归一化 LF/CRLF）。不包含依赖目录的 IDE/CI 清理。

### 厂商与材质边界

提交：`9ea90043`。

- 原生实现及对应测试迁到 `platform/coloros`；功能页面通过 `HomeMaterialControls` 调用 Dock、图标按钮、搜索、命名与课程菜单。
- 首页 Dock 布局回到 `feature/home`；共享菜单模型、窗口边距计算和父级手势排除协议移到 `core/ui`。厂商实现不再依赖应用装配或助手功能。
- 模式切换测试验证退出原生 provider 时释放消费者、移除能力，再切回时创建新的消费者。Dock 重组边界与助手手势相关测试和 Release Kotlin 编译通过。
- 组件 FQCN、课程 provider authority 和转场 wire id 保持不变。

### 应用装配与共享控件

提交：`edf7346e`。

- `ScheduleAppUi.kt` 从约 10,500 行收敛到约 4,400 行，设置宿主、详情快照、顶栏和导航各自归位。Manifest 入口及 Host 的包名不变。
- 个性化界面按状态合并、滑条、课程配色、壁纸取色和面板拆分；设置根页、详情页、更新提示及关于页面进入对应功能目录。
- 公共 Slider、Toggle、Toolbar、设置分组/行/输入框/时间选择器归入 `core/ui`；Web 下载、Custom Tabs 与 WebView 清理从 UI 聚合文件分离。
- 对 213 个原声明做移动前后函数体核对，除文件可见性、显式接口类型及等价的时间解析入口外一致；没有改动 Modifier 顺序、动画参数和保存流程。Release Kotlin 编译通过。
- 导入明确指向实际职责；共享颜色规则移入 `glass/ui`，共享时间格式移入 `domain/schedule`，避免公共控件反向依赖页面。
- 定向测试发现旧断言要求紧凑滑条仍显示完整三行时间轴，与已发布的紧凑模式（空间不足时只显示节次编号）不符。核对 `49d83172` 以来的实现后，保留渲染行为，更新断言保护编号的可读高度和时间行收起条件。
- 24 项个性化状态、时间轴几何、Dock/IME、下载权限、Slider 与 QuickSheet 定向测试全部通过。

### 共享模型与业务规则

提交：`a1a3c2f4`。

- 助手计划的预览、验证与课程片段计算移到 `domain/agent`，操作 DTO 移到 `model/agent`。数据库事务不再依赖助手页面包。
- 图标偏好快照归入共享模型，备份中的 JSON 字段和默认值不变。删除没有实现的启动性能接口及 WebView 公共清理对导入协议的反向依赖；协议卸载仍由既有调用者负责。
- Release Kotlin 编译与 40 项助手计划、图标偏好、备份编解码测试通过。测试包同步跟随生产代码。
- 新增 `scripts/check_architecture.py`，PR 检查共享层、厂商层与页面之间的禁止引用。
- 边界检查还发现包迁移误改了旧 ColorOS 备份身份字符串；`305fdd42` 恢复已发布包名，并改用真实包名字面量保护跨版本迁移，避免测试和实现同时引用错误常量。

### 页面与组件职责

提交：`60b909b7`。

- 周课表拆出标题、课程布局、卡片文字、卡片绘制与编辑会话；日课表拆出壁纸、可读性、日期标题、课程分组、卡片和编辑确认。
- 助手拆出卡片、呈现缓存、窗口几何、对话生命周期、输入控件与消息内容。Widget 按 Android Provider 入口和各自 renderer 分开。
- 对原文件 207 个声明逐个核对，函数体保持一致，仅调整跨文件可见性。Release Kotlin 编译通过。
- 98 项编辑运动、窗口几何、桌面组件、助手计划和旧包兼容测试通过。主线已有的宽窗口布局、圆角增强和直接返回轨迹对应旧测试未同步；核对 `9662d103` 后保留实现，删除退役时长约束并更新行为断言。

### 下载边界修复

提交：`c9554794`。

- HTTP 响应中的文件名先解码、提取末级名称和限制 UTF-8 长度；旧 Android 使用独占创建，避免目录穿越及覆盖同名文件。写入失败只删除本次创建的半成品。
- Android 10 及以上通过 MediaStore 的 `IS_PENDING` 发布完整文件，创建或写入失败时删除本次条目；连接在成功与失败路径均释放。
- 显式处理有限次数重定向，按每个目标读取 Cookie，跨来源 Referer 仅发送来源地址；拒绝 HTTPS 降级。data URL 支持 Base64 与百分号编码，并在后台解码、写入。
- Release Kotlin 编译与 15 项下载命名、失败清理、重定向和解析测试通过。文件名截断随后改为线性扫描，最终回归再次覆盖。
- API 依据：[共享存储及待发布文件](https://developer.android.com/training/data-storage/shared/media)、[HttpURLConnection](https://developer.android.com/reference/java/net/HttpURLConnection)。未替换既有下载入口或用户确认流程。

### 剩余职责整理

提交：`51ff49e9`。

- 课程编辑分成草稿规则、页面会话、表单、选择器和标题；通用设置、AI 导入设置与助手设置分别对应文件。
- 玻璃层按配色算法、公共表面、光照与课程卡片绘制组织；首页 Morph 按宿主、状态、纯几何、菜单与个性化动画组织。原声明 166 个函数体核对一致，Release Kotlin 编译通过。
- 相关测试目录跟随生产职责，PR 纳入模式切换、共享协议、下载及新目录测试。架构树同步在 `PROJECT_STRUCTURE.md`。
- 第一次完整回归共 1,325 项，1,314 通过、11 失败：8 项为备份并发测试中的 Windows SQLite 临时路径问题，2 项为已有规则的旧断言，1 项为课程结束判断边界。修复单独提交，不混入职责迁移。

### 完整回归发现的问题

- `97794e61`：当课程最后一节缺少时间时，日课表曾把较早的已知节次当作课程结束时间。现在保持未结束状态；完整课程时间和自定义时间的既有规则不变。5 项日课表状态测试通过。
- `b74e5e4e`：缩短 Robolectric 的数据库临时路径，解决 Windows SQLite 路径限制后，进一步发现恢复日志更新使用 `File.renameTo` 无法跨平台替换已有文件。改用同目录 `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`，保留写入同步、原子替换和失败暴露，未添加非原子回退。16 项恢复并发、协调器和 Room 恢复测试通过。
- `145a3b3c`：按 `9662d103` 已有配色阈值与部分自定义铃声规则修正过时断言；不通过修改配色或课程时间规则迎合旧测试。7 项配色及 3 项自定义铃声测试通过。
- `8e3d3141`：整理本轮涉及文件的 import 顺序、重复及无用引用，并统一局部排版。105 个 Kotlin 文件逐个比对，仅 import 和空白变化，声明体不变。

### 临时产物清理

逐个解析路径并检查重解析点，清理旧渠道 APK 输出、过期构建日志及已经完成的依赖验证副本，共 115 个目标、1,562 个文件、124,280,430 字节（约 119 MiB）。保留当前候选、用户素材、既有工作树和未完成任务；未读取或清理私有后端。

随后删除本轮已完成的迁移脚本、补丁中间件、验证副本及旧日志，共 59 个目标、416 个文件、10,276,178 字节；合计约 128 MiB。最终验证日志和本轮 APK 保留，临时文件不进入 Git。

### 最终验证（2026-10-14）

- `testGithubDebugUnitTest`：196 个测试类、1,326 项测试全部通过，0 失败、0 错误、0 跳过。
- `assembleGithubRelease`：通过，包含 R8、资源压缩、lintVital、打包与正式签名。
- `compileStoreReleaseKotlin`：通过；核对生成的渠道配置，GitHub 实验功能为 `true`，商店为 `false`。
- 三项 Gradle 任务顺序执行，4 GiB 堆、单 worker，共 8m31s。保留现有 API 弃用及部分空值诊断警告，未以批量修改兼容路线的方式消除警告。
- `scripts/check_architecture.py`：409 个受约束 Kotlin 文件通过；338 条变更路径没有禁入文件。Room schema 和连续迁移未变，完整 PR diff 空白检查通过。
- APK：`app/build/outputs/apk/github/release/app-github-release.apk`，包名 `com.xiaomanjun.sleepdownschedule`，`1.2.7_beta5` / versionCode `34`，7,475,450 字节；`apksigner verify --verbose` 通过（v2 签名）。这是待实测候选，未更改发布版本。
- APK SHA-256：`7E4C15E31FA290D15CB434FE15C2BAE427BEDD1C7198700E20B1236C4CD33103`。
- 最终日志：本地忽略目录 `tmp/architecture-final-validation-20261014.log`。未运行设备基准测试，不宣称已经证明视觉一致或帧率提升。

### 按用户要求安装（2026-10-14）

通过项目 SDK 的 5038 通道，确认唯一在线设备 PLJ110，Android 17、ColorOS `V17.0.0`。使用 `install -r` 覆盖安装上述签名 APK，返回 `Success`，保留应用数据；安装后再次读取包版本与更新时间。未自动启动应用，视觉与交互仍待用户实测。

### 实测反馈：视效入口上移

用户要求将模式选择放到上一级菜单，并在凝光模式下屏蔽玻璃调整页。`f5417440` 将选择项移到“通用设置 → 外观”；凝光生效时隐藏调整入口，页面内部也在创建预览和滑条前阻断。切回液态玻璃时继续使用原来的模糊参数，超级性能模式仍保留既有玻璃限制。

此次仅调整页面入口与可见性，重新运行 `assembleGithubRelease`，含 R8、资源压缩、lintVital 与签名，4m08s 通过；架构检查和 diff 检查通过，未重复运行整套单元测试。新 APK 签名校验通过，7,475,450 字节，SHA-256 为 `4880A8993E292118093C05D48E66A99D1795F740C130CA4CA9970DB4FFBA75E9`。在同一 PLJ110 上再次覆盖安装返回 `Success`，更新时间为 2026-10-14 01:08:30，未自动启动。

### 交付边界

用户选择先创建 GitHub PR，实机测试后再合入，并在最终构建后明确要求安装。此分支不合并 `main`，不发布 Release。设备验收应覆盖两种材质切换、明暗主题、首页日/周视图、编辑保存、弹层退出及桌面组件；帧率与启动性能仍需同设备、同场景测量。

草稿 PR：[#74](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/74)，目标为 `main`。远端 CI 检查接口连接失败，未将其状态记为通过；上述构建和测试结果均来自本地执行。
