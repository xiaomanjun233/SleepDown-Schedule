# 西安建筑科技大学（XAUAT）教务适配：问题分析与解决方案

日期：2026-11-16。范围：`app/src/main/assets/shiguang_warehouse-main/resources/XAUAT/xauat.js`（上游 `XAUAT_01`，树维教务平台，本科）。
§1～§8 是修改前的分析、方案和验证结果；§9 记录实施结果、测试中遇到的问题和二次修改须知。分析阶段用的临时原型已被正式实现取代，可重复运行的测试是 `scripts/tests/xauat-adapter.cjs`。

## 1. 结论

1. 三个现象同出一个根因：适配器没有使用教务系统已经给出的**周次**与**学期起始日**，而是用「最早一节课所在日期的周一」反推开学日。本学期学生第 1～3 周没有课（最早一节在 9 月 23 日，实际是第 4 周），推出的开学日是 9 月 21 日，比真实开学日 8 月 30 日晚 3 周。此后所有周次整体偏小 3，周日课程偏小 4。
2. 作息不对是因为适配器**从未提交作息**：只调用 `saveCourseConfig` 和 `saveImportedCourses`，没有 `savePresetTimeSlots`，并把每门课都标为 `isCustomTime`。
3. 教室不对有两层：周次错位会让「第几周在哪间教室」整体错位；同时适配器按 `lesson|星期|起止时间` 分组，只取分组内**第一条**的教室和老师，所有周共用一个值。
4. 方案选择：**修改现有适配器**，不换用 uniapp。用测试账号逐接口核对后，uniapp 课表和旧教务页面读的是同一份排课数据（同为 197 条记录，内容一致），uniapp 没有更准确的数据；反而需要额外取 `idToken`，在拾光 bridge 的脚本环境里取不到（见 §5.2）。
5. 修复所需数据都已在现有会话可访问的接口里：周次 `weekIndex`、开学日与周起始 `semester/get`、作息 `timetable-layout`。原型对真实数据通过全部检查，见 §7。

## 2. 事实来源与复现方法

用测试账号走**统一身份认证**登录（不是教务系统本地账号；本地账号接口 `/student/login` 对该账号返回「用户名或密码错误」，之后要求验证码）：

1. 打开 `https://swjw.xauat.edu.cn/student/sso/login`，302 到 `authserver.xauat.edu.cn/authserver/login?service=…`。
2. 登录页是金智 authserver：先看 `checkNeedCaptcha.htl`（本次 `isNeed:false`），表单字段 `username`、`password`（`pwdEncryptSalt` 作 AES-CBC 密钥加密：64 位随机前缀 + 明文密码，随机 16 位 IV，Base64）、`execution`、`_eventId=submit`、`cllt=userNameLogin`、`dllt=generalLogin`。
3. 提交返回 302 到 `…/student/sso/login?ticket=ST-…`，跟随后 `/student/for-std/course-table` 返回 200，带 `SESSION` Cookie。

uniapp 路线单独验证：`https://swjw.xauat.edu.cn/eams-door/jwt/cas/login?redirect_uri=…/uniapp/index.html` → authserver → 回跳 `…/uniapp/index.html?state=null&idToken=<JWT>`。uniapp「课表」页只是 iframe 到 `/eams-student-course-table-app/index.html?idToken=…`，该 SPA 调用 `https://swjw.xauat.edu.cn/eams-micro-server/api/v1`，请求头带 `X-Id-Token`、`Authorization`、`userToken`。

账号、密码不写入仓库、日志或本文。

## 3. 根因（对照 `xauat.js` 行号）

