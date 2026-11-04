package com.example.liuruyi.data

import android.content.Context

/** 简单偏好：天气城市等。 */
object AppPrefs {
    private const val PREFS = "liuruyi_prefs"
    private const val KEY_CITY = "weather_city"

    fun city(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CITY, "").orEmpty()

    fun setCity(context: Context, city: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CITY, city.trim()).apply()
    }
}
