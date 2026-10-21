# 周视图模糊消失与采样偏移修复

用户反馈另一套周视图优化后没有模糊且采样错位。检查当前工作树发现共享背景被改为 0.5 比例双图层“烘焙”，本次只修正该链路，不回滚其他 AI 的导入、设置拆分等改动。

## 根因及修正

1. `baked.record { drawLayer(raw) }` 记录对 raw 的绘制引用，随后 `raw.renderEffect = null` 会移除最终显示依赖的模糊；它不是将效果冻结为像素。删除没有栅格化收益的 baked 中转层，把完成录制且始终带增艳/模糊效果的 raw 提供给消费者。仅半径/增艳参数变化或生命周期重置后重建效果，静止内容继续复用录制。[Android GraphicsLayer 文档](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer) 明确区分显示列表与绘制时效果属性。
2. 原变换 `S(1/sample) * I * T(-offset)` 将位移一同放大。改为与原 LayerBackdrop 对齐的 `I * T(-offset) * S(1/sample)`；纹理像素先回到全尺寸坐标，再减去全尺寸卡片位置，最后应用消费者逆变换。0.5 比例下，原先 (200,300) 的卡片偏移会被错误放大为 (400,600)。
3. 缓存按完整窗口尺寸失效，避免不同奇偶宽高向上取整后得到同一降采样尺寸而误复用。

保留当前 0.5 共享比例、等比例模糊半径以及卡片折射、高光、阴影；资源回到单共享层所有权。没有将“记录一次”误报为“GPU 只执行一次模糊”，也未声称画面已实机验收。

修改文件：本地 Kyant `backdrops/SharedBlurBackdrop.kt`、可追溯补丁与本记录。第三方基线保持 2.0.0。

## 洛阳理工不入包

本地 LYIT 适配资源从 Android assets 移至 `patches/shiguang-lyit-upstream/` 留存；YAML 索引及 protobuf 删除 LYIT。修改前后对比其余 150 个学校的序列化数据完全一致，协议仍为 2；远端上游未更改。最终 APK 的 zip 条目与两个索引均已检查，无 LYIT 资源和学校条目，包内其余 150 个学校数据与修改前一致。

## 验证

36 项应用定向测试及 3 项库内缓存测试通过（使用 `tmp/glass-motion-tests.init.gradle` 筛选，非全仓库测试）。`compileGithubReleaseKotlin` 与完整 `assembleGithubRelease` 通过，保留 R8、资源压缩和 lintVital；构建耗时 7m 40s。第三方补丁反向应用检查通过。

最终 APK：`app/build/outputs/apk/github/release/app-github-release.apk`，生成时间 2026-09-08 21:53:42，6,466,567 字节；SHA-256：`54fa3cd4bb0d25241f27e3c51b941c432dca292e41e4a4779f823f791c47797b`。

未安装、未执行实机截图和性能采集，沿用用户此前只查源码及已有记录的约束；现有逻辑测试不覆盖 Android GPU 的模糊像素输出，因此不宣称视觉与流畅度已经实机验收。
