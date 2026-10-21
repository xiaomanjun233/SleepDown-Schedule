# 预制高光后再对比 Nexio

用户反馈预制描边改善不明显。2026-09-08 再次通过 GitHub connector 核实 master 仍是 `260db78781c55f87968d091beea0c909ff827e2b`。没有新上游性能提交可直接同步。对照最新 CourseCard、MainScheduleScreen 和当前库内实现，本轮不继续修改渲染源码。

1. **内外阴影仍是明确额外工作。** Nexio master CourseCard 设置 highlight/shadow=null 且不传 innerShadow。我们移除方向高光后仍保留两种阴影。`InnerShadowModifier.kt` 持有 Offscreen GraphicsLayer 及 BlurEffect，普通课程卡半径 5dp；记录缓存只省重复录制，最终 drawLayer 仍存在。20 张已挂载卡片意味着最多额外 20 个内阴影节点层和 20 个外阴影节点层，不能把节点数当成同帧实际 GPU 分配或执行次数。
2. **折射参数不同。** Nexio 调用 lens(radius,strength)，默认 depthEffect=false；SleepDown courseCard 继承 GlassMaterialSpec.depthEffect=true。我们保留的边缘深度方向也参与计算和外观。不能不经视觉比较直接关闭；不要把它说成另一个独立全屏模糊。
3. **共享前处理不同。** Nexio master 只有 blur；我们仍有 vibrancy → blur，均保留效果在共享层。两边逐卡都需采样和折射，不存在其完全免除逐卡纹理的证据。
4. **采样比例与驻留已接近。** 多课时 0.5 对 0.48，双方 Pager 预留相邻页 1；本地已去掉滑动可见性切换并移到库内采样。不能再以增加滚动剔除/重建作为本用户任务的默认方案。
5. **首页额外录制需区分状态。** 本地 screenGraphicsLayer 缓存分支在遮挡/Morph 时使用；普通 drawContent 与缓存捕获分支不能一概计为每帧双重全屏离屏渲染。contentBackdrop 供其他真实玻璃消费，也不能直接删除。

源码来源：[Nexio CourseCard](https://github.com/HaoZai000/NexioSchedule/blob/260db78781c55f87968d091beea0c909ff827e2b/app/src/main/java/com/haooz/chedule/ui/components/CourseCard.kt)、[MainScheduleScreen](https://github.com/HaoZai000/NexioSchedule/blob/260db78781c55f87968d091beea0c909ff827e2b/app/src/main/java/com/haooz/chedule/ui/screens/MainScheduleScreen.kt)。

结论：预制高光没有移除全部装饰效果成本；目前最明确的剩余差异是阴影层与材质参数。没有本次 GPU trace，不能宣称其占比或性能根因已确认。用户未授权降低阴影、深度和增艳外观；本轮仅保留可核实的对照，未采集设备、未以进一步删效果出试验包。
