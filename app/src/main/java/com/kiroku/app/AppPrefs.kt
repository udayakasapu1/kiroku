package com.kiroku.app

import android.content.Context

/** Small app settings kept outside the database. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("kiroku_prefs", Context.MODE_PRIVATE)

    /** True once the user has finished (or skipped) the first-launch folder setup. */
    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /** "links" or "notes": the space the app reopens in. */
    var lastSpace: String
        get() = prefs.getString(KEY_SPACE, SPACE_LINKS) ?: SPACE_LINKS
        set(value) = prefs.edit().putString(KEY_SPACE, value).apply()

    // ---- Web inbox (Supabase) ----

    var inboxUrl: String
        get() = prefs.getString("inbox_url", "").orEmpty()
        set(value) = prefs.edit().putString("inbox_url", value).apply()

    var inboxAnonKey: String
        get() = prefs.getString("inbox_anon_key", "").orEmpty()
        set(value) = prefs.edit().putString("inbox_anon_key", value).apply()

    /** Secret shared with connected browsers; only its hash is stored in Supabase. */
    var inboxKey: String
        get() = prefs.getString("inbox_key", "").orEmpty()
        set(value) = prefs.edit().putString("inbox_key", value).apply()

    var inboxLastCheck: Long
        get() = prefs.getLong("inbox_last_check", 0L)
        set(value) = prefs.edit().putLong("inbox_last_check", value).apply()

    var inboxLastAdded: Int
        get() = prefs.getInt("inbox_last_added", 0)
        set(value) = prefs.edit().putInt("inbox_last_added", value).apply()

    var inboxLastError: String
        get() = prefs.getString("inbox_last_error", "").orEmpty()
        set(value) = prefs.edit().putString("inbox_last_error", value).apply()

    companion object {
        const val SPACE_LINKS = "links"
        const val SPACE_NOTES = "notes"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_SPACE = "last_space"
    }
}
