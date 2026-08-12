package com.xiaomanjun.sleepdownschedule

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val AiImportHistoryParabolicMotionExtra = "ai_import_history_parabolic_motion"

class AiImportHistoryActivity : ComponentActivity() {
    private var morphSnapshotToken: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val sourceBounds = intent.anchoredSourceBoundsOrNull()
        val useParabolicMotion = intent.getBooleanExtra(AiImportHistoryParabolicMotionExtra, false)
        morphSnapshotToken = intent.anchoredMorphSnapshotTokenOrNull()
        val transitionSnapshots = AnchoredMorphSnapshotStore.get(morphSnapshotToken)
        val app = application as CourseScheduleApp
        setContent {
            val state by app.repository.state.collectAsStateWithLifecycle(AppState())
            CourseScheduleTheme(config = state.config) {
                var detailRequest by remember { mutableStateOf<AiImportHistoryDetailMorphRequest?>(null) }
                var detailSourceHidden by remember { mutableStateOf(false) }
                var returnToMain by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    AnchoredDetailActivityMorph(
                        sourceBounds = sourceBounds,
                        sourceCornerRadius = 21.dp,
                        backgroundSnapshot = transitionSnapshots?.background,
                        sourceSnapshot = transitionSnapshots?.source,
                        motionStyle = if (useParabolicMotion) {
                            AnchoredDetailMotionStyle.Parabolic
                        } else {
                            AnchoredDetailMotionStyle.Liquid
                        },
                        onFinished = { finish() },
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
                            hiddenEntryId = detailRequest?.entry?.id.takeIf { detailSourceHidden },
                            onOpen = { entry, rowBounds, sourceSnapshot ->
                                val draft = AiImportHistoryStore.restore(entry, state.config).getOrNull()
                                if (draft != null) {
                                    detailRequest = AiImportHistoryDetailMorphRequest(
                                        entry = entry,
                                        draft = draft,
                                        sourceBounds = rowBounds,
                                        sourceSnapshot = sourceSnapshot
                                    )
                                }
                            }
                        )
                    }
                    detailRequest?.let { request ->
                        Box(Modifier.fillMaxSize().zIndex(500f)) {
                            AiImportHistoryDetailMorphOverlay(
                                request = request,
                                config = state.config,
                                onSourceHandoff = { detailSourceHidden = true },
                                onClosed = {
                                    detailSourceHidden = false
                                    detailRequest = null
                                    if (returnToMain) {
                                        startActivity(
                                            Intent(this@AiImportHistoryActivity, MainActivity::class.java).addFlags(
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            )
                                        )
                                        returnToMain = false
                                    }
                                },
                                onImportRequested = { draft, createNewSchedule ->
                                    AiEduImportProgressSession.requestFinalImport(draft, createNewSchedule)
                                    returnToMain = true
                                },
                                sourceContent = {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f))
                                    ) {
                                        AiImportHistoryRowContent(
                                            entry = request.entry,
                                            modifier = Modifier.fillMaxSize(),
                                            textColor = glassForegroundColor(settingsVisualConfig(state.config))
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) AnchoredMorphSnapshotStore.remove(morphSnapshotToken)
        super.onDestroy()
    }
}

@Composable
private fun AiImportHistoryPage(
    config: ScheduleConfigEntity,
    onBack: () -> Unit,
    hiddenEntryId: String?,
    onOpen: (AiImportHistoryEntry, androidx.compose.ui.geometry.Rect, Bitmap?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textColor = glassForegroundColor(settingsVisualConfig(config))
    var entries by remember { mutableStateOf(AiImportHistoryStore.load(context)) }
    val scope = rememberCoroutineScope()
    val snapshotLayer = rememberGraphicsLayer()
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootPosition = it.positionInWindow() }
            .drawWithContent {
                snapshotLayer.record { this@drawWithContent.drawContent() }
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
                            showSource = hiddenEntryId != entry.id,
                            onDelete = {
                                AiImportHistoryStore.delete(context, entry.id)
                                entries = entries.filterNot { it.id == entry.id }
                            },
                            onOpen = { bounds ->
                                scope.launch {
                                    val frame = runCatching {
                                        snapshotLayer.toImageBitmap().asAndroidBitmap()
                                    }.getOrNull()
                                    onOpen(entry, bounds, frame?.cropToWindowBounds(bounds, rootPosition))
                                }
                            }
                        )
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
