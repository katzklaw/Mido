package app.mido.pro

import app.mido.BuildConfig

/**
 * The public half of your licence-signing key (base64, X.509). Put it in local.properties as
 *     PRO_PUBLIC_KEY=<the key printed by `java tools/KeyTool.java init`>
 * so that unzipping a newer Mido project never wipes it. While it is empty, no licence key can unlock Pro
 * (a build with PRO_UNLOCKED=true in local.properties still can).
 */
object ProKeys {
    /** Only used if local.properties has no PRO_PUBLIC_KEY. */
    private const val PASTED_KEY_B64 = ""

    val PUBLIC_KEY_B64: String get() = BuildConfig.PRO_PUBLIC_KEY.ifBlank { PASTED_KEY_B64 }
}
