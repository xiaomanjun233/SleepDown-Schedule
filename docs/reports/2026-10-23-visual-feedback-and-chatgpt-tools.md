# 2026-10-23 弹窗截图反馈与 ChatGPT 工具续修

本轮接续 `tmp/sharedviewkit-ai-history` / `codex/sharedviewkit-ai-history`、HEAD `f0d0f8ba` 及已有未提交工作。用户提供五张实机截图，要求统一凝光表单透明度、修复文字反色和返回加号、明确两种课程材质的归属、平滑液态材质交接、扩大顶栏渐变模糊并参考 Kyant 默认描边。随后确认 ChatGPT 的表现是“回复无法读取，没出现工具执行记录”。不保存截图、课表内容或账号凭据。

## 实现

- `NativeMaterialSurfaceRole.Form` 使用原生模糊但不叠加深色预设 tint。首页个性化、添加和口令表单统一使用该入口与原有薄染色；个性化仍固定最终测量尺寸。不是把所有材质变为不透明纯色。
- 公共 `DialogCapsuleField` 和 `DialogLiquidButton` 从面板前景决定控件明暗，避免系统浅色主题生成白底而玻璃正文仍为白色。课程编辑、首页添加与口令明确继承所在面板前景；独立原生 Dialog 保留自己的主题。
- 凝光模式不读取旧课程材质 opt-in 的开关值，不再显示独立凝光开关或液态卡片选项；液态偏好仍保存在原字段中。原生课程渲染失败时保留普通染色，不混入液态卡片。
- 原生加号的返回层改为窗口 PixelCopy 得到的真实按钮画面，保留实际尺寸、图标及原生效果；源按钮停稳后准备，首次尚未附着导致复制缺失时可在点击时重试。去掉手绘实色圆与缩小图标、软件 View 截图的旧实现。
- 液态课程展开先复制真实卡片画面，再与低成本透明外壳和完整预载表单叠化，源画面及表单在运动期动态模糊；稳定表单去掉临时内容 RenderEffect。部分越界的卡片继续用原有内容副本，避免拉伸裁剪截图。透明外壳继续只绘制原颜色和高光，不采样下层。
- `MorphDialogSurface` 的描边调用 Kyant 原有 `HighlightElement`，仅在第三方模块补一个公开 modifier 入口；沿用默认宽度、模糊和 shader，降低整体 alpha。没有新建 Backdrop 消费者。课程编辑顶栏模糊覆盖从 70dp 扩至 104dp，保留 70dp 按钮布局，取消额外 tint 和亮色 fallback。
- ChatGPT wire 继续按账号专用预览规则把函数放入 `sleepdown` 命名空间，所需工具通过该命名空间提供。解析明确校验调用的 namespace、保持 call_id 与推理项；未知命名空间拒绝映射成本地函数。
- 对明确的课表请求，ChatGPT 首轮仅提供只读函数并设置 `tool_choice=required`；获得本地结果后恢复正常工具决策。普通问候不强制读课表，导入工作区沿用自身工具。无工具调用的回复只纠正一次，连续未调用显示诊断，不伪称已读到数据。写入与记忆工具不在强制读工具集合中，用户确认保存的规则保留。

账号工具分组遵循 [SIWC 专用预览限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)，required 和调用结果回传参考 [OpenAI 官方函数调用文档](https://developers.openai.com/api/docs/guides/function-calling)。模型没有执行工具前的拒绝不能推断为数据库、系统权限或 OAuth 失效；本轮没有使用真实账号发请求。

## 验证与交付

首轮 UI `compileGithubReleaseKotlin -Psleepdown.sharedViewKitTest=true` 成功，3m22s。新增往返用例执行真正的 runner，脚本化响应经过实际 wire 标准化，并验证只读声明、函数执行、call_id 结果回传、推理项回放、最终回答、一次重试上限和问候不强制读取。此处使用合成课表，不能代替真实账号验收。

调查中先按通用 Responses 规则尝试了平铺函数，40 项脚本测试通过；随后补查 SIWC 专用限制，确认该尝试不适用于账号通道，未上传或安装该候选，已停止旧构建并保留原命名空间要求。最终测试与交付只以修正后的命名空间请求为准。

最终账号规则的 `testGithubDebugUnitTest`（显式金标参数，单 worker）3m14s 成功，6 类 55 项，0 失败、错误或跳过：ChatGPT 本地工具 8、账户 wire 16、工具协议 7、Agent 任务循环 9、课程飞行几何 12、首页 Morph 预载 3。之后只收紧了凝光模式隐藏液态材质控件的条件，以及内容模糊在关闭起点从零渐变的视觉参数；没有再改协议、纯几何或预载状态规则，最终 Release 包含这两处界面修订。

最终 `assembleGithubRelease -Psleepdown.sharedViewKitTest=true --console=plain --no-parallel --max-workers=1 --no-daemon` 4m58s 成功（159 项：21 执行、138 复用），含 Release Kotlin、R8、资源压缩、lintVital 与正式签名。日志在主目录 `tmp/build-visual-feedback-gold-final-20261023.log`；最终源码 46 个生产文件的 SHA-256 与构建前清单一致，无源码漂移。

新候选为主目录 `tmp/review-apks/SleepDown-visual-feedback-gold-20261023.apk`，7,607,823 字节，SHA-256 `13221a0bc939ff1b6fa88be5be18ee770c8520cded00159319d7235b0911bd99`。包名 `com.xiaomanjun.sleepdownschedule`，版本仍为 `1.2.7.1` / 35；v2 签名验证通过，正式证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。生成的实验与金标开关均为 true。按本次 R8 mapping 用 APKAnalyzer 检查 APK 内 `ActivityTransitionCoordinator$open$2`（`b3.p`），本地金标门控已折叠，远端 allowlist 的 `Set.contains` 检查为零；这不替代 ROM 实际接管验收。

已按用户“只传包”及专用目录授权上传为新的 Drive 文件，保留旧候选。回读文件元数据与目录列表确认名称、大小、父目录、未对外分享和可下载标记；实际链接与 ID 记录在主目录忽略文件 `tmp/review-apks/remote-delivery-20261023.json`。本轮不等待 ADB；尚无本候选手机安装、真实 ChatGPT 账号或实机视觉/性能验证，不将旧安装记录当作本包证据。

没有本轮提交、推送、PR、合并或发布，没有清除数据、数据库迁移或改变备份格式。
