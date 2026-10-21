# 横屏导航、AI 附件与导入体验修订

本轮继续草稿 PR #78，回应横屏凝光滑选、AI 加号、口令导入交接、自动教学周、导入配色及教务搜索框入场反馈。未合并 main、未发布新版本。

## 改动与依据

### 横屏凝光滑选

设置页与课表页切换会改变导航明暗外观。原 Segment 以 dark 为 remember key，因而重建原生 View；导航锁还调用 `setEnabled(false)`。本地 COUI 完整实现确认该 setter 调用 `handleActionCancel`，`refresh` 则刷新颜色和描边而不重建选中指示器。

改为保留主题 Context 与 View，在外观变化时原地 refresh，再恢复无容器描边协议。对于抬手提交的导航，宿主负责触摸、键盘和无障碍锁定，避免导航期间通过禁用原生 View 中断正在收尾的弹簧；其他分段控件保留正常 disabled 语义。

### AI 加号与导入交接

- 附件入口移除原生 ShortcutMenu 分支，始终复用右侧模型选择器的 `SleepDownLiquidCascadingPopup` 与真实按钮宿主。加号作为 collapseContent 参与 Morph，源图标隐藏到退出完成再交回。
- 口令解析后等待原入口实际关闭，再显示预览，替换固定等待时间和直接替换正文的路径。
- 保存时保留原课表视觉状态；保存成功先关闭导入入口和预览，等待生命周期卸载，再释放到新课表并淡入，最后沿用原有导入后课表设置流程。失败保留预览并恢复原视觉状态。

### 自动教学周与导入默认值

- 详细设置与快速设置移除自动周次开关，当前周为只读。新建、启动旧课表、配置保存、导入和备份恢复完成后由 Repository 统一开启自动计算。
- 保留 Room/备份中的旧字段与格式，没有清库、删除历史字段或新增 schema。已有开学日期按日期计算；无日期时保留原周次并提示补充，不猜测开学日期，也不阻止保存其他设置。
- AI 不再提供关闭自动周次或直接写入手动周次的设置，当前周快照使用实际推算结果；修改教学周依据通过开学日期完成。
- 新导入使用 COLORFUL，课程着色 50%、液态模糊 35%（3.5/10）、普通模糊 35%（8.4/24）、折射 50%，字号与高度为自适应默认、圆角为 50%。沿用现有外观重置入口，教务自动刷新继续保留当前课表外观。
- 旧导入默认模糊为 18，而液态滑杆最大值为 10，显示时必然被限制成 100%；本轮按滑杆已有标记点设置，不再复用超出当前范围的旧半径。凝光独立偏好的默认值仍为着色 50%、模糊 50%，没有为了新导入覆盖用户的全局材质偏好。

### 教务搜索框入场

原生搜索框的透明度动画改用 ModulateAlpha，避免自动离屏层隔断 View 级模糊的窗口前序采样。底部渐变与搜索框共用入场进度；主题原地刷新，功能按钮文字仅在实际变化时更新，避免每个动画重组都重复调用原生 setter。

## 验证与交付

- 运行时代码提交：`b248b6b6`（导航动画）、`c823df8b`（AI Popup）、`ecd92d7f`（导入交接）、`85a45c24`（自动周次）、`0c0db4ba`（导入默认值）、`0f019f1d`（搜索框入场）。
- `assembleGithubRelease --console=plain --no-parallel --max-workers=2` 完整通过，5m12s，159 个任务中 19 个执行、140 个复用，保留 R8、资源压缩、lintVital 和正式签名。首次编译的类型导入与局部函数作用域问题已修复。
- APK：`tmp/review-apks/SleepDown-1.2.7-navigation-import-defaults-20261019.apk`，7,574,871 字节，SHA-256 `501c345545aa5d8d3ea4ff327d6a853ac66e29e51d4fdaef259b18619157c790`，与标准输出 APK 摘要一致。
- Manifest 为 `com.xiaomanjun.sleepdownschedule`，`1.2.7` / 35。`apksigner verify --verbose --print-certs` 通过，v2 签名有效，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与既有正式签名一致。
- `testGithubDebugUnitTest --tests 'com.xiaomanjun.sleepdownschedule.domain.schedule.ScheduleTermBoundaryTest' --tests 'com.xiaomanjun.sleepdownschedule.model.ImportedCourseCardDefaultsTest' --console=plain --no-parallel --max-workers=2` 通过，含编译 3m37s。JUnit XML 确认分别运行 8 与 1 项，共 9 项，失败、错误及跳过均为 0。工程未提供 Release 单元测试任务，故使用既有 Debug 入口；没有运行完整套件。
- 先按用户选择交付 APK；用户随后明确要求“安装”，于 2026-10-19 13:08 经项目 ADB 5038 通道向 USB 设备 PLJ110（Android 17）执行 `install -r`，返回 Success。系统核对为 `1.2.7` / 35，更新时间为 13:08:23；保留应用数据，未自动启动。
- 没有截图、自动 UI 或性能测试。界面与原生运行时效果仍由用户实机验收，安装成功不等于交互验收通过；系统反编译源码与构建产物不提交。
