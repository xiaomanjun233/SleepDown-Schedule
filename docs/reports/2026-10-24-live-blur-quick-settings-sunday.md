# 液态实时模糊、详细设置金标与周日切周

用户本轮要求：液态课程卡片用低成本实时模糊打开，继续调查 Android 新 API；快速设置进入详细设置接金标；合并快速设置“当前周/学期状态”，新增周日作为周首日。用户进一步明确该开关同时改变星期排列和教学周边界，并要求构建缓存不要继续堆在系统盘。

## 实现与边界

- 移除首页课程编辑的 PixelCopy 开场、请求中的源 Bitmap 与截图替代层。源文字实时布局，由同一个运动外壳持有染色和 Kyant 描边；外壳在源端使用半分辨率、无折射的实时背景模糊，按展开量 12%–52% 叠化到已有全屏模糊。大弹窗稳定后释放局部采样。该局部消费者显式不冻结坐标，避免首页冻结策略把模糊停在原站位；保留原曲线、打断和输入所有权。
- 快速设置的 `quick_sheet_to_settings_detail` 加入现有金标候选，通过真实、不透明的 `NativeQuickSheetSettingsDetailActivity` 复用设置 host 和保存退出流程。源仍取实际按钮，返回重新解析实时位置，截图裁切坐标与窗口锚点分开；没有能力时沿用透明 Legacy host。
- 快速设置将当前周与学期状态合为“当前周”一行，新增周日开关。设置按课表持久化，Room 47→48 只加默认关闭的列，原课程星期编号仍为 ISO 1–7。统一周起点与星期偏移用于教学周/学期末、首页、调休、编辑日期、课程复制、周课表小组件、AI 本周范围及 ICS 导出/导入；备份新字段默认 false，旧档保持原规则。周日起始不让开学日期之前的课程提前生效。
- 新 `scripts/Invoke-AndroidBuild.ps1` 将 Gradle 缓存放到 D 盘 `tmp/build-cache/gradle`，TEMP/TMP 放到短路径 `D:/sd-tmp`，主目录和工作树 AGENTS 的构建入口同步。约 6.6 GB 旧依赖已复制成功，用户级 `GRADLE_USER_HOME` 指向 D 盘。原 C 盘仍有其他 Gradle 进程持有旧缓存，未停止其他进程或删除其缓存。Codex 自身的会话存储不属于 Gradle 构建缓存，未改动。

## Android API 调查

已检查 [Android 17 功能和 API](https://developer.android.com/about/versions/17/features)、[RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect)、[Compose 模糊](https://developer.android.com/develop/ui/compose/graphics/blur/overview)和 [AOSP 窗口模糊](https://source.android.com/docs/core/display/window-blurs)。`RenderEffect` 从 API 31 提供图层内容模糊；窗口背景模糊面向独立窗口，受设备支持和成本限制，不直接提供 Compose 卡片背景。新 Compose 渐变模糊 API（文档标明 1.13.0-beta01 起）仍是内容模糊，不替代此处的背景 producer/consumer。复用现有 RenderEffect/Kyant 半分辨率链路，未为本次引入 Compose 大版本升级或私有 API。

## 验证与交付

本功能独立候选最终 14 类 133 项全部通过，完整金标签名 Release 8m21s 成功；89 个生产文件哈希、实际 APK 金标门控、原曲线重播与详细设置 Native 路线、正式 v2 签名核验通过。候选 `SleepDown-live-blur-sunday-gold-20261024.apk`，SHA-256 `f3f7b681ac60fd276c3e3e71cda17cfa04431fcde7d299711557462118c5696f`，未上传。用户随后追加特殊课程 UI 与错误基线问题，现由[特殊课程集成](2026-10-24-special-course-placeholder-integration.md)统一交付；为兼容两种旧 48 结构，最终数据库版本改为 49。下段记录前期验证过程。

使用 D 盘缓存与短 TEMP 后，14 类 133 项定向回归全部通过，0 失败/错误/跳过，包含新 47→48 及旧 44/45/46 的真实 SQLite/Room 升级、周日边界、备份、ICS、快速设置保存、金标握手与原曲线/多指所有权。首轮另有横屏材质参数遗漏的编译错误，已补齐；长 TEMP 路径下两项原生 SQLite 打不开文件，改短 TEMP 后迁移测试通过，未改生产数据库 journal 策略；ICS 日期断言已正确处理导出的 TZID。完整金标签名 Release 及产物核验进行中。

手机材质、帧率及 ROM 金标接受情况没有设备证据，构建不等于视觉验收。沿用只传包到私有“SleepDown 测试包”的授权，不安装、不提交或推送。