| 位置 | 现状 | 后果 |
| --- | --- | --- |
| `deriveSemesterStartDate`（81～89 行） | 取 `scheduleList` 最早日期，回退到所在周的**周一**；注释已承认「假设最早上课日在第一周」 | 本学期推出 2026‑09‑21；真实为 2026‑08‑30（周日）。周一起算也忽略了西建大周日为一周之首 |
| `convertCourses`（102～133 行） | `week = floor((date − 推算开学日)/7) + 1`，完全不看接口给的 `weekIndex` | 197 条记录**全部**与 `weekIndex` 不一致：190 条偏 −3，7 条周日记录偏 −4 |
| `runImportFlow`（231 行） | `saveConfig({ semesterStartDate, semesterTotalWeeks })`，没有 `firstDayOfWeek` | App 侧 `weekStartsOnSunday` 保持 false（`ShiguangImportSession.kt:348`） |
| 同上 | 没有调用 `savePresetTimeSlots`；每门课 `isCustomTime: true` 并写自定义起止时间 | 课表作息不会更新为西建大作息；每门课都当「特殊时间」处理，周视图出现偏离网格标记 |
| `convertCourses` 分组键 | `lessonId|day|startTime|endTime`，`position`、`teacher` 取分组第一条 | 本学期 25 组中 4 组存在多个教室；按组取首条后，23 条记录的教室、3 条记录的老师与实际不符 |
| `clampWeeks` / `LIMITS` | 周数夹在 16～25 | 本学期教务周历是 26 周；App 自身允许 1～60 |
| `chooseSemester` | 默认选中第 0 项 | 学期下拉按时间倒序，第 0 项是 2027‑2028‑1，不是当前学期 |

### 用户手动设置后「后面的课往前挪」的原因

在 App 里手动改成「周日为首日」「8 月 30 日开学」只改了日期换算；课程里保存的周次仍是适配器按 9 月 21 日算出的（偏小 3）。同一门课于是在日历上整体提前 3 周，看起来就是「后面的课程向前位移」。问题在导入数据，不在 App 的周计算。

App 的周计算本身是对的：`scheduleCalendarWeekStart` 在 `weekStartsOnSunday` 下用 `weekday % 7` 作偏移，周日偏移为 0。原型按同一公式把 `(周, 星期)` 还原成日期，与教务原始日期逐条一致（§7）。

## 4. 接口事实（测试账号，2026‑2027‑1，学期 ID 361）

| 项 | 来源 | 实测 |
| --- | --- | --- |
| 学期起止、周起始 | `GET /student/ws/semester/get/361` | `startDate=2026-08-30`（周日）、`endDate=2027-02-27`、`weekStartOnSunday=true`。其他学期同样：301→2025‑08‑31、341→2026‑03‑01、381→2026‑07‑19、421→2027‑02‑28，全是周日 |
| 周次 | `POST /student/ws/schedule-table/datum` 的 `scheduleList[].weekIndex` | 每条都有。用 `startDate` 按周日起算 `floor((date−start)/7)+1` 与 `weekIndex` **197/197 吻合**，周日课程同样吻合 |
| 星期 | `scheduleList[].weekday` | 1～7，周日为 7，与日期的 ISO 星期 197/197 一致 |
| 作息 | `POST /student/ws/schedule-table/timetable-layout`，请求 `{timeTableLayoutId}`；ID 来自 `course-table/get-data` 的 `timeTableLayoutId`（本次 21，草堂校区） | 12 节，每节 45 分钟：08:30‑09:15、09:20‑10:05、10:25‑11:10、11:15‑12:00、12:10‑12:55、13:00‑13:45、14:00‑14:45、14:50‑15:35、15:45‑16:30、16:35‑17:20、19:30‑20:15、20:20‑21:05 |
| 节次对应 | `scheduleList[].startTime/endTime`（如 830、1005） | 全部 197 条的起点正好是某节起点、终点正好是某节终点，可无损换成 `startSection/endSection`。如 14:00‑15:35 = 7～8 节，11:15‑13:45 = 4～6 节 |
| 教室 | `scheduleList[].room.nameZh`，可能为 `null`，另有 `customPlace` | 同一课程同一星期同一节次，不同周教室不同（例：大学英语 1 周一 3～4 节在 13‑313 与 8‑309‑310 机房之间切换，并有一周代课老师不同）；周六补课一条无教室 |
| 并行教室 | 同上 | 实验课「C语言程序设计实验」周三 9～10 节同一周有 2 个教室、周四 7～8 节同一周有 3 个教室同时出现（同一课程的 5 个行政班各占一间） |
| 当前学期 | 课表页 HTML | `<option selected="selected" value="361">` |

### uniapp 与旧接口对比

| 项 | 旧接口（适配器在用） | uniapp（micro‑server） |
| --- | --- | --- |
| 课表条数 | 197 | 197，逐条同日期、同时间、同教室、同老师 |
| 周次 | `weekIndex` | `weekIndex`，UI 直接按它分周 |
| 节次 | 仅时间 | 多了 `startUnit/endUnit`；缺失时前端也用 `all-course-units-map` 按时间推算 |
| 开学日 | `semester/get` | `current-semester`，同样是 2026‑08‑30、周日起算 |
| 鉴权 | 教务 `SESSION` Cookie | 仅 `idToken`；带旧会话 Cookie 返回 401 |

