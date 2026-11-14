# COUI 开关与滑条接入

- 日期：2026-11-06
- 分支：`codex/couikit-dock-fab`
- 父提交：`7fa1a4c2`（组合底栏、Button 与文档）

## 1. 范围

- 把宿主现有的 `NativeSwitchRequest` / `NativeSliderRequest` 兼容入口接入 `third_party/coui-kit` 已有的 `Switch` / `Slider` 组件。
- 凝光 + Android 14+ 优先使用 COUI；ColorOS 私有控件可用时回退原生；旧系统或未启用 COUI 时保持液态。
- 滑条继续遵守宿主的 preview / commit / cancel 事务、snapValue、键盘/无障碍步进和垂直滚动让位。
- 开关继续遵守受控 checked、enabled、compact 和主题强调色。

## 2. 组件参数

| 项目 | COUI Kit 值 |
| --- | --- |
| 开关视觉尺寸 | 44dp × 24dp |
| 开关 thumb | 18dp，padding 3dp |
| 开关点击区 | 最小 48dp × 48dp |
| 滑条最小尺寸 | 144dp × 48dp |
| 滑条轨道左右 inset | 14dp |
| 滑条轨道高度 | 20dp，按压展开 8dp |
| 滑条 thumb | 由 `CouiSliderMotion` 控制，白色 + 阴影 |
| 滑条 snap marker | 2.5dp 白点，位置按 14dp inset 计算 |

来源：

- `third_party/coui-kit/.../switch/Switch.kt`
- `third_party/coui-kit/.../slider/Slider.kt`
- `third_party/coui-kit/.../slider/CouiSliderDefaults.kt`
- `third_party/coui-kit/.../slider/CouiSliderRenderer.kt`

这些组件此前已随 COUI-Kit 接入；本步只增加宿主适配，没有修改其算法。

## 3. 滑条适配

`CouiValueControls.kt` 的 `CouiValueSlider`：

- 用 `CouiSliderDefaults.colors(MaterialTheme.colorScheme.primary, dark, enabled)` 保留宿主强调色和深浅。
- 内部维护未吸附的 `internalValue` 供 Kit 轨道/thumb 使用，避免 snap 回写触发 `syncValue` 取消拖动。
- `NativeSliderSession` 复用原 preview/commit/cancel：
  - 首次变化 `start()`，持续 `onPreview`；
  - `onValueChangeFinished` 提交；
  - 系统取消或垂直滚动接管时恢复初始值并退出 preview。
- `snapValue` 在范围 1.5% 内吸附；释放后外部值回到吸附点。
- 在 14dp 轨道 inset 处绘制与既有控件一致的 2.5dp 白点 marker。
- 垂直滑动未进入水平拖动时主动结束 preview，避免父级滚动后 slider 的 preview 模式卡住。

## 4. 开关适配

`CouiValueSwitch`：

- 直接使用 Kit `Switch` 的 44×24 轨道、18dp thumb、按压/拖动动画和触感。
- 颜色使用宿主 `MaterialTheme.colorScheme.primary` + COUI `CouiSwitchDefaults`。
- `compact` 用 0.8 视觉缩放，保持原 fallback 观感，不改变宿主行高。
- `enabled` 由 Kit toggleable 控制，禁用时保留禁用色和语义。

## 5. 分发

`ProvideColorOsMaterials` 新增 `slider` / `switch`：

1. 凝光 + Android 14+：先 COUI Slider / Switch。
2. 否则 ColorOS 私有控件可用：原生 `ColorOsSlider` / `ColorOsSwitch`。
3. 否则：现有液态 M3 / Kyant 控件。

`lightBackdrop`/`cardLightShadow` 只存在于 Button，不影响本步。

## 6. 验证

已执行：

- `compileGithubReleaseKotlin`：通过。
- `:sleepdown-coui:testDebugUnitTest`：通过。
- 定向 `VisualEffectsCapabilityTest`、`ColorOsDockCompositionTest`、`CouiValueControlsRouteTest`：通过。
- `assembleGithubRelease`：通过（5m37s，R8、资源压缩、lintVital、打包与签名）。APK `tmp/review-apks/SleepDown-1.2.8-beta2-couikut-switch-slider-20261106-cb0b1670.apk`，7,692,584 字节，SHA-256 `CB0B16705491FE33C668B6BC163773B0BE07A19D0A322E170E8BC14E948EF7C5`；V2 签名通过，证书 SHA-256 仍为 `0f2b50df…`。精确 mapping 同目录同名 `-mapping.txt`，156,296,338 字节，SHA-256 `5EC5CB90FD49054361D8BC2D04E0FD978F4A50098AE79C7D902CE34192AD688E`。`com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / versionCode 36`，minSdk 26，targetSdk 36。构建内嵌 revision 为 `652ac337`。

未执行 / 未证实：

- 未在真机核对滑条拖动、fling、snap 回弹、垂直滚动让位和深色对比度。
- 未在真机核对开关 compact、按压拖动和焦点语义。
- Robolectric 不替代 COUI 的实际触摸/GPU 动画。

## 7. 回滚

本步在 `codex/couikit-dock-fab` 上以 `652ac337` 单独提交；回退即可恢复原有原生/液态开关与滑条分发，不影响已完成的 Dock、FAB 和 Button 接入。
