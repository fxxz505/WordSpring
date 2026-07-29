package com.wordspring.app.feature.search

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.domain.model.Word
import com.wordspring.app.ui.components.LoadingState

private val SearchCanvas = Color(0xFFF4F6F1)
private val SearchInk = Color(0xFF123229)
private val SearchMoss = Color(0xFF195543)

@Composable
fun SearchScreen(contentPadding: PaddingValues, onOpenWord: (Long) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().background(SearchCanvas).padding(contentPadding).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("查词", style = MaterialTheme.typography.headlineMedium, color = SearchInk)
        Spacer(Modifier.height(4.dp))
        Text("支持英文拼写、中文释义与词书中的关键词。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::updateQuery,
            placeholder = { Text("输入单词或释义") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        when {
            state.isLoading -> LoadingState(message = "正在查找单词…")
            !state.hasSearched -> SearchHint()
            state.results.isEmpty() -> SearchEmpty(state.query)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("找到 ${state.results.size} 个结果", style = MaterialTheme.typography.labelMedium, color = SearchMoss) }
                items(state.results, key = { it.id }) { word -> SearchResultRow(word, { onOpenWord(word.id) }) }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun SearchHint() {
    Surface(color = Color(0xFFE2EEE5), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("从一个词开始", style = MaterialTheme.typography.titleMedium, color = SearchInk)
            Text("例如输入 “achieve” 或 “实现”。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchEmpty(query: String) {
    Surface(color = Color(0xFFFFFCF7), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Text("没有找到 “$query”，试试更短的拼写或中文关键词。", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchResultRow(word: Word, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(word.spelling, style = MaterialTheme.typography.titleLarge, color = SearchInk, fontWeight = FontWeight.SemiBold)
                word.phonetic?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("查看", style = MaterialTheme.typography.labelMedium, color = SearchMoss)
        }
    }
}
