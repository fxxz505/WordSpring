package com.wordspring.app.feature.books

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
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
import com.wordspring.app.domain.model.WordBook
import com.wordspring.app.ui.components.LoadingState

private val BookCanvas = Color(0xFFF4F6F1)
private val BookInk = Color(0xFF123229)
private val BookMoss = Color(0xFF195543)

@Composable
fun BooksScreen(
    contentPadding: PaddingValues,
    onOpenBook: (Long) -> Unit,
    onImportCsv: (Long) -> Unit,
    onOpenWord: (Long) -> Unit,
    viewModel: BooksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.isLoading) {
        LoadingState(message = "正在整理你的词书…")
        return
    }
    val enabled = state.books.count { it.isEnabled }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BookCanvas).padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { BooksHeader(enabled, state.books.size) }
        items(state.books, key = { it.id }) { book ->
            BookRow(book, state.progressByBookId[book.id], { onOpenBook(book.id) }) { viewModel.toggleEnabled(book.id, it) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun BooksHeader(enabledCount: Int, totalCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("词书", style = MaterialTheme.typography.headlineMedium, color = BookInk)
        Text("选择适合当前目标的词书，启用后便会加入今日学习计划。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = BookMoss, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("学习书架", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("已启用 $enabledCount / $totalCount 本", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD8ECE1))
                }
                Text("$enabledCount", style = MaterialTheme.typography.displaySmall, color = Color(0xFFFFD57D))
            }
        }
    }
}

@Composable
private fun BookRow(
    book: WordBook,
    progress: com.wordspring.app.domain.model.BookLearningProgress?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (book.isEnabled) Color(0xFFDCEDE3) else Color(0xFFEDF0EC),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (book.isEnabled) "学习中" else "未启用",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (book.isEnabled) BookMoss else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Switch(checked = book.isEnabled, onCheckedChange = onToggle)
            }
            Text(book.name, style = MaterialTheme.typography.titleLarge, color = BookInk, fontWeight = FontWeight.SemiBold)
            Text("每日新词 ${book.dailyNewLimit} · ${book.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (progress != null) {
                val started = progress.completedCount
                Text("总词量 ${progress.totalCount} · 已开始 $started", style = MaterialTheme.typography.labelMedium, color = BookInk)
                LinearProgressIndicator(
                    progress = { progress.completionFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = BookMoss,
                    trackColor = Color(0xFFE2EAE2)
                )
                Text(
                    "已掌握 ${progress.masteredCount} · 学习中 ${progress.learningCount} · 待学 ${progress.newCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("点击查看词表与学习进度", style = MaterialTheme.typography.labelMedium, color = BookMoss)
        }
    }
}
