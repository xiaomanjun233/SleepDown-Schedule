# 多课表管理页：卡片照亮下方按钮（凝光）

## 接手修正：直接调用系统光效（2026-11-04）

用户本轮要求“帮我接手，让多课表管理页正确调用这个光效”。沿用 `codex/rescue-import-materials` 的未提交工作树，只修改多课表浮层及材质桥接；保留其他活动任务。下文“本项目接入”和旧 APK 记录为上一轮历史实现，本轮已替换其中的 Compose 模拟光带。

- `ColorOsCardLight.kt` 从已安装且带系统应用标志的 `com.oplus.wallpapers` 加载真实 `com.oplus.wallpapers.personal.views.WallpaperShadowView`。类名已通过原始 dex descriptor 核对，不能使用临时反编译 Java 顶部被重写的 `com.coui.appcompat.toolbar.views` 包名。引擎、native 库与 `shadow_card.coz` 均继续由系统 APK 提供，不复制到本项目。
- View Context 和 `applicationContext` 的 resources/assets/classLoader 成对指向该系统 APK，宿主身份仍使用 SleepDown；后者是 COE 构造器找到原始 `.coz` 的必要条件。资源高度读取 `wallpaper_shadow_height`，原生 View 自行测量设备对应宽度；保留原厂投影、两趟降采样模糊及参数，不再手画 24 条扇形切片。
- `NativeCardLightRequest` 接入公共 `ControlMaterialRenderers`，只有凝光 provider 提供该能力。`ScheduleCardGlow` 仅转发请求，业务层不再直接引用 `platform/coloros` 或另读偏好；系统组件缺失/不兼容时记录诊断并略过附加光效，保留原有课表与按钮。
- 浮层将 native host 放在黑色背景之后、卡片与控件之前，覆盖整个浮层。按真实卡片底边定位，避免 AndroidView 在 pager 边界裁掉射向按钮的光。切页停稳且目标页匹配后才渐亮；拖动、重命名、删除和退出时淡出，透明度叠加浮层 chrome 进度。图片只在来源改变时上传，不随透明度动画重传。
- 三个底部按钮通过显式 `nativeLightBackdrop` 请求调用原厂 `OplusViewBackgroundRenderEffect.setBackgroundRenderEffect(createBlurEffect(30f,30f,MIRROR), view)`，先清除普通 COUI 模糊/着色预设，保留现有跟手光、描边、原生触摸与 Compose 内容，再叠加已有 `#4DFFFFFF → #1AFFFFFF` 白色着色。普通按钮不进入这条分支。该调用采样 View 背景，不是给文字调用 `setRenderEffect`。
- Activity 生命周期、光效显隐与宿主 detach 共同管理 `d()`/`e()`（原 APK 的 onStart/onStop）。退出移除原生 View，由它释放 COE/Surface；原生 View 不进复用池，不回收课表快照。Android Manifest 仅增加壁纸包可见性查询。

首包 `7bab36cc…` 的 4 项 `ColorOsCardLightTest` 与完整签名 Release（10m10s）通过，随后用户授权“做完安装到adb”。通过 SDK 37.0.1 / 15733141 的客户端与同来源 5038 USB 服务向 PLJ110 / Android 17 / SDK 37 / `3B15AE023YL00000` 覆盖安装 Success；未清数据、未由助手启动应用。已检查其他 adb 监听，只有 OPPO Connect 5037（无设备）与 SDK 5038，两端 mDNS 无无线端点。首包为 `1.2.8_beta2 / 36`、7,893,273 字节、SHA-256 `7bab36cc1458863bd3395764a736d5b0da6f1704608b2d1229f98e94e0e277c5`，既有 V2 证书；该包保全在忽略的 review-apks 下。

### 实机闪退与修复

用户打开后反馈“这个光效打开闪退，看看日志”。真实崩溃为 23:23 多次 `COE_Render` 线程 `SIGSEGV / SEGV_MAPERR`、地址 `0x54`，顶部 `libnaiveEngine.so / Material::setTexture +144 → SceneManager::doTasks`，不是 Java 异常，`prepare` 不能拦截。**首包不作为可用验收包**。

