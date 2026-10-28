package com.xiaomanjun.sleepdownschedule.transition

/** Published SharedViewKit wire keys; SDK classes stay at the distribution boundary. */
internal object SharedViewKitContract {
    const val Bounds = "sharedView.bounds"
    const val BackType = "sharedView.backAnimType"
    const val FallbackType = "sharedView.fallbackAnimType"
    const val Color = "sharedView.color"
    const val Bitmap = "sharedView.bitmap"
    const val Open = 0
    const val Back = 1
}

internal data class SharedViewKitCapabilities(
    val windowCoordinates: Boolean,
    val missingTargetBackType: Int?,
    val fallbackType: Int?,
    val returnBitmap: Boolean = false
) {
    companion object {
        fun from(extras: Map<String, String>): SharedViewKitCapabilities {
            val bounds = extras[SharedViewKitContract.Bounds].orEmpty()
            val token = bounds.split('|').getOrNull(1)?.trim()?.uppercase()
            val window = when (token) {
                "WINDOW" -> true
                "SCREEN" -> false
                else -> bounds.contains("WINDOW", ignoreCase = true)
            }
            return SharedViewKitCapabilities(
                windowCoordinates = window,
                missingTargetBackType = returnType(extras[SharedViewKitContract.BackType]),
                fallbackType = returnType(extras[SharedViewKitContract.FallbackType]),
                returnBitmap = extras.containsKey(SharedViewKitContract.Bitmap)
            )
        }

        private fun returnType(metadata: String?): Int? {
            if (metadata == null) return null
            val parts = metadata.split('|')
            if (!parts.first().trim().equals("int", ignoreCase = true)) return null
            val types = parts.getOrNull(1)?.split(',')?.map { it.trim() }.orEmpty()
            // A bare int declares a writable parameter with an unknown support set.
            if (types.isEmpty() || types == listOf("")) return 2
            return listOf("VIEW_ANIM_FOLLOW_SYSTEM" to 2, "VIEW_ANIM_FADE_OUT" to 3,
                "VIEW_ANIM_NONE" to 1).firstOrNull { it.first in types }?.second
        }
    }
}
