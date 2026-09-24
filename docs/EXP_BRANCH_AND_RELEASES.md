# 普通版实验功能、版本与发布

## 产品基线

`main` 是唯一持续维护的应用基线。原 `exp` 分支及 `-expN` 标签保留历史记录，暂不继续开发或发布独立实验版。厂商课程通知能力作为“实验功能”进入普通版，不建立第二套应用版本、更新通道或数据库。公开 GitHub 版启用，商店版关闭。

普通版继续保持相同的应用包名、Room 数据库、备份协议和发布签名。不能通过清库、换包名或换签名处理兼容问题。历史实验包可按 Android 版本号和签名规则覆盖安装普通包，应用内更新只提供正式版与 Beta 版，不推荐历史 `-expN` 包。

## 实验功能边界

- `feature/experimental/` 集中维护厂商模式选择、小米通知扩展与设置接入；`feature/coloros/` 和 `coloros-wakeup-proxy/` 维护 ColorOS/荣耀课程组件及协议。公共通知和设置代码只保留少量调用钩子。
- `BuildConfig.SLEEPDOWN_EXPERIMENTAL_FEATURES` 在 `github` 渠道为 `true`，在 `store` 渠道为 `false`。Shizuku 依赖、权限、Provider 与实现只进入 `app/src/github/`；商店渠道使用空桥接实现。ColorOS 课程导出 Provider 也只在 GitHub Manifest 注册。
- OPPO/一加/realme：普通通知、实时活动、流体云、流体云+实时活动。荣耀：普通通知、实时活动、YOYO建议+实时活动。小米/Redmi/POCO：普通通知、实时活动、超级岛。用户主动选择通知样式，系统专属配置说明按设备分别展示。
- 仅流体云模式关闭 SleepDown 自身课程实时活动；组合模式和荣耀 YOYO 建议同时保留实时活动。组件只提供读取入口，不维护第二份课表；发现官方 WakeUp 课程表占用相同包名时提示冲突，不自动卸载或覆盖。
- 小米超级岛采用 [Nexio 课程表](https://github.com/HaoZai000/NexioSchedule) 的课程通知结构：独立的高重要级别通道直接发送焦点通知，大岛固定使用[小米官方模板 2](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2143)，左侧课程名、右侧地点或“已上课”；小岛显示应用图标，[展开态模板 9](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2142)显示课程详情、课前原生倒计时和“查看课表”按钮。岛通知不经实时活动前台服务重发，课程开始和结束由既有闹钟更新。Shizuku 未就绪时直接发送；授权后，GitHub 版可在发送前短暂调整小米服务联网规则，随后恢复，并记录中断状态供下次启动恢复。此能力影响系统服务，需要小米真机逐项验收。
- 商店版制作时可移除隔离目录、组件模块、GitHub Manifest 入口和公共调用钩子；不能直接删除当前已发布用户数据或迁移逻辑。

## 版本与更新

当前应用版本以 `app/build.gradle.kts` 中 `sleepDownVersionName` 和 `versionCode` 为准。正式版使用 `MAJOR.MINOR.PATCH`，普通 Beta 使用 `MAJOR.MINOR.PATCH_betaN`；当前开发基线为 `1.2.6_beta10` / `versionCode 33`，转正式版时仍使用 `1.2.6`。不再从普通版生成 `-expN` 版本名或单独实验 APK。

普通版“接收 Beta 版更新”开关沿用既有行为。正式版只接收非预发布，开启 Beta 后接收普通预发布与正式版；两者均排除历史 `-expN` 标签。同一 Release 若同时有主应用与课程组件，应用更新只选主应用 APK，文件名含 `coloros-course-component` 或 `wakeup-proxy` 的附件不能作为主应用安装。

课程组件单独使用 `com.suda.yzune.wakeupschedule` 包，当前构建版本为 `6.0.18` / `versionCode 258`，最低兼容版本仍为 `6.0.17` / `257`。应用内“安装课程组件”查找 Gitee Release 附件，下载后核对包名、签名和版本，再决定是否进入安装；组件接口或最低要求变化时，主应用与契约测试一并更新。公开发布主应用与组件时分别核对版本、证书和下载附件，不覆盖旧标签或旧附件。

## 验证与发布

普通 GitHub 版使用 `:app:assembleGithubRelease` 构建；需要验证商店隔离时使用 `:app:assembleStoreRelease`。厂商实验功能至少检查模式可见性、组件读取、通知、Shizuku 授权与恢复路径，以及两个渠道的合并 Manifest。构建和单元测试不能代替 OPPO、荣耀和小米设备上的通知与后台行为验收。

每次 Beta 发布按全局指南更新应用内日志和发布页，保留本轮各 Beta 的独立日志；正式版再归并。远端标签、Release 和附件须在用户授权范围内操作。历史 `exp` 分支及标签不重写、不强推。
