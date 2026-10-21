# 助理附件 Popup、日视图上传与课程外观反馈

本轮继续草稿 PR #78，未合并 main 或发布新版本。用户明确补充：“把周视图那个上传文件的一起加进来”指日视图助理也支持上传文件。

## 改动与原因

- `be02fd55`：级联 Popup 原先只按靠近哪一侧边缘计算收回位置，菜单被安全区调整位置后就无法准确回到左侧加号。共享 Morph 增加真实按钮中心相对最终面板的位置补偿；加号菜单采用 Start 对齐、横向额外偏移为 0，使展开后的左边缘对齐按钮。图片或文件操作延迟到菜单退出完成，避免系统选择器提前改变窗口及键盘位置。
- `cab74613`：日视图的 `HomeScreen → DayScheduleScreen → TodayAgentHost → TodayAgentCard → DayAgentConversationDialog` 接通同一文件回调。加号菜单显示“上传文件”，选择后先收回助理，再进入已有材料确认、提取、校验和预览链路；没有新增协议、重复解析器或直接写库入口。
- `47b37ae7`：`courseCardUsesAssignments` 和 `resolvedCourseCardPalette` 移除无壁纸时强制多彩的规则，单色遵循选中颜色，渐变遵循选中种子色，彩色继续使用调色板。个性化移除第二分类冗余的“课程卡片”总标题。周视图移除已结束课程的灰化计算及参数传递，普通节次和补充自定义时间课程都保持原色；停课、调休提示与日视图自己的结束状态保留。

## 依赖补丁与构建

- 核对实际构建入口为 `gradle.properties` 指定的 `miuix-local`，没有误改旁边的未打补丁参考目录。
- 修改前完整补丁在官方 `v0.9.3`（`c36fab72391801d1e3ea5a00f966bf16bac28d4c`）的干净副本中应用成功，与当前 20 个 UI/preference 运行时源码一致；修改后重新导出完整补丁，再在新的官方标签副本中执行 `git apply --check`、应用及逐文件比较，20 个文件全部一致。核对目录仅在忽略的 `tmp/` 中。
- 首次打包于 59s 停在依赖 `classes.jar` 文件占用；Windows Restart Manager 确认占用者为 Gradle daemon 2792，`gradlew --status` 确认该版本唯一 daemon 处于 IDLE。正常停止这一个空闲 daemon 后重新构建，没有清理工作树或用户数据。

## 验证与交付

- `47b37ae7` 的完整 `assembleGithubRelease --console=plain --no-parallel --max-workers=2` 通过，6m37s，159 个任务中 28 个执行、131 个复用，保留资源压缩、R8、lintVital 和正式签名。最终日志为忽略目录中的 `tmp/build-20261019-popup-day-file-course-feedback-final.log`。
- APK 副本：`tmp/review-apks/SleepDown-1.2.7-popup-day-file-cards-20261019.apk`，7,574,871 字节，SHA-256 `0cac9908eba9c1224af5b932f42c956ef36b20a9fcaa4ab34d3bdd62ed63e8a4`，与标准输出 APK 摘要一致。包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7` / 35。
- `apksigner verify --verbose --print-certs` 通过，v2 签名有效，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与既有正式签名一致。
- ADB 5038 通道向 USB 设备 PLJ110（Android 17）执行 `install -r` 返回 Success；系统核对为 `1.2.7` / 35，更新时间 2026-10-19 22:23:04。保留用户数据，未自动启动或操作应用。
- 此阶段未运行额外单元、自动 UI 或性能测试。安装后用户继续反馈时间显示截断、多校区作息兼容、上传 Popup 文字反色及导入预览“熊猫字”；随后修订及最终交付见[通用多作息与界面反馈](2026-10-19-standard-clock-ui-feedback.md)，不能据本阶段构建或安装成功宣称已通过实机验收。
