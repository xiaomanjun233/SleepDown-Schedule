# 共享预模糊与关闭恢复

用户要求参考 Nexio 最新实现，并明确同意采用共享预模糊。代码范围延续现有玻璃性能分支；不涉及数据、导入或后台。

## 源码对照

GitHub connector 在 2026-09-08 查询：最新发布版为 [v1.4.8-0903](https://github.com/HaoZai000/NexioSchedule/releases/tag/v1.4.8-0903)，最新主分支 commit 为 `77b78f24741d513447715f40b9892228b1cedef8`。

[SharedBlurBackdrop](https://github.com/HaoZai000/NexioSchedule/blob/77b78f24741d513447715f40b9892228b1cedef8/app/src/main/java/com/kyant/backdrop/backdrops/SharedBlurBackdrop.kt) 存在于主分支，发布标签中尚不存在。[CourseCard](https://github.com/HaoZai000/NexioSchedule/blob/77b78f24741d513447715f40b9892228b1cedef8/app/src/main/java/com/haooz/chedule/ui/components/CourseCard.kt) 在共享模式跳过卡片 blur，保留 lens，采样比例 0.48，highlight/shadow 为 null。主分支共享 recorder 本身仍逐帧录制及赋 BlurEffect，也缺少 downsampleScale 相等性字段，因此不能宣称其已包含用户前述所有指纹优化。

## 本次实现

- 本地 Kyant 新增独立实现的共享背景：全分辨率源先完成原有 vibrancy 和 blur，课程卡只保留后续 lens。原有卡片采样比例、高光、阴影、色散、圆角、文字不变，不引入课程分组。
- 背景 producer 后、内容 producer 前录制共享层；静止壁纸按已有指纹复用，换图渐变期间实时录制。相同模糊参数不重新配置效果。仅匹配源对象、半径和增艳参数、且共享层已就绪的课程卡使用；编辑器/自定义参数继续原路径，避免重复模糊。离开首页由节点释放共享层，无全局缓存。
- 横向离屏期间保留既有当前/相邻页卡片节点，只暂停玻璃绘制和效果工作，返回预热区域时复用节点；页面淘汰仍释放。遮挡期间的生产卸载策略继续保留。此项减少滑动中反复创建材质，资源上界仍为已有页面范围。
- 编辑器关闭背景从 560+40 ms 改为 440 ms 无延迟，与面板结束时间一致，Closing 完成提前 160 ms；个性化背景取消 20 ms 关闭延迟。保持位置/圆角轨迹函数、目标比例和缓动，按用户要求改变关闭背景时间。
- 当前周恢复后立即开始原 280 ms 渐入，相邻周在其间继续每批两张恢复；不等待所有相邻页才显现。Revealing 只允许已恢复 key 挂载，防止提前一口气创建相邻页。回归覆盖当前页 key 查询和显现期间相邻页隔离。

共享全图模糊与原逐卡裁剪后模糊在边界的取样不同，不能在没有像素对比时宣称逐像素一致；本次保留视觉参数与装饰，并按用户明确要求学习预模糊。完整往返、浅/深壁纸、窗口边缘、滑块和旋转仍需实机检验。全分辨率共享层增加一个窗口尺寸层，以减少多卡重复模糊，未量化 GPU 内存和收益。

## 验证

36 项应用定向测试及 3 项库内缓存测试通过，0 失败、0 错误；`compileGithubReleaseKotlin` 通过。应用测试沿用 `tmp/glass-motion-tests.init.gradle` 的玻璃范围隔离，不声称全仓测试通过。Kyant 补丁反向应用检查通过。完整打包结果待最终构建补齐。设备安装、视觉验收与性能采集未执行，沿用用户只查源码与既有记录的限制。源码测试不能证明帧时间改善。

构建命令使用 JDK `D:\Android studio\JDK`、既有 Gradle home，执行 `:kyant-backdrop:testAndroidHostTest testGithubDebugUnitTest compileGithubReleaseKotlin assembleGithubRelease -I tmp/glass-motion-tests.init.gradle --no-daemon --console=plain --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8'`。未关闭 R8、资源压缩或 lintVital。

最终完整构建通过：`BUILD SUCCESSFUL in 6m 54s`，R8、lintVital、资源压缩及签名打包全部完成。

- APK：`app/build/outputs/apk/github/release/app-github-release.apk`。
- 生成时间：2026-09-08 15:21:04 +08:00；大小：6,470,790 字节。
- SHA-256：`90AEC2DD8376C6F5CCC993C007657C906931E008B5F341B0469EDCE674A3D570`。
- 包含当前工作树；未安装、推送或发布。
