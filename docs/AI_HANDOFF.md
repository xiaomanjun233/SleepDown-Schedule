# 当前工作交接

## 2026-11-06 新任务：保存 PR 基线并迁移统一渲染框架

用户在本轮明确要求：安装完成后保留现有更改并上传 PR，再以该基线建立可回滚的独立分支；统一 Kyant 液态玻璃与最新 COUI-Kit 的采样渲染链路，保留现有玻璃观感，布局共用、材质和触摸反馈区分；逐项核对凝光现有系统类，按 COUI 最新指南移植并推广到其他机型，缺失组件参考 E 盘 ColorOS 完整反编译包。用户最后明确覆盖了即时删除要求：原实现保留，等其明早核验后才逐步删除无用旧代码；已借用 Miuix 按实际用途保留。没有合并或发布授权，没有协作 Agent 授权。

当前已完成安装（见下节），正在将此前所有现有源码、测试和相关记录保存为 `codex/rescue-import-materials` 的 Draft PR，目标 main。原图 output、tmp、APK/mapping、设备截图、缓存、私有后端与凭据留本地。当前会话无 GitHub 插件工具，因此已向用户说明能力缺口，使用 Git credential helper 已有凭据和 GitHub 官方 API 处理 PR，不使用 gh，凭据只在内存。已 fetch origin/main，现有 HEAD `3cb7285f` 比 main 多一笔首页修复，作为当前基线保留。

后续分支计划 `codex/unified-coui-rendering`，必须从已保存 PR head 派生，先阅读 E 盘最新 COUI 文档/源码、当前 glass 与原生 ColorOS 调用。保留可回滚旧实现和切换能力；完成后交付新包与真实验证，不用纯本地渲染结果冒充跨机型 GPU 验收。

## 2026-11-06 续作：全称、GitHub 与全屏更新内页（已安装）

用户追加截图要求：关于头图写全称，下方不再使用应用名称/开发者双列，直接放个人 GitHub，正常纵向排版；查看日志与新版本使用全屏内页。现头图为“SleepDown课程表”，下方普通 COUI 行显示 `github.com/xiaomanjun233` 并打开个人主页；保留项目源码等其他入口。检查更新进入新增 `SettingsPage.SoftwareUpdate`，历史日志由 `SettingsPage.Changelog` 单独全屏呈现。已同时接入手机 SettingsDetailActivityHost 和宽窗口 SettingsScreen，保留原转场/返回栈；代理协议 ABOUT/CHANGELOG/DOWNLOAD 分别指向对应页面。

新版本页使用纯底、可滚动版本说明和固定在底部安全区上方的 220×44dp 主题色按钮，复用原 AppUpdateSession 与底部安装权限窗口。最终定向 About/Home 5 项通过，已查看真实 Native Graphics 栅格，日志 `tmp/c17-about-fullscreen-tests-20261106.log`。没有重跑无关套件。下方 21 项是这次全屏追加前的验证。

本轮重新枚举设备：SDK 37 客户端；5038 为 SDK 服务但无设备；5037 为 `D:\Oppo Connect\daemon\bin\adb.exe` 36.0.1 服务，USB 连接同一台 `3B15AE023YL00000 / PLJ110 / Android 17 / SDK 37`。两通道 mDNS 均无端点。未停止互联服务；隔离 ART 和覆盖安装均显式使用 SDK 客户端 `-P 5037 -s 3B15AE023YL00000`。新 Release `326fde9c` 已通过 ART（nd4，43 方法），覆盖安装返回 Success，设备 base APK 哈希核对一致，未启动应用。完整元数据 `tmp/c17-about-widget-delivery-20261106.json`。

本轮报告：[C17 关于、更新与投光修订](reports/2026-11-06-c17-about-widget-refinement.md)。18 项生产文件与资源构建前后哈希一致。完整 assembleGithubRelease 6 分 49 秒通过，APK 与精确 mapping 已保存于 `tmp/review-apks/SleepDown-1.2.8-beta2-c17-about-widget-20261106-326fde9c*`。新视觉与小组件投光尚待用户实机验收，未发布。

## 2026-11-06 C17 关于/软件更新、深色预览与组件光效（当前轮，验证中）

上一轮 `beb9347f` 已按用户“安装”要求，在 11 月 5 日 22:50 经 SDK 5038 / USB 覆盖安装到 PLJ110（Android 17 / SDK 37），Success 后读取已安装 APK SHA256 与交付包一致，未自动启动。元数据 `tmp/settings-art-crash-delivery-20261105.json` 已标记 installed=true。下方交付前“未安装”是历史状态。

用户新要求：读取相册截图对齐深色排版；视效说明用普通副标题样式；分隔线恢复系统原色；检查更新与关于页合并，参考 ColorOS 17 关于本机与软件更新页及按钮；生成头图；组件光效仍有问题并给出新截图。已读取手机最近相关截图与 E 盘 Settings 17 资源，另外只读拉取当前 com.oplus.ota APK 到 tmp 并解码资源。没有把截图、系统 APK 或反编译源码加入应用资源。

已完成源码（尚未通过本轮最终构建）：预览按原始 210×425px / xxhdpi 的 70×141.67dp 比例缩小并恢复选中强调色；Dropdown 说明显式使用 PreferenceSummary + onSurfaceVariant；分隔线恢复 outlineVariant（浅 #1F000000、深 #33FFFFFF）；首页关于行进入统一 About，移除单独检查更新行，搜索改“搜索设置项”及统一结果组/路径说明。原 About/Changelog 路由兼容保留，所有历史日志提取保留；新关于页为版本头图、40dp 检查入口、应用信息、日志、项目/隐私/许可/致谢。检查结果和版本说明在本页呈现，原下载/安装会话复用，下载按钮按 OTA 原始 220×44dp、14sp 和胶囊组织，安装权限仍走底部窗口。

用户已明确允许 API/CLI 并指定当前 custom 的 gpt-image-2.5-sunburst。已通过本机配置的服务确认模型并使用 imagegen 官方 CLI 生成成功（45s）；凭据仅在进程内存使用，没有输出。生成原图 `output/imagegen/sleepdown-about-hero-c17.png`（1774×887），项目资源 `app/src/main/res/drawable-nodpi/about_sleepdown_hero.webp`（1280×640，24,984 字节）；提示词 `tmp/imagegen-about-hero-prompt.txt`，日志 `tmp/imagegen-sunburst-about-20261105.log`。

组件截图显示圆角外露光与屏幕右缘硬切。原代码 cornerRadius 没有用于投光输入，AndroidView 软件截图又不包含 Compose 外层 squircle；渐隐矩形还延伸到屏幕外。现仅给投光输入加与预览相同的 squircle 掩码，并按实际窗口约束渐隐范围；显式固定横向变换中心。不修改原 RemoteViews、用户壁纸、预览保存或课程快照流，COE 本体与原版动效继续复用。增加对应像素/边界回归。

当前运行 `testGithubDebugUnitTest`，筛选 SettingsHomeVisualTest / AboutSettingsUiTest / ColorOsCardLightTest / ScheduleDetailPageUiTest / SettingsSearchTest，日志 `tmp/c17-settings-and-widget-ui-tests-20261106.log`。需要检查输出 `tmp/coui-visual-review-20261106/` 的真实栅格，通过后再 assembleGithubRelease、保存 APK+mapping 并复核 ART；本轮还没有新交付包。保留此前 PeriodSchemeEditor 的定向 R8 保护。当前仍是 codex/rescue-import-materials 大量未提交工作树，未暂存或推送。

## 2026-11-05 详细设置真实 VerifyError 与通知单行开关（当前轮）

用户要求先读取真实崩溃栈再修详细设置闪退，并按原版调整通知设置单行开关。已通过 SDK ADB 5038 / USB 连接 PLJ110（Android 17 / SDK 37），服务端来源和当前用户密钥核对通过。手机安装的仍是 `c9705f18`，读取的 21:31、22:06、22:09 三次崩溃均为 `VerifyError`：作息编辑器 `PeriodSchemeEditor` 的顶部间距加法将 Modifier 引用当作 Float，非此前本地测试的 Mesh 异常。

用同一手机上的隔离 DEX 验证器强制初始化作息编辑器类，旧包 `c9705f18`（bg4）与上一轮交付 `d83a1b0c`（jd4）分别稳定复现 `0xC5F/v0` 与 `0xC5B/v2` 类型错误。通过准确 APK、方法参数和 Compose 源标记定位；未用新 mapping 还原旧包。只在 `/data/local/tmp` 验证，没有安装、启动主应用或读取课程数据库。修复在 app/proguard-rules.pro 中仅禁止 `PeriodSchemeEditor-*` 方法优化，允许混淆和裁剪，未全局关闭 R8。后续 APK 须连同精确 mapping 本地保存，并通过同一手机的强制初始化校验后交付。

通知开关此前子 Switch 自带最小 48dp 点击区，再加父行 20dp 内距，单行被撑到 68dp。已按 E 盘实际 Demo 和 Settings 17 反编译改为整行唯一点击/无障碍 owner，共享反馈源，子开关只测量 24dp 轨道并保留拖动；目标普通单行 48dp，多行说明自适应，液态路径及绿色不变。

固定源码后，4 项 ScheduleDetailPageUiTest（包含真实通知页渲染、48dp 行高和标题/右侧点击各提交一次）与完整签名 Release 顺序通过（7m40s，244 项中 22 执行），日志 `tmp/settings-art-crash-and-notification-package-20261105.log`。已打开查看 `tmp/coui-visual-review-20261105/notification-settings-native.png`。最终 APK 在同一 PLJ110 的隔离 ART 强制初始化通过：`VERIFIED class=kd4 methods=43`，`tmp/settings-art-active-verify-fixed-20261105.txt`；不是只凭 Robolectric 或构建结果宣称修复。

新包 `tmp/review-apks/SleepDown-1.2.8-beta2-settings-crash-notification-20261105-beb9347f.apk`，8,180,451 字节，SHA256 `beb9347faff27011f5acf34ac796eabf6d86cba5d01ea5aab66c5a7315c35f43`，`com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / 36`、minSdk 26。复制后哈希一致，V2 证书仍为 `0f2b50df…`，保留 R8、资源压缩、lintVital。精确 mapping 与 APK 同目录同名 `-mapping.txt`，map id `7f43fffc…`，摘要 `tmp/settings-art-crash-delivery-20261105.json`。已清理手机本轮五个 /data/local/tmp 测试文件；没有覆盖安装、启动主应用、访问课程数据、上传、暂存或推送。原始诊断、APK 单类反编译和验证器均在忽略的 tmp 中。详见 [本轮报告](reports/2026-11-05-settings-art-verify-crash-and-switch-height.md)。`d83a1b0c` 已确认仍存在同一 VerifyError，不能再作为闪退修复包；本轮通过的是设备字节码校验，尚未覆盖安装后点击整条路线验收。

## 2026-11-05 首页分类与 Popup 细节续修（当前轮）

用户在 `c9705f18` 包后要求：关于应用入口下方线右移、分隔线更淡；Popup 恢复原改版 Miuix；液态开关保留原绿色；深色模式参考系统设置；设置弹窗不居中；首页不写分类名。已按这些要求完成源码：保留 COUI 页面和 Item，下拉面板恢复 `miuix-local` 原 Morph / 玻璃 / 滑选并补齐透明本地 host；首页去掉三个分类标题保留分组，头部线 88dp、其他图标行线 56dp、全部设置线 12% 前景；液态开关恢复默认绿色；预览按系统节奏和可用列宽排布、改 COUI Radio；ContentDialog / Alert 改 Bottom，关闭交接等 250ms 系统倍率动画。未改其他活动功能、数据或 E 盘资料，也未继续猜调 Widget 光区。

9 项定向回归已通过（2m25s，0 失败/错误/跳过），包括 SDK 26/35 详情、作息下拉、日期底部 Window gravity、Popup 重开/点击空白关闭/再选择，以及首页颜色/轮廓/行高/分类消失/首条线新缩进与淡色、预览手动选择保存。已实际打开原生/液态首页、通用设置预览及原 Miuix Popup 的 Compose Native Graphics 栅格，仍不作为手机 ROM 字体和硬件材质验收。图在 `tmp/coui-visual-review-20261105/`，验证日志 `tmp/settings-refinements-verification-final-20261105.log`。详见 [本轮报告](reports/2026-11-05-settings-popup-and-home-refinements.md)。

最后核查将首页“检查更新”弹窗 host 移入设置内容作用域，独立首页也读取该页主题和 Backdrop；中间 Release 已停止，补齐后固定源码顺序重跑上述 9 项回归和完整签名 `assembleGithubRelease`，全部通过（11m12s，244 项中 27 执行），日志 `tmp/settings-refinements-final-package-20261105.log`。构建前后 10 个设置源码哈希一致，保留 R8、资源压缩、lintVital、GitHub 金标与既有 V2 签名。

新交付 `tmp/review-apks/SleepDown-1.2.8-beta2-settings-refinements-20261105-d83a1b0c.apk`，8,164,067 字节，SHA256 `d83a1b0cc96d67de63d452a853286674741bb1fecdf9bc5ff826e8e2910c9b5f`，`com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / 36`、minSdk 26。复制后哈希一致，V2 校验通过，证书仍为 `0f2b50df…`；摘要 `tmp/settings-refinements-delivery-20261105.json`。`c9705f18` 为本轮修改前的历史包，不应作为本轮交付。包来自完整工作树，当前仍是 `codex/rescue-import-materials` 大量未提交改动，未安装、启动、上传、暂存、提交或推送。手机实际字体、玻璃效果及旧闪退同源结论仍未实机确认。

## 2026-11-05 用户截图纠正（当前轮）

用户已否定 `51121cf6` 包并给出三张截图，明确指出圆角、布局仍不对，要求按各页面用途分析，必要时反编译 ColorOS。此前仅通用容器与测试结果不足以证明视觉正确。已读取 E 盘实际 Demo 的 Settings 专用装配和现有 Settings 17 完整反编译资源/Java；没有修改 E 盘或上传系统源码、用户截图。

本轮纠正：设置卡片从通用 #333 容器改为 #1AFFFFFF 叠黑；卡片原生/液态共用普通 COUI 24dp/2.5 轮廓，库 Group 只增加 Shape 参数，默认 Demo 的权重 3 不变；首页行 52dp、二级普通行 48dp，首页分隔线起点 56dp；头部图标槽恢复 56dp、版本在标题下方；数字字段收为 64dp，液态搜索补回真实 16dp 边距，首组间距 12dp。原业务功能图标、保存、导航和主题强调色保留。

已输出并实际查看 Compose 首页（原生/液态）及二级页栅格；渲染结果揭示了搜索边距和数字字段挤压，已继续修复。固定最终源码后，5 项页面/栅格回归及完整签名 `assembleGithubRelease` 顺序执行通过（8m58s，244 项中 35 执行）。验证涵盖卡片 RGB、普通轮廓内外采样点、首页 52dp、图标/版本排版、数字标题单行、详细设置滚动和作息/日期提交。图在 `tmp/coui-visual-review-20261105/`，对照明确标注非实机；ROM 字体/硬件材质及手机整体视觉尚未验收。新报告见 [截图纠正](reports/2026-11-05-settings-screenshot-correction.md)。

新交付 `tmp/review-apks/SleepDown-1.2.8-beta2-settings-visual-20261105-c9705f18.apk`，8,230,175 字节，SHA256 `c9705f182de18d878bcc911e24975b4e6ed442a2be2ad7d97a5a18eec52f7385`，`com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / 36`、minSdk 26。保留 R8、资源压缩、lintVital 与既有 V2 证书 `0f2b50df…`，新旧 APK 和 classes.dex 哈希均已区分，复制后校验一致。构建日志 `tmp/coui-screenshot-final-package-20261105.log`，摘要 `tmp/coui-settings-visual-delivery-20261105.json`。包来自完整工作树；未安装、启动、上传、暂存或推送。`51121cf6` 不再作为当前交付或验收结果。

## 2026-11-05 全面 COUI 设置替换与详情崩溃排查（优先于下方旧记录）

用户否定下方 `be9e2fa2` 包，明确“直接移除原来的 miuix 二级页，全面换用 coui”，并报告当前课表详细设置闪退，要求先看代码。后续要求：液态设置搜索使用教务样式但位置不变，COUI 控件跟随应用主题色；保留业务功能、保存语义、首页悬浮搜索和 Rounded 功能图标。

本轮已删除旧设置首页/平板详情/Activity 详情的 Miuix 页面实现及嵌套 Scaffold，直接使用 COUI Scaffold、Item、Menu、Dialog、WheelPicker。设置搜索与教务共用 `LiquidSearchStyle`；强调色接入宿主主题。小组件已修正“传入 receiverBounds 却仍按卡片 1.2 倍裁切”的实际边界缺口，继续使用系统 COE 光效。保留设置以外功能的 Miuix 依赖及兼容主题，不修改用户数据。仍在 `codex/rescue-import-materials` 大量未提交工作树中续作，没有暂存、推送或修改 E 盘源码。

扩展详情测试后复现 COUI Mesh 阴影创建异常。Robolectric 4.16.1 缺少 MeshSpecification 原生 shadow，因此不能认定与手机闪退同源；内部组件入口已定向保护初始化的 IllegalArgumentException，记录诊断并保留按钮/菜单交互，支持 Mesh 的设备继续原 renderer。14 项定向回归及 Release Kotlin 编译通过（8m10s），日志 `tmp/coui-shadow-guard-verification-20261105.log`。此前 USB/5038/5037/mDNS 均无目标，用户明确要求先看代码，本轮未继续等待设备、安装或启动。详情见 [本轮报告](reports/2026-11-05-coui-full-settings-replacement.md)。

最终完整 `assembleGithubRelease` 已通过（8m04s，195 项中 34 执行），保留 R8、资源压缩、lintVital、GitHub 金标与既有 V2 签名。交付 `tmp/review-apks/SleepDown-1.2.8-beta2-coui-full-20261105-51121cf6.apk`，8,230,175 字节，SHA256 `51121cf6be116aef0c544fdb6a67cc5bae63d6de0ed213e1caabb78650faffde`，`com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / 36`、minSdk 26，证书仍为 `0f2b50df…`。复制后哈希一致，构建日志 `tmp/coui-full-release-package-20261105.log`、摘要 `tmp/coui-full-release-delivery-20261105.json`。该包来自完整工作树，包含此前活动功能；未安装、上传或发布，手机闪退与视觉尚待实机确认。不要再将下方 `be9e2fa2` 旧包作为当前交付或验收结果。

## 2026-11-05 COUI 设置签名 Release 已打包交付

用户在本地修复交付后追问“包呢”，已基于当前完整工作树完成 `assembleGithubRelease`，包含本轮设置 / Popup / 小组件投光修复以及此前已有活动功能，不是独立 PR 验证包。没有新增安装、发布或上传指令。

首轮 4GB 堆构建在 R8 阶段发生 JVM native malloc 内存不足，未生成新包；原 12:57 APK 已保全为 `tmp/review-apks/SleepDown-before-coui-audit-20261105-125721-d531348b.apk`，不得当作本轮结果。诊断确认只有一个 2GB Gradle 守护进程处于 IDLE，已正常停止以释放内存。重试使用单 worker、`android.r8.maxWorkers=1`、3GB 堆、`ActiveProcessorCount=2` 和一次性进程，保留 R8、资源压缩、lintVital、GitHub 金标与既有正式签名；没有修改持久构建配置。完整构建 3m51s 成功，日志 `tmp/coui-settings-release-package-retry-20261105.log`。

