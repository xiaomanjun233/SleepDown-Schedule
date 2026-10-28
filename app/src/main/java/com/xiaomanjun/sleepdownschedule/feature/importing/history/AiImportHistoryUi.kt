package com.xiaomanjun.sleepdownschedule.feature.importing.history

import com.xiaomanjun.sleepdownschedule.core.ui.theme.appUsesDarkTheme

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaomanjun.sleepdownschedule.*
import com.xiaomanjun.sleepdownschedule.R
import com.xiaomanjun.sleepdownschedule.core.ui.chrome.DockScrollPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.DetailActivityScaffold
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.detailContentTopPadding
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsPageBackground
import com.xiaomanjun.sleepdownschedule.core.ui.scaffold.settingsVisualConfig
import com.xiaomanjun.sleepdownschedule.feature.importing.*
import com.xiaomanjun.sleepdownschedule.feature.importing.progress.*
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.glass.ui.glassForegroundColor
import com.xiaomanjun.sleepdownschedule.transition.ActivityTransitionCoordinator
import com.xiaomanjun.sleepdownschedule.transition.CrossActivityTransitionHost
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorFrame
import com.xiaomanjun.sleepdownschedule.transition.TransitionAnchorProvider
import com.xiaomanjun.sleepdownschedule.transition.TransitionPayload
import com.xiaomanjun.sleepdownschedule.transition.TransitionRouteId
import com.xiaomanjun.sleepdownschedule.transition.legacy.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

open class AiImportHistoryActivityHost : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityTransitionCoordinator.prepareDestinationBeforeOnCreate(this)
        super.onCreate(savedInstanceState)
        ActivityTransitionCoordinator.installDestinationWindowBackground(this)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val app = application as CourseScheduleApp
        setContent {
            val state by app.repository.state.collectAsStateWithLifecycle(AppState())
            CourseScheduleTheme(config = state.config) {
                val transitionScope = rememberCoroutineScope()
                val pageConfig = settingsVisualConfig(state.config)
                val pageBackgroundArgb = settingsPageBackground(pageConfig).toArgb()
                CrossActivityTransitionHost(
                    activity = this@AiImportHistoryActivityHost,
                    sourceContent = {
                        Box(
                            Modifier.fillMaxSize().background(
                                if (appUsesDarkTheme(state.config)) {
                                    Color.White.copy(alpha = 0.10f)
                                } else {
                                    Color.White.copy(alpha = 0.28f)
                                }
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_history),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                ) { requestClose ->
                    AiImportHistoryPage(
                        config = state.config,
                        onBack = requestClose,
                        onOpen = { entry, rowBounds, sourceSnapshot, backgroundSnapshot, returnBoundsProvider ->
                            if (sourceSnapshot != null && backgroundSnapshot != null) {
                                val anchor = TransitionAnchorFrame(
                                    boundsInWindow = rowBounds,
                                    cornerRadiusPx = with(resources.displayMetrics) { 18f * density },
                                    bitmap = sourceSnapshot
                                )
                                transitionScope.launch {
                                    ActivityTransitionCoordinator.open(
                                        activity = this@AiImportHistoryActivityHost,
                                        routeId = TransitionRouteId.AiHistoryToDetail,
                                        intent = Intent(
                                            this@AiImportHistoryActivityHost,
                                            AiImportHistoryDetailActivity::class.java
                                        ).putExtra(AiImportHistoryDetailActivityHost.EntryIdExtra, entry.id),
                                        payload = TransitionPayload(
                                            openingAnchor = anchor,
                                            // The row may scroll, be reshuffled or disappear while the
                                            // detail is open; resolve the live landing position by ID.
                                            returnAnchorProvider = TransitionAnchorProvider {
                                                returnBoundsProvider()?.let { anchor.copy(boundsInWindow = it) }
                                            },
                                            backgroundBitmap = backgroundSnapshot,
                                            backgroundColor = pageBackgroundArgb
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

}

@Composable
private fun AiImportHistoryPage(
    config: ScheduleConfigEntity,
    onBack: () -> Unit,
    onOpen: (
        AiImportHistoryEntry,
        androidx.compose.ui.geometry.Rect,
        Bitmap?,
        Bitmap?,
        () -> androidx.compose.ui.geometry.Rect?
    ) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textColor = glassForegroundColor(settingsVisualConfig(config))
    var entries by remember { mutableStateOf(AiImportHistoryStore.load(context)) }
    val scope = rememberCoroutineScope()
    val snapshotLayer = rememberGraphicsLayer()
    val snapshotRequested = remember { AtomicBoolean(false) }
    var snapshotRequestVersion by remember { mutableStateOf(0) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    // Live row geometry for the return animation. Entries that leave the viewport are removed so a
    // deleted or recycled row can never be used as the CLOSE target.
    val rowBounds = remember {
        mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()
    }
    Box(
        Modifier
            .semantics { testTag = "benchmark_import_history_list" }
            .fillMaxSize()
            .onGloballyPositioned {
                rootPosition = it.positionInWindow()
                rootSize = it.size
            }
            .drawWithContent {
                if (snapshotRequestVersion >= 0 && snapshotRequested.compareAndSet(true, false)) {
                    snapshotLayer.record { this@drawWithContent.drawContent() }
                }
                drawContent()
            }
    ) {
        DetailActivityScaffold(
            title = "导入历史",
            config = config,
            onBack = onBack,
            compactTopBar = true,
            centerCompactTitle = true
        ) { _ ->
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有导入记录", color = textColor.copy(alpha = 0.52f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = detailContentTopPadding() + 10.dp,
                        bottom = DockScrollPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        AiEduHistorySwipeRow(
                            entry = entry,
                                textColor = textColor,
                                showSource = true,
                                onDelete = {
                                    AiImportHistoryStore.delete(context, entry.id)
                                    entries = entries.filterNot { it.id == entry.id }
                                },
                                onOpen = { bounds ->
                                    scope.launch {
                                        snapshotRequested.set(true)
                                        snapshotRequestVersion += 1
                                        withFrameNanos { }
                                        withFrameNanos { }
                                        val frame = runCatching {
                                            snapshotLayer.toImageBitmap().asAndroidBitmap()
                                        }.getOrNull()
                                        onOpen(
                                            entry,
                                            bounds,
                                            frame?.cropToWindowBounds(bounds, rootPosition),
                                            frame,
                                            { rowBounds[entry.id] }
                                        )
                                    }
                                },
                            onBoundsChanged = { bounds ->
                                val fullyVisible = rootSize.height > 0 &&
                                    bounds.top >= rootPosition.y &&
                                    bounds.bottom <= rootPosition.y + rootSize.height &&
                                    bounds.left >= rootPosition.x &&
                                    bounds.right <= rootPosition.x + rootSize.width
                                if (fullyVisible) rowBounds[entry.id] = bounds
                                else rowBounds.remove(entry.id)
                            }
                        )
                        DisposableEffect(entry.id) {
                            onDispose { rowBounds.remove(entry.id) }
                        }
                    }
                }
            }
        }
    }
}

private fun Bitmap.cropToWindowBounds(
    bounds: androidx.compose.ui.geometry.Rect,
    rootPosition: Offset
): Bitmap? = runCatching {
    val left = (bounds.left - rootPosition.x).roundToInt().coerceIn(0, width - 1)
    val top = (bounds.top - rootPosition.y).roundToInt().coerceIn(0, height - 1)
    val cropWidth = bounds.width.roundToInt().coerceIn(1, width - left)
    val cropHeight = bounds.height.roundToInt().coerceIn(1, height - top)
    Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
}.getOrNull()
