# 课程表滚动、遮挡恢复与 Morph 实施记录

> 后续变更：用户反馈后已移除课程表的活动分组渲染路径，并定向恢复今日助手与详细设置的 v1.2.3 裁剪方式。本文为上一批实施记录，最新状态见 [无分组跟进记录](2026-09-08-native-routes-without-card-groups.md)。

日期：2026-09-08。分支：`codex/glass-motion-performance`。

代码实施和本地构建已完成；视觉等价与性能收益尚未通过手机、平板验收。按用户要求，本次没有安装、启动、操作设备或重新采集性能数据。

## 当前生效范围

| 项目 | 实施结果 | Release 状态 |
| --- | --- | --- |
| 课程卡测量 | 宽度改取 `coordinates.size.width`；保留原 `boundsInRoot()` Morph 锚点；离屏检测采用 `boundsInWindow(clipBounds = false)` | 生效 |
| 滚动分组 | 整页和列级备用路径都从完整几何生成固定成员分组，再选择整组；组 key、紧边界不随成员进出视口重算 | 生效 |
| 材质剔除一致性 | 同组采样与逐卡装饰共享可见成员集合；进入使用原预热距离，退出使用两倍距离形成滞回 | 生效 |
| 表头位移 | 连续偏移在 `graphicsLayer` 读取；星期列表只随页号与相邻方向变化 | 生效 |
| Kyant 接入 | 本地 Android 源码模块替代 2.0.0 二进制；保留上游包名、许可证、版本及逐文件 SHA | 生效 |
| 节点更新 | 等价形状提供者可比较且保留节点缓存；位置/表面回调变化不直接重建材质效果；只有显式完整输入 key 才允许跳过效果求值 | 生效 |
| 逐卡装饰 | 跳过空采样层，继续使用原裁剪、隔离、高光、内外阴影与混合顺序 | 生效 |
| 详细设置 | 动画状态下移至绘制/图层；固定镜像模糊对象复用；源内容阈值单独派生 | 生效 |
| 遮挡保留节点 | 保留整页分组与逐卡材质节点，暂停时跳过采样/装饰录制及效果更新，恢复可复用未变化的分组效果 | 诊断候选，默认仍为原卸载恢复 |
| 编辑器固定材质宿主 | 开闭轨迹预采样固定包络，逻辑矩形/圆角/采样原点与宿主尺寸分离，内容保持原尺寸；轮廓光纳入同一固定宿主 | 诊断候选 |
| 个性化固定材质宿主 | 手机和平板共用固定包络主面板，保持原材质和源内容运动；Open 返回普通主面板 | 诊断候选 |

仍保留每组八张、原采样比例、空间分组填充/面积限制和相邻一页。没有增加常驻页数。完整组的可见性变化仍可挂载/卸载整组；这里消除的是滚动时的成员重排，不能解释为完全没有节点生命周期变化。

## 视觉约束与候选边界

没有降低模糊/折射质量、改变色散、动画时长或缓动，没有非等比缩放整块面板，没有进一步延后表单，也没有开启原来关闭的 aura/渐进模糊。详细设置仍使用连续动态模糊及十层羽化。

固定包络面积上限沿用 1.65 倍目标面积；超过上限继续原路径。逻辑矩形以原整数像素网格对齐；几何越界或效果 padding 超出容量会显式失败，避免悄悄裁掉画面。Open 释放运动专用宿主，保留常规表单裁剪。每个节点仍独占 RuntimeShader；没有全局共享可变 shader。

固定采样区域、移动裁剪、高光和内容交接的像素等价尚需实际渲染对照，特别是高分辨率壁纸边缘、连续圆角与局部采样边界。JVM 几何测试不能证明 RenderThread/GPU 不再分配纹理，也不能替代截图和 FrameTimeline 验收。

保留节点候选仍沿用原批次节奏和 280ms 恢复渐入，以便单独比较生命周期。没有把透明挂载当作 GPU 预热完成。暂停恢复未通过实机验收前，不将其设为生产默认。

## 诊断开关

仅 `debug`、`benchmark` 读取以下属性。`release` 明确固定为 `legacy` 与 `false`；已检查生成的 BuildConfig，即使传入候选属性也不会启用。

```powershell
# 三种遮挡对照；一次只改变一个变量
'-Psleepdown.glassOcclusionMode=legacy'
'-Psleepdown.glassOcclusionMode=retained'
'-Psleepdown.glassOcclusionMode=live'

# 独立开关；首轮与 legacy 遮挡组合，隔离固定纹理宿主的影响
'-Psleepdown.glassFixedMorph=true'
```

`live` 同时关闭课程材质遮挡和首页缓存复用/冻结背景路径，作为实时绘制对照。不能仅因这个模式减少恢复等待就认定它更快，必须统计完整打开、关闭与背景绘制成本。