差别只有一处：旧接口多返回 1 个无排课的「军事技能」教学班（`scheduleState=NOT_SCHEDULED`），uniapp 不显示。这不影响课表。

并行教室问题两个来源**都存在**。uniapp 的课表页把同一时段的多条记录合并成一张卡片；它并不知道学生在哪个行政班。接口里 `students` 为空，`scheduleGroups` 只有一个默认组，无法确定学生自己的那一间。

## 5. 方案评估

### 5.1 方案 A：修复现有适配器（推荐）

改用接口提供的周次、开学日和周起始，补提交作息，教室和老师按周分组。

- 数据完整：上表三项都能在现有 `/student/` 会话内取到，且同域，不涉及 CORS 和额外登录。
- 范围小：只改 `xauat.js`，App 侧不需要任何改动，`firstDayOfWeek=7`、`savePresetTimeSlots`、节次课程在拾光会话里都已受支持。
- 风险低：不引入新的登录路径；保留一致性校验，数据不一致时直接报错而不是导入错误数据。
- 局限：并行教室仍无法精确到学生本人的一间，只能合并显示（见 §6.4）。

### 5.2 方案 B：改用 uniapp / micro‑server

不推荐，理由都有实测依据：

1. 数据没有更准。同账号逐条对比 197/197 相同，周次、教室并行、节次推算逻辑与旧接口一致。「官网有时候不准」在本学期数据里是适配器造成的，不是接口造成的。
2. 在适配器脚本里拿不到 `idToken`。micro‑server 只认 `idToken`，旧会话 Cookie 返回 401。取 token 必须走 `eams-door/jwt/cas/login` → authserver 的跨域重定向；实测 authserver 的 302 响应没有 `Access-Control-Allow-Origin`，按 CORS 规则脚本里的 `fetch` 会被拦截（[INFERENCE]：未在浏览器里实测）。要取 token 就得整页导航到 uniapp，脚本上下文随之销毁，还要重新登录或依赖 authserver 的 SSO Cookie。拾光 bridge 的适配脚本是在当前教务页面里运行的 IIFE，不适合这种多跳流程。
3. token 有效期约 8 小时，需要额外错误处理；而旧会话与现有「请先登录教务系统」的说明一致。
4. 要多维护一套登录与接口协议。AGENTS.md 要求教务导入复用拾光官方 adapter，不重复维护学校协议。

可保留的价值：uniapp 的 `startUnit/endUnit` 与 `all-course-units-map` 说明教务官方也是按「时间→节次」换算，我们的换算与它一致。

### 5.3 方案 C：在 App 侧事后纠正周次

适配器继续输出偏移周次，由 App 导入时再改。不推荐：不同学校规则混在 App 里，且依赖「偏移恰好为整数周」的猜测；根因在适配器取数。

### 5.4 方案 D：AI 导入

可作为临时手段，但需要截图或文件，同样要靠模型猜周次，不能保证准确，不作为主路径。

### 比较

| 方案 | 周次/周日 | 作息 | 按周教室 | 登录与鉴权 | 改动面 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| A 修适配器 | 正确（服务端 weekIndex） | 正确 | 正确（并行教室合并） | 沿用现有会话 | 单文件 | 采用 |
| B uniapp | 同 A | 同 A（需读另一接口） | 同 A | 需 idToken，脚本取不到 | 新协议 | 不采用 |
| C App 修正 | 靠猜 | 不涉及 | 不涉及 | – | App 侧 | 不采用 |
| D AI | 不保证 | 不保证 | 不保证 | – | – | 仅备用 |

## 6. 推荐设计（方案 A）

### 6.1 取数流程

在现有流程上增加两个请求，其余沿用（登录提示、学期选择、get‑data、datum）：

1. 解析课表页 HTML，学期下拉默认选中 `selected` 的那一项，找不到再用第 0 项（修正 `chooseSemester` 默认值）。
2. `get-data` 读取 `lessonIds`、`timeTableLayoutId`。
3. `datum` 读取 `lessonList`、`scheduleList`（保持现有请求格式）。
4. 并行请求 `semester/get/{id}`（得 `startDate`、`endDate`、`weekStartOnSunday`）与 `timetable-layout`（得 `courseUnitList`）。这两项是**必需**的：取不到就报错，不再默默用推算值。

