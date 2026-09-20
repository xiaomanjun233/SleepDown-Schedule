# 1.2.5 Beta6 发布记录

用户要求为删除特效增加轻量动态模糊并发布 Beta6，随后追加彩色文字的白色提亮／背景明暗适配。此前要求停止额外测试的偏好继续沿用。

## 本次交付

- 删除粒子沿实际轨迹进行 18ms、四次加权时间采样，形成短而柔和的动态模糊；保持自上而下侵蚀、随风飘散与 1.1 秒时长。
- 彩色文字保留粗体，依据卡片原有的明暗前景选择向白色或黑色混合，使主题色进入更清晰的亮度区间。结果随颜色配置缓存，不逐帧读取壁纸。
- 包含本轮此前完成的个性化即时刷新、首页点选复制、玻璃提示胶囊整体反馈、删除单一范围弹窗、背景冻结、侧边轮廓光移除及明日预告修复。

产品更新说明见 [Beta6 Release Notes](RELEASE_NOTES_1.2.5_BETA6.md)。

## 构建与发布

- 发布源码：`codex/mainline-preview-performance`，提交 `4159c83938efe10070d3ffd0adae95af5397354a`；GitHub 远端标签 `v1.2.5_beta6` 已核对指向该提交。源码同步至 GitHub 与 Gitee。
- 最终 `assembleGithubRelease` 成功，耗时 3 分 9 秒，包含 Kotlin、R8、资源压缩、lintVital 与签名打包。
- APK v2 签名验证通过；包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.5_beta6`，versionCode 延续测试版系列的 `31`。
- 文件名：`SleepDown-Schedule_v1.2.5_beta6.apk`；大小：7,127,399 字节。
- SHA-256：`96413aa2a45b7cb10dc03acf7a4911de8ae70868abd8408e795bbd2311fe3a04`。
- [GitHub 发布页](https://github.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.5_beta6)：上传资产的 API 摘要与大小匹配后发布，`prerelease=true`，不替换正式版 latest。
- [Gitee 发布页](https://gitee.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.5_beta6)：`prerelease=true`，更新 API 已暴露 APK 下载地址；沿应用更新使用的地址下载回读，大小和 SHA-256 与本地构建一致。

没有新增测试套件或实机视觉／帧率测量。发布时 `adb devices -l` 无已连接设备，本次未覆盖安装 Beta6；此前手机上的最近一次覆盖安装为 Beta5。彩色文字未进行所有壁纸局部区域的对比度测量。
