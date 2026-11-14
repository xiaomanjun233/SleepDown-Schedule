# COUI-Kit 设置页迁移准备

用户要求先提交光效/分页条 PR，再以可回滚的独立分支或 PR 将设置首页和二级设置页迁往 COUI-Kit。首页按 COUI 设置首页装配，二级页统一顶栏按钮布局及图标；特殊页面保留必要交互并改为 COUI 风格，液态玻璃模式仅保留材质差异。用户已确认“耳机页”指二级设置页。

## 上游与文档

已检查 E 盘两个克隆：`COUI-Kit` 保留大量本地开发改动，未触碰；干净的 `COUI-Kit-upstream` 已 fetch 上游 main，并在 `codex/sleepdown-settings-integration-20261105` 分支检出 `c35a652`。没有向 COUI-Kit 上游提交或推送。

最新接入文档包括 `docs/guide/getting-started.md`、`appearance.md`、`secondary-pages.md`、`compatibility.md`，以及 `docs/components/settings.md`、`collapsing-app-bar.md`、`top-app-bar.md`、`icons.md`。公开 Group/Item/Divider/Footer、Scaffold、CollapsingTopAppBar/SearchBar、TopAppBarButton 和 CouiIcons 可直接消费，不需要复刻 Demo 业务。

## 宿主入口

- 设置首页：`feature/settings/SettingsRootScreen.kt`。
- 公共页面容器及平板/Activity 二级页：`core/ui/settings/GlassMiuixSettings.kt`。
- 行、分组、输入、下拉及时间选择：`core/ui/settings/SettingsRows.kt`、`SettingsGroups.kt`、`SettingsTextFields.kt`、`GlassMiuixPopup.kt`、`SettingsTimePickers.kt`。
- 保存、返回拦截、课程编辑和导航状态继续由现有页面持有；只替换展示及组件装配。

## 待决定的边界

COUI-Kit 最新源码 minSdk=31，宿主目前 minSdk=26；库文档禁止使用 overrideLibrary 绕过。已向用户询问是否允许独立实验迁移分支提高到 Android 12，未获答复前不修改最低系统要求。核心/适配源码的版本、Compose 和 DynamicAnimation 约束仍需在接入分支核对。

光效独立分支为 `codex/native-card-light-pages`，功能提交6b480f98和交接提交01c77e12已推送；11项定向回归和最终4m06s完整签名Release通过。当前会话没有 GitHub 插件工具，已询问用户是否允许通过本机 Git 凭据及 GitHub 官方 API 创建已授权 PR，尚未答复，因此PR未创建；不使用 gh，不因工具缺失自行改用其他远端写入方式。

## 回滚边界

光效与设置迁移分支分离；迁移再按依赖接入、公共容器/控件、首页、二级页分阶段提交。保留原工作树和 COUI-Kit 本地开发克隆，不清数据、不覆盖课程或迁移 Room。用户未授权合并、Release 或向 COUI-Kit 上游投稿。
