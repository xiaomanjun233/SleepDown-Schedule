# 详细设置 Release 字节码闪退与通知开关行高

用户要求先读真实崩溃栈，再修复点击课表详细设置闪退，并参考原版调整通知设置里的单行开关。沿用现有未提交工作树，不修改课程数据、数据库或手机偏好。

## 真实设备与根因

本轮通过 SDK ADB 5038 的 USB 通道连接 PLJ110，Android 17 / SDK 37。已核对服务端为本机 Android SDK 程序及当前用户密钥。设备已安装 APK 的 SHA256 为 `c9705f182de18d878bcc911e24975b4e6ed442a2be2ad7d97a5a18eec52f7385`，并非上一轮较晚交付的 `d83a1b0c`。

11 月 5 日 21:31、22:06、22:09 的三次 Java 崩溃一致：`VerifyError`，`bg4.a(...)` 在 DEX 偏移 `0xC5F` 把 `Modifier` 引用寄存器当成 Float。旧包对应 mapping 已被后续构建覆盖，因此没有使用不匹配的 mapping 假装还原；通过精确 APK 的单类反编译、方法参数和 Compose 源位置标记，确认该方法就是 `PeriodSchemeEditorUiKt.PeriodSchemeEditor`。出错位置对应普通页面分支的 `topPadding + 12.dp`，不是 UI 操作后的保存或数据库异常。

当前 R8 9.2.14 的 `d83a1b0c` mapping 对应同一函数的 `jd4.a(...)`。使用隔离的 Java/DexClassLoader 验证器在同一手机上强制初始化该类，`c9705f18` 稳定复现 `0xC5F / v0`，`d83a1b0c` 稳定复现 `0xC5B / v2` 的对象/浮点类型错误。验证只使用 `/data/local/tmp` 的 APK 与小型 DEX，不安装、启动应用，也不读取课程数据。仅以不初始化的反射加载不能触发此校验，因此最终验证显式使用 `Class.forName(..., true, ...)`。

修复仅在 ProGuard 规则中禁止 `PeriodSchemeEditor-*` 方法优化，允许名称混淆和未使用项裁剪；其他方法继续原有 R8 优化。没有全局禁用 R8、取消资源压缩或改动时间线的业务规则。此前 Robolectric 的 Mesh 初始化异常与本次真实 VerifyError 不同，不能将两者混为同源。

## 通知开关的原版结构

E 盘实际 Demo 的 `DemoSwitchPreference` 在整行安装 toggleable 和反馈，内部 COUI Switch 的 `onCheckedChange=null`，只按 24dp 轨道测量。系统反编译 `coui_preference_widget_switch.xml` / `_compat.xml` 也将子开关设为不可单独点击。普通设置行最小 48dp，文字内容上下内距 10dp，多行说明按内容增长。

SleepDown 此前直接给内部 Switch 传点击回调，Switch 便增加最小 48dp 点击区，再叠加父行上下 20dp，导致无说明的行被撑为 68dp。现改为整行唯一点击/无障碍 owner、共享反馈源和原版触感，内部开关只绘制并保留拖动回调。单行回到最小 48dp，多行说明仍按内容增高；液态路径及原绿色不变。

## 验证与交付

固定源码后，4 项真实页面回归与完整签名 Release 顺序通过（7m40s，244 项中 22 执行），日志 `tmp/settings-art-crash-and-notification-package-20261105.log`。包括 SDK 26/35 详细设置滚动、作息菜单和日期提交；实际通知页面的“自动勿扰”“课程提醒”单行均为 48dp，说明行按文字增长；标题点击、右侧轨道点击各仅提交一次。已打开查看 `tmp/coui-visual-review-20261105/notification-settings-native.png`，属于本地 Compose Native Graphics 栅格，非手机 ROM 视觉验收。

最终包在同一手机的隔离 ART 强制初始化通过：`VERIFIED class=kd4 methods=43`。对应记录 `tmp/settings-art-active-verify-fixed-20261105.txt`；两个旧包的失败记录分别为 `tmp/settings-art-active-verify-old-20261105.txt` / `tmp/settings-art-active-verify-d83-20261105.txt`。确实保留完整 R8、资源压缩和 lintVital；没有以 Debug APK、全局关闭优化或未初始化的反射加载绕过验证。

新包 `tmp/review-apks/SleepDown-1.2.8-beta2-settings-crash-notification-20261105-beb9347f.apk`，8,180,451 字节，SHA256 `beb9347faff27011f5acf34ac796eabf6d86cba5d01ea5aab66c5a7315c35f43`；`com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / 36`、minSdk 26。复制后哈希一致，V2 签名通过，证书 SHA256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a` 不变。APK 同目录保留同名 `-mapping.txt`，R8 map id 为 `7f43fffc444945b8dc793614d2b4b7438e4047f2aa029fd5ae899767bf358391`；摘要 `tmp/settings-art-crash-delivery-20261105.json`，构建前后源码哈希一致。

已清理手机上本轮创建的五个精确 `/data/local/tmp` 文件。未覆盖安装或启动主应用，未读取课程数据、上传、暂存、提交或推送。设备字节码验证确认消除了本次 VerifyError；尚未覆盖安装后点击整条设置路线验收。旧包与 raw 诊断只留忽略的 tmp 目录，不进入提交。
