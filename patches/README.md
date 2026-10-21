# 第三方补丁

## Miuix

上游：[compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)，标签 `v0.9.3`（`c36fab7`），Apache-2.0。

`miuix-0.9.3-sleepdown.patch` 是唯一完整补丁，直接应用到干净的官方标签。它覆盖弹层生命周期、页面与弹层采样分离、Popup 锚点动画与滑动选择、级联菜单、可选原生材质/条目插槽、内联 BottomSheet 与同级材质节点。未使用可选接口时保留原有默认行为。

旧版本的增量补丁已合并，不要在已经打过旧补丁的目录重复应用。2026-10-13 验证发现旧链的第五份补丁依赖未记录的中间版本，因此从现有实际使用的 20 个 UI/preference 源码文件重新导出相对官方标签的完整差异。仅包含运行时源码，不含本地 IDE、CI、构建产物或无关实验。

构建命令与 `sleepdown.miuixSourcePath` 配置见根目录 README。后续修改先确认本地源码与补丁一致，再更新完整补丁；在全新标签副本中执行 `git apply --check`、应用并逐文件比较。不能只以开发者自己的依赖目录能编译作为交付证据。

补丁中的空白上下文行是 unified diff 格式的一部分。源码与文档使用 `git diff --check`；补丁使用 `git apply --check`，不要删除其必要的上下文空格。

## Kyant Backdrop

`kyant-backdrop-2.0.0-sleepdown.patch` 记录已纳入 `third-party/kyant-backdrop` 的差异，不要重复应用到该目录。来源与变更说明见该目录的说明文件和 `THIRD_PARTY_NOTICES.md`。

2026-10-07 从确切的 `bebb11a` 上游文件重新导出该补丁，补齐此前已纳入源码的录制、密度和轮廓缓存差异，以及本次 Popup 坐标补偿和共享壁纸采样复用。源码目录仍是构建事实来源。
