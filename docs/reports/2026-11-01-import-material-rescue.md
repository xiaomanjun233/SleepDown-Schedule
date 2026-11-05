# 2026-11-01 教务、凝光与自动刷新救援

分支：`codex/rescue-import-materials`。接手时工作树干净，HEAD 为 `3cb7285f`，在本机已发布 Beta2 交接提交 `3792fdba` 上多出两行 UI 修改。本轮保留该提交，不回滚其他任务、stash 或私有后端。用户授权为本地修复、检查自动刷新链路及检索 GitHub 案例，没有新增推送、PR、发布或安装授权。

## 修复内容

- 教务灵动岛：根浮层升至返回按钮之上；纯黑收拢态不挂载底层凝光材质，不遗留描边/跟手光；展开保留渐变玻璃。
- 教务浏览器底栏：通过公共材质 renderer 接入 HomeDock 凝光，保留地址输入、历史、键盘形变与导入操作；非凝光继续原液态玻璃。
- 首页：取消壁纸浅色场景的中性预设，使用系统默认白色色层；背景采样先乘 0.94，再执行原有模糊和两层系统混色，文字、描边及白色色层不被压灰。课程 shortcut、三点与加号菜单恢复默认菜单配方，不应用采样压暗。壁纸明暗仅限定首页。
- 课程拖动：浮起卡片通过外部手势坐标接收原生跟手光，松手立即结束；凝光不再叠加独立的移动 halo。源卡不可见期间关闭其原生材质，保留节点与最终落地交接；根节点接管松手后补回同款震动。
- 表头：异校区导入保留自定义真实时钟和源节次；新增跨导入预览到当天表头的回归。占据网格且指定逐节时间的特殊课程参与当天表头，按星期、有效周次筛选，正式课程占用优先；独立显示仍使用自己的时间标签。

## 四川机电登录态失败的根因

用户补充实际提示为“没有正在执行的拾光任务”。本机四川机电脚本在普通 IIFE 内调用异步 `runImportFlow()`，没有返回 Promise。原 `isolatedShiguangScript` 的无条件 `finally` 把 `undefined` 当作整个流程结束，第一条异步提示刚发出便调用 `notifyExecutionFinished`，清除了后续数据暂存需要的任务。

现在只有实际返回的 Promise 才由包装器跟踪结束；未返回 Promise 的官方脚本等待自己的 bridge 完成通知。显式完成时清理校验器，旧的 Promise 收尾不得清除新任务校验器。官方脚本的明确取消提示也会释放忙碌状态，不把可继续的作息警告误当完成。新增同形脚本回归：旧实现失败于 `notifyExecutionFinished` 提前出现，修复后直到保存和 `notifyTaskCompletion` 才完成。

浏览器和后台同时统一到等待 Cookie 回调完成的恢复入口；恢复旧快照只补缺失凭证，不覆盖浏览器更新的 Cookie 或跨 local/session storage 轮换的 token。Cookie 恢复被拒绝时明确失败，不能继续假装已恢复。

## 自动刷新优化及参考

通过 Codex GitHub plugin 核对了下列上游实现，没有使用 GitHub CLI：

