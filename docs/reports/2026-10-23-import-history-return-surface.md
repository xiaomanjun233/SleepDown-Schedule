# 2026-10-23 导入历史返回按钮改用真实按钮表面

用户反馈：返回“导入历史”时，动画落点的那颗红色按钮是空白，要求按右下角加号的方式处理。加号路线已经用独立录制层保存真实按钮表面，本次把同一个做法补到手动导入表单的“导入历史”按钮。

## 原因

- `ManualImportToHistory` 的锚点位图来自 `captureHistoryBackground()`：它读取首页内容录制层 `detailScreenGraphicsLayer`，再按按钮的窗口坐标裁剪。
- 手动导入表单挂在 `HomeAnchoredMorphOverlayHost` / `HomeMenuDestinationOverlay`，是首页内容层之后的同级节点，不在这张录制里。因此在按钮位置裁剪到的是表单背后的壁纸/衬底，不是红色按钮本身；开场与返回都拿到这张空表面，返回时最明显。
- 加号路线不受影响，因为它用 `dockImportReturnLayer` 单独录制真实按钮，而不是从整页裁切。

## 变化

- `AiManualImportUi` 为 `DialogLiquidButton("导入历史")` 增加独立 `GraphicsLayer`，在按钮空闲时按请求录制一次，缓存为 `cleanHistoryButtonBitmap`；不在每帧重复录制这颗液态按钮，避免额外的离屏开销。
- 打开历史时优先使用这张按钮表面作为 `openingAnchor.bitmap`，返回锚点继续按当前按钮位置重新解析 bounds，位图沿用同一张真实按钮；页面快照只保留给背景 underlay，裁剪结果降为最后兜底。
- 隐藏源按钮的 `alpha` 留在录制节点外侧，录制内容始终是不透明的按钮；不改按钮颜色、圆角（21dp）、路由与总时长。

## 验证

- `compileGithubReleaseKotlin` 通过（4m07s，仅 Kotlin 改动）。
- 完整 `assembleGithubRelease "-Psleepdown.sharedViewKitTest=true"` 6m06s 成功，159 项中 20 执行、139 复用，含 R8、资源压缩、lintVital 与正式签名；生成的两个开关仍为 true。
- 候选 `tmp/review-apks/SleepDown-import-history-return-gold-20261023.apk`（主目录），7,607,847 字节，SHA-256 `31fb5228b35d1bc74e2d34a3d7d96ef9ece5ed1e72124deda2ba768c326a26d8`，`1.2.7.1` / 35，v2 签名证书 `0f2b50df…`。

## 边界

- 没有设备，未实机确认返回落点；本轮只修正交给系统的位图内容，返回仍要求设备声明 `sharedView.bitmap`（与原逻辑一致）。
- 历史条目（`AiHistoryToDetail`）仍使用页面裁切，本轮未改；如也出现空白，应按同样方式为条目单独录制。
