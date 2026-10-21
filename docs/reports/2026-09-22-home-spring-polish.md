# 首页弹簧曲线、切换圆角与重组开销

日期：2026-09-22。继续普通版 main / Beta9，不更新 exp。

## 用户反馈与改动

- 上版 230 ms 的固定时长曲线显得生硬且过快，用户要求非线性回弹、继续打磨卡顿，并给首页/设置页切换加圆角。
- 六组进度改用欠阻尼弹簧（dampingRatio 0.74、stiffness 260、visibilityThreshold 0.0015），不限制位移到 0–1，保留真实过冲与回落。分组间隔 18 ms，最多延后 90 ms；延迟遵循系统动画倍率。取消旧目标时记录各组最后一帧速度，反向切换继承位置与速度，不再重复起步延迟。
- 零初速度的理想弹簧解析曲线在约 290 ms 到达首个峰值，过冲约 3.15%；这只是参数检查，不是设备帧率或实测耗时。页面保留与玻璃采样持续到全部组落稳。
- 首页/设置页外层、两页背景、壁纸色调与首页顶栏共用运动期圆角：最大 32 dp，接近端点时逐步归零，停止后移除裁切。顶栏仅裁切顶部两角；日周页不额外裁切页面圆角。背景仍使用原来的单一 producer。

## 有代码证据的开销

- `SinglePillWeekScheduleScreen` 原先在 `onGloballyPositioned` 写入 `overlayHostBounds`，又在整页组合中读取该值传给编辑浮层。整页横移每帧都会改变位置，因而使整页重新执行组合。改为传递稳定 State，只在存在编辑请求的 `WeekEditOverlayHost` 内读取；坐标保持更新，普通切换不订阅它。
- 根页面的详情稳定帧捕获原先在日周或首页/设置运动期间仍会录制整页。现在切换期间暂停这条无关录制，停稳恢复；明确请求的 clean-frame 捕获仍保留优先级。没有冻结正在运动的课程玻璃。

## 验证

- `assembleGithubRelease` 通过（4 分 4 秒），包含 Kotlin 编译、R8、资源压缩、lintVital 与 Release 签名。`apksigner verify --print-certs` 通过，证书与现有 Release 一致。
- 包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.6_beta9` / versionCode 33。APK SHA-256：`B2E8318B355884A6A5D01E550444D81D82C2A7EBBD14A99D4BDAA36F7FE9A2A9`，交付文件为工作树根目录的 `SleepDown-Schedule_v1.2.6_beta9.apk`。
- 未增加只复述视觉参数的测试；核对反向取消的速度保存、延迟随系统倍率、过冲没有被 clamp、State 延后读取与两页背景/内容共用圆角路径。
- 打包前后 ADB 均没有连接设备，本次未覆盖安装，未实机确认手感或量化帧率改善。
