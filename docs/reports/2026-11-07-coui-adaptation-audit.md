# COUI 适配进度盘点

- 日期：2026-11-07
- 分支：`codex/couikit-dialog`
- 源码基线：`5d7f7235`
- 最新交付：`tmp/review-apks/SleepDown-1.2.8-beta2-glow-popup-pickers-20261107-031cf109.apk`

## 结论

公共控件和设置体系已经基本收口：Dialog、Picker、分段、开关、滑条、胶囊按钮、Dock/FAB、系统菜单、设置页 Scaffold/Search/TopAppBar 都已接入 COUI。当前真正剩余的可见缺口集中在**首页/课表的顶栏图标按钮、学校搜索框、首页添加菜单/侧栏**三类；课程卡片、页面指示器等属于材质层，Kit 没有对应组件，继续走原生/液态是合理的。

## 已完成

| 领域 | 状态 | 入口 |
| --- | --- | --- |
| 主题、颜色、字体、图标、页面间距、Overscroll | 完成 | `CouiTheme`、`CouiText`、`CouiIcons`、`CouiPageDefaults`、`rememberCouiOverscrollEffect` |
| 设置页与二级详情页 | 完成 | `CouiSettingsPageScaffold`；全部设置/详情页统一走 COUI 布局，液态仅切材质 |
| 设置行、分组、Popup、RadioButton、SearchBar、TopAppBar | 完成 | `CouiSettingsComponents`、`CouiSettingsPopup`、`SettingsThemeModeUi`、`CouiSettingsScaffold` |
| Dialog / Alert / Input / NameDialog | 完成 | `CouiDialogs.kt`，`ColorOsMaterialProvider` 按模式分发 |
| WheelPicker / CalendarPicker / 自定义表单留白 | 完成 | `CouiDialogs.kt`、`WheelPicker`、`CalendarPicker`、`CouiPickerFormPadding` |
| 胶囊分段选择器 | 完成 | `CouiSegments.kt`、`CouiSegmentedControl`，含回弹/取消/纵向手势交接 |
| Switch / Slider | 完成 | `CouiValueControls.kt`，保留 preview/commit/cancel、snap、compact |
| 普通胶囊按钮 | 完成 | `CouiCapsuleButtonRenderer`；主色/中性、禁用材质、sheen 已映射 |
| 首页/课表顶栏独立图标按钮 | 完成 | `CouiHomeIconButton` 复用 `CouiCapsuleButton`；42dp 圆形、COUI 表面/轮廓/按压/跟手光，仅限 `AppTopBar` |
| Dock + FAB | 完成（COUI 路由） | `CouiDockBar`、`NativeHomeDockChrome`；旧系统/失败回退原生/液态 |
| 课程长按 Shortcut、三点 Overflow 菜单 | 完成（按 Gallery/Weather 复核） | `GlowSystemMenus.kt`、`SystemPopupMenu`；196dp、12dp 面板圆角、退出后提交 |
| 首页菜单冻结、退出后交接、后台停止清理 | 完成 | `GlowSystemMenus.kt` |

## 部分完成 / 剩余

| 项目 | 当前实现 | 影响 | 证据 |
| --- | --- | --- | --- |
| 学校搜索框 | `HomeMaterialControls.SearchField` 仍走 `ColorOsSchoolSearchField`，无原生时走自定义 Glass | 凝光跨品牌时学校选择页搜索框不是 COUI SearchBar；设置页搜索已 COUI | `EduSchoolSelectionUi.kt:967`、`ColorOsSearchField.kt` |
| 首页添加菜单 / 侧栏 | `HomeAddMenuPanel`、`HomeSidebar` 使用 `LocalNativeMenuSurface/Row/List`；无原生时走自定义 Compose | 添加菜单和侧栏仍是原生/自定义材质，不是 Kit Menu；与系统 Shortcut/Overflow 是两套路线 | `HomeAddMenuPanel.kt:291/323/364`、`HomeSidebar.kt:502` |
| QuickSheet / SchedulePickerOverlay | 自定义玻璃外壳，内部按钮/间距/选择器已用 COUI | Kit 没有 QuickSheet 对应组件；是否继续迁移取决于是否要求外壳也统一 | `SleepDownQuickSheet.kt`、`SchedulePickerOverlay.kt` |
| 课程卡片、页面指示器、卡片光、外轮廓 | 仍优先 ColorOS 原生，否则液态 | Kit 没有等价的 Card/Pager 组件；属于材质层，不建议为“组件适配”强行替换 | `ColorOsMaterialProvider.kt`、`ColorOsCourseCard`、`ColorOsPagerIndicator` |
| 独立 FAB / 旧 Dock 回退 | 原生或液态 | COUI Dock 内部 FAB 已完成；旧系统回退保留是预期行为 | `NativeHomeDockChrome.kt:42-59` |

## 未使用 / 不需要迁移

- Checkbox、Chip、SwipeReveal、Kit `NavigationBar`、独立 `Menu`、独立 `Fab`：当前没有对应业务界面，或已被自定义 Dock/SystemPopupMenu 替代。
- 这些不是“漏迁移”，除非后续新增相应交互，不需要为覆盖率强行替换。

## 建议顺序

1. **学校搜索框**：用 Kit `SearchBar` 替换 `ColorOsSchoolSearchField`，需要搜索态和 toolbar/search backdrop；可沿用本轮“局部 COUI 能力分发”的方式，先不拆整页源。
2. **首页添加菜单/侧栏**：与现有菜单、锚点、源卡恢复链路耦合最深，适合单独一轮；不要和顶栏按钮混做。
3. **QuickSheet/Card/Pager**：先确认是否要求外壳统一；否则保留现有材质层，避免为了形式替换破坏已有预览/采样。
4. **顶栏按钮精确系统材质**：本轮用 COUI 胶囊表面完成统一；若后续要求与 `TopAppBarButton` 完全一致，再单独评估拆分顶栏采样源。

## 当前验证基线

- `5d7f7235` 的 26 项定向回归、主编辑器/星期/调色板 3 项、最终菜单/编辑器 6 项均通过。
- 首页顶栏按钮及两个系统菜单新增/覆盖 `GlowSystemMenusTest` 用例；最终 `e43ab2ee` 的该类 4 项通过，0 失败/错误/跳过。
- 最终签名 Release 通过（8m31s，R8、资源压缩、lintVital、V2 签名）。APK `E5D9E435…`，7,841,364 字节；mapping SHA-256 `0134D293…`；内嵌源码 `e43ab2ee`。
- 桌面只保留 APK `SleepDown-1.2.8-beta2-couikit-menu-topbar-20261107-e5d9e435.apk`；mapping 只在 `tmp/review-apks`。
- 未安装、未推送；真机 GPU、系统模糊、OEM 触觉和帧耗时仍未验收。
