# 云端短口令（2026-11-03）

## 目标与范围

分享面板原来的“分享课表口令”会把可读的完整 canonical JSON 直接发出去。本 PR 将口令入口替换为 **云端短口令**：

- 分享时上传紧凑 canonical JSON，服务端加密保存并返回十位 Crockford Base32。
- 接收端粘贴 `SD-XXXXX-XXXXX`，服务端解密后本地解析、预览、确认。
- 15 分钟有效；需要联网；不调用 AI。
- 删除“分享课表口令”入口；`SDCT1`、canonical JSON 和 ICS 的**导入**兼容不变。

JSON 文件分享是同一轮的后继独立改动，不在本 PR 内；后续 PR 再补。

## 口令格式

- 十位 Crockford Base32：`0123456789ABCDEFGHJKMNPQRSTVWXYZ`（排除 I、L、O、U）。
- 展示：`SD-XXXXX-XXXXX`。
- 输入兼容大小写、空格、连字符、`O→0`、`I/L→1`，也接受十位原码。
- 服务端只保存口令的 SHA-256 摘要作为文件名，目录列表不能直接得到可用口令。

## 服务端（私有后端，已部署）

后端接口不在本仓库；实现与部署记录如下：

- `POST /api/v1/short-codes`：`{"payload":"<compact canonical JSON>"}`，上限 32 KiB，必须包含 `courses`；返回 `{"code":"...","expiresAt":...}`。
- `POST /api/v1/short-codes/resolve`：`{"code":"..."}`，口令在 body，避免代理 access log 记录；返回解密后的 `payload` 与 `expiresAt`。
- 文件存储默认 `数据库目录/short-codes`，权限 0700；记录用部署主密钥 AES-256-GCM 加密，读取时云端解密。
- TTL 15 分钟；创建时顺带清理，主进程每分钟清理；创建 10 次/分钟/IP，读取 60 次/分钟/IP。
- 无数据库迁移；部署脚本的 schema/数据一致性检查通过。

部署与验证：

- 服务端 `go vet ./...`、`go test ./...` 通过；Linux amd64 候选 SHA-256 `0b8730b260ceb718abf3a4ef108151c4b1d252b5779a3076e989a7fa4f18bcfe`。
- 已使用本机阿里云 CLI 部署：标签 `sleepdown-platform-20261103-195130-44fb29`，回滚备份 `/var/backups/sleepdown-server/sleepdown-platform-20261103-195130-44fb29`。
- 生产实测：创建、`SD-XXXXX-XXXXX` 解析、错误口令 404、旧 GET 404、`/healthz`、`/readyz` 均符合预期。

## 客户端改动

- 新增 `feature/importing/CloudShortCode.kt`：上传/读取、口令提取与归一化、中文错误提示。
- `CanonicalScheduleJson.encodeCompact`：短口令使用去掉默认值/空值的同一 canonical 文档。
- `AiManualImportUi.parseDraft`：本地解析失败后识别云端口令；请求期间显示“解析中…”，成功后进入现有预览确认，失败直接显示原因，不误触发 AI 修复。
- `ScheduleManagerUi` / `SchedulePickerOverlay` / `ScheduleAppUi`：口令入口改为异步生成短口令；生成期间提示“正在生成短口令…”。
- 删除可读口令分享入口和 `shareScheduleToken`。

## 验证

- `compileGithubReleaseKotlin`：通过。
- `CloudShortCodeTest`：5 项通过（提取/归一化、紧凑载荷、上传与读取、404 提示）。
- 未安装新 APK、未做真机分享面板与跨设备端到端验收。

## 安全与隐私

- 口令是 15 分钟有效的 bearer capability；拿到口令即可读取课表。
- 服务端按产品要求可以解密课表；落盘始终是主密钥加密的密文。
- 口令空间 50 bit，配合 15 分钟 TTL 与按 IP 限流；不适合长期或高敏感场景。
- 服务端不记录正文；客户端不把口令写入日志。
- 发布前需在隐私政策/应用内说明补充：用户主动分享短口令时课表正文会上传并在 15 分钟内由服务端解密。本 PR 未修改线上协议。
