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

        when (intent?.action) {
            ACTION_PLAY -> handlePlayTrigger()
            else -> scheduleNextPlay()
        }

        return START_STICKY
    }

    private fun handlePlayTrigger() {
        if (!shouldPlay()) {
            // 条件不满足（如未连蓝牙），短间隔重试，避免用户连上蓝牙后还要等很久
            scheduleRetrySoon()
            return
        }
        playAffirmation()
    }

    private fun scheduleRetrySoon() {
        val triggerTime = System.currentTimeMillis() + RETRY_DELAY_MS
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_PLAY
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
            )
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
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

    private fun playAffirmation() {
        val recordingFile = settings.getRecordingFile(this)
        if (!recordingFile.exists()) {
            scheduleNextPlay()
            return
        }

        val repeatCount = settings.repeatCount
        val intervalMs = settings.intervalSeconds.toLong() * 1000

        var currentRepeat = 0

        fun playOnce() {
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
                            reschedule()
                        }
                    }
                    start()
                }
            } catch (e: Exception) {
                reschedule()
            }
        }

        playOnce()
    }

    private fun scheduleNextPlay() {
        val minMs = settings.randomMinMinutes.toLong() * 60 * 1000
        val maxMs = settings.randomMaxMinutes.toLong() * 60 * 1000
        val range = (maxMs - minMs).coerceAtLeast(1)
        val rand = Random()
        val delay = minMs + (rand.nextDouble() * range).toLong()

        val triggerTime = System.currentTimeMillis() + delay

        val intent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_PLAY
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

    private fun reschedule() {
        scheduleNextPlay()
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

        val modeText = if (settings.scheduleMode == "custom") "自定义间隔模式" else "预设频率模式"
        val statusText = if (settings.bluetoothOnly && !isBluetoothHeadsetConnected()) {
            "服务运行中，未连接蓝牙，等待连接后播放"
        } else {
            "服务运行中（$modeText），等待下次播放"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("习惯养成")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
