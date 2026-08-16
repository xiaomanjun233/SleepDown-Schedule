# 1.1.5 之后的技术债

本文件只记录审计和 Phase B 设计中发现、但不应在迁移桥版本中顺手重构的事项。1.1.5 只修复会造成数据损失、备份/恢复错误、覆盖升级失败、crash 或阻塞发布的问题。

## TD-01：Room Entity 与长期备份 DTO 分离

当前 Room entity 适合本地存储，不适合作为跨包协议。1.1.5 已通过独立 DTO、stable ID 和 mapper/restore boundary 解决；2.0 可以进一步把协议 schema、版本兼容和 domain model 分层，避免每次 Room 改列都触碰备份协议。

## TD-02：Agent DAO 的全量读取边界

`AgentDao` 原先主要提供按日期 Flow、近期消息和清理方法；1.1.5 已补齐全量 session/message 读写边界，并在备份 mapper 中处理 attachment marker。更完整的 Agent history repository、写入一致性和查询模型仍留给 2.0。

## TD-03：SharedPreferences 缺少统一 schema

AI、Agent、图标、登录历史、更新和提醒各自拥有独立 preferences 文件。1.1.5 只定义安全白名单 DTO，不复制原始 XML；2.0 可考虑迁移到带 schema/version 的统一存储，但必须提供逐文件兼容迁移，不能直接清空旧 prefs。

## TD-04：WebView 登录态和教务历史边界

WebView Cookie/DOM storage 与应用加密登录历史不是同一份 source of truth。1.1.5 明确不迁移 Cookie，并可在未来通过去 Cookie 元数据辅助重新登录；2.0 可设计明确的学校连接 profile，但不能把登录凭据放进普通备份。

## TD-05：content URI 失败后的用户体验

壁纸复制到 `filesDir` 失败时，历史配置可能仍保留外部 `content://`。1.1.5 导出将其转为 missing asset warning，恢复时对 present bytes 生成新的应用私有 URI；2.0 可进一步让日常壁纸保存操作也成为“复制成功后才提交配置”的单一事务，彻底消除外部 URI 依赖。

## TD-06：Widget instance 与外观配置分离

Android `appWidgetId` 属于系统实例，不能跨包/跨设备迁移；当前 Room 又保存了 default 和 instance appearance。1.1.5 迁移逻辑外观并提示重新添加组件；2.0 可设计 widget profile 与系统实例生命周期的更清晰绑定。

## TD-07：跨 Room / 文件 / SharedPreferences 的提交协议

1.1.5 已采用 staging payload/marker、Room transaction 和可重入 resume，明确承认文件系统与 SQLite 不具备严格 ACID。2.0 可以把 restore journal、原子 preferences snapshot 和资源引用 GC 做成独立事务框架，减少 crash-window 复杂度。

## TD-08：ScheduleRepository snapshot 的职责边界

当前 active snapshot、manager snapshot 和备份全量读取需求不同。1.1.5 不复用 active `AppState` 做导出；2.0 可将 UI snapshot、通知 snapshot、widget snapshot 和 backup snapshot 变成明确的 domain query，减少空字段和误用风险。

## TD-09：AI import history 的大文本/图片布局

当前历史 context JSON 可以包含 screenshot base64，虽然条目和 retention 有上限，仍不适合长期大型资源。1.1.5 格式把 context 与截图纳入 asset manifest 设计；2.0 可把历史记录、页面文本和图片做成独立可索引资源，并加入压缩/分页策略。

## TD-10：Room 历史 migration 图和 repair 代码的复杂度

v1～v34 的 migration 与开库前 structural repair 是真实用户兼容基础设施，1.1.5 不清理它们。2.0 可以在有完整用户数据测试和迁移工具后整理历史 schema，但必须保留从所有支持版本升级的路径或提供一次性、可验证的迁移器。

## TD-11：发布签名 fallback

现有发布配置在缺少四个 `sleepdown.release*` 属性时回退 debug signing；官方 v1.1.4 的实际证书已核验，但 1.1.5 不能在没有私钥决策时擅自更换。2.0/发布基础设施阶段应建立明确的 release signing CI、证书指纹门禁和无私钥本地构建策略。

## TD-12：Android Auto Backup 与 SleepDown backup 的并存说明

Manifest 当前允许系统 Auto Backup，但它不是跨 applicationId 的稳定迁移协议。2.0 可进一步评估 backup rules，避免系统备份和用户 `.sleepdown` 在隐私、文件范围和恢复语义上产生误解；不能依赖系统备份替代显式导出。

以上事项若在后续测试中变成数据损坏、覆盖升级或发布 blocker，应从“技术债”升级为当前版本的修复项，并在工作日志中记录证据。
