# 公共作息库来源与归并修复

日期：2026-10-04。沿用 `codex/issues-48-55-beta2`，版本仍为 `1.2.7_beta2` / 34。

## 根因

旧课表各自持有作息，抽取到公共库时只生成不同 ID，未记录来源；原去重仍比较名称，导致名称不同但内容相同的作息重复。保存与备份恢复仅按 ID 合并，无法归并已有重复项。详细设置的切换菜单仍读取各课表本地副本，与管理页的公共列表不一致。

## 处理

- 为公共作息增加可选来源（课表名、原作息名）、归并前名称和手动新建标记，不引用 Room ID。卡片保持三行紧凑排版，显示来源，点击来源可查看完整信息。
- 内容相同才合并：比较四段节次结构、实际开始 / 结束时间、有效分段起点以及增删节次使用的默认时长；名称、库 ID、来源和未启用分段的起点不参与比较。来源与原名称全部保留。
- 加载、首次抽取、保存和备份恢复复用同一归并规则。“当前”也按内容识别，重复应用不新增等价副本；编辑归并时保留本次指定的名称。
- 旧导入标记升级为版本 2。已有条目补齐可恢复的来源，不覆盖其编辑后的时间，不重建用户已删除的条目。旧版本无法恢复的来源显示“来源未记录”。
- 切换菜单和管理页读取同一公共库；不再展示各课表累积的重复副本。库中已删除但课表仍在使用的时间显示“课表保留”，不清除课表数据。
- 备份保留新增元数据；旧 JSON 缺省时按原格式读取。相同 ID、不同内容的恢复冲突保留两份。恢复后的课表 ID 可能复用，因此新旧备份都重新发现来源；旧备份没有公共库字段时仍保留本机库。

Room schema、课程节次、现有课表时间和已发布课表备份格式不变；不会将公共库编辑强制传播到其他课表的保留副本。

## 验证

作息迁移 / 归并、重复应用、ID 冲突及备份往返使用定向单元测试。Gradle 仅提供 Debug 单元测试任务；使用 `testGithubDebugUnitTest`，临时 init 脚本只编译本次相关测试，脚本不提交。

最终代码重新编译后测试通过，命令为 `testGithubDebugUnitTest --tests com.xiaomanjun.sleepdownschedule.domain.schedule.PeriodSchemeLibraryTest --tests com.xiaomanjun.sleepdownschedule.feature.backup.BackupCodecTest -I tmp/period-library-tests.init.gradle --console=plain --no-parallel --max-workers=1`，耗时 3m40s。

- `PeriodSchemeLibraryTest`：13 项通过。
- `BackupCodecTest`：28 项通过，包括来源元数据往返、旧字段缺省与无效数据拒绝。
- 合计 41 项，失败、错误和跳过均为 0。

Debug / Release 合并执行时出现 JVM 内存告警，已取消该轮，最终改为单线程、先验证再打包。

签名 Release 构建 `assembleGithubRelease --console=plain --no-parallel --max-workers=1` 成功，耗时 6m25s，包含 Kotlin 编译、R8、lintVital、资源压缩与签名打包。产物为 `app/build/outputs/apk/github/release/app-github-release.apk`，生成时间 2026-10-04 19:11:31，版本 `1.2.7_beta2` / 34，6,925,623 字节。

SHA-256：`A24796C17F26738FAE448174E45FA7F6BA9C37B5BAC7D5A3732EEE9577557387`。

安装前通过 SDK `5038` 确认 `192.168.1.10:42533` 的 PLJ110 在线，使用项目入口覆盖安装最终 APK，返回 `Success`。保留应用数据，按用户要求未自动启动、未做设备界面验收；来源迁移会在用户打开详细设置或管理页时执行。
