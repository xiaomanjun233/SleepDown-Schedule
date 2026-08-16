# SleepDown 课程表：开发交接

## 项目约束

- Android 课程表，核心特色是 Miuix 风格、液态玻璃视觉和 AI/Agent 融合。
- 保留现有功能、视觉、动画、模糊/折射/色散效果及第三方开源库引用；性能优化不得靠删除既有效果完成，除非用户明确要求。
- 数据安全优先。数据库、配置、备份恢复和升级必须提供迁移路径，不得主动清除或覆盖用户数据。
- UI 按窗口和安全区自适应，不为单一 DPI、分辨率或设备写死特例。
- 工作树可能包含用户素材和临时证据；不要使用 `git reset --hard`、`git checkout --`，不要删除或顺手整理不在任务范围内的文件。
- 未经明确同意，不推送、不打标签、不发布 Release。

## 仓库与构建

- 工作目录：`D:\Android studio\CourseSchedule`
- 集成分支：`main`
- GitHub：`origin`；Gitee：`gitee`
- Java：`D:\Android studio\JDK`
- Gradle 用户目录：`C:\Users\23085\.gradle`
- Release 构建：

  ```powershell
  $env:JAVA_HOME='D:\Android studio\JDK'
  $env:GRADLE_USER_HOME='C:\Users\23085\.gradle'
  .\gradlew.bat assembleRelease --console=plain
  ```

- GitHub Release APK：`app\build\outputs\apk\github\release\app-github-release.apk`
- 设备验证只安装签名 Release，不安装 Debug。安装前运行 `adb devices -l`；无线地址会变化。
- 可以代为覆盖安装 Release，但不得启动应用、点击界面或执行真机 UI 自动化，验收由用户完成。

## 当前基线

- 已发布正式版为 `v1.1.5`，旧包名 `com.example.courseschedule`，`versionCode=25`，Room v36；该版本已冻结。
- 开发版为 `1.2.0` / `versionCode=26`，正式包名 `com.xiaomanjun.sleepdownschedule`，含 `github/store` 渠道和 `.debug` 隔离。
- Release 签名保存在仓库外的长期 keystore，用户级 Gradle properties 已配置；不要把证书路径或密码写入仓库。
- 旧包升级通过 v1.1.5 导出 `.sleepdown`、新包恢复完成；`BackupFormatV1` 保持兼容。迁移与持久化审计见 `docs/migration/`。

## 当前实现状态

- 首页“个性化”和三点菜单为两个独立 42dp `LiquidButton`。三点菜单从按钮下方展开并保持右边界对齐；液滴缩小/下移占动画 20%，圆角在该段内开始向目标过渡，内容 handoff 的绝对时机、40% 回弹峰值和幅度保持不变。
- 三点菜单外壳为 208×220dp、30dp 圆角；选中胶囊有效圆角 19dp，左/右/底同心间距均为 11dp；玻璃 surface tint 为浅色 0.28、深色 0.40。
- 个性化面板和三个菜单目的页保留真实液态玻璃、动态壁纸采样及开关动画。运动阶段避免根 detail layer 重录，内容使用 GraphicsLayer 缓存；不得恢复 Bitmap/ImageBitmap/RenderNode 截图路线。
- 个性化滑块支持快速拖动隐藏面板、吸附点、逐帧预览合并和局部 override。液态玻璃设置映射为 0%–50%=0x–1x、50%–75%=1x–2x、75%–100%=2x–4x，UI 中点仍为 50%。
- 首页日期区总高 42dp：日期 21sp 加粗，周次 14sp 次级灰色。首页系统状态栏图标按壁纸顶部实际可见亮度自适应，离开首页恢复跟随应用主题。
- AI 教务导入会话输入框会识别窗口是否已被 IME resize，只补一次底部 inset；AI 导入附件全屏 Morph 到达 Open 后圆角归零并取消外壳裁切/离屏合成。
- 手机周视图节次栏为 52dp，课程主体保留 8dp 右余量；相邻页滑动内容可延伸进该区域。大屏保留原 tablet 布局。
- 官网源码位于 `sleepdown-site/`，线上仍由已预付杭州 ECS 直出；不要在未授权时创建 OSS Bucket、CDN 域名或其他按量资源。

## 已验证与待验收

- 最新代码通过 `HomeAnchoredMorphGeometryTest`、`assembleGithubRelease`（Kotlin、R8、资源优化、lintVital、签名打包）。
- 最新 Release 已覆盖安装到 PLJ110；助手未启动或操作应用。
- 用户仍需优先验收：AI 输入框键盘定位、AI 全屏二级页动画终点裁切、三点菜单液滴连续性与 11dp 三边同心间距、首页日期排版及状态栏反色。
- 完整 unit/backup、四变体、benchmark compile、真实 v1.1.5 恢复、Store 权限与新包 Widget 首装仍待发布前补跑。
- 性能 benchmark 路线当前暂停；已有诊断代码、trace 结论和失败方案记录在 `docs/performance/UI_PERFORMANCE_BENCHMARK_HANDOFF.md`。没有用户明确要求时不要恢复 Perfetto、JankStats 或 UI 自动化。

## 下一步

1. 等待用户完成上述真机视觉与交互验收，按反馈做小范围修正。
2. 发布前补齐迁移、备份、四变体及 Store/GitHub 渠道验证。
3. 确认 Draft PR、应用商店身份和升级说明后，再由用户决定是否推送、合并远端或发布；当前不要发布。

## 工作方式

- 默认使用 PowerShell 7；搜索优先 `rg` / `rg --files`，源码修改使用 `apply_patch`。
- 先诊断并保留证据，再修复；崩溃优先读取实际堆栈。
- GitHub 仓库、PR、Issue、Review、CI 和 Release 优先使用已连接的 GitHub 能力；本地 `git` 用于工作树、分支、暂存和提交。
- `.gradle-user-home/`、`tmp/`、`sleepdown-promo/`、`ui.xml`、根目录设备截图和临时验收图片不属于源码提交范围，除非用户单独指定。