手机系统组件 `17.04.0034 / 17040034` 与本机调查样本 `17.04.0037_sp3 / 17040037` 不是同一 APK。只读核对手机 APK 的 classes6：原始类名、Context 构造器、`d/e/setBitmap/setViewAlpha/setViewProcess` 均为公开且签名匹配，COE 库存在；`shadow_card.coz` 与本机样本 SHA-256 完全相同（`22fe9bca33fe857d25bccfd636ec878c7c52927aae07208de9fe5a2f43638ef9`）。实机库仅导入 `AndroidBitmap_getInfo/lockPixels/unlockPixels`，无 HardwareBuffer 读取入口；`convertJavaBitmapToNative` 也有 lockPixels 失败诊断。课表快照来自 `GraphicsLayer.toImageBitmap()`，GPU 的 HARDWARE Bitmap 必须转换后才能交给此 JNI。

修复加入两项边界：非软件 ARGB_8888 快照先复制为可读 ARGB_8888，不回收用户原快照；保留原生 Surface listener，通过 shader alpha=0 预热，等首帧 Surface update 后才提交纹理，避免构造后抢先 stop 或 View alpha=0 阻止 Surface 创建。原有 4 项回归及新增首帧时序回归（5 项）先通过，最终加入硬件 Bitmap 回归后的 6 项与 Release 构建中，日志 `tmp/schedule-picker-native-light-crash-fix-20261104.log`。最终安装及实机结果待补记。

定向测试验证宿主协议与像素转换，不能替代设备上的 COE/GPU 验收。全仓架构检查当前另有既存 `DayAgentProgressBar.kt:35 feature.agent → platform` 违规；本轮未改该任务文件。

## 原效果取源

ColorOS 的「桌面、锁屏与个性化」（`com.oplus.wallpapers`）首页，顶部是保存的锁屏样式卡片，卡片下方一条光把按钮照亮。反编译结果：

- 布局 `my_theme_header_item`：`PersonalMyThemeRecyclerView`（样式卡片）→ `ViewStub coeViewStub`（布局 `my_theme_shadow_stub`）→ `WallpaperShadowView`（id `coeView`，高 `wallpaper_shadow_height` = **136dp**，`clipChildren=false`）→ `AllThemeButton`（id `allThemeButton`）。
- `BasePersonalFragment` 在 `K(View)` 里 inflate 该 stub 并 `setViewAlpha(0f)`；`y(int, Bitmap, g, Function1, boolean)` 里 `shadowView.setBitmap(bitmap)`，当前样式且非 showPath 时 `animate().alpha(1f).setDuration(1000L)`；`onStart/onStop` 调 `shadowView.d()/e()` 控制渲染循环；`MyThemeShadowViewScrollListenerImpl` 在轮播滚动时切换画面并做 alpha 0→1（步长 0.1）的隐现。
- `WallpaperShadowView` 是 `TextureView` + `COEEngine`，加载 `assets/shadow_card.coz`（effect `SlidingWindow`），30fps；`setBitmap` → `u_OriginalTexture`，`setViewAlpha` → `uAlphaC`，`setViewProcess` → `uProcess`；尺寸变化时建 3 张 **1/4 分辨率** RenderTexture，两趟 box blur（`uBlurRadiusX1`/`uBlurRadiusY1` = 80）再合成。

即：**卡片画面 → 降采样 → 两趟大半径模糊 → 合成到卡片下方 136dp 的发光带**，按钮落在这片光里。

### 2026-11-04 原厂按钮受光调用补充

再次从 `E:/COUI/erofs/my_stock/priv-app/Wallpapers/Wallpapers.apk` 的 `classes6.dex` 核对：

- `com.oplus.wallpapers.personal.views.AllThemeButton#e()` 在 `AppFeatureUtil.l0()` 分支中执行 `RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.MIRROR)`，再由 `OplusViewBackgroundRenderEffect.setBackgroundRenderEffect(effect, getButtonWrapper())` 设置**背景**模糊。`onAttachedToWindow()` 调用该方法；这是按钮采样背后光带的独立处理，不能仅用按钮自身内容的 `View.setRenderEffect` 替代。
- 同分支的 `all_theme_button_shape_background`（APK 中 `res/xr1.xml`）是 270° 竖向渐变，`startColor=#4DFFFFFF`、`endColor=#1AFFFFFF`。按钮玻璃由背景采样与这层透明白色着色组成；布局 `all_theme_button_layout` 中按钮是 `AllThemeButtonSpotlightFrameLayout`。
- `AllThemeButtonSpotlightFrameLayout` 通过 `com.oplus.wallpapers.core.base.view.ui.e` 创建 COUI 跟手光，类型为 `TYPE_TRANSPARENT_SMALL_1`，Drawable 放入 `ViewOverlay`；触摸事件转发、尺寸更新和 detach 清理均由原包装处理。
- `AllThemeButton` 另加 `EffectiveAnimationView`，加载 `assets/light_button_border.json`；`setAnimationProgress(float)` 驱动该描边动画，并有独立底部阴影图 `all_theme_button_bottom_shadow`。这些是按钮附加效果，卡片发散光仍由 `WallpaperShadowView` 与 `shadow_card.coz` 绘制。

