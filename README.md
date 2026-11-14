# SleepDown 课程表 · 液态玻璃和凝光视效

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/version_welcome_mascot.webp" width="190" alt="SleepDown 2.0 欢迎动画中的看板娘" />
</p>

SleepDown 是一款面向手机和平板的 Android 课程表。导入整学期的课程后，可以按日或按周查看，安排调休和特殊课程，并在上课前收到提醒。基本课表无需注册，数据默认保存在设备上。

[下载 2.0（GitHub）](https://github.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v2.0) · [Gitee 发行版](https://gitee.com/xiaomanjun233/SleepDown-Schedule/releases) · [官网](https://sleepdownschedule.cn) · [2.0 更新日志](release-notes/v2.0.md)

最低支持 Android 8.0。项目 QQ 交流群为 `1108032519`。

## 2.0 的界面

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/about_sleepdown_hero.webp" width="100%" alt="SleepDown 2.0 关于页使用的凝光背景" />
</p>

- **凝光视效和液态玻璃**。Android 14 及以上的各品牌设备都可使用凝光，底栏、课程卡片和控件会随背景呈现不同的材质效果。可在「通用设置 → 视效模式」切换，较旧系统自动使用液态玻璃。
- **重构二级界面**。课表设置、课程管理、AI 设置、通知、小组件与备份恢复统一使用 COUI 顶栏、分组列表和操作按钮。平板与横屏按窗口宽度选择单栏或双栏布局。
- **壁纸与动效**。横竖屏可分别取景，首页根据壁纸文字区域确定明暗配色。欢迎动画可从「软件更新 → 查看亮点功能」重播；GitHub 版在兼容设备上支持金标联盟无缝动画。

## 日常功能

| 功能 | 用法 |
| --- | --- |
| 课表与学期 | 管理多张课表，在日视图和周视图间切换。开启新学期时可沿用原设置和作息，之前的课程留在原课表。 |
| 作息与特殊课程 | 设置常规作息与按日期生效的方案，支持调休、停课和补课。午休等固定时段可单独安排，临时课时及自定义时间按实际时刻显示。 |
| 导入与教务刷新 | 从首页加号进入。支持手动录入、课表口令、JSON、ICS 和教务导入。教务适配复用[拾光仓库](https://github.com/xingheyuzhuan/shiguang_warehouse)，可用学校以应用内列表为准。已支持的教务入口可定期检查变更。 |
| 分享 | 生成 15 分钟内有效的短口令，接收方粘贴后预览。也可直接分享 JSON 或 ICS 文件。 |
| AI 今日助手 | 查询课表与作息，协助调整课程或设置。可用文本、图片和 PDF 整理课表，处理过程中可追加要求。课表导入或修改需经预览确认。 |
| 提醒与小组件 | 支持课前提醒、课程勿扰和实时活动。桌面组件可调整背景与排版。流体云、荣耀 YOYO 建议及小米超级岛在 GitHub 版按设备、系统和权限提供。 |
| 备份与恢复 | 可手动导出本地备份，也可配置自己的 WebDAV。恢复前可预览要应用的内容。 |

AI 属于可选功能，具体模型和文件能力以所选服务为准。应用内「设置 → 问题反馈」可提交问题或建议，并在详情查看处理进度。经管理员发布后可公开原文，选填联系方式仅本人和管理员可见。

### 从 1.1.5 迁移

旧版包名为 `com.example.courseschedule`，请先在旧版导出 `.sleepdown` 备份，再安装 `com.xiaomanjun.sleepdownschedule` 并恢复。核对课程、作息和提醒后再处理旧应用。详见[旧包迁移说明](docs/migration/1_2_0_PACKAGE_MIGRATION.md)。

## 开发协作

项目以 `main` 为持续开发基线。发布记录和应用版本对应关系见[版本基线](docs/RELEASE_MODEL.md)。参与前阅读[贡献指南](CONTRIBUTING.md)、[开发工作流](docs/DEVELOPMENT_WORKFLOW.md)和[项目说明](AGENTS.md)。目录与模块入口见[架构文档](docs/architecture/PROJECT_STRUCTURE.md)。

### 本地构建的 Miuix 依赖

使用 JDK 17、项目 Gradle Wrapper 和 `app/build.gradle.kts` 指定的 Android SDK。工程通过 composite build 使用 Miuix `v0.9.3` 与本仓库补丁。下面的 PowerShell 命令在仓库根目录执行，依赖放在全新的相邻目录。

```powershell
git clone --branch v0.9.3 --depth 1 https://github.com/compose-miuix-ui/miuix.git ../miuix-reference
$miuixPatchRoot = (Resolve-Path ./patches).Path
git -C ../miuix-reference apply "$miuixPatchRoot/miuix-0.9.3-sleepdown.patch"
$miuixSourceRoot = (Resolve-Path ../miuix-reference).Path
.\gradlew.bat assembleGithubDebug "-Psleepdown.miuixSourcePath=$miuixSourceRoot"
```

补丁说明见 [patches/README.md](patches/README.md)。构建 Release 需自行配置签名。第三方代码和素材来源见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可证

本项目采用 [SleepDown 署名非商业、源码可见许可 1.1](LICENSE.md)。允许个人、非商业地查看、克隆、编译和修改源码。对外分发修改版时须同步公开足以检查和重建的对应源码，在发布页面和 App 内显著标明原作者 `xiaomanjun233`、原项目链接、主要修改内容和「非官方修改版」。商业使用须另行取得书面授权。第三方代码与资源遵循各自的许可证。
