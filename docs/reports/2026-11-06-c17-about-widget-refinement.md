# 2026-11-06 C17 关于、软件更新与小组件投光修订

## 本轮范围

延续用户对 COUI 设置页的修订要求：以本机最新 Demo、手机相册截图及系统原版资源核对比例、颜色和按钮；保留既有功能和液态玻璃搜索位置。用户随后明确要求关于页显示全称，移除挤压的应用名称/开发者双列信息块，直接放开发者 GitHub；新版本说明和历史日志通过全屏内页查看。

本轮仍在 `codex/rescue-import-materials` 的既有工作树上推进，保留其他活动任务和此前作息编辑器的定向 R8 修复。未操作数据库、课程数据或用户壁纸。

## 原版依据与实现

- 深色模式预览：E 盘 Settings 17 的 `darkmode_preview_preference.xml` 和两张 `210×425` xxhdpi WebP，换算为 `70×141.67dp`。应用保留自己的课表预览内容，采用原版比例和选中颜色，不复制系统品牌内容。
- 分隔线：恢复 COUI `outlineVariant`，浅色 `#1F000000`、深色 `#33FFFFFF`。视效说明使用 `PreferenceSummary` 与 `onSurfaceVariant`。
- 关于页：头图显示“SleepDown课程表”和版本；下方使用普通纵向列表，开发者入口为 `https://github.com/xiaomanjun233`。项目源码、反馈、隐私、许可、捐赠和致谢保留。
- 软件更新：参考当前手机 `com.oplus.ota` 的 `logo_area.xml`、`fragment_local_update.xml` 及安装下载控件资源。关于页检查入口进入独立全屏页面，版本说明可滚动，主题色主按钮为 `220×44dp`、14sp，固定在底部安全区上方。复用原检查、下载、安装会话，权限继续使用原底部窗口。
- 更新日志：独立全屏页面，保留所有版本正文和展开交互；软件更新页也提供历史日志入口。手机接入既有详情 Activity 转场，宽窗口接入原详情导航栈。既有代理协议 `ABOUT`、`CHANGELOG`、`DOWNLOAD` 分别进入对应页面，协议标识保留。
- 搜索：提示语为“搜索设置项”，液态模式继续使用教务同款搜索材质和原位置，搜索结果统一成组并显示设置路径。

系统资源、系统 APK 与手机截图只存放在忽略的 `tmp/` 或 E 盘，没有作为项目资源提交。

## 小组件光效

用户最新截图中存在圆角外露光与屏幕右缘硬切。投光输入此前没有应用卡片圆角，AndroidView 软件截图也不包含 Compose 外层的 squircle；渐隐边界延伸到窗口之外。

`ColorOsCardLight.kt` 只在 Widget 的 `fitToCardWidth` 路径给投光输入应用相同的 squircle 掩码，并按实际窗口约束渐隐区域、明确横向变换中心；缓存上传圆角，透明度变化不重复上传。保留 COE 本体、原动效参数、RemoteViews、用户壁纸与既有课程快照流程。普通课表选择器路径维持原几何。

像素与边界验证能覆盖掩码和渐隐输入；COE 在手机上的最终投光位置与范围仍需要新包实机画面验收。

## 头图生成

用户明确允许 API/CLI 备用方式，并指定当前 custom 的 `gpt-image-2.5-sunburst`。本机模型列表确认存在该模型后，通过 imagegen 技能官方 CLI 调用，45 秒生成成功，没有替换模型。凭据只在子进程内存使用，没有输出或写入仓库。

- 原图：`output/imagegen/sleepdown-about-hero-c17.png`，1774×887。
- 应用资源：`app/src/main/res/drawable-nodpi/about_sleepdown_hero.webp`，1280×640、24,984 字节。
- 生成日志：`tmp/imagegen-sunburst-about-20261105.log`。

完整提示词：

```text
Use case: stylized-concept
Asset type: landscape header artwork for the SleepDown course schedule app's About screen.
Primary request: an original, quiet, polished abstract artwork that fits the spacious visual hierarchy of a ColorOS 17 About Device header, while belonging to SleepDown.
Subject: broad softly curved translucent forms, suggesting a calm moonlit rhythm and the gentle flow of time. A subtle silver-blue glow, a restrained hint of lavender. No characters or objects.
Style/medium: premium sculpted glass and satin abstract product artwork, smooth continuous shapes, soft studio light, detailed clean edges.
Composition/framing: wide composition, central and upper-middle area calm and low in detail so the app can overlay its live title, version, and update button. Shape movement concentrated toward the perimeter. Designed to crop safely to a rounded mobile card.
Color palette: luminous pale blue, cool silver, subtle lavender, deep muted blue; avoid strong rainbow colors or oversaturated purple.
Text: none. No typography, numerals, logos, device names, interface controls, phones, watermarks, borders, or Android screenshots.
Constraints: generate only the artwork, not a UI mockup; original composition; good readability under real overlaid app text.
```

## 验证与交付

初轮 5 个定向测试类共 21 项通过（About、首页、搜索、课表详细设置、小组件光效）。这发生在全屏内页追加前，不能作为最终源码全部验证通过的证明。

全屏内页追加后 About/Home 共 5 项通过，0 失败、0 错误；最后一次耗时 1 分 51 秒。覆盖独立日志导航、返回、进入软件更新、应用全称与 GitHub 行、220×44dp 按钮及单次下载回调。日志 `tmp/c17-about-fullscreen-tests-20261106.log`。已查看最终关于、软件更新、日志三页并排图和通用设置图。

完整 `assembleGithubRelease` 通过，6 分 49 秒，保留 R8、资源压缩、lintVital、正式签名和默认 SharedViewKit 配置。18 项本轮生产源码/资源构建前后哈希一致。交付包 `tmp/review-apks/SleepDown-1.2.8-beta2-c17-about-widget-20261106-326fde9c.apk`，7,659,816 字节，SHA256 `326fde9cc64de04b41ed6a49f3a1a21304a39c663cdcf19f79cd5e98af5e1fa6`。同目录保存精确 `-mapping.txt`，map id `83b4c26581cc9062076f1530d615c8a39bc402e417d2ca1439906a20c4e305a9`。包身份 `com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / 36`，签名证书与前版一致。

通过 SDK 37 ADB 客户端连接现有 5037 的 OPPO 互联 36.0.1 服务，在同一 USB 手机 `3B15AE023YL00000 / PLJ110 / Android 17 / SDK 37` 执行隔离 ART 强制初始化：`VERIFIED class=nd4 methods=43`。随后按用户本轮明确要求覆盖安装，返回 `Success`，读取设备 base APK 哈希与交付包一致，未启动应用。摘要 `tmp/c17-about-widget-delivery-20261106.json`、校验输出 `tmp/c17-art-verification-20261106.txt`。项目 5038 仍是 SDK 服务但当时没有设备，两通道 mDNS 均无无线端点，未停止 OPPO 互联。

本地截图输出 `tmp/coui-visual-review-20261106/` 使用 Compose Native Graphics，属于本地布局验证，不能代替 ColorOS ROM 和 COE 实机验收。本轮新包已安装，未发布；新视觉与小组件投光仍待用户实机验收。
