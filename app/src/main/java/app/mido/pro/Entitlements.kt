package app.mido.pro

import android.content.Context
import app.mido.BuildConfig

/** The one question every paid feature asks: is Pro unlocked? */
object Entitlements {
    private const val PREFS = "mido_pro"
    private const val KEY = "license_key"

    /** True for a developer build (PRO_UNLOCKED=true in local.properties) or a valid licence key. */
    fun isPro(context: Context): Boolean = BuildConfig.PRO_UNLOCKED || license(context) != null

    val isDeveloperBuild: Boolean get() = BuildConfig.PRO_UNLOCKED

    fun license(context: Context): LicenseKey.License? {
        val stored = prefs(context).getString(KEY, null) ?: return null
        return LicenseKey.verify(stored, ProKeys.PUBLIC_KEY_B64)
    }

    /** Checks and stores [key]. Returns the licence, or null (nothing stored) when the key isn't valid. */
    fun activate(context: Context, key: String): LicenseKey.License? {
        val license = LicenseKey.verify(key, ProKeys.PUBLIC_KEY_B64) ?: return null
        prefs(context).edit().putString(KEY, key.filter { !it.isWhitespace() }).apply()
        return license
    }

    fun deactivate(context: Context) = prefs(context).edit().remove(KEY).apply()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
