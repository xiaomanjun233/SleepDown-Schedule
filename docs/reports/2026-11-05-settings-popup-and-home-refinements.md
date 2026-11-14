# 设置首页与 Popup 细节修订

用户在上一轮 `c9705f18` 包后继续要求：首页不显示分类名；应用头部下方分隔线向右移、分隔线颜色更淡；Popup 恢复原改版 Miuix；液态开关保留原绿色；深色模式区域参考系统设置；设置弹窗不居中。

## 实现

- 首页保留四个功能分组和原功能图标，移除“应用 / 智能助手 / 其他”分类标题。无标题组沿用公共 16dp 间距，首组仍为 12dp。
- 仅应用大图标下方的分隔线由 56dp 移至 88dp，与应用标题起点对齐；其余图标行保持 56dp。设置分隔线统一使用 12% 前景色，继续保留 COUI 的 0.33dp（至少一像素）厚度。
- COUI Item 仍负责下拉触发行；弹出面板恢复 `miuix-local` 的改版 `OverlayDropdownPopup`，复用原玻璃材质、25dp 外壳、Morph 和滑动选择。当前构建通过 `gradle.properties` 引用本仓库 `miuix-local`，不是相邻参考克隆。
- 透明、零 Insets 的 Miuix Scaffold 只托管本地浮层。COUI 继续负责页面、顶栏与安全区；完整页面 producer 位于内层 COUI Scaffold，Popup 在其外的后绘制兄弟层。面板使用当前设置窗口的 host，锚点来自真实 trailing Row；没有恢复旧 Miuix 设置页面壳。
- 液态 Toggle 不再强制应用主题 accent，恢复组件默认浅色 `#34C759` / 深色 `#30D158`；COUI 原生控件继续跟随应用强调色。
- 深色模式保留原日/周 × 明/暗四种预览图和原保存语义。参考 Settings 反编译的 `darkmode_preview_preference.xml` 调整为两列、上 34dp / 下 25dp、图文间距 24dp、文字与圆点间距 10dp；预览按可用列宽约束，避免窄屏使用固定大图；使用 COUI RadioButton，每列保持单一可访问点击目标。“跟随系统”前增加普通分隔线。
- 设置 ContentDialog / Alert 明确使用 COUI Bottom placement；交接等待对应 250ms bottom_exit 动画并读取系统窗口动画倍率。没有修改设置以外弹窗的默认布局。
- 首页“检查更新”的弹窗 host 从页面壳外移入设置内容作用域，直接取得该页主题与 Backdrop；避免独立调用首页时落回居中 Alert。发现此入口后已停止中间 Release 构建，固定源码再顺序运行验证和打包。

E 盘 Demo、系统反编译和用户截图继续只读，不加入仓库；本轮未继续猜测调整 Widget 光效参数。保留完整工作树中的其他活动任务与用户数据。

## 验证与交付

9 项定向回归通过（2m25s，0 失败/错误/跳过，`tmp/settings-refinements-verification-final-20261105.log`）：

- SDK 26/35 加载真实非空作息模型的详细设置，滚动、原 Miuix 下拉选择与日期提交通过；日期窗口显式检查 `Gravity.BOTTOM`。
- SDK 26 的 Popup 选择后重开、点击空白关闭、再次打开并选择通过，保留原控件几何与主题桥接检查。
- 原生与液态首页均保留 RGB 26 卡片、COUI 普通轮廓、52dp 行高、大图标与版本排布；分类标题不存在；首条分隔线原 72dp 采样位置保持卡片底色、96dp 位置命中新 12% 细线。
- 通用设置完整页面渲染通过，手动选择暗色仍取消跟随系统并提交原字段。

已打开查看本轮原生/液态首页、通用设置预览及原 Miuix Popup 栅格，产物为 `tmp/coui-visual-review-20261105/` 中的 `settings-home-native.png`、`settings-home-liquid.png`、`general-appearance-native.png`、`schedule-miuix-popup-native.png`。Compose 本地栅格与手机 ROM 字体、系统栏和硬件玻璃材质不同，不能据此宣称实机验收通过。

首页更新 host 补齐后，固定源码顺序重跑上述 9 项回归与完整 `assembleGithubRelease` 全部通过（11m12s，244 项中 27 执行），最终日志为 `tmp/settings-refinements-final-package-20261105.log`。保留资源压缩、R8、lintVital 和 GitHub 金标；构建前后 10 个设置源码哈希一致，记录在 `tmp/settings-refinements-source-hashes-20261105.json`。

新包：`tmp/review-apks/SleepDown-1.2.8-beta2-settings-refinements-20261105-d83a1b0c.apk`，8,164,067 字节，SHA256 `d83a1b0cc96d67de63d452a853286674741bb1fecdf9bc5ff826e8e2910c9b5f`。元数据 `com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / 36`、minSdk 26；复制后哈希一致，V2 签名校验通过，证书 SHA256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a` 与既有包相同。摘要 `tmp/settings-refinements-delivery-20261105.json`。

包来自当前完整工作树，不是独立 PR 包。未安装、启动、上传、暂存、提交或推送；手机实际字体、玻璃效果及此前详情闪退是否与 Mesh 同源仍未实机确认。本轮新包替代 `c9705f18` 作为当前交付。
