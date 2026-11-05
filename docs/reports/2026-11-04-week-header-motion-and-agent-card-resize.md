# 周视图顶栏切周动效与助手卡片伸缩

## 周视图顶栏

用户要求周视图滑动切周时，顶栏所有会变化的内容都改为轻微的缩放、叠化、动态模糊淡入淡出。原来的两套顶栏分别处理：

- 无界周视图的顶栏在 `ScheduleAppUi` 的 AppTopBar 层：`HomeDateTitle` 的“第 X 周”/“点击此处回到本周”，以及 `BoundlessWeekdayHeaderRow` 的周数和星期/日期标签。
- 常规周视图在 `WeekScheduleScreen`：中间“第 X 周”，以及表头胶囊里的 `WeekPagerHeaderLabels`。后者原来按 pager 偏移横向平移两天/两组标签。

新增 `feature/home/week/WeekHeaderMotion.kt`：

- `WeekHeaderCrossfade` 在 target 变化时保留旧内容，旧内容 alpha 1→0、模糊 0→4dp；新内容 alpha 0→1、模糊 4dp→0。缩放不再作用于整个容器。
- 缩放通过 `LocalWeekHeaderTextScale`（`() -> Float`）下发，`WeekdayHeaderLabels` 只在每个日期/星期文字列上读取并应用 `graphicsLayer` scale，因此今日胶囊、表头背景和整个表头不会被放大缩小。
- 动画 280ms，使用 `FastOutSlowInEasing`；去掉 `animateContentSize`，静止时直接绘制当前内容、不再创建任何 `graphicsLayer`，消除了左上角“第 X 周”后面残留的矩形黑框。
- 当可见星期数变化（5 ↔ 7）时，outgoing/incoming 行改为 20dp 模糊平移（6dp 模糊），文字不再缩放：新增行从展开方向滑入，旧行向相反方向滑出。
- API 31+ 使用已有 `rememberPageMotionBlur()` 缓存 RenderEffect；低版本只保留 alpha/translation。

接入点：

- `HomeDateHeader.kt`：顶栏第一行“第 X 周”/学期提示/回到本周提示；静止无额外图层。
- `WeekScheduleHeaders.kt` 的 `BoundlessWeekdayHeaderRow`：左侧周数单独交叉叠化；右侧标签的 target 为 `(周数, 可见星期列表)`，因此隐藏周末等设置变化（5↔7）也会触发模糊平移；每个日期文字单独缩放，今日胶囊不缩放。
- `WeekScheduleScreen.kt`：常规周视图中间“第 X 周”，以及 `WeekPagerHeaderLabels`。
- `WeekPagerHeaderLabels` 改为连续跟手：按 `currentPage + currentPageOffsetFraction` 取 `floor` 页和下一页；base/next 只做 alpha 和模糊，文字单独缩放；当两侧可见星期数不同时增加方向相关的 20dp 平移和 6dp 模糊。静止时用一次性 `WeekHeaderCrossfade` 包住当前标签，这样设置变化（5↔7）也会动画，同时滑动结束后不会重播旧标签。移除了原 `displayWeek` 参数。


## 今日助手卡片伸缩

`DayAgentCard.kt`：

- 当前内容的 `animateContentSize` 从 320ms tween 改为 `spring(dampingRatio = 0.9f, stiffness = 380f)`，卡片高度随内容变化做轻微弹性伸缩。
- 把 `clip(shape)` 和 `dayAgentCardSurface` 移到 `animateContentSize` 外侧，让材质、描边和圆角按动画中的实际高度绘制，而不是先跳到目标高度。
- 旧内容的 1.25s 爆发副本从 `fillMaxWidth` 改为 `matchParentSize`，并显式关闭自身的 `animateContentSize`。它只作为淡出/模糊覆盖层，不再参与 Box 高度，也不会因父高度逐帧变化反复启动尺寸动画；卡片高度只由当前内容驱动。
- 卡片外壳、原生/液态玻璃和文字裁切继续随内容尺寸伸缩。

## 同日修正：共享元素化的顶栏文字

用户反馈叠化+动态模糊会闪烁、左上角“第 X 周”在动画途中仍会闪黑框，并明确顶栏所有文字都应是共享元素：字一样但位置不同靠非线性平移，字不一样靠叠化，两者同时发生就一边平移一边叠化（5 天切 7 天时原列左移、多出的两天从右进入）。上面第 12–16、21–23 行的描述已被本节取代。

两个根因：

1. `WeekHeaderCrossfade` 在 `LaunchedEffect` 里交换 current/previous，切换后的第一帧仍在渲染已经置为新的内容、`progress` 还是上一轮的 1，于是新文字先以满透明度画一帧，下一帧才回到旧文字开始叠化——这就是肉眼看到的闪。
2. 左上角黑框：单条文字叠化时，两层副本各自带一份 `HomeReadableText` 可读性阴影（黑/白、alpha 最高 0.62、模糊 5–12dp），叠在一起等于阴影翻倍，在周数文字后面压出一个边缘清楚的半透明暗矩形；同理，该槽位的模糊层正好是 44×46 的图层，也同尺寸。

