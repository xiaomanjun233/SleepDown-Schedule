package com.xiaomanjun.sleepdownschedule

/**
 * Light obfuscation for the bundled quota credential. This prevents plain-text extraction only;
 * quota enforcement and abuse prevention must remain server-side.
 */
internal object ManagedFreeAiCredentials {
    private val payload = intArrayOf(
        46, 233, 138, 130, 190, 119, 81, 24, 243, 206, 128, 146, 92, 119, 22, 193, 227,
        151, 144, 120, 15, 84, 252, 231, 231, 201, 40, 35, 16, 195, 196, 174, 168, 78,
        13, 25, 221, 194, 150, 119, 118, 7, 14, 227, 232, 149, 58, 102, 11, 27, 229
    )

    fun apiKey(): String = buildString(payload.size) {
        payload.forEachIndexed { index, encoded ->
            append((encoded xor ((0x5d + index * 37) and 0xff)).toChar())
        }
    }
}
