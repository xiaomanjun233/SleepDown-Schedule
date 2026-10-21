# 2026-10-18 工作目录与正式版基线整理

- GitHub plugin 核对最新正式 Release 为 `v1.2.7`，标签指向 `cfe8776f8c2811e54cc4ff23445cfd67f2b6af9e`，版本 `1.2.7` / `35`。
- 拉取 `origin/main` 后仍为 `a320f6a8`，是正式版源码的祖先，相差 64 个提交。从该 main 建立 `codex/nexio-glass-parity`，仅在任务分支快进到正式版源码；远端 main、发布标签和版本号均未改写。后续 PR 因而也携带这 64 个已发布提交，供 main 收口。
- 原来的 README、版本基线修改和 10 月 15 日正式版记录保留为独立文档提交。
- 任务外 `AgentImportWorkspace.kt` 的 7 行未提交修改保存在 Git stash `workspace-before-nexio-glass-2026-10-18`，未混入正式版或玻璃修改。原 stash 也保留了整理前的两份文档修改。可通过 `git stash list` 按此名称定位，再只恢复需要的文件。
- 未跟踪的 `.codex-remote-attachments/` 和 `edu-adapters/` 完整移到被本地忽略的 `tmp/workspace-preserved-20261018/` 下，保留同名目录；没有删除、上传或提交其中资料。
- 其他 worktree、独有历史分支、私有后端、现有构建缓存和任务外素材保留原处；本轮没有获得这些活动目录可安全删除的证据。

本记录仅说明本轮整理，不把任务分支快进视为 main 已合并，也不把已有构建产物视为新代码的验证结果。
