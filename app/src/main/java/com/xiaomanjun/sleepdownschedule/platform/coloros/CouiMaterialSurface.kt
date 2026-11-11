package com.xiaomanjun.sleepdownschedule.platform.coloros

import android.graphics.BlendMode
import android.graphics.Paint
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipPath
import com.xiaomanjun.sleepdownschedule.glass.courseTexture
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import com.itos.coui.backdrop.drawCouiBackdrop
import com.itos.coui.effect.CouiContour
import com.itos.coui.effect.CouiContourStyle
import com.itos.coui.effect.CouiSpotlightTouch
import com.itos.coui.effect.SpotlightPreset
import com.itos.coui.effect.couiControlMaterialEffect
import com.itos.coui.effect.drawCouiCaustic
import com.itos.coui.effect.rememberCouiSpotlight
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.navigation.CouiDockStyle
import com.itos.coui.topappbar.CouiTopAppBarButtonDefaults
import com.xiaomanjun.sleepdownschedule.core.performance.AppMaterialPreferences
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeMaterialSurfaceActive
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalNativeCourseCardMode
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRequest
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.NativeMaterialSurfaceRole
import com.xiaomanjun.sleepdownschedule.core.ui.material.LocalHomeMaterialStyle
import com.xiaomanjun.sleepdownschedule.glass.CourseMaterialSampling
import com.xiaomanjun.sleepdownschedule.glass.LocalGlassSampleRecordKey
import com.xiaomanjun.sleepdownschedule.glass.drawPreparedCourseBackdrop
import com.xiaomanjun.sleepdownschedule.glass.rememberCouiGlassBackdrop
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

// COUIMaterialBlurEffect.Companion resolves the transparent-button preset to 25px
// with the same two color layers in both themes (Wallpapers 17.04.0034, classes2.dex).
internal const val CouiCourseBlurRadiusPx = 25f

private fun couiCourseMaterial(dark: Boolean) =
    CouiTopAppBarButtonDefaults.systemMaterial(dark, capsule = false).copy(
        blurRadius = CouiCourseBlurRadiusPx,
        firstColor = Color(0x3F333333),
        firstBlendMode = BlendMode.OVERLAY,
        secondColor = Color(0x3FFFFFFF),
        secondBlendMode = BlendMode.LUMINOSITY,
    )

@RequiresApi(31)
internal fun couiCourseBackdropEffect(dark: Boolean, radiusPx: Float) =
    couiControlMaterialEffect(couiCourseMaterial(dark).copy(blurRadius = radiusPx)).asComposeRenderEffect()

@RequiresApi(34)
@Composable
internal fun CouiCourseCard(request: NativeMaterialSurfaceRequest, content: @Composable () -> Unit): Boolean {
    val mode = LocalNativeCourseCardMode.current ?: return false
    val configuredBlurScale = if (request.blurRadius == null) mode.blurRadiusScale else 1f
    return CouiMaterialSurface(request.copy(blurRadiusScale = configuredBlurScale * request.blurRadiusScale),
        course = true, content = content)
}

