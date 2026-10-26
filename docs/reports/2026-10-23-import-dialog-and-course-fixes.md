# 导入、弹窗与节次续修

2026-10-23，Asia/Shanghai。工作树 `tmp/sharedviewkit-ai-history`，分支 `codex/sharedviewkit-ai-history`，接续 `f0d0f8ba` 与用户已有未提交修改。本轮用户要求继续修复教务金标返回外观、液态编辑器残影、弹窗加载与性能、刷新保护、ChatGPT 工具、GitHub issue 和节次重开不一致。

## 实现与检查

- 教务返回：凝光加号快照使用显式配置的 FAB 颜色和实际 `ic_add_course` 矢量，去掉材质 drawable 内部 blend 色与手绘加号；主题、颜色、密度变化后重新录制。用户原有金标路线、Manifest、导航精简与其他未提交工作均保留。
- 液态弹窗：课程编辑与个性化统一预载，表单随 Morph 叠化，关闭时保留到动画完成。课程编辑的大外壳停稳后不再持有原课程卡片材质树。按用户安装后反馈，液态外壳保留原有颜色与透明度，直接透出宿主已有的低成本全屏模糊，只加染色与高光描边；外壳不采样下层、不分配独立模糊或折射。按钮和内层小控件继续各自管理。表单圆角在图层阶段读取，不为每帧圆角变化重新组合整个编辑器。
- 个性化：去掉多余的预载等待帧与逐行入场；凝光使用公共原生表面，原生 View 固定最终尺寸，随外壳变换，避免逐帧重测材质 View。
- 口令导入：运动期不读取整屏历史背景位图，停稳后才准备历史返回截图；历史文件读取移到 IO。
- 刷新：核对已有 `6453fae4` 的暂存、差异选择、事务校验与网页刷新入口。自动检查和手动刷新均不直接替换绑定课表；默认保留原内容。修正手动刷新被“相同远端内容已核对”抑制的问题，允许用户重新比较先前放弃的更新。网页刷新现在显式保留入口身份：登录绑定失效或适配器变化时给出错误，不能因为 `retainedSession == null` 退回普通导入、覆盖当前打开的其他课表；用户明确另存新课表时仍可继续。
- ChatGPT：保留原账户 Responses 协议、命名空间和原始调用 ID。首次能力拒绝后，只补救一次，并要求从只读函数中选取查询；不强制调用写入、计划或记忆工具。接受已知 `functions` / `tools` / `sleepdown` 前缀组合，其他命名空间仍拒绝。协议依据：[OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)。
- [issue #82](https://github.com/xiaomanjun233/SleepDown-Schedule/issues/82)：教务 bridge 原生 prompt 根据账号、学号、用户名和密码语义补上 Compose Autofill `ContentType`，密码使用 Password 键盘与掩码；验证码和普通问题不标为密码。现有 WebView 的域名自动填充链路继续保留。未发评论或关闭 issue。
- 节次：旧逐课时数据按真实起止匹配当前作息；仅完整、连续、无歧义的匹配显示为“按节次”，错位或部分匹配保留精确时间。显式选择连续范围后不再被旧记录的非连续编号反向覆盖；仅查看、未修改时保留原始时钟。

## 验证与边界

9 类 75 项定向测试通过，0 失败/错误/跳过：刷新仓库事务 5、差异合并 6、时间映射 20、编辑数据保护 11、工具协议 7、ChatGPT 本地工具 4、账户 wire 16、教务语义 3、Dock 组合边界 3。`testGithubDebugUnitTest` 与首轮完整 Release 合计 9m20s。测试编译中发现旧 Dock 用例仍引用已删除参数，按现接口修正三处调用，保留原断言；没有扩大到完整测试套件。

网页绑定失效保护在上述编译完成后补齐，因此另跑最终 `assembleGithubRelease`，5m40s 成功（159 项：19 执行、140 复用），包含 Kotlin、R8、资源压缩、lintVital、打包和正式签名。已在最终 Release class 中核对新拒绝分支的提示文本，并对照最终构建前后 39 个修改过的生产文件 SHA-256，未发生源码漂移。最后这个 Activity 保护分支经过最终 Release 编译，未用真实登录态触发。

最终候选位于主目录 `tmp/review-apks/SleepDown-import-dialog-fixes-20261023.apk`：7,607,823 字节，SHA-256 `1d492c1517ba2ff192107b6d51615857988c0a006a5570eff89a433b85ddf84e`。`apksigner verify --verbose --print-certs` 成功，v2 签名、证书 SHA-256 `0f2b50df…` 与既有发布证书一致；`aapt dump badging` 确认为 `com.xiaomanjun.sleepdownschedule`、`1.2.7.1` / 35。最终日志为主目录 `tmp/build-bugfix-package-20261023.log`，75 项测试及前批完整构建见 `tmp/build-bugfix-verified-20261023.log`。临时产物不入库。

没有使用用户真实账号发送 ChatGPT 请求、操作教务账号或修改手机课表；构建与单元测试不能代替实际 ROM 金标外观、密码管理器填充和帧率验收。首次完整构建的 lint 出现内部 `AsyncExecutionService.getService` / PSI 异常，使用独立 Gradle 进程重试，未跳过 lint、R8 或资源压缩。

## 覆盖安装

用户随后明确要求“安装看看”。先核对项目 5038：SDK 服务端、当前用户密钥目录正确，但无设备；5037 已有 OPPO USB 服务连接唯一目标 OPPO PLJ110（Android 17），两通道 mDNS 均无端点。实际客户端为 `D:\Android studio\AndroidSDK\platform-tools\adb.exe`（37.0.1），使用现有 5037 服务端 `D:\Oppo Connect\daemon\bin\adb.exe`（36.0.1），未终止第三方服务。目标序列号 `3B15AE023YL00000`。

通过显式 `-P 5037 -s 3B15AE023YL00000 install -r` 安装修复包，返回 `Success`。系统 `lastUpdateTime=2026-10-23 12:43:55`，版本 `1.2.7.1` / 35；设备 `base.apk` SHA-256 与上述候选完整一致。覆盖安装保留数据，未主动启动；安装成功不代表视觉、性能或账号链路已完成实机验收。

本轮修改保留在本地未提交工作树中，未暂存用户其他改动；未推送、开 PR、合并或发布。未清库、未升级 Room 或修改备份协议，版本保持不变。

## 安装后修订：金标开关与透明外壳

用户反馈上一包未启用金标。核对确认本轮普通 Release 命令漏传 `-Psleepdown.sharedViewKitTest=true`，生成的 `SLEEPDOWN_SHARED_VIEW_KIT_TEST=false`；旧包 `1d492c15…` 的实际 DEX 仍要求远端总开关与路由 allowlist。前述安装和哈希检查仅证明装入了该包，没有验证金标启用，旧包不再作为本轮验收候选。后续本轮本地安装包必须显式带上该参数；普通发行构建的默认远程门控不变。

用户继续明确液态材质应保留颜色与半透明／全透明，复用已模糊的全屏背景，弹窗自身不再采样，仅加高光描边。`MorphDialogSurface` / `morphDialogTint` 改为透明染色与一条渐变描边，不接收或消费采样 Backdrop；课程编辑复用课程色和 `cardAlpha`（为零可全透明），个性化、添加与口令面板恢复原来的薄染色，中心 Dialog / 外层 QuickSheet 恢复原来的透明染色。沿用首页 `HomeBackgroundBlurLayer` 与中心 Dialog 的既有全屏 plain blur，不新增弹窗 blur/lens，也未恢复小玻璃卡片残影的旧材质树。

凝光口令外观变化定位为 `HomeMenuDestinationOverlay` 的简化分支漏了材质模式判断。已将简化分支限定为液态；凝光口令表单恢复原来的 `LiquidPanel` 参数及路线，保留本轮历史截图延后与 IO 读取优化。横屏同样按材质模式分支。

最终完整构建命令为 `assembleGithubRelease -Psleepdown.sharedViewKitTest=true --console=plain --no-parallel --max-workers=2 --no-daemon`，4m14s 成功（159 项：19 执行、140 复用），含 Kotlin、R8、资源压缩、lintVital 与正式签名；日志 `tmp/build-bugfix-gold-transparent-20261023.log`。这里只改表面和模式分支，不重跑前述数据／协议定向测试。用户补充材质要求前的金标构建虽成功，也没有安装。

新候选为主目录 `tmp/review-apks/SleepDown-import-dialog-fixes-gold-transparent-20261023.apk`，7,607,823 字节，SHA-256 `f671eb08bf938e4311c75f1dffa672dac0a5e71dce99606c42ede03a174e3165`，`1.2.7.1` / 35，v2 签名和证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a` 核验通过。生成配置的两个实验／金标开关均为 true；使用最终 R8 mapping 与 APKAnalyzer 实际反汇编 `ActivityTransitionCoordinator$open$2`（`b3.p`），已确认最终 APK 的 native 分支直接检查适配路线、锚点和目标，不再带远端 allowlist 的 `Set.contains` 判断（旧包有一处，新包零处）。这验证本地金标门控，不代替 ROM 实际动画验收。

准备安装时手机已从 5037 USB 消失；重新枚举 5037/5038、两个服务的 mDNS、所有 ADB 进程监听端口及当前 USB，均没有可用目标或无线端点。5037 OPPO / 5038 SDK 服务源正常且未被终止。已请求用户恢复连接或提供当前无线调试端点；新候选尚未安装，手机仍可能运行上一包，不能将前述旧包安装记录当作新包的安装结果。

用户随后明确改为只传包，并批准 Google Drive 上传及创建专用目录。已通过 Google Drive 插件将新候选上传并移入私有“SleepDown 测试包”；文件元数据与目录列表回读确认大小为 7,607,823 字节、属于该目录、未对外分享且可下载。实际目录和文件链接仅保存在主目录忽略记录 `tmp/review-apks/remote-delivery-20261023.json`，后续复用该目录。此轮交付不再等待 ADB，仍未验证手机下载安装或实际动画表现。
