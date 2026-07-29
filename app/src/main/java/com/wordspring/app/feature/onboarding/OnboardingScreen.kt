package com.wordspring.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.ui.components.ErrorState
import com.wordspring.app.ui.components.LoadingState

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler {
        viewModel.back()
    }
    when {
        state.isLoading -> LoadingState(message = "正在准备学习材料…")
        state.errorMessage != null -> ErrorState(
            message = state.errorMessage!!,
            onRetry = { viewModel.load() },
            onBack = onFinish
        )
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state.step) {
                OnboardingStep.GOAL -> GoalStep(
                    selected = state.selectedGoal,
                    onSelect = viewModel::selectGoal
                )
                OnboardingStep.BOOK -> BookStep(
                    books = state.books,
                    selectedBookId = state.selectedBookId,
                    onSelect = viewModel::selectBook
                )
                OnboardingStep.DAILY -> DailyStep(
                    preset = state.dailyNewLimit,
                    customInput = state.customDailyInput,
                    onPreset = viewModel::selectDailyPreset,
                    onCustomInput = viewModel::updateCustomDaily
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { viewModel.next(); if (state.step == OnboardingStep.DAILY) onFinish() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(if (state.step == OnboardingStep.DAILY) "开始学习" else "继续")
            }
            TextButton(onClick = { viewModel.skip(); onFinish() }, modifier = Modifier.fillMaxWidth()) {
                Text("跳过")
            }
        }
    }
}

@Composable
private fun GoalStep(selected: OnboardingGoal, onSelect: (OnboardingGoal) -> Unit) {
    Text("你想用 WordSpring 做什么？", style = MaterialTheme.typography.headlineSmall)
    Text(
        "一屏只做一个决定，随时可以调整。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    GoalOption("备考复习", "针对考试的高强度复习", selected == OnboardingGoal.EXAM) { onSelect(OnboardingGoal.EXAM) }
    GoalOption("日常积累", "每天少量新词，长期坚持（推荐）", selected == OnboardingGoal.DAILY) { onSelect(OnboardingGoal.DAILY) }
    GoalOption("自定义词集", "导入你自己的 CSV 词集", selected == OnboardingGoal.CUSTOM) { onSelect(OnboardingGoal.CUSTOM) }
}

@Composable
private fun GoalOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BookStep(
    books: List<com.wordspring.app.domain.model.WordBook>,
    selectedBookId: Long?,
    onSelect: (Long) -> Unit
) {
    Text("选择一本词书开始", style = MaterialTheme.typography.headlineSmall)
    Text("可随时在“词书”里启用更多", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    if (books.isEmpty()) {
        Text("正在准备内置词书…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    books.forEach { book ->
        Surface(
            onClick = { onSelect(book.id) },
            shape = MaterialTheme.shapes.medium,
            color = if (book.id == selectedBookId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (book.id == selectedBookId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(book.name, style = MaterialTheme.typography.titleMedium)
                Text("每日新词 ${book.dailyNewLimit} · 授权：${book.license}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DailyStep(
    preset: Int,
    customInput: String,
    onPreset: (Int) -> Unit,
    onCustomInput: (String) -> Unit
) {
    Text("每天学多少新词？", style = MaterialTheme.typography.headlineSmall)
    Text("节奏由你决定，可随时调整", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    listOf(10 to "10 个新词 / 天（推荐）", 20 to "20 个新词 / 天", 30 to "30 个新词 / 天").forEach { (n, label) ->
        Surface(
            onClick = { onPreset(n) },
            shape = MaterialTheme.shapes.medium,
            color = if (preset == n && customInput == n.toString()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (preset == n && customInput == n.toString()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            Text(label, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("或自定义（1–100）：", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = customInput,
        onValueChange = onCustomInput,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