第一轮改为“按列共享元素 + 无任何 RenderEffect”，用户判定拖影观感不可用（`990d69e4`），要求恢复第一版的缩放+模糊叠化，只在其上叠移动。最终实现：

- `WeekHeaderCrossfade` 保留第一版观感（280ms `FastOutSlowInEasing`、alpha 叠化、文字 2% 缩放），但旧内容改为组合期同步取（`remember(target)` 读上一次已提交的 target，进度随 target 重新置 0，`SideEffect` 提交），消除闪一帧新字；两层副本统一提供 `LocalHomeTextShadowScale = 0.5`，阴影总量保持一份，不再叠出暗矩形；单条小文字不再挂 `RenderEffect`。
- 新增 `WeekdayStrip` / `WeekdayStripTransition` / `rememberWeekdayStripTransition`：一次变化由 `from`、`to` 两个 `WeekdayStrip` 和 0..1 进度描述，进度只在布局/绘制 lambda 里读取，动画期间不重组。一次性变化（切周、隐藏周末设置）沿用第一版的 280ms 立方缓动；动画中途反向时沿同一对状态退回，不跳帧。
- 星期列改为自定义 `Layout`（`WeekdayHeaderStrip`）：列位置用累计取整，末列与课程网格边界严格对齐；布局只决定目标槽位，动画期间由各列自己在绘制期计算位移，不需要逐帧重新布局。
  - 两态都存在的列＝共享元素，按中心位移平移；
  - 只在新态的列从最近的顶栏边缘滑入并淡入（5→7 时周六、周日从右侧进入），只在旧态的列反向滑出；
  - 同一列日期/今日/调休状态变化时，该列内部两行文字做 alpha 叠化和 2% 缩放；今日胶囊、表头背景不缩放；
  - 整行在第一版的位置再挂一层模糊（`4dp × (1 - |2f - 1|)`，端点 0），所以移动中的列仍在动态模糊里。
- 常规周视图的 `WeekPagerHeaderLabels` 直接以 `pagePosition - basePage` 作为进度，跟手插值列位置、文字叠化与整行模糊，不再叠两整行、不再整行 20dp 平移。
- 保持位置和列宽的列（`SlotMotion.motionless`）不挂 `graphicsLayer`，静止的顶栏依旧没有额外图层。

改动文件：`feature/home/week/WeekHeaderMotion.kt`（重写）、`feature/home/week/WeekScheduleHeaders.kt`（星期列重写、`LocalWeekHeaderTextScale` 与整行 20dp 平移删除）、`feature/home/day/HomeDateHeader.kt`（日期大标题也走同一叠化）、`feature/home/day/HomeReadability.kt`（新增 `LocalHomeTextShadowScale`）。

## 验证

- `compileGithubReleaseKotlin` 通过。
- 完整签名 `assembleGithubRelease` 通过（4m15s，R8、资源压缩、lintVital、GitHub 金标），产物 `1.2.8_beta2 / 36`，7,893,241 字节，SHA-256 `f35047aaf51048f76b095fad8ce58a1d1dfd9f7a6a579714def976038369c2b5`，保全 `tmp/review-apks/SleepDown-1.2.8-beta2-week-header-card-motion-20261104-f35047aa.apk`。
- 当前 5038、5037 与 mDNS 均无设备，未安装；实际切周手感、模糊强度、卡片伸缩是否无缝待实机确认。
- 共享元素化修正后再次 `compileGithubReleaseKotlin` 通过，并完整签名 `assembleGithubRelease`（3m29s，R8、资源压缩、lintVital、GitHub 金标），产物 `1.2.8_beta2 / 36`，7,893,241 字节，SHA-256 `990d69e47f6e1cf1dfb4d3e8213efd43676366e6058df07e4bf181107621aec6`，`apksigner verify` 沿用既有证书（`0f2b50df…`），保全 `tmp/review-apks/SleepDown-1.2.8-beta2-week-header-shared-motion-20261104-990d69e4.apk`。该版拖影观感被判定不可用，仅作过程记录。
- 恢复第一版叠化并叠加列移动后，`compileGithubReleaseKotlin` 通过，完整签名 `assembleGithubRelease` 通过（4m23s，R8、资源压缩、lintVital、GitHub 金标），产物 `1.2.8_beta2 / 36`，7,893,241 字节，SHA-256 `4d1fad21b817cb50d6bb634c388526a1ccee3498759e02f1d175b858c7e16ba1`，签名证书沿用既有 `0f2b50df…`，保全 `tmp/review-apks/SleepDown-1.2.8-beta2-week-header-move-20261104-4d1fad21.apk`。
- 该包仍未安装（5038、5037、mDNS 均无设备）：闪烁是否消失、左上角黑框是否消失、列平移与整行模糊的实际观感、卡片伸缩均待实机确认；若黑框仍在，说明还剩别的暗色图层，需要动画中的截图定位。
