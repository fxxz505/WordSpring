package com.wordspring.app.feature.word

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.domain.model.Definition
import com.wordspring.app.domain.model.Stage
import com.wordspring.app.feature.study.DefinitionDisplayFormatter
import com.wordspring.app.ui.components.ErrorState
import com.wordspring.app.ui.components.LoadingState

@Composable
fun WordDetailScreen(
    wordId: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: WordDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    Scaffold(
        containerColor = Color(0xFFF4F6F1),
        topBar = {
            TopAppBar(
                title = { Text(state.word?.spelling ?: "词条") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(message = "正在加载词条…")
            state.errorMessage != null -> ErrorState(message = state.errorMessage!!, onBack = onBack)
            state.word == null -> ErrorState(message = "找不到这个词条", onBack = onBack)
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF4F6F1))
                    .padding(contentPadding)
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = Color(0xFF195543)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(state.word!!.spelling, style = MaterialTheme.typography.displayMedium, color = Color.White)
                        state.word!!.phonetic?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Color(0xFFD8ECE1)) }
                    }
                }
                if (state.ttsAvailable) {
                    OutlinedButton(onClick = { viewModel.pronounce() }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("系统发音")
                    }
                } else {
                    Text("系统英语语音不可用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { viewModel.installTtsData() }) { Text("安装英语语音") }
                }
                distinctDefinitionsForDetail(state.definitions).forEach { DefinitionCard(it) }
                StageChip(state.card?.stage)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { viewModel.toggleFavorite() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.Star, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isFavorite) "已收藏" else "收藏")
                    }
                    OutlinedButton(
                        onClick = { viewModel.toggleDifficult() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isDifficult) "已标为困难" else "标记困难")
                    }
                }
                Button(
                    onClick = { viewModel.relearn() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("重新学习") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DefinitionCard(def: Definition) {
    val display = DefinitionDisplayFormatter.format(def)
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))) {
        Column(Modifier.padding(16.dp)) {
            display.lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.titleMedium)
            }
            def.example?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            display.translationLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun distinctDefinitionsForDetail(definitions: List<Definition>): List<Definition> =
    definitions.distinctBy { definition ->
        val display = DefinitionDisplayFormatter.format(definition)
        listOf(
            display.lines.joinToString(separator = "\n"),
            definition.example.orEmpty().trim(),
            display.translationLines.joinToString(separator = "\n")
        )
    }

@Composable
private fun StageChip(stage: Stage?) {
    val label = when (stage) {
        null, Stage.NEW -> "New"
        Stage.LEARNING -> "Learning"
        Stage.REVIEW -> "Reviewing"
        Stage.SUSPENDED -> "Paused"
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}



