# 2026-10-28：1.2.8 Beta1 基线收束

用户要求以 1.2.8 Beta1 为最新开发基线，整理本地、远端 PR，得到干净工作区，再分开建立时间显示和金标动画修复 PR。随后明确要求将“认定发布哪个 Release，就必须完成该版本所基于 PR 的审查和合并，不得堆积”写入 AGENTS.md；已在 `1c95479d` 落实，并由 #87 进入 main。

## 远端结果

| PR | 处理与依据 |
| --- | --- |
| #87 | 完成审查、实际回归修复及完整 CI 后以 merge commit `e72643f204a6b456adbbd93c5dc85dbadf6b1e5c` 合入 main，head 为 `1c95479d3d4542223f6d288a10513598600110f7` |
| #79、#86 | head 都是 #87 的祖先，GitHub 已确认 merged；原始提交完整保留 |
| #81 | head `5b62be48` 是已合并 #87 的祖先，关闭重复 PR，保留分支及历史 |
| #80 | develop 流程已经过时，关闭而不合并；main 继续作为唯一持续开发基线 |

本地 main 已快进至 `e72643f2`，核对 `v1.2.8_beta1` / `498d39cc` 是其祖先。版本、发布标签、公开附件及最新正式版渠道未改变，没有发布新包或安装。

## 审查与验证

首轮完整 CI 执行 946 项，发现 12 项失败。实际回归是稀疏作息缺少中间节点、但已知起止时间的课程无法导入；现保留已知源时钟及原节次，不补造中间铃声。其他断言对齐 Room 50 的源节次/显示投影分离、纯时钟无伪造节次及新 JSON 字段；仍覆盖非法日期、空课程、损坏阶段、重叠时间、缺少端点、调休周次和奇偶周边界。

五类定向回归共 69 项通过：PeriodSchemesTest 14、ScheduleAdjustmentsTest 19、AiImportCheckpointTest 26、AiImportConversationTest 4、WakeUpImportSessionTest 6。

最终 head 的 [完整 CI #68](https://github.com/xiaomanjun233/SleepDown-Schedule/actions/runs/40691906089) 成功，包含范围/架构检查、Release Kotlin 编译、工作流定向回归及 Debug 打包。实机观感与真实教务网络不由 CI 替代；本轮新增反馈在独立修复 PR 跟踪。

## 本地保全

根目录两份未提交文档先保存在具名 stash `preserved: main docs before 1.2.8 beta1 consolidation 2026-10-28`（`04a996d7`）。其中有效安装/ADB/构建规则已由 Beta1 分支承载，新 Release 收尾规则已进入 main；旧文档原样可恢复。

另对以下八个有未提交内容的旧工作树，按实际状态中的精确文件清单分别创建 `archived: beta1 consolidation 2026-10-28 <branch>` stash：gdcp-new-timetable、main-adjustment-badge、notification-weekjump-fix、sdwcvc-edu-adapter、home-material-corrections、sharedviewkit-ai-history、morph-current-state，以及 detached perf-before。保全后各树源码状态为空；未删除分支或工作树，未改写历史，未动忽略目录中的后端、素材、APK、缓存及原有三份 stash。

按名称查询 `git stash list` 后，可在对应旧树检查某份记录；不要整批 pop/drop。存档只用于恢复历史，不作为后续开发基线。
