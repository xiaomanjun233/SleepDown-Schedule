# 1.2.7 修复版更新识别

## 用户目标与基线

用户反馈昨晚发布的 `1.2.7-r1` 无法被 `1.2.7` 的应用内更新发现，要求解决；随后明确选择兼容补发版本名 `1.2.7.1`，并要求撤下 r1 发布。

本次继续处理 r1 发布反馈，复用 `codex/nexio-glass-parity`。开始时工作树干净，HEAD 为 `add3ed64`，比远端 #78 head `746b0ac2` 多一笔既有应用内日志补全提交。GitHub 插件核对 #78 仍为面向 `main` 的 Draft，main 仍为 `a320f6a8`。本次不处理 main 回流。

## 根因与修复

- 调查时 GitHub 与实际更新来源 Gitee 都存在 `v1.2.7-r1` 正式 Release，`prerelease=false`，主应用 APK 附件可由元数据找到。
- `v1.2.7` 的 `AppUpdate.kt` 仅比较数字版本、预发布阶段与阶段序号，忽略 `-rN`；`1.2.7` 与 `1.2.7-r1` 比较相等。提高 Android versionCode 不会修复该字符串比较。
- `ParsedVersion` 新增修订号，比较顺序为数字版本 → 阶段 → 阶段序号 → 修订号。保留 Beta、正式版、历史 exp 过滤与忽略 `+metadata` 的规则。修复及测试为提交 `5d123ac4`；版本与日志为 `bdc380ee`，均已推送双平台。
- 按用户选择准备 `1.2.7.1` / 35；第四段数字可由已发布旧客户端识别，同时让已装 r1 的用户正常升级。没有把旧 APK 改名充当新包。
- 增加应用内日志、日期及 `release-notes/1.2.7.1.md`，保留 r1 完整修复记录。课程组件未改动。

## 验证

- `:app:testGithubDebugUnitTest --tests com.xiaomanjun.sleepdownschedule.feature.update.GiteeAppUpdaterTest --console=plain --no-parallel --max-workers=2` 通过，3m01s；12 项、0 失败/错误/跳过。覆盖修订数字排序、正式/Beta 顺序、阶段序号优先于修订号、忽略构建元数据、两种渠道与列表正反顺序。
- 从 `v1.2.7:app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/update/AppUpdate.kt` 原样提取比较函数与 `ParsedVersion`，用本机缓存的 Kotlin 编译器运行 8 项兼容场景：旧比较器对 `1.2.7 → 1.2.7-r1` 返回 false，对 `1.2.7`、`1.2.7-r1`、`1.2.7_beta5 → 1.2.7.1` 均返回 true；安装 1.2.7.1 后不会继续提示自身或回退到旧版，后续 1.2.8 仍可更新。脚本在忽略目录 `tmp/Verify-LegacyUpdate.ps1`，生成源与 class 也只在 tmp。
- 完整 `:app:assembleGithubRelease --console=plain --no-parallel --max-workers=2` 通过，3m58s；159 项中 32 执行、127 复用，包含 R8、资源压缩、lintVital 和正式签名，未使用跳过资源压缩参数。
- `aapt2 dump badging` 与构建元数据均为 `com.xiaomanjun.sleepdownschedule` / `1.2.7.1` / `35`；`apksigner verify --verbose --print-certs` 通过，v2 签名有效，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与历史发布一致。
- 本机交付副本 `tmp/review-apks/SleepDown-1.2.7.1-20261020.apk`，7,591,259 字节，SHA-256 `3c920a97b319567bd367a482ccd493f55d59aa8c3dfe54b5dcd49c153b83fc8f`；与构建输出摘要相同。与 r1 包大小恰好相同，摘要及 Manifest 不同，确为新构建。
- 未安装、未启动应用、未执行自动 UI 或性能测试。

## r1 下架

用户于本会话明确同意“允许，使用现有脚本连接撤下 r1”和“允许，使用现有脚本双平台补发 1.2.7.1”，授权本次插件能力缺口的例外。GitHub 插件缺少 Release 写入工具，网页管理两次超时；本次沿用昨晚的本机发布连接，凭据仅在进程内使用，没有打印、保存或提交。

- GitHub Release `417503498` 已转为草稿，插件回读确认 `draft=true`，原有两份 APK 保留。
- Gitee Release `1219995` 已删除，按 ID 查询返回 404，按标签查询返回 HTTP 200 / JSON null，匿名发布列表不再包含 `v1.2.7-r1`。第一次脚本把 200/null 当成失败，核对真实状态后修正核验；没有再次删除其他对象。
- GitHub 匿名 Release API 查询返回 403，未据此宣称匿名接口验证通过；下架以插件回读的草稿状态为证。Gitee 的匿名更新列表已核验。
- 双平台 `v1.2.7-r1` Git 标签仍指向源码 `fa93ae659b44fa25d79d81134819821e24084262`；Gitee 原注释标签对象 `765e367a…` 保留，剥离后源码不变。源码和历史没有删除。

## 1.2.7.1 正式补发

- 发布源码和双平台新标签 `v1.2.7.1` 均为 `bdc380ee6796de6c633fe2688120a77c9fe8356f`，即已验证候选；未重新打包或替换产物。
- [Gitee 正式发布](https://gitee.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.7.1)：Release `1220655`，2026-10-20 10:11:30（Asia/Shanghai），`prerelease=false`。
- [GitHub 正式发布](https://github.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.7.1)：Release `417930773`，2026-10-20 10:11:57（Asia/Shanghai），`draft=false`、`prerelease=false`；插件回读 `releases/latest` 确认为本版。
- 主应用 `SleepDown-Schedule_v1.2.7.1.apk` 为上方已核验的 7,591,259 字节 / `3c920a97…`。组件仍为 3,577,334 字节，SHA-256 `5f5b4c4716c2218ce5277fe336b221cd6f85b85d04dc8ee9f72ecbb8d78ac593`。四份公开附件均流式回读，大小与 SHA-256 与本地相同；两平台发布说明均与 `release-notes/1.2.7.1.md` 一致（统一 LF 比较）。
- 上传顺序为 GitHub 草稿核验 → Gitee 正式发布与公开回读 → GitHub 公开并设为最新正式版，再公开回读。脚本及产物清单仅在忽略目录 `tmp/`，不入库。
- 额外从 v1.2.7 原样提取 `selectRelease`，与原比较函数一起运行真实 Gitee 公共更新列表：`includeBeta=false` 与 `true` 都选中 `v1.2.7.1`；`1.2.7`、`1.2.7-r1` 均满足更新条件，`1.2.7.1` 自身不再提示。初版临时脚本未展开 PowerShell 的返回数组，修正核验脚本后通过；未因此修改应用代码。
- #78 仍为 Draft、未合并；main 仍为 `a320f6a8`，是发布源码的祖先，发布提交比其多 106 个提交。主分支回流仍待授权，实机安装和 UI 验收未执行。自动检查仍遵循既有每日一次规则，用户可在设置中手动检查更新。
