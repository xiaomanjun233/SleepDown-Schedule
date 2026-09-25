# SleepDown for SWU：SDK 接入试验准备

记录日期：2026-09-13。

## 本轮范围与状态

用户要求建立独立分支，以最小改动集成 aTrust SDK，并构建新包名的签名 Release，供本人测试教务访问；前提是不需要额外注册账号。此授权取代此前“仅调查”的范围。

- 已建立分支：`codex/sleepdown-for-swu`。
- 起点：`b12b8c2`（原工作分支 `codex/shortcut-course-editor`），开始时工作树干净。
- 拟用应用名：`SleepDown for SWU`。
- 拟用 applicationId：`com.xiaomanjun.sleepdownschedule.swu`；当前尚未修改构建配置。
- 尚未获得官方 AAR，未集成 SDK、未构建或安装测试 APK。
- 本次只增加这份记录，未改动应用代码、数据库或用户设备。
- 用户确认没有深信服自助服务平台账号，并明确选择“按原条件暂停 SDK 集成”。本轮在此结束，后续须有新的继续指示才恢复集成。

## 官方 SDK 获取结果

[官方 SDK 文档的下载入口](https://bbs.sangfor.com.cn/atrustdeveloper/appsdk/other/demo_sdk.html)指向[深信服自助服务平台 SDK 列表](https://support.sangfor.com.cn/productSoftware/list?product_id=19&category_id=94)。

通过列表页面实际使用的公开列表接口读取到：

| 字段 | 返回值 |
| --- | --- |
| 名称 | 深信服移动端零信任 SDK |
| 版本 | 2.7.10.1 |
| 更新日期 | 2026-08-30 |
| 平台 | Android、iOS、HarmonyOS NEXT |
| 标注大小 | 76.05 M |
| 官方标注 MD5 | `8726024479d4c25b0b84c5c9129ff852` |

上述大小、版本和 MD5 是官方列表元数据，未通过实际 SDK 文件验证。

在未登录状态下请求列表给出的下载链接，最终跳转至 `https://support.sangfor.com.cn/user/login`。响应为 HTTP 200、`text/html; charset=utf-8`、92,193 字节，内容是账号登录与注册页面，不是 SDK 压缩包。因此，公开可读的 SDK 文档和下载列表不代表可以匿名下载开发包。

下载账号与 SDK 运行凭据需要区分：

- **下载**：本次实测要求登录深信服自助服务平台。若没有已有账号，当前官方路径不满足“不额外注册账号”的条件。登录后是否还需要客户身份认证，尚未验证。
- **初始化**：[官方接口文档](https://bbs.sangfor.com.cn/atrustdeveloper/appsdk/android/android_sdk_api_initialize.html)的参数为 Context、模式、flags 和额外配置，未列出开发者 AppKey。不能据此断言获取与使用 SDK 完全没有其他条件。
- **学校接入**：同一文档说明，未发布给用户的 SDK 应用能否以接入模式运行，取决于服务端是否允许；西南大学的这项配置尚未验证。

## 获得 SDK 后的最小验证范围

1. 核对官方压缩包完整性、AAR 的真实 API、Manifest、原生架构、依赖和混淆规则。
2. 保留原 namespace 和组件类名，在本分支修改 applicationId 与应用名，检查 launcher alias、Provider authority、签名与并存安装。
3. 增加独立的接入测试入口：认证状态、统一认证流程、内网页面访问、错误展示和退出认证；先不实现课表、调课、考试、成绩同步。
4. 使用应用内 TCP 接入能力测试，保留正常 TLS 校验，不复制手机 aTrust 的私有凭据。
5. 学校[Android 手册](https://nic.swu.edu.cn/info/1034/2161.htm)说明入口为 `https://v.swu.edu.cn`，目前仅启用统一身份认证。因此不能直接假定通用 SDK 用户名密码示例适用于该部署，必须核对 AAR / Demo 支持的认证流程。
6. 构建签名 `assembleGithubRelease`，检查最终包名、SDK 打包内容和签名后交付。SDK 文件未取得前不制作冒充已接入 SDK 的测试包。

验证独立接入时，应由用户先断开手机原有 aTrust 系统 VPN，并确认不在校内直连网络；记录测试时是否存在系统 VPN。仅打开公开的 VPN 登录页不能证明教务内网已经可达，需要访问真实教务页面。用户关闭应用后能否免交互续接、后台更新与会话过期恢复属于后续验证，本轮不作保证。

## 本轮验证

- 已核对 Git 工作树并建立独立分支。
- 已核对官方 SDK 列表与下载重定向、初始化文档、学校认证说明。
- 未执行 Android 构建或设备安装：缺少官方 SDK 文件，且尚无应用代码修改。
- 临时公开网页与响应保存在忽略目录 `tmp/atrust-analysis-20260913/sdk-official/`，不加入版本管理。
