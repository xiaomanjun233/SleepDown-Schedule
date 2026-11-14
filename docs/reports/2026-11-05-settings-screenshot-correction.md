# 按用户截图纠正设置卡片与页面装配

用户否定 `51121cf6` 包并提供系统设置、SleepDown 首页、COUI Demo 三张截图，追加指出圆角不对，要求按不同页面分析，必要时核对 ColorOS 反编译实现。上一轮“已按 COUI 完成”的结论没有实际画面对照支撑，不能视为视觉验收通过。

## 实际差异与来源

- 系统设置与 Demo 截图静态卡片均为 RGB `(26,26,26)`；SleepDown 旧包为 `(51,51,51)`。错误来自把通用 `CouiPresets.Dark.container` 当作设置卡片色。E 盘实际 Demo 的 `CouiSettingsDefaults.colors` 和 Settings APK `values-night/colors.xml` 都使用 `#1AFFFFFF` 叠在黑底上。
- 首页目录不能直接沿用普通二级表单的最小高度。`DemoCatalog.kt` 明确首页 52dp、组内分隔线从 56dp 起；Settings APK 的 `homepage_preference_min_height=52dp`，普通 `support_preference_min_height=48dp`。旧首页缺少对应分隔线，并沿用了通用 48dp。
- 应用头部的 56dp Image 被通用 24dp 图标槽二次约束，加上末端 padding 后实际更小；版本被装到 trailing，挤压应用名。现将图标槽明确为 56dp，版本放回标题下方；业务功能图标仍保留原 Rounded 23dp。
- 原生与液态路径此前使用不同 Shape，液态卡片被换成 Kyant RoundedRectangle。现在设置卡片使用同一 COUI 平滑路径。系统设置 `COUICardListSelectedItemLayout` 的当前普通分支使用 24dp、权重 2.5；Demo 目录默认权重 3，因此没有把 Demo 默认轮廓无条件套到设置卡片。库 Group 仅增加可选 Shape 参数，默认行为不变，菜单/搜索/预览各自形状不全局替换。
- 实际二级页渲染发现：170dp 的固定短数字输入槽会把“当前周（自动）”挤成多行。数字字段改为 64dp；日期、时间、长文本保留各自入口。
- 实际液态首页渲染发现：自定义搜索没有承担上游 SearchBar 自己承担的 16dp 横向边距，导致贴到屏幕两侧。已补回同一栏位的边距，并避免旧系统重复缩进；首个首页分组按 Demo 使用 12dp 起始间距。

读取了 `E:/COUI/COUI-Kit` 实际 Demo/Settings 源码，以及 `E:/COUI/二级页面布局调查/04-完整反编译/Settings-apktool` 和 `Settings-jadx` 的原始资源、首页与 cardlist 消费者。E 盘内容均只读；没有把系统反编译源码或用户截图加入仓库。截图里的临时灰块也出现在 Demo 中，属于其按压反馈，本轮保留原反馈，不根据截图猜测删掉动效。

## 验证与证据边界

前两轮页面核对已通过，包含实际首页栅格输出，确认静态卡片 RGB、首页 52dp、56dp 图标槽、版本上下排版、菜单及日期提交。首轮渲染确实发现了搜索边距与二级数字槽问题，按实际结果继续修正，不能只据编译宣称完成。

最终固定源码后，5 项页面/栅格回归及完整 `assembleGithubRelease` 在同一顺序构建中通过（8m58s，244 个任务中 35 执行、209 复用，`tmp/coui-screenshot-final-package-20261105.log`）。5 项包括原生/液态首页真实栅格，以及 SDK 26/35 的详细设置滚动、作息菜单、日期提交；普通轮廓内外采样点、静态卡片 RGB、首页 52dp、图标槽/版本上下排版及数字标题单行检查均通过，0 失败/错误/跳过。中间一轮数字标题检查抓到了旧编译输入仍含 compact 条件；核查字节码后停止生产源码变更，重新编译和最终验收通过，没有调低断言掩盖换行。

渲染图位于忽略的 `tmp/coui-visual-review-20261105/`；是 Robolectric Native Graphics 的真实 Compose 栅格，不是修改用户截图或 HTML 示意。已实际打开首页两种模式和二级页渲染图检查。最终首页卡片角部在距顶部 1/5/10/20/40/60/80 像素的边缘偏移为 59/44/33/20/7/1/0，与用户系统设置截图同一组探针一致。对照图只拼接用户旧版应用截图和本轮渲染，并明确标注非实机；没有包含系统账户头像和用户名。ROM 字体、状态栏和硬件材质不等于实机，不能据此宣称手机整体视觉已经验收。

最终包 `tmp/review-apks/SleepDown-1.2.8-beta2-settings-visual-20261105-c9705f18.apk`，8,230,175 字节，SHA-256 `c9705f182de18d878bcc911e24975b4e6ed442a2be2ad7d97a5a18eec52f7385`，包名 `com.xiaomanjun.sleepdownschedule`、`1.2.8_beta2 / 36`、minSdk 26。保留 R8、资源压缩、lintVital 和既有 V2 签名，证书 SHA-256 为 `0f2b50df…`，复制后完整哈希一致。新旧包大小恰好相同，但 APK 与 classes.dex 哈希都不同，确认并非旧包改名。摘要 `tmp/coui-settings-visual-delivery-20261105.json`。

未安装、启动、上传、暂存、提交或推送；该包来自完整工作树，保留此前活动功能及其他未提交工作。此前手机详细设置闪退是否与 Mesh 阴影同源，仍无设备堆栈佐证，不把本轮布局修复写成已确认的实机闪退修复。
