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

    companion object {
        const val SPACE_LINKS = "links"
        const val SPACE_NOTES = "notes"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_SPACE = "last_space"
    }
}
