# 同课表滑动：Nexio 与当前 SleepDown 对照

2026-09-08，用户反馈同一课表仍比 Nexio 卡顿。本次仅检查代码和已有记录，没有采集设备帧时间；用户安装的 Nexio 版本尚未确认，不能把主分支实现当作其安装包实现。

## 对照基线

GitHub connector 核实最新正式版为 [v1.4.8-0903](https://github.com/HaoZai000/NexioSchedule/releases/tag/v1.4.8-0903)。最新 master 为 `260db78781c55f87968d091beea0c909ff827e2b`，相比此前 `77b78f2` 只修改统计徽章，玻璃实现没有新增变化。SleepDown 对照当前工作树以及此前安装的 21:53 Release 修复包；没有改动源码。

## 确认的结构差别

| 项目 | Nexio 正式版 | Nexio master | 当前 SleepDown |
| --- | --- | --- | --- |
| 背景模糊 | CourseCard 内逐卡 blur + lens | 共享模糊源，卡内只做 lens | 共享 vibrancy + blur，匹配源和参数后卡内只做 lens |
| 采样比例 | CourseCard 未传自定义比例，不将 master 的 0.48 套用到正式版 | 卡片明确 0.48 | 共享源 0.5；卡片按周传入的课程数量，少于 8 为 1，8–11 为 0.75，12 起为 0.5；编辑模式为 1 |
| 装饰 | highlight=null，但保留 edgeLight；shadow 未显式关闭 | highlight/shadow=null，普通描边 | 保留高光、外阴影、5dp 内阴影；可选底部渐变发光 |
| 降采样组织 | 无 master 共享分支 | 库节点内部缩小采样缓冲，卡片布局保持原尺寸 | 缩小采样子布局再放大，另有全分辨率装饰子布局 |
| 翻页预留 | beyondViewportPageCount=1 | 同样预留相邻页 | 同样为 1，不能解释成我们常驻更多页 |

来源：[正式版 CourseCard](https://github.com/HaoZai000/NexioSchedule/blob/v1.4.8-0903/app/src/main/java/com/haooz/chedule/ui/components/CourseCard.kt)、[主分支 CourseCard](https://github.com/HaoZai000/NexioSchedule/blob/260db78781c55f87968d091beea0c909ff827e2b/app/src/main/java/com/haooz/chedule/ui/components/CourseCard.kt)、[主分支 DrawBackdropModifier](https://github.com/HaoZai000/NexioSchedule/blob/260db78781c55f87968d091beea0c909ff827e2b/app/src/main/java/com/kyant/backdrop/DrawBackdropModifier.kt)、[SharedBlurBackdrop](https://github.com/HaoZai000/NexioSchedule/blob/260db78781c55f87968d091beea0c909ff827e2b/app/src/main/java/com/kyant/backdrop/backdrops/SharedBlurBackdrop.kt)。

比例只能比较目标像素面积，不能推断帧耗时：同尺寸忽略 padding，0.5 与 0.48 的面积差约 8.5%，0.75 则约为 0.48 的 2.44 倍。多课达到 12 门后，继续把 0.5 降至 0.48 不足以作为明确根因修复，且不能免除视觉验证。

## 本地还存在的工作

1. `glass/ui/GlassUi.kt:1142` 起的降采样分支使用采样 Box 和装饰 Box。即使 sampleBackdrop=false 已避免空采样层，装饰仍走 `DrawBackdropNode` 的 `placeWithLayer`，强制 Offscreen；独立高光、内外阴影也仍参与最终合成。已有 cacheDecorations 只复用录制，不等于消除图层合成或保证 GPU 不执行效果。不能再次将“加录制缓存”当作已解决这部分成本。
2. `feature/home/week/WeekScheduleUi.kt:3180` 附近将 `cullHorizontal=false`。因此横向移出视口不改变 viewportMaterialMounted，传入的 renderEnabled 缺少横向暂停信号。框架仍可能裁剪完全离屏的绘制，不能据此宣称 GPU 必然绘制全部相邻页，但应用侧确实没有此前文档所说的横向暂停。节点驻留和是否绘制应分开判断。
3. 双方共享模式均仍逐卡 `recordLayer`，记录卡片对应的共享源区域并应用折射。Nexio 的共享分支没有消除逐卡缓冲，不能把我们缺少同名分支等同于多做一次全屏录制。我们的背景指纹和效果参数缓存反而比所查 master 更严格；Nexio master recorder 仍逐次 record 和赋 BlurEffect。
4. 我们可选底部光照每次 draw 创建渐变 Brush 和 colorStops；属于可核实的 CPU 分配点，但没有证据表明它单独主导帧耗时。可缓存按尺寸、颜色、亮暗、强度区分的画刷，保留实际混合顺序。

## 后续修复优先级（保持外观）

1. 单独修复横向绘制可见性：保留现有节点所有权，带效果外扩和滞回地暂停视口外材质；不能把恢复分批机制重新引入滑动过程。先验证暂停时仍有正确的位置更新，避免再次采样错位。
2. 将逐卡采样尺寸管理移到库内，保持布局与装饰全尺寸，把当前两个子布局的职责收回单节点。采样比例和效果几何保持原值；这一步的目标是减少布局和隔离层，而非降低模糊质量。
3. 审核装饰隔离层的必要性，合并可证明输出等价的重复合成。Plus/Screen 与阴影顺序要求保留，不能直接删除所有 Offscreen，也不照搬 Nexio 的关闭高光/阴影。
4. 缓存底部光照画刷及稳定几何。完成上述单变量验证后，再评估是否需要真正的静态像素预渲染；不再次把 GraphicsLayer 录制误当像素烘焙。

这些是可执行的下一轮方向，尚无本轮前后实机数据，不能排序为已证实的毫秒占比。修改仅此调查文档，无编译、打包、安装及新实机采集；此前 APK 和源码保持不变。
