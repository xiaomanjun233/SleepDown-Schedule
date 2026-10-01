# 第三方补丁

## Miuix

上游：[compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)，标签 `v0.9.3`（`c36fab7`），Apache-2.0。前三份补丁均以该标签为基线，文件范围互不重叠；第四份补丁在前三份之后应用：

1. `miuix-0.9.3-sleepdown.patch`：BottomSheet、Dialog、Dropdown、ListPopup、TopAppBar 及 Popup host 的表面修饰、内容裁切、居中和关闭生命周期接口。
2. `miuix-cascading-popup-surface.patch`：级联菜单的主/次表面、真实锚点及内容交接。
3. `miuix-scaffold-underlay.patch`：新增 `Scaffold.underlayModifier`，把页面、TopBar 等放入底层布局，Popup/Dialog host 后绘制为同级节点，避免 Backdrop 自采样。
4. `miuix-compact-dropdown.patch`：为列表菜单增加可选的条目文字样式、垂直内距和最小高度，默认值保留原排版。个性化面板借此将文字排布入口与菜单条目统一为其他选项的字号、字重和紧凑高度。

从全新官方源码按上述顺序 `git apply`，具体命令见根目录 README。旧补丁目录不要再次叠加新版完整补丁，应使用新的依赖目录。`sleepdown.miuixSourcePath` 指向打完补丁的源码根目录。

2026-09-10 重新导出已在本地使用的 UI/preference 源码差异，补齐 Scaffold 和菜单接口，修正旧级联补丁损坏的 hunk。没有改动本地 Miuix 运行时代码，也没有导出本地 IDE、CI 清理、构建产物或未使用的 blur 实验。公开补丁可通过普通 `git apply --check`，不需要 `--recount` 或忽略空白错误。

验证：在独立的官方 `v0.9.3` 工作树应用三份补丁后，16 个变更源码文件与本地实现逐文件一致（仅归一化 CRLF/LF），逆向检查也通过。使用命令行 `sleepdown.miuixSourcePath` 指向该独立目录，完成 `assembleGithubRelease --console=plain --no-parallel --max-workers=1`，耗时 8m56s，包含 R8、资源压缩、lintVital 与签名。这不是只验证当前 `miuix-local` 可以编译。

补丁中的空白上下文行以一个空格开头，这是 unified diff 格式的一部分；源码/文档使用 `git diff --check`，patch 文件使用正反向 `git apply --check`，不要为了消除普通文本的行尾空白提示破坏补丁上下文。

## Kyant Backdrop

`kyant-backdrop-2.0.0-sleepdown.patch` 记录已纳入 `third-party/kyant-backdrop` 的差异，不要重复应用到该目录。来源与变更说明见该目录的说明文件和 `THIRD_PARTY_NOTICES.md`。
