# 仓库、草稿 PR 与 Actions 整理

记录时间：2026-10-19，Asia/Shanghai。用户反馈 GitHub 持续发送 PR run 通知，本轮核对实际工作流、失败日志、发布历史与工作树后收口。

## 通知来源与修复

最近四次向草稿 PR #78 推送都运行了完整 `Pull request checks`，并在同一测试失败。最近一次为 [run 39189017994](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39189017994)：

- PR 范围与架构检查通过，Release Kotlin 编译通过。
- `Verify shared schemes, backups and reminder scheduling` 执行 623 项测试，其中 1 项失败：`AgentImportWorkspaceTest.overlappingAndGappedPeriodsAreRejectedBeforePublishingAStage`。
- 后续汇总检查因此失败；没有仍在运行、需要取消的旧任务。

`AgentImportWorkspace.applyPatch` 缺少发布暂存结果前的作息校验。最初整理工作目录时，这段已有的 7 行修改与其他未提交内容一起保存在 `workspace-before-nexio-glass-2026-10-18` stash。本轮只恢复该文件的原改动：解析作息后调用 `validateResolvedPeriodTimes`，在重叠或节次不连续时拒绝发布。stash 保留，未恢复其他任务外文件，未修改数据库或备份协议。

工作流调整：

- 草稿 PR 的 scope 和汇总 job 跳过，依赖 scope 的 Android job 随之跳过。
- 明确监听 `opened`、`synchronize`、`reopened`、`ready_for_review`。转为可评审时自动开始完整检查，可评审 PR 后续推送继续检查。
- 保留所有现有编译、定向测试、Debug 打包及并发取消规则，没有关闭 Actions，也没有修改 Pages 或个人邮件偏好。
- 这是随 #78 提交的工作流改动；`main` 的文件需合并后才更新。草稿推送仍可能显示一条跳过的 run 记录，不等同于删除 Actions 历史或关闭所有 GitHub 通知。

GitHub 官方说明：[PR 事件与 ready_for_review](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows)、[job 条件与跳过结果](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-jobs-with-conditions)。

## 已发布草稿收口

核对后关闭下列三个旧草稿，均未合并，分支和提交保留；原 PR 描述末尾补充发布覆盖证据与关闭原因。

| PR | 与 v1.2.7 的关系 |
| --- | --- |
| [#57 贡献流程](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/57) | `git cherry` 确认该分支 5 个提交均有补丁等价的已发布提交 |
| [#61 作息编辑一致性](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/61) | 原实现已通过 `2178952f` 回流，发布历史及既有课程数据安全报告均保留该功能 |
| [#65 公共作息引用](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/65) | 分支头 `8444dd01` 已是 v1.2.7 的祖先 |

当前仅保留 [#78 渲染与交互修复](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/78)，目标 `main`，仍为草稿，等待用户实机验收。

## 基线与保留内容

| 对象 | 核对结果 |
| --- | --- |
| `origin/main` | `a320f6a8c3f72dfcc91e80de3e4f224ec9a0fd66`，实际构建版本 `1.2.7_beta5` / 34 |
| 最新正式版 [v1.2.7](https://github.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.7) | `cfe8776f8c2811e54cc4ff23445cfd67f2b6af9e`，版本 `1.2.7` / 35 |
| 两者关系 | main 是正式版祖先，正式版比 main 多 64 个提交，没有 main 独有提交 |
| 本轮工作分支 | `codex/nexio-glass-parity`，已包含正式版基线与 #78 后续修复 |

未直接合并 #78 或改写 main、标签和发布，避免将待验收渲染与交互提前归入主分支。正式版回流 main 的欠账仍需在分支收尾时处理。

盘点时有 27 个远端分支、45 个本地分支、13 个工作树及 2 个 stash。历史分支本身不触发当前 `pull_request` 工作流；工作树还有其他任务使用，本轮未删除分支、工作树或 stash。私有后端、签名、用户数据及任务外素材未暂存、提交或上传。

## 验证范围

- `testGithubDebugUnitTest --tests 'com.xiaomanjun.sleepdownschedule.feature.agent.AgentImportWorkspaceTest.overlappingAndGappedPeriodsAreRejectedBeforePublishingAStage' --console=plain --no-parallel --max-workers=2` 通过，含编译耗时 6m02s，86 个任务中 20 个执行、66 个复用。JUnit XML 确认执行 1 项，失败、错误和跳过均为 0。该方法覆盖重叠、缺口拒绝及合法相邻作息；未新增测试或重跑 623 项套件。
- `git diff --check` 通过。
- 远端开放 PR 列表已核对，仅 #78；推送后的草稿检查结果记录在 #78 的 Checks 与描述中。
- 本轮为仓库与 CI 整理，没有重新打包或安装 APK；此前交付 APK 不包含这次恢复的 AI 暂存校验。
