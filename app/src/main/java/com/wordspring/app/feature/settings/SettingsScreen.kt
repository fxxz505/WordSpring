package com.wordspring.app.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.app.DatePickerDialog
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.BuildConfig
import com.wordspring.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M13 设置页。包含学习、外观、发音、提醒、数据与关于。
 * 破坏性操作（恢复备份、清除数据）必须二次确认。
 * 备份/恢复使用 SAF，由用户选择文件 URI。
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingRestoreContent by remember { mutableStateOf<String?>(null) }
    var pendingRestoreName by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // SAF launcher: pick a file to write backup to.
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = viewModel.exportBackup()
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入文件")
                }.onSuccess {
                    statusMessage = "备份成功"
                }.onFailure { errorMessage = "备份失败：${it.message}" }
            }
        }
    }

    // SAF launcher: pick a backup file to read.
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { ins ->
                            ins.readBytes().toString(Charsets.UTF_8)
                        } ?: error("无法读取文件")
                    }
                    val validation = viewModel.validateBackup(content)
                    if (validation != null) {
                        errorMessage = validation
                    } else {
                        pendingRestoreContent = content
                        pendingRestoreName = uri.lastPathSegment ?: "备份文件"
                    }
                }.onFailure { errorMessage = "读取失败：${it.message}" }
            }
        }
    }

    // Notification permission launcher (Android 13+).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Honor user choice; turn off reminder if denied.
        viewModel.setReminderEnabled(granted)
        if (!granted) errorMessage = "已拒绝通知权限，提醒保持关闭"
    }

    Scaffold(
        containerColor = Color(0xFFF4F6F1),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F6F1))
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ---------- 学习 ----------
            SectionHeader("学习")
            ToggleRow(
                title = "高级评分（增加“熟练”）",
                subtitle = "默认仅显示 忘记 / 模糊 / 记得",
                checked = state.advancedGrading,
                onChange = { viewModel.setAdvancedGrading(it) }
            )
            ToggleRow(
                title = "答错后自动显示答案",
                subtitle = "关闭后仍可手动查看",
                checked = state.showAnswerOnWrong,
                onChange = { viewModel.setShowAnswerOnWrong(it) }
            )

            HorizontalDivider()

            // ---------- 外观 ----------
            SectionHeader("外观")
            SettingsRow(title = "主题", subtitle = "影响深浅色") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.themeMode == ThemeMode.System,
                        onClick = { viewModel.setThemeMode(ThemeMode.System) },
                        label = { Text("跟随系统") }
                    )
                    FilterChip(
                        selected = state.themeMode == ThemeMode.Light,
                        onClick = { viewModel.setThemeMode(ThemeMode.Light) },
                        label = { Text("浅色") }
                    )
                    FilterChip(
                        selected = state.themeMode == ThemeMode.Dark,
                        onClick = { viewModel.setThemeMode(ThemeMode.Dark) },
                        label = { Text("深色") }
                    )
                }
            }
            ToggleRow(
                title = "动态配色",
                subtitle = "Android 12+ 跟随系统壁纸",
                checked = state.dynamicColor,
                onChange = { viewModel.setDynamicColor(it) }
            )
            ToggleRow(
                title = "减少动效",
                subtitle = "降低动画与过渡强度",
                checked = state.reduceMotion,
                onChange = { viewModel.setReduceMotion(it) }
            )
            ToggleRow(
                title = "触觉反馈",
                subtitle = "答题正误轻量提示",
                checked = state.haptic,
                onChange = { viewModel.setHaptic(it) }
            )

            HorizontalDivider()

            // ---------- 发音 ----------
            SectionHeader("发音")
            SettingsRow(title = "TTS 语速", subtitle = "使用 Android 系统 TextToSpeech") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.ttsSpeed == 0.75f,
                        onClick = { viewModel.setTtsSpeed(0.75f) },
                        label = { Text("0.75x") }
                    )
                    FilterChip(
                        selected = state.ttsSpeed == 1.0f,
                        onClick = { viewModel.setTtsSpeed(1.0f) },
                        label = { Text("1.0x") }
                    )
                    FilterChip(
                        selected = state.ttsSpeed == 1.25f,
                        onClick = { viewModel.setTtsSpeed(1.25f) },
                        label = { Text("1.25x") }
                    )
                }
            }

            HorizontalDivider()

            // ---------- 提醒 ----------
            SectionHeader("提醒")
            ToggleRow(
                title = "学习提醒",
                subtitle = "默认关闭；仅有待复习时通知；每自然日最多一次",
                checked = state.reminderEnabled,
                onChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.setReminderEnabled(true)
                        } else {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        viewModel.setReminderEnabled(enabled)
                    }
                }
            )
            if (state.reminderEnabled) {
                SettingsRow(title = "提醒时间", subtitle = "%02d:%02d".format(state.reminderHour, state.reminderMinute)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8, 20).forEach { h ->
                            FilterChip(
                                selected = state.reminderHour == h,
                                onClick = { viewModel.setReminderTime(h, 0) },
                                label = { Text("%02d:00".format(h)) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // ---------- 数据 ----------
            SectionHeader("数据")
            Button(
                onClick = { backupLauncher.launch("wordspring-backup-${System.currentTimeMillis()}.json") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("备份学习数据") }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("从备份恢复") }
            OutlinedButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("清除全部学习数据") }

            HorizontalDivider()

            // ---------- 关于 ----------
            SectionHeader("关于")
            Text(
                "版本 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "WordSpring 是一款离线、无账号、无追踪的背单词应用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // Restore confirmation dialog.
    pendingRestoreContent?.let { content ->
        AlertDialog(
            onDismissRequest = {
                pendingRestoreContent = null
                pendingRestoreName = null
            },
            title = { Text("确认恢复备份") },
            text = {
                Column {
                    Text("恢复将向当前数据追加备份内容（词书与词条）。建议先备份当前数据。")
                    pendingRestoreName?.let { Text("文件：$it", style = MaterialTheme.typography.bodySmall) }
                    Text("此操作不可撤销。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = content
                    pendingRestoreContent = null
                    pendingRestoreName = null
                    scope.launch {
                        runCatching { viewModel.restoreBackup(c) }
                            .onSuccess { statusMessage = "恢复成功" }
                            .onFailure { errorMessage = "恢复失败：${it.message}" }
                    }
                }) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRestoreContent = null
                    pendingRestoreName = null
                }) { Text("取消") }
            }
        )
    }

    // Clear data confirmation dialog.
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除全部学习数据") },
            text = {
                Text("将清除所有学习记录、卡片进度、自定义词集和备份历史。内置词书会保留。此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        runCatching { viewModel.clearAllData() }
                            .onSuccess { statusMessage = "已清除全部学习数据" }
                            .onFailure { errorMessage = "清除失败：${it.message}" }
                    }
                }) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    // Status / error toast-like dialog.
    statusMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { statusMessage = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { statusMessage = null }) { Text("好的") }
            }
        )
    }
    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("出了点问题") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))
    ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { contentDescription = "$title，${if (checked) "已开启" else "已关闭"}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    }
}
