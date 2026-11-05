# COUI 设置页布局迁移

## 范围与来源

用户要求设置首页和二级设置页迁往 COUI-Kit，并明确：Android12以下只保留排版、关闭材质采样；液态模式沿用同一排版并保留材质、开关和滑条等控件；现有系统类调用先留着；Miuix 剔除等待后续指令。

迁移在 `codex/coui-settings-migration` 独立分支完成，基线 origin/main `3792fdba`，光效修复仍在 #101 的 `e36d83c0`。E盘干净上游克隆抓取全部远端分支后，采用最新已推送分支 `codex/demo-component-coverage-20261104` 的 `af952eb6b2288a60652277cba2fb64a54a68aeb7`；main 的 c35a652 并不包含这批新顶栏/Scaffold接口。已有本地改动的 COUI 克隆未动，未向上游写入。

## 页面接入

按官方源码模块方式保留 COUI 核心与许可/图标来源。首页消费 LargeTopAppBar、COUI 分组、Item、固定文字角色、常用系统图标和列表回弹。二级 Activity 与平板详情共用52dp顶栏，返回、标题和操作按COUI槽位测量。保存、返回拦截、作息编辑、组件编辑及导航状态仍由原业务持有。

液态分组使用相同平滑圆角与行布局，材质继续由 glass 入口提供；背景源与正文源分离，分组只读取独立背景，顶栏读取正文，避免自采样。原系统加载和液态开关/滑条源码保留。关于页补充 COUI 来源，许可文件进入APK资源。

宿主 minSdk26 保留。本地兼容副本在Android12以下不录制或消费材质源，向二级正文传null背景；顶栏直接布局普通表面。Android8字体使用旧平台样式API，Android9起保留精确字重。Group 的外边距/组间距可以由宿主承担，默认用法不变。其他上游效果仍遵守各自RequiresApi，不能据本模块minSdk宣称整个上游支持Android8。依赖保留上游BOM和DynamicAnimation约束，没有overrideLibrary、force或exclude。

回滚入口：`-Psleepdown.couiSettings=false` 保留原设置实现。依赖接入与页面接入分开提交；没有移除Miuix或改动课程、Room、备份和快照格式。

## 验证

COUI源码模块编译1m44s通过。宿主Release Kotlin及定向回归4项通过，9m58s：API26真实Compose布局、显示及点击1项，作息编辑退出协调器既有3项。API26验证确认材质开关关闭，并经过Scaffold、TopAppBar、Group、Item和文字实际组合。此为Robolectric兼容验证，不等同旧手机或设备GPU验收。

首次宿主依赖解析遇到镜像缺包缓存；已核实官方和镜像均提供对应AAR，刷新缓存后按上游原始约束继续，不以降低或强制依赖掩盖。两处宿主接口类型错误修正后，本轮编译/测试通过。

联合完整脏工作树的签名Release构建与安装待补记；不应安装缺少其他活动功能的独立分支验证包。
