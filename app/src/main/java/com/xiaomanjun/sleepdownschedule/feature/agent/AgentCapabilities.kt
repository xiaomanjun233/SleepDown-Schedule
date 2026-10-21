package com.xiaomanjun.sleepdownschedule.feature.agent

import kotlinx.serialization.json.*

internal enum class AgentCapabilityArea(val label: String, val actions: Set<AgentActionType>) {
    COURSES("课程与精细调课", setOf(AgentActionType.ADD_COURSE, AgentActionType.UPDATE_COURSE, AgentActionType.REPLACE_COURSE, AgentActionType.DELETE_COURSE)),
    PERIODS("节次与作息", setOf(AgentActionType.SET_PERIOD_SETTINGS)),
    SETTINGS("显示、提醒与应用设置", setOf(AgentActionType.SET_SETTING)),
    ADJUSTMENTS("停课与补课日期", setOf(AgentActionType.SET_ADJUSTMENTS)),
    SCHEDULES("多课表管理", setOf(AgentActionType.CREATE_SCHEDULE, AgentActionType.ACTIVATE_SCHEDULE, AgentActionType.DELETE_SCHEDULE)),
    NAVIGATION("页面与导入交接", setOf(AgentActionType.OPEN_SETTINGS, AgentActionType.OPEN_IMPORT))
}

internal fun agentPlanningToolDefinition(name: AgentToolName, strict: Boolean): JsonObject = buildJsonObject {
    put("type", "function")
    put("function", buildJsonObject {
        put("name", name.name)
        put("description", if (name == AgentToolName.GET_ACTION_GUIDE)
            "按功能分区获取操作格式与限制，可与必要事实同轮查询：COURSES 课程/精细调课；PERIODS 作息；SETTINGS 外观/提醒；ADJUSTMENTS 调休；SCHEDULES 多课表；NAVIGATION 页面/导入。"
        else "校验完整变更计划并展示确认卡，绝不直接保存。先取得目标事实和对应分区格式，再单独调用本工具；成功后由应用结束本轮，无需重复调用或再生成操作。")
        put("parameters", buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                if (name == AgentToolName.GET_ACTION_GUIDE) {
                    put("area", buildJsonObject {
                        put("type", "string")
                        put("enum", JsonArray(AgentCapabilityArea.entries.map { JsonPrimitive(it.name) }))
                    })
                } else {
                    put("actionsJson", buildJsonObject { put("type", "string"); put("description", "操作对象数组的合法 JSON 字符串；遵循 GET_ACTION_GUIDE 返回的字段。") })
                }
            })
            put("required", if (name == AgentToolName.GET_ACTION_GUIDE) JsonArray(listOf(JsonPrimitive("area")))
                else JsonArray(listOf(JsonPrimitive("actionsJson"))))
            put("additionalProperties", false)
        })
        if (strict) put("strict", true)
    })
}

