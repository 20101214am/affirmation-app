package com.affirmation.app

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private lateinit var settings: SettingsStore

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result handled implicitly */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        requestPermissions()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun AppContent() {
        var serviceEnabled by remember { mutableStateOf(settings.serviceEnabled) }
        var repeatCount by remember { mutableStateOf(settings.repeatCount) }
        var intervalSeconds by remember { mutableStateOf(settings.intervalSeconds) }
        var randomMinMinutes by remember { mutableStateOf(settings.randomMinMinutes) }
        var randomMaxMinutes by remember { mutableStateOf(settings.randomMaxMinutes) }
        var bluetoothOnly by remember { mutableStateOf(settings.bluetoothOnly) }
        var isRecording by remember { mutableStateOf(false) }
        var hasRecording by remember { mutableStateOf(settings.hasRecording) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("信念播放器") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Recording Section ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("录音", style = MaterialTheme.typography.titleMedium)

                        if (hasRecording) {
                            Text("已录音", color = MaterialTheme.colorScheme.primary)
                            Button(onClick = { playTest() }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("试听")
                            }
                        }

                        Button(
                            onClick = {
                                if (isRecording) {
                                    stopRecording()
                                    isRecording = false
                                    hasRecording = settings.hasRecording
                                } else {
                                    startRecording()
                                    isRecording = true
                                }
                            },
                            colors = if (isRecording) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ) else ButtonDefaults.buttonColors()
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isRecording) "停止录音" else "开始录音")
                        }
                    }
                }

                // --- Playback Settings ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("播放设置", style = MaterialTheme.typography.titleMedium)

                        Text("重复次数: $repeatCount")
                        Slider(
                            value = repeatCount.toFloat(),
                            onValueChange = {
                                repeatCount = it.toInt()
                                settings.repeatCount = it.toInt()
                            },
                            valueRange = 1f..20f,
                            steps = 18
                        )

                        Text("每次间隔: ${intervalSeconds}秒")
                        Slider(
                            value = intervalSeconds.toFloat(),
                            onValueChange = {
                                intervalSeconds = it.toInt()
                                settings.intervalSeconds = it.toInt()
                            },
                            valueRange = 1f..60f,
                            steps = 58
                        )

                        Text("最短触发间隔: ${randomMinMinutes}分钟")
                        Slider(
                            value = randomMinMinutes.toFloat(),
                            onValueChange = {
                                randomMinMinutes = it.toInt()
                                settings.randomMinMinutes = it.toInt()
                            },
                            valueRange = 5f..240f,
                            steps = 46
                        )

                        Text("最长触发间隔: ${randomMaxMinutes}分钟")
                        Slider(
                            value = randomMaxMinutes.toFloat(),
                            onValueChange = {
                                randomMaxMinutes = it.toInt()
                                settings.randomMaxMinutes = it.toInt()
                            },
                            valueRange = 10f..480f,
                            steps = 93
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("仅蓝牙耳机播放", modifier = Modifier.weight(1f))
                            Switch(
                                checked = bluetoothOnly,
                                onCheckedChange = {
                                    bluetoothOnly = it
                                    settings.bluetoothOnly = it
                                }
                            )
                        }
                    }
                }

                // --- Master Switch ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用播放", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (serviceEnabled) "服务运行中" else "已停止",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (serviceEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = serviceEnabled,
                            onCheckedChange = { enabled ->
                                serviceEnabled = enabled
                                settings.serviceEnabled = enabled
                                if (enabled) {
                                    startPlaybackService()
                                } else {
                                    stopPlaybackService()
                                }
                            }
                        )
                    }
                }

                // --- Tips ---
                Text(
                    "提示: 请确保已授予录音、蓝牙、通知权限。\n小米手机需在设置中开启自启动权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun startRecording() {
        try {
            val file = settings.getRecordingFile(this)
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "录音失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            settings.hasRecording = true
        } catch (e: Exception) {
            recorder = null
            Toast.makeText(this, "停止录音失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playTest() {
        try {
            val file = settings.getRecordingFile(this)
            if (file.exists()) {
                player?.release()
                player = MediaPlayer().apply {
                    setDataSource(file.path)
                    prepare()
                    setOnCompletionListener { release() }
                    start()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startPlaybackService() {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopPlaybackService() {
        stopService(Intent(this, PlaybackService::class.java))
    }

    override fun onDestroy() {
        recorder?.release()
        player?.release()
        super.onDestroy()
    }
}
