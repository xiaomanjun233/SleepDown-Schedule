# SleepDown-Schedule 1.1.5：Release 签名审计

审计日期：2026-08-10
阶段：Phase A（只读审计；尚未改版本号，尚未写 BackupService）

## 结论

官方 v1.1.4 安装包可以从 GitHub Release 获取，且已完成以下核验：

- 包名：`com.example.courseschedule`
- `versionCode`：`24`
- `versionName`：`1.1.4`
- 官方 APK 与当前本地 Release 候选 APK 的证书 SHA-256 相同
- 因此“找不到官方包/无法确认升级签名”不是当前 1.1.5 Phase A 的阻塞项

但现有发布身份是一个必须记录的风险事实：官方 v1.1.4 使用的是 `Android Debug` 证书。`app/build.gradle.kts` 在四个 `sleepdown.release*` Gradle 属性不完整时会回退到 debug signing config。Phase A 不擅自更换证书，也不猜测私钥位置；1.1.5 最终包仍必须在版本号变更后重新做同一项核验。

## 官方 v1.1.4 证据

来源：

- Release 页面：[v1.1.4](https://github.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.1.4)
- APK：[SleepDown-1.1.4.apk](https://github.com/xiaomanjun233/SleepDown-Schedule/releases/download/v1.1.4/SleepDown-1.1.4.apk)

GitHub Release API 给出的资产信息：

| 项目 | 值 |
| --- | --- |
| Release tag | `v1.1.4` |
| Release id | `367188497` |
| 发布日期 | `2026-08-08` |
| 资产文件 | `SleepDown-1.1.4.apk` |
| 文件大小 | `5,172,694` bytes |
| GitHub digest | `sha256:a0d83a3ba9b61737e263817d38a71f48a64449fa36637aab9fa2ef26982309bb` |
| 本地下载 SHA-256 | `A0D83A3BA9B61737E263817D38A71F48A64449FA36637AAB9FA2EF26982309BB` |

本地保存的官方资产：`tmp/SleepDown-1.1.4-official.apk`。该文件仅作为审计证据保留，不属于 1.1.5 功能实现。

### 官方包 badging / 签名

由 Android SDK `aapt` / `apksigner` 检查：

| 项目 | 官方 v1.1.4 |
| --- | --- |
| package | `com.example.courseschedule` |
| versionCode / versionName | `24 / 1.1.4` |
| 签名方案 | V2（RSA 2048） |
| 证书 DN | `C=US, O=Android, CN=Android Debug` |
| 证书 SHA-256 | `9ac88e98f545cbedc8b91b3d45163229ceb5f3289bd4bf1d7f7bfab5fa8e27dc` |
| 证书 SHA-1 | `10d0769eeba18f8aa36d0f2981c1cae6656f9828` |
| 证书 MD5 | `b2ca312b5324813250ba34825ef4cddd` |

## 当前本地 Release 候选

文件：`app/build/outputs/apk/release/app-release.apk`。

| 项目 | 当前本地候选 |
| --- | --- |
| package | `com.example.courseschedule` |
| versionCode / versionName | `24 / 1.1.4` |
| APK SHA-256 | `4F1EC5FE0F1F2FD79F5E1B5BD64945D9EB8A83F0D83C9ED068B8AE9799B553A9` |
| 文件大小 | `5,189,162` bytes |
| 证书 SHA-256 | `9ac88e98f545cbedc8b91b3d45163229ceb5f3289bd4bf1d7f7bfab5fa8e27dc` |

APK 字节哈希与 GitHub 资产不同是正常的：本地重新构建的资源压缩、构建时间或 Gradle 输出可以不同。本次判断升级兼容性使用证书公钥指纹，而不是要求 APK 字节完全相同。

## 构建配置审计

当前 `app/build.gradle.kts:11-20,67-86` 的逻辑是：

1. 读取 `sleepdown.releaseStoreFile`、`sleepdown.releaseStorePassword`、`sleepdown.releaseKeyAlias`、`sleepdown.releaseKeyPassword`。
2. 四项全部存在时创建 `release` signing config，并启用 V2 signing。
3. 否则 release variant 使用 `signingConfigs.getByName("debug")`。

仓库中没有 keystore、密码或 `gradle.properties` 私钥配置。Phase A 不读取、不导出、不修改用户环境中的秘密，也不把当前 debug 证书“修复”为另一个未知证书。

## 1.1.5 必做的最终门槛

版本号仍保持 `versionCode=24`、`versionName="1.1.4"`，直到迁移实现和测试完成。最终构建阶段必须：

1. 先生成并保存 1.1.5 Release APK 的 SHA-256。
2. 检查 package、versionCode、versionName。
3. 用 `apksigner verify --print-certs` 检查证书 SHA-256，并与官方 v1.1.4 的指纹对比。
4. 若指纹变化，立即停止发布/安装验证，要求明确的签名决策；不得通过卸载重装规避升级问题。
5. 使用 `adb install -r` 在已有 1.1.4 数据的设备上做升级验证，确认数据仍在。

Phase A 的签名结论是“已核验、暂不改动”，不是“已经核验 1.1.5”。
