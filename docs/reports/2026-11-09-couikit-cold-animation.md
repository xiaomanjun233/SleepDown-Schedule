# 凝光首开动画与融球实现核查

续接 `codex/couikit-dialog` 的 `e2f1fe57` 和现有未提交的 Kit 迁移。覆盖设置搜索、Dock 加号回归、首页课程编辑器、跳转周数和编辑器内的节次选择。

## 完整包取证

从 E 盘 PMX110 `17.0.0.115` 完整包的分区产物直接取证。`system` 和 `system_ext` 的 build incremental 均为 `1791819005317`。取证对象的 SHA-256 前缀为 Settings APK `D97DF8A92275`、Launcher APK `628C791B62B8`、`oplus-framework.jar` `34CF64A9E723`、`libhwui.so` `92F43D020DBD`、`librenderengineextimpl.so` `1174428082D2`。

- 设置的 `COUISearchBar.changeStateWithAnimation` 启动前给搜索背景和功能按钮设定主体索引并绑定融球链。回到收起态时 `resetBallChain` 缩起功能按钮，解除搜索背景的关联。材质和 IME 是各自入口，凝光展开中的输入请求使用 `post` 调度。
- 设置和 Launcher 的 `COUIBottomFloatingAccessoryNavigationBar` 均分开持有菜单和附件 View。设置 APK 的融球路径给菜单使用索引 1，附件使用索引 2。其扩展控制器独立更新位移、缩放和透明度，退出时清除对应附件的融球参数。`COUIMaterialBlurEffect` 给背景组装 `MIRROR` 模糊和两层混色，轮廓、焦散分别设置。
- `COUIMaterialEffect` 经 `OplusMaterialUtil` 把主体关联写到 RenderNode。`OplusViewBackgroundRenderEffect` 也把背景效果写到 RenderNode，返回变更后才使 View 缓存失效。在 `libhwui.so` 用 JNI 注册表定位 `nSetOplusMaterialMetaBallParams`（`0xa1aa50`）。汇编比较五个参数，变化时设置 dirty 位，关联的材质入口（`0x732280`）已有滤镜时复用，缺失时才创建 `SkImageFilters::Material`。
- `librenderengineextimpl.so` 的 `SkiaRenderEngineExtImpl::precompile` 与 `OplusJsonShaderPrecompilation::drawOneFile` 属于系统 RenderEngine。后者有物化图层、创建输出缓冲和等待 Fence 的路径。组件反编译证据可追溯至 HWUI RenderNode，系统预编译入口单独记录。

## 同包预绘对照

Pixel 10 Pro AVD、Android 16、RTX 4060 宿主 OpenGL、1280×2856。同一 V2 签名诊断 APK `dc122e09`，ART `verify`，每次新进程前通过 ProfileInstaller 清理应用 shader 缓存，广播返回 14。按开、关、关、开交错。每轮取主窗口操作后 1.25 秒 FrameTimeline 的应用帧，P95 是应用帧耗时。输入到呈现用触摸事件时间与主窗口下一次可关联的呈现估算。

| 首开场景 | 开预绘 P95 | 关预绘 P95 | 开 → 关的输入到呈现 |
| --- | ---: | ---: | ---: |
| 搜索较早触发 | 48.3–71.5 ms | 34.1–41.2 ms | 64.4–64.6 → 43.3–51.6 ms |
| 搜索空闲后首开 | 44.2–56.1 ms | 44.3–89.3 ms | 47.8–54.9 → 47.6–58.5 ms |
| Dock 较早触发 | 36.2–36.3 ms | 32.6–39.6 ms | 62.8–77.3 → 40.3–48.2 ms |
| Dock 空闲后首开 | 47.7–111.5 ms | 46.2–46.6 ms | 53.2–121.9 → 44.2–46.0 ms |

搜索较早场景改用进入设置后约 0.92 秒的触发，0.4 秒的测试未命中搜索回调并排除。关闭预绘消除该阶段的同步读回。搜索空闲后的一次首开和复开在关闭预绘时出现约 30 ms 的驱动链接，故候选保留可见主体的预绘。Dock 仍有波动，候选只跳过附件隐藏或禁用时的预绘。原始追踪及汇总位于 `tmp/coui-cold-ab1-*` 和 `tmp/coui-cold-ab-early-*`。

## 候选实现

- 融球继续按主体计算遮罩和 Z 次序。两个搜索主体的采样配方相同时共享输入效果，槽位映射和绘制分组随配置复用。投影共享编译好的 Mesh 规格，投影绘制记录随几何重建，背景变化只刷新采样。追踪分开记录 renderer 建立、材质、焦散、投影、采样、预绘读回、IME 请求和加号区域截图。
- 首页清晰和缩采样背景直接引用同一份已录制场景。场景键或尺寸失效时刷新源，背景包装层随尺寸重建。编辑器凝光卡只订阅其渲染分支使用的模糊参数。
- 菜单和独立弹窗可复用相同 Kawase 等级及像素网格的 RenderEffect。缓存只持有滤镜描述，采样画面和图层由所在窗口管理。窗口配置、首次布局和 Kawase 录制分别打点。

