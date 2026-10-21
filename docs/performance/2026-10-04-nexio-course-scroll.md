# 最新 Nexio 课程卡滑动对照与优化

## 参照基线

2026-10-04 读取两个远端 `master`，没有使用本地旧 Nexio checkout：

| 来源 | 最新提交 | 版本 / 提交时间（北京时间） |
| --- | --- | --- |
| [GitHub](https://github.com/HaoZai000/NexioSchedule/commit/a23c602ab623fd9e939b78e5325871f1561b7981) | `a23c602ab623fd9e939b78e5325871f1561b7981` | v1.6.2beta4 / 2026-10-03 21:44:18 |
| [Gitee](https://gitee.com/com_haooz_account/hyper_schedule/commit/821e779884b15d19e5872d65b0ca335e36a9d1c9) | `821e779884b15d19e5872d65b0ca335e36a9d1c9` | v1.6.1beta1 / 2026-10-02 16:38:42 |

GitHub 源码通过 Codex GitHub plugin 读取，Gitee 通过公开 API 读取，文件均固定到上述提交。两边 `DrawBackdropModifier.kt` 的 blob SHA 都是 `9539b0fd27488f0bb97247eb5a418361d42bc8ed`，`SharedBlurBackdrop.kt` 都是 `ccb6c1b804373992fcebd1d907e94a375ef0eeaa`；课程卡文件有版本差异，但以下滑动优化在两边均存在。

SleepDown 在 `origin/main` 的 Beta2 基线 `62c704ed1ad30c8f520115a2d14fee52a56c6de0` 上继续处理，保留本轮作息管理和课程自动勿扰/静音改动。没有改回 Beta1，没有变更版本号或发布。

## 代码证据与落实

1. Nexio 的 [GitHub CourseCard](https://github.com/HaoZai000/NexioSchedule/blob/a23c602ab623fd9e939b78e5325871f1561b7981/app/src/main/java/com/haooz/chedule/ui/components/CourseCard.kt) 与 [Gitee CourseCard](https://gitee.com/com_haooz_account/hyper_schedule/blob/821e779884b15d19e5872d65b0ca335e36a9d1c9/app/src/main/java/com/haooz/chedule/ui/components/CourseCard.kt) 都只在按压、拖拽和涟漪期间挂缩放层。SleepDown 已有同样的条件层，因此没有重复移植；修正周卡 `layerOffset == null` 被 `null != 0f` 误判为运动的问题，默认无偏移的卡片不再挂空变换层。
2. Nexio 保存非观察型 `LayoutCoordinates`，滑动中跳过交互边界计算，长按前按需刷新。SleepDown 周卡改为同样保留坐标引用，普通横向翻周、纵向滚动时跳过 `boundsInRoot()` 和复制目标通知。停滑后下一帧刷新一次；点击、快捷菜单和拖拽请求构造前立即刷新。初次定位、编辑、复制、涟漪、落地和活动菜单仍实时报告边界，玻璃采样节点继续跟随背景。
3. SleepDown 文字原来即使已冻结对比度，也会在每段文字的定位回调中先执行 `localToWindow()` 并维持延迟采样协程。现在冻结时只保留坐标引用；页面恢复后沿用既有 `LaunchedEffect(background)` 更新阴影。关闭自适应对比度的文字不再挂这个定位回调。
4. Nexio [DrawBackdropModifier](https://github.com/HaoZai000/NexioSchedule/blob/a23c602ab623fd9e939b78e5325871f1561b7981/app/src/main/java/com/kyant/backdrop/DrawBackdropModifier.kt) 让共享壁纸和每卡采样缓冲保持相同的 0.48 比例，直接按采样像素平移共享层。SleepDown 已有该分支，但课程组件把共享消费者固定为 1.0，实际不能进入它。本轮把共享消费者固定为已有 `SharedBlurSampleScale`（0.48），共享层就绪前后的缓冲比例一致，继续保留此前防止首帧错位的生命周期修复。

节点内部同步缩放折射的尺寸、dp 密度和圆角参数，最终放大回原布局尺寸。只有背景采样与折射缓冲降低分辨率；文字、着色、高光、轮廓光、外阴影和内阴影仍以原尺寸绘制。固定 Morph 保持 1.0，非共享材质继续使用既有自适应采样。本轮没有改动材质生命周期和 shader 所有权。

单张共享消费者的纹理面积由约 `W × H` 变为 `(0.48W) × (0.48H)`，不计取整和效果留白约为原来的 23%。这是代码中的缓冲面积变化，不代表整帧耗时降低 77%，也不是实机 FPS 测量；折射纹理精度的变化仍需用户观察。

## 验证

按用户「不要再检查了，安装我自己测」的要求，仅执行签名 Github Release 打包，不追加测试套件、自动启动或实机滑动检查。

- 使用规定的 JDK、Gradle 用户目录和 `assembleGithubRelease --console=plain --no-parallel --max-workers=2`，完整构建成功，耗时 6 分 9 秒；包含 Kotlin 编译、R8、资源压缩、lintVital、打包和签名配置验证。
- APK：`app/build/outputs/apk/github/release/app-github-release.apk`，包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7_beta2` / 34，大小 `6,925,623` 字节。
- SHA-256：`BA88CE36D250CEEEA1641BAD35A18887957CE95D115BF291F40343AFBF06EA05`。
- 构建结束时 SDK ADB `-P 5038 devices -l` 没有在线设备；mDNS 无无线调试服务，原地址 `192.168.1.10:39747` 重连超时（10060）。未执行覆盖安装，未启动应用。
- 本轮没有实机帧时间、FPS 或视觉验收结果。代码和签名包已准备完成，实际滑动体验由用户测试。

### 后续覆盖安装（2026-10-04）

用户返回后，SDK ADB 5038 已通过无线调试服务连接到 PLJ110，当前地址为 `192.168.1.10:42533`。确认 APK 的 SHA-256 与上述产物一致后，通过明确设备目标执行 `install -r`，结果为 `Success`。已覆盖安装 `1.2.7_beta2` 优化包，保留应用数据；未自动启动或执行实机滑动测试。
