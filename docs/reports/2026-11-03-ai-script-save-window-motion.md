# 当前脚本保存、小窗惯性与摘要展示

日期：2026-11-03。分支 `codex/rescue-import-materials`，沿用已有未提交救援工作树，未提交、推送或公开发布。

## 需求与最终行为

用户要求导入预览下增加“保存当前脚本”，按拾光仓库规范保存；网页小窗拖动略有延迟、松手惯性滑行、碰边回弹，并限制在顶栏与输入区之间。推理展示最终澄清为国内模型对齐当前 GPT 显示，保留思考摘要，取消旧独立走字框。

当前预览仅在本任务实际 `RUN_SCRIPT` 成功、有已验证源码时显示保存入口。确认框支持展开“查看脚本”、修改名称和登录入口；保存适配不写课程库，成功后显示“已保存到我的适配”并禁用重复操作。模型 `OFFER_SAVE` 与用户入口复用同一确认框。

`EduVerifiedScripts` 绑定执行任务、规范化文件路径和源码哈希。编辑或覆盖文件立即撤销已验证候选，重新执行成功后恢复；旧任务结果不能发布为新任务候选。当前进程中保留实际执行的源码，任务临时文件清理后仍能保存；未保存源码的旧导入历史不能反向恢复，需重新执行。登录入口保留正常 CAS／跳转参数，移除凭据参数、会话路径标识与 fragment。

`LocalEduAdapterPackage` 生成单校仓库 ZIP，只含以下四项，JavaScript 原样保留注释与来源信息：

- `index/root_index.yaml`：官方四字段 root，local initial 为 `L`。
- `resources/LOCAL_…/adapters.yaml`：官方七字段学校／适配元数据。
- `resources/LOCAL_…/adapter.js`：实际执行源码。
- `school_index.pb`：官方 v2 proto 字段／枚举，版本 `TIME_yyyyMMddHHmmss_SSS`，复用现有仓库解析器。

存储使用 AtomicFile。旧 `.json`／备份继续读取，显式更新写新 ZIP，不做自动清理或数据库迁移。删除只处理被选择的本地适配及其 AtomicFile 文件；不影响仓库更新或其他适配。

`EduBrowserWindowMotion` 使用有界弹簧跟随与实际手势速度，释放后摩擦衰减，碰边反射并衰减。拖动只作用在标题栏，WebView 页面操作保持原路线。输入区、键盘与窗口尺寸变化会停止旧运动并限制合法位置；收起／离开停止逐帧更新。继续挂载同一个已登录 WebView，没有新运行框架或手机命令行依赖。

导入实时片段和历史摘要已共用 `AiImportTranscriptEntry`，不按国内／GPT 服务商设置独立框。公开摘要保留 Markdown、13sp 状态字体及对应历史；正文与工具文字继续各自层级。不删除 reasoning transport／状态／历史，也不新增全量隐藏摘要开关。

## 截图证据的范围

仅拉取用户本机已保存的 13:04:19、13:04:26、13:05:06 三张 App 截图。存在真实“编辑本次任务脚本”工具行，预览为 2 门课／4 节作息／18 周，运行码 BFCCE0、课程时间与周次匹配本机合成样本。

截图没有完整脚本源码和展开的最终运行工具结果，因此不声称逐行审查用户生成脚本，或仅凭截图证明全部原生执行步骤。检查时前台为 Launcher，未启动应用、未保存课程。截图只保存在忽略的 tmp，没有上传或暂存。

## 验证

最终 `testGithubDebugUnitTest` 定向 10 类、40 项全部通过，失败／错误／跳过均为 0：

- AiEduWorkspaceThreadTest、AiEduBrowserSessionTest、AiEduWorkspaceEntryTest。
- AiImportConversationPreviewUiTest、EduPageCompactionTest、EduVerifiedScriptsTest。
- LocalEduAdapterStoreTest、LocalEduAdaptersTest、EduBrowserWindowMotionTest。
- ShiguangBridgeLifecycleTest。

覆盖实际 worker／原生 Bridge 回调后的候选生成、编辑失效、重跑和临时文件清理；真实预览保存入口／查看源码／保存不导入课程／保存后禁用；旧 JSON 兼容、ZIP 原子恢复与单项删除；拖动滞后、惯性反射不越界、键盘变化停止旧飞行。

首轮 39/40 的失败暴露公共玻璃弹窗按钮点击虽被替换为空回调、却未向 LiquidButton 传 `enabled`，已在 SleepDownDialog 的 LiquidAlertActionButton 修复禁用语义，未修改断言绕过。

完整签名 `assembleGithubRelease` 与上述测试同轮 **7m32s** 成功，保留 R8、资源压缩、lintVital 及默认金标 SharedViewKit。日志 `tmp/ai-script-save-window-motion-final-20261103.log`。架构检查 631 个生产文件，0 违规依赖，41 条原有循环边跟踪；`git diff --check` 通过。没有为文档修改重复 Android 构建。

拾光规范通过内置 GitHub plugin 回读 `ShiGuangSchedule/shiguang_warehouse` 的 pending 分支。README、root／adapter schemas、proto、build_data.py 与生成的 Python proto 均以实际文件为准。真实 Kotlin 测试生成 ZIP 经官方 schemas 校验通过；官方生成 proto 解码成功，官方 build_data.py 用同一版本 id 重建的 `school_index.pb` **字节完全一致**。验证环境和上游文件仅在 D 盘忽略目录，不进入 App 依赖或仓库：`tmp/edu-schema-venv`、`tmp/edu-schema-upstream`、`tmp/validate-local-edu-package.py`。

未执行付费模型／真实学校端到端验证，也未实机评估新小窗手感。此前生产 DevTools／Bridge 的 Node 检查结果保留，见上一份报告。

## 交付

| 项目 | 实际结果 |
| --- | --- |
| APK | `SleepDown-1.2.8-beta2-script-save-motion-20261103-ad5da225.apk` |
| 版本 | `1.2.8_beta2 / 36`，包名 `com.xiaomanjun.sleepdownschedule` |
| 字节数 | 7,876,857 |
| SHA-256 | `ad5da225aaa33250d63e497c6b83b1703c10fa8ee5d3f445c7a11057ef99711f` |
| V2 证书 | `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a` |
| 本地 | `tmp/review-apks/` 下 APK 及同名 JSON；汇总 `tmp/ai-script-save-motion-delivery.json` |
| Drive | [本轮 APK](https://drive.google.com/file/d/1Z-RA0sKNarXoQqujQewrV-xpI0eLufMS/view?usp=drivesdk)，已回读名称、大小、原测试目录 |
| 安装 | 待手机重连，当前没有执行新包覆盖安装 |

第一次上传被自动审核要求核实目标账户／目录归属；随后以 `'me' in owners` 搜索确认同一“SleepDown 测试包”目录为当前用户私有且 `shared=false`，再用相同插件上传成功。没有更换渠道绕过审核，也没有修改共享权限。远端未提供 APK 哈希，不声称验证远端 SHA-256。

本轮交付时 SDK 5038（37.0.1，SDK 路径）与 OPPO Connect 5037（36.0.1）都没有设备，两个 mDNS 列表为空，其他 adb 监听及 USB 检查也无目标。已请用户接回原 PLJ110，后续重查服务端来源和设备后按既有授权覆盖安装、比对设备 APK 哈希；不卸载、清数据或主动启动。手机此前仍为 870dd666 包，不能混作本轮已装。

本机合成教务服务为用户保留，连接与运行码不能作为以后固定值；复测与关闭见 [验证说明](../ai/AI_SCRIPT_SMOKE_TEST.md)。
