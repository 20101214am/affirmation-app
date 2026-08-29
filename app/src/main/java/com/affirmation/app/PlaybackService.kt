package com.affirmation.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.Random

class PlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "affirmation_playback"
        private const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.affirmation.app.ACTION_PLAY"
        const val ACTION_START = "com.affirmation.app.ACTION_START"
        private const val RETRY_DELAY_MS = 5 * 60 * 1000L
    }

    private lateinit var settings: SettingsStore
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        ensureScheduled()

        when (intent?.action) {
            ACTION_PLAY -> handlePlayTrigger()
            else -> scheduleNextPlay()
        }

        return START_STICKY
    }

    // 服务首次启动时，给尚未排期的轨道一个初始触发时间（1 分钟内随机）
    private fun ensureScheduled() {
        val now = System.currentTimeMillis()
        settings.enabledTracks.forEach { track ->
            if (settings.getNextPlayTime(track.id) <= now) {
                settings.setNextPlayTime(track.id, now + Random().nextInt(60_000))
            }
        }
    }

    private fun handlePlayTrigger() {
        val now = System.currentTimeMillis()
        if (settings.isInSleep(now)) {
            // 休眠时段内不播放，闹钟顺延到休眠结束
            setAlarm(settings.sleepEndMillis(now), makePendingIntent())
            refreshNotification()
            return
        }
        if (!shouldPlay()) {
            // 未连蓝牙且开启了仅蓝牙播放，短时重试
            scheduleRetrySoon()
            return
        }
        if (isInCall()) {
            // 正在语音/视频通话，暂停播放并短时重试
            scheduleRetrySoon()
            return
        }
        val track = pickDueTrack() ?: run {
            scheduleNextPlay()
            return
        }
        playAffirmation(track)
    }

    private fun pickDueTrack(): TrackConfig? {
        val candidates = settings.enabledTracks
        if (candidates.isEmpty()) return null
        return candidates.minByOrNull { settings.getNextPlayTime(it.id) }
    }

    private fun scheduleRetrySoon() {
        val now = System.currentTimeMillis()
        val triggerTime = if (settings.isInSleep(now)) {
            settings.sleepEndMillis(now)
        } else {
            now + RETRY_DELAY_MS
        }
        setAlarm(triggerTime, makePendingIntent())
    }

    private fun shouldPlay(): Boolean {
        if (!settings.bluetoothOnly) return true
        return isBluetoothHeadsetConnected()
    }

    private fun isBluetoothHeadsetConnected(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) return false
        return adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
    }

    // 检测是否处于通话中（蜂窝通话或微信/WhatsApp 等 VoIP 语音视频通话）
    // 通话时系统音频模式为 IN_CALL / IN_COMMUNICATION，无需额外权限
    private fun isInCall(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = am.mode
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return am.activePlaybackConfigurations.any {
                it.audioAttributes.usage == AudioAttributes.USAGE_VOICE_COMMUNICATION
            }
        }
        return false
    }

    private fun playAffirmation(track: TrackConfig) {
        val recordingFile = settings.getRecordingFile(this, track.id)
        if (!recordingFile.exists()) {
            settings.setNextPlayTime(track.id, System.currentTimeMillis())
            scheduleNextPlay()
            return
        }

        val repeatCount = track.repeatCount
        val intervalMs = track.intervalSeconds.toLong() * 1000

        var currentRepeat = 0

        fun playOnce() {
            if (settings.isInSleep()) {
                // 播放途中进入休眠时段，停止本轮重复播放
                mediaPlayer?.release()
                mediaPlayer = null
                rescheduleTrack(track)
                return
            }
            if (isInCall()) {
                // 通话开始，停止本轮重复播放，稍后重试
                mediaPlayer?.release()
                mediaPlayer = null
                rescheduleTrack(track)
                return
            }
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(recordingFile.path)
                    prepare()
                    setOnCompletionListener {
                        release()
                        mediaPlayer = null
                        currentRepeat++
                        if (currentRepeat < repeatCount) {
                            handler.postDelayed({ playOnce() }, intervalMs)
                        } else {
                            rescheduleTrack(track)
                        }
                    }
                    start()
                }
            } catch (e: Exception) {
                rescheduleTrack(track)
            }
        }

        playOnce()
    }

    private fun rescheduleTrack(track: TrackConfig) {
        val next = computeNextTime(track)
        settings.setNextPlayTime(track.id, next)
        scheduleNextPlay()
    }

    // 按本轨道自己的频率档位/自定义区间计算下次触发时间
    // 休眠约束不在此处理，统一由 scheduleNextPlay 设闹钟前拦截
    private fun computeNextTime(track: TrackConfig): Long {
        val minMs = track.randomMinMinutes.toLong() * 60 * 1000
        val maxMs = track.randomMaxMinutes.toLong() * 60 * 1000
        val range = (maxMs - minMs).coerceAtLeast(1)
        val delay = minMs + (Random().nextDouble() * range).toLong()
        return System.currentTimeMillis() + delay
    }

    private fun scheduleNextPlay() {
        val now = System.currentTimeMillis()
        val candidates = settings.enabledTracks
        val raw = if (candidates.isEmpty()) {
            now + 60 * 60 * 1000L
        } else {
            candidates.minOf { settings.getNextPlayTime(it.id) }.coerceAtLeast(now)
        }
        // 落在休眠时段则顺延到休眠结束，保证闹钟不会在此期间响
        val triggerTime = if (settings.isInSleep(raw)) {
            settings.sleepEndMillis(raw)
        } else {
            raw
        }
        setAlarm(triggerTime, makePendingIntent())
    }

    private fun makePendingIntent(): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_PLAY
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(triggerTime: Long, pendingIntent: PendingIntent) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "习惯养成",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台播放服务"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activeCount = settings.enabledTracks.size
        val statusText = when {
            settings.isInSleep() -> "服务运行中，休眠时段，暂停播放"
            isInCall() -> "服务运行中，正在通话，已暂停播放"
            settings.bluetoothOnly && !isBluetoothHeadsetConnected() ->
                "服务运行中，未连接蓝牙，等待连接后播放"
            else -> "服务运行中，已启用 $activeCount 条内容"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("习惯养成")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    // 通知默认只在服务启动时构建一次，状态变化时需主动刷新
    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
