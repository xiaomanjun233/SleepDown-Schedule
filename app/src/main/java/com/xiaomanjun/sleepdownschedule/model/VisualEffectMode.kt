package com.xiaomanjun.sleepdownschedule.model

enum class VisualEffectMode(val id: String, val label: String) {
    LiquidGlass("liquid_glass", "液态玻璃"),
    ColorOsGlow("coloros_glow", "凝光视效");

    companion object {
        /** 凝光跨品牌可用；未保存偏好时默认启用，已保存的旧值仍按 id 保留。 */
        fun fromId(id: String?): VisualEffectMode = entries.firstOrNull { it.id == id } ?: ColorOsGlow
    }
}