### 6.2 转换规则

- 周次：直接用 `weekIndex`。不要再用日期差计算。`weekday` 直接作为 `day`（周日为 7）。
- 配置：`semesterStartDate = semester.startDate`，`firstDayOfWeek = weekStartOnSunday ? 7 : 1`，`semesterTotalWeeks` 取课程最大周次（保持现有「优先课程实际最大周次」语义，下限 16，上限放宽到 App 允许的 60，不再夹在 25）。
- 作息：`savePresetTimeSlots`，由 `courseUnitList` 生成 `{number: indexNo, startTime: "HH:MM", endTime: "HH:MM"}`，在 `saveImportedCourses` 之前提交。
- 节次：`startSection = 起点等于 startTime 的节`、`endSection = 终点等于 endTime 的节`，用 `isCustomTime:false`。找不到对应节或 `start > end` 的记录回退为 `isCustomTime:true` 并带真实起止时间，不丢课。
- 分组：先按 `(lessonId, weekday, startTime, endTime, weekIndex)` 合并成「单元格」，同一单元格的多个教室、多个老师各自**去重、排序后**用 `/`、`,` 连接；再按 `(lessonId, weekday, startTime, endTime, 教室串, 老师串)` 汇总周次。这样每一周只落在一张卡上，且教室、老师逐周正确。教室取 `room.nameZh`，缺失用 `customPlace`，仍无则留空，不再写「未知地点」。
- 一致性校验：对每条记录计算 `date − ((weekIndex−1)×7 + 周内偏移)`，必须都等于 `semester.startDate`（周内偏移在周日起算时为 `weekday % 7`，否则为 `weekday − 1`），且 `weekday` 必须等于日期的星期。任一不一致就中止并提示，不导入。

### 6.3 核心代码（原型，已通过 §7 的测试）

```js
const offsetInWeek = (weekday, sundayFirst) => (sundayFirst ? weekday % 7 : weekday - 1);

function weekOneStart(item, sundayFirst) {
  const ms = utc(item.date) - ((item.weekIndex - 1) * 7 + offsetInWeek(item.weekday, sundayFirst)) * MS_PER_DAY;
  return new Date(ms).toISOString().slice(0, 10);
}

function convert({ lessonList, scheduleList, semester, courseUnitList }) {
  const sundayFirst = semester.weekStartOnSunday === true;
  const startDate = semester.startDate;
  if (!startDate) throw new Error("无法获取学期开始日期");
  for (const it of scheduleList) {                       // 一致性校验
    if (typeof it.weekIndex !== "number") throw new Error("缺少周次");
    if (weekOneStart(it, sundayFirst) !== startDate) throw new Error("周次与学期起始日不一致");
    if (it.weekday !== isoWeekday(it.date)) throw new Error("星期与日期不一致");
  }
  const unitByStart = new Map(courseUnitList.map((u) => [u.startTime, u.indexNo]));
  const unitByEnd = new Map(courseUnitList.map((u) => [u.endTime, u.indexNo]));
  // 1) 单元格：(lesson, day, time, week) 内多教室/多老师合并
  // 2) 组：(lesson, day, time, 教室串, 老师串) 汇总 weeks
  // 3) 节次对齐 -> startSection/endSection，否则 isCustomTime
  return { config: { semesterStartDate: startDate, semesterTotalWeeks, firstDayOfWeek: sundayFirst ? 7 : 1 },
           timeSlots, courses };
}
```

`config`、`timeSlots`、`courses` 依次交给 `saveCourseConfig`、`savePresetTimeSlots`、`saveImportedCourses`。排序用默认字符串排序，不用 `localeCompare`，避免不同 WebView 环境顺序不同。

### 6.4 边界情况与取舍

