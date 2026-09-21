# ColorOS 课程 Provider 前后台读取观察（2026-09-21）

设备：PLJ110，Android 17 / ColorOS V17.0.0。已安装 SleepDown 1.2.6-exp2、课程组件 6.0.16（256）。本次工作树中的缓存与更新器修改尚未安装，因此以下记录只代表已发布旧组件。

## 已观察到的事实

测试课开始时间为 20:05，提前 20 分钟节点是 19:45。

| 时间 | 观察 |
| --- | --- |
| 19:43:29 | 手动测试刷新后，Metis 成功从代理读到包含测试课的课程列表。 |
| 19:43:37 | 代理无法取得 SleepDown 源 Provider，但成功回退内存快照。 |
| 19:45–19:47 | Metis 每分钟报找不到代理 Provider。用户报告未弹出。 |
| 约 19:56–19:57 | 用户插线并打开 SleepDown 后报告弹出；记录中的课程卡对应原来的 20:05 测试课，策略是 `20_MINUTES_BEFORE_CLASS`。 |
| 19:57:36–19:57:39 | 多次成功读取课程，系统记录课程卡 `onShow`。 |
| 19:58:31 | Hans 明确冻结 SleepDown，场景 `StrictMode-3 / LcdOff`。 |
| 20:00:43 | Hans 因 Activity 解冻代理，随后 Metis 成功读取初始化、今天及明天课程。 |
| 20:00:47、20:00:50 | Hans 分别冻结代理和 SleepDown，场景 `StrictMode-3 / LcdOn`。 |
| 20:01:00 | Metis 再次报找不到代理 Provider，但现有课程卡处理日志仍为 `shouldShow=true`。 |
| 20:01:36 | ADB 查询代理 `/has_init` 触发 Hans `reason: Provider` 解冻代理；代理仍无法取得 SleepDown 源，回退快照并返回成功。两秒后代理再次被冻结。 |

## 判断与边界

- 前后台变化期间，SleepDown、代理、Metis 的 PID 均未变化；没有发生组件卸载、升级或 Metis 重启。
- PackageManager 与 ActivityManager 中两个 Provider 的注册均存在。报“找不到 Provider”不能直接等同于组件未安装或 Manifest 错误。
- 两个应用的 cgroup `cgroup.freeze` 均实际读到 `1`。普通 ActivityManager 的 `isFrozen=false` 不足以排除这台设备上的 OEM 冻结。
- 取样时系统省电开关为 0，插线供电为 true；冻结仍发生。不能将问题简单归因于未充电或普通省电模式。
- 当前有明确的“Activity 解冻 → 读取成功 → 后台冻结 → Metis 读取失败”时间链。尚未通过单变量对照证明 Hans 是唯一原因；尤其需要继续解释为什么 ADB 的 Provider 查询能够唤醒代理，而 Metis 的定时读取未能成功。
- 已生成的系统课程卡可以与 Provider 读取失败同时存在。不能仅凭 Provider 错误或某一条中间态 `shouldShow=false` 日志判定卡已消失，应结合最终处理日志和实际显示。
- 当前证据不足以把本轮问题定性为此前换包后的旧包缓存。不能以重启 Metis 作为已经验证的本轮修复。

## 对修复的约束

持久化完整、按日期索引且有过期时间的代理快照，可以解决源应用不可用及代理进程被回收后的数据丢失；真实空课表必须覆盖旧快照，读取失败不得伪装成成功的空课表。该修复不能保证系统一定能访问被冻结的代理组件。

下一步应对照验证课程组件的系统后台管理设置与 Metis 的唤醒行为；在证据不足前，不引入永久前台服务、循环唤醒或把清系统数据作为修复方式。

日志仅保留读取状态、时间、路径和计数等诊断摘要，不纳入真实课程内容、设备截图或原始系统数据。

## 自启动设置后的补充观察

用户约 20:05 表示已允许代理自启动。20:06–20:09 的 Metis 分钟读取仍出现找不到代理 Provider；代理的 cgroup 冻结值仍为 1。20:08 读取到的系统电池优化白名单包含主应用和代理，代理的 `RUN_ANY_IN_BACKGROUND` 为 allow。本次没有通过自动化替用户切换自启动开关，也未能独立回读 OEM 自启动开关值；上述设置时点采用用户反馈。

代码补充了 `RECEIVE_BOOT_COMPLETED`、仅接受 `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` 的私有 Receiver，以及实验设置页的组件自启动、后台管理入口。Receiver 只通知一次课程刷新，复用 Provider 的持久快照，不启动 Activity 或常驻服务。快照在凭据加密存储中，故不注册解锁前的 `LOCKED_BOOT_COMPLETED`。

开机广播属于 Android 允许的静态广播例外，参考 [Android 官方广播例外说明](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)。这一启动入口不等于 OEM 会放行所有后台跨应用调用；手机重启后的实际验收尚未执行。
