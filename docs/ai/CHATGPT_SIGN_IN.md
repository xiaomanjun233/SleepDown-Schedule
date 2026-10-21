# ChatGPT 账号登录（本机预览）

AI 设置中的 ChatGPT 使用官方 Sign in with ChatGPT（SIWC）个人、本机流程。新安装不再展示官方 OpenAI API Key 填写入口；已有 `openai` 配置保留原 ID、接口设置和加密密钥，显示为旧版 API 配置。自定义兼容接口和其他供应商不变。

## 登录与凭据

- 用户点击“使用 ChatGPT 继续”后，在系统浏览器登录并授权。应用不读取浏览器 Cookie，不要求粘贴令牌，不嵌入共享 client secret。
- 每个安装保留随机 host ID。首次注册使用 `dynamic_agent_client`，回调中的已签发 client ID 用于代码交换及以后登录。
- 回调只监听 `127.0.0.1` 随机端口的 `/auth/callback`；使用新 state、nonce、S256 PKCE，严格校验回调、JWKS RS256 签名、issuer、audience、expiry 和账户身份。
- 登录由用户可取消的短时前台服务承载；Activity 旋转或浏览器切到前台不会主动取消。进程被系统杀死后需重新发起，旧回调不能恢复登录。
- host ID、账户注册和凭据以 Android Keystore AES-GCM 加密，原子写入 `noBackupFilesDir`；不进入云备份、课表导出、FileProvider 或诊断日志。
- 续期、旋转 refresh token、退出并发均受序列化与版本控制保护。临时网络错误保留凭据；确认无效的 refresh token 才停止使用。退出尝试官方撤销端点，并说明远端撤销未确认的情况。

## 模型与推理

- 模型来自当前账户的 `GET https://api.openai.com/v1/models`，使用 `models[]` 中 `visibility=list` 的顺序、`display_name` 与 `slug`。不使用 API Key 模型目录，也不硬编码账户权益。
- 每次请求在发送前解析有效 OAuth token，固定发送到 `POST https://api.openai.com/v1/responses`，禁止 HTTP 重定向。OAuth token 不存入供应商配置。
- HTTP 请求固定 `stream=true`、`store=false`，重放所需上下文，函数工具分组到本地 namespace；不发送不支持的预算、采样、持久会话字段，也不访问 Files upload API。
- 只有 `response.completed` 成功终态可以交给后续执行/导入校验。截断、失败、退出后的流和仅有 delta 的断连都不算完成。
- ChatGPT 选中后，未登录、模型不可用、权限不足或用量受限均明确失败；不会自动换到另外的 API Key、付费或免费线路。
- 目前公开 SIWC 模型说明未给出稳定的图像能力字段。仅在目录明确提供能力时启用图像；未声明时保守使用文本，不承诺截图/PDF 图像可用。

## 账号与额度界面

界面显示连接账户、套餐调用授权、模型目录加载/错误状态和最近一次调用结果。官方 SIWC 直接流程目前未公开剩余订阅额度百分比、重置时间或套餐等级查询接口，因此这两项明确显示“暂无法读取”，不伪造进度条，不把响应 token 数或请求速率限制当作订阅余额。

“刷新连接状态”刷新模型与授权状态，不消耗测试推理、不声称查询额度。“ChatGPT 用量”打开官方 [设置 → 用量](https://chatgpt.com/settings/usage)。结构化 `subscription_sharing_*` 错误按真实代码提示；用量受限可能是应用限额，不代表整个套餐用尽，也不能据此猜测恢复时间。

## 验证与发布边界

- 回归覆盖回调污染/重复参数、state/PKCE、JWT、loopback 超时取消、凭据编码、供应商保留与隔离、Responses 请求格式和完成条件。
- 本次不使用真实账户登录、创建 OAuth 授权或读取凭据。Android 浏览器跳转、OEM 后台行为、Keystore 失效与实际 SIWC 登录仍须真机验证；自动化通过不能替代实机验收。
- 仓库采用自定义非商业源码可见许可，不是 OSI 开源许可。官方资料提到个人、本机项目，但未给出本应用的分发审批；此实现不宣称已获批用于面向所有用户的商业或托管产品。
- 不包含第三方 SIWC devkit 源码。本实现依据公开协议独立编写，保持草稿 PR，不合并、不发布。

## 官方依据

- [注册和登录](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)
- [模型和推理](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [预览限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
- [错误与恢复](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)
- [个人本机项目的接入说明](https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt)
