# COUI 设置布局、Popup 与小组件投光核查

> 后续纠正：用户否定本报告对应的 `be9e2fa2` APK，继续报告布局、光效问题及“当前课表详细设置”闪退，并明确要求移除旧 Miuix 设置页面。以下保留当时记录，不能视为现状或验收通过；后续见 [全面 COUI 替换](2026-11-05-coui-full-settings-replacement.md)。

## 范围与依据

用户要求保留设置功能、分组与保存语义，将首页和二级页的布局、字体、表面颜色及返回图标按 COUI 规范统一；首页保留悬浮搜索。同时修复 Popup 不显示和小组件光效位置 / 范围异常。

用户随后明确“那个选项行大小图标先用原来的”。最终实现据此保留原选项行的 Miuix 测量、56dp 最小高度、原内边距及 Rounded 功能图标；COUI 页面、分组、配色、文字和返回图标要求继续生效。

只读检查 E 盘两个克隆，保留 `E:/COUI/COUI-Kit` 的大量未提交改动。fetch 确认 `origin/main` 为 `c35a652`，实际接入的最新 Demo 分支 `codex/demo-component-coverage-20261104` 为 `af952eb6b2288a60652277cba2fb64a54a68aeb7`，与 `third_party/coui-kit/SLEEPDOWN_INTEGRATION.md` 的固定源码一致。

完整对照上游 `appearance`、`getting-started`、`secondary-pages`、`settings`、`scaffold`、`top-app-bar` 文档，以及 `DemoTheme`、`DemoApp`、`DemoSettingsPage`、`DemoPreferenceRows` 和实际组件实现。上游源码及其工作区未改动。

## 确认的问题与修复

| 问题 | 修复 |
| --- | --- |
| 仅 COUI 控件读原生色板，混用的 Material / Miuix 内容仍读宿主主题 | 在设置作用域映射相同的原生颜色与文字角色，区分页面、卡片和组件表面 |
| 旧列表间距和 COUI 分类标题留白叠加，部分普通二级页继续预留首页 Dock 的空白 | 公共列表及分组按 COUI Defaults 分工，普通二级页使用 Scaffold 安全区；有悬浮操作的页面继续单独避让 |
| 行适配器忽略 `insideMargin`，普通行与紧凑行挤在同一规格 | 正确传递内边距，并按用户最终要求复用原行测量，恢复原 23dp 功能图标及间距 |
| 返回仍走旧 42dp 原生按钮，右向箭头被压成方形 | 返回使用上游 Back 矢量、48/40/24dp 几何及 Demo 的 systemMaterial；Next 保留 12×24dp 比例 |
| COUI Scaffold 没有 Miuix Popup / Dialog 的注册及绘制宿主 | 页面壳保留独立 Miuix 浮层宿主，正文和顶栏的采样源位于 host 之外 |
| 设置首页缺少 COUI 常驻搜索 | 接入折叠标题和搜索，查询只过滤导航结果，不改设置值及页面分组 |
| 小组件只维护一个预览边界，投射深度受预览高和 112dp 固定上限影响 | 按组件类型持有完整、未裁剪的窗口边界，投光终点由实际按钮行下沿及渐隐留白决定 |
| 原生 COUI Generic Shape 不满足现有 glass Lens 的输入契约 | 原生表面继续使用 COUI Shape；液态分组沿用支持 Lens 的玻璃 Shape 及对应半径，搜索沿用玻璃 Capsule |

现有导航、Activity 标识、业务回调、即时保存、离页提交、Room 和备份格式未改变。液态模式保留 glass 材质；Android 8 的兼容接入与构建回滚开关保留。

## 验证

- 首轮 `compileGithubReleaseKotlin` 通过，3m12s；随后 Release 编译及 8 项定向回归通过，7m05s（日志 `tmp/coui-settings-popup-light-tests-20261105.log`）。
- 定向回归覆盖 Popup 打开与选中、原生主题角色、适配组与上游组的几何对照、搜索结果及小组件投光终点 / 坐标平移。新增行尺寸要求后，几何对照改为“COUI 分组 + 原行测量”的明确组合。
- 保留原行尺寸与功能图标后，`compileGithubReleaseKotlin` 和上述 8 项回归再次全部通过，6m45s，0 失败 / 0 跳过，日志 `tmp/coui-settings-final-verification-20261105.log`。
- 最后的原始 Insets 消费、液态搜索与 Shape 兼容检查收口后，`compileGithubReleaseKotlin` 通过，2m19s，日志 `tmp/coui-settings-final-compile-20261105.log`。这部分是布局 / 材质修改，没有重复扩展无关测试。
- 末轮验证曾因自动审批服务额度限制而未执行；用户要求继续后审批恢复，最终验证已完成。未绕过审批。
- 用户随后要求安装包，完整 `assembleGithubRelease` 已通过（3m51s），包含 R8、资源压缩、lintVital 和既有正式签名，日志 `tmp/coui-settings-release-package-retry-20261105.log`。首轮 JVM native 内存不足，释放已空闲的 Gradle 并降低 R8 并发后成功；未修改持久构建配置。
- APK 本地保全为 `tmp/review-apks/SleepDown-1.2.8-beta2-coui-settings-20261105-be9e2fa2.apk`，8,110,423 字节，SHA-256 `be9e2fa20ca590f5aa29dc36505efa0f4edf0c2f66a0970974f79997c187c675`。包元数据为 `1.2.8_beta2 / 36`、`com.xiaomanjun.sleepdownschedule`；V2 签名有效，证书与此前 Release 一致，复制后完整哈希一致。
- 尚未安装或做实机视觉验收，不能把编译和回归结果当成实机验收。没有上传或发布。

本轮仍在已有 `codex/rescue-import-materials` 工作树内续作，保留其他未提交活动任务；没有暂存、提交、推送、创建 PR 或修改 E 盘源码。只读快照及构建日志位于忽略的 `tmp/`。
