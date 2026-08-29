package com.affirmation.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.Calendar

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

    // 休眠时段，时间以「当日 0 点起的分钟数」存储
    // 起始大于结束即表示跨午夜，例如 1350(22:30) -> 420(07:00)
    var sleep1Enabled: Boolean
        get() = prefs.getBoolean(KEY_SLEEP1_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP1_ENABLED, value).apply()

    var sleep1Start: Int
        get() = prefs.getInt(KEY_SLEEP1_START, 22 * 60 + 30)
        set(value) = prefs.edit().putInt(KEY_SLEEP1_START, value).apply()

    var sleep1End: Int
        get() = prefs.getInt(KEY_SLEEP1_END, 7 * 60)
        set(value) = prefs.edit().putInt(KEY_SLEEP1_END, value).apply()

    var sleep2Enabled: Boolean
        get() = prefs.getBoolean(KEY_SLEEP2_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP2_ENABLED, value).apply()

    var sleep2Start: Int
        get() = prefs.getInt(KEY_SLEEP2_START, 13 * 60)
        set(value) = prefs.edit().putInt(KEY_SLEEP2_START, value).apply()

    var sleep2End: Int
        get() = prefs.getInt(KEY_SLEEP2_END, 14 * 60)
        set(value) = prefs.edit().putInt(KEY_SLEEP2_END, value).apply()

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

    // 单个时段是否包含第 m 分钟
    private fun inRange(m: Int, enabled: Boolean, start: Int, end: Int): Boolean {
        if (!enabled) return false
        return if (start <= end) {
            m >= start && m < end
        } else {
            m >= start || m < end
        }
    }

    // 此刻是否处于任一已启用的休眠时段
    fun isInSleep(now: Long = System.currentTimeMillis()): Boolean {
        val m = minuteOfDay(now)
        return inRange(m, sleep1Enabled, sleep1Start, sleep1End) ||
                inRange(m, sleep2Enabled, sleep2Start, sleep2End)
    }

    // 当前休眠时段的结束时刻。不在休眠内则原样返回入参，可直接当作闹钟时间
    fun sleepEndMillis(now: Long = System.currentTimeMillis()): Long {
        val m = minuteOfDay(now)
        val inSleep1 = inRange(m, sleep1Enabled, sleep1Start, sleep1End)
        val inSleep2 = inRange(m, sleep2Enabled, sleep2Start, sleep2End)
        if (!inSleep1 && !inSleep2) return now

        val end = if (inSleep1) sleep1End else sleep2End
        val start = if (inSleep1) sleep1Start else sleep2Start

        val target = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, end / 60)
            set(Calendar.MINUTE, end % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 跨午夜时段且当前已过起始点，结束时刻落在次日
        if (start > end && m >= start) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis
    }

    private fun minuteOfDay(now: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
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
        private const val KEY_SLEEP1_ENABLED = "sleep1_enabled"
        private const val KEY_SLEEP1_START = "sleep1_start"
        private const val KEY_SLEEP1_END = "sleep1_end"
        private const val KEY_SLEEP2_ENABLED = "sleep2_enabled"
        private const val KEY_SLEEP2_START = "sleep2_start"
        private const val KEY_SLEEP2_END = "sleep2_end"
    }
}
