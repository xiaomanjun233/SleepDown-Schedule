# 参与 SleepDown 开发

`main` 是唯一持续开发基线。欢迎直接提交小型修复；涉及数据格式、系统权限或大幅交互变化时，建议先在 Issue 中说明场景和方案。没有 Issue 编号也可以提交 PR。

## 外部贡献

1. 阅读 [许可证](LICENSE.md) 和 [开发指南](AGENTS.md)，然后 Fork 仓库。
2. 从最新上游 `main` 创建自己的短期分支。分支名和提交标题只需表达改动含义，没有强制前缀。
3. 每个 PR 围绕一个问题，目标分支选上游 `main`；可先开 Draft 分享进展。
4. 按 PR 模板说明原来的问题、修改后的行为和实际验证结果。截图或录屏只在能说明界面变化时提供，并去除个人信息。
5. 根据审查意见更新同一个 PR，确认讨论已解决后由维护者合并。PR 合并不等于立即发布。

```bash
git clone https://github.com/<你的用户名>/SleepDown-Schedule.git
cd SleepDown-Schedule
git remote add upstream https://github.com/xiaomanjun233/SleepDown-Schedule.git
git fetch upstream main
git switch -c fix/my-change upstream/main
```

使用 AI 辅助贡献时，由提交者核对修改和验证结果；不要把生成内容或未运行的测试当作已验证事实。

## 维护者协作

维护者继续使用现有仓库权限和主分支工作流；外部贡献指引不要求维护者改用 Fork。普通协作仍推荐短期分支和 PR，管理员保留主分支操作权限，不要求自己批准自己的 PR，也不增加发布审批环节。已有 Pages 发布工作流保持原样。

`main` 的保护规则要求普通 PR 至少一次批准，新提交使旧批准失效，并要求解决审查讨论。代码所有者负责对应路径的审查；管理员不强制受这组规则约束。禁止强推和删除主分支的保护项继续保留。

CI 先作为可见的检查结果运行。只有维护者确认检查稳定、Fork 可运行、纯文档改动能正常跳过构建后，再决定是否将 `PR checks` 设为必需检查；不会因为新增一个尚未验证的工作流阻断日常协作。

## 本地构建与验证

使用 JDK 17、Android SDK（版本以 `app/build.gradle.kts` 为准）及仓库 Gradle Wrapper。按 [README 的 Miuix 步骤](README.md#本地构建的-miuix-依赖) 克隆 `v0.9.3` 并顺序应用三份补丁。使用 `-Psleepdown.miuixSourcePath=<绝对路径>` 覆盖本机依赖路径，不要提交个人路径。

| 改动 | 验证 |
| --- | --- |
| Kotlin / Compose | `compileGithubReleaseKotlin` |
| 资源、Manifest、依赖或打包 | 外部贡献者运行 `assembleGithubDebug`；维护者按正式流程验证签名 Release |
| 解析、日期、数据库、状态机 | 另运行相关定向单元测试或给出真实场景证据 |
| 文档 | 检查内容、链接和 diff |

例如，在 PowerShell 中执行：

```powershell
.\gradlew.bat compileGithubReleaseKotlin "-Psleepdown.miuixSourcePath=<绝对路径>" --console=plain --no-parallel --max-workers=2
.\gradlew.bat testGithubDebugUnitTest --tests '*feature.agent.*' "-Psleepdown.miuixSourcePath=<绝对路径>" --console=plain --no-parallel --max-workers=2
```

第二条仅用于 Agent 相关改动，其他模块选择对应测试。无法验证的项目如实说明原因。Fork 的 CI 不需要正式签名、模型 Key 或任何维护者凭据，也不打包发布版。

## 提交范围与 CI

- 保留数据库、备份、导入、Widget 和通知的兼容；数据变更需说明迁移与回归验证。
- 不提交密钥、签名文件、个人数据库、设备日志、构建产物或缓存。`SleepDown-Server/` 是私有后端，不属于公开贡献范围。
- 保留第三方代码来源和许可证，依赖补丁见 [patches/README.md](patches/README.md)。
- PR 工作流检查 diff 和禁止提交的路径，Android 相关改动编译 Kotlin；文档改动跳过 Android 构建。该检查不替代模块测试和设备验收。
- 首次 Fork 贡献可能需要维护者在 GitHub 批准运行工作流。使用 `pull_request` 和只读权限，不向贡献者代码提供仓库密钥。

维护者的提交、分支收尾与发布流程见 [开发工作流](docs/DEVELOPMENT_WORKFLOW.md)。
