package com.xiaomanjun.sleepdownschedule.model

enum class VisualEffectMode(val id: String, val label: String) {
    LiquidGlass("liquid_glass", "液态玻璃"),
    ColorOsGlow("coloros_glow", "凝光视效");

    companion object {
        fun fromId(id: String?): VisualEffectMode = entries.firstOrNull { it.id == id } ?: LiquidGlass
    }
}

fun effectiveVisualEffectMode(selected: VisualEffectMode, colorOsAvailable: Boolean): VisualEffectMode =
    if (selected == VisualEffectMode.ColorOsGlow && !colorOsAvailable) VisualEffectMode.LiquidGlass else selected