| 情况 | 处理 |
| --- | --- |
| 周六/周日补课 | 与普通记录同一路径。本学期周六 5 条、周日 7 条记录，周次均与 `weekIndex` 一致 |
| 同一课程同一时间多个教室（实验分班） | 合并成「教室 A/教室 B/教室 C」，每周一张卡。接口无法确定学生所在行政班，**不猜**；用户可在课程编辑里改成自己的教室。可在导入完成提示中说明 |
| 某周代课老师或换教室 | 拆成多条课程，各带自己的周次，每周只出现一张卡 |
| 无教室（周六补课一条） | 地点留空 |
| 无排课的教学班（如军事技能） | 没有 `scheduleList`，不导入，与现有行为一致 |
| 非草堂校区作息 | `timeTableLayoutId` 来自 `get-data`，随学生实际使用的作息；回退到真实时间的记录保证不丢课。已知 `all-course-units-map` 里还有 ID 为 1 的另一套作息（08:00 起） |
| 起始日不是周日的学期 | `weekStartOnSunday=false` 时 `firstDayOfWeek=1`，已用合成学期验证 |

## 7. 验证结果

在 Bun 下用测试账号 2026‑2027‑1 的真实响应运行原型和测试（`datum` 197 条、12 个教学班）：

| 检查 | 结果 |
| --- | --- |
| 配置 | `semesterStartDate=2026-08-30`、`firstDayOfWeek=7`、`semesterTotalWeeks=18`（最大课程周 18） |
| 作息 | 12 节，递增、互不重叠，生成 `HH:MM` |
| 节次对齐 | 所有记录均为节次课程，无一回退到自定义时间 |
| App 校验规则（按 `ShiguangImportSession` 移植）：总周数 1～60、`day` 1～7、`weeks` 为正、节次存在于作息 | 通过 |
| 日期往返：用 App 公式 `scheduleCalendarWeekStart + (周−1)×7 + 周内偏移` 把 30 条课程的每个 `(周, 星期)` 还原成日期，与教务原始 179 个「课程‑日期‑节次」组合比较 | 缺失 0、多出 0 |
| 逐周教室、老师：179 个单元格逐一与教务原始记录比对 | 0 处不一致 |
| 并行教室实验课 | 合并成周三、周四 2 行，周次 11～16，各列出全部教室 |
| 大学英语 1 周一 | 拆为 3 行：13‑313 卫娟（9 周）、8‑309‑310 赵兴（第 9 周）、8‑309‑310 卫娟（第 12、15、18 周），各周与教务一致 |
| 当前适配器逻辑对同一数据 | 推算开学日 2026‑09‑21，**197/197 周次错误**，4 组存在被首条覆盖的多教室 |
| 合成周一起算学期（开学日 2026‑08‑31、`weekStartOnSunday=false`） | `firstDayOfWeek=1`，往返 179/179 一致 |
| 一致性校验 | 把 `startDate` 故意改成 2026‑09‑06，校验抛出「周次与学期起始日不一致」 |
| 不在作息网格内的时间（合成 19:00‑20:00） | 回退为 `isCustomTime` 并保留真实时间 |

测试中暴露过一个实现缺陷：接口对同一单元格的多教室顺序逐周不同，直接拼接会让实验课拆成 7 行。排序后收敛为 2 行，已写入 §6.2 的规则。

### 未验证与限制

- 没有运行 Kotlin 测试。App 一侧只做了读码核对和规则移植；适配器输出能否被 `ShiguangImportSession` 接受，由 §9.5 所列的真机预览确认。
- 只有一个学生账号、一个有数据的学期（新生，大一上）。没有验证：雁塔校区作息（ID 1）、同一学期多套作息、研究生/其他 `bizTypeId`、教师课表。
- 学生本人所在实验分班的教室无法由接口确定。
- 学期起始日与 `weekIndex` 一致性是本学期的实测；一致性校验会在其他学期不成立时报错，而不是静默导入。

## 8. 实施计划（修改前）

原计划共 7 步，实际完成情况见 §9.1。其中「Kotlin 回归测试」改为 Node 测试（理由见 §9.1），其余按计划执行。

## 9. 实施记录与二次修改须知

### 9.1 已完成的修改

| 文件 | 内容 |
| --- | --- |
| `app/src/main/assets/shiguang_warehouse-main/resources/XAUAT/xauat.js` | 按 §6 重写：周次取 `weekIndex`、开学日与周起始取 `semester/get`、作息取 `timetable-layout`；删除 `deriveSemesterStartDate`、`calculateTotalWeeks`、`clampWeeks` 与 `fetchSemesterInfo` 的静默降级 |
| `scripts/tests/xauat-adapter.cjs` | 在 `vm` 中运行**真实**的 `xauat.js`，用模拟的 `fetch`、`DOMParser` 和 Bridge 喂入夹具 |
| `scripts/fixtures/xauat-2026-2027-1.json` | 2026‑2027‑1 课表的脱敏快照：教师改为「教师A…」、班级改为「示例班」，只留适配器用到的字段 |