交付包 `tmp/review-apks/SleepDown-1.2.8-beta2-coui-settings-20261105-be9e2fa2.apk`，8,110,423 字节，`1.2.8_beta2 / 36`、`com.xiaomanjun.sleepdownschedule`，SHA-256 `be9e2fa20ca590f5aa29dc36505efa0f4edf0c2f66a0970974f79997c187c675`。apksigner 已确认 V2 签名有效，新旧包证书 SHA-256 均为 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`；复制后的完整文件哈希一致。摘要 `tmp/coui-settings-release-delivery-20261105.json`。未安装、启动、上传或发布。

## 2026-11-05 COUI 设置布局 / Popup / 小组件投光本地修复完成

用户本轮连续要求：仔细阅读 E 盘最新 COUI 文档与 Demo，首页和二级页的布局、表面颜色、字体与返回图标全部按 COUI 规范统一，保留原有功能、分组与保存语义，首页保留悬浮搜索；同时报告 Popup 消失、小组件光效位置或照射范围不对。没有新增发布或安装指令。

已核对实际源码：主工作树已存在 COUI 兼容接入，并非下节记录的“尚未迁移”。上游 main 为 c35a652；实际固定的最新 Demo 分支为 af952eb，与接入副本一致，E 盘源码及本地开发改动未覆盖。当前继续在 `codex/rescue-import-materials` 未提交工作树内修复公共设置入口与相关页面，保留所有其他活动任务。

已修正混用主题的颜色 / 字体角色、组间距和内边距、返回与右向矢量、普通二级页安全区；补回独立 Miuix Popup/Dialog 宿主，并接入首页折叠悬浮搜索。小组件改为分类型读取未裁剪预览边界，投光终点随实际按钮行计算。用户随后明确“选项行大小图标先用原来的”，已恢复原行测量及 Rounded 功能图标，保留 COUI 页面 / 分组 / 配色 / 字体和此前明确的 COUI 返回图标。

首轮 Release Kotlin 编译通过（3m12s），随后 Release 编译和 8 项定向回归通过（7m05s）。保留原行尺寸与图标后，Release 编译和 8 项回归再次通过（6m45s，0 失败 / 0 跳过，`tmp/coui-settings-final-verification-20261105.log`）；最后的 Insets 与玻璃 Shape 兼容收口后，最终 Release Kotlin 编译通过（2m19s，`tmp/coui-settings-final-compile-20261105.log`）。审批额度曾短暂阻断末轮验证，用户要求继续后已恢复并完成，没有遗留审批阻塞。

液态路径不能直接把 COUI Generic Shape 交给 Lens，分组使用支持 Lens 的玻璃形状及对应半径，搜索保留玻璃 Capsule；原生路径仍使用 COUI Shape。实机视觉尚未验收。详情见 [本轮核查](reports/2026-11-05-coui-settings-layout-popup-audit.md)，后续打包结果见上节。未安装、暂存、提交或推送，保留已有其他活动任务改动。

## 2026-11-05 光效独立分支已推送，COUI-Kit 设置迁移待两项选择

用户追加：分页条同步桌面的震动反馈；组件光区从偏短状态折中；完成光效 PR 后，在可回滚的独立分支/PR 将设置首页和二级页迁往 COUI-Kit。用户已确认“耳机页”为“二级设置页”，并要求先同步 E 盘克隆上游，使用库现有完整接入方法，液态玻璃保留材质。开光效 PR 已授权提交/推送/维护，不含合并或 Release；设置迁移另用分支或 PR，不能混入光效 PR。

已在托管工作树 `C:/Users/23085/.codex/worktrees/native-card-light/CourseSchedule` 创建 `codex/native-card-light-pages`，基于最新 `origin/main`（原救援分支额外的扁黑色岛提交也未夹带）。通过逐文件/逐 hunk 抽取，仅含原生投光、按钮受光、有限分页条、组件投光及其测试/专项报告共 22 文件；AI 导入、分享协议、助手外描边和首页材质等原工作树改动保留原处。不要将原救援工作树全部提交。

真实 Launcher 17.3.11 的 PageIndicatorTouchHelper → VibrationUtils 已核实：只跨页触发，线性马达 WaveformEffect type 0，普通马达 65ms，遵循系统触感开关。原生调用失败与桌面一样在边界捕获。组件深度改为高度×0.65、上限112dp，宽度贴合/渐隐保留。课表仍为原 v4/原实时截图及保存流程，软件转换仅在 COE 上传入口。

独立工作树 7 项光效与 4 项分页/震动去重用例通过，完整签名 Release 8m26s 成功，日志 `tmp/native-light-pr-isolated-20261105.log`。补齐震动异常边界后，最终完整签名 Release 4m06s 成功，日志 `tmp/native-light-pr-final-20261105.log`，构建 session 已结束。功能提交 `6b480f9865162600fa40aab87c8510d749f8880a`，随后交接提交 `01c77e12eb9f629ffb101ba3fc7d97a66f7a2407`，两者已推送 origin/codex/native-card-light-pages，远端首轮功能 SHA 已回读一致，工作树干净。**PR 尚未创建**，官方 API 问题仍待用户回复。正文 `tmp/native-light-pr-body-20261105.md` 已更新实际通过结果；公开报告 `docs/reports/2026-11-05-native-card-light-and-launcher-indicator.md` 随功能提交。

独立 PR 验证包保全 `tmp/review-apks/SleepDown-1.2.8-beta2-native-light-pr-validation-20261105-54581da0.apk`，7,774,615字节，SHA256 `54581da0732ca040329cae62840532a90d1997f3fd7f436e4405ebf2c2fda769`，既有V2证书。汇总 `tmp/native-light-pr-delivery-20261105.json`。该包基于 main+本PR，不含主工作树其他未提交活动功能，**仅用于独立 PR 验证，不自动覆盖现有完整工作树安装包**。旧恢复快照流程的4m57s完整构建也通过，日志 `tmp/native-light-original-snapshots-20261105.log`，不是新增震动的验收结果。

E:/COUI/COUI-Kit 有大量本地未提交开发改动，未触碰。干净 E:/COUI/COUI-Kit-upstream 已 fetch 上游 main，建立 codex/sleepdown-settings-integration-20261105 并检出 c35a652。已读取最新 getting-started / appearance / secondary-pages / compatibility / settings / collapsing-app-bar / top-app-bar / icons 接入说明及 Demo 入口；迁移预检见 [COUI-Kit 设置迁移准备](reports/2026-11-05-coui-kit-settings-migration-preflight.md)。尚未开始迁移源码。

两条异步问题尚未收到回复，不能将时间经过视为许可：① 最新 COUI-Kit minSdk31，宿主26，是否允许独立迁移分支提高到 Android12（未改 minSdk，不使用 overrideLibrary）；② 会话没有任何可调用的 GitHub 插件工具，是否允许仅为已授权光效 PR 复用本机 Git 凭据、通过官方 API 创建（不使用 gh、凭据仅在内存）。用户目前只回复了“二级设置页”澄清，没有回复这两项。Git 提交/推送已由开 PR 授权，可在验证和 diff 审查后完成；API 建 PR 及最低系统变更须等相应答复。创建 PR 后必须 attach_artifact；没有合并/Release/COUI-Kit 上游投稿授权。

最新 ADB SDK5038/OPPO5037 USB、设备表、mDNS 均为空，无其他 ADB 监听端口，尚未安装新增震动版本。云盘权限阻断没有解除证据，本轮未重复触发扩展/原生应用授权。

## 2026-11-05 恢复原快照流程，仅保留光效范围微调（修订包构建中）

用户纠正：“我上次关于快照的提示词似乎让你误入歧途，请先回复原来的那个快照流，只微调光效范围”。按“恢复原来的快照流”执行，已撤回上节 9d49be96 中未证实根因的快照改造：`ScheduleSnapshotStore.kt` 恢复到 Git 基线内容（v4 目录、原 decodeFile/JPEG 临时文件保存），两处截图去掉 freeze，竖屏恢复 `snapshotFallbackOnly = !landscape`，删除只验证已撤回 v5/freeze 行为的新增测试。实时截图、内存复用、磁盘持久化和缺图表现回到原路线；不删除任何设备缓存或课程数据。

只在光效入口保留软件纹理转换与 Surface 首帧门控以防原生闪退；不改变作为卡片/持久缓存使用的原始快照。光区仍保留上轮按组件宽度收束、有限深度、圆角覆盖及角度/横向范围调整，没有追加快照生命周期、方向判断或缓存协议。上一轮已完成的按钮颜色、按钮投影和分页条未改。

沿用未提交工作树和既有打包/安装授权，修订完整签名 Release 构建中，日志 `tmp/native-light-original-snapshots-20261105.log`。最新查验 SDK 5038 与 OPPO Connect 5037 的 USB/设备/mDNS 均为空，两 adb 进程只分别监听 5038、5037。云盘上传的扩展本地文件权限/原生应用授权阻断尚无解除证据，本轮不重复触发授权。下节 9d49be96 只作历史产物，**不可继续当作用户当前要求的交付包**。完成后补记构建及修订 APK；未提交、推送或发布。

## 2026-11-05 桌面分页条、按钮投影与持久快照（历史包；快照改造已按上节撤回）

用户在 355e 闪退修复版后追加：恢复两个圆形按钮颜色；“项目里所有的带表示滑动进度的页签栏”改为可交互 ColorOS 原生组件；组件设置页也加光效、删除操作提示行；动画速率对齐系统，渐进照亮并覆盖圆角、照亮按钮。后续指定桌面 APK 的分页条完整材质/交互、按钮下原版投影、组件光宽更贴合且下沿缩短、更大投射角，并报告久未打开的持久快照颠倒。用户明确“这次做完放云盘”；此前“做完安装到adb”授权持续有效。继续保留 `codex/rescue-import-materials` 未提交工作树；未提交/推送/发布。

第一版 `a32975f3…`（7,909,657 字节、1.2.8_beta2 / 36）已通过完整签名构建和 8 项回归，2026-11-05 00:57:57 由 SDK 5038 USB 向 PLJ110 / Android 17 / SDK 37 覆盖安装 Success，设备 APK 哈希一致；保全 `tmp/review-apks/SleepDown-1.2.8-beta2-native-light-pages-20261105-a32975f3.apk`。包含恢复圆按钮颜色、组件投光/删除提示、650ms 原生展开和独立 1000ms alpha、圆角覆盖和按钮受光映射。

第二版新增 Wallpapers 原资源按钮投影、uAngleX=99、组件按宽度映射/有限深度与左右渐隐。分页条已从 COUIPageIndicator 改为桌面 OplusPageIndicator 绘制边界 + Launcher 原生 TouchAnimatorHelper + COUI 模糊/描边/跟手光；整类依赖 Launcher ActivityContext，未直接实例化，不能对用户声称直接加载整类。支持连续拖动选页、抬手吸附、取消恢复、RTL、禁用取消原生按压。

快照捕获先冻结为不可变软件 ARGB_8888，显示与 JPEG 使用同组像素，AtomicFile 串行写；缓存 v5，旧 v4 保留但不读，无缓存用课程数据预览。不碰课程/Room/备份；尚未在手机重现“久未打开颠倒”，不盲目旋转旧图。实机 native 已证实 JNI malloc/memcpy，不可再断言光效会修改源 Bitmap。

第二版 14 项定向回归通过（光效7、分页4、快照3），完整签名 Release 8m10s 成功；补齐缓存文件打开失败的日志/返回后，快照3项复测及最终完整 Release 6m36s 成功（R8、资源压缩、lintVital、GitHub 金标、既有 V2 签名），最终日志 `tmp/native-light-launcher-snapshot-final-20261105.log`，单 worker/临时 4GB 堆。

最终 APK `tmp/review-apks/SleepDown-1.2.8-beta2-launcher-light-snapshot-20261105-9d49be96.apk`，1.2.8_beta2 / 36，7,909,685 字节，SHA-256 `9d49be96f1aea1c35c809a79e0ebc2a4198cba7ae3d0e9387809918590d656c0`，证书 SHA-256 `0f2b50df…`。汇总 `tmp/native-light-launcher-snapshot-delivery-20261105.json`。最终重查 SDK 37.0.1 的 5038 与 OPPO Connect 36.0.1 的 5037：USB/设备表/mDNS 均为空，两 adb 进程只分别监听 5038、5037；**本包未安装、未启动，实机输出与长期冷缓存颠倒仍待验证**。手机最后确认安装的是 a32975f3，不能混淆。

**云盘未上传成功**。已登录 Edge 打开原 Drive 目录 `1NyxeLWGlDNmHmh2aqTgs4hwkB6O_Hnb4`（当前用户所有、未改私有权限）。浏览器文件选择器 `setFiles` 明确要求 Edge ChatGPT 扩展启用 Allow access to file URLs；随后依 computer-use 技能尝试原生文件选择器，`get_window_state` 返回 `Computer Use app approval timed out`，未操作或改变扩展/系统安全权限。回读云盘无 9d49be96 文件或上传完成状态，页签已标记 handoff。待用户完成必要工具/扩展授权后，只上传上述最终 APK；不要报告已上云。用户上传及安装授权持续有效，无需再问业务授权。详见[本轮报告](reports/2026-11-05-native-page-indicators-and-card-light.md)。全仓架构检查既存 DayAgentProgressBar 的 feature→platform 未改。

## 2026-11-04 多课表管理页原厂卡片投光接手（355e 闪退修复已安装，后续见上节）

用户本轮原话：“帮我接手，让多课表管理页正确调用这个光效”，随后“做完安装到adb”，安装后反馈“这个光效打开闪退，看看日志”。安装及必要的修复更新已获授权。当前仍在 `codex/rescue-import-materials`，保留已有未提交工作树；未提交、推送、开 PR 或发布。

已将上一轮 Compose 压缩/模糊/扇形光带替换为真实系统调用：新增 `platform/coloros/ColorOsCardLight.kt`，从已安装且经系统标志校验的 `com.oplus.wallpapers` 加载 `com.oplus.wallpapers.personal.views.WallpaperShadowView`，使用原厂 COE、native 库和 `shadow_card.coz`。View 与 applicationContext 的 assets/resources/classLoader 成对指向系统 APK，保留宿主身份；高度读 `wallpaper_shadow_height`（原厂 136dp）、宽度由原生 View 测量。无系统组件时记录诊断并省略附加光效，不打包系统二进制，也不再用切片模拟。注意临时反编译 Java 顶部的包名被改写过，实际类名以原始 dex descriptor 核实。

公共 `NativeCardLightRequest` / `ControlMaterialRenderers.cardLight` 只由凝光 provider 提供。`ScheduleCardGlow` 改为转发，不再直接读偏好或引用 platform。接入仍只在首页实际打开的 `SchedulePickerOverlay`，不是遗留 `ScheduleManagerActivity`；native host 覆盖整个浮层并位于卡片/按钮之前，按真实卡片底边定位，避免 pager 裁掉光带。切页停稳且选中目标匹配后渐亮，拖动/编辑/删除/退出淡出；图片仅来源改变时上传，生命周期及显隐调用原厂 `d()/e()`。detach 后释放且不复用已销毁的 COE，不回收课表快照。

底部三按钮新增显式 `nativeLightBackdrop` 请求，调用 `OplusViewBackgroundRenderEffect.setBackgroundRenderEffect(createBlurEffect(30f,30f,MIRROR), view)`，清掉会挡光的普通 COUI 模糊/颜色层，再保留原有描边、跟手光、触摸和内容。白色 `#4DFFFFFF → #1AFFFFFF` 着色在投光淡出时仍保留，保证滑动时按钮可见。普通胶囊不进入此分支。Manifest 增加壁纸包可见性查询。详情及历史实现见[报告](reports/2026-11-04-schedule-manager-card-glow.md)。

首包 4 项定向测试、完整签名 Release 均通过（10m10s，单 worker/临时 4GB 堆，无构建配置改动），`1.2.8_beta2 / 36`、7,893,273 字节、SHA-256 `7bab36cc1458863bd3395764a736d5b0da6f1704608b2d1229f98e94e0e277c5`，既有 V2 签名。按新指令通过 SDK 客户端/同来源 5038 USB 服务（37.0.1 / 15733141）向 PLJ110 / Android 17 / SDK 37 / `3B15AE023YL00000` `install -r` 返回 Success；未清数据、未由助手启动应用。已同时核查 OPPO Connect 5037（36.0.1），无设备；两通道 mDNS 均无端点。首包保全 `tmp/review-apks/SleepDown-1.2.8-beta2-native-picker-light-20261104-7bab36cc.apk`，**该包打开光效会崩溃，不可作为验收包**。

用户打开后真实堆栈：23:23 多次 `COE_Render` / SIGSEGV / `Material::setTexture` / 空地址 0x54，落在系统 Wallpapers APK 的 `libnaiveEngine.so`。该原生崩溃不受 Java try/catch 保护。手机壁纸组件为 `17.04.0034 / 17040034`，本机原调查样本为 `17.04.0037_sp3`；已只读拉取手机系统 APK 到忽略目录，原始 dex 核对类、构造器、六个入口均匹配，`shadow_card.coz` SHA-256 两者相同（`22fe9bca…`）。实机 `.so` 只导入 `AndroidBitmap_getInfo/lockPixels/unlockPixels`，不支持 HardwareBuffer。课表快照源为 Compose `GraphicsLayer.toImageBitmap()`，直接传入 HARDWARE Bitmap 不符合这版纹理入口。

软件 `ARGB_8888` 转换和 Surface 首帧门控已修复本次原生闪退：6 项定向测试通过，完整签名 Release 8m21s 成功，日志 `tmp/schedule-picker-native-light-crash-fix-20261104.log`。`355e15d7bfa2f0ac5c1d301d91b50355b0f24dba68f3907d4bea4ff104d9f969` / 7,893,273 字节 / `1.2.8_beta2 / 36`，既有 V2 证书；已通过同一 SDK 5038 USB 向 PLJ110 覆盖安装 Success，设备 APK 哈希一致，lastUpdateTime=2026-11-04 23:55:10。为复测已启动原 App，实机原浮层能正常显示真实光带，用户反馈“可以不赖”。APK 保全 `tmp/review-apks/SleepDown-1.2.8-beta2-native-light-crash-fix-20261104-355e15d7.apk`，用户询问包位置时已提供链接。上节追加项尚未包含在该包中。未清数据、未提交/推送/发布；其他任务记录保留。

## 2026-11-04 周视图顶栏切周动效与助手卡片伸缩（已打包，未安装）

用户要求周视图滑动切周时，顶栏变化内容使用轻微缩放、叠化、动态模糊淡入淡出；今日助手卡片切换高度时无缝伸缩。随后多轮纠正：左上角“第 X 周”后面的矩形黑框要去掉；缩放只作用于变化的文字，不能缩放整个表头；5 天↔7 天要带模糊平移；叠化+模糊会闪烁、黑框在动画途中仍会闪；顶栏所有文字都要当共享元素——字一样但位置不同做非线性平移，字不一样做叠化，两者同时发生就一边平移一边叠化。

最新实现：`WeekHeaderCrossfade` 保留第一版观感（280ms 立方缓动、alpha 叠化、文字 2% 缩放），旧内容改为组合期同步取（`remember(target)` 读上一次已提交的 target，进度随 target 重新置 0，`SideEffect` 提交），消除“新字先满透明度闪一帧”；两层副本统一 `LocalHomeTextShadowScale = 0.5`，避免阴影翻倍在左上角压出半透明暗矩形；单条小文字不再挂 `RenderEffect`。星期列改为 `WeekdayStrip`／`WeekdayStripTransition` 共享元素：一次变化由 from/to 两个 strip 和 0..1 进度描述，进度只在布局/绘制 lambda 读取；自定义 `Layout` 用累计取整给出目标槽位，列在绘制期按自身宽度算位移，两态都有的列平移、列内文字叠化，只在新态的列从最近顶栏边缘滑入淡入（5→7 时周六、周日从右侧进入），只在旧态的列反向滑出；整行在第一版位置再挂一层 `4dp × (1 - |2f - 1|)` 动态模糊，端点 0。`WeekPagerHeaderLabels` 按 `pagePosition - basePage` 跟手插值，不再叠两整行或整行 20dp 平移。`HomeDateHeader` 的日期大标题也走同一叠化。（上一版的方向性拖影 `990d69e4` 观感不可用，已删除。）

`DayAgentCard.kt` 当前内容的高度动画从 320ms tween 改为 `spring(dampingRatio = 0.9f, stiffness = 380f)`；旧内容的 1.25s 爆发副本从 `fillMaxWidth` 改为 `matchParentSize`，只作为淡出/模糊覆盖层，不再把旧高度保留 1.25s 后突然掉高。卡片外壳、`dayAgentCardSurface`、原生/液态玻璃和文字裁切继续随内容尺寸伸缩。

`compileGithubReleaseKotlin` 通过；最终完整签名 `assembleGithubRelease` 通过（4m23s，R8、资源压缩、lintVital、GitHub 金标），`1.2.8_beta2 / 36`，7,893,241 字节，SHA-256 `4d1fad21b817cb50d6bb634c388526a1ccee3498759e02f1d175b858c7e16ba1`，签名证书沿用既有 `0f2b50df…`；保全 `tmp/review-apks/SleepDown-1.2.8-beta2-week-header-move-20261104-4d1fad21.apk`（与下方锥光外描边／扩散修正同包）。当前 5038、5037 与 mDNS 均无设备，未安装；闪烁、左上角黑框、列平移手感、整行模糊强度和卡片伸缩均待真机确认。未提交、推送或发布，保留现有未提交工作树。详见[本轮报告](reports/2026-11-04-week-header-motion-and-agent-card-resize.md)。

## 2026-11-04 液态玻璃锥光进度条（已打包，未安装）

