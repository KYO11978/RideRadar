package com.rideradar.app.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ride_radar_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AMAP_KEY = "amap_web_key"
        private const val KEY_RADIUS = "search_radius"
        private const val KEY_VOICE_ENABLED = "voice_enabled"
        private const val KEY_SPEED_ADAPTIVE = "speed_adaptive"
        private const val KEY_FILTER_FOOD = "filter_food"
        private const val KEY_FILTER_SCENIC = "filter_scenic"
    }

    var amapKey: String
        get() = prefs.getString(KEY_AMAP_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AMAP_KEY, value).apply()

    var searchRadius: Int
        get() = prefs.getInt(KEY_RADIUS, 50)
        set(value) = prefs.edit().putInt(KEY_RADIUS, value).apply()

    var isVoiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ENABLED, value).apply()

    var isSpeedAdaptive: Boolean
        get() = prefs.getBoolean(KEY_SPEED_ADAPTIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEED_ADAPTIVE, value).apply()

    var isFilterFood: Boolean
        get() = prefs.getBoolean(KEY_FILTER_FOOD, true)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_FOOD, value).apply()

    var isFilterScenic: Boolean
        get() = prefs.getBoolean(KEY_FILTER_SCENIC, true)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_SCENIC, value).apply()
}