没有写 Kotlin 测试：本机环境没有现成的 Gradle 测试条件，而适配器的输入输出完全由 JS 决定，Node 测试能直接覆盖它。适配器输出能否被 App 接受要看真机预览（§9.5）。

运行方式（无网络、无账号）：

```bash
node scripts/tests/xauat-adapter.cjs                  # 测试仓库里的适配器
node scripts/tests/xauat-adapter.cjs /path/to/old.js  # 对照测试另一份脚本
```

对修改前的脚本运行会在第一条断言（默认学期应为页面选中的 2026‑2027‑1，旧脚本选中 2027‑2028‑1）处失败。

### 9.2 适配器依赖的接口字段（教务升级后先查这里）

| 接口 | 字段 | 用途 | 缺失时 |
| --- | --- | --- | --- |
| 课表页 HTML | `#allSemesters option[value]`，带 `selected` 的是当前学期 | 学期列表和默认项 | 没有 `selected` 就选第 0 项；找不到下拉框提示未登录 |
| `get-data` | `lessonIds`、`timeTableLayoutId` | 教学班和作息 ID | 没有 `lessonIds` 报错；`timeTableLayoutId` 为空则不提交作息，课程退回真实时间 |
| `semester/get/{id}` | `startDate`（`YYYY-MM-DD`）、`weekStartOnSunday` | 开学日和周起始 | 取不到 `startDate` 直接中止 |
| `schedule-table/datum` | `scheduleList[].date/weekday/weekIndex/startTime/endTime/lessonId/room.nameZh/customPlace/personName`；`lessonList[].id/courseName/teacherAssignmentList[].name` | 课程内容 | 缺日期或时间的行跳过；`weekIndex` 缺失时按开学日换算 |
| `schedule-table/timetable-layout` | `result.courseUnitList[].indexNo/startTime/endTime` | 作息和节次对齐 | 请求失败中止；时间不合法的节忽略 |

`startTime`、`endTime` 是 `HHMM` 整数（如 `830`）；`weekday` 为 1～7，周日是 7。

### 9.3 测试与实现中遇到的问题

| 问题 | 原因 | 处理与注意 |
| --- | --- | --- |
| 用教务本地账号接口 `/student/login` 登录失败，随后要求验证码 | 测试账号是统一身份认证账号，本地账号体系不认 | 走 §2 的 authserver 流程。再次失败会触发验证码，不要对同一账号反复试密码 |
| 同一周的多个并行教室，顺序逐周不同 | 接口对同一天同一节次的多条记录没有稳定顺序 | 教室与老师先**去重再按字符串排序**再拼接，否则同一门实验课被拆成 7 行。不要改用 `localeCompare`，不同 WebView 的排序会不一致 |
| 周次与开学日一致性校验的误报风险 | `startDate` 不一定恰好是一周的第一天（测试里验证了开学日落在周一、周起始是周日的情形） | 校验的是 `weekStartOf(startDate)` 与记录算出的周起点，不是 `startDate` 本身。若今后遇到某学期 `weekIndex` 合理地跳周（例如停课周不计数），这条校验会中止导入——此时应改成按记录自带周次直接使用，并把推算出的起点与 `startDate` 的差异显示给用户，而不是简单删掉校验 |
| 旧代码的日期运算依赖本地时区 | `new Date("2026-09-23")` 是 UTC 零点，旧代码却用 `getDay()` 和本地 `setDate()` 取星期和周一 | 新代码一律用 `Date.UTC` 和 `getUTC*`，周内偏移由 `offsetInWeek` 统一计算 |
| 课表页首次请求耗时长 | 分析时取 `/student/for-std/course-table` 一次约 6.5 秒，更早一次在 30 秒超时 | 适配器里每个请求超时 15 秒。如果用户反馈取学期列表超时，只需提高这一个请求的超时，不要全局放宽 |
| 实验课同一周同一时间有 2～3 间教室 | 接口列出了该教学班全部行政班的教室，没有学生所属班级的字段 | 合并显示并在导入末尾弹窗列出相关课程，让用户自行改。没有选择「第一间」是因为那会把错误数据伪装成确定的 |
| 周六补课记录没有教室 | `room` 为 `null` 且没有 `customPlace` | 地点留空（拾光会把空地点存成无地点），不再写「未知地点」 |
| 测试里取消选择没有生效 | 模拟的 `showSingleSelection` 用 `pick ?? preset`，`null` 被当成缺省 | 用 `'pick' in mutate` 判断；模拟 Bridge 收到的是学期**名称**字符串数组，不是对象 |
| 模拟 `DOMParser` 在没有下拉框时崩溃 | 夹具函数没处理「未登录页面」 | 夹具里用 `noSemesterSelect` 开关返回 `null` 选择框 |
| 适配器不可等待 | 脚本末尾自己调用 `runImportFlow()`，没有返回 Promise | 测试轮询 `done`、终止弹窗或取消提示，最多等 2 秒 |
| 仓库样式规则 | `clearTimeout` 对空值无副作用，不应再加守卫 | `requestText` 里直接 `clearTimeout(timer)` |

