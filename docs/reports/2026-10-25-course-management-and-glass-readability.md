# 课程管理与玻璃可读性

## 目标与基线

用户要求优化特殊课程二级编辑页的保存/删除，参考最新版 Nexio 改善课程管理卡片：省略教师、给地点和安排增加图标、安排用小胶囊；提高首页特殊课程占位模糊，并稍压液态课表卡片的默认亮度。

继续 `tmp/sharedviewkit-ai-history` / `codex/sharedviewkit-ai-history`，在上一轮 `af04869b…` 候选上修改。开始时其 110 个生产文件哈希全部匹配，未采用主目录旧特殊课程代码。沿用用户已授权的私有 Drive 传包方式，不安装、不提交、推送或发布。

## 参考与实现

通过 Codex GitHub plugin 核对 Nexio 默认分支最新提交仍为 [`21919ead` / 1.6.7-1025](https://github.com/HaoZai000/NexioSchedule/commit/21919eade54a1fbd8b4353017795a91d59b35ebb)，读取其 [CourseManageScreen](https://github.com/HaoZai000/NexioSchedule/blob/21919eade54a1fbd8b4353017795a91d59b35ebb/ui-shared/src/commonMain/kotlin/com/haooz/chedule/ui/activities/CourseManageScreen.kt)。参考紧凑标题、元信息图标和安排胶囊的层级，在 SleepDown 现有组件上独立实现；按用户要求省略列表教师，不改变详情字段或数据。

- 特殊课程编辑页改为“基本信息”和“显示方式”两组，移除重复的编辑页标题与底部取消/保存并排区。保存使用公共 `DialogLiquidButton` 主操作，全宽 48dp，带确认图标和保存中/禁用态；返回保留原脏稿确认。删除成为独立中性设置组内的红色图标/文字操作，去掉导航箭头和重复说明，仍先确认再落库。
- 课程管理手机/横屏共用 `ManagedCourseListCardContent`：标题最多两行；地点汇总全部安排的去重非空地点并带位置图标；安排使用带时钟图标的轻染色胶囊，最多显示四种不同安排，多出的数量单独提示。保留课程色竖条、原卡片材质和全部编辑入口。没有教师或地点时不占空行。
- 周视图特殊课程占位仍每个连续星期段共用一个材质，模糊从 2.25dp 增为 10dp（仍乘既有质量系数），关闭此占位的额外 vibrancy；凝光占位复用现有原生材质，模糊半径比例为 1.4。既有分段、行高、甩尾和数据不变。
- 液态课程卡片在已有材质绘制回调中、文字和描边之前增加 8% 黑色薄染色，稍压背景和课程染色的亮度；保留透明度、颜色和玻璃高光。文字对比度的缓存壁纸估算同步该染色。凝光、简单高斯和纯色卡片不应用此值。没有新增 sampler、RenderEffect、GPU 回读、持续图层或帧循环。

## 验证与交付

本轮是局部布局和材质参数修改，未修改时间规则、数据、状态机或备份，未重跑旧测试。完整 `assembleGithubRelease -Psleepdown.sharedViewKitTest=true` 成功（5m21s），包含 Kotlin 编译、R8、资源压缩、lintVital 和正式签名。112 个生产文件哈希未变；本轮仅上述 5 个生产文件相对上包变化，金标和既有首页动画基线保留。实际 APK 的正式 v2 签名、包身份、两个金标开关、SDK 门控、原退出曲线调用、特殊课程原生宿主与普通滑入资源均核验通过，Room 仍为 49。

成品主目录 `tmp/review-apks/SleepDown-course-management-readability-20261025.apk`，7,675,359 字节，版本 `1.2.7.1` / 35，SHA-256 `b42cae6e7dcb65e38b2642adc9a57fd4dc5ae1c093d035424920cceb2ed19d76`。已作为新文件上传原本人私有“SleepDown 测试包”，metadata 与目录回读确认文件大小、归属、未分享及可下载。准确链接和历次候选保存在主目录 `tmp/review-apks/remote-delivery-20261023.json`；校验记录为 `tmp/course-management-readability-package-verification-20261025.json` 和 `tmp/course-management-readability-drive-readback-20261025.json`。

缓存继续位于主目录 `tmp/build-cache/gradle`，TEMP/TMP 为 `D:/sd-tmp`。未安装、提交、推送或发布；实机两种视效、特殊课程模糊观感、字体比例、触摸和 FPS 待验收。
