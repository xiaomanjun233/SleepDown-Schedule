package com.xiaomanjun.sleepdownschedule.feature.importing

import com.xiaomanjun.sleepdownschedule.core.ui.settings.*
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.LocalCenteredDialogSceneBackdrop
import com.xiaomanjun.sleepdownschedule.core.ui.designsystem.sleepDownPanelForegroundColor
import com.xiaomanjun.sleepdownschedule.glass.ui.*
import com.xiaomanjun.sleepdownschedule.feature.home.day.*

import com.xiaomanjun.sleepdownschedule.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@Stable
internal class AiRuntimePickerState(initialSettings: AiImportSettings) {
    var settings by mutableStateOf(initialSettings)
        private set
    var expanded by mutableStateOf(false)
        private set
    internal var revision by mutableIntStateOf(0)

    fun open() {
        expanded = true
    }

    fun dismiss() {
        expanded = false
    }

    internal fun commit(next: AiImportSettings) {
        settings = next
        revision++
    }
}

@Composable
internal fun rememberAiRuntimePickerState(): AiRuntimePickerState {
    val context = LocalContext.current
    return remember(context) { AiRuntimePickerState(AiImportSettingsStore.load(context)) }
}

@Composable
internal fun AiRuntimePicker(
    state: AiRuntimePickerState,
    config: ScheduleConfigEntity,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    labelMaxWidth: Dp = 104.dp,
    embedded: Boolean = false,
    foregroundOverride: Color? = null,
    onSettingsChanged: (AiImportSettings) -> Unit = {}
) {
    val context = LocalContext.current
    // Settings pages expose their complete root underlay through LocalSettingsPopupBackdrop. The
    // home Agent has no settings scaffold, so use the same complete dialog-scene producer that its
    // root Miuix host keeps outside the popup tree before falling back to the trigger's local glass.
    val popupBackdrop = LocalSettingsPopupBackdrop.current
        ?: LocalCenteredDialogSceneBackdrop.current
        ?: backdrop
    val foreground = foregroundOverride ?: if (embedded) {
        glassForegroundColor(config)
    } else {
        sleepDownPanelForegroundColor(config)
    }
    // The cascading surface owns the final popup tint. Resolve its rows against that actual
    // light/dark glass surface instead of inheriting a possibly different composer foreground.
    val popupForeground = if (appUsesDarkTheme(config)) {
        Color.White
    } else {
        Color(0xFF111111)
    }
    val lightGlass = glassUsesLightStyle(config)
    val profiles = remember(state.revision) {
        AiImportSettingsStore.selectableProfiles(context)
            .filter { it.id != AiProviderPresets.none.id }
            .filter { profile ->
                AiImportSettingsStore.loadProvider(context, profile.id).apiKey.isNotBlank()
            }
    }
    val modelOptions = remember(state.settings.profile, state.revision) {
        AiProviderPresets.modelOptions(state.settings.profile)
    }
    val efforts = remember(state.settings.profile, state.revision) {
        AiProviderPresets.reasoningEfforts(state.settings.profile)
    }
    val responsesEnabled = AiProviderPresets.shouldUseResponses(state.settings.profile)

    fun commit(next: AiImportSettings) {
        AiImportSettingsStore.save(context, next)
        state.commit(next)
        onSettingsChanged(next)
    }

    val menuItems = listOf(
        SleepDownLiquidMenuItem(
            key = "provider",
            text = "供应商",
            summary = state.settings.profile.displayName,
            enabled = profiles.isNotEmpty(),
            children = profiles.map { profile ->
                SleepDownLiquidMenuItem(
                    key = "provider-${profile.id}",
                    text = profile.displayName,
                    selected = profile.id == state.settings.profile.id,
                    onClick = {
                        commit(AiImportSettingsStore.loadProvider(context, profile.id))
                    }
                )
            }
        ),
        SleepDownLiquidMenuItem(
            key = "model",
            text = "模型",
            summary = state.settings.profile.defaultModel,
            enabled = modelOptions.isNotEmpty(),
            children = modelOptions.mapIndexed { index, option ->
                SleepDownLiquidMenuItem(
                    key = "model-$index-${option.model}",
                    text = option.model,
                    selected = option.model.equals(
                        state.settings.profile.defaultModel,
                        ignoreCase = true
                    ),
                    onClick = {
                        val candidate = state.settings.profile.copy(defaultModel = option.model)
                        val supportedEfforts = AiProviderPresets.reasoningEfforts(candidate)
                        val nextProfile = candidate.copy(
                            reasoningEffort = candidate.reasoningEffort.takeIf { it in supportedEfforts }
                                ?: supportedEfforts.firstOrNull()
                                ?: candidate.reasoningEffort
                        )
                        commit(state.settings.copy(profile = nextProfile))
                    }
                )
            }
        ),
        SleepDownLiquidMenuItem(
            key = "reasoning",
            text = "思考强度",
            enabled = responsesEnabled && efforts.isNotEmpty(),
            summary = if (responsesEnabled) {
                state.settings.profile.reasoningEffort.label
            } else {
                "需启用 Responses"
            },
            children = if (responsesEnabled) {
                efforts.map { effort ->
                    SleepDownLiquidMenuItem(
                        key = "reasoning-${effort.name}",
                        text = effort.label,
                        selected = effort == state.settings.profile.reasoningEffort,
                        onClick = {
                            commit(
                                state.settings.copy(
                                    profile = state.settings.profile.copy(reasoningEffort = effort)
                                )
                            )
                        }
                    )
                }
            } else {
                emptyList()
            }
        )
    )
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier = Modifier
            .wrapContentSize()
            .onGloballyPositioned { anchorBounds = it.boundsInRoot() }
    ) {
        val shape = RoundedCornerShape(50)
        val labelContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.settings.profile.defaultModel.ifBlank {
                        state.settings.profile.displayName
                    },
                    color = foreground.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = labelMaxWidth)
                )
            }
        }
        if (embedded) {
            Box(
                modifier = modifier
                    .heightIn(min = 32.dp)
                    .clip(shape)
                    .background(foreground.copy(alpha = 0.045f))
                    .border(
                        width = 0.75.dp,
                        color = foreground.copy(alpha = 0.14f),
                        shape = shape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = state::open
                    )
            ) {
                labelContent()
            }
        } else {
            GlassSurface(
                backdrop = backdrop,
                config = config,
                modifier = modifier.heightIn(min = 32.dp),
                shape = shape,
                tokens = GlassTokens.pill(intensity = 0.72f).copy(
                    blur = 8.dp,
                    surfaceAlpha = if (lightGlass) 0.14f else 0.10f,
                    shadowAlpha = 0.06f
                ),
                baseSurfaceColorOverride = if (lightGlass) {
                    Color.White
                } else {
                    Color(0xFF111318)
                },
                onClick = state::open
            ) {
                labelContent()
            }
        }
        SleepDownLiquidCascadingPopup(
            show = state.expanded,
            anchorBounds = anchorBounds,
            items = menuItems,
            onDismissRequest = state::dismiss,
            backdrop = popupBackdrop,
            config = config,
            contentColor = popupForeground
        )
    }
}
