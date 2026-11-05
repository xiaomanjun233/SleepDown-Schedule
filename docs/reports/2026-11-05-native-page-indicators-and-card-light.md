# 原生交互页点与卡片渐进照亮（2026-11-05）

## 用户目标与基线

承接 11 月 4 日的原厂投光接入。修复 HARDWARE Bitmap 与 Surface 时序后，`355e15d7…` 的 6 项定向测试及完整签名 Release 通过（8m21s），已在 PLJ110 / Android 17 / SDK 37 经 SDK 37.0.1 的 5038 USB 通道覆盖安装，设备 APK SHA-256 与本地一致：`355e15d7bfa2f0ac5c1d301d91b50355b0f24dba68f3907d4bea4ff104d9f969`。用户反馈光效可用，又要求：恢复分享/新建圆形按钮颜色；项目所有表示翻页进度的指示条改为可交互 ColorOS 组件；组件设置页加入同款投光并删除操作提示行；原版的徐徐照亮、速率、圆角覆盖与下方按钮受光都要正确。

当前分支 `codex/rescue-import-materials`，沿用并保留既有未提交工作树。用户此前“做完安装到adb”的授权覆盖本轮更新；未提交、推送或发布。

## 原版事实

- 实机 Wallpapers `17.04.0034 / 17040034` 的 `MyThemeShadowViewScrollListenerImpl#M()` 使用 `ValueAnimator.ofFloat(0f,1f)`，时长 `0x28a` = **650ms**，默认 AccelerateDecelerate 插值；`#N()` 逐帧调用 `WallpaperShadowView.setViewProcess`。不能把原版概括成整体淡入。
- `shadow_card.coz` 的 `uProcess` 在椭圆中心 -150→100、两个半轴 200→300 / 100→400、起止软遮罩之间插值，产生扩散和逐步照亮；原来的桥接固定为 1，丢失了这段效果。
- `BasePersonalFragment#y()` 另有 View alpha 的 1000ms 淡入。两条动画独立。
- `my_theme_header_item` 的原按钮距卡片 Recycler 底边 **28dp**，`all_theme_button_height` 为 **44dp**，受光中心位于 **50dp**。本项目另有页点和操作区，照搬投光位置会使按钮落在衰减尾部。

调用链为页面 `NativeCardLightRequest` → `ControlMaterialRenderers.cardLight` → `ColorOsCardLight` → 系统 `com.oplus.wallpapers.personal.views.WallpaperShadowView`。使用包可见性查询和 `createPackageContext(CONTEXT_INCLUDE_CODE | CONTEXT_IGNORE_SECURITY)` 配对其 classLoader/resources/assets；构造 View 自动读取系统 `shadow_card.coz`。Surface 首帧之后调用 `setBitmap(软件位图)`，逐帧调用 `setViewProcess(0..1)`，View alpha 独立控制透明度，`d()/e()` 对应原生启停。没有将厂商 APK 或 native 库打进安装包。

## 实现

- 公共 `NativeCardLightRequest` 增加展开进度、卡片圆角和接收按钮矩形。`rememberCardLightAnimation` 统一 650ms 原生展开与 1000ms 透明度；宿主逐帧更新 `setViewProcess`，稳定后不重复提交纹理或重启引擎。
- 光从卡片底部圆角区域开始，原生 TextureView 保留原始渲染尺寸及模糊。仅按实际按钮中心映射输出 Y 方向，匹配原版的受光平面；不在动画期重建 RenderTexture。
- 分享/新建圆形按钮回到原有绿色/主题色 COUI 材质，不再被中性受光材质清色；中央课表设置按钮继续使用原厂背景采样与白色透明着色。
- 新增公共 `SleepDownPagerIndicator` / `SleepDownPageIndicator` 和 `ColorOsPagerIndicator`。首版使用 `COUIPageIndicator`，用户随后指定桌面 APK 的完整材质分页条，现已替换为下节所述桌面方案；范围语义保留无障碍选页。ColorOS 可用时两个视觉模式均可使用，其他设备保留可点击 Compose 实现。
- 覆盖原公共 `ProjectPagerIndicator` 的课程编辑、组件预览、恢复说明，以及真实多课表浮层和遗留列表管理页。首页无限日期/周分页没有现存有限页点，不额外增加指示器；今日/课程表/设置导航不属于本次页点范围。
- 组件页只在当前预览、有效布局和 RemoteViews 更新时捕获实际软件位图，通过相同投光能力照亮下方操作；切页、进入编辑器或预览滚出视口时退出。删除“手动添加方法”操作提示行，平台要求手动添加时的原有说明弹窗保留。

## 验证与交付

首版 `a32975f3405df6fe615d123bea4ee5466527a4dd4aac3063ad16c37065f07aae`，7,909,657 字节，`1.2.8_beta2 / 36`，完整签名 Release 与 8 项定向回归通过。2026-11-05 00:57:57 经 SDK 37.0.1 / 15733141、同来源 5038 USB 向 PLJ110 / Android 17 / SDK 37 覆盖安装 Success，设备 APK 哈希一致。保全 `tmp/review-apks/SleepDown-1.2.8-beta2-native-light-pages-20261105-a32975f3.apk`。

此前全仓架构检查只有未修改的 `DayAgentProgressBar.kt:35 feature.agent → platform`。本次新增公共协议与实现均在对应 core/platform 边界内。本轮未重复运行全仓检查；相关 diff 空白检查通过。

## 用户追加与第二版

