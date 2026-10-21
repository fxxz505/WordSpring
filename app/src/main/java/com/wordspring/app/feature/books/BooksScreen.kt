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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.data.seed.BuiltInBookCatalog
import com.wordspring.app.domain.model.BookSourceType
import com.wordspring.app.domain.model.WordBook
import com.wordspring.app.ui.components.LoadingState


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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(contentPadding),
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
        Text("词书", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text("选择适合当前目标的词书，启用后便会加入今日学习计划。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("学习书架", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("已启用 $enabledCount / $totalCount 本", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text("$enabledCount", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (book.isEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (book.isEnabled) "学习中" else "未启用",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (book.isEnabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                bookTag(book)?.let { tag ->
                    Spacer(Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            tag,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Switch(checked = book.isEnabled, onCheckedChange = onToggle)
            }
            Text(book.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text("每日新词 ${book.dailyNewLimit} 个" + (progress?.let { " · 共 ${it.totalCount} 词" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (progress != null) {
                val started = progress.completedCount
                Text("已开始 $started / ${progress.totalCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(
                    progress = { progress.completionFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "已掌握 ${progress.masteredCount} · 学习中 ${progress.learningCount} · 待学 ${progress.newCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("点击查看词表与学习进度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Exam label for built-in books (e.g. "四级"), "自定义" for user books. */
private fun bookTag(book: WordBook): String? =
    if (book.sourceType == BookSourceType.BUILT_IN) BuiltInBookCatalog.byName(book.name)?.shortLabel else "自定义"
