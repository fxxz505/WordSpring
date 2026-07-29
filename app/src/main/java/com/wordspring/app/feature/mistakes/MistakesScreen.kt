package com.wordspring.app.feature.mistakes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.data.repository.MistakeCard
import com.wordspring.app.ui.components.EmptyState
import com.wordspring.app.ui.components.LoadingState

private val MistakeCanvas = Color(0xFFF4F6F1)
private val MistakeInk = Color(0xFF123229)
private val MistakeMoss = Color(0xFF195543)

@Composable
fun MistakesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit = {},
    onOpenWord: (Long) -> Unit,
    onRelearn: (String) -> Unit,
    viewModel: MistakesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    Scaffold(
        containerColor = MistakeCanvas,
        topBar = { TopAppBar(title = { Text("错词本") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(MistakeCanvas).padding(padding).padding(bottom = contentPadding.calculateBottomPadding()).padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("把不熟的词，变成下一次的底气。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MistakeMoss), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("针对性复习", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("重新学习近 30 天答错过的单词。", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8ECE1))
                    Button(onClick = { viewModel.startRelearnSession(onRelearn) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF9ED), contentColor = MistakeMoss)) { Text("开始错词复习") }
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                state.isLoading -> LoadingState(message = "正在整理错词…")
                state.mistakes.isEmpty() -> EmptyState(title = "暂无错词", message = "保持这个节奏，答错过的词会在这里等待复习。")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("${state.mistakes.size} 个待巩固词", style = MaterialTheme.typography.labelMedium, color = MistakeMoss) }
                    items(state.mistakes, key = { it.cardId }) { MistakeRow(it) { onOpenWord(it.wordId) } }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MistakeRow(m: MistakeCard, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.spelling.ifBlank { "词条 #${m.wordId}" }, style = MaterialTheme.typography.titleMedium, color = MistakeInk)
                m.meaningZh?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text("上次答错：${com.wordspring.app.data.TimeUtil.localDateString(m.lastWrongAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("复习", style = MaterialTheme.typography.labelMedium, color = MistakeMoss)
        }
    }
}
