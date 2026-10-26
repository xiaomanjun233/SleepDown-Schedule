# 特殊课程编辑金标与占位行甩尾

## 目标与基线

用户要求点击特殊课程卡片后，先通过金标无缝动画进入独立编辑界面；非金标设备普通滑入，特殊课程行参与甩尾。继续 `tmp/sharedviewkit-ai-history` / `codex/sharedviewkit-ai-history`，在已交付 `194cd402…` 候选上修改；开始时核对其 104 个生产文件哈希均未发生其他变化。沿用原私有 Drive 传包授权，不安装、不提交、推送或发布。

## 实现

- 新路线 `special_course_to_editor` 使用真实 `SpecialCourseEditorActivity` 和不透明 `NativeSpecialCourseEditorActivity`；两者共用既有设置 Activity host、三种时间选择器、保存和退出确认。列表不再直接替换为编辑表单，新增卡片同样进入编辑页。
- 金标使用点击卡片的单次 PixelCopy 和真实窗口坐标、圆角，复用源交接遮罩及会话清理；不录制全屏背景，不创建持续录制图层。注册、取消、返回均释放源遮罩和位图。源卡片保持原样，启动锁不通过 disabled 状态改变其截图颜色。
- 返回重新解析列表中卡片的实时边界；删除后明确撤销返回目标，交由已有金标安全返回类型处理。进入首帧复用已加载课表，最多保留 6 个短期交接，Room 数据到达后接替，退出时释放。
- 普通路径为标准设置滑入／滑出，使用四个 translate 动画和 Activity 窗口样式，金标能力或锚点不满足时直接走该路线，不尝试卡片 Morph。
- `CrossActivityTransitionHost` 的普通页面分支支持原生返回，并让 SDK 回调独占 NativeRegistered / NativeRunning / Open 状态推进，保存和脏稿返回仍由编辑页先处理。
- 周视图特殊课程占位胶囊接入现有 `weekPageTail` 和 `homeSwitchGroup`。同一特殊课程的分段使用相同顺序，保持整行一致；排序采用共享行轴的实际位置，密集页面的性能策略计入真实占位材质数量。跟随仍使用既有有限轨道，静止时不保留甩尾移动图层。特殊课程设置入口也接通用首页切换甩尾入口。
- 无数据库、备份或三种时间规则变更；首页占位保持只显示，不新增业务弹窗。

## 验证与交付

9 类 67 项定向测试全部通过，覆盖路由/门控、原生开合及删除后无目标返回、交接清理、甩尾和特殊课程时间行轴。同次完整 `assembleGithubRelease -Psleepdown.sharedViewKitTest=true` 成功（7m31s），含 R8、资源压缩、lintVital 和正式签名。110 个生产文件哈希未变；实际 APK 的 v2 签名、两个金标开关、SDK 门控、原退出曲线调用、特殊课程真实原生宿主、普通滑入资源及甩尾标记均已核验，Room 仍为 49。缓存使用主目录 `tmp/build-cache/gradle`，TEMP/TMP 为 `D:/sd-tmp`。

新包 `tmp/review-apks/SleepDown-special-course-gold-tail-20261025.apk`（主目录，7,658,975 字节，`1.2.7.1` / 35），SHA-256 `af04869b4115a830700846031df577c6714428a4d43bf8e074fe64e6343fe1c5`，已作为新文件上传原本人私有“SleepDown 测试包”目录；metadata 和目录回读确认大小、归属、未分享及可下载。准确链接及历次包保存在主目录 `tmp/review-apks/remote-delivery-20261023.json`，本轮校验记录为 `tmp/special-course-gold-tail-package-verification-20261025.json` 和 `tmp/special-course-gold-tail-drive-readback-20261025.json`。

未安装、提交、推送或发布。实机金标观感、普通设备滑入、触摸和 FPS 尚未验收；代码和成品检查不代表手机视觉验收。
