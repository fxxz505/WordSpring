package com.wordspring.app.feature.books.bookdetail

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.ui.components.ErrorState
import com.wordspring.app.ui.components.LoadingState
import java.time.LocalDate

private val DetailCanvas = Color(0xFFF4F6F1)
private val DetailInk = Color(0xFF123229)
private val DetailMoss = Color(0xFF195543)
private val DetailSand = Color(0xFFFFFCF7)
private val DetailGold = Color(0xFFFFD57D)

@Composable
fun BookDetailScreen(
    bookId: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenWord: (Long) -> Unit,
    onImportCsv: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    Scaffold(
        containerColor = DetailCanvas,
        topBar = {
            TopAppBar(
                title = { Text(state.book?.name ?: "词书详情") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(message = "正在加载词书…")
            state.errorMessage != null -> ErrorState(message = state.errorMessage!!, onBack = onBack)
            state.book == null -> ErrorState(message = "找不到这本词书", onBack = onBack)
            else -> BookDetailContent(state, contentPadding, padding, onOpenWord, onImportCsv, viewModel)
        }
    }
}

@Composable
private fun BookDetailContent(
    state: BookDetailUiState,
    contentPadding: PaddingValues,
    scaffoldPadding: PaddingValues,
    onOpenWord: (Long) -> Unit,
    onImportCsv: () -> Unit,
    viewModel: BookDetailViewModel
) {
    val book = state.book ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DetailCanvas).padding(contentPadding).padding(scaffoldPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { BookProgressCard(state) }
        item { StudyPlanCard(book.isEnabled, book.targetEpochDay, book.dailyNewLimit, state.progress?.newCount ?: 0, viewModel) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("词表 · ${state.entryCount} 个", style = MaterialTheme.typography.titleMedium, color = DetailInk)
                if (state.entryCount > state.words.size) {
                    Text("预览前 ${state.words.size} 个词条；可通过搜索页查找全部词汇。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.wordsLoading && state.words.isEmpty()) {
            item { LoadingState(message = "正在加载单词…") }
        } else if (state.words.isEmpty()) {
            item { Text("该词书暂时没有词条。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.words, key = { it.entry.id }) { row -> WordListRow(row) { onOpenWord(row.entry.wordId) } }
        }
        item {
            OutlinedButton(onClick = onImportCsv, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("导入 CSV 补充词条") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StudyPlanCard(
    isEnabled: Boolean,
    appliedTargetEpochDay: Int?,
    defaultDailyNewLimit: Int,
    remainingNewWords: Int,
    viewModel: BookDetailViewModel
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val todayEpochDay = today.toEpochDay().toInt()
    var pendingTargetEpochDay by remember(appliedTargetEpochDay) { mutableStateOf(appliedTargetEpochDay) }
    val preview = pendingTargetEpochDay?.let {
        completionPlanPreview(remainingNewWords, todayEpochDay, it)
    }
    val pendingDate = pendingTargetEpochDay?.let { LocalDate.ofEpochDay(it.toLong()) }

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = DetailSand), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("加入今日计划", style = MaterialTheme.typography.titleMedium, color = DetailInk)
                    Text("到期复习会按遗忘曲线自动进入学习队列。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isEnabled, onCheckedChange = viewModel::setEnabled)
            }
            Text("完成计划", style = MaterialTheme.typography.titleMedium, color = DetailInk)
            Text("选择完成日期后，先预览每日新词量，再应用到这本词书。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30L, 60L, 90L).forEach { days ->
                    val target = today.plusDays(days).toEpochDay().toInt()
                    val daily = (remainingNewWords + days - 1) / days
                    DurationOption(
                        modifier = Modifier.weight(1f),
                        days = days,
                        dailyNewWords = daily.toInt(),
                        selected = pendingTargetEpochDay == target,
                        onClick = { pendingTargetEpochDay = target }
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    val initial = pendingDate ?: today.plusDays(30)
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> pendingTargetEpochDay = LocalDate.of(year, month + 1, day).toEpochDay().toInt() },
                        initial.year,
                        initial.monthValue - 1,
                        initial.dayOfMonth
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("选择完成日期") }

            if (preview == null || pendingDate == null) {
                Text("尚未设置完成日期；当前默认每天 ${defaultDailyNewLimit} 个新词。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                PlanPreviewCard(pendingDate, preview, isApplied = pendingTargetEpochDay == appliedTargetEpochDay)
                if (pendingTargetEpochDay != appliedTargetEpochDay) {
                    Button(
                        onClick = { viewModel.setCompletionDate(pendingTargetEpochDay) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DetailMoss, contentColor = Color.White)
                    ) { Text("应用这个计划") }
                } else {
                    TextButton(onClick = { viewModel.setCompletionDate(null) }) { Text("清除完成日期") }
                }
            }
        }
    }
}

@Composable
private fun DurationOption(
    modifier: Modifier,
    days: Long,
    dailyNewWords: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 78.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE0EEE5) else Color(0xFFF6F4ED))
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$days 天", style = MaterialTheme.typography.titleSmall, color = DetailInk)
            Text("$dailyNewWords 词/天", style = MaterialTheme.typography.labelMedium, color = DetailMoss)
        }
    }
}

@Composable
private fun PlanPreviewCard(targetDate: LocalDate, preview: CompletionPlanPreview, isApplied: Boolean) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DetailMoss), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (isApplied) "当前计划" else "计划预览", style = MaterialTheme.typography.labelLarge, color = DetailGold)
                Text("完成日期：$targetDate · 剩余 ${preview.daysRemaining} 天", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8ECE1))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${preview.dailyNewWords}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("新词 / 天", style = MaterialTheme.typography.labelMedium, color = Color(0xFFD8ECE1))
            }
        }
    }
}

@Composable
private fun BookProgressCard(state: BookDetailUiState) {
    val progress = state.progress
    val completed = progress?.completedCount ?: 0
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = DetailMoss), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("学习进度", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("每一次复习都会累积为可见的掌握度。", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8ECE1))
                }
                Text("$completed / ${state.entryCount}", style = MaterialTheme.typography.titleLarge, color = DetailGold)
            }
            LinearProgressIndicator(progress = { progress?.completionFraction?.coerceIn(0f, 1f) ?: 0f }, modifier = Modifier.fillMaxWidth(), color = DetailGold, trackColor = Color(0x334D8C77))
            Text("已掌握 ${progress?.masteredCount ?: 0} · 学习中 ${progress?.learningCount ?: 0} · 待学 ${progress?.newCount ?: state.entryCount}", style = MaterialTheme.typography.labelMedium, color = Color(0xFFD8ECE1))
        }
    }
}

@Composable
private fun WordListRow(row: BookWordRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = DetailSand)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.word?.spelling ?: "未命名词条", style = MaterialTheme.typography.titleMedium, color = DetailInk)
                row.word?.phonetic?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                row.primaryMeaning?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Text("#${row.entry.position + 1}", style = MaterialTheme.typography.labelMedium, color = DetailMoss)
        }
    }
}
