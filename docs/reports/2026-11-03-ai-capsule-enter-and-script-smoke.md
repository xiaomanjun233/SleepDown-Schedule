# 胶囊入场与临时脚本验证

用户要求导入页底部胶囊和按钮出现时轻微放大、动态模糊；随后将平板统计位置也指定为输入框上方，手机和平板统一。另要求可实际测试临时写脚本的方法。

## 实现

`core/ui/interaction/SoftBlurEnter.kt` 提供一次入场，260ms、94%→100% 缩放、6dp→0 模糊与渐显。复用已有模糊 effect 表，只在运动期间存在；稳定后移除临时图层及 effect，Android 31 以下只缩放／淡入。接入统计、附件、带稳定 id 的候选消息、导入输入胶囊及“回到最新”按钮；控件内的数值和文字刷新不改变入场身份，原有柔性反馈保留。

统计胶囊统一在输入框上方，不再按宽窗口分支切换前后顺序。实际高度仍参与对话留白与键盘避让，更新已有平板断言以对应用户新要求。

## 临时脚本测试方法

新增 [验证说明](../ai/AI_SCRIPT_SMOKE_TEST.md)、`scripts/ai-edu-smoke-server.py`、只供开发检查的参考脚本与 `scripts/tests/ai-edu-smoke.cjs`。本机合成教务只监听 127.0.0.1，并且只提供页面／页面 JS／课程 API；不提供工作区目录。页面不显示课程或随机运行码，模型需实际查询接口，再使用任务文件 WRITE／READ／EDIT 与教务 RUN_SCRIPT，按标准拾光 Bridge 顺序生成预览。2 门课、4 节作息、18 周均为明确标注的虚构样本，不代表学校数据。

按本地适配 skill 读取了上游 pending README（blob `78bcc8b6691d23bc9eeb2a42a2c2e6b8a7b14f2c`），宿主实际 Bridge 和原有官方异步适配案例是当前联调事实来源；没有修改或提交学校索引／适配器。

自动检查已通过：真实随机端口 HTTP → 生产隔离包装／Bridge JS → 配置、作息、课程按顺序与 executionToken 提交；既有 `shiguang-execution.cjs` 也通过。它们不访问真实学校、模型或用户账号，也不能替代手机原生 Bridge 运行结论。

为用户准备的服务已通过原 PLJ110 USB 反向转发，`http://127.0.0.1:18765`；电脑后台 PID `76024`，运行码 `BFCCE0`，日志 `tmp/ai-edu-smoke-server.out.log`／`.err.log`，PID 文件 `tmp/ai-edu-smoke-server.pid`。只为用户后续验证保持运行，结束时核对精确进程及脚本后停止并移除此端口转发。重新接续时读取当前进程与 ADB 状态，不假定历史 PID／通道继续有效。SDK 客户端 → OPPO Connect 5037 USB 服务，目标 PLJ110／Android 17／SDK 37／`3B15AE023YL00000`；5038 与两条 mDNS 已查，无其他设备。已从手机以真实 HTTP 请求回读接口，确认 HTTP 200、运行码与 2／4／18 一致。

## 验证与交付状态

3 类 11 项 Android 定向检查与完整签名构建同次 8m50s 通过（192 个任务，0 失败／错误／跳过），日志 `tmp/ai-capsule-enter-and-script-smoke-20261103.log`。不新增机械样式测试，只运行更新后的既有对话预览／历史切换、任务文件哈希与 Bridge 生命周期检查。R8、资源压缩、lintVital 和默认金标均保留。架构当前 625 生产文件，0 违规依赖，41 条既有环边；diff 检查通过。

包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.8_beta2 / 36`，7,844,089 字节，SHA-256 `9b6e849b8d9aed551e5664eaff42e3941678d3bc623a864f74ff443b32dafdaa`，V2 证书与前批相同。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-ai-capsule-enter-20261103-9b6e849b.apk` 及同名 JSON。经已确认的原 PLJ110 5037 USB 覆盖安装 Success，手机 base.apk 哈希一致；没有清数据或自动启动应用。Drive 插件上传原测试目录，并回读名称、大小与父目录：[下载本轮 APK](https://drive.google.com/file/d/1NrZV9lxcQyNyygxTtiA3O1B5_O0ywPzR/view?usp=drivesdk)。未改共享权限，未提交、推送或公开发布。

用户随后反馈测试页在 App 教务地址栏无法打开。重新检查服务、USB reverse、手机 HTTP 请求均正常（页面和 API 均 200）；读取必要 UI 摘要发现实际地址为 `https://127.0.0.1:18765/`。此测试服务仅提供 HTTP，已明确告知将地址改为完整 `http://127.0.0.1:18765/`，文档补充保留协议；不为测试环境关闭 App 的正常默认 HTTPS。临时 UI 树仅在手机 `/data/local/tmp` 与进程内读取，读后已删除，没有保存／输出其他用户页面内容。未执行真实模型的临时脚本导入，不能把 Node 检查或手机 HTTP 可达写为模型／原生 Bridge 端到端通过。
