# 2026-10-23 个性化内容交接、液态关闭交接点与同卡片连续打断

用户要求：先按课程卡片的文字/表单处理方式改造个性化弹窗；液态关闭的切换再早一点；首页支持连续打断（打开到一半返回或上滑，立刻在当前进度减速反向；反向途中再点同一卡片马上回到打开状态）；之后再尝试并行动画。补充说明打断不分材质模式，只有交接点不同。

## 1. 个性化内容改用课程卡片的交接方式

课程编辑的做法是：表单在目标尺寸布局一次，`wrapContentSize(TopStart, unbounded)` + `requiredSize(target)`，`graphicsLayer` 用外壳原点把目标尺寸非等比拉伸到运动矩形，透明度走预载叠化，液态额外叠临时模糊。

个性化弹窗原来是把目标尺寸的内容层**居中**缩放，视觉上像被不断缩小的窗口裁切。现在改成与课程卡片完全一致：

- 内容层改为 `wrapContentSize(Alignment.TopStart, unbounded = true)` + `requiredSize(targetWidth, targetHeight)`；
- `transformOrigin` 设为左上角，`scaleX/scaleY = 运动矩形 / 目标尺寸`；
- 透明度继续用预载叠化，静止态尺寸与位置不变。

## 2. 液态关闭的切换提前

`liquidPreloadedContentAlpha` 的液态窗口由 18%～50% 提前到 12%～44%。开合共用同一窗口，所以关闭时更早交还卡片内容，且中途反向时透明度连续；凝光仍是 35%～70%。

## 3. 同卡片连续打断

- 返回/点空白在打开途中触发：原本就由 `LaunchedEffect(request)` 取消打开动画，从当前进度反向，未改。
- 反向途中再点同一张卡片：
  - `openCourseEditor` 允许在 `Closing` / `Disposing` 阶段继续打开，并复用上一次请求的源快照，避免重新截屏（截到的会是正在收回的外壳）；
  - 宿主识别「同一课程 + 当前进度 > 0 + 正在关闭」为恢复，跳过 `Preparing`、`snapTo(0)` 与首帧布局等待，直接从当前进度动画回 1，时长按剩余距离等比缩短，背景景深同步回退。
- 反向途中点**另一张**卡片仍被忽略，等这段关闭播完；并行动画不在本轮。

## 4. 并行动画：未实现，需要结构改动

当前宿主只有一个 `CourseEditorMotionState`、一份 `renderedRequest` 和一套表单渲染。要让「关闭 A」与「打开 B」各自完整播放，需要二选一：

1. 双会话：宿主持有两个 `(request, progress, phase)`，各自一套外壳与表单；改造成本高，但同时保留真实内容；
2. 快照幽灵：新请求到达时把正在关闭的外壳抓成一张位图，交给独立的幽灵动画收回卡片，主会话立即开新编辑器；实现小，但关闭段是冻结画面。

本轮没有动这部分，等确认方向后再做。

## 验证

- `compileGithubReleaseKotlin` 通过（41s）。
- 完整 `assembleGithubRelease "-Psleepdown.sharedViewKitTest=true"` 6m23s 成功，159 项中 20 执行、139 复用，含 R8、资源压缩、lintVital 与正式签名。
- 候选 `tmp/review-apks/SleepDown-personalize-handoff-gold-20261023.apk`（主目录），7,607,847 字节，SHA-256 `159dc80bb6c5d5f151d547ba32d44c22a3605ef81afcdf8fa535a7815da38ac3`，`1.2.7.1` / 35，证书 `0f2b50df…`。

## 边界

- 没有设备，三项均未实机确认；「上滑」目前没有对应的收起手势，若需要上滑直接反向，需要单独加一个拖拽识别。
