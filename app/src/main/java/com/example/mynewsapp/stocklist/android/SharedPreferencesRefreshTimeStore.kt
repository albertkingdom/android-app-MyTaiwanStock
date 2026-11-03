package com.example.mynewsapp.stocklist.android

import android.content.Context
import com.example.mynewsapp.stocklist.RefreshTimeStore

/** Keeps the time of the last successful stock list refresh in SharedPreferences. */
class SharedPreferencesRefreshTimeStore(context: Context) : RefreshTimeStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override var lastRefreshMillis: Long?
        get() = read(KEY_LAST_REFRESH)
        set(value) = write(KEY_LAST_REFRESH, value)

    override var lastFailedAttemptMillis: Long?
        get() = read(KEY_LAST_FAILED_ATTEMPT)
        set(value) = write(KEY_LAST_FAILED_ATTEMPT, value)

    private fun read(key: String): Long? =
        if (preferences.contains(key)) preferences.getLong(key, 0L) else null

    /** Written synchronously (called from the refresh worker) so it stays in step with the cache file. */
    private fun write(key: String, value: Long?) {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putLong(key, value)
        editor.commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "stock_list_refresh"
        const val KEY_LAST_REFRESH = "last_refresh_millis"
        const val KEY_LAST_FAILED_ATTEMPT = "last_failed_attempt_millis"
    }
}