用户先澄清昨晚接入的是 `COUIIntentSeekBar` 的锥光（conic light）效果，不是普通 `COUISeekBar` 的 Glitter 拖动闪光，并明确可复用 `E:/COUI` 的反编译框架。本轮读取 `E:/COUI/work/src7/sources/com/coui/appcompat/seekbar/` 的 `COUIIntentSeekBar`、`IntentConicProgressRenderer`、`ConicLightEffectRenderer`、`ConicLightShaderParams`、`ConicLightPaintHostDrawer` 与 `plugin-resources.txt`，原厂调用点为 `setUseMaterialEffect(1)` + `setConicLightEnabled(true)` + `setConicLightColor(accent)` + `setPlaybackState(PLAYING)` + `setShowThumb(false)` + `setProgress`。

新增 `feature/agent/AgentConicLight.kt`：逐 token 搬运原厂 AGSL（2767 个非空白字符核对一致），使用 `RuntimeShader` + `SCREEN`，复刻 450px 高、300px 水平留白、500px 光斑、height 0.6 / falloff 0.6 / coneK 0.2 / w0 0.008 / alpha1 1.0 / alpha2 0.6，以及形状 900ms、透明度 300ms 的入场；API < 33 或 shader 构造失败使用紧凑径向光。修改 `feature/agent/DayAgentProgressBar.kt`：凝光模式继续用原生 `COUIIntentSeekBar`；液态玻璃模式用 Compose 复刻 4dp 轨道、2dp 圆角、15% 前景轨道色、强调色实心进度、整条轨道 1/5 宽的透明→白亮边（进度 >20% 才出现）；无进度仍回到 1dp 分割线；RTL 镜像；不改卡片布局高度。液态模式的锥光与亮边采用与凝光原生相同的数值，按用户要求统一。

用户随后澄清“光感描边”指今日助手**外卡片**，不是进度条头部亮边；并反馈复刻进度条的光尾被布局裁切。已新增 `NativeStrokeOverlayRequest`／`LocalNativeStrokeOverlayRenderer`，在液态模式仍只提供 `strokeOverlay`：`ColorOsCardOuterStroke` 复用 `NativeMaterialSurface` 的 Dock 样式，只开 `TYPE_FRAMEWORK_CIRCLE_2` 原生外描边，关闭模糊、着色和跟手光；`rememberColorOsStrokeContext` 不依赖凝光模式。`DayAgentCard` 在液态 + ColorOS 可用时绘制该原生描边并关闭 Kyant 高光，保留共享 `verticalGlassAccent`；非 ColorOS 继续用 Kyant。进度条删除轨道中心上下 12dp 的显式 `clipRect`，并把卡片 `expandVertically`/`shrinkVertically` 改为 `clip = false`，锥光只受卡片圆角约束，可向轨道上下弥散。

`compileGithubReleaseKotlin` 通过；最终完整签名 `assembleGithubRelease` 通过（4m15s，R8、资源压缩、lintVital、GitHub 金标），`1.2.8_beta2 / 36`，7,893,241 字节，SHA-256 `f35047aaf51048f76b095fad8ce58a1d1dfd9f7a6a579714def976038369c2b5`；保全 `tmp/review-apks/SleepDown-1.2.8-beta2-week-header-card-motion-20261104-f35047aa.apk`（与上方周视图／卡片伸缩同包）。当前 SDK 5038、默认 5037 与 mDNS 均无设备，未安装、未做真机截图对比；液态模式外描边、锥光扩散与凝光一致性待实机验收。未提交、推送或发布，保留现有未提交工作树。详见[本轮报告](reports/2026-11-04-liquid-glass-conic-progress.md)。

## 2026-11-03 云端短口令、JSON 分享与后端更新（后端已部署，APK 未安装）

用户要求保留现有工作树，先调查短口令，随后改为“去掉第二个口令，唯一云端解密短口令”，并明确“后端利用本机阿里云cli更新”。已移除分享面板的“课表口令”，只保留云端短口令、JSON、ICS 三项：短口令为十位 Crockford Base32（展示 `SD-XXXXX-XXXXX`），上传紧凑 canonical JSON 到 `POST /api/v1/short-codes`，服务端主密钥加密落盘、15 分钟后删除，接收端 `POST /api/v1/short-codes/resolve`（口令在 JSON body，避免代理日志记录） 由云端解密后本地预览，不调用 AI。旧 `SDCT1`/canonical/ICS 导入兼容保留；上一版本地 `ShortScheduleCode` 已删除。

