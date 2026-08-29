package com.affirmation.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppContent() {
        var serviceEnabled by remember { mutableStateOf(settings.serviceEnabled) }
        var bluetoothOnly by remember { mutableStateOf(settings.bluetoothOnly) }
        var tracks by remember { mutableStateOf(settings.tracks) }
        var selectedId by remember { mutableStateOf<String?>(null) }
        var sleep1Enabled by remember { mutableStateOf(settings.sleep1Enabled) }
        var sleep1Start by remember { mutableStateOf(settings.sleep1Start) }
        var sleep1End by remember { mutableStateOf(settings.sleep1End) }
        var sleep2Enabled by remember { mutableStateOf(settings.sleep2Enabled) }
        var sleep2Start by remember { mutableStateOf(settings.sleep2Start) }
        var sleep2End by remember { mutableStateOf(settings.sleep2End) }

        if (selectedId == null) {
            ListScreen(
                serviceEnabled = serviceEnabled,
                bluetoothOnly = bluetoothOnly,
                tracks = tracks,
                onToggleService = { enabled ->
                    serviceEnabled = enabled
                    settings.serviceEnabled = enabled
                    if (enabled) startPlaybackService() else stopPlaybackService()
                },
                onToggleBluetooth = { value ->
                    bluetoothOnly = value
                    settings.bluetoothOnly = value
                    restartServiceIfRunning()
                },
                sleep1Enabled = sleep1Enabled,
                sleep1Start = sleep1Start,
                sleep1End = sleep1End,
                sleep2Enabled = sleep2Enabled,
                sleep2Start = sleep2Start,
                sleep2End = sleep2End,
                onToggleSleep = { slot, enabled ->
                    when (slot) {
                        1 -> {
                            sleep1Enabled = enabled
                            settings.sleep1Enabled = enabled
                        }
                        else -> {
                            sleep2Enabled = enabled
                            settings.sleep2Enabled = enabled
                        }
                    }
                    restartServiceIfRunning()
                },
                onSetSleepTime = { slot, start, end ->
                    when (slot) {
                        1 -> {
                            sleep1Start = start
                            sleep1End = end
                            settings.sleep1Start = start
                            settings.sleep1End = end
                        }
                        else -> {
                            sleep2Start = start
                            sleep2End = end
                            settings.sleep2Start = start
                            settings.sleep2End = end
                        }
                    }
                    restartServiceIfRunning()
                },
                onOpenTrack = { selectedId = it }
            )
        } else {
            val track = tracks.first { it.id == selectedId }
            TrackDetail(
                track = track,
                onBack = {
                    selectedId = null
                    tracks = settings.tracks
                },
                onSaved = {
                    tracks = settings.tracks
                    if (settings.serviceEnabled) {
                        stopPlaybackService()
                        startPlaybackService()
                    }
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ListScreen(
        serviceEnabled: Boolean,
        bluetoothOnly: Boolean,
        tracks: List<TrackConfig>,
        onToggleService: (Boolean) -> Unit,
        onToggleBluetooth: (Boolean) -> Unit,
        sleep1Enabled: Boolean,
        sleep1Start: Int,
        sleep1End: Int,
        sleep2Enabled: Boolean,
        sleep2Start: Int,
        sleep2End: Int,
        onToggleSleep: (Int, Boolean) -> Unit,
        onSetSleepTime: (Int, Int, Int) -> Unit,
        onOpenTrack: (String) -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("习惯养成") },
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
                        Switch(checked = serviceEnabled, onCheckedChange = onToggleService)
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("仅蓝牙耳机播放")
                            Text(
                                if (isBluetoothConnected()) "已连接蓝牙" else "未连接蓝牙",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBluetoothConnected()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(checked = bluetoothOnly, onCheckedChange = onToggleBluetooth)
                    }
                }

                SleepPeriodCard(
                    s1Enabled = sleep1Enabled,
                    s1Start = sleep1Start,
                    s1End = sleep1End,
                    s2Enabled = sleep2Enabled,
                    s2Start = sleep2Start,
                    s2End = sleep2End,
                    onToggleSleep = onToggleSleep,
                    onSetSleepTime = onSetSleepTime
                )

                tracks.forEach { track ->
                    TrackCard(track = track, onClick = { onOpenTrack(track.id) })
                }

                Text(
                    "提示: 请确保已授予录音、蓝牙、通知权限。\n" +
                            "小米手机需在设置中开启自启动权限，否则后台被杀。\n" +
                            "开启\"仅蓝牙耳机播放\"后，未连接蓝牙不会播放，点\"试听\"可立即验证录音。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SleepPeriodCard(
        s1Enabled: Boolean,
        s1Start: Int,
        s1End: Int,
        s2Enabled: Boolean,
        s2Start: Int,
        s2End: Int,
        onToggleSleep: (Int, Boolean) -> Unit,
        onSetSleepTime: (Int, Int, Int) -> Unit
    ) {
        // Triple(时段序号, 0=起始/1=结束, 当前分钟数)
        var picker by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("休眠时段", style = MaterialTheme.typography.titleMedium)

                SleepRow(
                    label = "休眠时段一",
                    enabled = s1Enabled,
                    start = s1Start,
                    end = s1End,
                    onToggle = { onToggleSleep(1, it) },
                    onPickStart = { picker = Triple(1, 0, s1Start) },
                    onPickEnd = { picker = Triple(1, 1, s1End) }
                )

                SleepRow(
                    label = "休眠时段二",
                    enabled = s2Enabled,
                    start = s2Start,
                    end = s2End,
                    onToggle = { onToggleSleep(2, it) },
                    onPickStart = { picker = Triple(2, 0, s2Start) },
                    onPickEnd = { picker = Triple(2, 1, s2End) }
                )

                Text(
                    "时段内不触发也不播放，闹钟自动顺延到时段结束。" +
                            "跨午夜时让起始晚于结束即可，例如 22:30 到 07:00。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        picker?.let { (slot, which, minutes) ->
            TimePickerDialog(
                title = if (which == 0) "设置开始时间" else "设置结束时间",
                initialMinutes = minutes,
                onConfirm = { newMin ->
                    if (slot == 1) {
                        if (which == 0) onSetSleepTime(1, newMin, s1End)
                        else onSetSleepTime(1, s1Start, newMin)
                    } else {
                        if (which == 0) onSetSleepTime(2, newMin, s2End)
                        else onSetSleepTime(2, s2Start, newMin)
                    }
                    picker = null
                },
                onDismiss = { picker = null }
            )
        }
    }

    @Composable
    private fun SleepRow(
        label: String,
        enabled: Boolean,
        start: Int,
        end: Int,
        onToggle: (Boolean) -> Unit,
        onPickStart: () -> Unit,
        onPickEnd: () -> Unit
    ) {
        val textColor = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.outline

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(checked = enabled, onCheckedChange = onToggle)
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f), color = textColor)
            TextButton(onClick = onPickStart, enabled = enabled) {
                Text(formatTime(start))
            }
            Text("→", color = textColor)
            TextButton(onClick = onPickEnd, enabled = enabled) {
                Text(formatTime(end))
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TimePickerDialog(
        title: String,
        initialMinutes: Int,
        onConfirm: (Int) -> Unit,
        onDismiss: () -> Unit
    ) {
        val state = rememberTimePickerState(
            initialHour = initialMinutes / 60,
            initialMinute = initialMinutes % 60,
            is24Hour = true
        )
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(16.dp))
                    TimePicker(state = state)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TrackCard(track: TrackConfig, onClick: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append(if (track.enabled) "已开启" else "已关闭")
                            append(" · ")
                            append(if (track.hasRecording) "已录音" else "未录音")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (track.enabled && track.hasRecording) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TrackDetail(
        track: TrackConfig,
        onBack: () -> Unit,
        onSaved: () -> Unit
    ) {
        val focusManager = LocalFocusManager.current
        var enabled by remember { mutableStateOf(track.enabled) }
        var hasRecording by remember { mutableStateOf(track.hasRecording) }
        var isRecording by remember { mutableStateOf(false) }
        var repeatCount by remember { mutableStateOf(track.repeatCount) }
        var intervalSeconds by remember { mutableStateOf(track.intervalSeconds) }
        var scheduleMode by remember { mutableStateOf(track.scheduleMode) }
        var frequencyPreset by remember { mutableStateOf(track.frequencyPreset) }
        var customMinMinutes by remember { mutableStateOf(track.randomMinMinutes) }
        var customMaxMinutes by remember { mutableStateOf(track.randomMaxMinutes) }
        var customMinText by remember { mutableStateOf(track.randomMinMinutes.toString()) }
        var customMaxText by remember { mutableStateOf(track.randomMaxMinutes.toString()) }
        var dirty by remember { mutableStateOf(false) }

        val pickAudioLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
            onResult = { uri ->
                uri?.let {
                    try {
                        val file = settings.getRecordingFile(this@MainActivity, track.id)
                        contentResolver.openInputStream(it)?.use { input ->
                            file.outputStream().use { out -> input.copyTo(out) }
                        }
                        val t = settings.getTrack(track.id)
                        settings.saveTrack(t.copy(hasRecording = true))
                        hasRecording = true
                        dirty = true
                        Toast.makeText(this@MainActivity, "已导入音频", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "导入失败: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(track.name) },
                    navigationIcon = {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            onBack()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("开启此条", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (enabled) "将参与循环播放" else "不参与播放",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = {
                            enabled = it
                            dirty = true
                        })
                    }
                }

                // --- Recording Section ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("录音或导入", style = MaterialTheme.typography.titleMedium)

                        if (hasRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("已录音", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Button(onClick = { playTest(track.id) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("试听")
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        deleteRecording(track.id)
                                        hasRecording = false
                                        dirty = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("删除")
                                }
                            }
                        } else {
                            Text("尚未录音", color = MaterialTheme.colorScheme.outline)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        stopRecording(track.id)
                                        isRecording = false
                                        hasRecording = settings.getTrack(track.id).hasRecording
                                    } else {
                                        startRecording(track.id)
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
                            OutlinedButton(onClick = { pickAudioLauncher.launch("audio/*") }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("导入本地音频")
                            }
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
                                dirty = true
                            },
                            valueRange = 1f..20f,
                            steps = 18
                        )

                        Text("每次间隔: ${intervalSeconds}秒")
                        Slider(
                            value = intervalSeconds.toFloat(),
                            onValueChange = {
                                intervalSeconds = it.toInt()
                                dirty = true
                            },
                            valueRange = 1f..60f,
                            steps = 58
                        )

                        HorizontalDivider()
                        Text("播放模式", style = MaterialTheme.typography.titleMedium)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = scheduleMode == "random",
                                onClick = { scheduleMode = "random"; dirty = true }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("预设频率")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = scheduleMode == "custom",
                                onClick = { scheduleMode = "custom"; dirty = true }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("自定义间隔")
                        }

                        if (scheduleMode == "random") {
                            HorizontalDivider()
                            Text("播放频率", style = MaterialTheme.typography.titleMedium)
                            FrequencyOption(
                                selected = frequencyPreset == "high",
                                onSelect = { frequencyPreset = "high"; dirty = true },
                                title = "高频",
                                detail = "每 10-15 分钟"
                            )
                            FrequencyOption(
                                selected = frequencyPreset == "medium",
                                onSelect = { frequencyPreset = "medium"; dirty = true },
                                title = "中频",
                                detail = "每 30-40 分钟"
                            )
                            FrequencyOption(
                                selected = frequencyPreset == "low",
                                onSelect = { frequencyPreset = "low"; dirty = true },
                                title = "低频",
                                detail = "每 1-2 小时"
                            )
                        } else {
                            HorizontalDivider()
                            Text("自定义间隔（分钟）", style = MaterialTheme.typography.titleMedium)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("最小间隔", modifier = Modifier.width(72.dp))
                                OutlinedTextField(
                                    value = customMinText,
                                    onValueChange = { txt ->
                                        customMinText = txt.filter { it.isDigit() }
                                        val v = customMinText.toIntOrNull() ?: 1
                                        customMinMinutes = v.coerceIn(1, 480)
                                        dirty = true
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.width(72.dp)
                                )
                                Text("分钟", modifier = Modifier.padding(start = 4.dp))
                            }
                            Slider(
                                value = customMinMinutes.toFloat(),
                                onValueChange = {
                                    customMinMinutes = it.toInt().coerceAtMost(customMaxMinutes)
                                    customMinText = customMinMinutes.toString()
                                    dirty = true
                                },
                                valueRange = 1f..480f,
                                steps = 479
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("最大间隔", modifier = Modifier.width(72.dp))
                                OutlinedTextField(
                                    value = customMaxText,
                                    onValueChange = { txt ->
                                        customMaxText = txt.filter { it.isDigit() }
                                        val v = customMaxText.toIntOrNull() ?: 1
                                        customMaxMinutes = v.coerceIn(1, 480)
                                        dirty = true
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.width(72.dp)
                                )
                                Text("分钟", modifier = Modifier.padding(start = 4.dp))
                            }
                            Slider(
                                value = customMaxMinutes.toFloat(),
                                onValueChange = {
                                    customMaxMinutes = it.toInt().coerceAtLeast(customMinMinutes)
                                    customMaxText = customMaxMinutes.toString()
                                    dirty = true
                                },
                                valueRange = 1f..480f,
                                steps = 479
                            )

                            Text(
                                "随机在 ${customMinMinutes}-${customMaxMinutes} 分钟之间触发一次播放",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // --- Save Button ---
                Button(
                    onClick = {
                        val min: Int
                        val max: Int
                        if (scheduleMode == "custom") {
                            min = customMinMinutes.coerceAtMost(customMaxMinutes)
                            max = customMaxMinutes.coerceAtLeast(customMinMinutes)
                        } else {
                            min = settings.presetMinMinutes(frequencyPreset)
                            max = settings.presetMaxMinutes(frequencyPreset)
                        }
                        settings.saveTrack(
                            track.copy(
                                enabled = enabled,
                                hasRecording = hasRecording,
                                repeatCount = repeatCount,
                                intervalSeconds = intervalSeconds,
                                scheduleMode = scheduleMode,
                                frequencyPreset = frequencyPreset,
                                randomMinMinutes = min,
                                randomMaxMinutes = max
                            )
                        )
                        dirty = false
                        focusManager.clearFocus()
                        Toast.makeText(this@MainActivity, "已保存", Toast.LENGTH_SHORT).show()
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("保存设置")
                }
                if (dirty) {
                    Text(
                        "设置已修改，点上方\"保存设置\"后生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    @Composable
    private fun FrequencyOption(
        selected: Boolean,
        onSelect: () -> Unit,
        title: String,
        detail: String
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title)
                Text(
                    detail,
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

    private fun isBluetoothConnected(): Boolean {
        val bm = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter ?: return false
        if (!adapter.isEnabled) return false
        return adapter.getProfileConnectionState(BluetoothProfile.HEADSET) ==
                BluetoothAdapter.STATE_CONNECTED
    }

    // 全局设置改动后重启服务，让新的闹钟时间立刻生效
    private fun restartServiceIfRunning() {
        if (settings.serviceEnabled) {
            stopPlaybackService()
            startPlaybackService()
        }
    }

    private fun formatTime(minutes: Int): String {
        return String.format("%02d:%02d", minutes / 60, minutes % 60)
    }

    private fun startRecording(trackId: String) {
        try {
            val file = settings.getRecordingFile(this, trackId)
            file.delete()
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

    private fun stopRecording(trackId: String) {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val t = settings.getTrack(trackId)
            settings.saveTrack(t.copy(hasRecording = true))
        } catch (e: Exception) {
            recorder = null
            Toast.makeText(this, "停止录音失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteRecording(trackId: String) {
        try {
            val ok = settings.deleteRecording(this, trackId)
            Toast.makeText(
                this,
                if (ok) "录音已删除" else "删除失败",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playTest(trackId: String) {
        try {
            val file = settings.getRecordingFile(this, trackId)
            if (!file.exists()) {
                Toast.makeText(this, "请先录音", Toast.LENGTH_SHORT).show()
                return
            }
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                setOnCompletionListener { release() }
                start()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
