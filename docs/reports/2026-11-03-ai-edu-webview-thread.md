# AI 教务线程修复与轻量网页 DevTools

用户要求查看最新报错截图，随后追加可拖动／收起的网页小窗、胶囊退出占位平移、已适配学校直接导入，以及删除网页预抓长图和截图／文本材料选择。最终采用现有 Agent 循环、WebView 与拾光 Bridge，未引入新框架、CLI 或依赖。

## 页面线程与调查工具

截图显示“教务页面读取遇到 WebView 线程错误”。READ_PAGE 在后台重新创建 eduPageMaterialReader，构造时就读取 WebView.url，发生在其内部切回 Main 之前。生产修改前，严格 WebView 替身从真实后台工作区入口复现了 `WebView.getUrl called off main thread`。现在工作区在 UI 线程绑定读取器，后台调用已绑定能力，所有 WebView URL／DOM／JS 操作回到 Main；保留任务、同源、文档版本及绑定 URL 检查。普通工具超时返回明确错误，父任务取消继续传播。

网页点击导入直接启动受控调查，初次仅提供脱敏入口，不先发送页面全文或截图。旧材料预发送／识屏选择、自动滚动批量截图实现和导入模式 Popup 已删除；文件与图片入口、ImportDraft → 本地校验 → 预览 → 用户确认写库保留。

EDU_WORKSPACE 增加 PROBE_SCRIPT、SCROLL 和仅视觉模型可用的 VIEW_PAGE。探测执行任务文件中的异步函数体，核对最新哈希，可返回 JSON 和受限 console 日志；提供的 fetch 限于当前教务同源 GET／POST，沿用登录态，结果 120000 字符、日志 20 条、20 秒后取消。探测不直接导入，最终仍运行标准拾光脚本并校验草稿。SCROLL 支持可选容器选择器和六个方向；VIEW_PAGE 只查看单个当前视口／裁切，不自动生成长图。视口像素使用实际挂载窗口，避免借出后错误采集旧 Activity 的窗口。

这不是被动保存全部请求正文的完整 DevTools，也不保证所有跨域页面、验证码或未暴露作息都可自动取得。工具与提示要求读取实际来源，不猜数据、不输出凭据、不进行选课／退课等教务写入。PROBE_SCRIPT 只有受控 fetch 实际发起请求才记录接口尝试；DOM／视口和接口调查仍受提交证据门控。

## 小窗与胶囊

AiEduBrowserSession 让原浏览器的 FrameLayout 临时借出同一 WebView，AiEduBrowserWindow 在当前任务会话中显示真实网页和操作状态，可拖动、收起。收起只折叠可见槽，保持非零网页视口供工具继续调用。窗口受安全区、输入区和键盘可用范围约束，拖动限于标题栏。离开会话／切历史／Activity 停止时归还原容器；源页面释放或渲染器失效时撤销能力。弱引用注册保留各源的独立撤销，旧宿主延迟释放不能移除新宿主，旧源释放不能清掉新任务。

统计、附件和候选胶囊退出时保留内容至高度收缩结束，对话避让和回到最新位置随实际高度移动；候选 LazyRow 按稳定 id 做删除和位置动画，退出项不接受操作。已适配学校底部按钮直接运行原适配；未知地址直接进入模型调查；已适配页的 AI 入口保留在更多操作中，并继续按既有策略优先使用已存在的本机适配。

主要文件：AiEduWorkspace.kt、AiEduBrowserSession.kt、progress/AiEduBrowserWindow.kt、EduDevToolsScripts.kt、EduPageCapture.kt、EduImportBrowserUi.kt、EduBrowserDock.kt、AiTaskWorkspace.kt 与 AgentInputCapsules.kt。

## 验证

最终 6 类 26 项 Android 检查，0 失败／错误／跳过：线程和工具能力 6、浏览器租借生命周期 5、会话／小窗／直接导入 UI 4、双协议调查门控 1、DOM 合并脱敏 2、Bridge 生命周期 8。完整签名 assembleGithubRelease 同次 7m05s 通过（192 个任务），保留 R8、资源压缩、lintVital 和默认金标。日志 `tmp/ai-edu-devtools-final-20261103.log`。

UI 测试发现 Robolectric WebViewProvider 的深代理没有实际 Chromium 的测量／setFrame 委托：宿主和测量是 333×280，WebView 布局仍为 0。测试替身补齐测量，自定义 shadow 转发隐藏的 setFrame 到 View，最终继续断言展开／收起均有非零原生视口、拖动位置改变、退出归还和释放撤销；未改生产渲染参数来绕过测试。更早的编码断言和测试 API 编译失败均不计为通过，历史失败日志保留在 tmp。

生产探测 runner／滚动 JS 的 5 项 Node 检查通过；既有 shiguang-execution.cjs 与 ai-edu-smoke.cjs 也通过，后者真实 loopback HTTP → 生产隔离包装／Bridge JS → 2 门课、4 节作息、18 周。架构 628 生产文件、0 违规依赖、41 条既有环边，diff 检查通过。未调用真实模型或真实学校，不能把替身检查与手机 HTTP 可达写为真实模型／Chromium 端到端验收。

## 安装与交付

包名 com.xiaomanjun.sleepdownschedule，1.2.8_beta2 / 36，7,844,089 字节，SHA-256 `870dd666bd87bb1cfb0f498c5caedcccf609178b13114b4171613a560f82bd5f`，V2 签名证书与前批一致。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-edu-devtools-20261103-870dd666.apk` 及同名 JSON，汇总 `tmp/ai-edu-devtools-delivery.json`。

2026-11-03 13:00:12（Asia/Shanghai）已覆盖安装原 OPPO PLJ110／Android 17／SDK 37／3B15AE023YL00000，Success，手机 base.apk 哈希与本包一致。实际 SDK 37.0.1 客户端和服务端，5038 USB；5037 与两条 mDNS 同时检查无其他目标。未清数据或主动启动应用。

[测试 APK](https://drive.google.com/file/d/1B3vtldC8n-x_jQ2NTrlM1Hm3fPEXpDZR/view?usp=drivesdk) 已上传原“SleepDown 测试包”目录并回读名称、大小与父目录；未改共享权限，无远端哈希字段可供比较。未提交、推送、公开发布或合并。

测试服务 PID 76024 与命令行已核实，仍监听本机 127.0.0.1:18765，运行码 BFCCE0。已恢复原手机的 USB reverse，并从手机实际回读 HTTP 200、2 门课／4 节作息／18 周。App 地址栏使用完整 `http://127.0.0.1:18765/`，点击 AI 导入即开始调查；使用[验证说明](../ai/AI_SCRIPT_SMOKE_TEST.md)中的要求核对文件编辑、最新哈希、真实运行码和待确认预览。服务特意留给用户复测，结束时按说明核对精确进程并清理该转发。