私有后端新增 `internal/shortcode/` 文件存储（默认数据库目录 `short-codes`，0700，AES-256-GCM 主密钥，创建/读取按 IP 限流，无数据库迁移），已用本机阿里云 CLI 部署候选 `0b8730b2…`，标签 `sleepdown-platform-20261103-195130-44fb29`，备份 `/var/backups/sleepdown-server/sleepdown-platform-20261103-195130-44fb29`；远程校验候选哈希、schema 8、真实库副本未变化，服务 active；生产接口创建/读取/404 与 `/healthz` 实测通过。客户端 `compileGithubReleaseKotlin` 通过，`CloudShortCodeTest` 5 项通过；`go vet`/`go test ./...` 通过。口令部分已按开发流程提交 PR [#100](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/100)（head `3478522e`，PR checks 全部 success）；PR 只含短口令，JSON 文件分享仍留在当前未提交工作树，待后续独立 PR。

已从当前未提交工作树执行完整签名 `assembleGithubRelease`（3m45s），包 `1.2.8_beta2 / 36`，7,876,857 字节，SHA-256 `0d78955519a7863d8a4da060d8f2ce280efe18ab032ed3d788a824fb9a01f9af`；经 SDK 5038 / 唯一设备 PLJ110（Android 17 / SDK 37 / `3B15AE023YL00000`）`install -r` 返回 Success。设备 `base.apk` SHA-256 与本地一致，`lastUpdateTime=2026-11-03 22:28:57`，未清数据、未启动应用。该安装包包含短口令与 JSON 分享，不能用 PR #100 的 head 直接对应；PR 仍只含口令部分。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-cloud-short-code-20261103-0d789555.apk`。未提交其他工作树改动、未推送/发布，真机分享面板验收待用户操作。详见[报告](reports/2026-11-03-short-passcode-and-json-share.md)。

随后将分享弹窗改为四项等间距（`SleepDownPickerDialog`：分享短口令 / 分享 JSON 文件 / 分享 ICS 文件 / 取消，50dp 等高、10dp 等距、无长说明），`compileGithubReleaseKotlin` 通过；按用户要求本轮**未打包、未安装**，手机仍是上述 `0d789555` 旧包，改动留在未提交工作树。

## 2026-11-03 当前脚本保存与小窗惯性（已打包上传，待手机重连安装）

870dd666 包已按下节安装上传后，用户要求查看最新截图。已仅拉取 13:04:19、13:04:26、13:05:06 三张同 App 已保存截图：能看到真实“编辑本次任务脚本”工具行与最终 2 门课／4 节作息／18 周预览，运行码 BFCCE0、时间和周次与本机样本一致；没有脚本源码或完整运行工具结果，尚不能逐行认定脚本正确。前台为 Launcher，未启动应用、未展开其他 UI 或操作保存课程。截图只在忽略的 tmp 下。

最新要求：预览下增加“保存当前脚本”入口，并严格按拾光仓库格式保存；小窗在顶栏／输入区间内拖动有轻微滞后，松手惯性滑行、碰边衰减回弹。关于推理显示，用户最终澄清为“国内模型与现在 GPT 展示对齐”，摘要保留，取消旧独立走字框；不要误作删除全部思考摘要或改 transport。当前导入已统一用普通进度文本，不应重引入特殊框。

本轮重新完整读取 shiguang-edu-adapter skill／仓库契约，通过 GitHub plugin 回读 pending README（78bcc8b6691d23bc9eeb2a42a2c2e6b8a7b14f2c）、adapters schema（54ce1ee80dc6e92008765608f989e835c2a6d480）、root schema（281e316942114ac51f91854d11caa28090e8bc3b）和官方 proto（99c8da34f6e220ae0171e1e0622313c36b7ac6b1）。root 四字段／initial 单大写字母、adapter 七字段、v2 proto 字段／枚举已核实；不提交学校或推送仓库。

已完成预览下显式保存／查看源码、确认名称与登录入口、实际 RUN_SCRIPT 成功后的任务／路径／哈希候选保留、编辑失效与旧任务隔离。保存独立于课程写库，成功后禁用重复操作；候选仅当前进程保留，旧历史未记录的源码需重新执行。新增 LocalEduAdapterPackage，原子写单校仓库 ZIP（index/root_index.yaml、resources/LOCAL_…/adapters.yaml、原样 adapter.js、school_index.pb），复用 v2 parser，旧 JSON 适配兼容读取。D 盘忽略目录内的 Python 验证环境已安装；真实 Kotlin 测试生成包通过官方 YAML schemas，并用官方 build_data.py／生成 proto 验证索引字节完全一致。小窗已接入有界弹簧跟随、惯性／碰边衰减回弹与键盘／窗口改变时停止旧运动。摘要共用当前 GPT 对话组件，保留 transport 和历史，没有删除思考摘要。

最终 10 类 40 项回归全部通过，0 失败／错误／跳过，完整签名 assembleGithubRelease 同次 7m32s 成功，日志 tmp/ai-script-save-window-motion-final-20261103.log。首轮 39/40 的真实问题为公共玻璃确认按钮未暴露禁用语义，已给 LiquidAlertActionButton 传递 enabled；未弱化测试。架构 631 文件／0 违规依赖，diff 通过。新包 1.2.8_beta2 / 36，7,876,857 字节，SHA-256 ad5da225aaa33250d63e497c6b83b1703c10fa8ee5d3f445c7a11057ef99711f，既有 V2 证书；保全 tmp/review-apks/SleepDown-1.2.8-beta2-script-save-motion-20261103-ad5da225.apk 与同名 JSON，汇总 tmp/ai-script-save-motion-delivery.json。[本轮新包](https://drive.google.com/file/d/1Z-RA0sKNarXoQqujQewrV-xpI0eLufMS/view?usp=drivesdk) 已上传并回读名称／大小／父目录；首次自动审核要求核对目录归属，随后通过 me-in-owners 搜索确认原目录为当前用户私有且 shared=false，再原工具上传成功，未改共享权限。详见[本轮报告](reports/2026-11-03-ai-script-save-window-motion.md)。

本轮尚未安装：14:20 左右重查 SDK 5038 与 OPPO Connect 5037、全部现有 adb 监听、USB 和两端 mDNS，均无设备；已异步请用户接回原 PLJ110。安装授权来自此前“打包装 adb”，重连无需再问授权，需重新核对实际服务端和目标，覆盖安装后比对设备 APK 哈希，不启动或清数据。当前手机仍是下节 870dd666 包，不能写成 ad5da225 已装。测试服务此前 PID76024／BFCCE0 为用户保留，接续核对实际进程及重建 reverse；手机断连后不要沿用旧连接结论。未提交、推送或公开发布，未以真实学校／付费模型端到端验收新改动，也未实机评估新惯性手感。

## 2026-11-03 AI 教务轻量 DevTools（已安装与上传）

用户要求看最新报错截图，后续明确增加可拖动／收起的网页小窗、胶囊退出占位平移、已适配学校直接导入，以及去掉网页预抓长图和截图／文本选择，交给模型按需浏览；要求保持轻量，无整套 harness／CLI。已从真实后台入口复现并修复 READ_PAGE 构造读取器时在后台访问 WebView.url：现在 UI 绑定、后台调用、Main 执行，保留原文档与任务约束。

同一个已登录 WebView 可借入当前会话小窗，收起仍保留非零视口，退出归还，源释放撤销，旧宿主和旧源不能影响新任务。统计／附件／候选退出收缩实际占位，候选按稳定 id 做删除／平移。网页点击直接启动调查，删除材料选择和自动批量截图；已适配学校直接执行原适配，AI 入口放更多操作且继续优先本机适配。EDU_WORKSPACE 新增 PROBE_SCRIPT（最新文件哈希、异步函数体、20s、受限结果／日志）、SCROLL（可选区域）和仅视觉模型的 VIEW_PAGE（单视口／裁切）。复用现有循环与 Bridge，无新框架／CLI／依赖；不是被动保存所有请求正文的完整 DevTools。预览与用户确认写库、文件图片导入兼容保留。

最终 6 类 26 项 Android 回归全部通过，0 失败／错误／跳过，完整签名 Release 同次 7m05s 成功，日志 tmp/ai-edu-devtools-final-20261103.log。Robolectric WebView 的测量／隐藏 setFrame 委托在测试 shadow 中补齐，保留真实非零视口、拖动、归还及撤销断言；未修改生产渲染来绕过测试。5 项新 JS 检查和既有 Bridge／loopback 合成教务检查通过；架构 628 文件／0 违规，diff 通过。未执行真实模型／学校端到端，详细过程见[报告](reports/2026-11-03-ai-edu-webview-thread.md)。

1.2.8_beta2 / 36，7,844,089 字节，SHA-256 870dd666bd87bb1cfb0f498c5caedcccf609178b13114b4171613a560f82bd5f，既有 V2 证书。2026-11-03 13:00:12 经 SDK 37.0.1 客户端与同来源 5038 USB 服务向原 PLJ110／Android 17／SDK 37／3B15AE023YL00000 覆盖安装 Success，手机 APK 哈希一致，未清数据或启动应用。此前无设备时已查 5037／5038／mDNS；用户随后重连，实时发现设备在 SDK 5038，未沿用旧 OPPO 5037 通道。保全 tmp/review-apks/SleepDown-1.2.8-beta2-edu-devtools-20261103-870dd666.apk 与同名 JSON，汇总 tmp/ai-edu-devtools-delivery.json。[新包](https://drive.google.com/file/d/1B3vtldC8n-x_jQ2NTrlM1Hm3fPEXpDZR/view?usp=drivesdk) 已上传原 Drive 测试目录并回读名称／大小／父目录，未改共享权限。未提交、推送或公开发布。

本机测试服务 PID 76024 与命令行已复核，运行码 BFCCE0；恢复当前手机 USB reverse 后实际回读 HTTP 200、2 门课／4 节作息／18 周。App 地址栏使用完整 http://127.0.0.1:18765/，点击 AI 导入直接开始；不再有开始检查／截图文本选择。服务为用户复测保留，结束时按[验证说明](ai/AI_SCRIPT_SMOKE_TEST.md)核对精确进程并移除本端口转发；接续必须重查 PID、服务端来源与设备通道。下方 9b6e849b 为旧包，不能混作本次结果。

## 2026-11-03 胶囊入场与临时脚本验证（已安装、测试服务就绪）

用户要求底部胶囊／按钮轻微放大、动态模糊入场，并需要临时写脚本的验证方法；随后指定平板统计胶囊也移到输入框上方，两端统一。已新增 `softBlurEnter`（260ms、94%→100%、6dp→0 模糊／渐显，稳定后移除 layer 与 effect），接入统计／附件／候选／输入胶囊及“回到最新”按钮，内容刷新不重入场。已移除统计位置的宽窗口分支，并调整既有平板断言。

新增仅供本地的 `scripts/ai-edu-smoke-server.py`、参考脚本与 Node 真实 loopback／生产 Bridge JS 检查，以及[验证说明](ai/AI_SCRIPT_SMOKE_TEST.md)。页面不显示课程和随机运行码，模型必须查询实际接口、写／读／编辑文件再运行标准拾光脚本；合成数据不代表真实学校通过。两组 Node 检查通过；3 类 11 项 Android 定向检查及完整签名 Release 同次 8m50s 通过，0 失败／错误／跳过，日志 `tmp/ai-capsule-enter-and-script-smoke-20261103.log`。架构 625 文件、0 违规依赖，diff 检查通过。

版本 `1.2.8_beta2 / 36`，7,844,089 字节，SHA-256 `9b6e849b8d9aed551e5664eaff42e3941678d3bc623a864f74ff443b32dafdaa`，既有 V2 证书。已按此前授权经 SDK → OPPO Connect 5037 USB → 原 PLJ110／Android 17／SDK 37／`3B15AE023YL00000` 覆盖安装 Success，手机 APK 哈希一致，未清数据或主动启动应用；5038、5037 与 mDNS 均已检查。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-capsule-enter-20261103-9b6e849b.apk` 及同名 JSON；[新包](https://drive.google.com/file/d/1NrZV9lxcQyNyygxTtiA3O1B5_O0ywPzR/view?usp=drivesdk) 已上传原 Drive 测试目录并回读名称、大小、父目录，未改共享权限。未提交、推送或公开发布，未调用真实模型或学校作端到端验收。完整记录见[报告](reports/2026-11-03-ai-capsule-enter-and-script-smoke.md)。

测试服务特意保留供用户验证：后台 Python PID `76024`（接续须核实），本机 `http://127.0.0.1:18765/`，运行码 `BFCCE0`，`tmp/ai-edu-smoke-server.pid` 与对应 out/err 日志；通过上述 PLJ110 的 USB reverse 转发，已从手机实际读取 HTTP 200、2 门课／4 节作息／18 周。结束时只停止核对过命令行的本仓库测试进程并移除此端口转发，步骤见验证说明。用户反馈在 App 教务地址栏打不开，诊断读取到当前实际地址为 `https://127.0.0.1:18765/`；服务和转发正常，已告知改为完整 `http://127.0.0.1:18765/`。没有为测试服务改动 App 全局默认 HTTPS 行为。诊断 UI 树只在设备临时文件和进程内处理，已删除，未保存用户页面原始数据。

## 2026-11-02 手机统计胶囊位置更正（已安装）

用户纠正：手机统计胶囊应在输入框上方，平板继续在下方。`AiImportAdaptivePage` 将已有宽窗口判断传给会话组件，同一统计胶囊按布局放在 composer 前／后；高度继续计入滚动避让与键盘占位。此次只改位置，不追加机械测试；完整签名 `assembleGithubRelease` 5m27s 通过，日志 `tmp/ai-mobile-capsule-position-release-20261102.log`，架构与 diff 检查通过。

沿用本聊天打包／ADB 安装授权，已经 SDK 客户端 → 原 OPPO Connect 5037 USB 服务 → PLJ110／Android 17／SDK 37／`3B15AE023YL00000` 覆盖安装，返回 Success，手机 APK 哈希一致；5038 与两条 mDNS 也已重查，无其他设备，未清数据或启动应用。版本仍 `1.2.8_beta2 / 36`，7,844,089 字节，SHA-256 `77a5cc454265a05a1329fe4bc7396c55ae62ef866de4ac452a4dcb6a3b057774`，签名证书与前批一致。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-mobile-capsule-20261102-77a5cc45.apk` 与同名 JSON；[位置修正版 APK](https://drive.google.com/file/d/1ncToPdnezscnEmEkoVovKY-RVBzuqoqA/view?usp=drivesdk) 已上传原 Drive 测试目录并回读名称、大小与父目录，未改共享权限。下方 `229798ca` 为更正前已安装包，60 项测试为此前整合验证，本次未重复运行。未提交、推送或公开发布。

## 2026-11-02 胶囊、多选、平板布局与教务入口修正（已打包安装）

用户最新明确：信息合为一条图标胶囊并启用柔性反馈；发送中的补充直接进入候选胶囊，内含编辑／删除／引导三个图标，去掉发送前两个模式选项；附件居中并启用柔性反馈。又反馈教务入口直接发文本，无法试接口。以上已接线：教务入口只发送脱敏地址／标题，点击“开始检查教务”后由模型主动调查；`EduImportEvidence` 阻止未调查直接交课表，可由模型说明 PAGE_FALLBACK 依据。详情见[本轮记录](reports/2026-11-02-ai-capsules-and-edu-entry.md)。

用户继续追加的多图、多文件与平板布局均已接线：`AiImportBatch`／多文件材料目录与 file 参数，多图候选与完整 marker 历史、备份的全附件数组；宽窗口左侧常驻历史，右侧正文与输入居中，选中后才读取该历史完整图片；统计胶囊移到输入框下方。停止后候选编辑和容量边界也已修正，连续追加图片不能绕过 20 张／40 MiB 上限。详情及实现边界见[本轮记录](reports/2026-11-02-ai-capsules-and-edu-entry.md)。

最终 13 类 60 项定向测试通过，0 失败／错误／跳过，3m36s；含平板实际切换历史再回当前运行任务与统计位置验证。完整签名 `assembleGithubRelease` 5m30s 通过，保留 R8／资源压缩／lintVital／金标 true；日志分别为 `tmp/ai-multi-adaptive-tests-complete-20261102.log`、`tmp/ai-multi-adaptive-release-final-20261102.log`。长期 JVM 曾原生内存不足，后改用 2560m／4 核／单 worker 的独立进程；没有进行清库或设备清理。所有构建已结束，架构 624 文件、0 违规依赖，diff 检查通过。

用户明确“打包装 adb”后，已通过 SDK 客户端 → OPPO Connect 5037 USB 服务 → 原 OPPO PLJ110／Android 17／SDK 37／`3B15AE023YL00000` 覆盖安装，ADB 返回 Success，手机 base.apk SHA-256 与本地候选一致。此前已检查 SDK 5038 与两条服务 mDNS，无其他可用设备；未抢占其他服务、清数据或主动启动应用。版本 `1.2.8_beta2 / 36`，7,844,089 字节，SHA-256 `229798ca58614a057c0db830f85bdcc3018afdc7539e0b8a905b19d93ef857bd`，V2 证书与前批一致。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-multi-tablet-20261102-229798ca.apk` 及同名 JSON。[本轮 APK](https://drive.google.com/file/d/1Te_S8YHPt6WHvMq3PdoEwtwkVdZ7K06N/view?usp=drivesdk) 已用 Drive 插件上传原测试目录，回读确认名称、大小和父目录，未改共享权限。尚未真实学校／付费模型／ChatGPT 账号端到端或手机视觉／触觉验收，未提交、推送或公开发布。下方 d4f95ae2 为上轮产物。

## 2026-11-02 AI 工作区整合（最新进度，覆盖下方阶段待办）

当前仍为 `codex/rescue-import-materials` 未提交救援树，保留其他所有既有改动。已实际接线统一循环、插嘴／队列／编辑撤回及停止恢复、默认 256k 上下文、材料分页／原图裁切、任务文件编辑与隔离 JS、当前教务页面／同源接口／标准拾光脚本工作区、本地自用适配保存管理、AI 入口本机适配优先，以及特殊课程局部补丁。下方“尚未实现”的文字为本轮前期状态，不能据此重做。当前实现与边界见 [整合报告](reports/2026-11-02-ai-harness-workspaces.md) 和 [架构](ai/AGENT_HARNESS.md)。

新增 `AiTaskWorkspace`／`AiEduWorkspace`：普通数据 JS 与当前 WebView 的标准拾光脚本分开；只有用户确认材料且开放 DOM 的教务任务具有网页工具，只发截图不开放。生成脚本的 Bridge 输出先返回模型复核，最终仍需预览确认；成功且源码哈希不变才可建议保存自用适配。保存由用户点击，学校页单列本地来源、可编辑／测试／删除。已有官方／自用适配匹配在模型请求之前，按确切 origin，多个候选本机选择；重新加载目录以涵盖刚更新和刚保存的适配。

最终验证：21 类 142 项回归通过（3m02s，0 失败／错误／跳过）。随后为本地适配补了 AtomicFile 中断恢复，并追加脚本到 Native 的 executionToken，阻止停止后的旧异步脚本污染新任务；标准 Bridge 协议不变。三组 Node 检查（含 HNSF 学年校验、新旧异步标识、POST 桥接、自动刷新会话脚本）通过。最终 Bridge 8 项／存储 2 项及完整签名 `assembleGithubRelease` 同次通过（6m54s，192 个任务），保留 R8、资源压缩、lintVital 和默认金标 true，日志 `tmp/ai-harness-final-package-20261102.log`。架构 621 文件、0 违规依赖，diff 检查通过。所有构建均已结束。

已交付：`1.2.8_beta2 / 36`，7,825,484 字节，SHA-256 `d4f95ae211c37bdd7995f24194bd1963d355b4ac96e67cd93c66c34f825dd183`，V2 证书与前批一致。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-harness-20261102-d4f95ae2.apk`；[下载最新 APK](https://drive.google.com/file/d/15gaclGmROhdN2NSD_5ITCSowzanKb0JQ/view?usp=drivesdk)。Drive 插件上传后已回读名称、大小和原父目录；未改共享权限。未调用真实学校／付费模型／当前 ChatGPT 账号做端到端验证，未实机验收、安装、提交、推送或公开发布。此前打包／上传授权已完成，保留源码供继续验收。

## 2026-11-02 全 App AI harness 与材料工具（进行中）

最新追加与执行边界：用户接受“参考成熟框架设计后 Kotlin 重写”，不要求移入 Codex／DSH 的所有功能；同时明确希望多借鉴插嘴、排队／覆盖、上下文管理，并指定默认 256k。`AgentMailbox.kt`、`AgentContextPolicy.kt` 已新增，当前收件箱尚未接入所有执行与 UI。上下文已接入 Chat／Responses 投影、恢复被压缩只读结果的工具可见性；默认容量已加到兼容配置。新增 `AgentTaskFiles.kt`（任务相对路径、版本哈希、精确编辑）和 `AgentJavaScript.kt`（AndroidX JavaScriptEngine 隔离进程），`app` 新依赖 `androidx.javascriptengine:javascriptengine:1.1.1`，依赖解析成功；文件／JS 工具目前尚未向模型开放。不要将这些骨架误写成已完成工作流。

新增完整待办：
- AI 导入与今日助手都支持插嘴／排队、编辑覆盖与撤回，消息／附件／操作用输入框外独立材质胶囊。核心 mailbox 需要实际接线与测试，保留历史中用户追加内容；失败／停止不得丢弃待处理消息。
- 核对 ChatGPT 账号 OAuth、模型能力、原生工具、上下文与文件流程，避免假定账号支持 Files 上传。增加 `ChatGptLocalToolsTest` 等针对性验证。
- 工具覆盖现有新功能，已确认“特殊课程”实际为 `domain/schedule/TimeSlotCourses.kt` 的 `TimeSlotCourse`，保存于 `ScheduleConfigEntity.timeSlotCoursesJson`，不是仅普通课程的自定义时间；当前 AgentSettingRegistry 没有它。可沿 SET_SETTING 的读取／规范化／预览／确认写入链路扩展并保留未改项，需核对最新编辑器字段。
- 教务流程让模型自主选择识别类型、复用官方拾光适配器、调用真实接口、现场写／编辑 JS、执行并结合 DOM/结果复核；现有“本机 DOM＋截图工具”尚不等于完整教务脚本工作流。请求不猜接口，不执行远端内联脚本，不泄露 Cookie。计划使用独立 JS 环境处理任务材料，接口由 App 当前登录态代理；最终脚本必须符合拾光 Bridge 协议，不能另造不兼容的 main(input) 适配格式。当前 AgentJavaScript 仍只是通用纯转换 runner，尚待适配这一点。
- 模型试运行脚本成功、结果经本地校验后，可以建议用户保存为本地自用适配器，由用户确认后落盘；适配列表单列“我的适配”，标明本地来源，并支持编辑／再试／删除。尚未实现存储／UI。
- 点击 AI 教务导入，先用本机更新后的仓库索引与自用适配明确匹配，提示后直接运行本机适配以省额度；多个候选先选，不明确或明确失败后才用 AI，不让同一教务反复付费解析。尚未实现新增匹配策略。

已实现追加：导入预览节次已复用连续区间合并（`compactPeriodsLabel`），只影响该预览；新增了周次／节次测试。

技能：本轮已读 PDF 两份技能参考其本地提取／渲染／分页思路。用户最新明确调用 `C:/Users/23085/.codex/skills/shiguang-edu-adapter/SKILL.md`，已完整读及 `references/warehouse-contract.md`，并已告知用户使用。已用 GitHub 插件核对官方仓库最新 README、Schema `scripts/schemas/adapters.schema.json`、`proto/school_index.proto` 及官方 Tester 的 `CourseImporterTestTool/shiguang-bridge.d.ts`。最新 Bridge 为 `window.shiguangBridgePromise`（异步 showAlert/showPrompt/showSingleSelection/saveCourseConfig/savePresetTimeSlots/saveImportedCourses/saveComboSchedule）和同步 `window.shiguangBridge.notifyTaskCompletion()`；旧 AndroidBridge 别名兼容。saveComboSchedule 需先成功保存基础作息；失败／取消不能宣称成功。元数据按官方字段，默认维护者“小漫君”，保留来源和 MIT 声明。本轮没有 GitHub 提交／PR／发布授权。

验证状态更新：首轮 Release Kotlin + Debug 109 项测试跑完，3 失败：AgentTaskLoopTest 的有界修复、AiInteractiveImportTest 的不可变请求历史和 Responses 摘要消息表示。已经按根因修正：请求 `JsonArray(history.toList())` 冻结、普通摘要续接维持 tool_choice 策略、保留原生消息并修正测试的多模态取文、格式自修复仍最多六次。随后当前会话 `78677` 正执行第二轮 Release Kotlin + 17 类定向测试，日志 `tmp/ai-harness-regression2-20261102.log`，包括新 mailbox/context/files/usage 与 ChatGPT、节次测试，尚未读到最终结果。此期间不要修改生产文件造成编译快照混杂。之前段落的 43706 会话已完成并失败，不再运行。架构扫描最近为 611 生产文件、0 违规依赖；新增文件后需重扫。

用户已授权尝试替换整个 App 的 AI 执行循环，参考 Codex／DSH 开源设计，并要求临时克隆仓库且不要放 C 盘。已浅克隆到本项目 `tmp/upstream-codex`（`3026a8e54e0f48c4d8074cd700f35b0697abc329`）和 `tmp/upstream-dsh`（`ce7073d51254c26cd63b22a60835b8f948deb447`），只检出循环／工具／会话及文档。未安装 Node/Rust 运行环境，也未提交外部源码。

当前未提交代码新增 `core/ai/AgentHarness.kt`、`AiUsage.kt`、`AgentProtocolHistory.kt`，已接入今日助手 Chat／Responses 与初次导入循环，统一步数／取消、同 call ID 重放和参数冲突处理。新增 `AiMaterialWorkspace`，提供 `READ_IMPORT_MATERIAL`（分段／PDF 分页文字）和 `VIEW_IMPORT_DETAIL`（原页 0–1000 坐标裁切）；初次导入及今日助手／导入修订都可使用材料工具。原始图片保留像素，预览单独压缩；PDF 在 Android 35+ 使用系统文本解码及区域渲染，旧系统保留既有文字提取与视觉路线。教务网页用固定本地 DOM 读取器按需取内容并核对文档版本，不开放模型 JS、Cookie 或任意网页请求。用户明确文件读取不写白名单、无需依赖上传，支持格式除图片均参考 PDF skill 在本地处理，必要时可评估手机终端；当前能力已用 Android 本地工具实现，尚未使用手机终端。原生文件发送仅为显式 `RESPONSES_FILE` 且配置能力支持的可选路线，移除原 NativeResponsesDocumentExtensions 名单；`AUTO` 优先本地。

同轮 UI：已将导入总读秒／识别提示接成输入框上方的独立材质胶囊，约每十秒在状态与可用 token／平均吞吐／上下文用量／缓存统计间轮换，容器伸缩、文字模糊淡入叠化；只展示接口实际统计，未提供容量不编造上下文上限。历史按钮增加显式明暗及前景取色、主题变化重录快照。以上实现尚在验证；首编译仅缺 `json.put` 导入已修正。当前 Gradle 会话 `43706` 正执行 Release Kotlin + 十二类定向 Debug 回归，日志 `tmp/ai-harness-regression-20261102.log`，尚无最终结果。

新增待做：①导入预览节次也合并连续区间；②整个 App AI 增加插嘴／消息排队，排队可查看撤回，附件和插嘴／队列操作做输入框外独立悬浮胶囊，不挤输入区域；③对照最新领域模型／编辑器补齐工具能力，重点特殊课程的读取、定位、修改和预览，不丢字段。这些追加请求尚未实现，不能在交付中写为完成。仍需定向测试、文档基线更新、正式打包／上传（此前“做完打包／网盘”授权持续）；未安装、推送或公开发布。以下链接均是本轮 harness 改造之前的已交付包。

## 2026-11-02 AI 导入步骤统一与预览精简

用户追加工具展开图标像字符、按压未裁胶囊、删除“课表选项”、连续周次合并，以及统一各类步骤显示。已实现矢量展开／工具标记和胶囊裁剪、结构化消息的就地分组与相邻重复去除、旧摘要／步骤仅补缺失渠道、底部仅展示未收录公开片段。预览移除阶段与 JSON 选单，继续对话使用最新可见结果；旧轮快照导入和历史格式保留。周次显示抽到领域层并复用编辑器既有连续区间规则，不改课程数据。六类 42 项回归通过（5m12s，0 失败／错误／跳过），完整签名 `assembleGithubRelease` 通过（5m23s，R8、资源压缩、lintVital 与金标 true），架构 605 文件、0 违规依赖，diff 检查通过。详见[步骤规则记录](reports/2026-11-02-ai-import-step-rules.md)，日志 `tmp/ai-import-step-rules-{regression,package}-20261102.log`。

新包 `1.2.8_beta2 / 36`，7,759,239 字节，SHA-256 `da209591eb6d9713a4331d2b16022c651fd653f974c1a74dcf65706135e8a136`，V2 证书与前批一致。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-step-rules-20261102-da209591.apk`；[下载最新 APK](https://drive.google.com/file/d/1TlkdhOSA1Zu_l8tYzMX-lQD-3SW-aVnR/view?usp=drivesdk)，已回读名称、大小与父目录，未改共享权限。上传前两次连接失败、浏览器备用通道也超时，最终第三次 Drive 插件上传成功。未安装、推送或公开发布，手机尚未验收。以下为前批产物。

交付期间用户进一步询问复用 DSH／Codex agent loop／harness、Codex 是否开源可复制。已用 GitHub 插件核对官方源码和许可：Codex Apache 2.0，DSH MIT／开发预览；对照现有循环，建议优先引入统一回合／工具事件、调用账本、取消／失败恢复和日志派生上下文，保留课表工具及本地校验。本轮为只读可行性评估，尚未执行 harness 替换，当前 APK 不含此改造。细节与来源见[初步评估](reports/2026-11-02-agent-harness-assessment.md)。

## 2026-11-02 AI 导入状态与中间消息

用户继续反馈状态的 Markdown 星号外露、中间信息不留在下方、字号层级与工具缩进不清、等待读秒应改为本次导入总用时。已修正实时 Markdown（包括流式未闭合加粗），补齐普通助手正文与公开原生摘要的完整段落记录、Chat／Responses 完成回放去重及分段边界。状态 13sp、工具 12sp 等宽、正文 16sp，预览元数据仍为 14sp；状态和执行文字统一 24dp 标记列。执行事件就地分组，最新运行组默认展开，完成默认折叠。单调任务总计时不随 token、工具或回调重绑重置；旧历史不显示伪实时计时。最终七类 77 项回归通过（2m56s，0 失败／错误／跳过），完整签名 `assembleGithubRelease` 3m54s 通过，保留 R8、资源压缩、lintVital 和金标 true；架构 606 文件、0 违规依赖，diff 检查通过。日志分别为 `tmp/ai-import-status-regression-20261102.log`、`tmp/ai-import-status-package-20261102.log`，详情见[状态与对话记录](reports/2026-11-02-ai-import-status-timeline.md)。

最新包 `1.2.8_beta2 / 36`，7,774,531 字节，SHA-256 `8eaf3cb8c44981268dd257f81a5fd5ef600d9cc2d455422eb44277aebd2d2500`，V2 证书与前批一致。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-status-timeline-20261102-8eaf3cb8.apk`；沿用此前做完打包／网盘交付要求，已上传既有目录：[下载最新 APK](https://drive.google.com/file/d/140CBY-xzvKL_JadJ7NCRyZvCBXMuyRKp/view?usp=drivesdk)，回读名称、大小和父目录，未改共享权限。包含本轮及前批救援／历史图片修改。源码保持 `codex/rescue-import-materials` 未提交工作树，没有安装、推送或公开发布；手机尚未验收。

## 2026-11-01 AI 教务导入连续对话与预览

最新交付：用户要求精简信息、恢复蓝色气泡、取消阶段卡片，每轮对话附原图，并明确历史也保留／重发图片，“做完打包”；随后追加输入框柔性反馈、日视图今日助手关闭末尾闪烁，最后要求“继续”。已完成，完整 `assembleGithubRelease` 与十类 63 项回归同轮通过（5m24s，0 失败／错误／跳过），保留 R8、资源压缩、lintVital 和金标 true。历史图片独立私有保存并接通 V1 资产备份恢复，详情与通知恢复按需加载；旧预览选择不移动后续结果，技术记录默认折叠。输入框复用公共弹性控件，隐藏源卡同时关闭其原生材质，待弹层完整退出再交还，并统一末帧圆角。架构 606 文件、0 违规依赖，diff 检查通过。详情及测试命令见[本轮记录](reports/2026-11-01-ai-import-history-and-feedback.md)，日志 `tmp/ai-import-history-feedback-delivery.log`。

新包 `1.2.8_beta2 / 36`，7,774,531 字节，SHA-256 `af9592df816d4320d05d8ff7d59ffd0b9b4f6b6451903f2d3d276ce2e3199246`，V2 证书与此前候选一致。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-history-feedback-20261101-af9592df.apk`；已上传既有测试包目录：[下载最新 APK](https://drive.google.com/file/d/1HYEvlCoA0lt7_74EMB8SW0tHRxd3np9q/view?usp=drivesdk)，回读名称、大小及父目录，未改共享权限。包含本轮全部修改与早前救援；未做手机 ROM 视觉验收，旧历史已经丢弃的图片无法补回。沿用 `codex/rescue-import-materials` 未提交工作树，无安装／推送／公开发布操作。

前批交付：用户明确要求“打包上传”。完整签名 `assembleGithubRelease` 2m23s 通过，保留 R8、资源压缩、lintVital 与金标 true；`1.2.8_beta2 / 36`，7,758,147 字节，SHA-256 `3bec5084c56d854513bb61a191a422cd67c02fc3f72a497e652e069c62f844e6`，V2 证书与此前候选一致。APK 保全于 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-typography-picker-20261101-3bec5084.apk`，上传既有“SleepDown 测试包”目录：[前批 APK](https://drive.google.com/file/d/1WVVoSEdGqC-2FSgCeMmK4KG-4aXMdpB6/view?usp=drivesdk)。已回读文件名、大小、父目录，未改共享权限。包含下述字体层级、模型选单位置/动画、居中圆形箭头、预览所属轮次及全部前批救援修复；没有安装、推送或公开发布。

当前追加：用户要求按 Codex 梳理本页字体层级，并确认模型 Popup 问题为“弹出位置或缩放方向不对”。已新增局部 `AiImportTypography`：系统字体 16sp 正文、14sp 中间进度、13sp 等宽执行/JSON、12sp 状态/脚注，16/18sp 半粗体标题；明暗主/次/弱灰色统一且校验对比度。模型选单修复提前隐藏/归还触发胶囊、选择后源文字变宽及完整关闭后重开复用旧测量坐标；中途反向打开保留弹簧会话。最终 Release Kotlin 与 4 类 8 项回归同轮通过（4m49s，0 失败/错误/跳过），含真实 Miuix Popup 底部向上、顶部移位后向下展开、退出保持源状态、Activity 暂停释放，以及既有对话/键盘用例。架构 605 文件、0 违规依赖，diff 检查通过。详情见[文字与选单记录](reports/2026-11-01-ai-import-typography-and-picker.md)。已包含本段最新云盘交付，尚未做手机视觉验收。

上一批源码：用户要求“回到最新”使用居中圆形向下箭头，并让下一句话接在所属轮次的导入预览下方。已将按钮改为输入框上方居中的 44dp 圆形，仅显示向下箭头并保留无障碍名称；每个不可变课表快照固定在首次产生它的轮次后，继承的相同结果不在新消息末尾重复追加，阶段选择/JSON 入口跟随最近预览。新回复独立显示，后续新课表追加为新的预览，旧预览导入仍使用其对应快照。Release Kotlin 和 5 类 38 项定向回归同轮通过（5m37s），含真实页面跨轮次/历史恢复/旧快照导入及圆形箭头点击；架构 604 文件、0 违规依赖。现已包含本段最新云盘交付，下方旧 `06b416e1` 不包含圆形按钮与预览位置修改。

前批交付：用户再次明确“内容要被输入框顶起，是的话就打包放云盘”。已确认行为为到底时内容随输入框实际占位上移，完成完整签名 `assembleGithubRelease`（2m20s，R8、资源压缩、lintVital、金标 true），并上传既有“SleepDown 测试包”目录。包为 `1.2.8_beta2 / 36`，7,758,147 字节，SHA-256 `06b416e18d2944569a634a5cdba867befc6ec30e5fe9c99dd0b7a7550bc48257`，V2 证书与既有 Release 一致。本地保全 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-dialog-shortcut-20261101-06b416e1.apk`；[前批网盘 APK](https://drive.google.com/file/d/1hLSe4pKGkM6v0rPSGGKVWEZViVqj9RnC/view?usp=drivesdk)，已回读文件名、字节数及父目录，未更改共享权限。包含当次打包前的连续对话、浅色预览、键盘与 shortcut 修复及前批救援，不包含后续圆形箭头与预览位置修改。没有安装或公开发布。

后续用户追加“已到底时点输入框内容应顶起”和“凝光长按 shortcut 闪烁”。已将列表 viewport 边界、composer 实际底部 padding 纳入贴底跟随；原先上滑查看旧消息仍保持位置。shortcut 源卡隐藏时同时关闭原生材质可见性，关闭菜单后恢复原节点；浮层 host 提前测量，避免打开时等待首次定位的一帧空档。新增键盘回归在修复前明确失败，修复后 Release Kotlin 与 4 类 16 项回归同轮通过（4m54s），涵盖逐步键盘避让/窗口缩小、阅读位置保持、真实源卡及 overlay 的材质交接、连续开关和既有退出屏障/拖动。厂商 renderer 用测试替身，尚未做实际 ROM 视觉验收。详情并入下方界面记录，前批 69 项验证记录保留。

用户追加要求教务 AI 导入界面更接近 Codex，持续显示中间对话，并改善浅色模式预览彩字。沿用 `codex/rescue-import-materials`，没有覆盖此前救援改动。公开模型进度与执行消息按顺序进入 LazyColumn，每轮保留至多 128 条，跨轮次和历史恢复一起保留；旧 JSON 缺少新增字段可正常读取，仍沿用最近 8 轮与 2 MiB 上下文边界。当前状态可换行，停止水平追字；上滑查看旧消息后不再被流式更新或任务完成强制拉到底部，并提供“回到最新”。预览独立使用稳定主题底色、可读的课程色标题与面板正文色，文字对比度至少 4.5:1，不改首页课程卡片。

Release Kotlin 与 7 类 69 项定向测试同轮通过（2m42s），0 失败/错误/跳过；架构 604 文件、0 违规依赖，diff 检查通过。详见[界面优化记录](reports/2026-11-01-ai-import-conversation-ui.md)。代码尚未提交、推送或实机验收，最新打包上传见本段首条；下方旧 Drive 链接是此前救援包。其他活动任务记录保留。

## 2026-11-01 教务、凝光与自动刷新救援

本轮用户要求修复教务灵动岛黑色收拢/渐变展开及返回键层级、特殊课程与校区作息表头、教务底栏凝光、首页白色与默认菜单参数、拖动光效闪烁和震动；补充四川机电读取登录态提示“没有正在执行的拾光任务”，并要求检索 GitHub 自动刷新案例、验证差异弹窗与逐项覆盖。工作位于主目录新分支 `codex/rescue-import-materials`，基于原干净 HEAD `3cb7285f`，保留原有提交和其他工作树。

上述实现已本地修改；四川机电根因是普通 IIFE 启动异步流程但不返回 Promise，被包装器提前结束任务。旧包装器对新增回归明确失败，修复后的生产包装器通过本机四川机电三组模拟响应。统一 Cookie 恢复并保留较新凭证，后台按实际结果成功/失败/有界退避重试。差异核对模拟覆盖自动提醒、默认保留、全选后逐项取消，以及 Room 中只应用选中新增/删除、保留本地修改和防止旧快照覆盖。

最终同一轮 Release 编译与 15 类 96 项定向回归全部通过（4m48s，0 失败/错误/跳过），包含 2 项真实 Compose 弹窗操作和 6 项内存 Room 核对落库。Node 执行包装器/会话脚本、四川机电生产包装器三组模拟响应、603 文件架构扫描通过。此前混合变体构建的 2GB 堆不足已取消，最终使用单 worker 和 3GB 堆串行验证。详见[救援记录](reports/2026-11-01-import-material-rescue.md)。

用户随后明确要求“打个包放谷歌网盘”。完整签名 `assembleGithubRelease` 3m02s 通过，保留 R8、资源压缩、lintVital 与默认金标 true。包为 `1.2.8_beta2 / 36`、7,758,147 字节，SHA-256 `a109dc95ad599c7e5390f39f4ecfef7063c11de131e1dfdd56c2498941275751`，V2 证书与既有 Release 一致。本地保全 `tmp/review-apks/SleepDown-1.2.8-beta2-rescue-20261101-a109dc95.apk`，已上传既有“SleepDown 测试包”文件夹并回读文件名、大小与父目录：[网盘 APK](https://drive.google.com/file/d/1QxIgbixBC1iSXJ3RD3j3UD3l41jLfjjt/view?usp=drivesdk)。未更改共享权限。

代码和记录目前未提交、未推送；本轮没有新增 PR、公开发布或安装授权。已检查 5038、5037、5039 的 USB/无线设备和 mDNS 均为空，没有实机视觉或真实账号验收。原有活动任务记录保留。

## 2026-10-29 返回尾段衔接纠正

用户明确纠正“不是一直垫按钮，等返回动画快结束再垫”。本批替代此前 `bada208c` 的全程预铺：返回请求只准备圆角像素；系统在 BACK 尾段将已隐藏的源 View alpha 从 0 恢复时才挂载落点图，再沿用真实业务源恢复帧的 pre-draw 交接。仅处理 BACK 已开始的当前会话，忽略迟到 OPEN 恢复；无 alpha 信号的设备在结束回调交接，不猜固定时长。Release Kotlin 及四类 30 项原生图形/会话回归通过（5m52s），包含返回前段无垫图、尾段才出现、迟到 OPEN、无恢复信号与销毁释放。实际 ROM 观感尚未验证；#88 同轮修复 HD、节次上限与显示调用链，联合包由其报告维护。尚未发布或合并 #88/#89。

## 2026-10-29 返回末帧续修

用户明确反馈金标最后一帧衔接源闪烁，并要求提前出现。`bada208c` 在返回请求成功时预铺圆角落点图，完成回调恢复业务源后，在下一动画帧 pre-draw 移除；不再等 BACK 结束才创建衔接图。Release Kotlin 与原生图形/会话回归 29 项通过（1m12s），源销毁、取消和超时清理保留。时间/日期表头、列表按压胶囊、居中标签和特殊课程 Popup 在 #88 处理；本轮联合构建与实际安装记录以 #88 报告为准，未发布或合并。
## 2026-10-30 导入课程的校区自发作息改为按课时

用户要求把“某门课所在楼宇作息与课表不同”的上游数据映射为按课时课程，并复用 #88 已实现的当天表头替换与黄色节次标，不新增第二套作息、不写入作息库。PR #91（Draft，base #88 `codex/week-time-display`，head `codex/imported-course-bells`）只改导入侧：`ShiguangImportSession` 把只给整体起止的课程转成 `customPeriodTimes`，仅首尾边界取上游值、中间节次沿用目标作息，切分不成时回退整体区间；同时修正 `isCustomTime` 课程被清空节次的既有问题，并让 `day=0`、`number=0` 作息与超出学期的 `weeks` 不再中止整次导入（分别转待安排课程、不进网格、扩展总周数并提示）。表头替换与黄标仍由 `weekDayTimeline` 负责，本次未改显示层。

验证：`compileGithubReleaseKotlin` 通过；新增 `ShiguangImportSessionBellSyncTest`（6）与 `CampusBellFixtureTest`（1），既有 `ShiguangImportSessionCustomTimeTest`（4）、`WeekCourseBellTest`（9）通过。联合基线（#88 + #89）完整签名打包 6m58s 通过，1.2.8_beta1 / 36，APK 7,758,151 字节，经 5038 USB 覆盖安装到原 OPPO PLJ110 返回 Success；验收用 `tmp/验收-校区作息.json` 已推送至设备 Download。尚未合并或发布；表头黄标与卡片位置的实机观感待用户确认。

最新联合候选 `b5ef16f4` 已完整签名打包成功（5m57s），含时间/HD `2eb22f1e` 与金标 `56f7dde8`，1.2.8_beta1 / 36、金标开启；保全 APK `tmp/review-apks/SleepDown-1.2.8-beta1-hd-b5ef16f4-30b65f4f.apk`（主仓库相对路径），SHA-256 `30b65f4fc711ceb0b4832d412156afa00c72e19dca66199d3bdbb95ef853a7b2`。此前安装时离线，用户随后再次要求“安装”，已于 2026-10-29 17:07:01（Asia/Shanghai）经 SDK 37.0.1 客户端与同来源 5038 USB 服务覆盖安装到原 OPPO PLJ110（Android 17 / SDK 37），返回 Success，手机 base.apk SHA-256 与本包完全一致。5037/5039 和全部无线 mDNS 同时检查，无其他设备；保留数据，未主动启动，无需重新构建。源码保持时间 PR 分支；详细签名、通道及实际安装结果见[联合交付](reports/2026-10-28-time-gold-review-install.md)。实际界面与 ROM 观感待确认。

## 2026-10-29 HD、显示调用及手动时间边界

用户要求核对数据结构实际调用的显示方式，并明确停止设备采集，先修源码。#88 本批修复普通日期的课程投影只落在调休分支的问题，日视图复用完整日期周桶；当天/下周实际卡片时钟一致。手动新建/修改的普通自定义课程不能早于作息原点，用户明确教务导入和已有课程保留真实时间，名称/地点等非时间编辑不受影响，特殊课程独立时间不受限；临时补卡片高度方案已撤回。节数上限为当前作息从起始节次起实际剩余数量，选项压暗为胶囊；黄标数字白字，特殊课程三条信息胶囊，文字滚动无额外矩形侧裁切。

HD 移除表头重复侧栏留白及整个设置 Pane 的 56dp 下移，按 Tabbar 实际范围与详情标题/返回控件局部避让；侧栏内容固定测量，帧读数隔离到表面，HD 运动复用既有玻璃采样，停稳恢复，模糊用有界效果池。最终 Release Kotlin 与九类 62 项回归通过（7m14s），此前 bridge/Room/导出 53 项通过仅为兼容证据；架构 602 文件/0 违规。显示分支与验证见[本轮报告](reports/2026-10-29-hd-and-display-routing.md)。设备原旋转 0 已恢复，不继续采集性能。

#89 已推送 `56f7dde8`，返回只准备像素，BACK 源 View 尾段恢复 alpha 才垫图，避免全程垫第二个按钮；无恢复信号降为结束交接。30 项回归及 Release Kotlin 通过，ROM 观感待验收。本轮沿用已授权的联合打包安装，结果记入联合交付报告，不能把此前 cb02b47d 视为新修复已安装；仍未公开发布或合并这两个 PR。

## 2026-10-29 当天表头与界面续修

用户先看 JSON，随后明确开始修改；纠正时间不标红，只在异常上课时段把数字标由蓝改黄；创建安排的列表每一行按压背景要胶囊，特殊课程显示方式用 Popup。#88 `1e2125f9` 移除多余时间方式/说明，修正标签真实居中、逐行胶囊和 Popup；`32d1fa0d` 实现当天实际时钟表头及数字标策略，次日/其他周恢复，日视图复用，不写回数据或增加轴行。Release Kotlin 与 33 项定向回归通过（5m10s）。详见[本轮续修报告](reports/2026-10-29-date-rail-and-popup.md)。

#89 `bada208c` / 文档 head `f36b9c15` 已预铺返回圆角落点图，再按源恢复帧交接，29 项原生图形/会话回归通过（1m12s）。沿用打包安装授权准备联合候选；本轮检查全部 ADB 进程仅监听 5038/5037，两条通道 USB 与 mDNS 均无设备，已提示连接原 OPPO PLJ110。后续构建/安装结果记入联合交付报告，不能把此前 `b29fa88d` 的安装算作这次新包已安装。

联合候选 `cb02b47d` 已完整签名构建成功（4m58s），1.2.8_beta1 / 36、金标开启；APK `83777845…cf40e6` 保全于 `tmp/review-apks/SleepDown-1.2.8-beta1-daily-rail-cb02b47d-83777845.apk`。构建时设备离线；用户随后明确要求“安装”，已于 2026-10-29 10:41:30 经 SDK 37.0.1 客户端及同来源 5038 USB 服务向原 OPPO PLJ110（Android 17 / SDK 37）覆盖安装，Success，手机 APK SHA-256 与新包完全一致。5037 与两条通道 mDNS 同时查过，无其他设备；保留数据、未主动启动，无需重复构建。时间树保持原 PR 分支；完整签名、来源及实际安装结果见[联合交付记录](reports/2026-10-28-time-gold-review-install.md)。实际界面和 ROM 观感待用户确认。

## 2026-10-29 用户反馈修复

#88 继续于 `codex/week-time-display`：教务返回键安全区 `27a62492`；特殊课程 Miuix 字体、既有周次范围选择器、文字自动滚动，日期作息分组及胶囊选中态 `591fde68`；单课仅跟随当前课表或自定义时间、导入与旧编辑引用转实际时钟 `3bb3bf2d`。日期作息说明按用户要求改为“在不同时间应用不同的作息”。最终 Release Kotlin 和 57 项定向回归通过（4m30s），架构检查通过。旧引用保持兼容读取，编辑保存时转换，不静默批量改写用户数据库。详见[本轮反馈报告](reports/2026-10-29-time-ui-feedback.md)。

#89 后续 `e51ff0df` 显式传圆角 px，并使提交给系统的位图角像素本身透明；27 项原生图形与会话回归通过（1m32s），已推送 `d6f8c33b`。两项继续独立 PR；沿用用户打包安装授权，本地联合候选及安装结果继续记录在联合安装报告。未新增公开 Release 或合并这两个 PR。

本轮联合候选 `c5dfb5ef` 已完整签名打包成功（4m52s），1.2.8_beta1 / 36、金标开启；APK `b29fa88d…fa13b2` 已于 2026-10-29 01:26:36 经 SDK 客户端 → OPPO Connect 5037 服务 → 原 PLJ110 USB 覆盖安装，Success，手机 APK 哈希一致。首选 5038 与两条通道 mDNS 均查过，无其他目标；未停止 OPPO 服务、未清数据或自动启动。安装后恢复时间 PR 分支，联合分支及 APK 保留；详见[联合安装记录](reports/2026-10-28-time-gold-review-install.md)。视觉待用户确认。

## 2026-10-28 Beta1 已收束，后续两项独立修复

用户在本轮明确要求“以 1.2.8 beta1 为最新基线收束，整理、合并或关闭远端、本地 PR，获得干净工作区”，并要求建立时间显示和金标动画两个 PR；随后强调认定某个 Release 后“必须完成审查和合并，不要堆在上面”。该长期约束已写入 AGENTS.md，不能解释成仅有可选合并授权。

main 源码基线现为 `e72643f2`，包含 `v1.2.8_beta1` / `498d39cc` 的全部祖先。#79、#86、#87 已合并，#81 内容已被完整包含后关闭，过时的 develop 流程 #80 关闭。首轮完整 CI 的 12 项失败已逐项处理，真实稀疏节点导入回归已修复；五类 69 项定向回归与最终完整 CI #68 成功。版本保持 1.2.8_beta1 / 36，没有改标签、发布新包或新增安装。详细结果及九份新增具名 stash 的保全规则见[收束记录](reports/2026-10-28-beta1-consolidation.md)。以下历史段落中的 #86/#87 未合并及授权边界，只记录当时状态。

- [时间显示 PR #88](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/88)：`tmp/week-time-display` / `codex/week-time-display`。保留单一时间轴、起止时间贴卡片上下沿、当天日期作息替换；特殊课程独立/占位/整周智能模式、地点、备注及起止周；日期选择改二级胶囊弹窗并去掉上移/移除。Release Kotlin 与四类 39 项回归、架构检查通过，保持 Draft 等待设备观感。详见该分支 `docs/reports/2026-10-28-week-time-display.md`。
- [金标源 View PR #89](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/89)：`tmp/gold-source-corners` / `codex/gold-source-corners`。`e4f337ef` 将既有局部像素投影为完整、已 attach 的单一源 View，提供 Outline 和圆角软件采样，返回桥接同样裁切；保留会话释放及降级。GitHub/商店 Release Kotlin 与三类 26 项回归、架构检查通过，保持 Draft 等待 ROM 圆角、裁切和返回实机确认。详见该分支 `docs/reports/2026-10-28-gold-source-corners.md`。

本轮只授权并完成旧发布 PR 的收束；两个新增修复 PR 尚未发布或合并。后续继续对应分支，不在旧动画树重做。源码工作树通过提交或具名 stash 收口，原分支、旧 stash 和忽略素材保持可恢复，禁止把私有后端或原始用户数据作为清理对象。

金标 PR #89 的后续圆角反馈已在 `e51ff0df` 修正：View Bundle 显式传 px 半径，并将提交给系统的源/返回位图本身裁成透明圆角；Release Kotlin 和原生图形/会话回归共 27 项通过。保留原图、资源释放与降级。时间和界面反馈继续在 #88 处理，联合包与实际安装记录以其报告为准；未公开发布或合并这两个新 PR。
用户随后要求“打包安装”，已将 #88/#89 在本地集成至 `codex/beta1-review-install-20261028` / `140d9a34`，完整签名 GitHub Release 6m13s 通过、金标开启；APK `bd6211eb…a4d020` 已于 21:50 经 SDK 5038 USB 覆盖安装到原 OPPO PLJ110（Android 17 / SDK 37），返回 Success，手机 APK 哈希一致，未主动启动。用户确认 USB 就绪前已枚举 5038/5037 和无线 mDNS；未动 OPPO Connect 服务。两个 PR 继续独立、尚未合并或公开发布，视觉待验收；时间树已恢复原 PR 分支。候选、完整哈希、实际通道及保全 APK 见[联合安装记录](reports/2026-10-28-time-gold-review-install.md)。

## 历史：2026-10-27 课程时间模型重构已提交

**1.2.8 Beta1 已发布：** 用户确认日志、指定金标文案并授权“改完就可以发布了”。源码及双平台标签均为 `498d39cc`，主应用 `1.2.8_beta1` / 36；完整签名 Release 6m12s、18 条日志逐字比对、架构与包身份核验通过。GitHub Release `425042198` / Gitee `1245016` 均为预发布，四份公开附件摘要匹配，GitHub latest 仍为 1.2.7.1，Gitee 实际更新列表已含 Beta1。新主包 `f1eb232c…0f2429d`，组件沿用 6.0.19 / 259。详见[发布记录](2026-10-27-beta1-1.2.8-release.md)。用户明确本地 GitHub 凭据、不要浏览器已写入两处 AGENTS；发布使用本地 credential helper 和既有官方 API。没有合并 #86/#87，main 尚未包含发布源码；未新增 Beta1 安装或实机验收。

用户后续要求以后打包默认启用金标动画、按节次选中态为胶囊，以及详细设置重排。GitHub 渠道默认金标已开启（保留显式 false 对照），商店渠道关闭，提交 `b2c17770`；“学期与显示 → 特殊课程 → 调休 → 本课表作息（含选择、日期规则、节次编辑）→ 作息管理”的界面调整及滚轮胶囊在 `0aeedd59`。Release Kotlin 编译（2m51s）、两渠道生成配置核对及正式 Release（2m43s，R8/资源压缩/lintVital/V2 签名）通过。新 APK 位于本树 `app/build/outputs/apk/github/release/app-github-release.apk`，SHA-256 `13284189a6d91e129aaf3dd691d173fbd4f348718094777d4c80f25d2e4fb84d`，7,725,403 字节，1.2.7.1 / 35。用户确认单课弹窗只是新增行影响视觉重心，无需改变尺寸。安装目标仍为 OPPO PLJ110，用户表示稍后重连，新金标包尚未覆盖安装；另一台无线 25053RP5CC 不属于本次安装目标，未安装。

用户后续要求安装并修改 AGENTS.md。已通过现有 5037 USB 通道向 OPPO PLJ110（Android 17 / SDK 37）执行覆盖安装，返回 Success，手机 base.apk SHA-256 与本轮 `b20668c9…7db8de5` 一致；未主动启动。两处 AGENTS.md 均明确同时检查 USB、无线、现有 ADB 进程和监听端口，首选 5038 无设备时继续查询可用服务；不抢占 USB、不重启 OPPO 服务。此前“未安装”属于提交时状态，后续安装事实以本段和报告为准；真实导入、迁移和界面尚未手动实机验收。

现状已由 [#86](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/86) 保全，head `a13199bb`。本聊天用户确认方案并要求执行，随后要求一并解决 [Issue #85](https://github.com/xiaomanjun233/SleepDown-Schedule/issues/85)。实现已提交 [PR #87](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/87)，目标 main，依赖 #86；核心代码 `8296564d`、Issue #85 独立提交 `95340850`。工作树为 `tmp/course-time-model-room50` / `codex/course-time-model-room50`。39 类 296 项回归、正式 Release（9m44s）、V2 签名、架构及脚本检查通过，详细模型、APK 摘要与验证见[本轮报告](reports/2026-10-26-course-time-model-refactor.md)和[课程时间模型](architecture/COURSE_TIME_MODEL.md)。保持 Draft 等待真实教务/WakeUp 网络和真机视觉验收，未合并或发布；其他活动任务记录保留。授权包含本任务提交、推送、PR 及用户后来明确要求的打包安装，不包含合并或发布。

更新：2026-10-24，Asia/Shanghai。此页保存首页凝光修复任务的当前交接，保留依赖的架构任务历史；不代表主目录或其他 worktree 的任务已结束。接手前完整阅读 [AGENTS.md](../AGENTS.md)，再核对实际 Git、PR、用户新指令与本页是否一致。流程和交接字段见 [DEVELOPMENT_WORKFLOW.md](DEVELOPMENT_WORKFLOW.md)。

给下一位助手的简短入口：

> 先读 AGENTS.md 和 docs/AI_HANDOFF.md，核对当前分支、工作树及 PR 状态，接着完成未完事项。沿用可核实的用户授权和已有成果，按本次变化选择验证；遇到差异先查事实并更新交接。

## 本次首页凝光修复

**2026-10-26 金标返回与卡片底色已交付：** 用户确认按钮闪在“返回落回按钮时”，课程管理误取内部胶囊颜色。以上包 `7c052ae8…` 为基线，118 生产文件起始核对一致；四个生产文件修改：实际卡片底色与原生锚点共用函数、明暗衬底合成和最新返回颜色；原两帧桥接改为来源下一次 pre-draw 撤除，防止半透明按钮叠亮，并保留分离/后台无绘制的有界清理；不立即 recycle 仍可能被 RenderNode 引用的桥接像素。最终 3 类 28 项及完整金标签名 Release 7m21s 通过；120 源码摘要和实际包签名/门控/原曲线/新桥接反汇编核对通过。新包主目录 `tmp/review-apks/SleepDown-gold-return-fill-20261026.apk`（`488fb102…`，7,675,359 字节，`1.2.7.1` / 35）已传原私有 Drive 并双回读，准确链接见共享交付清单，历史 14 项保留。详见[本轮记录](reports/2026-10-26-gold-return-and-card-fill.md)。D 盘缓存，不安装/提交/推送/发布；未动后端，手机返回闪烁与实际底色待验收。

**2026-10-25 表面采样、设置同步与浮动顶栏交付：** 以上包 `988fe440…` 接续；液态课程以中性灰轻提亮、编辑头部采样完整外壳+字段、渐变 shader 坐标/颜色修正、编辑与口令表单局部高斯增强；二级页按钮独立浮动并保留标题留位/隐藏触摸语义。同日时间分行、单条超长自动滚动；金标移动表面色独立于页面底色，返回末帧用真实源控件短桥接；快速设置按已保存基线逐字段重基，保留未保存编辑；周数 Medium/轨道对齐、全卡片六组甩尾、设置页预热、凝光隐藏材质分级。最终 6 类 65 项与完整金标签名 Release 同次 8m29s 通过；118 源码哈希和实际包签名/门控/原曲线/路线/高斯标记核对通过。新包主目录 `tmp/review-apks/SleepDown-ui-surface-sync-20261025.apk`（`7c052ae8…`，7,675,359 字节，`1.2.7.1` / 35）已传原私有 Drive 并双回读确认，链接见主目录交付清单，历史 13 项完整保留。详见[本轮报告](reports/2026-10-25-surface-sync-and-floating-toolbar.md)。用户明确更新现有线上后台，私有后端已复核部署并通过数据库副本/健康/资产哈希/鉴权检查；详情只在忽略的后端目录。构建期间主目录被其他工作切至 `main` / `9b6acbb2`，本动画树仍为 `f0d0f8ba`，没有混用。D 盘缓存；未做 Android 安装、提交、推送或发布，手机材质/触摸/FPS 待验收。

**2026-10-25 液态高斯、双列特殊课程与凝光打开交接交付：** 接续上包 `b42cae6e…`，液态课程编辑/个性化/添加/口令/公共表单与 Picker、QuickSheet 恢复半分辨率局部纯高斯，保留透明课程/面板色与默认描边，不启用 lens/vibrancy/depth；外壳导出不含表单，来源仍单向。液态课程卡片原绘制压暗从 8% 调到 12%，文字估算同步。课程管理按同日合并单行胶囊、星期一次、超长时间横滑，图标居中。用户追加要求已纳入：特殊课程列表用课程管理式双列 Miuix 卡片分行展示信息，编辑页保存/删除复用通知设置按钮并放普通滚动内容中，公共按钮新增默认 enabled；真实卡片金标与非金标滑入保留。凝光课程/个性化打开侧与液态统一按展开量 24%～62% 交接，初始和打断重播路线均接入；凝光关闭交接与所有几何/时长保留。最终 3 类 42 项（含原关闭帧与新策略连续打断）和完整金标签名 Release 7m10s 通过；112 源码哈希、实际包签名/门控/高斯标记/旧返回路径核对通过。新包主目录 `tmp/review-apks/SleepDown-dialog-gaussian-day-chips-20261025.apk`（`988fe440…`，7,675,359 字节，`1.2.7.1` / 35）已传原私有 Drive 并回读确认，链接见主目录交付清单。详见[本轮报告](reports/2026-10-25-dialog-gaussian-and-day-chips.md)。未改数据库/备份/时间规则，D 盘缓存不变；未安装、提交、推送或发布，实机视觉/触摸/FPS 待验收。此前两轮中间构建未上传，以下均为旧交付历史。

**2026-10-25 课程管理与玻璃可读性交付：** 在 `af04869b…` 基线上优化特殊课程独立编辑页：基本信息/显示方式分组，全宽保存按钮与确认图标，删除独立红色操作并保留确认。通过 GitHub plugin 核对 Nexio 最新 `21919ead`，课程管理列表按用户要求省略教师、汇总地点并加图标，安排采用最多四个轻染色图标胶囊与剩余数量提示，手机/横屏共用；详情字段保留。特殊课程占位 blur 2.25→10dp，原生模糊比例 1.4；液态课程材质绘制回调轻压 8% 亮度，文字对比度估算同步，无新增采样/离屏层，不影响凝光和简单高斯。仅五个生产文件变动，未改数据/时间/状态机。完整金标签名 Release 5m21s、112 源码哈希和实际包门控/签名/原路线核验通过；视觉调整未重跑旧测试。新包主目录 `tmp/review-apks/SleepDown-course-management-readability-20261025.apk`（`b42cae6e…`，7,675,359 字节，`1.2.7.1` / 35）已传原私有 Drive 并回读确认，准确链接见主目录交付清单。详见[本轮报告](reports/2026-10-25-course-management-and-glass-readability.md)。D 盘缓存继续使用，未安装、提交、推送或发布，实机视觉/触摸/FPS 待验收。以下保留上轮记录。

**2026-10-25 特殊课程金标与甩尾交付：** 在上一候选 `194cd402…` 基线上新增 `special_course_to_editor`，点击现有/添加特殊课程卡片先进入独立编辑 Activity；金标走真实卡片锚点，非金标普通滑入，沿用三种时间方式与保存/脏稿确认。只截一次源卡片，删除时撤销返回目标，首帧交接最多 6 个并清理；周视图特殊课程行接入现有周切换和首页切换甩尾，计入既有性能策略。9 类 67 项和完整金标签名 Release 7m31s 通过；110 生产文件哈希、正式签名及实际 APK 的门控/宿主/滑入资源/甩尾标记核验通过。新包主目录 `tmp/review-apks/SleepDown-special-course-gold-tail-20261025.apk`（`af04869b…`，7,658,975 字节，`1.2.7.1` / 35）已传原私有 Drive 目录并回读确认，链接见主目录交付清单。详见[本轮报告](reports/2026-10-25-special-course-gold-and-tail.md)。数据库/时间规则未变，缓存和临时文件仍在 D；未安装、提交、推送或发布，手机视觉/触摸/FPS 待验收。以下原文保留历史含义。

**2026-10-25 特殊课程与快速设置交付：** 用户要求撤掉诡异展开表单和独立新增按钮，改为标准 Miuix 卡片、二级管理页内添加／编辑／删除；追加要求时间限制显示位置，并完整复用普通课程“按节次／自定义时间／按课时”。已完成，选项限定所在间隙或覆盖区间整体前后边界；可选 JSON 字段保存模式，按节次随作息、其他模式保留自定义钟点，旧数据兼容。落库事务保留其他时段／设置并拒绝陈旧编辑，旧节次可重新选择。快速设置的液态玻璃组内增加留白，当前周状态放右侧，三个开关移除解释。仍使用本树，保留首页材质和动画基线。5 类 73 项通过，最终完整金标签名 Release 5m58s 通过；104 源码哈希、正式签名与实际 APK 内容核验通过。新包主目录 `tmp/review-apks/SleepDown-special-course-settings-quick-sheet-gold-20261025.apk`（`194cd402…`，7,657,023 字节，`1.2.7.1` / 35）已作为新文件上传原本人私有 Drive 目录，回读确认大小／归属／私有／可下载，准确链接见最新交付清单。详见[本轮报告](reports/2026-10-25-special-course-settings-and-quick-sheet.md)。缓存及临时目录继续在 D；未安装、提交、推送或发布，手机 UI／触摸／FPS 待验收。以下旧轮记录保留。

**2026-10-24 最终综合交付（覆盖下方“正在构建/尚未上传”的过程状态）：** 用户要求特殊课程参考 Nexio、修正错误基线，并明确不要弹窗。已在本动画工作树保留金标/连续打断/实时模糊/周日规则，完成特殊时段共享行轴、连续星期占位材质、左侧时间与设置内展开编辑；复制、移动/缩放、返回同步实际行列，两种旧 48 结构兼容升 49。最终 10 类 113 项及完整金标签名 Release 同次 7m21s 通过；99 生产文件哈希、正式 v2 签名和实际 APK 门控/新功能核验通过。主目录 `tmp/review-apks/SleepDown-special-slots-live-blur-gold-20261024.apk`（`d8c1a765…`，7,640,639 字节，`1.2.7.1` / 35）已上传原本人私有 Drive 目录，并回读确认归属、大小、私有与可下载；准确链接见主目录 `tmp/review-apks/remote-delivery-20261023.json`。详见[集成报告](reports/2026-10-24-special-course-placeholder-integration.md)。缓存/临时目录仍使用 D；未安装、提交、推送或发布，手机 UI/FPS 待验收。以下原文保留为历史过程。

2026-10-24 特殊课程续作：用户确认“大肥鱼”的问题是 UI 与错误代码基线，要求参考 Nexio，最后明确不要弹窗、只留玻璃/凝光占位胶囊。已把主目录该功能移到本动画工作树，改为独立共享行轴、连续星期共用占位材质、左侧时间栏；详细设置内展开编辑，不增加特殊课程弹窗。复制、返回锚点、移动/缩放同步新轴，周日起始按实际列序落点。两边旧候选都用了 48 但列不同，现升 49 兼容任一结构并保留数据。初次数据集成 5 类 66 项通过；澄清 UI 后重做，当前正跑最终 10 类回归及完整金标 Release，日志主目录 `tmp/verify-time-slots-placeholder-gold-final-20261024.log`，尚未上传新包。旧 `f3f7b681…` 实时模糊/周日候选已通过 133 项和完整 Release，但被本轮集成替代，未上传。详情见[特殊课程集成](reports/2026-10-24-special-course-placeholder-integration.md)。保留只传私有 Drive、不安装、不提交/推送的授权；真机 UI/FPS 待验收。下段为本轮前期过程记录。

2026-10-24 当前续修：用户要求液态不再用截图假卡片，改低成本实时模糊；详细设置接金标，快速设置合并当前周/状态并加周首日。已确认周日开关同时影响排列与教学周边界。完成代码接入及 Room 47→48 默认关闭迁移、备份和 ICS 兼容，正用 D 盘缓存运行定向回归和完整金标 Release。用户要求缓存迁出系统盘，约 6.6 GB 依赖已复制到主目录 `tmp/build-cache/gradle`，新构建入口同步设置 TEMP/TMP 到 D，用户级 GRADLE_USER_HOME 也已设置；旧 C 盘缓存仍有其他 Gradle 进程在用，未删除/停止。新包尚未生成或上传，不能用旧包代替。详情见[本轮报告](reports/2026-10-24-live-blur-quick-settings-sunday.md)。继续沿用只传包、私有 Drive 目录授权，无安装、提交、推送授权。

2026-10-24 原曲线重播与并行输入候选已交付：用户拒绝 `b9d60070…`，明确从打断位置直接重播原退出曲线、不要分段；双指均不打开，首卡普通开合期间拒绝其他站位，主动打断后才允许另一卡独立并行。本轮已按此重做三条路线，并修复液态并行无截图源层只有文字的问题：补课程染色/公共描边，液态实时绘制自己的外壳，复用已有模糊；彩字改为亮背景白色软阴影、暗背景黑色，保留采样冻结。10 类 113 项回归和完整金标签名 Release 同次 7m17s 通过；68 文件哈希、签名、实际门控和原曲线重播调用均核对。新包主目录 `tmp/review-apks/SleepDown-authored-exit-input-gold-20261024.apk`（`5786df7b…`，`1.2.7.1` / 35）已上传原本人私有 Drive 目录，metadata 与目录均回读确认大小、归属、私有和可下载；准确链接见主目录 `tmp/review-apks/remote-delivery-20261023.json`。报告位于动画工作树 `docs/reports/2026-10-24-authored-exit-input-and-liquid-parallel.md`。额外材质像素测试受 Robolectric 截图/软件 RuntimeShader 限制无法执行，已删除该额外测试且未削减高光；手机材质、观感及帧率待验收。没有安装、提交、推送或发布。下段均为旧候选历史，不代表用户认可。

2026-10-24 最新实际速度弧线候选已交付：用户拒绝 `798ccdb2…` 的中断观感并要求先搜索。已查 Android 官方/源码、Apple WWDC 及 SVG 规范，撤掉卡片 raw 制动与归旧轨残差，中断时以当前可见帧和像素/秒速度重建独立二次 Bezier；正常完整开合、原站位与独立会话保留。8 类 106 项回归和完整金标签名 Release 同次 6m58s 通过，66 个生产文件哈希、正式签名、实际门控与新 Flight 均核对。新包主目录 `tmp/review-apks/SleepDown-world-velocity-arc-gold-20261024.apk`（`b9d60070…`，`1.2.7.1` / 35）已作为新文件上传原本人私有 Drive 目录，metadata 和目录回读确认大小、归属、私有与可下载，准确链接见主目录 `tmp/review-apks/remote-delivery-20261023.json`。详情见[调研与修复](reports/2026-10-24-world-velocity-retarget-research.md)。没有安装、提交、推送或发布；手机新观感/帧率待复验，旧候选不是用户验收通过。

2026-10-24 关闭曲线续修已传包：用户确认上包打开正确，明确保留原关闭曲线、仅在打断节点续接原回程。本轮恢复课程、个性化和加号菜单独立关闭几何，每会话衔接当前帧/切线，打断返回局部制动后接回原时钟速率；打开曲线与背景续接不改。8 类 103 项回归与完整金标签名 Release 同次 6m51s 通过，66 个生产文件哈希、正式签名、实际 APK 金标路径和新残差实现已核对。新包主目录 `tmp/review-apks/SleepDown-original-return-curve-gold-20261024.apk`（`798ccdb2…`，`1.2.7.1` / 35）已上传原本人私有 Drive 文件夹，metadata 和目录回读确认私有、字节数及可下载；准确链接见主目录 `tmp/review-apks/remote-delivery-20261023.json`。详见[关闭曲线续接报告](reports/2026-10-24-original-return-curve-continuation.md)。没有安装、提交、推送或发布，手机关闭观感/帧率仍待复验。以下为上次候选与过程记录。

2026-10-24 最新候选已交付：正常动画速率、固定原站位重开、系统动画倍率及收尾帧复用均已进入主目录 `tmp/review-apks/SleepDown-morph-rate-anchor-20261024.apk`（SHA-256 `ee638f74…`，`1.2.7.1` / 35）。最终 4 类 42 项回归与完整金标签名 Release 同次调用 6m45s 通过，63 个生产文件哈希、实际 APK 门控和正式签名核对通过；已作为新文件上传原本人私有 Drive 目录并回读确认，准确链接见主目录 `tmp/review-apks/remote-delivery-20261023.json`。用户要求的原站位是在卡片尚未归位时仍可点击，已通过连续点空站位 8 次、内容只挂载一次的 Compose 触摸验证；手机视觉速率/帧率仍待验收。详见[本轮报告](reports/2026-10-24-morph-rate-and-original-anchor.md)。没有安装、提交、推送或发布；下段构建中为此前过程记录。

2026-10-24 速率与原站位续修：用户反馈上包速率混乱，并明确单卡连续打断须在关闭途中点击固定原站位重开。本轮恢复课程 380/440ms、个性化 340/220ms、一级菜单 260/300ms 及背景原曲线，仅中断时作保留速度、边界内制动和有限时长续接；修复防重复判断吞掉关闭同卡，并补固定原站位热区与优先路由。4 类 40 项定向回归通过（含连续点空站位 8 次、只挂一份内容），完整金标 Release 构建中。当前详见[本轮报告](reports/2026-10-24-morph-rate-and-original-anchor.md)；原只传包授权持续，不安装、不提交或推送，手机视觉与帧率未验收。以下是旧候选与本轮过程记录。

2026-10-23 最新交付完成：下段连续打断候选 `eaaf6f19…` 已作为新文件上传原本人私有 Drive 测试包目录，metadata 与目录列表均已回读确认名称、7,624,231 字节、归属、未分享与可下载。准确链接已更新到主目录 `tmp/review-apks/remote-delivery-20261023.json`。7 类 63 项回归与完整金标 Release 均通过，未安装或实机验收；后续“待回读”是本次交付过程记录，现已完成。

2026-10-23 连续打断续修：用户确认旧锚点已经对齐，随后反馈另一助手候选的液态课程管理返回右上角按钮消失、课程内容交接、个性化内容形变和顶栏渐变模糊，并明确区分“跳过”与“保留速度的打断”、同卡快速反向与多卡连续输入，要求兼顾性能。本轮新增按卡片独立会话、保留速度的可重定向弹簧、运动期上滑/真实课程点击路由及退出 GPU 录制层；修复按钮返回真实录制、个性化显式放置层缩放，液态交接按展开量 24%～62%，顶栏 82dp/22dp 只采样滚动表单。7 类 63 项定向回归、完整金标 Release（5m04s）、正式 v2 签名、实际 APK 门控与 63 个生产文件哈希均通过。主目录候选 `tmp/review-apks/SleepDown-interruptible-home-gold-20261023.apk`，SHA-256 `eaaf6f19…`，`1.2.7.1` / 35；云端交付待回读。具体状态见[本轮记录](reports/2026-10-23-interruptible-home-flights.md)。没有手机视觉/帧率验收，不安装、不提交或推送，按既有授权只传包。以下是此前候选历史。

2026-10-23 21:23 动画续调：个性化内容层改用课程卡片的交接方式（目标尺寸 + TopStart 原点非等比拉伸 + 预载叠化），液态叠化窗口 18%～50% → 12%～44%（开合共用，反向连续）；同卡片连续打断已接：`openCourseEditor` 在 Closing/Disposing 允许同课程并复用上次源快照，宿主识别恢复后跳过 Preparing/snap，从当前进度等比缩时回到 Open。反向途中点另一张卡仍被忽略；并行动画（关闭 A 同时打开 B）未实现，需在「双会话」与「快照幽灵」之间选型，详见[本轮报告](reports/2026-10-23-personalize-handoff-and-interrupt.md)。编译 41s、完整金标 Release 6m23s 通过，候选 `tmp/review-apks/SleepDown-personalize-handoff-gold-20261023.apk`，SHA-256 `159dc80b…`，`1.2.7.1` / 35。无实机验证。

2026-10-23 20:54 用户要求回撤上一轮四项改动，已按文件级还原：移除 `TransitionPayload.restoreSourceOnClose` 与宿主的提前释放分支、两条导入历史路线的标记；液态预载叠化回到 18%～50%；`CourseEditorFixedHeader` 回到 104dp / 12dp / 0.64 / 0.5 并移除 `gradientBackdrop`；个性化内容回到居中缩放。相关设计文档与专项报告一并撤回，源码状态与 20:14 候选一致，当前可用包仍是 `tmp/review-apks/SleepDown-import-history-return-gold-20261023.apk`（SHA-256 `31fb5228…`）。回撤未触碰上一轮的按钮录制层改动，也没有安装或提交。

2026-10-23 20:14 返回表面续修：用户指出返回“导入历史”时落点的红色按钮是空白，要求按右下角加号的方式处理。根因是 `ManualImportToHistory` 的锚点位图从 `detailScreenGraphicsLayer` 按按钮窗口坐标裁切，而手动导入表单挂在 `HomeMenuDestinationOverlay`，不在该录制层内，裁到的是壁纸衬底。现为表单底部 `DialogLiquidButton` 增加独立 `GraphicsLayer`，空闲时按请求录制真实按钮，开场与返回都用这张表面；页面快照只留作背景 underlay 与兜底。编译 4m07s、完整 `assembleGithubRelease "-Psleepdown.sharedViewKitTest=true"` 6m06s 通过，候选主目录 `tmp/review-apks/SleepDown-import-history-return-gold-20261023.apk`，SHA-256 `31fb5228…`，`1.2.7.1` / 35，正式签名与两个开关已核对。没有设备安装或返回视觉验证；历史条目仍走页面裁切，未改。详见[本轮报告](reports/2026-10-23-import-history-return-surface.md)。

2026-10-23 19:30 接手续作：用户确认此前“锚点外观不匹配”已解决，并要求继续按文档接入金标无缝动画。按[接入调查](reports/2026-10-21-sharedviewkit-integration-audit.md)顺序补上 P2 的“首页菜单 → 课程管理”：新增 `NativeCourseManagementActivity`（继承原页面，仅作不透明宿主）、Manifest 用 `AppTheme.NativeDetail` 声明，并把 `home_to_course_management` 加入 Kit 候选。开场源仍为整张菜单快照、返回锚点仍为真实加号/Dock 按钮，Legacy 几何与时长未改；门控、商店排除与横屏/平板 Legacy 路径不变。定向 3 类 24 项回归通过，完整 `assembleGithubRelease "-Psleepdown.sharedViewKitTest=true"` 7m59s 成功，候选主目录 `tmp/review-apks/SleepDown-home-course-management-gold-20261023.apk`，SHA-256 `33343e2e…`，`1.2.7.1` / 35，正式签名与打包 Manifest/DEX 已核对。用户同时要求定期回报进度并在打包后交付产物，本轮按当前对话交付，未使用 IM/邮件通道。没有安装、推送、PR 或发布；真机开合仍未验收。详见[本轮报告](reports/2026-10-23-home-course-management-gold-route.md)。

2026-10-23 18:28 续修已传包：用户确认上包的液态叠化偏晚、ChatGPT 仍提示未使用工具（选中 6 Luna），并追加锚点仍不匹配。液态课程编辑/个性化叠化提前至原始进度 18%～50%；ChatGPT 改用官方支持的 developer additional_tools 输入，并修复 SSE 完成对象覆盖掉已完成工具事件的问题。金标返回漏传真实位图已补：设备声明支持时传 sharedView.bitmap，应用内返回复用原 FAB 快照层，保留完整位图生命周期。两批 10 类 128 项回归与完整金标 Release（4m35s）通过；新包主目录 `tmp/review-apks/SleepDown-early-handoff-chatgpt-gold-return-20261023.apk`，SHA-256 `7a35136e…`，版本仍 `1.2.7.1` / 35，正式签名、实际 APK 金标门控/返回位图代码与 52 文件哈希核对通过。已作为新文件上传原私有 Drive 目录并回读确认，链接见主目录 `tmp/review-apks/remote-delivery-20261023.json`。没有真实账号或本候选设备验证；待用户确认锚点是否也有位置/大小偏差，当前已确定并修正的是位图漏接与副本外观。没有提交、推送或发布。详见[本次续修报告](reports/2026-10-23-early-handoff-chatgpt-and-gold-return.md)。下面均为旧候选历史，不代表真实账号或锚点已经验收。

2026-10-23 17:52 最新截图续修已交付：统一凝光首页表单的原生模糊与透明染色、控件反色、真实 FAB 返回快照、凝光课程材质归属、液态课程源快照动态模糊叠化、104dp 无提亮顶栏渐变与 Kyant 默认描边。用户确认 ChatGPT“回复无法读取，没出现工具执行记录”；明确课表请求现在首轮先走只读工具，保留 SIWC 必需的 namespace，结果往返与有界重试经过脚本验证。6 类 55 项定向回归及最终完整金标 Release（4m58s）通过；新候选为主目录 `tmp/review-apks/SleepDown-visual-feedback-gold-20261023.apk`，SHA-256 `13221a0b…`，版本 `1.2.7.1` / 35，正式签名、生成开关与实际 APK 门控均已核对，46 个生产文件哈希无漂移。已作为新文件上传私有“SleepDown 测试包”并回读验证，实际链接留在主目录 `tmp/review-apks/remote-delivery-20261023.json`。只传包授权持续有效，不再等待 ADB；真实账号和手机视觉/性能仍待用户安装复验。源码均未提交，未推送或发布；详见[本次续修报告](reports/2026-10-23-visual-feedback-and-chatgpt-tools.md)。以下为此前候选与安装的历史记录。

2026-10-23 最新交付方式：用户明确改为只传 APK，并批准上传到已连接 Google Drive，要求在本人账号建专用传包目录。已创建私有“SleepDown 测试包”，最新金标透明候选 `f671eb08…` 已上传并移入；云端大小、目录归属、未分享状态与可下载标记均已回读确认。实际文件夹及文件 ID / 链接保存在主目录忽略文件 `tmp/review-apks/remote-delivery-20261023.json`，后续传包复用该目录。此轮不再等待 USB 连接；手机下载安装尚未验证，下面“尚未安装”指没有设备安装证据。

2026-10-23 安装后最新修订：用户指出上一包没有金标、液态材质应保留颜色和透明度，并反馈凝光口令外观变化。已确认上一命令漏传 `-Psleepdown.sharedViewKitTest=true`；重新构建时显式启用。液态弹窗现在只绘制原染色和高光描边，透明透出已有全屏模糊，自身不采样下层；凝光口令已恢复原材质分支。最终完整 Release 4m14s 成功，候选为主目录 `tmp/review-apks/SleepDown-import-dialog-fixes-gold-transparent-20261023.apk`，SHA-256 `f671eb08…`，版本仍 `1.2.7.1` / 35；签名及实际 APK DEX 的本地金标门控已验证，旧 `1d492c15…` 不再用于验收。准备安装时手机已断开，5037/5038、mDNS 和其他 ADB 服务均无目标；已向用户索取恢复连接或当前无线端点，新包尚未安装。下一步复查设备后覆盖安装，保留数据；不要重建不带上述参数的普通包。源码、验证与连接边界见[本轮报告](reports/2026-10-23-import-dialog-and-course-fixes.md)。未提交、推送或发布，以下旧安装记录保留历史含义。

2026-10-23 用户追加本轮导入、液态弹窗、刷新保护、ChatGPT 工具、issue #82 与节次保存问题。继续在本工作树 `codex/sharedviewkit-ai-history` / `f0d0f8ba` 上修复，开始时已有教务金标和导航等未提交改动，均保留；没有回退到主目录旧代码。9 类 75 项定向回归与最终完整签名 Release 通过，最终 APK 在主目录 `tmp/review-apks/SleepDown-import-dialog-fixes-20261023.apk`，SHA-256 `1d492c15…`，`1.2.7.1` / 35；最后的网页刷新绑定保护已核对进入 Release。用户随后要求“安装看看”，已于 12:43:55 经 SDK 客户端和现有 5037 OPPO USB 服务覆盖安装到 PLJ110（Android 17），设备 APK 哈希匹配，未清数据或主动启动。5038 无设备、两通道 mDNS 均无端点，没有终止第三方服务。实现、通道来源与最终验证见[本轮报告](reports/2026-10-23-import-dialog-and-course-fixes.md)。本轮全部修改仍在本地未提交，未暂存用户其他改动，未推送、开 PR、合并或发布；尚未实机视觉和真实账号验收，以下活动任务与历史记录保留。

2026-10-22 本会话用户明确要求上线后端、按文档继续无缝动画并打包安装。后端已在主目录的私有部署流程上线，备份与细节仅留在私有目录。本工作树接续 `501cb1a8`，新增第四条手动导入→历史 Kit 候选及实时按钮返回锚点，并把主目录模型策略适配到本分支的 `ManagedAiRemoteConfig` 架构。完整签名 Release 与七类 41 项测试成功（8m59s，0 失败/错误/跳过）。新包 `tmp/review-apks/SleepDown-sharedviewkit-policy-20261022.apk` 已于 20:16:07 通过 SDK 客户端、现有 5037 USB 服务覆盖安装到 PLJ110（Android 17），设备 APK 哈希一致，未清数据或主动启动。用户明确允许本次 5037，随后要求 AGENTS.md 自动寻找所有可用通道，两处指南已同步。源码与验证见[本批集成记录](reports/2026-10-22-manual-history-and-managed-policy.md)。未合并、推送、发布或完成实机视觉验收；以下既有任务记录保留。

2026-10-22 新增独立任务收尾：用户要求接续另一助手的 AI 历史 SharedViewKit 扩展。实际工作树为 `tmp/sharedviewkit-ai-history`，分支 `codex/sharedviewkit-ai-history`，基线 `5b62be48`。三条候选路线、软件快照、页面底色、实时返回锚点和重复导航保护已完成；完整签名 Release 与 4 类 26 项定向回归通过。APK 在主目录 `tmp/review-apks/SleepDown-sharedviewkit-ai-history-20261022.apk`，版本仍为 `1.2.7.1` / 35，本地候选启用金标试点，尚未实机验收。没有安装、推送、合并或发布。详见[独立收尾记录](reports/2026-10-22-sharedviewkit-ai-history-handoff.md)。以下原有凝光任务记录保留。

2026-10-22 用户纠正：卡片的光应保留，只修复布局裁切黑边，表单在动画中途叠化。`94b552d2` 恢复卡片/移动副本的焦散，`2a470290` 修正资源作用域，`2a52e402` 将裁切放在完整外扩容器上；去掉普通暗色投影，大编辑器继续不带课程焦散。`acbeb9d1` 保留预载，在原始时钟 35%～70% 互补叠化两类表单，参数集中在既有 `glass/LiquidMorph.kt`，路径和总时长未改。最终源码 `2a52e402` 完整签名 Release 与 4 类 80 项定向回归成功（8m42s，0 失败/错误/跳过），567 文件架构扫描/14 项检查器回归/文档链接及 diff 检查通过。新包 `tmp/review-apks/SleepDown-course-glow-crossfade-2a52e402-20261022.apk`，SHA-256 `55b00126418162d49159938cb8b72b1f99a09fffd67243f00c3051efd0529715`；版本仍为 `1.2.7.1` / 35，v2 签名及证书匹配通过。金标未生效确认为课程管理→详情，但用户随后明确“手机不在，先不修这里”；本轮不再动金标，也没有设备安装或视觉验收。旧安装包不是本次候选，不把之前安装或构建当作本轮实机证据。详见[本轮记录](reports/2026-10-22-course-glow-and-midway-content.md)。

最新续修：用户在已安装包上反馈编辑展开亮框、拖动全屏闪光、滚动底部黑边，并要求凝光表单在动画中直接显示，同时核对金标未激活。`94307b41` 删除课程外部焦散/elevation、恢复裁切并限制独立 halo；`e9bec46c` 将凝光课程编辑/个性化改为 Preparing 完整布局、随 Morph 显示并保留至关闭完成，材质能力判断集中在既有公共 provider；`732c656f` 允许显式本地金标属性进入签名 Release，普通构建仍默认关闭，仅课程管理→详情为候选。首轮 102 项回归通过；最终源码 `732c656f` 完整签名 Release 构建及预载/课程数据/拖动状态 27 项回归成功（9m54s，0 失败/错误/跳过，两轮含重复项）。用户重新连接手机后，经项目默认 5038 通道在 PLJ110（Android 17）覆盖安装成功，系统更新时间 18:13:44，未清数据或自动启动。本轮包为 `tmp/review-apks/SleepDown-native-preload-pilot-732c656f-20261021.apk`，SHA-256 `125790245e56371e3a571f464741e449bffdc25931f06ae4b8caf4f155eb775c`；版本仍为 `1.2.7.1` / 35，签名核验通过。不要把旧 `ef98129a` APK 当作本轮产物。实际闪烁/黑边、预载流畅度及 ROM 金标接管仍待场景验收，不能以安装或开关替代。详见[后续反馈与验证](reports/2026-10-21-native-morph-live-content-feedback.md)。不合并或发布。

独立工作树 `tmp/home-accent-icon-study/`，分支 `codex/home-material-corrections`，基于 #79 的 `bfb5eecd`。用户要求修复首页凝光主题色偶发变白，以及让课程卡片复用 Dock 的晶莹光效；后续明确取消自绘图标，样稿已撤出，现有图标保留。实现、验证边界与依赖 PR 顺序见[本次记录](reports/2026-10-21-coloros-home-material-fixes.md)。用户随后明确要求“打包安装”，并要求“换个通道”，授权本次覆盖安装及 5037 通道例外；没有合并、版本变更或发布授权。

本批 `25a839aa` 为主题色修复，`5805745b` 为课程光效，可单独回退；关联 [PR 检索](https://github.com/xiaomanjun233/SleepDown-Schedule/pulls?q=is%3Apr+head%3Acodex%2Fhome-material-corrections)。最终代码 `compileGithubReleaseKotlin` 及 Dock 取色/Compose 边界/圆角三类 12 项回归成功（5m11s），0 失败/错误/跳过；567 文件依赖检查与 14 项检查器回归通过，无新增豁免。尚未实机验收主题色、外轮廓焦散阴影和系统效果开关。

2026-10-21 已从 [#81](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/81) 的 `ef98129a` 完整构建签名 Release（4m28s，含 R8/资源压缩/lintVital），版本仍为 `1.2.7.1` / 35，签名与正式版一致。经用户明确要求换通道后，使用 SDK 客户端和已核对的 5037 OPPO USB 服务端在 PLJ110（Android 17）执行 `install -r`，返回 Success；系统更新时间为 17:22:58。没有清数据或自动启动。5038 已先核对/重启但无法发现手机；本次例外没有修改项目默认 ADB 脚本或终止 5037 服务。本机 APK 副本 `tmp/review-apks/SleepDown-architecture-home-material-ef98129a-20261021.apk`，SHA-256 `15e42509b7ed9180eb5ee932d99bb80ac81d5d1bc0b6a0dd7f6e7d3084ae2b79`。设备视觉仍待用户验收，安装成功不替代视觉/性能验收。

#79 最新运行时代码 `bfb5eecd` 的[完整 CI](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39567706241)已成功；#80 最终 `1c6a2c5e` 的[完整 CI](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39557219802)已成功。两项 PR 均保持开放，没有实际同步/合并 main 或 develop。

## 依赖的架构任务与历史基线

| 对象 | 核对结果 |
| --- | --- |
| 主工作目录 | 主目录正在进行独立学校适配任务，并有未提交交接，保持不动；本任务使用独立工作树 `tmp/architecture-refactor/`，分支 `codex/architecture-boundaries` |
| 关联 PR | [#78](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/78) 已合并；本轮重构 [#79](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/79)，目标 main，不自动合并 |
| 最新正式版 | `v1.2.7.1`，源码 `bdc380ee6796de6c633fe2688120a77c9fe8356f`，应用 `1.2.7.1` / 35，双平台正式发布、GitHub latest 已确认。r1 已按用户要求撤下，双平台旧标签与源码 `fa93ae65` 保留 |
| main | `9b6acbb23512e48d0af24afec0813ecadf27573e`，实际 `1.2.7.1` / 35，已包含发布提交 `bdc380ee` 的完整祖先关系 |
| 首批重构代码锚点 | `027427aa` 提取 AI 设置执行与只读提示状态；`bd6ddb32` 恢复周视图助理偏好；`5c848e35` 拒绝覆盖后续偏好变更的旧撤销；`4bc78a43` 清理无用引用并增加架构检查 |
| 续作代码锚点 | `b45607ab` 首页导入交接状态；`98589c4a` 自动静音媒体音量与恢复；`a2eb95f4` 连续自定义课时显示及整列时间避让；`d6779eeb` 通知 payload 与 Service 恢复编解码；`3302c9d2` 恢复音量回读及部分失败所有权 |
| 2026-10-20 收口代码锚点 | `30b6d3c0` 共享值；`dacd6459` 个性化会话；`6290d180` Agent 纯计划准备；`6de4e216` 公共配置；`3f13196e` 教务生命周期；`823826d4` 通知呈现；`a004edb6` ChatGPT 工具与图片；`cb29c47f` 官方模型列表与选择；`1f1b2630` 材质和转场边界；`bc6b0e38` 共享备份快照 |
| 2026-10-21 续作代码锚点 | `636d07fc` 功能内课表状态与共享命令；`fbb8a6e3` 根包别名检查；`d0b3598f` 共享转场几何与窗口规则；`69c99b07` 动效规格、帧计算与播放职责（最新运行时） |
| 已发布的运行时代码锚点 | 最新发布运行时修复 `5d123ac4`（修订号比较），发布提交 `bdc380ee`；此前界面运行时锚点 `c3bb034c`。`844ba11d` 统一时间呈现，`e52d9918` 保留隐藏节次摘要，`1b111ad8` 统一附件/预览文字，`e7312a99` 修正 Dock 间距与触觉，`90a0fb3e` / `c3bb034c` 修正快速设置布局；`47b37ae7` 包含 Popup 锚点、日视图上传及课程外观修订 |
| 当前目标 | #79 继续根节点、循环依赖与金标试点批次，保留曲线/时长/视觉。最新审查和剩余复杂区域见[根节点续作](reports/2026-10-21-root-and-workflow-followup.md)，金标/Oplus 清理见[首批实现](reports/2026-10-21-sharedviewkit-pilot.md)。流程变更另在 #80，不自动合并、不改版本、不发布 |

本会话用户在架构重构请求后明确要求“先合并”，已通过 GitHub plugin 用 merge commit 合并 #78，并 fetch 核对发布源码可从 main 到达。此前 main 落后 1.2.7.1 的回流欠账已经收口；不要再从已合并的 `codex/nexio-glass-parity` 开发新功能。本次独立分支已快进到合并后的 main，再开始首批改动。

合并前 #78 的 Release Kotlin 编译通过，但 623 项云端回归中有 3 项作息测试失败。`d9b6f3a6` 修正夹具并增加旧记录只规范化一次的回归；本地受影响 27 项、与 CI 相同的完整 624 项通过，0 失败/错误/跳过。新云端 [39360361325](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39360361325) 已全部通过，包含 Release 编译、回归与 Debug 打包。没有改动保护规则。

本轮授权来自用户“SleepDown Schedule 全项目架构重构”的完整请求：允许必要实现、独立提交、推送和开 PR；补充“先合并”仅用于 #78。本次新重构 PR 没有合并或 Release 授权。旧发布授权记录保留为历史，不扩展到本次任务。

随后用户明确要求“继续完成重构工作”，并补充自定义课时时间显示与自动静音两项修复，确认“铃声、通知和媒体都静音，下课恢复”。本轮沿用开放的 #79，追加独立提交，不扩展为合并或发布授权。

随后用户要求“一口气全改完吧”，追加 ChatGPT 账号无法调用应用工具/读取数据/上传图片、MiMo 与 DeepSeek 获取官方最新模型、自定义模型勾选错误。用户确认工具问题“没有报错，模型说无法调用工具”。这些需求沿用 #79 的提交和推送授权；没有可用设备或凭据证据完成真实账号/实机验收，未获安装、合并或发布授权。

2026-10-21 用户再次要求“继续重构”，沿用 #79 的实现、提交和推送授权。开始时已核对该 PR 开放、head `d2aeddd7` 与本地一致，main 未变，没有评审线程。主目录的学校适配分支及其未提交交接不属于本轮，不切换、不提交或覆盖。

## 已完成与关键结论

- 续批将纯色板/ARGB 编解码/Oklab 色差归 model、通用主题归 core、玻璃诊断归 glass，57 个调用文件只迁移 import/FQN，原计算与参数保留。core→glass 文件 16→11；性能/壁纸两区域不再反向依赖且有检查器严格规则。Release Kotlin 编译和 10 类 60 项回归通过（6m02s），566 文件/0 违规/41 声明循环、14 项检查器回归通过。报告见[根节点与边界续作](reports/2026-10-21-root-and-workflow-followup.md)。
- 当前可复用的云端证据：#79 `79279664` [39557393396](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39557393396) 成功；#80 最终 `1c6a2c5e` [39557219802](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39557219802) 成功。本批基础边界在 79279664 后，不能用旧 head 代替新 head。merge-tree 仅预演确认将来集成有交接页与版本基线两份文档冲突；#80 合并后保留新流程及本任务/历史记录再解决，未实际合并。
- 用户追加的首页凝光主题色与课程光效已由本独立工作树承接，见本页开头及专项报告；用户随后取消 SVG 图标方案，现有图标保留。#79 的架构提交与本次视觉改动保持分开。

- 根节点的更新 UI 状态/安装授权收口到 `feature/update/AppUpdateSession`，首页与设置页各持有自己的 Composition 会话、复用原后台下载；原弹窗内容对照一致。`HomeResumeEffect` 订阅/释放独立并使用当前回调，保留首次启动语义和原 picker generation。根节点 4493→4406、设置根页 330→266；没有调样式/参数。移除失效 settings 引用后基线 44→41，0 新增豁免。Release Kotlin 编译和实际 4 类 28 项回归通过（4m32s），检查器 13 项通过；签名 APK 仍仅对应前批 `143eddb3`，未安装或实机验收。
- 新流程的 [#80](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/80) 已开放，目标 develop；实现 `a5df31a3` 的完整云端 CI [39555564064](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39555564064) 成功。两分支远端仍未同步，不把 PR 候选当作合并完成。#79 的 base 保持 main，待 #80 合并后再处理；本轮没有合并授权。

- 2026-10-21 在调查后，用户明确要求继续架构整理、逐步接入金标并删除旧 Oplus 动画/死代码。本批接入标准 Kit 的单路线试点，删除旧后端/探针/测试；只保留四个已发布 Activity 名称的 Manifest alias，不保留旧动画。112 项回归、完整签名 Release 打包、store Kotlin 编译与无 SDK 运行依赖检查通过，细节见[首批实现](reports/2026-10-21-sharedviewkit-pilot.md)。默认关闭，未实机验收、安装、发布；专有 SDK 分发资格未确认。
- 用户随后明确要求 main 稳定、develop 日常集成，覆盖此前 main-only 规则。已核对 develop `848ac5a4` 完全包含于 main `9b6acbb2`，落后 610、独有 0；另建 `codex/develop-workflow` 与独立工作树准备同步/流程 PR。main/develop 未直接更新，#79 暂保留 main；同步合并后再调整目标并重查 diff/CI。此任务不包含合并授权，保护细则因集成权限 403 无法读取。学校适配主工作树未动。

- 2026-10-21 用户提供金标联盟 SharedViewDemo，要求“先调查我们哪些动画可以接入、如何接入”。本轮仅调查，不是恢复原 Oplus 实验或替换全部 Compose 动效的授权。已核对官方示例 `4ed95e70`、Maven 最新 `1.0.0.4` 与 AAR 一致，优先候选为课程管理卡片→详情，其次 AI 历史→详情；通过既有 transition 后端接入，需处理坐标声明、动态返回锚点、源交接、宿主窗口与 R8。SDK POM 明确专有许可/授权伙伴，不等同于 Demo MIT。详见[接入调查](reports/2026-10-21-sharedviewkit-integration-audit.md)。该报告及本条仅在本地，未提交/推送；运行时代码与 PR head 保持 `bc69a3ef`，没有改依赖、启用实验、构建或安装。

- 2026-10-21 用户追加“一口气改完”并要求梳理动效，随后澄清“不是调整，就是看看怎么让改动效的时候更好维护”。沿用 #79 的实现、提交及推送授权；不调视觉参数。`69c99b07` 将通用横屏切页归 core，设置导航动效与状态分离，课程编辑参数/纯帧计算归功能内 `CourseEditorMotion.kt`，保留原播放与资源释放；首页深度改为显式接收编辑器缩放。具体计算和参数对照一致，Release 编译及 8 类 120 项回归通过（4m31s）；13 项检查器回归、563 文件的架构扫描和文档链接检查通过。维护入口及验证见[报告](reports/2026-10-21-motion-maintainability.md)。最终 CI 以 PR head 为准，不沿用 `80e1c370` 的通过结果。

- 2026-10-21：`636d07fc` 将课表管理、调休及登录页状态归入功能内 Activity ViewModel，与首页共用命令与刷新；Release 编译及 7 类 92 项通过。`fbb8a6e3` 增加根包 typealias/别名链和通配使用的检查，13 项检查器回归通过。`d0b3598f` 将共享 Legacy 轨迹归入转场层，首页专属材质适配留在 home，窗口弧高使用公共尺寸规则；Release 编译及 12 类 131 项通过，31 项迁移和 45 项保留声明已对照计算内容不变。生产扫描 561 文件、0 项违规文件依赖、44 条既有声明循环边。本轮最新运行时为 `d0b3598f`，完整 CI 证据见[本轮报告](reports/2026-10-21-schedule-state-and-transition-boundaries.md)和 #79，不把上一轮的通过结果代替本轮。没有实机验收或安装。
- 本轮已完成个性化会话、共享值、Repository 纯计划准备、公共配置与托管 AI 分层、教务 bridge 释放、通知 renderer、共享材质上下文、转场内容槽及备份快照边界。架构检查 557 个生产文件、9 项检查器回归通过，违规文件依赖 51→5、声明循环边 110→65，没有增加豁免。ChatGPT 图片能力保留未知状态，工具请求明确实际可用函数，首次无调用的能力拒绝纠正一次；官方模型按账户 API 刷新并缓存，自定义勾选按实际模型 ID。五批 Release 编译及对应 188/72/121/110/129 项定向回归均通过，批次有重叠，不相加当作独立总数。最终运行时 `bc6b0e38`；完整云端 CI 必须按 #79 最终 head 核对，最新结果记录在 PR。详见[收口报告](reports/2026-10-20-architecture-completion-and-ai-fixes.md)。没有设备或凭据实测，不宣称真实模型拒绝、OEM 音量或视觉验收已通过。
- 续作已将首页导入交接收进 Composition 所有的状态对象，并统一通知 Intent/Service 恢复编解码。连续的自定义课时合并显示，提醒仍保留逐课时时间；整列时间标注按实际字体测量避让。自动声音现在同时静音媒体并保存/恢复原音量，保留可检测的课中手动调整，授权返回后重新调度。联合验证 `d6779eeb` 的 Release Kotlin 编译及 16 类 140 项回归通过（5m16s）；随后 `3302c9d2` 保护静默拒绝恢复和部分调音失败的所有权，两项新回归修复前失败，修复后 Release 编译及相关 3 类 37 项通过（5m07s）。均为 0 失败/错误/跳过。其余运行时代码未再修改。9 项检查器回归通过，扫描 546 个生产文件，44 项既有文件依赖/109 条循环边未扩大。当前 5038 无连接设备，没有安装或实机验收。详见[续作报告](reports/2026-10-20-architecture-followup-and-course-fixes.md)；最终完整云端 CI 按 #79 head 核对。
- 本轮架构审查与首批重构已完成，详见[报告](reports/2026-10-20-architecture-review.md)。ViewModel 从 888 行变为 479 行，完整 AI 设置执行仍由原作用域管理；修复周视图助理偏好回滚/撤销遗漏，并在数据库撤销前拒绝已过期的助理偏好快照。Release Kotlin 编译及相关 5 类 87 项测试通过，0 失败/错误/跳过；9 项检查器回归通过，扫描 544 个生产 Kotlin/Java 文件。声明违规依赖 51→44、循环边 110→109，剩余债务保留清单。没有实机 UI/性能/OEM 验收，没有安装或发布。#79 的完整云端检查按最终 head 执行，结果见 PR Checks，不用 #78 或早期 head 的结果代替。
- 本轮更新识别：`1.2.7` 与 r1 已发布客户端忽略修订后缀，已以旧版能识别的 `1.2.7.1` 正式补发。当前代码按数字版本、发布阶段、阶段序号、修订号排序；12 项定向测试及原版比较器 8 项兼容场景通过。额外对真实 Gitee 更新列表运行原版选择函数，两种 Beta 设置都选中 `1.2.7.1`，旧版满足更新条件，新版不会重复提示。四份公开 APK 回读一致；r1 已撤下，源码标签保留。
- 课程玻璃改为共享全分辨率壁纸前缀与逐卡 Lens，处理课程详情展开时 producer 自订阅造成的持续更新；保留必要的共享 Offscreen 栅格边界。用户反馈左右切周已有明显改善，未作 FPS 测量。
- 横屏凝光顶部胶囊保留原生 Segment。其内部 TextView 会消费触控，容器 OnTouchListener 不能观察完整序列；现用透明 FrameLayout 的 `dispatchTouchEvent` 观察 DOWN 和分发后的 UP/CANCEL，抬手再导航。已审计本地 HD 侧栏组件职责，不能拿侧栏或双栏控件直接替代顶部胶囊。
- 本轮继续保留 Segment 实例与弹簧，设置/课表明暗变化采用原地 refresh。导航锁由宿主控制，避免 `setEnabled(false)` 触发原生取消动画；刷新后重申无外框协议。
- 侧栏/底栏切换按钮拆出独立点击区域；个性化收紧标题间距，一级与二级标题按用户要求同为 titleMedium，以字重区分。
- 已关闭发布内容覆盖的旧草稿 #57、#61、#65，保留分支和历史。草稿 CI 跳过规则已在 #78 实际验证，等待可评审时运行完整 CI。
- 恢复 `AgentImportWorkspace.kt` 的原有 7 行作息校验，修复连续失败的那项云端测试。规则、模板和文档入口本轮已统一，Miuix 构建说明改为唯一完整补丁。
- AI 加号改用模型选单同款 Popup Morph；导入保留旧视觉到弹窗卸载后再展示新课表。新导入使用彩色模式，着色 50%、模糊 35%、折射 50%，其他参数对齐已有标记点。
- 自动教学周始终开启，详细/快速设置和 AI 的手动切换入口已移除。旧字段继续兼容，缺少开学日期时保留原周并提示补充；新建、配置保存、启动和备份恢复均接 Repository 规范化。
- 教务搜索框采用 ModulateAlpha 保持原生模糊采样，底部渐变同步入场，避免逐帧重复设置原生功能按钮文字。
- 附件 Popup 使用真实按钮中心返回并左边缘对齐，系统图片/文件选择器等退出完成后再打开。日视图助理接入现有文件确认、提取、校验和预览链路。无壁纸可使用单色/渐变，周视图已结束课程保留原色，个性化去掉冗余分类标题。
- 所有来源的真实课程时钟按当前作息精确匹配：完整对应时显示节次，偏移与部分覆盖保留真实时间；旧导入无需重导或改库。编辑器未改时间的确认保留源时钟和编号，周视图完整时间按实际列宽排版。
- 附件展开菜单使用面板主题前景，预览卡片使用公共课程文字。液态 Dock 加号与导航间距对齐原生 12dp 并恢复点击轻触反馈；快速设置统一行边距、短数值宽度及状态/日期说明布局，顶栏标题适配剩余宽度并保持单行。

## 验证与产物：按代码锚点区分

| 对象 | 实际证据与边界 |
| --- | --- |
| `1.2.7.1` 正式发布包 | 完整 `assembleGithubRelease` 通过，3m58s，159 项中 32 执行、127 复用，R8、资源压缩、lintVital 和正式 v2 签名齐全。身份为 `1.2.7.1` / 35，证书 SHA-256 `0f2b50df…`。12 项定向测试、原版比较器 8 场景及真实 Gitee 列表选择验证通过。双平台正式发布、公开附件回读通过；未安装。副本 `tmp/review-apks/SleepDown-1.2.7.1-20261020.apk`，7,591,259 字节，SHA-256 `3c920a97b319567bd367a482ccd493f55d59aa8c3dfe54b5dcd49c153b83fc8f` |
| 上一轮界面代码包 | `c3bb034c` 完整 `assembleGithubRelease` 通过，3m30s，159 个任务中 21 个执行、138 个复用；保留 R8、资源压缩、lintVital 和正式 v2 签名，包含对应轮次的界面修订 |
| `1.2.7-r1` 发布包 | `fa93ae65` 的完整 `assembleGithubRelease` 通过，4m24s，159 个任务中 21 个执行、138 个复用；R8、资源压缩、lintVital 与正式 v2 签名齐全，证书 SHA-256 `0f2b50df…`。Manifest 为 `1.2.7-r1` / 35。GitHub 与 Gitee 四份公开 APK 已流式回读，大小与 SHA-256 与本地一致；发布说明三处逐字节一致。详见[发布记录](2026-10-19-stable-1.2.7-r1-release.md) |
| 本轮时间校验 | `e52d9918` 的 `CoursePeriodAlignmentTest` 19 项通过，0 失败/错误/跳过；含编译 2m50s，覆盖无原始快照的旧导入、多作息并存、连堂与写回保留；未跑完整套件 |
| AI 暂存校验 | 恢复后仅运行 `AgentImportWorkspaceTest.overlappingAndGappedPeriodsAreRejectedBeforePublishingAStage`，1 项通过、0 失败；含编译 6m02s，没有重跑 623 项套件 |
| 草稿 CI | [run 39191153304](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39191153304)，提交 `856d9b91`，三个 job 均 skipped；只证明草稿门控生效，不表示完整 CI 通过 |
| 上轮日期/默认值 | 对应 `0f019f1d`：现有 `ScheduleTermBoundaryTest` 8 项与 `ImportedCourseCardDefaultsTest` 1 项通过，共 9 项；含编译 3m37s，未跑完整套件；本轮导航样式修订未重跑这些测试 |
| 安装与实机 | 2026-10-19 23:04:03 经项目 ADB 5038 通道覆盖安装最终包到 USB 设备 PLJ110（Android 17），返回 Success，系统核对为 `1.2.7` / 35；保留数据、未自动启动。界面效果待用户验收，见[本轮报告](reports/2026-10-19-standard-clock-ui-feedback.md) |

最新包本机副本为上表的 `SleepDown-1.2.7.1-20261020.apk`，已随双平台 Release 发布。旧 r1 副本 `tmp/review-apks/SleepDown-1.2.7-r1-20261019.apk`（`a9bc1b4b…`）保留，对应发布已撤下。此前 `standard-clock-ui`（`0152a70d…`）、`popup-day-file-cards`（`0cac9908…`）、`navigation-full-capsule`（`41f6768d…`）、`navigation-import-defaults`（`501c3455…`）及 `COUI-gesture-dispatch` 副本也均为旧包。临时产物不入库，其他机器可能没有文件；需要新包时重新构建，不把旧包改名充当新构建。

## 下一步

1. 等待用户对 `1.2.7.1`（继承 r1 全部修复）的实机验收与反馈：旧课表时间/节次、窄列完整时间、附件菜单与日视图上传、无壁纸配色、液态加号间距/振动及快速设置标题/行排版。对应测试与 Release 构建已通过；没有自动 UI 或性能验收，不据构建或发布成功替代真实效果判断。
2. 本次更新送达修复和双平台补发已完成，无待发布候选。自动检查沿用每日一次规则；用户可在设置中手动检查 `1.2.7.1`。如反馈仍无法发现更新，再依据实际请求、渠道与设备证据定位，不重复修改已验证的版本排序。
3. 如需继续修复，围绕真实场景定位后追加独立提交，按风险构建；不要重复已排除的容器触控监听方案，也不要上传系统反编译源码。
4. #78 已完成可评审、回归修正、merge commit 与 main 回流核验。当前架构任务的实现与后续顺序见审查报告；新 PR 按自身 head 核对构建和 CI，不能用 #78 的通过结果代替。
5. 此前列出的五项反向依赖已在 2026-10-21 继续处理；具体提交、验证与剩余 44 条声明循环见[本轮报告](reports/2026-10-21-schedule-state-and-transition-boundaries.md)。后续围绕 Agent/导入事实交接、设置/课表保存装配及 core/glass 的实际职责处理，不机械拆分大文件。真实账号工具/图片、官方账户模型发现与实机自动静音/视觉仍待场景验收；本次没有安装、合并或发布授权。

## 用户要求与授权来源摘要

来源是本任务会话的用户指令；下列内容方便检索原消息，不替代原始授权：

- “先整理一下混乱的工作目录……和最新 release 对应上，然后执行该任务”：对应正式版基线与渲染任务。
- “不需要进行奇奇怪怪的测试……打包 release……自己测试”及后续“先交付 APK”：保持最小充分验证，先交付 APK；用户随后明确要求“安装”，已覆盖安装本轮最新包，未自动启动。
- 后续横屏滑选、侧栏按钮触控和标题调整：继续同一修复任务，当前仍待实机反馈。
- “帮我整理一下仓库的情况 github 一直给我发 pr run”：对应已完成的旧 PR 关闭、CI 与失败项修复。
- 本轮“整理……他人贡献……创建 PR 和提交合并规范……切换其他 AI……接手”：统一协作文档与交接。没有获得实际合并 #78、发布、改写标签或删除其他工作树的新增授权。
- 最新“继续改……横屏凝光 tabbar……AI 加号……口令导入……自动计算当前周……新导入彩色模式……教务搜索框闪烁”：继续六项体验修订，按既有交付要求打包供用户测试。
- 随后“安装”已于 13:08 完成；“查看手机最新截图……柔性反馈和发光被限定在内框”及“而且亮度太亮”：读取用户指定的最新截图，继续修正导航绘制与光效，沿用签名 Release 打包安装交付。
- 最新“继续修改……加号 popup 收回错位……左边缘对齐……无壁纸单色……分类大标题……周视图今天上过的课不用变灰”；补充明确要求“日视图助理也支持上传文件”。继续同一 PR 和既有签名 Release 安装交付，不把上传入口误解为另造解析链路。
- 后续截图反馈时间截断、GDCP 多校区作息，并明确“不要只适配这一个学校……按这类情况标准化处理”“升级之后原来的导入好的也要一起更新显示方式”；同时要求上传 Popup 文字反色、导入预览文字、液态加号与 Dock 间距/点击振动及快速设置文字对齐/换行修复。只更新呈现，保留原始课程时间与用户数据。
- 最新“以当前代码状态 不要改 versioncode 将版本名字改为 1.2.7-r1 并按项目规范书写更新日志 然后重新打包 上传 github/gitee 双平台 release”：完成 `1.2.7-r1` 修复版构建与双平台正式发布。明确约束是 `versionCode` 不改（保持 35），并明确该版属正式 release 而非 pre-release；发布对象为本轮代码状态，未合并 `main`。
- 2026-10-20 本会话用户要求“昨晚发布了1.2.7-r1 但是1.2.7收不到此项更新 你帮解决一下”，选择 `1.2.7.1` 并要求“你把r1的发布给下了吧”。随后明确回复“允许，使用现有脚本连接撤下 r1”及“允许，使用现有脚本双平台补发 1.2.7.1”，授权本次 GitHub 插件能力缺口的例外、任务提交推送、新标签与双平台正式补发。两项均已完成，源码标签保留；不包含合并 main 或其他发布授权。

原会话无法访问时，先完成可逆的核对与本地工作；需要新的远端写入、合并或发布时确认可追溯授权，不能把本页视为一份无限授权。

## 必须保留的本地内容

- stash 按名称 `workspace-before-nexio-glass-2026-10-18` 定位，其中 AI 作息校验已单独恢复并提交，其他内容与原 stash 保留；不要整份 pop 或 drop。另有 `backup: untracked app sources before home menu morph`，未处理。
- 最初未跟踪素材保存在本机忽略目录 `tmp/workspace-preserved-20261018/`；不是可随意删除的构建垃圾。
- `git worktree list` 中还有其他任务，包括教务适配与历史性能/转场工作。本页不表示这些工作树干净、停用或可以删除。
- 本轮文档整理期间出现未跟踪的 `app/src/main/assets/shiguang_warehouse-main/scripts/__pycache__/school_index_pb2.cpython-312.pyc`，属于生成缓存，未纳入文档提交，也未清理其他进程产生的文件。后续以 `git status` 核对，不应据此宣称源码有待提交修改。
- 私有后端、签名与用户数据不进入交接附件、仓库或日志。机器专用的 JDK/ADB/构建参数见 `AGENTS.md`，新环境需核对实际路径。

## 按需阅读的证据

- [正式版基线整理](reports/2026-10-18-workspace-release-baseline.md)
- [首轮玻璃渲染](reports/2026-10-18-nexio-glass-parity.md)与[展开/材质反馈](reports/2026-10-18-glass-feedback-followup.md)
- [横屏输入首轮](reports/2026-10-19-landscape-tab-input.md)与[HD 组件及最终分发诊断](reports/2026-10-19-coui-hd-dispatch-audit.md)：前者是当时尝试，后者修正根因
- [PR、Actions 与 main 回流盘点](reports/2026-10-19-repository-pr-actions-cleanup.md)
- [最新导航、Popup、导入与默认值修订](reports/2026-10-19-navigation-import-feedback.md)
- [横屏凝光整条胶囊反馈与亮度修订](reports/2026-10-19-navigation-shell-feedback.md)
- [助理 Popup、日视图文件上传与课程外观修订](reports/2026-10-19-popup-day-file-card-feedback.md)
- [通用多作息显示与界面反馈](reports/2026-10-19-standard-clock-ui-feedback.md)

## 2026-10-31 1.2.8 Beta2

已发布 `v1.2.8_beta2`（预发布，源码 `8e43ac36`）。#88 #89 #91 #92 #93 #94 #95 #96 已全部合入 `main`，无遗留 PR。附件 `SleepDown-Schedule_v1.2.8_beta2.apk`（7741763 / `06fe29d0…`）与 `SleepDown-ColorOS-Course-Component.apk`（3577334 / `5f5b4c47…`）经公开地址回读一致；组件未重建。后续待办：Gitee 同步 Release；凝光底栏材质仅保留“默认材质 + 浅色色层 0.9”，其余首页控件参数已按用户要求回退。
