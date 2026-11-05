# AI 胶囊交互与教务工作区入口修正

后续位置更正：用户明确手机统计胶囊放在输入框上方，平板宽窗口维持下方。会话页复用外层既有 840dp 宽窗口判断选择顺序，同一时间只显示一条；仍由输入停靠区实际测量高度避让对话与键盘。下文 `229798ca` 记录的是更正前交付，修正版结果见末尾。

用户要求信息只保留一条图标胶囊、启用柔性反馈；补充消息发送后成为独立候选，内部提供编辑／删除／引导三个操作，附件居中并启用柔性反馈。随后用户反馈 AI 教务入口仍直接发送页面文字，模型无法调查接口。

## 修改与原因

- 信息胶囊改为同时显示时钟、输出速度、缓存命中率、上下文占用环，不再按十秒轮播文字；未知值显示“—”，输入／输出 token 数仍可通过可访问性信息读取。保留容器伸缩及数字模糊叠化。
- 候选胶囊直接提供铅笔、删除和向上引导图标。发送中的新消息默认进入队列，点击引导才将同一消息转为 steering；编辑保留 id，不重新提交副本。移除发送前的“插嘴／排队”两个选项。停止后可以引导指定候选继续，今日助手在同一协调器锁内交接候选，避免重复领取。
- 附件独立居中；候选和附件两行都在输入框材质之外，实际占位同步到对话滚动区域。三个类型的胶囊通过 `GlassSurface` 的显式参数接入原生柔性反馈；其他调用默认行为不变，非原生材质使用既有按压反馈。
- 根因是教务工具虽已存在，但入口仍抓取 DOM 全文，沿“确认发送文本”和提取课表提示直接交给模型。现改为少量脱敏入口信息，确认按钮改成“开始检查教务”，由模型主动读取当前网页、接口线索和适配。
- 教务工作区的专用提示明确调查期间可以多步往返，不要求首步提交课表。它不暴露普通材料读取捷径。新增 `EduImportEvidence`：页面读取、真实接口／适配／脚本执行后才能提交；仅凭入口提交会收到工具配对反馈继续调查。模型核实没有结构化来源时，可说明 `PAGE_FALLBACK` 依据再读页面，不写死厂商或接口顺序。识屏仍是用户可选路线，本机已有适配仍优先直接抓取。

主要入口：`AgentInputCapsules.kt`、`AiImportStatusCapsule.kt`、`AgentMailbox.kt`、`DayAgentBackground.kt`、`AiImportTaskManager.kt`、`GlassSurfaces.kt`、`EduImportBrowserUi.kt`、`AiTaskWorkspace.kt`、`AiEduWorkspace.kt`、`EduImportEvidence.kt` 与 `AiImportSummaries.kt`。

## 继续追加的多选与平板布局

- 图片和文件均改用系统多选，普通助手到手动导入的回调完整传递 URI 列表。普通助手保留原始像素并使用对应 MIME，修正原来把 PNG 等原字节标成 JPEG 的问题，细节工具继续从原图裁切。
- `AiImportBatch` 先本地整理每份文件，原文件工作区按 `file` 编号查询，初始只附有预算的概览，后续可逐文件／页／区域核对。批次 20 文件／40 MiB，单文件 20 MiB；超限提示保留现有输入，不静默截断选择。
- 多图跟随候选编辑、引导、Chat／Responses 输入与普通助手历史。历史按连续 marker 保存全部图片；清理、V1 导出、ImportPlan 及恢复同时改为遍历完整数组，兼容旧单图片消息。
- `AiImportAdaptivePage` 在宽窗口提供 260dp 常驻历史侧栏，右侧使用同一个会话组件，正文与输入最大 840dp 居中。选中历史才加载完整材料，可切回当前任务。统计胶囊放在输入框下方，实际高度计入滚动留白和“回到最新”的位置。
- 用户随后明确要求 ADB 安装。已检查 5038、5037、USB 和 mDNS：SDK 5038 无设备，原 OPPO PLJ110（Android 17／SDK 37，序列号 `3B15AE023YL00000`）在 OPPO Connect 的 5037 USB 服务；不重启或抢占其他服务。将用 SDK 客户端显式指定 5037 与该设备覆盖安装。

## 验证状态

