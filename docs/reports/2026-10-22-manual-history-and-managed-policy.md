# 手动导入历史转场与模型策略集成

日期：2026-10-22，Asia/Shanghai。

用户在前批动画收尾后明确要求“上线后端，然后按文档继续接无缝动画打包安装”，随后要求 AGENTS.md 自动寻找一切可用安装通道。

## 本批变化

- 在原分支 `codex/sharedviewkit-ai-history` 接续 `501cb1a8`，保持架构/凝光前置改动。新增第四条 Kit 候选 `manual_import_to_history`，复用已有真实 opaque 历史 Activity，保留原 Parabolic Legacy 路线。
- 手动导入的历史按钮在源隐藏期间保留布局槽，持续记录当前窗口位置；返回时读取实时位置，源组件卸载后返回无锚点，由既有安全路径处理。复用原截图、源交接和会话释放，不引入占位 Activity 或双重 Morph。
- 把主目录 `28c8e117` 的模型策略代码适配到本分支：共享远程存储仍位于 core，供应商解析仍位于 `feature/importing/ManagedAiRemoteConfig`，没有把架构回退到旧 app/config 实现。官方模型发现与自有 Key 保持原有功能。
- 支持后台模型别名、可见性、思考强度范围/默认值/锁定值；各候选线路独立解析，Responses 与 Chat 请求发送对应参数，锁定时隐藏切换。无策略旧配置兼容。
- 四条候选为课程管理→详情、AI 历史→详情、AI 进度顶栏→历史、手动导入→历史。普通构建仍受远程门控，当前本地安装候选显式启用 `sleepdown.sharedViewKitTest=true`；商店版仍不打包 SDK。
- AGENTS.md 改为优先 5038，并主动枚举现有 USB、其他 ADB 服务及已配对无线通道。核对目标后使用可用通道，不为端口重复请求许可，不停止 OPPO 互联抢占 USB。

## 安装目标与授权

本次已识别 USB PLJ110，Android 17，序列号 `3B15AE023YL00000`。5038 无设备；5037 为 `D:\Oppo Connect\daemon\bin\adb.exe`，SDK 客户端固定为 `D:\Android studio\AndroidSDK\platform-tools\adb.exe`。用户已明确允许本次通过现有 5037 USB 覆盖安装，随后把可用通道选择写入长期指南。

mDNS 另发现一台小米设备，核对型号后已断开项目建立的无线连接，没有向其安装应用。未改动手机数据或关闭其他服务。

## 验证与交付

完整 `assembleGithubRelease` 和七类 `testGithubDebugUnitTest` 成功，8m59s，192 个任务，30 执行、162 复用；单 worker、临时 4GB JVM。保留 R8、资源压缩、lintVital 与正式 v2 签名，不使用跳过资源压缩选项。

| 测试类 | 通过数量 |
| --- | ---: |
| SharedViewKitBackendTest | 14 |
| SharedViewKitCapabilitiesTest | 3 |
| TransitionFeaturePolicyTest | 4 |
| TransitionRouteCatalogTest | 5 |
| ManagedAiPolicyTest | 6 |
| ManagedAiFailoverTest | 5 |
| RemoteConfigModelsTest | 4 |

共 41 项，0 失败/错误/跳过。没有运行无关完整套件。前批 26 项和主目录 14 项结果未用于冒充本批集成验证。

APK 副本：主目录 `tmp/review-apks/SleepDown-sharedviewkit-policy-20261022.apk`，7,607,803 字节，SHA-256 `41685c3fd853be5dce306ebd99f92396fdb28035b0c022d0cde79517b4206419`。包名 `com.xiaomanjun.sleepdownschedule`，`1.2.7.1` / 35，正式证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与既有安装一致。回读 BuildConfig 确认实验、SharedViewKit 本地试点和远程 AI 均启用，没有记录传输密钥。

2026-10-22 20:16:07 经上文的 SDK 客户端和 5037 USB 通道对 PLJ110 执行 `install -r`，返回 Success；系统版本回读正确，设备安装后的 APK SHA-256 与副本一致。没有清数据、主动启动或修改系统设置。安装前版本亦为 `1.2.7.1` / 35，更新时间 17:32:30；不能据相同版本名误认没有更新。

ROM 是否实际接管四条路线、完整开合、源隐藏/恢复、返回和视觉流畅度仍待用户在手机验证。其他未适配路线仍沿用 Legacy，本次没有合并、推送或公开发布。
