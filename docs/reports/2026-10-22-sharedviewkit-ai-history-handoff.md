# AI 历史共享转场收尾

日期：2026-10-22，Asia/Shanghai。用户在另一助手额度耗尽后要求继续其工作，并提供“第三条路线（AI 进度页顶栏 → 导入历史）代码已接上，先编译检查”的截图。本次在原工作树 `tmp/sharedviewkit-ai-history`、分支 `codex/sharedviewkit-ai-history` 接手，基线 `5b62be48`，没有把主目录后端升级改动混入。

## 最终变化

- 保留并完成原助手的三条 SharedViewKit 候选：课程管理 → 课程详情、AI 历史 → 详情、AI 进度顶栏 → 历史。新增两个独立 opaque Activity，保留既有 Legacy 路线和不支持设备的回退。
- SDK 接收独立的软件 Bitmap 副本，避免硬件快照拒绝或 SDK 回收影响 Legacy；补齐状态拒绝时的释放，取消 SDK 后再释放副本。
- 共享转场传递真实页面底色；历史列表按记录 ID 解析实时返回锚点，纠正窗口坐标与根布局尺寸混用，离屏或删除项不作为返回目标。
- 原生动画运动中拒绝重复导航，避免再启动一页 Legacy。返回解析异常采用既有安全返回路径。

## 实际验证

完整 `assembleGithubRelease` 和四类 `testGithubDebugUnitTest` 通过，9m25s，192 个任务中 51 个执行、141 个复用。使用单 worker、仅本次 `-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`；之前 2GB / 双 worker 编译因 OOM 失败，没有修改长期内存设置。

| 测试类 | 数量 | 失败 / 错误 / 跳过 |
| --- | ---: | --- |
| SharedViewKitBackendTest | 14 | 0 / 0 / 0 |
| SharedViewKitCapabilitiesTest | 3 | 0 / 0 / 0 |
| TransitionFeaturePolicyTest | 4 | 0 / 0 / 0 |
| TransitionRouteCatalogTest | 5 | 0 / 0 / 0 |

本次共 26 项，包含拒绝输入被 SDK 回收、运动期重复打开与无效会话的新增回归。`git diff --check` 通过。没有运行无关套件。

本地候选启用 `-Psleepdown.sharedViewKitTest=true`，供后续设备验证；普通构建仍默认关闭。版本 `1.2.7.1` / 35，R8、资源压缩、lintVital、v2 正式签名均通过，证书与既有正式版一致。

交付副本：主目录 `tmp/review-apks/SleepDown-sharedviewkit-ai-history-20261022.apk`，7,591,419 字节，SHA-256 `9cdbe56bcd9fc92eeb887fa572da466627065472710b0763e9f47e75d4f34690`。该副本是本轮源码产物，没有安装、发布或与主目录合并；ROM 是否接管及开合视觉仍需真实设备验收。
