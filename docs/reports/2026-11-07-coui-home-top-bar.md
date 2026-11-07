# 首页顶栏 COUI 图标按钮

日期：2026-11-07  
分支：`codex/couikit-dialog`  
源码基线：`5d7f7235`  
实现提交：`5a505ec8`

## 范围

把首页/课表 `AppTopBar` 内的独立图标按钮切到 COUI 胶囊按钮材质，保留原有：
- `HomeMaterialControls.IconButton` 能力分发。
- 液态/旧系统回退。
- 添加/个性化复合胶囊的原有玻璃外观。
- 页面图标按钮、Dock/FAB、菜单等既有路线。

## 实现

- `CouiHomeIconButton` 复用 `CouiCapsuleButton`：
  - 42dp 圆形，20dp 图标。
  - 浅色表面 `0x99F5F5F5`，深色表面 `0xCC262626`。
  - COUI 轮廓、按压形变、跟手光、禁用材质和语义。
  - 选中态使用宿主 `MaterialTheme.colorScheme.primary` 作为图标色。
- `LocalCouiHomeTopBarActive` 只在 `AppTopBar` 外层提供，避免影响：
  - 添加/个性化复合胶囊。
  - 首页外部源卡/返回按钮。
  - 其他页面的图标按钮。
- `ColorOsMaterialProvider` 的 `homeControls.IconButton` 只在 COUI 控件可用且位于顶栏作用域时使用 COUI；否则继续原生/液态。
- 没有引入新的 `TopAppBarButton` 采样源。首页顶栏位于整页 Dock 源内部，直接新增顶栏 backdrop 会自采样或需要拆分页面源；本轮用胶囊按钮自身的表面/轮廓/运动完成视觉统一，避免额外整页录制。
- 未新增 Kit 公共 API；`TopAppBarSurface` 保持原样。

## 验证

- `GlowSystemMenusTest` 4 项通过，含新增 `homeTopBarUsesCouiButtonInsideToolbarSurface`；0 失败/错误/跳过。
- 截图 `tmp/coui-visual-review-20261106/coui-home-top-bar-button.png` 已查看：圆形 COUI 表面、图标、按压命中正常。
- 正式 `assembleGithubRelease` 通过（7m14s，R8、资源压缩、lintVital、V2 签名）。
- APK：7,857,748 字节，SHA-256 `E60F0BEE305765FF5A640B135EA13FCF549FBC568F0BD7D32391EF89838EECC6`。
- Mapping：162,209,088 字节，SHA-256 `6EFC0059BAF16EF1B9478620C8E8CDFADF81D136221039B24DC4AFB3F2600765`。
- 内嵌源码：`5a505ec8c31127bd18df1ecfe1564741414132d6`；V2 证书 `0f2b50df…`。

## 桌面包策略

从本轮开始，签名 APK 与精确 mapping 放桌面，并且桌面只保留最新一套：

- 当前桌面目录：`D:\个人资料\桌面`
- 当前文件：
  - `SleepDown-1.2.8-beta2-couikut-home-topbar-20261107-e60f0bee.apk`
  - `SleepDown-1.2.8-beta2-couikut-home-topbar-20261107-e60f0bee.apk-mapping.txt`
- 下一轮构建成功并校验新包后，删除桌面上的上一套 `SleepDown*.apk` / `SleepDown*-mapping.txt`；构建失败时保留上一套。
- 仓库 `tmp/review-apks` 的历史包不因桌面清理而删除，仍作为本地追溯记录。

## 待真机

- 顶栏按钮在深浅壁纸/深色主题下的表面亮度、描边和跟手光。
- 大字体、横屏、顶栏折叠/转场和组合动画。
- 与系统 `TopAppBarButton` 的精确材质差异；如果需要完全对齐，再拆分顶栏采样源单独评估。
