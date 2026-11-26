package com.lagradost.cloudstream3

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.ui.setup.HAS_DONE_SETUP_KEY

object AnshDefaults {
    private const val SEEDED_KEY = "ansh_defaults_seeded_v1"

    fun apply(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(SEEDED_KEY, false)) return

        val booleans = mapOf(
            R.string.speedup_key to true,
            R.string.double_tap_enabled_key to true,
            R.string.playback_speed_enabled_key to true,
            R.string.compressor_enabled_key to true,
            R.string.show_name_key to false,
            R.string.show_resolution_key to false,
            R.string.enable_nsfw_on_providers_key to false
        )

        prefs.edit {
            booleans.forEach { (res, value) ->
                val key = context.getString(res)
                if (!prefs.contains(key)) putBoolean(key, value)
            }
            val langKey = context.getString(R.string.provider_lang_key)
            if (!prefs.contains(langKey)) putStringSet(langKey, setOf("hi"))
            val autoKey = context.getString(R.string.auto_download_plugins_key)
            if (!prefs.contains(autoKey)) putInt(autoKey, AutoDownloadMode.FilterByLang.value)
            val localeKey = context.getString(R.string.locale_key)
            if (!prefs.contains(localeKey)) putString(localeKey, "en")
            putBoolean(SEEDED_KEY, true)
        }
        setKey(HAS_DONE_SETUP_KEY, true)
    }
}
