package com.example.courseschedule

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val AiImportHistoryParabolicMotionExtra = "ai_import_history_parabolic_motion"

class AiImportHistoryActivity : ComponentActivity() {
    private var morphSnapshotToken: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sourceBounds = intent.anchoredSourceBoundsOrNull()
        val useParabolicMotion = intent.getBooleanExtra(AiImportHistoryParabolicMotionExtra, false)
        morphSnapshotToken = intent.anchoredMorphSnapshotTokenOrNull()
        val transitionSnapshots = AnchoredMorphSnapshotStore.get(morphSnapshotToken)
        val app = application as CourseScheduleApp
        setContent {
            val state by app.repository.state.collectAsStateWithLifecycle(AppState())
            CourseScheduleTheme(config = state.config) {
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
                        onOpen = { entry, rowBounds, snapshots ->
                            val detailIntent = Intent(this, AiImportHistoryDetailActivity::class.java)
                                .putExtra(AiImportHistoryDetailActivity.EntryIdExtra, entry.id)
                                .putAnchoredSourceBounds(rowBounds)
                            snapshots?.let(detailIntent::putAnchoredMorphSnapshots)
                            startActivityWithAnchoredMorph(detailIntent)
                        }
                    )
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
    onOpen: (AiImportHistoryEntry, androidx.compose.ui.geometry.Rect, AnchoredMorphSnapshots?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val textColor = glassForegroundColor(settingsVisualConfig(config))
    val pageBackgroundArgb = settingsPageBackground(settingsVisualConfig(config)).toArgb()
    var entries by remember { mutableStateOf(AiImportHistoryStore.load(context)) }
    var hiddenEntryId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snapshotLayer = rememberGraphicsLayer()
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hiddenEntryId = null
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
                                    val sourceSnapshot = frame?.cropToWindowBounds(bounds, rootPosition)
                                    val backgroundSnapshot = frame?.clearWindowBounds(
                                        bounds = bounds,
                                        rootPosition = rootPosition,
                                        color = pageBackgroundArgb
                                    )
                                    hiddenEntryId = entry.id
                                    onOpen(
                                        entry,
                                        bounds,
                                        backgroundSnapshot?.let {
                                            AnchoredMorphSnapshots(
                                                background = it,
                                                source = sourceSnapshot
                                            )
                                        }
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

private fun Bitmap.clearWindowBounds(
    bounds: androidx.compose.ui.geometry.Rect,
    rootPosition: Offset,
    color: Int
): Bitmap? = runCatching {
    val mutable = copy(Bitmap.Config.ARGB_8888, true)
    val left = (bounds.left - rootPosition.x).coerceIn(0f, mutable.width.toFloat())
    val top = (bounds.top - rootPosition.y).coerceIn(0f, mutable.height.toFloat())
    val right = (bounds.right - rootPosition.x).coerceIn(left, mutable.width.toFloat())
    val bottom = (bounds.bottom - rootPosition.y).coerceIn(top, mutable.height.toFloat())
    android.graphics.Canvas(mutable).drawRect(
        left,
        top,
        right,
        bottom,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    )
    mutable
}.getOrNull()
