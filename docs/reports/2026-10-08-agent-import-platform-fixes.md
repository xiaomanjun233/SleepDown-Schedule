# Agent 导入、教务执行与厂商通知修复

## 基线与范围

先按要求将 Beta4 分支通过 [PR #66](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/66) 合入远端 `main`，本地任务分支从合并提交 `3e77f78434424f16cb1c7ce9301689f90a5fc26b` 继续。本轮修改保留在 `codex/agent-import-platform-fixes`，没有发布新版本或更新远端附件。

用户确认：冲突课程只调整显示顺序，保留所有课程、原始时间，不把后面的课程实际上课时间推迟。

## 超级岛：Nexio 对照及根因

通过 Codex GitHub plugin 读取 Nexio `master`（提交 `2daf4c387ecf298cc78df85004db88bb8d68fb7e`）的 [IslandNotificationHelper](https://github.com/HaoZai000/NexioSchedule/blob/2daf4c387ecf298cc78df85004db88bb8d68fb7e/app/src/main/java/com/haooz/chedule/reminder/IslandNotificationHelper.kt)、[ShizukuManager](https://github.com/HaoZai000/NexioSchedule/blob/2daf4c387ecf298cc78df85004db88bb8d68fb7e/app/src/main/java/com/haooz/chedule/shizuku/ShizukuManager.kt) 和其 privileged service。

SleepDown 原来的 Shizuku 分支要求 `getUidFirewallRule(9, xmsfUid) == 0` 才执行临时绕过。但 Android 的读取接口通常返回有效值 `ALLOW=1` / `DENY=2`：正常允许状态也会被误判，跳过绕过后只发送普通通知。依据为 AOSP 的 [BpfNetMapsUtils.getUidRule](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/dc3f885ebe8ddc75bd9cf2d567eef4d1ed433a09/android-35/android/net/BpfNetMapsUtils.java) 及 [BpfNetMaps](https://android.googlesource.com/platform/packages/modules/Connectivity/+/d43a4fdbfdc175a070b3688f7dbbfc52a33a7a88/service/src/com/android/server/BpfNetMaps.java)。这是代码层面的确定错误；没有在本轮连接到小米设备复现。

修复内容：

- 接受有效的 0、1、2；未知值仍不改防火墙。写入前保存原规则与链开关，发送后恢复读到的原值，并保留原有异常恢复检查点与恢复闹钟。绕过窗口沿用与 Nexio 相同的 100 ms。
- Shizuku / Root 发送方法返回是否实际进入绕过路径；失败不再向测试入口谎报成功。原普通通知发送仍只调用一次。
- 测试使用独立的交替通知 ID，不覆盖课程通知；课程状态刷新、静音处理不会顺手删除仍有效的测试通知。
- 参数对齐 Nexio：课前 `enableFloat=true`，课中 `enableFloat=false` 与 `islandFirstFloat=true`。有勿扰访问权时为专用渠道设置绕过勿扰。
- 测试前分别判断通知权限/渠道、Shizuku 或 Root、焦点通知权限。系统未返回焦点权限字段时保留“未知”，不误判为已关闭。

不把“notify 已调用”当作“超级岛已在系统界面出现”。HyperOS 版本、焦点权限、渠道设置和后台投递仍需真机验收。

## AI 导入与会话

- 初次材料提取继续使用现有文件预处理、IMPORT_SCHEDULE 和本地校验。后续修改进入共享 `DayAgentService` 工具循环，复用查询、操作提案、周次范围与预览校验；不再调用独立的整表 PATCH 提示词。
- 工作区为完整草稿。局部操作在原草稿上应用，保留未涉及的课程与周次。“仅本周”还有本地范围检查，拒绝扩大到全学期。模型只提出草稿修改，不直接写数据库或修改助手记忆。
- 需要补充定位信息时显示提问，不生成空的成功交付物；仍经 Preview → 用户确认 → 数据库。
- 同一会话保留用户补充、停止状态、每轮回答及独立交付物。历史上下文 schema 2 兼容读取 schema 1，避免旧格式重复显示最后一轮。
- Markdown 用已有 Agent 渲染器。模型返回的推理流放在有高度上限的滚动框；无推理流时在会话内更新阶段摘要。完整输出的课程对象先成为待校验卡片，未闭合的 JSON 不进入卡片或持久化。
- 进度 Activity 收到新 Intent 时更新可观察的 taskId，并重建该任务页面状态。手动文件/口令确认取消会清除等待确认、消费回调一次并返回导入页。
- 本地历史草稿保留逐节铃声、原始时间快照和时间对齐模式；独立恢复入口允许已验证的来源字段，普通外部 AI JSON 入口仍保留原规则。

## 教务导入与冲突

参考 [拾光 WebView 实现](https://github.com/ShiGuangSchedule/shiguangschedule/blob/main/shared/src/androidMain/kotlin/com/xingheyuzhuan/shiguangschedule/ui/schoolselection/web/WebView.android.kt)，保留 adapter → bridge → SleepDown UI 链路。

- 每次在新函数作用域中直接 eval，保留最后一个异步表达式，以处理 JLU 顶层 `const` 第二次执行冲突和异步失败。
- 点击后立即显示加载并防连点；页面跳转/渲染器重建会结束运行状态。下载完成后检查页面是否仍是原文档。
- 异常统一向用户展示；桥接错误字符串转为 Error。保存类桥接等待 30 秒、用户交互等待 5 分钟；脚本局部 fetch 包含响应体传输的 30 秒截止时间。
- 同源 iframe 的 AI 适配器执行复用主页面桥接；本地 JLU 适配器可识别同源 iframe 内地址。跨域 iframe 和具体学生门户布局尚无现场证据，不宣称全部兼容。
- 每次执行重新下载远端 JS，避免目录索引不变时永远复用旧脚本；下载失败的既有 bundled fallback 保留。本地 JLU 修改不等于上游远端文件已更新。
- 教务重叠作息形成合法显示网格，课程保留源节次、真实起止时间及原始快照。预览显示冲突说明；周视图同一冲突组按真实开始时间排序，其他课程可切换查看。

## 默认项、Widget、ColorOS 与玻璃

- 深色模式跟随系统原本已经默认开启；并列导航改为默认开启。Beta 更新执行一次默认开启迁移，包括原先关闭的用户，迁移后仍可自行关闭。
- 添加 Widget 不再等待预览生成后才响应；首次点击直接调用系统 `requestPinAppWidget`，有系统确认结果回调和拒绝/不支持提示。没有虚构通用的桌面运行时权限 API。
- 所有 Widget 渲染入口共享下一边界刷新安排，补充解锁和配置变化刷新；有精确闹钟权限时使用精确非唤醒闹钟，否则保留系统允许的调度。修复 22:00 边界被推到次日的问题。
- ColorOS 主应用导出与独立组件缓存读取都过滤已结束课程；独立组件在下课、预览结束和缓存到期安排一次刷新通知，最后一节结束后返回空课程数组。Doze 下的系统调度时机仍需设备验证。
- ColorOS 组件是独立 APK；主应用更新不能替代组件覆盖更新。本轮未递增发布版本号或上传附件。
- 对照 Nexio CourseCard，当前共享采样已经为 0.48，并采用节点内采样。本次修正课程卡绘制回调在无关重组时反复替换的问题，保留材质、折射、高光、轮廓、阴影与 Morph 分支。没有本轮实机帧时间或 GPU 性能数据。

## 验证

只使用一个 worker 串行构建，没有同时启动多个 Gradle 构建。

- 定向单元测试通过：主应用 18 个测试类、117 项；ColorOS 独立组件 3 个测试类、8 项，均为 0 failure / 0 error。覆盖局部周次修改、不合法扩大范围、Agent 工具协议、取消确认、历史交付物/铃声恢复、冲突显示顺序、超级岛参数、通知 ID 选择、Widget 刷新 action 和过期课程缓存。
- 主应用使用临时 init script 只编译本轮相关测试源，再执行 `:app:testGithubDebugUnitTest`；组件执行 `:coloros-wakeup-proxy:testDebugUnitTest`。这不是全仓测试通过声明。
- 同一串行调用完成 `:app:assembleGithubRelease :coloros-wakeup-proxy:assembleRelease`，整体耗时 8 分 43 秒，257 个任务；主应用包含 R8、资源压缩、lintVital、打包和签名校验。没有跳过 Release 资源压缩。

| 产物 | 版本 / code | 字节数 | SHA-256 |
| --- | --- | --- | --- |
| `app/build/outputs/apk/github/release/app-github-release.apk` | `1.2.7_beta4` / 34 | 7,042,407 | `039445D4D4C434978D95B6B7BA4A5C42227700439C5FA3359AB106C3EB77924B` |
| `coloros-wakeup-proxy/build/outputs/apk/release/coloros-wakeup-proxy-release.apk` | `6.0.18` / 258 | 3,577,334 | `4EB434BE2E442A7DA2DC0B867AC29938A18B25A5AFF1A57BBF22445A9BC2A71C` |

这些是本地开发候选包，保留基线版本号。未来正式分发组件时需递增组件版本号；当前组件安装器不会把相同版本识别为在线更新。

两个 APK 均通过 SDK 36.1.0 `apksigner verify`，V2 签名有效，证书 SHA-256 一致。

- Node VM 脚本验证已通过：同页两次顶层声明、同源 iframe 转发桥接、异步/字符串错误、Response 元数据、桥接拒绝与超时；只使用合成适配器，无学校账号或网络请求。
- ADB 使用 SDK 5038 通道；本轮 `devices -l` 无在线设备。未安装 APK、未启动应用，未做小米/ColorOS/桌面启动器现场验收。
- 真机待验：重复点击超级岛测试及课前→课中切换；AI A 任务页→历史→B→通知跳转；文件与口令取消重试；JLU 同页再次导入；ColorOS 最后一课结束清除；各桌面 Widget 在锁屏/解锁/跨日后的刷新；首页玻璃滚动帧时间与视觉。
