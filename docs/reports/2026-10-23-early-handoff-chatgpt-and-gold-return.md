# 2026-10-23 提前液态叠化、ChatGPT 流式工具与金标返回续修

接续 `codex/sharedviewkit-ai-history` / `f0d0f8ba` 与全部未提交改动。用户安装上一候选后确认：液态弹窗叠化偏晚，ChatGPT 仍提示“模型未使用工具”，当前模型为 6 Luna；随后追加加号锚点仍不匹配。本次不切换模型，不清除账号或课程数据，继续沿用已授权的私有 Drive 传包方式。

## 变化与证据

- 液态课程编辑和个性化的内容叠化从原始进度 35%～70% 提前到 18%～50%；课程透明外壳的接入从形状进度 16%～68% 提前到 8%～48%。不改路线几何和总时长，凝光保留原窗口。相同进度用于中途反向，保持内容透明度连续。
- ChatGPT 请求改为 developer `additional_tools` 输入项，明确 `defer_loading=false`，工具定义排在回放的调用和结果之前。该方式是 [SIWC 预览规则](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)列出的另一条支持路径，位置及格式按 [additional_tools 官方说明](https://developers.openai.com/api/docs/guides/tools-tool-search#add-tools-at-a-specific-point-in-the-input)。不使用该账号通道不支持的工具搜索，不再依赖顶层 namespace 加载；旧的 namespaced 调用仍能解析。
- 找到并复现独立的流式解析问题：`ResponsesSseAccumulator` 收到最终 response 后直接返回完成对象，会丢掉此前已完成的函数调用事件。现在只合并经过 `output_item.done` 确认的项目，保留 call_id、namespace、完整参数、推理与消息顺序；最终非空字段优先，相同调用不重复加入。没有整体成功完成、只有 added/delta 的调用仍不得执行。测试用空 terminal output 的真实 SSE 格式经过 reader、runner、本地工具执行及结果回放，而非直接向 runner 填入带工具的完成 JSON。
- 连续没有可执行调用时，提示改为描述协议事实，不归因于模型能力。AI 设置保存 `local_tools_not_returned` 和输出类型摘要，不包含用户原话、课程或令牌。用户提供的 6 Luna 未更换；[模型官方说明](https://developers.openai.com/api/docs/models/gpt-6-luna)支持通过 Responses 调用函数，但真实账号的实际失败原因仍需结合本包实测，不能仅由模型名称或脚本测试认定。
- 金标锚点的具体漏接：原实现已采集真实 FAB，但 `SharedViewKitBackend.requestClose` 只传 bounds/radius，未将返回画面交给 SDK。核对实际依赖 `sharedview-kit:1.0.0.4` 的类常量及已审计官方 Demo README，`EXTRA_BITMAP=sharedView.bitmap` 可在 `overrideViewAnimationTransition` 刷新返回位图，必须先检查设备 extras 支持。现在按能力声明传入独立软件副本，保存到 BACK 完成或会话清理再回收，不影响业务原位图。应用内菜单/表单返回也复用同一真实 FAB 层，避免新建原生按钮时尺寸与材质上下文不一致。

## 验证与交付

首批叠化/工具回归 7 类 107 项通过（3m05s，0 失败、错误或跳过）：ChatGPT 本地工具 8、账户 wire 16、响应 reader 11、导入流事件 28、工具协议 7、Agent 循环 9、玻璃框架 28。覆盖提前叠化不改几何、反向透明度连续、输入声明不重复、流式完成项保留、同一调用去重、最终参数优先、未完成调用不恢复。

追加金标返回回归 3 类 21 项通过（3m08s，0 失败、错误或跳过）：后端 15、能力解析 3、Dock 组合 3。新用例检查开场菜单与返回按钮使用不同画面、位图尺寸与像素确实进入 SDK Bundle、只有设备声明支持才发送、BACK 完成前保持有效、清理只回收 SDK 副本不回收业务源。两轮共 10 类 128 项，无重复测试类。

最终 `assembleGithubRelease -Psleepdown.sharedViewKitTest=true --console=plain --no-parallel --max-workers=1 --no-daemon` 4m35s 成功（159 项：19 执行、140 复用），含 Kotlin、R8、资源压缩、lintVital 和正式签名。52 个变动生产文件的构建前后哈希一致。

候选 `tmp/review-apks/SleepDown-early-handoff-chatgpt-gold-return-20261023.apk` 位于主目录，7,607,823 字节，SHA-256 `7a35136e00ba44553a102c30ba388dc4972eb52e1482724400cc4e67280fc3ad`，身份仍为 `com.xiaomanjun.sleepdownschedule`、`1.2.7.1` / 35。v2 签名通过，正式证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`；两个实验/金标生成开关均 true。对照本次 R8 mapping 检查实际 APK：`b3.p` 金标门控没有远端 allowlist 检查；`SharedViewKitBackend.requestClose` 合入的 `rc` 类确实包含 `sharedView.bitmap` 与对应 `Bundle.putParcelable`。相关日志、源哈希清单及反汇编只留主目录 `tmp/`。

已作为新文件上传到用户授权的私有“SleepDown 测试包”，保留此前候选。文件元数据与目录列表回读确认文件名、大小、父目录、未分享状态及可下载；准确链接和 ID 在主目录 `tmp/review-apks/remote-delivery-20261023.json`。用户已选择只传包，本轮不等待 ADB。

本轮没有实际访问用户 ChatGPT 账号、操作课表、提交、推送、PR、合并或发布；没有本候选的设备安装或 ROM 视觉证据。6 Luna 账号工具是否恢复、设备是否声明返回位图能力及实际锚点外观仍待用户复验；新增询问“锚点对不上”是否也包括位置/大小尚未回应，不声称已复现几何偏移。
