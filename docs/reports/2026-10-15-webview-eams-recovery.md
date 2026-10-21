# 教务 WebView 资源与桌面缩放修复

## 接续范围

接续聊天「合入凝光视效并整理项目架构」，保留 `codex/vendor-liquid-runtime` 上已有修复。
本轮只提交 WebView 修复；工作树已有的 `PersonalizePanel.kt` 分隔线修改不纳入这次提交。
课表创建后的动画交接、AI 对话 IME 与导入页输入区域避让仍是后续工作。

## 真实站点证据

用户提供的 `http://111.117.146.166/eams/loginExt.action` 是沈阳建筑大学教学管理系统。
2026-10-15 对公开登录页执行 GET 对照：同一桌面 UA，只改变 `X-Requested-With` 请求头。

| 请求 | HTTP | 公开页面内容 |
| --- | --- | --- |
| 不带 `X-Requested-With` | 200 | 完整 HTML，包含 head、jQuery/Beangle、CSS 引用 |
| `X-Requested-With: com.xiaomanjun.sleepdownschedule` | 200 | 整个 head 与上述依赖被省略，仅留下页面主体 |

jQuery/Beangle 脚本和 Bootstrap CSS 本身在两种请求下均为 200。
因此该站点的问题是主文档被当作局部请求返回，不是单个静态文件失效；只修 CSS 请求无法恢复。
返回主体仍使用 `beangle` 和 `$`，可触发缺失依赖诊断。
验证没有输入账号密码、提交表单或读取用户课表；公开页面临时内容仅留在忽略的 `tmp/`，不入库。

## 上游参考

- Nexio [`552b9479`](https://github.com/HaoZai000/NexioSchedule/commit/552b94791273973dada8fff32dfe4cddb047faca)：按站点启用请求转发，剥离包名请求头，修正 gzip 与 UA。
- Nexio [`91f52aec`](https://github.com/HaoZai000/NexioSchedule/commit/91f52aec777e020759990c9a67d611cc923afc3e)：document-start 桌面视口、原生初始缩放、加载后的视觉缩放修正；兼容加载延迟 1200ms。
- 本次核对的公开 master 为 `9b5585bf67263f7897463f78dae217c9a213e924`（2026-10-15），不是上一轮核对的 2026-10-13 快照。

## 实现边界

- `WebCompatDelegate` 保留 document-start 布局，补充原生初始比例和 `visualViewport.scale` 检测。只在比例未落实时调用 `zoomBy`，400ms 后最多复检一次。
- 主窗口与弹出窗口共用入口。检测 jQuery/Beangle 缺失或同源脚本、样式 HTTP 错误后，按窗口内的精确 origin 启用兼容请求，延迟 1200ms 自动重载一次。不把猜测永久写入偏好。
- `EduWebResourceInterceptor` 只转发 GET/HEAD 主文档及受限的同源展示资源，保留实际 UA、Referer、Cookie 和服务器缓存语义。OkHttp 处理 gzip，不保留已失效的压缩长度头。
- POST 仍走原有拾光请求链路；先记录所有主文档请求的方法，再交给拾光拦截器，避免相同 URL 的登录 POST 被误认成原先 GET。未捕获的请求体、XHR/fetch、跨源资源、音视频、Range 和重定向交回 Chromium。
- 用户触摸页面、跳转、切换模式、销毁窗口或渲染器退出时，取消延迟重载/缩放；异步缩放回调也校验文档代次。自动恢复不重放登录提交，不覆盖用户手动缩放。
- TLS 校验仍由标准网络栈执行。诊断日志仅记级别、来源 origin 与行号，不输出页面脚本日志正文。

## 验证

- 首轮 `compileGithubReleaseKotlin` 成功。
- `scripts/tests/shiguang-post-intercept.cjs` 通过。
- `testGithubDebugUnitTest` 四套共 16 项通过，0 失败：`WebCompatDelegateTest`（7）、`EduWebResourcePolicyTest`（3）、`EduWebResourceInterceptorTest`（4）、原有 `ShiguangWebRequestInterceptorTest`（2）。
- 完整 `assembleGithubRelease` 成功（与定向测试串行，总计 11m 15s），包含 R8、资源压缩、lintVital 和签名。`apksigner verify --verbose` 确认 v2 签名有效。
- APK：`com.xiaomanjun.sleepdownschedule`，`1.2.7_beta5` / `34`，7,541,543 字节，SHA-256 `86B221246551C53BE0617CFE97E47038A21217E5CA37C69A60086590DBD53B41`。
- 通过 `scripts/Invoke-Adb.ps1` 固定的 5038 通道，向 OPPO PLJ110（Android 17 / API 37）执行 `install -r`，返回 `Success`。未启动应用、未改动课表数据；按用户上一轮要求，由用户自行复验界面。
- 真实站点 GET 对照和自动化回归不能替代手机上的页面验收；手机渲染、手动缩放与登录过程仍待用户复测。