诊断计数：`Kyant.Sample.*`、`Kyant.Highlight.*`、`Kyant.Shadow.*`、`Kyant.InnerShadow.*` 包括创建、释放、录制和尺寸变化；`Kyant.ProducerRecord.*` 是 producer 录制信息。装饰也进入应用消费者计数。

`RecordedPixelArea` 是累计录制像素量，**不是 GPU 分配或驻留内存**。Producer 的图层由原 Compose 所有者管理，其计数只描述录制，不冒充 native 分配事件。

详细设置新增 `GlassMotion.Detail.InputThroughCapture.Microseconds`、`Detail.Save.Microseconds` 和 `Detail.InputToOpenDispatch.Microseconds`。它们拆分动画前的捕获与保存阶段；真正输入到首帧、页面准备、帧提交及 GPU 完成仍须结合系统 trace，不能用 Open dispatch 时间替代。

## 验证结果

- `compileGithubReleaseKotlin`：通过。
- `assembleGithubRelease`：通过，执行了 R8、资源压缩、lintVital、签名及打包；没有使用跳过资源压缩参数。
- 定向 JVM 测试：33 项全部通过，包括原框架 27 项、新增分组/预热/包络 4 项、现有效果兼容 2 项。默认配置与 `fixedMorph=true + retained` 配置都通过。
- 测试编译限定为 glass 目录。修复该目录原有旧包名 import；两个旧断言对齐本次开始前的实际值：恢复渐入 280ms、Popup depthEffect=true。没有为通过测试修改生产材质或动画。
- `git diff --check`：通过。
- Kyant 上游补丁及三份本次增量审阅补丁：反向 `git apply --check` 通过；没有实际回退文件。
- 设备型号/系统/实机输入/帧指标/截图差分：未执行，用户限定仅代码与已有记录。不存在本次 FPS、P90 或视觉通过结论。

最终构建命令：

```powershell
$env:JAVA_HOME='D:\Android studio\JDK'
$env:GRADLE_USER_HOME='C:\Users\23085\.gradle'
.\gradlew.bat testGithubDebugUnitTest compileGithubReleaseKotlin assembleGithubRelease `
  -I tmp/glass-motion-tests.init.gradle `
  '-Psleepdown.glassFixedMorph=true' '-Psleepdown.glassOcclusionMode=retained' `
  --no-daemon --console=plain --no-parallel --max-workers=2
```

APK：`app/build/outputs/apk/github/release/app-github-release.apk`，6,470,790 字节。
SHA-256：`DAB91CCF71375963906663568577EE17EA661703B58FE5086FAE01009CD01F34`。
该 APK 包含工作树原有其他改动，不作为已完成实机验收的发布候选。

## 变更与回退边界

主要源码：`feature/home/week/WeekScheduleUi.kt`、`glass/`、`feature/course/editor/CourseEditorContainerOverlay.kt`、`feature/home/overlay/HomeAnchoredMorphOverlay.kt`、`transition/legacy/AnchoredDetailActivityMorph.kt`、`MorphSnapshotBackground.kt`、`app/ui/ScheduleAppUi.kt` 中相关转场诊断及实验分支。

第三方代码：`third-party/kyant-backdrop/`；上游差异：`patches/kyant-backdrop-2.0.0-sleepdown.patch`；接入：根 Gradle/settings 和 app Gradle。测试：`glass/GlassFrameworkTest.kt` 与 `GlassMotionAllocationTest.kt`。

未修改数据库、导入、后台或用户数据链路。工作树中这些模块的其他已有改动不是本次工作。

未将用户原有未提交修改混入提交，未推送、未创建远端 PR。当前保留工作树修改及以下增量审阅/回退材料：

- `tmp/glass-motion-review/01-scroll.patch`：滚动及分组。
- `tmp/glass-motion-review/02-material-morph.patch`：材质与 Morph；混合修改文件相对任务开始时备份生成。
- `tmp/glass-motion-review/03-detail.patch`：详细设置与诊断；混合修改文件同样使用任务开始时备份。
- 新增文件直接列在上述目录及 Git 未跟踪清单中；三份增量补丁不包含新增文件、Gradle 接入或测试，不是完整分发包。

后续提交时按依赖接入、材质/候选、滚动、详细设置/测试划分；混合修改文件须按上述任务基线逐块暂存。回退必须先 `--check`，不得覆盖用户原有修改。

## 尚待授权设备验收

手机和平板分别在明暗/高分辨率壁纸、稀疏/8/12/20 张课表测试双轴拖动、反向、编辑开闭保存、个性化展开/滑块、详细设置往返；覆盖立即返回、恢复中再打开、旋转/自由窗口和课程变更。

每项至少五轮同条件对照，记录 P50/P90/P95/P99、超时帧、输入到首帧和完整恢复耗时。P90 改善需超过重复波动，P50/P95 不得有超过 5% 的无解释退步；检查文字重影、材质跳变、圆角、高光、采样偏移与资源释放。通过之前保持生产策略。
