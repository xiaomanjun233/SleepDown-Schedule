package com.xiaomanjun.sleepdownschedule.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.CourseScheduleApp
import com.xiaomanjun.sleepdownschedule.CourseScheduleTheme
import com.xiaomanjun.sleepdownschedule.EduImportActivity
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModel
import com.xiaomanjun.sleepdownschedule.app.state.ScheduleViewModelFactory
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.TopGlassIconButton
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertAction
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertActionStyle
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LiquidAlertDialog
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.SleepDownDesignTokens
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.feature.course.management.HomeMenuActivitySourceFallback
import com.xiaomanjun.sleepdownschedule.feature.importing.EduSchoolPickerScreen
import com.xiaomanjun.sleepdownschedule.feature.importing.ShiguangWarehouse
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.ShiguangWarehouseUpdater
import com.xiaomanjun.sleepdownschedule.feature.importing.shiguang.describeAdapterRefresh
import com.xiaomanjun.sleepdownschedule.feature.importing.toIntentKey
import com.xiaomanjun.sleepdownschedule.feature.settings.title
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import kotlinx.coroutines.launch

open class EduSchoolSelectActivityHost : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContent {
            val app = application as CourseScheduleApp
            val viewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = ScheduleViewModelFactory(app, app.repository)
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val refreshScope = rememberCoroutineScope()
            var warehouseGeneration by remember { mutableIntStateOf(0) }
            var warehouseRefreshing by remember { mutableStateOf(false) }
            var warehouseManualRefreshing by remember { mutableStateOf(false) }
            var warehouseRefreshResultMessage by remember { mutableStateOf<String?>(null) }
            fun refreshWarehouse(manual: Boolean) {
                if (warehouseRefreshing) return
                warehouseRefreshing = true
                warehouseManualRefreshing = manual
                refreshScope.launch {
                    val beforeAdapters = if (manual) ShiguangWarehouse.loadAdapters(this@EduSchoolSelectActivityHost)
                        else emptyList()
                    runCatching {
                        if (manual) {
                            ShiguangWarehouseUpdater.refresh(this@EduSchoolSelectActivityHost)
                        } else {
                            ShiguangWarehouseUpdater.refreshIfStale(this@EduSchoolSelectActivityHost)
                        }
                    }.onSuccess { result ->
                        if (result == null) return@onSuccess
                        if (result.changed) warehouseGeneration += 1
                        if (manual) {
                            val afterAdapters = ShiguangWarehouse.loadAdapters(this@EduSchoolSelectActivityHost)
                            warehouseRefreshResultMessage = describeAdapterRefresh(
                                beforeAdapters, afterAdapters, result.changed
                            )
                        }
                    }.onFailure { error ->
                        if (manual) {
                            warehouseRefreshResultMessage =
                                "更新失败，继续使用当前适配列表：${error.message ?: "网络请求失败"}"
                        }
                    }
                    warehouseRefreshing = false
                    warehouseManualRefreshing = false
                }
            }
            LaunchedEffect(Unit) {
                if (ShiguangWarehouseUpdater.isRefreshStale(this@EduSchoolSelectActivityHost)) {
                    refreshWarehouse(manual = false)
                }
            }
            CourseScheduleTheme(config = state.config) {
                CrossActivityTransitionHost(
                    activity = this@EduSchoolSelectActivityHost,
                    sourceContent = {
                        HomeMenuActivitySourceFallback(
                            config = state.config,
                            highlightedRowIndex = 4
                        )
                    }
                ) { requestClose ->
                    DetailActivityScaffold(
                        title = "选择学校",
                        config = state.config,
                        onBack = requestClose,
                        topBarActions = { topBackdrop ->
                            TopGlassIconButton(
                                backdrop = topBackdrop,
                                config = settingsVisualConfig(state.config),
                                iconRes = R.drawable.ic_refresh,
                                contentDescription = if (warehouseRefreshing) "正在更新适配器" else "更新适配器",
                                onClick = { refreshWarehouse(manual = true) },
                                modifier = Modifier.size(SleepDownDesignTokens.SecondaryPage.BackButtonSize),
                                buttonHeight = 42.dp
                            )
                        }
                    ) { backdrop ->
                        EduSchoolPickerScreen(
                            state = state,
                            backdrop = backdrop,
                            warehouseGeneration = warehouseGeneration,
                            onSelect = { adapter ->
                                ActivityTransitionCoordinator.openImmediate(
                                    this@EduSchoolSelectActivityHost,
                                    TransitionRouteId.SchoolSelectToEduImport,
                                    Intent(this, EduImportActivity::class.java)
                                        .putExtra(EduAdapterExtra, adapter.toIntentKey())
                                )
                            }
                        )
                        if (warehouseManualRefreshing) {
                            LiquidAlertDialog(
                                title = "正在更新适配器",
                                message = "正在获取最新适配列表。现在可以返回或退到桌面，更新会在后台继续。",
                                actions = listOf(
                                    LiquidAlertAction(
                                        "后台继续",
                                        LiquidAlertActionStyle.Secondary,
                                        onClick = { warehouseManualRefreshing = false }
                                    )
                                ),
                                backdrop = backdrop,
                                config = state.config,
                                onDismissRequest = { warehouseManualRefreshing = false }
                            )
                        }
                        warehouseRefreshResultMessage?.let { result ->
                            LiquidAlertDialog(
                                title = "适配器刷新结果",
                                message = result,
                                actions = listOf(LiquidAlertAction("知道了", LiquidAlertActionStyle.Primary) {
                                    warehouseRefreshResultMessage = null
                                }),
                                backdrop = backdrop,
                                config = state.config,
                                onDismissRequest = { warehouseRefreshResultMessage = null }
                            )
                        }
                    }
                }
            }
        }
    }
}