## 构建与回归

完整 `assembleGithubRelease` 通过，包含 R8、资源压缩和 lintVital。签名候选 `e1a9b810` 大小为 7,836,988 字节，SHA-256 为 `E1A9B810AE85C138A8B5C3F3D279688BA965E1FD940B0DD0283A0A9172C57185`，V2 签名校验通过，包名和版本为 `com.xiaomanjun.sleepdownschedule`、`1.2.8_beta2 / 36`。APK 中有 `baseline.prof` 与 `baseline.profm`，从本次 R8 mapping 与最终 profile 核对 SearchBar、Shadow.draw、Spotlight 三条热规则均有映射。Kit 13 项、应用 37 项定向测试通过，覆盖选择器、弹窗反向、导航锚点和首页背景策略。

### 签名包模拟器复测

同一 AVD 开机周期覆盖安装 `abc7ea77` 后测量，再覆盖安装 `e1a9b810` 测量。两包使用 ART `verify` 并按场景重置应用 shader 缓存。此表是 `gfxinfo framestats` 操作窗口的帧耗时 P95，每个入口一组。追踪和实际帧数记录于 `tmp/coui-cold-sameboot.log`。

| 场景 | 旧包首开／复开 | 候选首开／复开 |
| --- | ---: | ---: |
| 设置搜索 | 152.4／114.9 ms | 135.1／74.4 ms |
| Dock 回到课表 | 176.4／107.3 ms | 142.1／132.4 ms |
| 课程编辑器 | 147.9 ms（19 帧） | 280.0 ms（13 帧） |
| 内层节次弹窗 | 402.1／192.3 ms | 269.1／108.2 ms |

Dock 复开这轮的 RenderThread `flush layers` 累计 64.1 → 36.2 ms，最长 `eglSwapBuffersWithDamageKHR` 为 62.1 → 83.4 ms，最长 `dequeueBuffer` 为 39.4 → 64.9 ms。候选的投影、材质效果、采样与焦散 CPU 单次记录分别不超过 1.0、0.6、0.2、0.2 ms。独立的底栏加号测试中，区域 `PixelCopy` 用时 7.3 ms，菜单在预期位置打开。

另一次候选节次追踪中，两个弹窗窗口各完成一次初始化，同一网格的 Kawase 配方只建立一次。首开仍有 56.3 ms 的缓冲分配、130.7 ms 的最长 RenderThread 同步等待和 48.6 ms 的单次 shader 编译。周数弹窗、凝光和液态编辑器的打断重开均通过功能点验。

同一候选正常侧载后系统报告 `verify`。应用包内 Profile 并确认 `speed-profile` 后，搜索首次 1.25 秒内两次样本的 JIT 记录从 51／102 次变为 9／11 次。FrameTimeline 首开 P95 从 130.1／80.3 ms 变为 80.5／102.1 ms，复开样本也有波动。本轮保留已有热规则。新版关闭预绘的早期搜索样本中，最长驱动链接为 57.3 ms。保留可见主体的预绘，实际触发与读回是否相交继续依赖各次挂载时机。

### 首次巨卡与连续点击对照

候选 `e1a9b810` 保持 ART `verify`。每个入口清一次应用 shader 缓存后连点六次，再分别测保留应用 shader 缓存的重启，以及再次清缓存的重启。每次测量保留同一课表、壁纸和交互路径。

- **Dock** 的 `gfxinfo` P95 依次为 **258.6、65.6、59.0、41.5、31.6、49.8 ms**。前 3 次各有 11、6、2 次 `driver_link_program`，后 3 次均无驱动链接记录。最长 `postAndWait` 从 186.6 ms 降到后 3 次的 14.9–25.1 ms，JIT 从 108 次降到 30–41 次。保留应用 shader 缓存重启后首开回到 305.1 ms，仍发生 12 次驱动链接。再次清缓存的样本 P95 为 861.7 ms（仅 7 帧），最长 RenderThread 同步等待 388.6 ms。进程重建即可带回明显开销，单次极慢样本用于定位。
- **搜索** 同进程 6 次 P95 为 78.5、83.5、123.6、125.5、69.0、68.9 ms。第 3 次仍触发一次 110.9 ms 的驱动链接。保留应用 shader 缓存重启后首开 P95 65.6 ms，再清应用 shader 缓存后重启升至 121.6 ms，最长驱动链接 11.5 → 24.3 ms。复测支持 shader 缓存影响冷路径，JIT、RenderThread 提交和 IME 仍各有开销。

原始追踪和样本在 `tmp/coui-cold-series-*` 与 `tmp/coui-cold-repeat-series.log`。以上复现了多点几次后变顺，首开停顿仍存在。签名候选用于本地验证，尚未完成 ColorOS 真机上每个入口 10 次首开和复开、50 ms 的验收。
