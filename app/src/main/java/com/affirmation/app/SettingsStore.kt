package com.affirmation.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("affirmation_settings", Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var repeatCount: Int
        get() = prefs.getInt(KEY_REPEAT_COUNT, 3)
        set(value) = prefs.edit().putInt(KEY_REPEAT_COUNT, value).apply()

    var intervalSeconds: Int
        get() = prefs.getInt(KEY_INTERVAL_SECONDS, 5)
        set(value) = prefs.edit().putInt(KEY_INTERVAL_SECONDS, value).apply()

    var randomMinMinutes: Int
        get() = prefs.getInt(KEY_RANDOM_MIN, 30)
        set(value) = prefs.edit().putInt(KEY_RANDOM_MIN, value).apply()

    var randomMaxMinutes: Int
        get() = prefs.getInt(KEY_RANDOM_MAX, 120)
        set(value) = prefs.edit().putInt(KEY_RANDOM_MAX, value).apply()

    var bluetoothOnly: Boolean
        get() = prefs.getBoolean(KEY_BLUETOOTH_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_BLUETOOTH_ONLY, value).apply()

    var hasRecording: Boolean
        get() = prefs.getBoolean(KEY_HAS_RECORDING, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_RECORDING, value).apply()

    var frequencyPreset: String
        get() = prefs.getString(KEY_FREQUENCY_PRESET, "medium") ?: "medium"
        set(value) = prefs.edit().putString(KEY_FREQUENCY_PRESET, value).apply()

    var scheduleMode: String
        get() = prefs.getString(KEY_SCHEDULE_MODE, "random") ?: "random"
        set(value) = prefs.edit().putString(KEY_SCHEDULE_MODE, value).apply()

    fun getRecordingFile(context: Context): File {
        return File(context.filesDir, "recording.m4a")
    }

    fun deleteRecording(context: Context): Boolean {
        val file = getRecordingFile(context)
        val deleted = if (file.exists()) file.delete() else true
        hasRecording = false
        return deleted
    }

    fun presetMinMinutes(preset: String): Int = when (preset) {
        "high" -> 10
        "low" -> 60
        else -> 30
    }

    fun presetMaxMinutes(preset: String): Int = when (preset) {
        "high" -> 15
        "low" -> 120
        else -> 40
    }

    companion object {
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_REPEAT_COUNT = "repeat_count"
        private const val KEY_INTERVAL_SECONDS = "interval_seconds"
        private const val KEY_RANDOM_MIN = "random_min_minutes"
        private const val KEY_RANDOM_MAX = "random_max_minutes"
        private const val KEY_BLUETOOTH_ONLY = "bluetooth_only"
        private const val KEY_HAS_RECORDING = "has_recording"
        private const val KEY_FREQUENCY_PRESET = "frequency_preset"
        private const val KEY_SCHEDULE_MODE = "schedule_mode"
    }
}
