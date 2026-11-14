# 云端短口令与 JSON 分享（2026-11-03）

## 最终形态

分享面板移除原来的“课表口令”，只保留三项：

1. **分享短口令**：十位 Crockford Base32 提取码，云端保存密文并解密，15 分钟内有效，需要联网。
2. **分享 JSON 文件**：完整 canonical 课表文档，可备份、交给 AI 或在本机重新导入。
3. **分享 ICS 文件**：日历应用可识别的 ICS，内嵌同一份 canonical JSON。

取消仍为独立操作。旧 `SDCT1`、canonical JSON、ICS 的**导入**能力全部保留，只是不再提供“分享课表口令”入口。

## 为什么改成云端

纯本地自包含密文对 20 门课约 1.4–1.7 KB，做不到可手输的短码；真正十位以内的短口令必须有一个短期存储与解密点。本轮按用户要求改为云端：

- 客户端把紧凑 canonical JSON 经 HTTPS 上传。
- 服务端用部署主密钥加密后写入文件存储，返回十位随机口令。
- 接收端提交口令，服务端解密并返回课表 JSON；客户端本地校验、预览、确认，不调用 AI。
- 服务端 15 分钟后删除；客户端不依赖本机时钟判断过期。

## 口令格式

- 服务端生成 10 位 Crockford Base32：`0123456789ABCDEFGHJKMNPQRSTVWXYZ`（排除 I、L、O、U）。
- 展示格式：`SD-XXXXX-XXXXX`。
- 输入兼容大小写、空格、连字符、`O→0`、`I/L→1`，也接受不带 `SD-` 的十位原码。
- 服务端只保存口令的 SHA-256 摘要作为文件名；目录列表不能直接得到可用口令。

## 服务端

私有后端 `SleepDown-Server/` 新增：

- `POST /api/v1/short-codes`：请求 `{"payload":"<compact canonical JSON>"}`，限制 32 KiB，必须包含 `courses`；返回 `{"code":"...","expiresAt":...}`。
- `POST /api/v1/short-codes/resolve`（口令在 JSON body，避免代理日志记录）：返回 `{"payload":"...","expiresAt":...}`；不存在或过期统一 404。
- 文件存储 `internal/shortcode/`：目录默认 `数据库目录/short-codes`，权限 0700；记录使用 `EncryptAtRest`（AES-256-GCM，主密钥）加密，读取时由服务端解密。
- TTL 15 分钟；创建时顺带清理，主进程每分钟清理过期记录和超过一小时的临时文件。
- 限流：创建 10 次/分钟/IP，读取 60 次/分钟/IP。
- 不记录口令或课表正文；错误响应沿用统一 envelope。
- 无数据库迁移，现有部署脚本的 schema/数据一致性检查可通过。

## 客户端

- 新增 `feature/importing/CloudShortCode.kt`：
  - `buildPayload` 复用 `CanonicalScheduleJson.encodeCompact`。
  - `create` 上传并解析 `code/expiresAt`。
  - `resolve` 从粘贴文本提取口令并拉取课表。
  - 网络错误、404、429、413、503 均有中文提示。
- `AiManualImportUi.parseDraft` 在本地解析失败后识别云端口令：请求期间显示“解析中…”，成功后进入现有预览确认，失败直接显示原因，不误触发 AI 修复。
- `ScheduleManagerUi` / `SchedulePickerOverlay` / `ScheduleAppUi` 改为三项分享；短口令异步生成，生成期间提示“正在生成短口令…”，完成后打开系统分享器。
- 删除上一版本地 `ShortScheduleCode` 及其测试，避免两套口令格式并存。

## 部署与验证

- 服务端：`go vet ./...`、`go test ./...` 全部通过；Linux amd64 候选 SHA-256 `0b8730b260ceb718abf3a4ef108151c4b1d252b5779a3076e989a7fa4f18bcfe`。本机没有系统 Go，验证使用 `tmp/go-toolchain/go` 便携 Go 1.27.3（忽略目录，不提交）。
- 已按用户要求使用本机阿里云 CLI 执行 `Deploy-Production.ps1 -Execute`：
  - 部署标签 `sleepdown-platform-20261103-195130-44fb29`。
  - 回滚备份 `/var/backups/sleepdown-server/sleepdown-platform-20261103-195130-44fb29`。
  - 远程校验：候选哈希一致、真实数据库副本未变化、schema 仍为 8、服务 active。
- 生产接口实测：`POST /api/v1/short-codes` 创建成功，`POST /api/v1/short-codes/resolve` 返回内容与上传完全一致（含 `SD-XXXXX-XXXXX` 形式），旧 GET 路径 404，错误口令 404；`/healthz` 与 `/readyz` 由 nginx 返回 200。
- 客户端：`compileGithubReleaseKotlin` 通过；`CloudShortCodeTest` 5 项通过（提取/归一化、紧凑载荷、上传与读取、404 提示）。
- 已从当前未提交工作树完整签名打包并安装：`1.2.8_beta2 / 36`，7,876,857 字节，SHA-256 `0d78955519a7863d8a4da060d8f2ce280efe18ab032ed3d788a824fb9a01f9af`；SDK 5038 / PLJ110（Android 17 / SDK 37 / `3B15AE023YL00000`）`install -r` Success，设备 `base.apk` 哈希一致，未清数据、未启动应用。安装包含短口令与 JSON 分享，PR #100 仍只含口令部分。
- 尚未做真机分享面板与跨设备端到端验收。

## 安全与隐私

- 口令是 15 分钟有效的 bearer capability；拿到口令即可读取课表。
- 服务端按用户要求可以解密课表；文件落盘始终是主密钥加密的密文。
- 口令空间 50 bit，配合 15 分钟 TTL 与按 IP 限流；不适合长期或高敏感场景。
- 服务端不记录正文；客户端不把口令写入日志。
- 若后续要端到端加密（服务端不可读），需要改为“口令派生密钥 + 客户端解密”，会失去“云端解密”语义，需单独确认。
- 发布前需要在隐私政策/应用内说明中补充：用户主动分享短口令时课表正文会上传并由服务端在 15 分钟内解密；本轮未修改线上协议或隐私政策。
