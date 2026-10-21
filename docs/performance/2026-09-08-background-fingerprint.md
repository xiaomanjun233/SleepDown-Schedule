# 首页背景内容指纹与效果失效收紧

分支：`codex/glass-background-fingerprint`。沿用上一批逐卡渲染与两个指定路线的原生圆角回退。

## 与用户提供方案的对应关系

1. 当前 `DrawBackdropModifier.update()` 已区分普通绘制回调和效果变化；`onDrawBehind/onDrawSurface` 换回调不会直接重建效果。应用入口还稳定了 shape/effects 回调。本次进一步把 `GlassEffectFrame` 中高光、阴影、缩放从模糊/折射观察输入中剥离，装饰变化不再通过同一个状态对象触发无关材质重建。所有模糊、透镜、色散、vibrancy 值原样保留，自定义效果保持原观察路径。
2. 本地 Kyant 的 `LayerBackdropModifier` 此前每次 draw 都执行 `recordLayer`。现增加可选 `recordKey` 提供器：只复用已经完成的录制；null 表示继续实时录制。源对象替换、尺寸、密度、字体比例、布局方向、节点释放都会失效。只在首页独立壁纸 producer 接入，动态课程内容及弹窗采样域不被冻结。
3. 仓库及所采用的 Kyant 2.0.0 没有 `SharedBlurBackdrop` 或 `preRenderModifier`，故没有宣称修复不存在的代码。本次在实际存在的 `HomeWallpaperLayer` 按精确模糊半径记忆 `BlurEffect`，避免仅 alpha/其他重组变化时重新创建相同效果；不引入全屏共享模糊代替原有逐卡采样，也不改变降采样比例。

没有直接把动态 `shapeProvider.innerShape` 求值塞进 equals/hashCode：旧、新回调可能读取不同状态但当时产生相同形状；跳过更新可能继续观察旧依赖。应用入口改用带结构相等策略的派生状态：同形状的新回调不向材质传播失效，但依赖切换到最新回调，新圆角仍会触发更新。动态 uniform 保持原观察链路，没有跨消费者共享可变 Shader。新增 SnapshotStateObserver 回归验证实际失效次数与新旧依赖交接。

## 壁纸缓存边界

`HomeWallpaper` 将实际可见/目标图片是否一致、交叉渐变是否结束交给 producer。换图尚未交接、有上一张图或渐变未结束时 key 为 null，继续逐帧录制。静止 key 包含实际源/模糊/低分辨率图片对象、配置（含裁剪）、质量选择和预览模糊，producer 另外加入当前采样亮度。图片消失或页面离开时清除 key；窗口变化由节点负责。140 ms 换图动画、原有裁剪、亮度叠加顺序和模糊曲线不变。

缓存复用的是节点原有的背景录制，没有新增 GPU 层。`ProducerRecord.Reused` 是录制复用诊断事件；它不证明 GPU 没有执行模糊，也不代表帧已提交。卡片移动改变采样区域时仍正常重新采样，不能因壁纸静止就冻结卡片的局部采样结果。

涉及文件：`ScheduleAppUi.kt`、`HomeScheduleUi.kt`、`glass/SleepDownGlassSurface.kt`、`glass/GlassSampling.kt`、本地 Kyant producer/cache/test 及构建测试接入。数据库、导入、后台链路未修改；工作树其他既有改动保留。

## 验证与交付

新增库内主机测试覆盖：静止录制复用、未完成录制不可复用、动态帧清除旧指纹、图片/亮度/尺寸/密度/方向变化以及释放重挂载。应用测试覆盖装饰与材质输入的隔离。

库内测试接入采用 Android KMP 的 [主机测试配置 API](https://developer.android.com/reference/tools/gradle-api/9.0/com/android/build/api/dsl/KotlinMultiplatformAndroidLibraryExtension)。验证命令：

```powershell
$env:JAVA_HOME='D:\Android studio\JDK'
$env:GRADLE_USER_HOME='C:\Users\23085\.gradle'
.\gradlew.bat :kyant-backdrop:testAndroidHostTest testGithubDebugUnitTest compileGithubReleaseKotlin assembleGithubRelease -I tmp/glass-motion-tests.init.gradle --no-daemon --console=plain --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8'
```

应用测试沿用上一批玻璃范围隔离脚本，不声称全仓测试通过。36 项应用定向测试及 3 项库内测试全部通过（0 失败、0 错误），包括实际形状依赖失效观察测试。最终整轮构建 `BUILD SUCCESSFUL in 7m 20s`：`compileGithubReleaseKotlin`、R8、lintVital、资源压缩和签名打包全部通过，未跳过发布检查。Kyant 补丁反向应用检查通过，producer 上游文件 blob 哈希匹配记录的 2.0.0 基线。

构建过程先修复遗漏的 `SideEffect` 导入；随后一次构建遇到 lint Kotlin 分析器内部异常 `Unexpected owner function: null` 和 2 GB 堆不足提示。最终构建以新的单次进程、单工作线程和临时 3 GB 堆运行，未关闭检查，未更改全局 Gradle 配置。

实机安装、视觉与性能采集：**未执行**，用户此前要求只查代码和已有记录。需手机/平板复验换壁纸全过程、裁剪、亮度/模糊滑块、旋转/自由窗口、快速横滑、开关弹窗；不能以逻辑测试替代视觉一致性与帧时间结果。

## APK

- `app/build/outputs/apk/github/release/app-github-release.apk`，githubRelease，1.2.4（30）。
- 生成时间：2026-09-08 12:45:01 +08:00；大小：6,454,406 字节。
- SHA-256：`333C1131A2A46C7B17A1A5B18FA6CB0F2B4C8A67C7EB225ADFF7BB2898E82811`。
- 包含当前工作树已有改动；本次未安装、推送或发布。
