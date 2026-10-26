# 根据实际画面速度重建中断后的弧线

用户验证 `798ccdb2…` 后反馈「像被扯住」，明确要求先搜索抛物线打断动画的做法。接续同一动画工作树与已有未提交改动；保持完整打开/关闭原曲线和原站位热区，沿用只传 APK 到原本人私有 Drive 文件夹的授权，没有安装或 Git 远端动作。

## 查证与实现判断

- [Android Compose 自定义动画](https://developer.android.com/develop/ui/compose/animation/customize) 说明，弹簧在重新设置目标时可保持速度连续，时长型 tween 本身不具备同样的速度连续保证。[Animatable 源码](https://android.googlesource.com/platform/frameworks/support/+/f68402285edc35592203bcd92aaf1af3636464a4/compose/animation/animation-core/src/commonMain/kotlin/androidx/compose/animation/core/Animatable.kt) 的约定是从当前值与速度开始新目标，并提醒取消/结束时的速度保存边界。
- [Apple WWDC23 Animate with springs](https://developer.apple.com/videos/play/wwdc2023/10158/) 同样将位置、速度连续作为可打断交互的重要条件。弹簧不等于必须回弹，也不能据此把已有完整开合的原始时钟再加一层弹簧。
- [SVG 路径规范](https://www.w3.org/TR/SVG11/paths.html#PathDataQuadraticBezierCommands) 将二次 Bezier 定义为当前起点、一个控制点和端点；下述控制点和时间参数的选择是本项目据实际约束推导的实现，不是 Android/Apple 官方指定算法。

上一候选对 raw progress 先制动再起步，同时给新方向原曲线叠加多通道残差。虽然节点处的一阶导数可连续，中间帧仍要向另一条固定旧轨迹纠偏；并且 raw 速度归零会令整个二维物体统一停住，而不是自然转弯。用户的实机观感与这两处机制吻合，故撤销该卡片中断策略，而非继续调补偿系数。

## 新的单一运动段

正常完整开合仍使用原始路线与时长。中断时：

1. 从当前已显示位置/尺寸等通道取起点 `P0`。若仍在原始路线，则通过 `d(frame)/dp * dp/dt` 转为像素/秒速度；若已经中断过，则直接取新运动段的解析速度。
2. 从目标方向的原始端点取 `P2`。中间过程不再参考另一条旧路线的位置，也不添加残差。
3. 按当前 Rect 到端点的实际位置/尺寸距离相对完整行程确定时长 `T`（最短 64ms，最长为对应完整路线时长），避免把「30% 原始时钟」当作「只展开 30%」。
4. 以 `s=t/T`、`q=1-(1-s)^2` 采样二次 Bezier，控制点 `C=P0+v0*T/4`。由 `B'(0)=2(C-P0)` 与 `dq/dt|0=2/T` 可得初速度恰为 `v0`；`dq/dt|1=0` 保证落点速度归零。原始时钟只以线性 tween 表示这段经过时间，不再额外制动或叠加 easing。
5. 再次反向时直接从这一段当前帧与解析速度重建，不等待归位；每张卡片仍有独立会话、时钟和输入热区。

一个二维转弯可以在速度方向变化时保持非零速率；只有一维原路返回才必然经过速度为零。该实现的目标是自然继承动量后转向目标，不强求所有通道先在固定 60ms 内一起停住。

## 性能与验证

仅目标改变时分配通道数组和解析弧线计划。每帧只计算标量多项式，复用既有绘制、表单和全屏模糊；未增加 Backdrop 采样、位图或 Offscreen。系统动画倍率交由现有 Compose 时钟处理。独立背景深度动画保持既有策略。

8 类 106 项定向回归已通过：`InterruptibleMorphTest` 9、`MorphCurveContinuityTest` 8、`RetainedMorphLayerTest` 2、`CourseEditorSessionsTest` 5、`HomePersonalizationGeometryTest` 5、`HomeAnchoredMorphGeometryTest` 45、`HomeMorphPreloadTest` 4、`GlassFrameworkTest` 28，均无失败、错误和跳过。已退役的两项固定制动/原时钟归速断言改为新实际运动段的一条线性时钟与系统倍率验证，未保留已撤销实现的约束。

验证包括实际像素速度连续、独立弧线不被旧中间帧牵引、一次自然转向及精确落点、实际距离决定时长、系统倍率、原站位与多卡独立性；不把 raw progress 导数相等当作实机自然感通过。

106 项回归与完整 `assembleGithubRelease -Psleepdown.sharedViewKitTest=true` 同次调用 6m58s 成功，192 项：26 执行、166 复用，含 R8、资源压缩、lintVital 与正式 v2 签名。66 个变动生产文件构建前后哈希一致；两个生成开关均为 true；实际 APK `b3.p` 金标能力查询/本地门控与新 `MorphCurveContinuity$Flight`（`nb3`）均反汇编核对。

交付为主目录 `tmp/review-apks/SleepDown-world-velocity-arc-gold-20261024.apk`，7,624,231 字节，SHA-256 `b9d600704e75e97bd0f8f7c3f5be5d66a6a3e4ce45344a3c5e75a8488144d277`。包名、版本保持 `com.xiaomanjun.sleepdownschedule`、`1.2.7.1` / 35，正式 v2 证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。已作为新文件上传原本人私有“SleepDown 测试包”；metadata 与目录回读确认名称、大小、目录、未分享和可下载，准确链接见主目录 `tmp/review-apks/remote-delivery-20261023.json`。

未安装、提交、推送、PR、合并或发布；本轮仍没有手机观感或 GPU 帧率验收。
