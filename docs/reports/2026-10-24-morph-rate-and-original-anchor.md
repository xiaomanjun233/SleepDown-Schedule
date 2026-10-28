# 首页动画速率与原站位重开

接续 `codex/sharedviewkit-ai-history` / `f0d0f8ba` 与全部未提交改动。用户反馈上一候选 `eaaf6f19…` 虽支持打断，但动画速率混乱；进一步明确单卡连续打断是「关闭途中，卡片尚未归位，点击原站位即重新打开」，不需要追着移动中的卡片点击。保持既有只传包到本人私有 Drive 文件夹的授权，不安装、不提交或推送。

## 根因与修正

- 上轮给已有位置/尺寸曲线的原始时钟再次套用 `spring(280)`，背景改用 `spring(140)`，丢失各路线时长，稳定/可输入状态被尾段延长。本轮正常开合恢复路线时长：课程 380/440ms，个性化 340/220ms，一级菜单 260/300ms；背景恢复原有延迟及 easing。
- `MorphRetargetState` 只对中断运动作速度续接：保留初值与速度，逆向速度先在最多 60ms 内制动，再以有限时长的 Hermite 曲线到达目标，时间按剩余距离缩放。峰值速度由路线时长确定，并保留已有的同向速度，不通过重复点击叠加弹簧能量。准备新请求及静默退出明确 reset；同卡重开保留状态。续接的帧间时间按 `MotionDurationScale` 累加，跟随系统动画倍率，倍率为 0 时直接完成；改变倍率也不会重新计算绝对进度导致跳帧。
- 原重复打开判断将 `request ?: retainedRequest` 作为同一活动课程，单独一张卡正在关闭时再点它只写入 null，未真正重开。判断迁入 `CourseEditorSessions.consumeActiveTap`，关闭中的同卡放行，复用原会话。
- `RetainedMorphLayer` 在回程期间保留初始 `sourceBoundsInRoot` 的独立热区，运动表面远离原位时，原站位仍可触发 `reopen(session)`。打开途中的点击路由也先查固定原站位，再查可见卡片，不依赖背景缩放后移动的源 View。
- 同一内容是否续接按存续会话与 Closing 阶段判断，不要求进度大于 0：卡片已归位但背景尚在收尾时也复用已布局内容，避免重置为 Preparing 后等不到新的尺寸回调。课程和个性化两处一致处理。
- 独立会话、GPU 录制及共享背景模糊继续复用。新热区无绘制、无材质采样；计划在一次打断时创建，不截位图、不重复挂表单。几何与叠化窗口沿用上一候选，本轮修正时钟与原站位输入。

## 验证

系统倍率修正后的 `testGithubDebugUnitTest` 定向 4 类 42 项通过（3m26s，0 失败、错误或跳过）：`InterruptibleMorphTest` 7、`RetainedMorphLayerTest` 2、`CourseEditorSessionsTest` 5、`GlassFrameworkTest` 28。此前 40 项及两轮 Release 均通过；收尾帧续接条件修正后，以同一次 Gradle 调用重新运行上述 42 项和完整 Release，最终结果在交付记录补充。中间产物均未复制交付。

包括正常时钟逐帧符合各自时长并准时结束、背景与原 tween 的延迟/曲线逐帧一致、边界内制动和峰值速度检查、2 倍系统动画倍率逐帧制动位置、关闭系统动画时立即完成；真实触摸测试将运动表面放在另一位置，连续点空站位 8 次均重开，内容仅挂载一次；同卡关闭不再被防重复判断吞掉。保留单卡快速反向、B 的独立逐帧轨迹与 A/B/A/C 会话隔离回归。

## 最终交付

含收尾帧续接修正的最终源码，在同一次调用中完成上述 42 项定向回归与 `assembleGithubRelease -Psleepdown.sharedViewKitTest=true`，6m45s 成功（192 项：25 执行、167 复用），含 R8、资源压缩、lintVital 与正式签名。63 个变动生产文件的构建前后哈希一致；实际 APK 的 Kit 能力查询路径及本地门控已验证，两个生成开关均为 true。

候选为主目录 `tmp/review-apks/SleepDown-morph-rate-anchor-20261024.apk`，7,624,231 字节，SHA-256 `ee638f749b2f48cc792762ea8883d88bf2071e980baebfbc021adc9b19ecd7d1`。身份保持 `com.xiaomanjun.sleepdownschedule`、`1.2.7.1` / 35，正式 v2 证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。已作为新文件上传原本人私有“SleepDown 测试包”，metadata 和目录列表回读确认名称、字节数、目录归属、未分享及可下载；没有覆盖旧候选。准确链接记录在主目录持续更新的 `tmp/review-apks/remote-delivery-20261023.json`。

没有安装、提交、推送、PR、合并或发布。日志、APK 与清单保持 Git 忽略；单元/Compose 宿主验证不代表手机视觉速率或 GPU 帧率验收。
