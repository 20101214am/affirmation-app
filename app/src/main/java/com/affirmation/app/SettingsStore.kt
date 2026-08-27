package com.affirmation.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File

data class TrackConfig(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val hasRecording: Boolean,
    val repeatCount: Int,
    val intervalSeconds: Int,
    val scheduleMode: String,
    val frequencyPreset: String,
    val randomMinMinutes: Int,
    val randomMaxMinutes: Int
)

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("habit_settings", Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var bluetoothOnly: Boolean
        get() = prefs.getBoolean(KEY_BLUETOOTH_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_BLUETOOTH_ONLY, value).apply()

    val tracks: List<TrackConfig>
        get() = TRACK_DEFS.map { def ->
            TrackConfig(
                id = def.first,
                name = def.second,
                enabled = prefs.getBoolean(key(def.first, "enabled"), true),
                hasRecording = prefs.getBoolean(key(def.first, "has_recording"), false),
                repeatCount = prefs.getInt(key(def.first, "repeat_count"), 3),
                intervalSeconds = prefs.getInt(key(def.first, "interval_seconds"), 5),
                scheduleMode = prefs.getString(key(def.first, "schedule_mode"), "random") ?: "random",
                frequencyPreset = prefs.getString(key(def.first, "frequency_preset"), "medium") ?: "medium",
                randomMinMinutes = prefs.getInt(key(def.first, "random_min"), 30),
                randomMaxMinutes = prefs.getInt(key(def.first, "random_max"), 120)
            )
        }

    fun getTrack(id: String): TrackConfig = tracks.first { it.id == id }

    val enabledTracks: List<TrackConfig>
        get() = tracks.filter { it.enabled && it.hasRecording }

    fun getNextPlayTime(id: String): Long =
        prefs.getLong(key(id, "next_play"), 0L)

    fun setNextPlayTime(id: String, time: Long) =
        prefs.edit().putLong(key(id, "next_play"), time).apply()

    fun saveTrack(t: TrackConfig) {
        prefs.edit().apply {
            putBoolean(key(t.id, "enabled"), t.enabled)
            putBoolean(key(t.id, "has_recording"), t.hasRecording)
            putInt(key(t.id, "repeat_count"), t.repeatCount)
            putInt(key(t.id, "interval_seconds"), t.intervalSeconds)
            putString(key(t.id, "schedule_mode"), t.scheduleMode)
            putString(key(t.id, "frequency_preset"), t.frequencyPreset)
            putInt(key(t.id, "random_min"), t.randomMinMinutes)
            putInt(key(t.id, "random_max"), t.randomMaxMinutes)
            apply()
        }
    }

    fun getRecordingFile(context: Context, id: String): File {
        return File(context.filesDir, "recording_$id.m4a")
    }

    fun deleteRecording(context: Context, id: String): Boolean {
        val file = getRecordingFile(context, id)
        val deleted = if (file.exists()) file.delete() else true
        prefs.edit().putBoolean(key(id, "has_recording"), false).apply()
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

    private fun key(trackId: String, field: String) = "track_${trackId}_$field"

    companion object {
        val TRACK_DEFS = listOf(
            "break_bad" to "打破坏习惯",
            "build_good" to "建立好习惯",
            "positive" to "积极信念"
        )
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_BLUETOOTH_ONLY = "bluetooth_only"
    }
}
