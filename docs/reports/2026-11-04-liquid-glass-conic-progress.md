# 液态玻璃锥光进度条复刻

## 目标澄清

用户先纠正：昨晚接入的是 `COUIIntentSeekBar` 的锥光（conic light）进度条，不是普通 `COUISeekBar` 的 Glitter 拖动闪光。原厂调用点为：

```java
// E:/COUI/work/src7/sources/com/coui/appcompat/seekbar/COUIIntentSeekBar.java
setUseMaterialEffect(1);
setConicLightEnabled(true);
setConicLightColor(accentColor);
setPlaybackState(PlaybackState.PLAYING);
setShowThumb(false);
setProgress(progress);
```

播放器侧参考 `E:/COUI/work/jadx_suiplugin_full/.../media/s1.java`，使用 `DirectionAwareSeekBar`，底层同样是 `COUIIntentSeekBar`。上一轮已在凝光模式通过反射直接挂载原生 View；本轮要求用 Compose 复刻同一进度条并放入液态玻璃模式，同时让液态模式的亮光描边与凝光模式一致。

## 原厂数值与绘制链路

从 `E:/COUI/work/src7/sources/com/coui/appcompat/seekbar/` 与 `E:/COUI/work/plugin-resources.txt` 核对：

| 项目 | 原厂值 |
| --- | --- |
| Intent 默认轨道/进度高度 | 4dp |
| 轨道圆角 | 2dp |
| 轨道颜色 | 调用方传入（本项目为前景色 15%） |
| 进度颜色 | `setConicLightColor` 同时写入进度渲染器，固色，无渐变 |
| 亮光描边 | 播放中取整条轨道的 1/5，透明 `0x00FFFFFF` → 白 `0xFFFFFFFF`；进度小于 20% 不绘制 |
| 锥光层 | 宽 = 轨道宽度 + 600px 水平留白，高 450px，`BlendMode.SCREEN` |
| 锥光参数（PLAYING） | height 0.6、heightFalloff 0.6、coneK 0.2、secondConeK 0.2、w0 0.008、alpha1 1.0、alpha2 0.6 |
| 固定光斑参数 | spotRadius 500px、axialFalloff 2.0、radialFalloff 1.0、threshold 0.2 |
| 入场弹簧 | 形状 response 0.9 / bounce 0，透明度 response 0.3 / bounce 0 |
| 小于 20% 的锥光 | `cubicBezierY(progress / 0.21)` 线性收敛高度与 falloff |

`IntentConicProgressRenderer` 先画固色进度，再叠加轨道 1/5 宽的白色亮边；`COUISeekBarCore.onDraw` 在进度之后调用 `drawEffects`，因此锥光覆盖在进度之上。锥光只出现在进度头部的后方（`q.x < 0`），RTL 时镜像。

## 实现

新增 `feature/agent/AgentConicLight.kt`：

- 原样搬运 `ConicLightShaderParams.AGSL_CODE`（去除注释，逐 token 与反编译源核对一致，2767 个非空白字符）。
- `RuntimeShader` + `Paint(BlendMode.SCREEN)`；按原生 450px 高、300px 水平留白、500px 光斑绘制。
- 复刻 `ConicLightEffectRenderer` 的 PLAYING 参数、20% 以下的 bezier 收敛，以及形状/透明度两段入场：形状 900ms、透明度 300ms。
- 仅 API 33+ 创建；构造失败或低版本使用紧凑径向光近似，不阻断卡片。

修改 `feature/agent/DayAgentProgressBar.kt`：

- 凝光模式继续使用原生 `COUIIntentSeekBar`；液态玻璃模式使用 Compose 复刻。
- 轨道 4dp、圆角 2dp、前景色 15% 轨道、强调色实心进度。
- 亮边宽度为整条轨道的 20%，从透明白（`0x00FFFFFF`，不是透明黑）到白，只在进度 > 20% 且已展开时绘制；这与 Compose 默认 `Color.Transparent` 的黑底插值不同，避免亮边中段出现灰带。
- 锥光裁剪在轨道中心上下 12dp（与原厂 24dp 高 View 的可见范围一致），不改变卡片布局高度；RTL 镜像整条进度条。
- 无进度时仍回到原 1dp 分割线；原生路径失败时也回退到同一 Compose 锥光条。

## 补正：外卡片光感描边与锥光扩散（2026-11-04 下午）

用户随后澄清两处：

1. “光感描边”指今日助手**外卡片**的光感轮廓，不是进度条头部亮边。已在液态玻璃模式复用凝光卡片的原生外描边：
   - `NativeMaterialSurfaceRenderer.kt` 增加 `NativeStrokeOverlayRequest` 与 `LocalNativeStrokeOverlayRenderer`。
   - `ControlMaterialProvider.kt` / `ColorOsMaterialProvider.kt` 在液态模式仍仅提供 `strokeOverlay`，不启用其他原生材质。
   - `ColorOsCourseCard.kt` 新增 `ColorOsCardOuterStroke`：复用 `NativeMaterialSurface` 的 Dock 样式，只开 `TYPE_FRAMEWORK_CIRCLE_2` 描边，关闭模糊、着色、跟手光；`ColorOsContext.kt` 新增不依赖凝光模式的 `rememberColorOsStrokeContext`。
   - `DayAgentCard.kt` 在液态模式且 ColorOS 能力可用时绘制该原生描边，并关闭 Kyant 高光；共享的 `verticalGlassAccent` 边框保留。非 ColorOS 或原生描边不可用时继续用原 Kyant 高光。
2. 复刻进度条的光尾被布局裁切。已删除 `DayAgentProgressBar.kt` 中轨道中心上下 12dp 的显式 `clipRect`，并把助手卡片展开/收拢的 `expandVertically` / `shrinkVertically` 改为 `clip = false`；锥光现在只受卡片圆角轮廓约束，可以像原版一样向轨道上下弥散。低版本径向光回退也移除同一裁切。

## 验证

- `compileGithubReleaseKotlin` 通过（`scale` 的 pivot 参数已改为 `Offset`）。
- 最终完整签名 `assembleGithubRelease` 通过（4m15s，含 R8、资源压缩、lintVital 与 GitHub 渠道金标），产物 `1.2.8_beta2 / 36`，7,893,241 字节，SHA-256 `f35047aaf51048f76b095fad8ce58a1d1dfd9f7a6a579714def976038369c2b5`，保全 `tmp/review-apks/SleepDown-1.2.8-beta2-week-header-card-motion-20261104-f35047aa.apk`（与周视图顶栏／卡片伸缩同包）。
- 当前 SDK 5038、默认 5037 与 mDNS 均无可用 ADB 设备，未安装、未做真机截图对比；液态模式外描边、锥光扩散与凝光一致性待实机确认。
