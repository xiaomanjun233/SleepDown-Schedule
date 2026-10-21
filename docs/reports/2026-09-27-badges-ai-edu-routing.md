# 角标、切周与 AI 教务导入修复

## 调查依据

- 当前 `main` 基线为 `a8e869e`，修改在 `codex/badges-motion-ai-edu`。
- 使用 Codex GitHub plugin 检查了 issues；[问题 #43](https://github.com/xiaomanjun233/SleepDown-Schedule/issues/43) 反馈自定义时间与教师文字重叠，[问题 #3](https://github.com/xiaomanjun233/SleepDown-Schedule/issues/3) 反馈切周首帧异常。没有搜到直接描述冲突角标飞离的 issue，不能把这两条当作同一根因的复现证据。
- 历史角标记录见 `2026-09-20-adjustments-agent-stability.md`，玻璃定位层重放问题见 `LIQUID_GLASS_FRAMEWORK.md` 及当前 Backdrop 绘制实现。当前补／停角标已设置 `placementLayer = false`，冲突角标仍启用额外定位层。

## 界面修改

- 冲突角标关闭额外玻璃定位层，沿用课程卡父层的分页与甩尾变换。
- 补／停胶囊最小尺寸从 22dp 缩至 16dp，文字从 10sp 调为 9sp，移到卡片顶部内侧并轻微外悬。周卡正文按字体缩放后的角标高度预留顶部空间；日卡保留原正文内距，大字体时增加必要留白。
- 冲突与补／停并存时合并成一个状态胶囊，避免在同一角叠放；冲突正文同样预留空间。
- 只有手势切周使用 16ms 行间延迟（原 28ms），六行最大延迟从 140ms 降至 80ms。程序跳周继续使用 28ms，日周切换及首页设置切换不变。
- 单课程编辑器在父层绘制记录中保留连续圆角裁切，覆盖固定玻璃分配区切回普通布局的交接。裁切随真实几何变化，不新增常驻 Offscreen 层。

## AI 教务导入

1. 本地脚本检查当前页与最多 12 个可访问 frame，深度最多 3 层。只读取已知 DOM 结构、jQuery 是否存在及资源路径中的厂商标记；不枚举全局变量、不读取 cookie/storage/隐藏字段值、不发送脚本正文。
2. 只匹配仓库中的正方、青果、URP、超星通用工具。单一且证据明确的候选直接提供通用导入选项，无需调用模型。
3. 有歧义时，用户可确认只发送少量结构特征。模型返回 `action/candidate/confidence` JSON；本地只接受候选列表内、置信度至少 0.9 的选择。无效 JSON、未知工具、额外脚本字段或低置信度均建议文本解析，不执行模型代码。
4. 分类请求使用既有 Provider/HTTP/前台服务，每次最多一次请求，输出预算 2048 token，不进入课程 JSON repair，也不把识别决定当作已导入课程。
5. 使用通用工具前重新检查目标文档与 frame 标识，防止导航后执行旧结果。复用原拾光 adapter → bridge → 本地草稿 → 预览确认链路，不新增学校协议。一次尝试后再次点击 AI 导入可进入文本回退。
6. 文本回退去除重复滚动快照及表格的重复 HTML；保留空单元格、合并行列、正文备注、iframe 和 Shadow DOM 内容。仅按完整块去重，不按课程行去重。超过 60000 字符时明确提示截断。识屏继续由用户选择。

数据库、备份协议、已有导入草稿和 AI 历史格式不变。

## 主要代码入口

- 角标与正文：`feature/home/AdjustedCourseActions.kt`、`feature/home/week/WeekScheduleUi.kt`、`feature/home/day/HomeScheduleUi.kt`。
- 手势甩尾：`feature/home/week/WeekPageTailMotion.kt`。
- 单课程编辑器：`feature/course/editor/CourseEditorContainerOverlay.kt`。
- 教务识别和执行：`feature/importing/AiEduRouting.kt`、`AiEduRoutingService.kt`、`AiEduPageRouting.kt`；由 `EduImportBrowserUi.kt` 接入，分类请求使用 `AiImportTaskManager.kt`。
- 文本压缩与表格结构：`feature/importing/EduPageCapture.kt`。

以上路径均相对 `app/src/main/java/com/xiaomanjun/sleepdownschedule/`。

## 验证

- 最终 `compileGithubReleaseKotlin --no-daemon --console=plain --no-parallel --max-workers=1` 通过（2 分 34 秒），覆盖全部 Kotlin 修改。
- `node scripts/tests/ai-edu-routing.cjs` 通过：四类教务、缺少依赖、登录页、歧义表格、frame 边界、导航标识及敏感状态排除；另验证实际脚本调度模板、顶层 bridge 复用和空单元格／合并行列提取。
- `testGithubDebugUnitTest` 单 worker 通过（3 分 50 秒），5 个定向套件共 23 项，0 失败、0 错误：切周轨迹 10、进度动作消费 3、路由 JSON 5、原有格式修复 3、文本去重 2。没有运行无关套件。
- 曾把 Release 编译与 Debug 测试放在同一轮执行，2GB Gradle 堆和机器可用内存不足，停止后改成单 worker 分开执行；没有修改仓库构建参数。
- `adb devices -l` 没有连接设备。未安装、未进行实机动画或真实学校登录验证，也未调用收费模型测量实际 token 用量。上述视觉修复仍需实机检查快滑、反向滑动、首末列、大字体和编辑器动画收尾。
