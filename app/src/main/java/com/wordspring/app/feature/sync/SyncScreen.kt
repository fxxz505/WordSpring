package com.wordspring.app.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.sync.SyncUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SyncScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshPending() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("云同步", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "不登录也能正常使用。登录后，背单词进度会在你的设备之间同步。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (state.signedIn) {
            SignedInContent(state, viewModel)
        } else {
            SignedOutContent(state, viewModel)
        }

        state.message?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            "同步使用 JWT 鉴权，只上传复习卡片状态与答题事件，不上传词书内容。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SignedOutContent(state: SyncUiState, viewModel: SyncViewModel) {
    OutlinedTextField(
        value = viewModel.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text("邮箱") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = viewModel.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("密码（至少 8 位）") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = viewModel::login,
            enabled = !state.syncing && viewModel.email.isNotBlank() && viewModel.password.length >= 8
        ) {
            Text("登录")
        }
        OutlinedButton(
            onClick = viewModel::register,
            enabled = !state.syncing && viewModel.email.isNotBlank() && viewModel.password.length >= 8
        ) {
            Text("注册")
        }
        if (state.syncing) {
            CircularProgressIndicator(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SignedInContent(state: SyncUiState, viewModel: SyncViewModel) {
    Text(
        state.email ?: "已登录",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "本机待上传：${state.pendingEvents} 条答题记录",
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(Modifier.height(2.dp))
    Text(
        "上次同步：${formatTimestamp(state.lastSyncAt)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = viewModel::syncNow, enabled = !state.syncing) {
            Text(if (state.syncing) "同步中…" else "立即同步")
        }
        OutlinedButton(onClick = viewModel::logout, enabled = !state.syncing) {
            Text("退出登录")
        }
    }
}

private fun formatTimestamp(value: Long?): String {
    if (value == null || value <= 0L) return "从未同步"
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(formatter)
}
