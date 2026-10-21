# 首页凝光主题色与课程光效

状态更新：用户随后反馈展开亮框、拖动全屏闪光和滚动底部黑边。初次直接复用 Dock 外部焦散的方案已撤回，删除 `ColorOsCourseCardGlow`、恢复裁切，并区分静态卡片/移动副本/弹层；本报告保留初次调查与包记录，当前实现及预载试验见[后续修复](2026-10-21-native-morph-live-content-feedback.md)。

日期：2026-10-21。此批是用户追加的视觉修复，与 #79 架构迁移分开提交；不修改版本、数据库、备份或课程时间。用户随后取消自绘图标方案，保留现有图标；样稿没有接入应用或纳入本次提交。

## 根因与实现

### Dock 选中色接近白色

原实现对壁纸底部取样，并将原始壁纸平均亮度交给选中色对比度计算。原生 Dock 有自己的着色/模糊材质，其表面明暗不等于壁纸像素；接近分界的中间亮度可能把主题色的 HSL 明度推到接近 1。加号仍使用原始色种，因此会出现加号有颜色而导航选中项接近白色的情况。

按原公式复算：`#007AFF` 色种对 `#747474` 壁纸样本会得到约 `#F8FBFF`（HSL 明度约 0.985）；这说明不需要色板提取失败，也会发生接近白色的结果。

`ColorOsDockAccent.kt` 分开接收壁纸资源的明暗选择与 Dock 的实际明暗语义。提取色种继续复用缓存，Dock 使用与 `HomeMaterialStyle` 相同的明暗状态选择对比方向，取消根节点里额外的底部壁纸取样。其他页面继续按页面底色处理。

回归覆盖提取深色壁纸色相、无彩壁纸、深浅对比度，以及主题色/按钮色保留；这些数值检查不替代真实半透明材质的可读性验收。

### 课程卡片复用 Dock 光效

本机原生 Dock 的常驻晶莹效果包含 `TYPE_FRAMEWORK_CAPSULE_3` 边缘预设与系统焦散阴影，并非只有按压 spotlight。课程卡片在既有凝光模式中复用这套边缘预设，去掉旧的手工边缘宽度/透明度覆盖；卡片仍使用自己实际的圆角和形状。

`ColorOsCourseCardGlow.kt` 只负责单个原生课程表面的焦散资源与开关，沿用 Dock 的 level4 资源及系统实现。没有新增采样器、模糊层、全局状态或动画框架。原有文字、课程着色、触摸光效、点击与材质配置继续由原入口负责。

焦散跟随 `materialVisible`、`edgeEffects` 和系统焦散开关；遮挡/禁用、detach 和 release 清理焦散与 elevation。外壳允许阴影绘制到轮廓外，文字仍单独按原卡片形状裁切。缺少焦散 API/资源时在既有原生能力边界记录失败，保留已可用的卡片材质。其他原生表面保留原边缘预设。

调查仅用于核对已存在的本机 API 和资源；没有提交系统反编译源码、系统 APK 或截图。

## 验证与交付

最终代码实际执行 `compileGithubReleaseKotlin` 与 `testGithubDebugUnitTest` 的三类定向回归，5m11s 成功：Dock 取色 4 项、Compose Dock 边界 3 项、材质圆角 5 项，共 12 项，0 失败/错误/跳过。首轮 6m04s 通过后，复核又完善了裁切 wrapper 的固定尺寸传递和原生效果应用顺序，以上为重新执行的结果。CI 增加取色和圆角类，防止以后遗漏这两项边界。

依赖检查通过：567 个生产文件、0 违规文件依赖、41 条已登记历史循环；14 项检查器回归通过。没有新增循环或豁免，`git diff --check` 通过。

原生焦散、AndroidView 外轮廓阴影以及实际壁纸下的颜色仍需要 ColorOS 设备验收；不将 JVM/Compose 测试或安装成功当作视觉、性能或 OEM 验收。没有 APK 发布或版本改动。

### 随后按用户要求打包安装

用户明确要求“打包安装”，随后要求“换个通道”。源码 `ef98129a` 的完整 `assembleGithubRelease` 成功，4m28s，159 个任务（62 执行、97 复用），保留 R8、资源压缩、lintVital 和正式 v2 签名。包名 `com.xiaomanjun.sleepdownschedule`，`1.2.7.1` / 35，targetSdk 36；签名证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与已发布正式版 APK 一致。

5038 的 SDK 来源、当前用户密钥路径、设备列表和 mDNS 均已核对；Windows USB 调试接口正常，但重启 5038 后仍无设备。用户明确要求换通道后，5037 核对为 OPPO Connect 的现有 USB 服务端，并识别到唯一 PLJ110（Android 17）。使用 SDK ADB 客户端在该通道执行 `install -r`，返回 `Success`；安装后系统核对仍为 `1.2.7.1` / 35，`lastUpdateTime=2026-10-21 17:22:58`。未清除数据、未启动应用、未终止其他 ADB 服务，也未修改项目的默认 5038 脚本。

本机保留 `tmp/review-apks/SleepDown-architecture-home-material-ef98129a-20261021.apk`，7,591,375 字节，SHA-256 `15e42509b7ed9180eb5ee932d99bb80ac81d5d1bc0b6a0dd7f6e7d3084ae2b79`。该 APK 对应 `ef98129a`，后续仅补记文档，不把文档提交称为重新构建。

此任务基于 #79 的 `bfb5eecd`，分支 `codex/home-material-corrections`；`25a839aa` 修复主题色，`5805745b` 接入课程光效，可分别回退。[本分支 PR](https://github.com/xiaomanjun233/SleepDown-Schedule/pulls?q=is%3Apr+head%3Acodex%2Fhome-material-corrections)先以 `codex/architecture-boundaries` 为依赖目标，避免把 #79 全部代码重复列为本次视觉改动。#80 的流程同步与 #79 的集成仍按各自 PR 说明处理，不自动合并或修改 main/develop。
