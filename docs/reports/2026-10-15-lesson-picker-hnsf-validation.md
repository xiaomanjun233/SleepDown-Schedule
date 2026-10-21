# 课程课时选择与河南师范大学输入校验

## 范围

用户暂缓 beta6 发布，继续修复第三种课程时间方式的节数选择和对齐，并调查河南师范大学适配器 1 的 `validateYearInput is not defined`。本轮不修改版本、不创建发布；测试包安装到用户指定的 OPPO PLJ110，由用户自行检查界面，不自动启动应用。

## 河南师范大学：App 回调作用域缺失

通过 GitHub plugin 读取[拾光当前 HNSF_01 脚本](https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/main/resources/HNSF/hnsf_01.js)，返回的 Git blob SHA 为 `c1c4602809b5b5a0f39be473ad4677669a79045d`。当前上游与本地内置脚本均定义 `validateYearInput`，并把这个名字交给 `showPrompt`。本次错误不需要修改学校接口或向上游提交学校补丁。

App 使用 direct eval 隔离适配脚本，使重复运行时的顶层声明互不冲突；但原生输入框仍在网页全局执行校验函数。HNSF_01 的函数只在适配器作用域内，因此输入四位学年也会失败。

修复在运行脚本的作用域提供校验入口；`showPrompt` 按请求捕获对应入口，原生表单通过请求编号调用。完成、拒绝或超时后清理请求，适配器结束后移除当前入口。iframe 继续复用顶层 Bridge，校验在适配器所在作用域执行。学校脚本及其四位学年规则保持原样，不跳过输入校验。独立提交：`3e432691`。

实际执行 `scripts/tests/shiguang-execution.cjs`，覆盖内置 HNSF_01 公告 → 学年输入 → 学期选择，合法/非法输入、字符串转义、两次请求作用域隔离、网页全局函数、iframe 和结束清理。`scripts/tests/ai-edu-routing.cjs` 也通过；同步修正其旧模拟脚本的返回值，使其遵守当前 `started` 协议，未修改页面识别规则。测试使用合成页面和输入，没有访问学校账号或获取用户课表。

## 课程时间选择器

节数由数字键盘改为 1–40 节滚轮，通过公共 `SleepDownPickerDialog` 提供取消与确定。取消保留原节数；确定只更新当前编辑草稿，外层课程保存语义保持不变。

时间字段转为选择器时，`SettingsTextFieldRow` 漏传 `compact`，导致“开始上课时间”与紧凑表单的缩进不同。补齐参数，并让逐节标题、说明和时间预览共用相同水平缩进。独立提交：`16fb7d4e`。

## 验证

Node 两项回归已通过。`CourseLessonClockDraftTest`、`CourseEditorDataSafetyTest`、`CourseEditorPeriodBoundsTest` 共 3 套、17 项、0 失败。

`assembleGithubRelease` 成功，包含 Release Kotlin 编译、R8、资源压缩、lintVital 和签名。APK 身份为 `com.xiaomanjun.sleepdownschedule`、`1.2.7_beta5` / `34`，大小 7,525,159 字节，SHA-256 为 `1BF4810DBD52995CE185A72994D2B33D2E192B6D8E810665FFD6EBDEADBCBE7A`。签名校验通过，证书 SHA-256 为 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`；另检查 APK 的 DEX 包含新增校验入口和节数选择器，排除误装旧产物。

通过项目 SDK ADB 的 5038 通道覆盖安装到 OPPO PLJ110 / Android 17，返回 `Success`，设备记录的更新时间为 2026-10-15 15:09:37。未自动启动应用，未修改用户课表。未进行河南师范大学真实账号端到端导入；手机界面由用户检查。没有创建 beta6 标签或发布。
