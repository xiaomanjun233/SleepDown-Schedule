package com.xiaomanjun.sleepdownschedule.feature.settings

internal data class SettingsSearchEntry(val page: SettingsPage, val title: String, val keywords: String)

private val settingsSearchEntries = listOf(
    SettingsSearchEntry(SettingsPage.General, "通用设置", "外观 深色 主题 图标 导航 视效 材质 更新"),
    SettingsSearchEntry(SettingsPage.Widgets, "小组件设置", "桌面 小部件 颜色 背景 透明度"),
    SettingsSearchEntry(SettingsPage.Schedule, "当前课表详细设置", "学期 总周数 开学日期 节次 时间 作息 特殊课程"),
    SettingsSearchEntry(SettingsPage.AutoRefreshSchedule, "自动刷新课表", "教务 适配 自动更新"),
    SettingsSearchEntry(SettingsPage.Notifications, "通知设置", "提醒 课前 铃声 勿扰 静音 明日课程 实时活动"),
    SettingsSearchEntry(SettingsPage.AiImport, "AI 设置", "人工智能 导入 模型 服务 API 密钥"),
    SettingsSearchEntry(SettingsPage.DayAgent, "AI 助理", "今日助手 对话 智能体"),
    SettingsSearchEntry(SettingsPage.BackupRestore, "备份与恢复", "导出 数据 WebDAV 网盘"),
    SettingsSearchEntry(SettingsPage.Donate, "捐赠支持", "赞助"),
    SettingsSearchEntry(SettingsPage.About, "关于应用", "版本 检查更新 更新日志 隐私"),
)

internal fun settingsSearchResults(query: String): List<SettingsSearchEntry> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (terms.isEmpty()) return emptyList()
    return settingsSearchEntries.filter { entry ->
        terms.all { (entry.title + " " + entry.keywords).contains(it, ignoreCase = true) }
    }
}
