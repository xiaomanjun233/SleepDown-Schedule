package com.xiaomanjun.sleepdownschedule.feature.settings

import androidx.compose.animation.core.Easing
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.map

enum class SettingsSection { Schedule, Notifications }
enum class SettingsPage { Root, General, LiquidGlass, Widgets, AiImport, DayAgent, Schedule, PeriodSchemes, AutoRefreshSchedule, Notifications, ScheduleManager, BackupRestore, WebDav, BackupPreview, About, Changelog, Donate, PrivacyPolicy }

/** Matches the navigation motion used by the bundled Miuix system-style navigator. */
private class MiuixSettingsNavigationEasing(
    response: Float = 0.8f,
    damping: Float = 0.95f
) : Easing {
    private val decay: Float
    private val frequency: Float
    private val phase: Float

    init {
        val omega = 2.0 * PI / response
        val stiffness = omega * omega
        val friction = damping * 4.0 * PI / response
        frequency = (sqrt(4.0 * stiffness - friction * friction) / 2.0).toFloat()
        decay = (-friction / 2.0).toFloat()
        phase = decay / frequency
    }

    override fun transform(fraction: Float): Float {
        val time = fraction.toDouble()
        val attenuation = exp(decay * time)
        return (attenuation * (-cos(frequency * time) + phase * sin(frequency * time)) + 1.0).toFloat()
    }
}

internal val MiuixSettingsNavigationMotion: Easing = MiuixSettingsNavigationEasing()

internal fun SettingsPage.title(): String = when (this) {
    SettingsPage.Root -> "设置"
    SettingsPage.General -> "通用设置"
    SettingsPage.LiquidGlass -> "视效设置"
    SettingsPage.Widgets -> "小组件设置"
    SettingsPage.AiImport -> "AI 设置"
    SettingsPage.DayAgent -> "AI 助理"
    SettingsPage.Schedule -> "课表详细设置"
    SettingsPage.PeriodSchemes -> "作息管理"
    SettingsPage.AutoRefreshSchedule -> "自动刷新课表"
    SettingsPage.Notifications -> "通知设置"
    SettingsPage.ScheduleManager -> "课表设置"
    SettingsPage.BackupRestore -> "备份与恢复"
    SettingsPage.WebDav -> "WebDAV 备份"
    SettingsPage.BackupPreview -> "恢复预览"
    SettingsPage.About -> "关于应用"
    SettingsPage.Changelog -> "关于应用"
    SettingsPage.Donate -> "捐赠支持"
	SettingsPage.PrivacyPolicy -> "隐私政策"
}

internal fun SettingsPage.usesPersistentCenteredSettingsTitle(): Boolean = when (this) {
    SettingsPage.LiquidGlass,
    SettingsPage.Widgets,
    SettingsPage.PeriodSchemes,
    SettingsPage.AutoRefreshSchedule,
    SettingsPage.About,
	SettingsPage.Changelog,
	SettingsPage.PrivacyPolicy -> true
    else -> false
}

internal data class TabletSettingsNavigationState(
    val rootPage: SettingsPage = SettingsPage.General,
    val detailPages: List<SettingsPage> = emptyList()
) {
    val displayedPage: SettingsPage
        get() = detailPages.lastOrNull() ?: rootPage

    fun selectRoot(page: SettingsPage): TabletSettingsNavigationState = copy(
        rootPage = page.takeUnless { it == SettingsPage.Root } ?: SettingsPage.General,
        detailPages = emptyList()
    )

    fun pushDetail(page: SettingsPage): TabletSettingsNavigationState =
        if (page == displayedPage) this else copy(detailPages = detailPages + page)

    fun popDetail(): TabletSettingsNavigationState =
        if (detailPages.isEmpty()) this else copy(detailPages = detailPages.dropLast(1))
}

internal val TabletSettingsNavigationStateSaver = listSaver<TabletSettingsNavigationState, String>(
    save = { state -> listOf(state.rootPage.name) + state.detailPages.map(SettingsPage::name) },
    restore = { names ->
        val root = names.firstOrNull()
            ?.let { runCatching { SettingsPage.valueOf(it) }.getOrNull() }
            ?.takeUnless { it == SettingsPage.Root }
            ?: SettingsPage.General
        val details = names.drop(1).mapNotNull { name ->
            runCatching { SettingsPage.valueOf(name) }.getOrNull()
        }
        TabletSettingsNavigationState(rootPage = root, detailPages = details)
    }
)
