package com.example.courseschedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt

class LiquidGlassTuningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CourseScheduleTheme(config = defaultConfig()) {
                LiquidGlassTuningScreen()
            }
        }
    }
}

private data class LiquidGlassTuningValues(
    val cornerRadius: Float = 28f,
    val blurRadius: Float = 2f,
    val refractionHeight: Float = 12f,
    val refractionAmount: Float = 24f,
    val surfaceAlpha: Float = 0.18f,
    val highlightAlpha: Float = 0.06f,
    val shadowAlpha: Float = 0.16f,
    val innerShadowAlpha: Float = 0.12f,
    val chromaticAberration: Boolean = false,
    val depthEffect: Boolean = true,
    val useVibrancy: Boolean = true
)

@Composable
private fun LiquidGlassTuningScreen() {
    var cornerRadius by rememberSaveable { mutableFloatStateOf(28f) }
    var blurRadius by rememberSaveable { mutableFloatStateOf(2f) }
    var refractionHeight by rememberSaveable { mutableFloatStateOf(12f) }
    var refractionAmount by rememberSaveable { mutableFloatStateOf(24f) }
    var surfaceAlpha by rememberSaveable { mutableFloatStateOf(0.18f) }
    var highlightAlpha by rememberSaveable { mutableFloatStateOf(0.06f) }
    var shadowAlpha by rememberSaveable { mutableFloatStateOf(0.16f) }
    var innerShadowAlpha by rememberSaveable { mutableFloatStateOf(0.12f) }
    var chromaticAberration by rememberSaveable { mutableStateOf(false) }
    var depthEffect by rememberSaveable { mutableStateOf(true) }
    var useVibrancy by rememberSaveable { mutableStateOf(true) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var rotation by rememberSaveable { mutableFloatStateOf(0f) }

    val values = LiquidGlassTuningValues(
        cornerRadius = cornerRadius,
        blurRadius = blurRadius,
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        surfaceAlpha = surfaceAlpha,
        highlightAlpha = highlightAlpha,
        shadowAlpha = shadowAlpha,
        innerShadowAlpha = innerShadowAlpha,
        chromaticAberration = chromaticAberration,
        depthEffect = depthEffect,
        useVibrancy = useVibrancy
    )
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            LiquidTuningBackdrop()
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("液态玻璃调参", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("拖动、双指缩放或旋转中间预览块", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TunableGlassSurface(
            backdrop = backdrop,
            values = values,
            shape = RoundedCornerShape(cornerRadius.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotate ->
                        offsetX += pan.x
                        offsetY += pan.y
                        scale = (scale * zoom).coerceIn(0.55f, 1.75f)
                        rotation += rotate
                    }
                }
                .size(210.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatTuningFloat(refractionHeight)} / ${formatTuningFloat(refractionAmount)} lens",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        TunableGlassSurface(
            backdrop = backdrop,
            values = values.copy(
                cornerRadius = 28f,
                blurRadius = 4f.coerceAtLeast(blurRadius * 0.75f),
                refractionHeight = 14f,
                refractionAmount = 28f,
                surfaceAlpha = 0.34f
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(14.dp)
                .fillMaxWidth()
                .heightIn(max = 430.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("实时参数", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        cornerRadius = 28f
                        blurRadius = 2f
                        refractionHeight = 12f
                        refractionAmount = 24f
                        surfaceAlpha = 0.18f
                        highlightAlpha = 0.06f
                        shadowAlpha = 0.16f
                        innerShadowAlpha = 0.12f
                        chromaticAberration = false
                        depthEffect = true
                        useVibrancy = true
                        offsetX = 0f
                        offsetY = 0f
                        scale = 1f
                        rotation = 0f
                    }) {
                        Text("Reset")
                    }
                }
                TuningSlider("Corner radius", cornerRadius, 0f..52f) { cornerRadius = it }
                TuningSlider("Blur radius", blurRadius, 0f..24f) { blurRadius = it }
                TuningSlider("Refraction height", refractionHeight, 0f..40f) { refractionHeight = it }
                TuningSlider("Refraction amount", refractionAmount, 0f..64f) { refractionAmount = it }
                TuningSlider("Surface alpha", surfaceAlpha, 0f..0.72f) { surfaceAlpha = it }
                TuningSlider("Highlight alpha", highlightAlpha, 0f..0.45f) { highlightAlpha = it }
                TuningSlider("Shadow alpha", shadowAlpha, 0f..0.55f) { shadowAlpha = it }
                TuningSlider("Inner shadow alpha", innerShadowAlpha, 0f..0.45f) { innerShadowAlpha = it }
                TuningToggle("Chromatic aberration", chromaticAberration) { chromaticAberration = it }
                TuningToggle("Depth effect", depthEffect) { depthEffect = it }
                TuningToggle("Vibrancy", useVibrancy) { useVibrancy = it }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val text = values.asGlassTokensSource()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("GlassTokens", text))
                        Toast.makeText(context, "已复制当前参数", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("复制当前参数")
                }
            }
        }
    }
}

