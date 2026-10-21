# 对齐 Nexio 的库内采样结构

用户明确要求不再通过滑动剔除重建优化，直接参考 Nexio。沿用 `codex/glass-background-fingerprint`，保留工作树其他 AI 修改；无导入、数据和后台变更。

## 修改

- 周视图移除逐卡视口判定与状态写入。滑动不切换材质可见性，页面仍由既有 Pager 管理，预留相邻页数量不增加。遮挡弹窗的既有生命周期保持独立。
- 参考 Nexio master `260db78781c55f87968d091beea0c909ff827e2b` 的库内采样缓冲结构：卡片保持全尺寸布局，在 Kyant 内缩小纹理、同比例更新效果尺寸与 density，再将效果输出绘回原尺寸。采样坐标使用原 density，避免再次放大位移。
- 普通课程卡合并为一个全尺寸玻璃入口，移除缩小采样 Box、放大布局层和常规降采样的独立装饰 Box。完整高光、内外阴影、tint、底部发光、原采样比例继续保留；没有复制 Nexio 关闭高光/阴影的配置。
- 固定 Morph 和导出采样仍使用原分辨率。默认比例 1 保持原缓冲尺寸与 padding 取整行为；其他消费方不启用新的采样比例。
- 更新 Kyant 聚合补丁，保留 2.0.0 基线和许可证；反向应用检查通过。

源文件：`WeekScheduleUi.kt`、`GlassUi.kt`、`SleepDownGlassSurface.kt`，库内 `BackdropRenderOptions.kt`、`BackdropEffectScope.kt`、`DrawBackdropModifier.kt`；新增 `SampledEffectGeometryTest.kt`。

## 验证

新增 host 测试验证降采样的 dp 与几何同比例、重复输入不失效、恢复全尺寸；不把这些测试作为 GPU 输出验证。36 项应用定向测试、4 项库内 host 测试通过，`compileGithubReleaseKotlin` 和完整 `assembleGithubRelease` 通过（7m 10s），保留 R8、资源压缩和 lintVital。应用测试由既有 `tmp/glass-motion-tests.init.gradle` 筛选，不是全仓库测试。

APK 生成于 2026-09-08 22:28:06，路径 `app/build/outputs/apk/github/release/app-github-release.apk`，6,466,567 字节，SHA-256 `26e088a3c749f595f70859635c313c5b7d343166e373fb6a90b637a855704b3c`。检查包内资源、YAML 和 protobuf 均无 LYIT，学校索引为 150 项。

本轮未采集实机帧时间或截图，不能宣称已达到 Nexio 流畅度，也不能宣称像素等价已验收。重点验收双向滑动的模糊和采样位置、连续圆角、阴影叠加、编辑模式切换，以及同一课表的帧时间。未自动安装或启动。
