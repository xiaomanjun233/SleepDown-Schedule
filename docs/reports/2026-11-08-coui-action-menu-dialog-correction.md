# Kit 动作菜单、原生遮罩与弹窗进入开销

接续 `c9108a33`，分支 `codex/couikit-dialog`。用户纠正本轮目标：使用COUIKit菜单，只适配原版选中／遮罩行为，菜单没有分割线；追加课表设置顶栏过大、弹窗进入掉帧及删除多余mapping。保持版本 `1.2.8_beta2 / 36` 与minSdk26，只本地实现、提交及签名产物，不自动安装或发布。

## 改动

- 删除自行拼装的 `SystemPopupMenu` 和仅验证该旧实现的定位测试；课程shortcut和三点动作菜单直接调用Kit `Menu`，保留196dp宽度，使用原材质、排版、滚动和开合。动作项不设置分割或持久选中；危险项颜色和宿主图标保留。
- 原生 `COUITouchRecyclerView.dispatchDraw` 先绘制子项再叠共享遮罩；`calculateVerticalMaskBounds` 仅水平内缩8dp；普通单行项使用半行高半径与weight1，多行才走资源半径。接入Kit已有 `CouiCapsuleShape`、ARGB进度与共享遮罩运动，通过独立样式将遮罩移至前景。此前凭8dp圆角资源近似单行选中框不正确。
- 滑选沿Kit原手势处理，UP再次核对最终命中；移出、CANCEL和滚动接管不提交。Menu增加实际退场和定位回调，继续等待真实退场才交接业务，避免另建一套定时器／Popup。
- 课表设置顶栏按钮44→38dp，凝光顶部留白24→12dp，标题20→18sp，标题下间距12→8dp；左取消／右完成的位置与现有保存回调保持。普通Dialog底部按钮不改。
- 对照本机Kit `CouiDialogWindow` 与Demo调用：动画参数和Kawase后端本来一致。进入值在组合阶段读取会逐帧重组并执行两处Window策略SideEffect；将数值读取延后到图层／绘制阶段，保持采样逆变换同步，避免动画帧重复设置窗口背景、dim与softInput。材质effect包装按基础effect缓存，不删模糊或动效。

## 验证与清理

本地回退边界：`46e999aa` Kit菜单／前景遮罩，`404bb5c2` 课表设置紧凑顶栏，`e9c6410b` Dialog动画读取阶段。

- 19项功能回归通过：菜单5项、原迁移UI11项、课表保存／关闭生命周期3项（日志 `tmp/coui-menu-mask-dialog-verified-tests-20261108.log`）。该轮另外一项新增诊断把显示状态切换的窗口策略交接也误判为动画重复写入，因此单独校准其观察方式。
- 最终动画诊断1项通过（1m40s，`tmp/coui-dialog-motion-check-20261108.log`）：已布局窗口关闭及反向重开各观察6个运动帧，允许owner与Dialog子组合一次策略交接，确认不逐帧执行Window配置。此前尝试捕获屏幕外动画节点及零交接断言均不作为产品失败；没有据此删除模糊或动画。
- 已查看Kit动作菜单静止／按压图和课表设置图：无分割线、前景单行遮罩、紧凑顶栏、原详细设置按钮与正文均正常布局。Robolectric只确认这些布局／状态及CPU属性写入行为，不代替设备GPU、真实采样像素或帧耗时验收。
- 独立完整签名 `assembleGithubRelease --console=plain --no-parallel --max-workers=1 --no-daemon` 通过（5m55s，195任务，36执行／159复用），保留R8、资源压缩、lintVital与V2签名。日志 `tmp/coui-menu-mask-dialog-release-20261108.log`；未安装、推送或发布。

先核对并清理 `tmp/review-apks` 内10份旧mapping，释放1.47GiB；新包成功签名及哈希校验后再删除上一份 `cb6af347` 的mapping，累计删除11份，约释放1.62GiB。归档目录现只留本次APK对应的精确mapping；其他APK和外部桌面没有删除。

## 本次产物

同次构建文件保存在 `tmp/review-apks`，副本与原输出哈希一致：

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `SleepDown-1.2.8-beta2-coui-menu-mask-dialog-20261108-b0c89f55.apk` | 7,891,084 | `B0C89F55B79AAE499597C849F0D53603B35341146E0A926DBFB685FC0E32C9E1` |
| `SleepDown-1.2.8-beta2-coui-menu-mask-dialog-20261108-b0c89f55-mapping.txt` | 163,130,863 | `0CAC40D82680D308D26DCF391529134E4489B43121DD992615DD98F1A73F69CA` |

内嵌源码 `e5c969430a3300739f4da3481ec37c113df7398e`；V2证书SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。版本 `1.2.8_beta2 / 36`、包名 `com.xiaomanjun.sleepdownschedule`、minSdk26／targetSdk36不变。
