# 1.2.5 Beta4 发布核验

用户授权：完成本轮课程编辑调整后直接合并 PR #31，发布 Beta4。此前的打包安装授权继续有效；剩余实机交互和帧率测试按用户要求停止。

## 最终调整

- Shortcut 的「移除」图标使用红色；通过菜单 action 的可选图标色复用公共菜单，文字及其他菜单保持原有主题色。
- 缩放角标继续截取课程卡自身的连续圆角轮廓，笔画由 6dp 改为 8dp，端部圆润半径由 3dp 改为 4dp；可见范围缩短，44dp 手势区域不变。
- 末行裁切原因：Pager 申请的上下绘制余量超过父布局高度，普通 height 受到父约束后减少了实际课程区高度。现在允许分页容器以完整高度测量并向顶部扩展，底部编辑余量由 24dp 增至 40dp，滚动底部留白另加系统安全距离。
- 版本为 `1.2.5_beta4`，versionCode 延续 Beta3 的 31；应用更新按 Beta 序号判断版本。

代码涉及首页快捷菜单、公共菜单可选图标色、周视图测量与角标、版本配置。不涉及数据库或导入协议。

## 构建与交付物

- 使用官方 Miuix v0.9.3 加仓库公开补丁的独立工作树构建。
- `assembleGithubRelease` 成功，耗时 7 分 14 秒；包含 Kotlin 编译、R8、资源压缩和 lintVital。
- APK v2 签名验证通过；APK 内包名为 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.5_beta4` / 31。
- 发布名称：`SleepDown 课程表 1.2.5 Beta4`；标签：`v1.2.5_beta4`；文件：`SleepDown-Schedule_v1.2.5_beta4.apk`。
- APK 大小：6,527,449 字节；SHA-256：`b0b5434fd5459f26add08509c0d52183d1ea15588da77d58e702afafc5d699bf`。
- OPPO Find X9（PLJ110，Android 17）无线覆盖安装返回 Success，设备版本及安装包 SHA-256 与上述一致；安装后未自动启动。
- 发布沿用 Beta3 的 Git Credential Manager 认证与本地 API 上传方式，目标为 GitHub 和应用更新实际读取的 Gitee；预发布不替换正式版 latest。

本轮未执行实机交互、帧率及 4×2 桌面预览复测，原因是用户明确要求停止剩余测试。不宣称达到 120 FPS；此前有效基线与测量限制见 [周视图跟进记录](performance/2026-09-10-week-swipe-followup.md)。
