# 作息管理子页与列表排版

日期：2026-10-04。基线为已发布的 `1.2.7_beta2`（`versionCode=34`），延续本轮需求分支。

## 页面与交互

- 入口改为“设置 → 当前课表详细设置 → 作息管理”，移除设置首页和大屏侧栏的同级入口。平板在详情区域进入子页，返回时仍选中课表详细设置。
- 详细设置用同一个 Miuix“作息”分组卡片承载“当前作息”切换菜单及“作息管理”入口。一级页移除名称输入框、新建选择弹窗、删除按钮及旧删除弹窗，新建、重命名和删除集中到管理子页；详细节次的“编辑”直接调整当前时间线。
- 从其他课表的详细设置进入时，传递该课表 ID；作息应用、课程一致性校验及节数调整都使用该课表的数据。
- 进入前保存详情草稿，保留已有节次重映射及学期缩短确认。手机在子页返回后重新读取作息；平板也刷新父页，防止旧草稿覆盖已保存的时间。
- 卡片采用紧凑横排：名称、“当前”标记、各段节数与起点在左侧，编辑和删除图标在右侧。内边距为横向 14dp、纵向 10dp，文本行距 2dp，列表间距 8dp；摘要不重复时段名称，单行省略，避免挤成大卡片。
- 名称使用 Miuix `headline1`（17sp、Medium），摘要使用 `body2`（14sp）及 `onSurfaceVariantSummary`，直接复用 Miuix Text，避免继承 Material 的额外行高。“当前”标记使用 Miuix 标准 13sp。
- 编辑、删除和右下角新建复用公共 `DialogLiquidButton`，恢复 LiquidButton 材质和按压效果。圆形视觉尺寸 42dp，保留至少 48dp 的交互区域及禁用语义；当前作息隐藏删除动作。编辑继续使用 SleepDown 的时间轴。
- 创建向导与快捷选择器的动作按钮复用 `LiquidAlertActions`，使用公共 Alert 的字号、字重、48dp 高度与间距。

## 排版参考

参考两端的 `CourseTimeSettingsScreen.kt`，仅沿用列表排版与动作位置：

- [Nexio GitHub](https://github.com/HaoZai000/NexioSchedule/blob/a23c602ab623fd9e939b78e5325871f1561b7981/app/src/main/java/com/haooz/chedule/ui/activities/CourseTimeSettingsScreen.kt)，本轮读取的最新 `master` 为 `a23c602`（v1.6.2beta4）。
- [Nexio Gitee](https://gitee.com/com_haooz_account/hyper_schedule/blob/821e779884b15d19e5872d65b0ca335e36a9d1c9/app/src/main/java/com/haooz/chedule/ui/activities/CourseTimeSettingsScreen.kt)，读取基线为 `821e779`（v1.6.1beta1）。

## 验证与产物

最终 `assembleGithubRelease --console=plain --no-parallel --max-workers=2` 完成，耗时 7m03s；包含 Kotlin 编译、R8、lintVital、资源处理、签名与打包。

产物：`app/build/outputs/apk/github/release/app-github-release.apk`，生成时间 2026-10-04 18:28:37，包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7_beta2` / 34，6,925,623 字节。

SHA-256：`5B47E5C47F58237094BE5A6D5C6CB8393409DD981C48C30DE2C044821E8359C4`。

安装前 `adb -P 5038 devices -l` 确认 `192.168.1.10:42533` 的 PLJ110 在线（Android 17 / SDK 37）。使用项目固定通道入口 `scripts/Invoke-Adb.ps1 -s 192.168.1.10:42533 install -r ...` 覆盖安装最终 APK，返回 `Success`，未自动启动应用。

未运行设备界面测试或帧率测试，按用户要求交由用户实测。