- [Now in Android SyncWorker](https://github.com/android/nowinandroid/blob/2982e63cb691ad49cd009aab43dc206fee6699c2/sync/work/src/main/kotlin/com/google/samples/apps/nowinandroid/sync/workers/SyncWorker.kt)：参考按同步结果返回 success/retry。
- [拾光 WebView 请求拦截器](https://github.com/ShiGuangSchedule/shiguangschedule/blob/feabb68f75c4058319b449287980b5f645eb8d61/shared/src/androidMain/kotlin/com/xingheyuzhuan/shiguangschedule/ui/schoolselection/web/WebViewRequestInterceptor.kt)：继续复用官方 adapter/bridge 和 CookieManager 请求链，不另写学校协议。
- [Pledger Android 后台同步 ADR](https://github.com/pledger-io/android-app/blob/main/docs/adr/010-workmanager-background-sync.md)：参考唯一周期任务和会话失效边界，不照搬其业务和全量缓存模型。
- [Android CookieManager](https://developer.android.com/reference/android/webkit/CookieManager)及[唯一任务管理](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work)：核对异步 Cookie 回调、唯一工作与调度边界。

本轮落实：Worker 不再无论失败成功都返回 success。明确的主文档连接/超时、429/5xx 等临时故障及总任务超时可指数退避重试，30 分钟起，最多两次；认证失败、交互取消、脚本错误不会盲重试。依旧使用一个唯一周期任务，刷新只产生差异待确认结果。

后续可进一步处理结构化的阶段耗时与错误类型、页面准备完成信号，减少现有 2.5 秒等待。没有充分学校样本前不删除等待，不新增第二套学校接口或假保活。

## 差异提醒与逐项覆盖

首页和自动刷新设置均挂载核对 host，前台 RESUMED 且没有冲突弹窗时展示待处理差异。默认不选任何更新；可逐项或全部采用/保留，学期信息单独选择，只有“确认选择”才进入 Room 事务。核对期间的新编辑会使旧快照失效并重新生成差异。

新增模拟弹窗选择和 Room 混合新增/删除/修改验证。模拟只使用测试私有目录及内存数据库，不接触用户课表或凭证。

## 验证与边界

- Node 适配器执行及会话脚本回归通过；旧执行包装器对新增 detached-flow 用例明确失败。
- 架构扫描通过：603 个生产文件、0 违规文件依赖，既有 41 条声明循环未增加。
- 单 worker / 3GB 堆的 Release Kotlin 首轮通过；此前混合变体构建因 2GB 堆持续 GC 被取消，不能算通过。
- 最终同一轮 `compileGithubReleaseKotlin` + `testGithubDebugUnitTest` 通过（4m48s，单 worker / 3GB 堆）。15 类共 96 项测试，0 失败/错误/跳过，包含 2 项真实 Compose 弹窗操作、6 项内存 Room 核对落库、逐项差异及时间/拖动/会话回归。弹窗测试使用与产品一致的 Miuix Scaffold/host；所有课程和登录绑定均为模拟值。
- 本机保留的四川机电适配器通过生产包装器运行 3 组模拟接口响应（正常作息、旧作息、作息接口失败），数据保存和显式完成前任务保持有效。此结果验证离线协议与作息处理，不替代真实学校网络/账号验收。
- 已检查 5038、5037（OPPO Connect）和 5039（SDK）以及各通道 mDNS，没有可用设备；没有安装或实机视觉/账号验收。

本轮不改数据库、备份或公开课程交换协议，不清除用户数据。

## 用户追加打包与 Google Drive 交付

用户明确要求“打个包放谷歌网盘”。已从本轮工作树运行完整 `assembleGithubRelease`，3m02s 成功；保留 R8、资源压缩、lintVital 和签名，金标 true、本地教务测试资产 false。没有改版本号、安装、创建 PR 或公开 Release。

- APK：`com.xiaomanjun.sleepdownschedule` / `1.2.8_beta2` / `36`，7,758,147 字节。
- SHA-256：`a109dc95ad599c7e5390f39f4ecfef7063c11de131e1dfdd56c2498941275751`。
- V2 验签通过，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与既有 Release 一致。
- 本地保全：`tmp/review-apks/SleepDown-1.2.8-beta2-rescue-20261101-a109dc95.apk`。
- 通过 Google Drive 技能与连接器上传到既有“SleepDown 测试包”文件夹，没有改变共享权限；元数据回读确认名称、大小及父目录一致。[下载本轮 APK](https://drive.google.com/file/d/1QxIgbixBC1iSXJ3RD3j3UD3l41jLfjjt/view?usp=drivesdk)。连接器元数据未返回文件摘要，因此不声称完成了远端 SHA-256 回读。