用户进一步要求原版按钮下投影、桌面 APK 的完整材质与交互、组件光更贴宽度且缩短下沿、更大投射角，随后报告久未打开的持久快照可能颠倒，并明确本轮做完放到之前云盘。构建、覆盖安装和云盘测试目录上传授权持续有效；无提交、推送或公开发布授权。

- Wallpapers 原生按钮投影读取 `all_theme_button_bottom_shadow`（`res/He.webp`）、208×44dp 按钮基准和距按钮顶 22dp 的位置，随原生按压及最多 −6dp 位移；分享/新建圆按钮保留本身颜色。
- 保留 COE 原素材及渲染尺寸，将 `uAngleX` 从 95.5 调至 99，并在展开时重设以免原布局动画覆盖；普通光区 X 方向 1.2×。组件按实际卡片宽度映射，左右渐隐，深度取 `min(88dp, 组件高度×0.45)`，不再按下方按钮距离拉长，覆盖组件圆角。
- 桌面样本 `E:/COUI/work/OplusLauncher.apk` 为 17.3.11 / 170030011；`OplusPageIndicator` 构造器强制要求 Launcher ActivityContext。移植其 28dp 高、8dp 圆点、14dp 内距、6dp 间距及连续拉伸指示的绘制边界，使用系统 COUI 模糊/描边/跟手光，模糊半径 120px、深色背景色层 `#3DFFFFFF`。通过 Launcher `TouchAnimatorHelper.createViewTouchAnimator$default(Context,View)` 和 `COUITouchMotionAnimator.m(MotionEvent)` 复用真实按压/橡皮筋。没有声称直接实例化整个 Launcher 指示器。PagerState 使用 UserInput 滚动连续选页，抬手吸附、取消恢复；禁用/释放时同步取消原生按压。
- 持久快照旧路径把 Compose HARDWARE Bitmap 直接交给后台 JPEG 编码。现捕获时先冻结为不可变软件 ARGB_8888，让显示与保存使用同一组像素，再通过 AtomicFile 串行写入。新缓存使用 v5；无方向标记的 v4 图片保留但不读取，不盲目翻转，不影响课程、Room 或备份。缺快照时用已有课程数据预览代替空壁纸。尚未在设备重现长期冷缓存颠倒，不能仅凭代码将根因断言为 COE 修改源图片：实机 native `convertJavaBitmapToNative` 已确认 malloc/memcpy。
- 定向回归 14 项通过：光效 7、分页点击/拖动/取消/RTL/禁用 4、快照硬件转软件→JPEG→冷读四角方向及旧缓存隔离 3；无失败、错误、跳过。完整签名 Release 8m10s 成功，日志 `tmp/native-light-launcher-snapshot-20261105.log`。随后补齐 AtomicFile 打开失败的错误处理，快照3项复测与最终签名 Release 6m36s 成功，日志 `tmp/native-light-launcher-snapshot-final-20261105.log`。保留 R8、资源压缩、lintVital 和 GitHub 金标默认；未修改 Gradle 配置，单 worker/临时 4GB 堆。

最终产物 `tmp/review-apks/SleepDown-1.2.8-beta2-launcher-light-snapshot-20261105-9d49be96.apk`，1.2.8_beta2 / 36，7,909,685 字节，SHA-256 `9d49be96f1aea1c35c809a79e0ebc2a4198cba7ae3d0e9387809918590d656c0`，apksigner 验证既有 V2 证书 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。交付摘要 `tmp/native-light-launcher-snapshot-delivery-20261105.json`。

最终重查 SDK 5038 和 OPPO Connect 5037 的 USB、设备表、mDNS 均无设备；这两进程只分别监听 5038、5037。新包未安装，尚无法验证新增系统材质/角度实际输出、实机 Launcher 版本适配和长期冷缓存颠倒；不能用模拟器测试代替这些结论。

云盘上传未成功：原 `SleepDown 测试包` 目录 `1NyxeLWGlDNmHmh2aqTgs4hwkB6O_Hnb4` 已登录、列表文件属当前用户。网页上传 `setFiles` 失败，要求 Edge ChatGPT 扩展开启 Allow access to file URLs。改用 computer-use 技能的原生选择器时，应用状态工具返回 `Computer Use app approval timed out`；未操作安全权限。回读云盘没有新文件及上传完成状态，未上传其他文件、未改共享权限。保留该页签供后续授权后继续；当前仅能提供本地 APK，不能提供虚假的云盘文件链接。

## 用户纠正：恢复原快照流程

用户明确上轮快照提示造成方向偏离，要求恢复原快照流，只微调光效范围。现已撤回前文的 freeze/v5/AtomicFile/缺图课程预览改造；前文关于该方案及其测试仅为历史记录，不代表当前实现或已确定的根因。

`ScheduleSnapshotStore.kt` 与 Git 基线一致，重新使用原 v4 目录、decodeFile 和 JPEG 临时文件保存；`ScheduleAppUi` 两处采集恢复直接使用 GraphicsLayer bitmap，内存快照复用逻辑不变；竖屏重新使用原缺图表现。移除仅保护已撤回 freeze/v5 行为的 3 项新增测试。设备缓存、课程和数据库未删除或迁移。

光效入口仍在上传 COE 纹理前转软件 ARGB_8888，并等待 Surface 首帧，这是已定位原生闪退的修复，不修改截图或持久化流程。光区保留组件实际宽度适配、有限下沿、圆角覆盖及既有角度/宽度调整；未追加其他视觉改动。修订完整签名包构建中，日志 `tmp/native-light-original-snapshots-20261105.log`；新包完成后补记。