本次为调用点调查，未修改应用实现；现有 Compose 光带不能据此写成已接通原厂按钮背景采样。

## 本项目接入

页面：首页加号菜单 / 长按空白处 / 侧边栏打开的「多课表管理」——实际是 `feature/schedule/picker/SchedulePickerOverlay.kt` 的全屏浮层（堆叠卡片轮播 + 页点 + 底部 分享/课表设置/新建），不是 `ScheduleManagerActivity`。

**第一版接错了位置**：`ScheduleManagerActivity` 只在 Manifest 里声明（`android:parentActivityName=".MainActivity"`），源码里没有任何地方 `startActivity` 它，属于遗留页面；`SettingsPage.ScheduleManager` 的列表页也只挂在 `SCHEDULE_MANAGER` 深链与 AI 页面交接上。这两处都不改，浮层才是真实界面。

- 新增 `feature/schedule/manager/ScheduleCardGlow.kt`：
  - `rememberScheduleManagerGlowEnabled()`：只在 **凝光**（`effectiveVisualEffectMode == ColorOsGlow`，且 `ColorOsNativeUi.source` 可用、API ≥ 31）时启用；浮层没有 `LocalVisualEffects` 提供者，直接读 `VisualEffectsPreferences`。
  - `ScheduleCardGlow`：把居中的 `ScheduleCarouselCard` 录进 `GraphicsLayer`（`record` 在卡片自身绘制时执行，卡片本体照常清晰绘制），发光带里把整张卡**压缩 + 模糊**后铺开（对应原版 1/4 分辨率两趟 80 半径的 box blur），再用 `BlendMode.DstIn` 的竖向渐变（0.62 → 0.34 → 0.10 → 0）和左右各 34dp 的横向渐变淡出。
  - **不能画黑底**：浮层背景是半透明黑罩 + 首页，第一版用「渐变到页面黑底」做遮罩会把首页压暗，所以改成 `saveLayer` + `DstIn` 的 alpha 遮罩，光带本身保持透明。
  - 模糊用 `BlurEffect(38dp, TileMode.Clamp)` 挂在被捕获的图层上，只影响光带里的副本；卡片本体不做离屏。
  - 光带高 152dp，起点是卡片底边上方 8dp。
- `SchedulePickerOverlay.kt`：光带作为卡片区 `BoxWithConstraints` 的最后一个子节点插入（`Modifier.matchParentSize()`），因此绘制顺序是 **卡片 → 光带 → 页点/按钮**：光在按钮下面，且能画出卡片区、覆盖底部 138dp 的操作区。卡片矩形用已有的 `boundsInRoot` 上报、卡片区原点单独记录，坐标相减；两者都只在绘制期读取，滚动不触发整页重组。滚动中不再重录图层，沿用上一次捕获。
- `ScheduleManagerUi.kt`：撤回第一版加在遗留 Activity 页上的光效，只保留 `ScheduleCarouselCard` 新增的可选 `captureLayer` / `onCardBounds` 参数（浮层用）。

## 验证

- `compileGithubReleaseKotlin` 通过。
- 完整签名 `assembleGithubRelease` 通过（4m36s，R8、资源压缩、lintVital、GitHub 金标），`1.2.8_beta2 / 36`，7,893,241 字节，SHA-256 `1843d4a586a40f21605cd0dfcaca736d23a5591a6c42a80695733230330ab141`，`apksigner verify` 沿用既有证书 `0f2b50df…`；保全 `tmp/review-apks/SleepDown-1.2.8-beta2-picker-glow-20261104-1843d4a5.apk`。
- 前一版 `b31fe6e1` 接在遗留 Activity 页上，实机看不到；已撤回到浮层。
- 未安装：当前 5038、5037、mDNS 均无设备。光带亮度、模糊半径、是否压住页点、以及与液态玻璃模式的对比均待真机确认。