/** Keep the background display list independent of caret, text and child controls. */
@RequiresApi(34)
@Composable
internal fun CouiMaterialSurface(
    request: NativeMaterialSurfaceRequest,
    course: Boolean = false,
    content: @Composable () -> Unit,
): Boolean {
    val source = request.backdrop
    if (source == null && !course) return false
    if (AppMaterialPreferences.isSuperPerformance) return false
    val home = LocalHomeMaterialStyle.current
    val dock = request.role == NativeMaterialSurfaceRole.HomeDock
    val indicator = request.role == NativeMaterialSurfaceRole.PageIndicator
    val menu = request.role == NativeMaterialSurfaceRole.Menu
    val dialog = request.role == NativeMaterialSurfaceRole.Dialog
    val dark = if (dock) home?.dark ?: request.dark else request.dark
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val baseMaterial = remember(dark, course, dock, indicator, menu, dialog, home?.weatherMaterial, density) {
        if (course) {
            couiCourseMaterial(dark)
        } else if (dock && home?.weatherMaterial != true) {
            CouiDockStyle.reference(dark, Color.Unspecified, Color.Unspecified, density.density).material
        } else {
            CouiTopAppBarButtonDefaults.systemMaterial(dark, capsule = false).copy(
                blurRadius = when {
                    dialog -> 200f
                    menu -> 160f
                    indicator -> 120f
                    else -> CouiCourseBlurRadiusPx
                },
                firstColor = when {
                    indicator -> Color(0x3DFFFFFF)
                    menu && home?.weatherMaterial != true -> Color(if (dark) 0x99262626 else 0xBFEEEEEE)
                    dialog -> Color(if (dark) 0xB2292929 else 0x509F9F9F)
                    else -> Color(0x3F333333)
                },
                firstBlendMode = if (indicator) BlendMode.SRC_OVER else BlendMode.OVERLAY,
                secondColor = when {
                    indicator -> Color.Transparent
                    menu && home?.weatherMaterial != true -> Color(if (dark) 0x99262626 else 0x99CDCDCD)
                    dialog -> Color(if (dark) 0x8C777777 else 0xDC909090)
                    else -> Color(0x3FFFFFFF)
                },
                secondBlendMode = if (indicator) BlendMode.SRC_OVER else BlendMode.LUMINOSITY,
            )
        }
    }
    val latest = rememberUpdatedState(request)
    val shape = remember { { latest.value.shapeProvider?.invoke() ?: latest.value.shape } }
    val lighting = request.renderSurface && request.materialVisible && request.edgeEffects
    val radius = request.followLightRadius?.let { with(density) { it.toPx() } }
    val (observeTouch, drawLight) = if (lighting && request.followTouch) {
        rememberCouiSpotlight(
            interactive = true,
            preset = when {
                menu -> SpotlightPreset.TranslucentLarge1
                indicator -> SpotlightPreset.TransparentSmall1
                course -> SpotlightPreset.OpaqueMedium2
                else -> SpotlightPreset.OpaqueSmall1
            },
            dark = dark,
            intensity = when {
                course -> if (dark) 0.15f else 1f
                menu -> if (dark) 0.65f else 0.8f
                indicator -> 1f
                else -> if (dark) 0.35f else 0.45f
            } * request.followLightIntensityScale,
            startRadius = radius?.times(0.2f),
            endRadius = radius,
            scaleRadiusToBounds = course && radius == null,
            cancelOnConsumedMove = true,
            externalTouch = request.externalTouch?.let { touch ->
                { touch().let { CouiSpotlightTouch(it.position, it.pressed) } }
            },
            acceptsGesture = { bounds, position ->
                latest.value.acceptsGesture(Size(bounds.width.toFloat(), bounds.height.toFloat()), position)
            },
        )
    } else Modifier to null
    val motion = if (request.elasticTouchFeedback) rememberCouiTouchMotion(expanded = true) else null
    val causticTint = request.causticTint
    val flatBacking = request.flatBackdropColor
    val preparedBackdrop = request.preparedBackdrop
    val wallpaperSampling = preparedBackdrop as? CourseMaterialSampling
    val needsLiveSample = preparedBackdrop == null ||
        (lighting && causticTint != null && flatBacking == null && wallpaperSampling?.hasStaticBitmap != true)
    val backdrop = if (needsLiveSample && source != null && request.renderSurface && request.materialVisible) {
        rememberCouiGlassBackdrop(source)
    } else null
    val samplingKey = LocalGlassSampleRecordKey.current
    val editorEffects = if (course && request.blurRadius != null) remember {
        linkedMapOf<com.itos.coui.effect.CouiFloatingMaterial, androidx.compose.ui.graphics.RenderEffect>()
    } else null
    val caustic = if (course && lighting && source != null && causticTint != null) {
        val projection = remember(causticTint, density.density) {
            val fabShadow = CouiFabDefaults.shadow(causticTint, density.density, contourGlowEnabled = true)
            val side = with(density) { 2.dp.toPx() }
            val bottom = with(density) { 7.5.dp.toPx() }
            fabShadow.copy(layout = Rect(-side, fabShadow.layout.top, side, bottom))
        }
        val cornerRadius: (Size) -> Float? = { size -> colorOsMaterialCorners(shape(), size, density, direction)?.radius }
        if (flatBacking != null) {
            Modifier.drawCouiCaustic(flatBacking, cornerRadius, projection)
        } else if (wallpaperSampling?.hasStaticBitmap == true) {
            Modifier.drawCouiCaustic(
                sample = { coordinates, padding -> wallpaperSampling.textureRenderer.shader(
                    wallpaperSampling, coordinates, 0f, androidx.compose.ui.geometry.Offset(-padding, -padding)) },
                redraw = samplingKey, cornerRadius = cornerRadius, shadow = projection,
                programs = wallpaperSampling.causticPrograms)
        } else if (backdrop != null) {
            Modifier.drawCouiCaustic(backdrop, cornerRadius, projection,
                sampleScale = if (preparedBackdrop != null) 0.5f else 1f)
        } else Modifier
    } else Modifier
    val background = if (preparedBackdrop != null && request.renderSurface && request.materialVisible) {
        if (wallpaperSampling?.supportsTexture == true) Modifier.courseTexture(wallpaperSampling, shape,
            onTexture = {
                // These native blend modes are the same two operations as the old filter chain.
                // The wallpaper is opaque, so they need no per-card offscreen surface.
                val canvas = drawContext.canvas.nativeCanvas
                canvas.drawColor(baseMaterial.firstColor.toArgb(), baseMaterial.firstBlendMode)
                canvas.drawColor(baseMaterial.secondColor.toArgb(), baseMaterial.secondBlendMode)
            })
        else Modifier.drawPreparedCourseBackdrop(preparedBackdrop, samplingKey)
    } else if (backdrop != null && request.blurRadiusScale > 0f) {
        val tintScale = if (request.role == NativeMaterialSurfaceRole.Form) 0f else request.tintAlphaScale
        val sampleScale = request.backdropSampleScale
        val material = baseMaterial.copy(
            blurRadius = (request.blurRadius?.let { with(density) { it.toPx() } } ?: baseMaterial.blurRadius) *
                request.blurRadiusScale * sampleScale,
            firstColor = baseMaterial.firstColor.copy(alpha = baseMaterial.firstColor.alpha * tintScale),
            secondColor = baseMaterial.secondColor.copy(alpha = baseMaterial.secondColor.alpha * tintScale),
            sampleBrightness = if (course || indicator) request.sampleBrightness
                else home?.sampleBrightness ?: request.sampleBrightness,
        )
        val effect = remember(material) {
            if (editorEffects == null) {
                couiControlMaterialEffect(material).asComposeRenderEffect()
            } else editorEffects.getOrPut(material) {
                if (editorEffects.size >= 6) editorEffects.remove(editorEffects.keys.first())
                couiControlMaterialEffect(material).asComposeRenderEffect()
            }
        }
        // The parent owns clipping. An additional offscreen wrapper would rasterize the same
        // animated surface again, after the sampled texture has already been blurred.
        Modifier.drawCouiBackdrop(backdrop, shape = null, effects = {
            padding = material.blurRadius
            renderEffect = effect
            this.sampleScale = sampleScale
        })
    } else Modifier
    val sharedContour = course && preparedBackdrop is CourseMaterialSampling
    val contour = if (lighting && !sharedContour) remember(course, menu, dialog) {
        when {
            course -> CouiContour(style = CouiContourStyle.LauncherFolder)
            menu -> CouiContour()
            dialog -> CouiContour(style = CouiContourStyle.Dialog)
            else -> CouiContour(style = CouiContourStyle.MaterialCircle)
        }
    } else null
    val paint = if (lighting) remember { Paint(Paint.ANTI_ALIAS_FLAG) } else null
    val draw = rememberUpdatedState(drawLight)
    val decoration = Modifier.drawWithCache {
        val corners = if (lighting) colorOsMaterialCorners(shape(), size, this, layoutDirection) else null
        val painter = if (sharedContour && corners != null)
            wallpaperSampling!!.contourPrograms.forGeometry(size, this.density, corners.radius, corners.weight) else contour
        if (corners != null && paint != null && painter != null) {
            paint.shader = painter.shader(size, this.density, corners.radius,
                if (menu) corners.weight.coerceAtLeast(2f) else corners.weight,
                Color.Transparent, when {
                    course -> 0.1f
                    !menu && !dialog -> 0.3f
                    else -> baseMaterial.innerGlowAlpha
                }, when {
                    course -> 0.35f
                    !menu && !dialog -> 0.25f
                    else -> baseMaterial.edgeAlpha
                }, 1f, lineWidthPx = 2.5f * this.density)
        }
        onDrawBehind {
            latest.value.onDrawTint?.invoke(this)
            if (corners != null && paint != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
            draw.value?.invoke(this)
        }
    }

    if (course && preparedBackdrop != null && motion == null) {
        // Dense cards have one drawing owner. Caustics remain outside the outline; body,
        // contour and content share the same clip without three permanent RenderNodes.
        val viewport = com.xiaomanjun.sleepdownschedule.glass.LocalCourseBackdropViewport.current
        val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val card = request.modifier.onGloballyPositioned { coordinates[0] = it }
            .drawWithContent {
                if (coordinates[0]?.let { viewport?.intersects(it) } != false) drawContent()
            }.then(observeTouch)
            .then(request.onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier)
            .then(caustic)
            .drawWithCache {
                val path = Path().apply { addOutline(shape().createOutline(size, layoutDirection, this@drawWithCache)) }
                onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
            }
            .then(background)
            .then(if (request.renderSurface) decoration else Modifier)
        Box(card) {
            CompositionLocalProvider(LocalNativeMaterialSurfaceActive provides true) { content() }
        }
        return true
    }
    Box(
        request.modifier
            .then(observeTouch)
            .then(if (motion == null) Modifier else Modifier.couiTouchMotion(motion, expanded = true) { position ->
                motion.coordinates?.size?.takeIf { bounds ->
                    latest.value.acceptsGesture(Size(bounds.width.toFloat(), bounds.height.toFloat()), position)
                }
            })
            .graphicsLayer {
                this.shape = shape()
                clip = !course
                if (motion != null) {
                    scaleX = motion.scaleX.value
                    scaleY = motion.scaleY.value
                    translationX = motion.translationX.value
                    translationY = motion.translationY.value
                }
            }
            .then(request.onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier),
    ) {
        if (request.renderSurface) {
            if (caustic != Modifier) {
                Spacer(Modifier.matchParentSize().graphicsLayer {
                    alpha = latest.value.surfaceAlpha()
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }.then(caustic))
            }
            Spacer(Modifier.matchParentSize()
                .graphicsLayer {
                    alpha = latest.value.surfaceAlpha()
                    if (course) {
                        this.shape = shape()
                        clip = true
                    }
                }
                .then(background)
                .then(decoration))
        }
        CompositionLocalProvider(LocalNativeMaterialSurfaceActive provides true) {
            if (course) Box(Modifier.graphicsLayer { this.shape = shape(); clip = true },
                propagateMinConstraints = true) { content() }
            else content()
        }
    }
    return true
}
