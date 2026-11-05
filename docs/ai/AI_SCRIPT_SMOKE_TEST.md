# 临时脚本的可重复验证

这里验证“模型现场写文件 → 读哈希 → 精确编辑 → 原生工具执行”，不以模型口头声称成功作为证据。页面和课程全部为合成数据，不需要真实学校账号。

## 教务网页脚本

电脑在仓库根目录运行，终端保持打开：

```powershell
py -3 scripts/ai-edu-smoke-server.py --port 18765
```

服务仅监听本机 `127.0.0.1`，只提供测试页面、页面 JS 和课程 JSON，不提供仓库文件。终端首行会显示本次随机 `runCode`，重启服务会改变运行码；不要把码提前告诉模型。页面故意不显示课程明细，课程名称只在实际接口中返回。

手机通过现有 ADB 通道转发该端口。先按项目 ADB 查找规则核对当前端口与目标，再执行（下面的端口和序列号是本轮已确认的 PLJ110 USB 示例，不代表未来固定连接）：

```powershell
& 'D:\Android studio\AndroidSDK\platform-tools\adb.exe' -P 5038 -s 3B15AE023YL00000 reverse tcp:18765 tcp:18765
```

1. 在 App 的教务浏览器地址栏完整输入 `http://127.0.0.1:18765/`，保留开头的 **`http://`**。地址栏在省略协议时默认补为 HTTPS，而此本地服务只提供 HTTP；2026-11-03 用户首次无法打开时，实际页面地址为 `https://127.0.0.1:18765/`，手机 shell 访问 HTTP 已返回 200。页面显示“同源课程接口可用”后，点击 AI 导入直接开始调查；没有截图／文本选择和第二次开始确认。
2. 将页面文本框内的“测试要求”复制到导入对话发送。要求模型读取当前页面与真实接口、用 `TASK_WORKSPACE.WRITE/READ/EDIT` 生成并编辑临时文件，再用 `EDU_WORKSPACE.RUN_SCRIPT` 执行标准拾光异步脚本。发送到正在运行的会话会先成为候选，点候选的“引导”即可交给当前任务。
3. 展开执行步骤，确认出现任务文件与教务脚本执行的真实工具结果。只有读取接口、模型回复“成功”都不足以证明注入链路通过。
4. 导入预览应有 **2 门课、4 节作息、18 周**；课程名带本次运行码。数学在周一第 1–2 节、第 1–4 周；程序设计在周三第 3–4 节、第 2、4、6、8 周。时段应为 `08:00–08:45`、`08:55–09:40`、`10:00–10:45`、`10:55–11:40`。
5. 验证到预览即可，无需保存。要保留试验结果时选新建课表。需要检查脚本保存时，点击预览下“保存当前脚本”，展开“查看脚本”核对实际源码；确认后应显示“已保存到我的适配”，并在“我的适配 · 本地”出现。保存脚本不应直接保存课程。测试后可删除该自用测试适配，避免下次命中本机适配绕过 AI；也可使用另一个端口重新验证。

脚本应按标准 Bridge 顺序等待保存配置、作息、课程全部成功后才通知完成；用的是当前 WebView 的 DOM／fetch，不能使用纯转换脚本的 `main(input)` 格式代替。生成结果仍在草稿预览内，未经用户确认不会写课程数据库。

测试结束后在电脑终端按 Ctrl+C 停止服务，并移除本次转发：

```powershell
& 'D:\Android studio\AndroidSDK\platform-tools\adb.exe' -P 5038 -s 3B15AE023YL00000 reverse --remove tcp:18765
```

如果使用本轮助手已启动的后台服务，可读取 PID 文件并核对进程命令行确为当前仓库测试脚本后再停止；PID 不匹配时不结束其他进程：

```powershell
$smokeProcessId = [int](Get-Content tmp/ai-edu-smoke-server.pid)
$smokeProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$smokeProcessId"
$smokeScript = (Resolve-Path scripts/ai-edu-smoke-server.py).Path
if ($smokeProcess.CommandLine -and $smokeProcess.CommandLine.Contains($smokeScript)) {
    Stop-Process -Id $smokeProcessId
}
```

## 纯 JavaScript 与文件编辑

今日助手可直接发送下面的要求，不需要教务页面：

> 只做本地工具自检，不修改课程和设置。用 TASK_WORKSPACE.WRITE 创建 self-test.js，内容为同步函数 main(input)，返回 {sum: input.values.reduce((a,b)=>a+b,0), marker:"stage1"}。READ 取得最新 sha256，EDIT 把唯一的 stage1 改成 stage2，再用最新 sha256 RUN，inputJson 为 {"values":[7,11,13]}。只根据实际工具结果报告结果；不能执行时说明错误。

预期工具返回 `sum=31`、`marker=stage2`。这只验证任务文件和 AndroidX 隔离 JS 引擎，不能替代上面的 WebView 注入与 Bridge 验证。若当前系统 WebView 不支持隔离引擎，会明确返回能力不足；教务脚本走另一条 WebView 路线。

## 自动检查的范围

```powershell
node scripts/tests/ai-edu-smoke.cjs
node scripts/tests/shiguang-execution.cjs
node scripts/tests/edu-devtools.cjs
```

第一项启动随机端口的真实本机 HTTP 服务，并通过生产隔离包装和 Bridge JS 执行参考脚本，核对配置／作息／课程的参数、调用顺序与 executionToken。参考脚本不向页面提供，模型仍需自己写。第二项覆盖既有标准异步脚本与旧回调隔离；第三项检查生产 DevTools 脚本的 DOM／资源线索、同源探测、日志与滚动行为。三者均不调用模型，也不等同于手机原生 Bridge 或真实学校通过；最终用前述手机步骤确认模型与原生链路。