internal fun agentActionGuide(area: String): String {
    val selected = AgentCapabilityArea.entries.firstOrNull { it.name.equals(area.trim(), true) }
        ?: throw IllegalArgumentException("未知功能分区：请选择 ${AgentCapabilityArea.entries.joinToString { it.name }}")
    val detail = when (selected) {
        AgentCapabilityArea.COURSES -> """
            先用 SEARCH_COURSES 精确定位；批量/跨周目标用 GET_SEMESTER_SCHEDULE，准确时间用 GET_PERIODS。
            以下 ID 42 仅为格式示例，必须替换为本轮查询得到的真实 ID。
            {"type":"ADD_COURSE","scope":"ALL_WEEKS","course":{"name":"课程","weekday":1,"periods":[1,2],"weeks":[3,5]}}
            {"type":"UPDATE_COURSE","courseId":42,"scope":"SELECTED_WEEKS","sourceWeeks":[3],"course":{"weekday":2,"periods":[5,6]}}
            UPDATE_COURSE 为局部补丁：name/teacher/location/note/weekday/periods/weeks/weekParity/customStartTime/customEndTime/customColorArgb。省略保留；clearFields 可显式清空 teacher/location/note/customTime。精确时间同时给 HH:mm 起止，不推算未提供的铃声。
            REPLACE_COURSE 完整替换 course；必须提供名称、星期、节次、周次，其余未提供的文本清空。
            {"type":"DELETE_COURSE","courseId":42,"scope":"SELECTED_WEEKS","sourceWeeks":[3]}
            scope=CURRENT_WEEK 仅当前有效教学周；SELECTED_WEEKS 必须给原始 sourceWeeks；ALL_WEEKS 整个记录。course.weeks 为目标周，省略留在所选周；跨周移动仍保留未选中的原周。
            精细调课：UPDATE/DELETE 可额外给 sourcePeriods=[9]，表示仅处理原课第9节，保留同周其余节次。UPDATE 的 course.periods 是目标节次，省略时保留所选节次；例如第3周第9节移至第4周周二第2节：sourceWeeks=[3],sourcePeriods=[9],course={"weeks":[4],"weekday":2,"periods":[2]}。同一记录的周×节次不能重叠提交。只有整体自定义时间而没有逐节铃声的课程不能按节次拆分，需明确整体时间。
            指定日期使用工具中的原 teachingWeek/originalDate，不能将补课日再次套作普通教学周。交换输出两条修改；批量按真实 ID 找全，不逐字段查询或删除重建。
        """
        AgentCapabilityArea.PERIODS -> """
            先读 GET_PERIODS、GET_SETTINGS；改变节次含义时再读相关课程，迁移与设置放同一计划。
            {"type":"SET_PERIOD_SETTINGS","periodSettings":{"morningPeriodCount":4,"noonPeriodCount":0,"afternoonPeriodCount":4,"eveningPeriodCount":2,"classDurationMinutes":45,"breakDurationMinutes":10,"morningStartTime":"08:00","afternoonStartTime":"14:00","eveningStartTime":"19:00"}}
            可用 schemeName/mode(AUTO_MATCH|MANUAL)/四时段 StartTime/classDurationMinutes/breakDurationMinutes/specialBreaks/overriddenPeriods/periods。四时段节数必须全部提供或全部省略；periods 如提供必须完整覆盖全部节次，每项 periodIndex/startTime/endTime。只调一节时间可用 SET_SETTING 的 PERIOD_n_TIME，格式按 GET_SETTINGS。
        """
        AgentCapabilityArea.SETTINGS -> """
            先读 GET_SETTINGS，严格使用返回的键、类型、范围和当前值。
            {"type":"SET_SETTING","settingKey":"DARK_MODE","settingValue":"true"}
            每键一条；NOTIFICATION_MODE 与 REALTIME_ACTIVITY 不能并存；COURSE_CARD_COLOR/COURSE_CARD_PALETTE/COURSE_CARD_COLOR_MODE 同组只选一个。FOLLOW_SYSTEM_DARK_MODE 与 DARK_MODE 可组合。系统权限仍需原页面授权。
        """
        AgentCapabilityArea.ADJUSTMENTS -> """
            先读 GET_SCHEDULE_ADJUSTMENTS。SET_ADJUSTMENTS 是整表替换，保留用户未要求删除的所有条目。
            {"type":"SET_ADJUSTMENTS","adjustments":[{"date":"2026-10-02","sourceDate":"2026-10-05","label":"补课"},{"date":"2026-10-03","label":"停课"}]}
            date 是实际日期，sourceDate 是原课程日期；两者不能相同，同日只能一条。仅在明确清空全部时传 []。
        """
        AgentCapabilityArea.SCHEDULES -> """
            先读 GET_SCHEDULES。
            创建不自动切换。以下 ID 42 仅为格式示例，必须替换为本轮查询得到的真实 ID。
            {"type":"CREATE_SCHEDULE","name":"新学期"}
            {"type":"ACTIVATE_SCHEDULE","scheduleId":42}
            {"type":"DELETE_SCHEDULE","scheduleId":42}
            切换后必须重新读取目标课表才能改课，不能在同一计划使用旧课表事实修改新课表；不能猜测新建课表 ID，不能删除最后一张课表。
        """
        AgentCapabilityArea.NAVIGATION -> """
            {"type":"OPEN_SETTINGS","settingsPage":"SCHEDULE"}
            {"type":"OPEN_IMPORT","importText":"用户原文"}
            页面：GENERAL 通用、PERSONALIZATION 首页外观、LIQUID_GLASS 玻璃、WIDGETS 小组件、AI_IMPORT 模型、DAY_AGENT 助手、SCHEDULE 周数/开学日期/节次、SCHEDULE_ADJUSTMENTS 调休、NOTIFICATIONS 提醒、SCHEDULE_MANAGER 多课表、BACKUP_RESTORE 备份、ABOUT 关于、CHANGELOG 日志、DOWNLOAD 下载、DONATE 捐赠、PRIVACY_POLICY 隐私。
            仅明确要求打开或必须人工完成时导航。壁纸/文件、教务登录、凭据、系统权限经原页面完成；打开不等于成功。导入必须走原预览确认，图片由应用交接，禁止编造本地路径。importText 最多40000字符。
        """
    }
    return "${selected.label}：\n${detail.trimIndent()}\n把完整数组作为 PROPOSE_ACTIONS.actionsJson 提交；应用根据实际字段生成确认说明。"
}