### 9.4 行为约定（修改前先对照）

- 周次只信 `weekIndex`；它与开学日、周起始不吻合时**中止**，不做自动修正。星期与日期不一致同理。
- `firstDayOfWeek` 只由 `weekStartOnSunday` 决定（真为 7，否则为 1）；`semesterStartDate` 原样取 `startDate`，不规整成周一。
- 总周数 = 课程最大周次，下限 16、上限 60。教务周历有 26 周，但课程只排到第 18 周；这样学期在最后一节课后进入「已结束」，与旧行为一致。App 对更晚周次会自动扩展总周数。
- 作息：节次互不重叠才提交；起点和终点都落在节次边界上的课程用 `startSection/endSection`，否则用真实起止时间。作息请求失败不会降级，直接中止。
- 提交顺序是 `saveCourseConfig` → `savePresetTimeSlots` → `saveImportedCourses`，然后才弹出并行教室提示并通知完成。
- 没有排课记录的教学班（如军事技能）不导入，不报错。
- 老师为空时回退到教学班的 `teacherAssignmentList`，再为空则留空，不再写「未知教师」。

### 9.5 没有做、也不确定的事

- 没有在真机运行；测试夹具只有一个新生账号的一个学期。需要补测：雁塔校区作息（`timeTableLayoutId` 为 1，08:00 起）、研究生或其他 `bizTypeId`、`weekIndex` 跳周的学期、非周日起算的真实学期。
- 没有确定学生所属实验分班的办法。
- 已导入的旧课表不会被修复，需要重新导入。
- 夹具只含脱敏数据，不能代替真实账号的最终验证；教务接口变化后，先用 §2 的方式重新抓一份真实响应，按 `scripts/fixtures/xauat-2026-2027-1.json` 的字段裁剪、脱敏后更新夹具。

### 9.6 构建与安装记录

- 在 WSL Arch 构建：JDK 21 运行 Gradle（Miuix 子项目要求 21 的工具链，只装 JDK 17 会报 `languageVersion=21` 找不到），Android SDK 用 `cmdline-tools` 安装 `platform-tools`、`platforms;android-37.0`、`build-tools;37.0.0`，Gradle 9.6.1 手动下载（wrapper 在慢网下 10 秒超时）。Miuix 按 README 克隆到 `../miuix-reference` 并打补丁。
- 命令：`gradle assembleGithubDebug -Psleepdown.miuixSourcePath=../miuix-reference --parallel --max-workers=8 --build-cache`，21 分 46 秒。release 包需要仓库外的签名配置，本机没有，因此验收包是 **debug 包**（applicationId 带 `.debug` 后缀，与正式版并存，不覆盖用户数据，也不带正式签名）。
- 产物 `tmp/review-apks/SleepDown-xauat-fix-debug.apk`（不入库），已核对包内 `xauat.js` 含 `timetable-layout`、热修复表含 XAUAT 条目。
- 远端脚本热修复：App 会先下载远端 `XAUAT/xauat.js`；远端哈希等于旧版（`d6900ee2…`）时自动改用包内脚本，上游更新后恢复使用上游。上游一旦发布新版本，需重新核对本地修复是否仍需保留。