前期 Release Kotlin 与 8 类 41 项回归、正式签名包曾通过，但用户随后追加多选与平板布局，不能把早期包当作当前交付。本次定向验证日志 `tmp/ai-multi-adaptive-tests-final-20261102.log`，完成结果与安装／上传身份在交付时补记。首次编译发现批量 canView 递归类型推断和新布局缺少 ImportDraft import，已修正后重跑。当前架构检查 624 生产文件、0 违规依赖，41 条既有环边；diff 检查通过。

最终 13 类 60 项定向测试通过，0 失败／错误／跳过，3m36s，日志 `tmp/ai-multi-adaptive-tests-complete-20261102.log`。覆盖完整多图 marker 与引导候选、多文件预览后的原文读取和第二个文件的原页裁切、多附件备份编码／计划／恢复、教务入口调查、Chat／Responses 材料循环、ChatGPT 本地工具与手机对话预览。新增宽窗口 Compose 场景实际点击历史后返回正在运行的会话，确认 Session 未切换／停止，并读取语义边界验证统计位于输入框下方。

中间失败已区分：长期 JVM 原生内存不足后改用 2560m／4 核／单 worker 的独立进程；新增批量测试的 JUnit 非 void 签名已修正。正式构建独立日志 `tmp/ai-multi-adaptive-release-final-20261102.log`，R8 单 worker，未关闭混淆、资源压缩、lintVital 或金标。测试后仅补了普通助手连续追加图片的累计 40 MiB 检查，防止分多次选择绕过组上限；由最终 Release 编译覆盖。

## 交付与安装

完整签名 `assembleGithubRelease` 5m30s 通过（159 个任务），V2 签名有效，证书 SHA-256 为既有 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。包名 `com.xiaomanjun.sleepdownschedule`，`1.2.8_beta2 / 36`，7,844,089 字节；SHA-256 `229798ca58614a057c0db830f85bdcc3018afdc7539e0b8a905b19d93ef857bd`。候选保全于 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-multi-tablet-20261102-229798ca.apk`，同名 `.json` 保存验证与交付元数据。

用户要求 ADB 安装后，实际以 `D:\Android studio\AndroidSDK\platform-tools\adb.exe -P 5037 -s 3B15AE023YL00000 install -r` 经 OPPO Connect 5037 USB 服务覆盖原 PLJ110（Android 17／SDK 37），返回 Success。安装后回读版本，并对 `/data/app/.../base.apk` 执行 SHA-256，比对与候选完全一致。未清数据、重启其他 ADB 服务或主动启动应用。

通过 Google Drive 插件上传到已核实的“SleepDown 测试包”目录，文件 ID `1Te_S8YHPt6WHvMq3PdoEwtwkVdZ7K06N`；[下载 APK](https://drive.google.com/file/d/1Te_S8YHPt6WHvMq3PdoEwtwkVdZ7K06N/view?usp=drivesdk)。已回读名称、7,844,089 字节与原父目录，未改共享权限。远端工具不返回 SHA，不能把本地／手机哈希核对表述成远端内容校验。未提交、推送、合并或公开发布。

### 手机位置修正版

只调整紧凑／宽窗口的统计胶囊顺序，未追加测试；完整签名 Release 5m27s 通过，保留 R8、资源压缩、lintVital 和金标。日志 `tmp/ai-mobile-capsule-position-release-20261102.log`，架构与 diff 检查通过。版本／包名／证书延续前批，大小 7,844,089 字节，SHA-256 `77a5cc454265a05a1329fe4bc7396c55ae62ef866de4ac452a4dcb6a3b057774`，保全 `tmp/review-apks/SleepDown-1.2.8-beta2-mobile-capsule-20261102-77a5cc45.apk` 与同名 JSON。

重新枚举 5038、5037 和 mDNS，仍为原 PLJ110 的 5037 USB；SDK 客户端覆盖安装 Success，手机 base.apk 哈希与本地一致。未清数据或启动应用。通过 Drive 插件上传同一测试目录，并回读名称、大小与父目录：[下载位置修正版](https://drive.google.com/file/d/1ncToPdnezscnEmEkoVovKY-RVBzuqoqA/view?usp=drivesdk)。未提交、推送或公开发布。

模拟教务测试检查工具目录、入口脱敏和过早提交拦截，不使用真实学校账号或付费模型。未做手机视觉／触觉或实际学校接口验收，不能将编译或模拟传输当作这类证据。
