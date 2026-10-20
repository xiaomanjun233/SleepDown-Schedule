# 架构收口与 AI 账号、模型列表修复

此页保留 2026-10-20 的实现与验证。此前剩余五项反向依赖已继续处理，见[2026-10-21 状态与转场续作](2026-10-21-schedule-state-and-transition-boundaries.md)；当前状态与 CI 以 #79 最新 head 为准。

本轮接续[首批审查](2026-10-20-architecture-review.md)和[课程显示、自动静音续作](2026-10-20-architecture-followup-and-course-fixes.md)。用户要求“一口气全改完”，并追加 ChatGPT 工具/数据/图片与官方模型列表问题。继续使用从 main `9b6acbb2` 派生的 `codex/architecture-boundaries` 和 [PR #79](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/79)，不合并、不改版本、不发布。

## 已实施的职责调整

| 提交 | 原问题 | 本次边界 |
| --- | --- | --- |
| `30b6d3c0` | model 反向引用 domain 的课程时间和调休值 | 共享值进入 `model/CourseArrangement.kt`、`model/ScheduleAdjustment.kt`；domain 保留规则和源码别名，调休序列化名称显式保留 |
| `dacd6459` | 首页个性化草稿、已提交但未回读的配置、滑条预览分散在根 Composable | `PersonalizationSession` 属于当前 Composition；关闭丢弃草稿但保留待回读提交，切课表清理旧会话，原动画和 effect 时机保留 |
| `6290d180` | Repository 同时解释 AI 计划和进行数据库写入 | `domain/agent/AgentPlanPreparation` 纯准备替换片段与新增课程；Repository 在同一 Room 事务读取、准备、写入、合并和回读，未拆事务 |
| `6de4e216` | 公共远程配置同时选择和激活 AI 供应商，功能反向引用 app | `core/remoteconfig/SleepDownRemoteConfig` 负责公共 bootstrap；`feature/importing/ManagedAiRemoteConfig` 负责托管 AI，Application 装配原加载/更新时序；清除确认无用的反向 import |
| `3f13196e` | 教务桥回调可以晚于 WebView/页面退出，旧任务可误停新任务 | bridge 按任务 generation 认领结果与 JS 回调，`close` 清空队列和引用；现有 Activity、隐藏浏览器、自动刷新流程在各自释放点关闭 bridge。未改拾光 adapter 或协议 |
| `823826d4` | 通知调度文件混入系统通知呈现 | `LiveUpdateNotificationRenderer` 接管原 builder、图标、操作与系统能力判断；Scheduler 保留兼容门面、闹钟、勿扰和 Service 调度 |
| `1f1b2630` | glass 读取首页功能的共享预览/可读性上下文，转场壳直接装配设置页 | 共享材质预览进入 `core/ui/material`，壁纸采样进入 `core/wallpaper`，冻结上下文进入 `core/ui/text`；详情 Morph 接收内容槽，由 app 装配原设置内容与保存握手。Dock 只接收实际所需的首页选中状态 |
| `bc6b0e38` | Agent、AI 导入为了备份快照反向依赖备份功能 | 四个快照、稳定 ID 与历史保留选项归入 `model/backup`；备份入口保留源码别名，四个 serializer 的旧名称、字段顺序和默认值保留 |

没有新增 Gradle 模块、接口框架、独立 CoroutineScope 或新的全局调度器。根 Composable 继续拥有跨叠层装配，局部手势/动画状态留在原使用者。#78 的采样、光照、Modifier 顺序、运动参数和材质函数体未重写；共享采样和预览迁移前后函数体已对照。

## ChatGPT 工具与图片

用户明确反馈“没有报错，模型说无法调用工具”，没有失败请求或 HTTP 诊断可用于证明服务端拒绝原因。`a004edb6` 修复了两个本地可确认的问题：

- 账号模型目录缺少图片字段时，原来被直接视为不支持。现在明确区分未知、支持、不支持；保留旧缓存布尔值，未知模型允许提交图片，由官方接口校验实际能力。仍使用 Responses 内联图片，不改用 Files 上传接口。
- 请求在保留原任务指令的同时列出本轮实际提供的 `sleepdown` 命名空间函数。首轮模型无调用且声称不能使用本地工具时，在同一受限任务循环内纠正一次；再次拒绝会明确提示未执行。没有强制调用无关查询，没有绕过预演、用户确认或数据库校验。

回归覆盖真实 Agent schema、内联图片、命名空间函数解析、工具执行与结果回传标识，以及拒绝反馈边界；未使用真实用户账号发出调用，不能据此宣称所有模型的自然语言拒绝均已实测消失。

协议依据：[OpenAI 套餐调用限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)、[模型与推理](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)。保持 `store=false`、流式传输、完整 output/reasoning 重放和官方 Responses 路径。

## 官方模型列表与自定义选择

`cb29c47f` 使用供应商官方账户 API，而非依赖网页 HTML：DeepSeek `GET https://api.deepseek.com/models`，小米 MiMo `GET https://api.xiaomimimo.com/v1/models`。设置模型弹出时刷新，也可手动刷新；运行时模型选择器使用同一缓存。使用该供应商已填写的 API Key，缓存只保存模型元数据；没有 key 时提示填写，失败或空列表保留原列表，不切换当前选中模型。

解析保留官方顺序并去重，按已提供的模态过滤非文本模型，MiMo 排除语音专用条目。支持取消，连接/读取有超时，不重定向、不重试。只对两个预置官方地址启用发现；用户自定义端点继续使用手填列表。新安装 MiMo 默认值更新为官方已列出的 `mimo-v2.6-pro`，已有选择不覆盖。

模型 Popup 先按实际 model ID 匹配具体行；编辑自定义值的状态不会再让已填好的模型错误勾选到“自定义”入口。相关解析、缓存和选中回归已接入 CI。

官方协议：[MiMo List models](https://mimo.mi.com/docs/en-US/api/model/list-models)、[DeepSeek List models](https://api-docs.deepseek.com/api/list-models/)。没有使用用户凭据进行在线供应商验证。

## 验证与兼容

以下每批均同时执行 `compileGithubReleaseKotlin` 和所列定向单测，全部通过，0 失败/错误/跳过；不同批次有重叠，不将数量相加当作独立用例总数。

| 验证批次 | 实际结果 |
| --- | --- |
| 共享值、个性化会话、Agent 计划准备 | 11 类 188 项，4m56s；含旧调休 JSON、Room 迁移覆盖、事务与偏好执行 |
| 公共配置、教务生命周期、通知呈现 | 15 类 72 项，4m29s；含过期 bridge 回调与通知恢复 |
| ChatGPT 与官方模型发现 | 15 类 121 项，4m24s；含三态图片缓存、真实工具 wire、备份设置兼容 |
| 材质上下文与转场内容槽 | 13 类 110 项，4m42s；含可读性、共享课程绘制、Widget、转场状态与几何 |
| 最终运行时代码 `bc6b0e38` | 13 类 129 项，4m37s；含备份编码、Room 恢复、并发恢复、历史、Agent 偏好与旧快照默认值 |

使用项目配置的 `miuix-local`，单 worker、临时 4 GiB JVM 堆，没有改项目构建参数。早期三次编译分别发现导入歧义/漏导入，已修正后完成上述成功运行；失败运行不计为通过。

9 项架构检查器回归通过，扫描 557 个生产 Kotlin/Java 文件；相对当前 main，已登记违规文件依赖 **51→5**，声明循环边 **110→65**。基线只删减，未新增豁免。检查包含各生产 source set、显式 import/全限定引用，通配 import 保守建边；不解析根兼容门面的传递调用、字符串模板表达式、反射或外部模块，不能表述为“消除了所有循环”。最终完整云端 CI 以 #79 的最终 head 为准，执行结果及链接保存在 PR。

版本、Manifest、Room schema/迁移、DAO、备份版本、SleepDown 口令、ICS、已发布组件和 Intent wire 值未改变。Repository 修改保留单一事务；共享备份类型显式保留旧 serializer 名称。模型目录缓存不进入备份，旧备份配置和账号缓存仍可读取。此前追加的自定义时间卡片和自动静音修复保留，详见续作报告。

5038 ADB 无连接设备，未安装或启动应用，未进行实机视觉、OEM 音量恢复、ChatGPT 真实账号或供应商凭据验证；本地单测与云端构建不能替代这些验收。没有发布 APK。

## 仍值得后续整理

剩余 5 项反向引用已逐文件列在架构基线：三个功能 Activity/Host 仍装配应用级 ViewModel，两个成熟 transition adapter 仍使用首页专属几何。它们涉及跨 Activity 状态共享与成熟 Morph 路线，应结合相应功能的实机场景逐步调整，不能通过把文件搬到 app、改统计口径或扩大别名来隐藏。

其余 65 条声明循环边包含功能间真实调用和通配声明；优先围绕具体新需求收敛 Agent/导入交接、设置/课表保存装配，而不是一轮替换所有页面控制器。`ScheduleAppUi.kt`、Repository、导入 transport 和 Widget renderer 仍可能较长，但本轮已迁出能明确独立所有权与验证收益的职责，没有按行数拆分稳定代码。
