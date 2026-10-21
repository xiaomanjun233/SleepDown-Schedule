# main / develop 流程迁移审查

核对时间：2026-10-21，Asia/Shanghai。用户明确要求恢复 main 稳定发布、develop 日常集成，并保留历史，不强推、不重置、不自动合并。本文记录本次事实；长期操作统一见 [开发工作流](../DEVELOPMENT_WORKFLOW.md)。

## 提交与任务盘点

| 对象 | 实际状态 |
| --- | --- |
| main | `9b6acbb23512e48d0af24afec0813ecadf27573e`，包含正式发布源码 `bdc380ee` |
| develop | `848ac5a40b068d84c3c4150c4abac57386b5a0d5`，2026-08-31 的文档提交 |
| 祖先关系 | `git rev-list --left-right --count origin/develop...origin/main` 为 `0 610`；merge-base 等于 develop；无独有 develop 提交 |
| #74 | 已合并到 main，merge `a320f6a8` |
| #78 | 已合并到 main，merge `9b6acbb2`；上一轮用户“先合并”仅用于此 PR |
| #79 | 唯一开放 PR，目标 main，核对时 head `bc69a3ef`；29 个提交、179 个文件；完整 CI [39549747482](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/39549747482) 成功；续作仍在原分支，未关闭、重建或合并 |
| 活动工作树 | 主目录学校适配分支与其未提交内容保持原状；架构续作及本流程变更各自独立 worktree，未操作其他工作树、stash |

GitHub 元数据通过内置 GitHub plugin 读取；Git 只用于 fetch、祖先检查和本地/已授权远端版本管理。没有使用 GitHub CLI。

## 同步方案与实际边界

develop 已完全包含在 main 历史中，因此在独立 `codex/develop-workflow` 分支从 main 准备同步，等价于从 develop 对 main 执行快进，不产生额外冲突。该分支只在 main 之上追加流程规范与 PR 触发事件调整，目标 PR 为 develop。同步 PR 的大 diff 是 610 个已在 main 的历史提交，新增流程改动应以 `origin/main...HEAD` 单独评审；完整 PR diff 和 CI 仍按 develop 基线核对。

本轮没有更新远端 develop/main。合并须另有用户授权，建议 **Create a merge commit**，保留 main 全部祖先；不要 squash/rebase 同步 PR。获准合并后 fetch 并核对 main 是 develop 的祖先，再把 #79 的 base 改为 develop，核对 diff 与 CI。#79 当前无需重写 head 或重新开 PR。待同步合并前，禁止从仍落后的 develop 开始新的普通 Android 任务；现有 #79 作为流程切换前任务续作。

## 保护规则

分支接口确认 main/develop 的 `protected=true`，简要响应列出的 required-status contexts 为空；`/rulesets` 返回空数组。两分支的完整 `/protection` 均返回 `403 Resource not accessible by integration`，因此不能据简要字段断言审批数、推送限制、管理员豁免或必需检查完整配置。本轮未修改保护。

建议维护者在有管理权限的界面核对：main/develop 禁止强推和删除、通过 PR 集成；普通 PR 运行 `PR checks` 汇总检查（文档 PR 允许 Android job 跳过）；正式发布需额外核对 APK/签名/实机证据。不要把可跳过的 Android job 单独设为必需，避免纯文档 PR 永久等待。审批数量按实际维护人数决定，不制造维护者无法完成的自审批要求。保护调整不在本轮实际执行范围内。

工作流原本对所有 PR 目标生效，已覆盖 develop/release/hotfix；新增 `edited` 事件，使调整 PR base 后重新计算完整 diff。保留 Draft 门控、只读权限和整个 PR 的 Android 范围判定，没有新增自动合并、推送、发布或标签操作。

实际检查发现：旧 develop 与 main 的 diff 含拾光及 Kyant 已进入 main 的空白问题，直接运行原 scope 的 `git diff --check` 会失败。本轮不批量改写第三方源码；仅在目标为 develop、目标 base 已包含于受信任 origin/main、该 main 又已包含于 head 的情况下，以 main 检查新增内容的空白。私有路径和 Android 范围仍按完整 PR diff 检查。分叉或缺少可信引用时沿用原检查。真实临时 Git 仓库的回归验证新加空白仍会失败、原有空白不反复阻止同步、分叉/缺失引用不绕过检查；共 2 项通过。

## AGENTS.md 的公开状态

`git ls-files --error-unmatch AGENTS.md` 确认它已公开跟踪。该文件是人和多种助手共用的长期入口，建议继续公开维护项目规则；本轮更新其中分支政策，没有 untrack、删除或清理历史。本机绝对路径等环境细节如需移出，可在后续明确任务中提取到未跟踪本地配置，公共指南只保留通用命令及配置入口；不通过改写历史实现。历史报告和发布证据继续保留。

## 验证与后续

本轮检查提交祖先、完整变更路径、文档链接、diff 空白、PR scope 并核对工作流事件；完整历史范围的 1105 个新增/修改路径未发现被 scope 禁止的路径。新增变更未修改 Android 源码、版本、数据库或协议，本地不重复 Android 构建；同步 PR 包含既有 Android 历史，云端按整个 PR 运行编译、定向测试和 Debug 打包。实际云端结果以该 PR 当前 head 为准，不挪用 #79 结果。

发布顺序：develop 集成 → release/* 验证 → PR main → 获准合并后核对实际源码 SHA、versionName/versionCode、applicationId、渠道、签名和 APK → 按独立授权创建 Tag/Release → main 回流 develop。紧急修复从 main 建立 hotfix/*，同样回流 develop。没有本轮正式版本变更、APK 安装或发布。
