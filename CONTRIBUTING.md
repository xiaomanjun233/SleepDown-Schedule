# 参与 SleepDown 开发

欢迎提交问题反馈、文档和代码修复。贡献前阅读 [许可证](LICENSE.md)与 [AGENTS.md](AGENTS.md)中的项目约定。涉及数据格式、系统权限或大幅交互变化时，建议先在 Issue 说明场景；没有 Issue 编号也可以提交 PR。

`develop` 是日常开发与集成入口，普通 PR 面向上游 `develop`；`main` 是稳定版本与正式发布基线。开始前查看 [版本基线](docs/RELEASE_MODEL.md)与 [当前交接](docs/AI_HANDOFF.md)，不要把 Release 标签自动当作 main 已更新的证明。

## 外部贡献：从 Fork 到合并

1. Fork 仓库，从最新上游 `develop` 创建短期分支；若依赖未回流的发布代码或其他 PR，在描述中明确说明。
2. 每个 PR 围绕一个目标。提交标题和分支名表达含义即可，不强制前缀、格式或 Issue 编号。
3. 完成修改，按风险执行下表中的最小验证，只提交相关文件；不要修改正式版本号、发布日志或第三方依赖来凑过检查，除非它们属于任务。
4. 可以先创建 Draft 分享进度。实现和必要验证准备好后转为 Ready for review，按[模板](.github/pull_request_template.md)说明最终行为、验证与未完成项。
5. 审查反馈继续更新原 PR，解决讨论；由维护者评审并合并。合并方式和收尾见 [开发工作流](docs/DEVELOPMENT_WORKFLOW.md#5-评审与合并)。PR 合并不等于立即发布。

```bash
git clone https://github.com/<你的用户名>/SleepDown-Schedule.git
cd SleepDown-Schedule
git remote add upstream https://github.com/xiaomanjun233/SleepDown-Schedule.git
git fetch --no-tags upstream develop
git switch -c fix/my-change upstream/develop
```

使用 AI 辅助贡献时，由提交者核对代码、来源和验证结果。只写实际执行的检查，界面截图或录屏按需提供并去除个人信息。需要换助手接续时，保留任务分支与 PR，按 [交接规范](docs/DEVELOPMENT_WORKFLOW.md#7-更换助手与交接维护)记录，不要求下一位从头实现或重跑无关测试。

## 本地构建与验证

使用 JDK 17、仓库 Gradle Wrapper 与 Android SDK，SDK 版本读取 `app/build.gradle.kts`。Miuix 使用官方 `v0.9.3`，在干净源码上应用**唯一完整补丁** `patches/miuix-0.9.3-sleepdown.patch`；不要重复应用历史增量补丁，也不要把已纳入源码的 Kyant 补丁再次打到 `third-party/kyant-backdrop/`。

完整准备命令见 [README 的 Miuix 步骤](README.md#本地构建的-miuix-依赖)，补丁基线与验证见 [patches/README.md](patches/README.md)。使用 `-Psleepdown.miuixSourcePath=<绝对路径>` 覆盖维护者本机依赖路径，无需修改或提交个人路径。

| 改动 | 本地验证 |
| --- | --- |
| Kotlin / Compose | `compileGithubReleaseKotlin` |
| 资源、Manifest、依赖或打包 | 外部贡献者运行 `assembleGithubDebug`；维护者按正式流程验证签名 Release |
| 解析、日期、数据库、状态机 | 按实际风险另选相关定向测试或真实场景证据 |
| 文档 | 检查内容、链接和 diff，无需 Android 构建 |

例如，在 PowerShell 中执行编译；需要测试时将占位符替换为相关类或方法，不默认运行所有测试：

```powershell
.\gradlew.bat compileGithubReleaseKotlin "-Psleepdown.miuixSourcePath=<绝对路径>" --console=plain --no-parallel --max-workers=2
.\gradlew.bat testGithubDebugUnitTest --tests '<相关测试类或方法>' "-Psleepdown.miuixSourcePath=<绝对路径>" --console=plain --no-parallel --max-workers=2
```

无法执行的验证说明原因和替代证据。Fork 的 PR CI 无需正式签名、模型 Key 或维护者凭据；其中的打包步骤为 Debug，不生成官方签名发布包。

本地保留现有 Maven 镜像设置。GitHub runner 使用 `-Psleepdown.useMavenMirrors=false` 访问官方仓库，具体参数以工作流为准；本地无需跟随修改。

## CI 与评审

- Draft PR 跳过自动检查，仍可能留下 skipped run；转为 Ready for review 开始检查，可评审 PR 后续推送、重开继续检查。
- 是否运行 Android 由整个 PR diff 决定。纯文档 PR 跳过 Android；Android PR 的最后一笔提交即使只改文档，仍按整个 PR 检查。
- Android 检查包含 Kotlin 编译、既有定向测试集合及 Debug 打包。具体入口见 [pr-checks.yml](.github/workflows/pr-checks.yml)，它不替代设备体验验收；skipped 也不代表测试通过。
- 首次 Fork 工作流可能需要维护者批准。工作流使用 `pull_request` 与只读权限，不向贡献者代码提供仓库密钥。
- 外部贡献由维护者审阅。批准数量、必需检查、代码所有者审查和管理员豁免以 GitHub 当前配置为准；文档不自动修改分支保护或增加维护者自审流程。

维护者沿用现有仓库权限，无需 Fork；普通工作使用短期分支和 PR。提交粒度、默认保留提交历史的合并方式、授权边界与合并后分支收尾统一见 [开发工作流](docs/DEVELOPMENT_WORKFLOW.md)，避免在多个入口维护不同规则。

## 提交范围

- 保持数据库、备份、导入、Widget 与通知的兼容；涉及数据变更时说明迁移和验证。
- 不提交密钥、签名文件、用户数据库、设备原始日志、构建产物或缓存。`SleepDown-Server/` 是私有后端，不属于公开贡献范围。
- 保留真实作者、第三方来源及许可证。提交前审阅 diff，用 `git add -- <本次文件>` 精确暂存。
- 修改构建、公共行为或协作方式时同步对应规范；单次调查与验收细节放报告，避免把临时结论写成全局限制。
