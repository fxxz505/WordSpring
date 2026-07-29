package com.wordspring.app.feature.importflow

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.ui.components.LoadingState

@Composable
fun ImportCsvScreen(
    bookId: Long,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ImportCsvViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            viewModel.parseFile(uri, content)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("导入 CSV", style = MaterialTheme.typography.headlineSmall)
        Text(
            "字段：word,phonetic,partOfSpeech,definition,example,translation（UTF-8）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { pickFile.launch(arrayOf("text/*", "text/comma-separated-values", "*/*")) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("选择文件") }

        if (state.isLoading) LoadingState(message = "正在解析…")
        state.preview?.let { p ->
            Text("有效 ${p.validCount} 条", style = MaterialTheme.typography.bodyMedium)
            Text("重复 ${p.duplicateCount} 条（将合并）", style = MaterialTheme.typography.bodyMedium)
            if (p.errorCount > 0) {
                Text("错误 ${p.errorCount} 行：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                p.rows.filter { it.error != null }.take(10).forEach { r ->
                    Text("  第 ${r.lineNumber} 行：${r.error}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (p.validCount > 0) {
                Button(
                    onClick = { viewModel.confirmImport() },
                    enabled = !state.isCommitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text(if (state.isCommitting) "正在导入…" else "确认导入") }
            }
        }
        state.successMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("完成") }
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
