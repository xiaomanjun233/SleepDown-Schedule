# 课程字色、超级岛与湖南邮电教务测试包

## 范围与依据

本轮按用户要求修正首页课程字色、补齐超级岛 R8 保留规则，并准备湖南邮电职业技术学院教务适配的本地 Debug 包。学校适配尚未提交 PR，也未上传远端。

- Nexio 参考版本：[`f2c4ab6`](https://github.com/HaoZai000/NexioSchedule/tree/f2c4ab6def84b5be8a5969ce6e46dcabb2a1cac4)，核对 `CourseCard.kt` 与 `app/proguard-rules.pro`。
- 拾光基线：`pending` 的 `177cf963a6cff54be3078f6d6371ea43aed93f10`。按 [仓库 README](https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/pending/README.md) 登记根索引、学校目录和适配元数据，使用 [v2 桥接协议](https://github.com/ShiGuangSchedule/shiguangschedule/wiki/如何适配教务v2)。将来验证通过后的 PR 目标应为 `pending`。
- [湖南邮电教务处](https://jwc.hnptc.edu.cn/) 的官方导航指向 `http://jx.hnxyjf.com:10081`；公开学生登录页 `/jsxsd/` 的表单提交到 `/jsxsd/xk/LoginToXk`。未访问任何学生账号或真实课表。

## 课程文字

日视图、时间线和周视图仍共用 `homeCourseTextColor`。参考 Nexio 保留色相、降低饱和度和提高亮度的思路，使用独立实现的 HSV 调整：壁纸下保留课程色相，按页面前景方向降低饱和度并设置亮度下限；无壁纸保留课程原色。停课等弱化状态和关闭彩色文字时仍使用既有页面前景。

旧算法把浅色页面上的课程字色统一压暗，且软阴影方向跟随页面黑白文字；彩色字与页面前景方向不一致时，阴影可能选错颜色。现在以课程文字自身的相对亮度决定黑/白阴影，分界为两者对比度相等时的约 `0.1791`，阴影强度继续由字形区域的真实卡片采样决定。原有滑动冻结、停止后采样和阴影渐变继续生效。

主要文件：`CourseTextContrast.kt`、`CourseCardText.kt`、`HomeScheduleUi.kt`、`WeekScheduleUi.kt`。

## 超级岛

在 `app/proguard-rules.pro` 保留 `XiaomiSuperIsland`、Shizuku / Root bridge、恢复网络的 receiver 及其内部类。规则覆盖本项目实际存在的类；系统框架反射目标不属于应用 R8 的重命名范围。修正超级岛“查看课表”Intent 的 Activity 类名，支持带 `.debug` 后缀的包。

## 学校适配与隔离

适配保留在独立本地仓库 `D:\Android studio\_external\shiguang-warehouse-hnptc` 的 `codex/hnptc-adapter` 分支，本地提交 `bd5860e7b9881c5ce02fe02b0e1858681b77345d`。变更为：

- `index/root_index.yaml`：登记 `HNPTC`。
- `resources/HNPTC/adapters.yaml`：`HNPTC_01`、官方学生端登录地址及维护信息。
- `resources/HNPTC/hnptc_01.js`：读取教务学期列表、请求整学期 HTML、解析为拾光标准课程 JSON。

按用户要求直接派生自拾光 `resources/BTBU/btbu.js`（原作者 `lztttt`），保留原始来源说明和 MIT 许可。学期选择、隐藏表单 + iframe 请求、课程字段解析、周次转换、合并去重和桥接流程继续使用上游实现。12 个核心函数经逐字比较确认一致（忽略文件换行符差异），没有交付自行重写的解析器。

学校差异只有入口与元数据，以及去掉北工商固化的节次模式 ID、5 分钟课间和 13 节作息表。节次模式从当前教务页读取；开学日期沿用上游输入/跳过流程。取消、未登录和原生保存失败均不发完成信号。页面格式与真实课程仍待用户登录验证，不能把上游学校的样本验证当作湘邮实测。

本地构建使用上游 `build_protobuf_index` 生成仅含该学校的 v2 索引，连同脚本和 MIT 许可放入临时资产目录。构建参数 `sleepdown.eduAdapterTestAssets` 只加入 Debug source set，且仅 Debug 启用 `SLEEPDOWN_LOCAL_EDU_TEST`。测试学校显示“本地测试”，读取自己的资产目录，不写入官方云端索引与脚本缓存。

Release 始终关闭本地测试开关。原来的云端索引、脚本下载地址、缓存更新和内置回退继续工作；未向正式内置学校索引或自动刷新白名单添加该学校。

Debug 包名为 `com.xiaomanjun.sleepdownschedule.debug`，桌面名称为“SleepDown 测试版”。ColorOS Provider authority 和签名权限按 applicationId 隔离，正式包对应值不变。Debug 可以与正式版共存，数据独立。

## 验证与交付

- 拾光 YAML Schema 校验和单学校 Protobuf 生成通过。
- 最终版本通过 Edge 无头浏览器中的 8 项检查：12 个上游核心函数一致性、真实隐藏表单 POST 与整学期参数、单双周和多节连堂、隐藏副本去重、未提供节次模式时不借用其他学校 ID、选择取消、登录失效、保存拒绝、手动开学日期和不写入来源学校作息。部分检查包含多个断言；测试均为合成样本，不代表真实账号联调已通过。早期自写解析器的测试结果不作为本版验收依据。
- `CourseTextContrastTest`：12 项通过。第一次运行发现色相比值断言未考虑 8 位 sRGB 量化，按实际量化误差修正容差后通过。
- 完整 `assembleGithubRelease` 与 `assembleGithubDebug` 构建通过；学校脚本替换后再次 `assembleGithubDebug` 成功（50 秒）。Debug 和 Release 均通过 APK v2 签名验证。
- Release `mapping.txt` 已确认超级岛、两种 bridge 和恢复 receiver 的类名保留。正式包名、版本、Provider authority 和权限名保持当前正式版身份。
- 解包确认 Debug 只额外包含本地学校索引、对应 JS 和许可，JS 与上游派生工作树逐字节一致；Release 不包含任何测试资产，DEX 中也没有 `edu_adapter_test`。两个 APK 的正式内置 `school_index.pb` 均与源码原文件一致。
- 当前 `adb devices -l` 没有连接设备。本轮交付 APK，由用户登录学校账号验收；未声称完成实机视觉或超级岛上岛验证。

测试方法：打开“SleepDown 测试版”，在教务导入中搜索“湖南邮电职业技术学院”，选择带“本地测试”的入口，登录学生端后点击导入，选择学期并检查预览中的课程、周次、节次、教师和地点。开学日期与作息按学校实际安排核对后再确认。

交付文件：`tmp/hnptc-delivery/SleepDown-1.2.6-hnptc-debug.apk`，31,859,221 字节，版本 `1.2.6-edu-test` / 33。SHA-256：`266c58967e428eebc8ca5c10e08c8a5c6a1c7a2b2d3005f693e1dc9f120f6400`。

验证使用的 Release SHA-256：`27b41a3d99e04051c93300bd3f39dd7e5c0cbf8e5fa025aae61107354cd11c73`。本轮没有安装、推送、创建 PR 或发布版本。

## 强智接口案例复查

用户追问接口和自动刷新后，对照了拾光 `pending` / `main` 的实际脚本；两分支在本次核对时的 `resources` 内容一致。以下都是已有上游实现：

| 案例 | 获取方式 | 返回内容与参考价值 |
| --- | --- | --- |
| [曲阜师范大学 QFNU](https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/177cf963a6cff54be3078f6d6371ea43aed93f10/resources/QFNU/qfnu_01.js#L52) | `fetch` POST `/jsxsd/xskb/xskb_list.do`，参数含学期、节次模式和空周过滤；必要时 GET | HTML 整学期课表；同时读取学期列表与教学周历，最适合优先参考其直接请求流程 |
| [湖南信息职业技术学院 HNIU](https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/177cf963a6cff54be3078f6d6371ea43aed93f10/resources/HNIU/hniu_01.js#L242) | `fetch` POST 同一 `xskb_list.do`，携带登录 Cookie 和学期 | HTML 课表，证明相近院校已有直接请求写法 |
| [湖南商务职业技术学院 HNVCC](https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/177cf963a6cff54be3078f6d6371ea43aed93f10/resources/HNVCC/HNVCC_01.js#L93) | GET `/jsxsd/framework/mainV_index_loadkb.htmlx?rq=all&xnxqid=…&xswk=false` | HTML 课表片段，使用 `rq=all` 请求全部课程；重庆工商职业学院 CQTBI 也用同一路径并附加节次模式 |
| [闽南科技学院 MKU](https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/177cf963a6cff54be3078f6d6371ea43aed93f10/resources/MKU/mku.js#L244) | 课表仍请求 `xskb_list.do`；`jxzlzc_xnxq_ajax` 返回 JSON | JSON 只包含学期周数，不能因此认为完整课程来自 JSON 接口 |

本次检查到的强智课表请求主要返回 HTML，未发现可直接套给湘邮的整学期课程 JSON 案例。HTML 是响应格式，不妨碍后台重复请求和自动解析。先前把自动刷新能力与 JSON 响应绑定的解释过于绝对：当前限制来自 SleepDown 的候选适配器识别规则和本地测试脚本尚未接入自动刷新，而不是 HTML 本身。

`ShiguangApiAdapterCatalog.isLikelyApiAdapter` 当前以“有网络调用且有 JSON 解析”作正向判断。这会漏掉完整的强智 HTML 课表请求，也可能把 MKU 的周数 JSON 当作课程接口证据。后续应依据完整课表获取链路、登录失效处理及实际验证判断能力，不能只放宽一个字符串条件。

推荐下一步优先复用 QFNU 的请求和学期/周历读取流程，在湘邮登录后的响应上验证；保留上游解析方法和预览确认。当前已交付 APK 仍使用前述 BTBU 派生脚本，本次仅核对案例和记录结论，未改包或宣称已启用自动刷新。
