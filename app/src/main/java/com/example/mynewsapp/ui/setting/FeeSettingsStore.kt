package com.example.mynewsapp.ui.setting

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.example.mynewsapp.tradecost.FeeSettings

/**
 * Reads and writes the fee settings in the default shared preferences, the same file that holds the earlier
 * "fee_discount" and "fee_custom" settings. Those are read as the rules of [FeeSettings.fromStored] say and
 * are never changed.
 */
class FeeSettingsStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context))

    fun load(): FeeSettings = FeeSettings.fromStored(preferences.all)

    /** Writes the discount and both minimum fees together. */
    fun save(settings: FeeSettings) {
        val editor = preferences.edit()
        settings.writeTo(object : FeeSettings.PreferenceWriter {
            override fun putString(key: String, value: String) {
                editor.putString(key, value)
            }

            override fun putInt(key: String, value: Int) {
                editor.putInt(key, value)
            }
        })
        editor.apply()
    }
}