@Composable
private fun LiquidTuningBackdrop() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFBFE4FF),
                        Color(0xFFFFD7E4),
                        Color(0xFFEDE7FF),
                        Color(0xFFE9F9D8)
                    )
                )
            )
    ) {
        val colors = listOf(Color(0xFF007AFF), Color(0xFFFF2D55), Color(0xFF34C759), Color(0xFFFF9500))
        repeat(10) { index ->
            Box(
                modifier = Modifier
                    .align(if (index % 2 == 0) Alignment.TopStart else Alignment.BottomEnd)
                    .padding(
                        start = (18 + index * 23).dp,
                        top = (72 + index * 31).dp,
                        end = (22 + index * 17).dp,
                        bottom = (84 + index * 21).dp
                    )
                    .size((64 + index * 7).dp)
                    .clip(RoundedCornerShape((18 + index % 4 * 8).dp))
                    .background(colors[index % colors.size].copy(alpha = 0.28f))
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(5) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index % 2 == 0) 0.82f else 0.62f)
                        .height((18 + index * 2).dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.38f))
                )
            }
        }
    }
}

@Composable
private fun TunableGlassSurface(
    backdrop: Backdrop?,
    values: LiquidGlassTuningValues,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val useGlass = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = values.surfaceAlpha.coerceIn(0f, 1f))
    val glassModifier = if (useGlass) {
        modifier.drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                if (values.useVibrancy) vibrancy()
                blur(values.blurRadius.dp.toPx())
                lens(
                    values.refractionHeight.dp.toPx(),
                    values.refractionAmount.dp.toPx(),
                    depthEffect = values.depthEffect,
                    chromaticAberration = values.chromaticAberration
                )
            },
            highlight = {
                if (values.highlightAlpha <= 0.001f) Highlight.Plain else Highlight.Default.copy(alpha = values.highlightAlpha)
            },
            shadow = { Shadow(alpha = values.shadowAlpha) },
            innerShadow = { InnerShadow(radius = 8.dp, alpha = values.innerShadowAlpha) },
            onDrawSurface = {
                drawRect(surfaceColor)
                drawRect(Color.White.copy(alpha = 0.012f), blendMode = BlendMode.Screen)
            }
        )
    } else {
        modifier
            .clip(shape)
            .background(surfaceColor.copy(alpha = surfaceColor.alpha.coerceAtLeast(0.86f)))
    }
    Box(modifier = glassModifier) {
        content()
    }
}

@Composable
private fun TuningSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(formatTuningFloat(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun TuningToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun LiquidGlassTuningValues.asGlassTokensSource(): String = """
GlassTokens(
    blur = ${formatTuningFloat(blurRadius)}.dp,
    lensHeight = ${formatTuningFloat(refractionHeight)}.dp,
    lensAmount = ${formatTuningFloat(refractionAmount)}.dp,
    surfaceAlpha = ${formatTuningFloat(surfaceAlpha)}f,
    borderAlpha = 0.24f,
    highlightAlpha = ${formatTuningFloat(highlightAlpha)}f,
    shadowAlpha = ${formatTuningFloat(shadowAlpha)}f,
    innerShadowAlpha = ${formatTuningFloat(innerShadowAlpha)}f,
    chromaticAberration = $chromaticAberration,
    depthEffect = $depthEffect,
    useVibrancy = $useVibrancy
)
""".trimIndent()

private fun formatTuningFloat(value: Float): String = "%.2f".format(java.util.Locale.US, value)
