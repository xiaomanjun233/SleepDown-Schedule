# 凝光运行路线与组件审查

基于 `codex/couikit-dialog` 的 `e2f1fe57` 与已有工作树改动。Kit 固定上游基线为 `520478b9`。E 盘恢复后读取本地 Kit 工作区 `f8423c3` 及完整系统包；该工作区仅供参考。

## 已迁移控件

| 入口 | 核查结果 |
| --- | --- |
| 设置和设置搜索 | 继续由 Kit 的 Scaffold、SearchBar 与页面控件装配。外观设置按实际渲染能力显示，切换模式只更新视效偏好，避免交换课程卡片的液态预设。通知测试等显式发光按钮恢复半透明采样。 |
| Dialog、Alert、Picker | 保留 Kit 标准窗口、嵌套窗口源与退出完成交接。固定视效 provider 的组合边界，切换两种模式时保留命名草稿。快速课表设置沿用顶部取消／完成。 |
| 分段、开关、滑条 | Kit 接收宿主受控状态；分段按抬手提交，滑条保留预览、提交与取消。液态及旧系统使用现有后备。 |
| 按钮、顶栏、Dock／FAB | 普通主按钮用 Kit 填色。独立顶栏按钮保留 42dp 并采局部工具栏前缀。Dock 只采页面下层，保留加号锚点及返回层。受光胶囊按原厂单独采样 30px MIRROR 背景。 |
| 课程快捷菜单、三点菜单 | 保留 196dp、无分隔线、原列锚点与立即提交一次。Kit 在 Popup 内观察按压及取消。长按原触点跨 Popup 的光效交接仍需真机核对。 |

## 通用材质与专用效果

- `glass/` 将现有 Kyant 源适配为 Kit 消费者，源与下游按组合生命周期成对保留。先绘制下层源，再绘制背景和清晰前景。表头、日期胶囊、输入与附件、灵动岛、浏览底栏、侧栏胶囊及全屏助理使用各自原有的采样域。教务学校搜索接 Kit 双胶囊，查询、结果及 IME 位置由宿主持有。
- 对照系统 `COUIMaterialBlurEffect`，内容透明按钮的深浅主题均使用 25px、`#3F333333` Overlay 和 `#3FFFFFFF` Luminosity。课程卡与拖动副本复用这套模糊、Kit 轮廓与焦散，并保留既有凝光着色和滑杆偏好。焦散的下侧外延 4.899994dp、SDF 10.399994dp 和色值 `#42FFFFFF` 来自系统 level4 资源。
- 完整包中的 Wallpapers 与缓存设备样本具有同一 `shadow_card.coz` SHA-256 `22fe9bca33fe857d25bccfd636ec878c7c52927aae07208de9fe5a2f43638ef9`。公开绘制路径复用卡片快照、透视几何、四分之一分辨率中间层和运行时两轴半径 80 的设定。窗口分类使用 Android 配置，受光按钮底影依照原资源尺寸与 alpha 分布拟合，触觉改为平台刻度反馈。
- 助理读条沿用已核对的 `COUIIntentSeekBar → IntentConicProgressRenderer` Compose 锥光实现。页指示器保留现有几何和拖动处理，Kit 负责 120px 模糊、亮色薄染及跟手光。渐进模糊沿用 Kit 从 AppBarBlurHelper 链路复刻的双向核，并保留应用现有遮罩、方向和首页表头平台区。
- App 入口只装配 Kit 或 Compose 后备。私有 ColorOS 实现保留供参数对照。独立厂商课程协议与 SharedViewKit 沿既有入口。

## 定向验证

- `:sleepdown-coui:testDebugUnitTest` 13 项与 `:app:testGithubDebugUnitTest` 定向 37 项全部通过，0 失败、0 错误、0 跳过。覆盖窗口反向和草稿、按钮采样、菜单手势与立即提交、Dock 路由、滑条、课程触点归属、新增表面源释放及投光 shader 构造。
- 首次定向回归发现投光 shader 标识符冲突、切换视效时命名草稿被重建以及通知发光按钮缺少采样标记。修正后使用同一用例重新验证。
- 最终同一次 Gradle 执行通过 GitHub 渠道 `assembleGithubRelease` 和商店渠道 `compileStoreReleaseKotlin`，耗时 12m56s。GitHub 产物通过 R8、资源压缩、lintVital、V2 证书、包名与 SDK 验证。扫描 Manifest 与 DEX，未检出旧凝光的系统包查询和私有 renderer 标记。

## 交付

- APK `tmp/review-apks/SleepDown-1.2.8-beta2-COUIKit-20261108-54f6fbac.apk`，7820604 字节，SHA-256 `54F6FBAC9B7596B24DF2A4CF6F4115B7D2BA47C3DDF1C8FDC70CC03B730CD9C9`。已上传用户的 Google Drive 根目录，回读确认文件名、大小及 APK MIME 类型。
- 同次构建的 mapping 保留在 `app/build/outputs/mapping/githubRelease/mapping.txt`，161442291 字节，SHA-256 `B6C881B392B6A859264FF6139914C5D489B6B5923F2531D991244FF7CDFE0AAB`。用该文件还原本包的混淆堆栈，避免在归档和网盘重复保存。

## 真机验收边界

ADB 5037 与 5038 均为 SDK 37.0.1，枚举时没有 USB、已连接无线设备或 mDNS 端点。仍需对照课程焦散、投光、Widget 底影、搜索分离、跨窗口跟手光、横屏和大字体。Robolectric 可验证公开 shader 的构造、状态交接与采样保留，画面一致性及帧耗时需要实机。
