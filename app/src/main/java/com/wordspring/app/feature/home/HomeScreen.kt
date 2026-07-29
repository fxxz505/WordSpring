package com.wordspring.app.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.ui.components.ErrorState
import com.wordspring.app.ui.components.LoadingState

private val HomeCanvas = Color(0xFFF4F6F1)
private val HomeInk = Color(0xFF112D25)
private val HomeMoss = Color(0xFF164D3E)
private val HomeMossDeep = Color(0xFF0D372C)
private val HomeSand = Color(0xFFFFF9ED)
private val HomeGold = Color(0xFFF3C96A)
private val HomeLine = Color(0x1AFFFFFF)

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onStartStudy: (String) -> Unit,
    onOpenBooks: () -> Unit,
    onOpenMistakes: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWord: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HomeCanvas, Color(0xFFEAF0E8))))
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        when (val snapshot = state) {
            HomeUiState.Loading -> LoadingState(message = "正在整理今天的学习计划…")
            HomeUiState.Empty -> EmptyHome(onOpenBooks = onOpenBooks, onOpenSettings = onOpenSettings)
            is HomeUiState.Failed -> ErrorState(message = snapshot.message, onRetry = viewModel::refresh)
            is HomeUiState.Ready -> ReadyHome(
                state = snapshot,
                onStartStudy = { viewModel.startStudy(onStartStudy) { viewModel.refresh() } },
                onOpenBooks = onOpenBooks,
                onOpenMistakes = onOpenMistakes,
                onOpenSearch = onOpenSearch,
                onOpenStats = onOpenStats,
                onOpenSettings = onOpenSettings
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ReadyHome(
    state: HomeUiState.Ready,
    onStartStudy: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenMistakes: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    HomeHeader(streakDays = state.streakDays)
    LearningHero(state = state, onStartStudy = onStartStudy)

    Text("学习总进度", style = MaterialTheme.typography.titleMedium, color = HomeInk)
    TotalProgressCard(state)

    Text("今日概览", style = MaterialTheme.typography.titleMedium, color = HomeInk)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OverviewCard(
            modifier = Modifier.weight(1f),
            label = "待复习",
            value = state.dueCount.toString(),
            note = if (state.dueCount == 0) "没有到期任务" else "优先完成",
            accent = Color(0xFFC88735)
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            label = "预计专注",
            value = "${state.etaMinutes} 分钟",
            note = if (state.allDone) "今日已收尾" else "剩余 ${state.pendingCount} 张",
            accent = HomeMoss
        )
    }

    Text("学习工具", style = MaterialTheme.typography.titleMedium, color = HomeInk)
    HomeShortcutGrid(
        onOpenBooks = onOpenBooks,
        onOpenMistakes = onOpenMistakes,
        onOpenSearch = onOpenSearch,
        onOpenStats = onOpenStats,
        onOpenSettings = onOpenSettings
    )
}

@Composable
private fun HomeHeader(streakDays: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "WORDSPRING · 今日计划",
            style = MaterialTheme.typography.labelMedium,
            color = HomeMoss,
            fontWeight = FontWeight.Bold
        )
        Text("让每一次复习，都留下痕迹", style = MaterialTheme.typography.headlineMedium, color = HomeInk)
        Text(
            text = if (streakDays > 0) "已连续学习 $streakDays 天，节奏正好。" else "从这一组单词开始，建立自己的节奏。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LearningHero(state: HomeUiState.Ready, onStartStudy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = HomeMoss)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("今日计划", style = MaterialTheme.typography.titleMedium, color = Color(0xFFD9ECE1))
                }
                Surface(color = Color(0xFF2D6C58), shape = RoundedCornerShape(18.dp)) {
                    Text(
                        text = if (state.allDone) "今日达成" else "进行中",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = HomeGold
                    )
                }
            }
            HorizontalDivider(color = HomeLine)
            PlanProgressRow("新学（今日应学）", state.newCount, state.newGoal, HomeGold)
            PlanProgressRow("复习（今日应复习）", state.reviewedCount, state.reviewGoal, Color(0xFFA9D9C2))
            Text(
                text = if (state.allDone) "今日新学与复习目标均已完成，明天继续巩固。" else "还有 ${state.pendingCount} 张待处理卡片，预计 ${state.etaMinutes} 分钟。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE5F0E8)
            )
            Button(
                onClick = onStartStudy,
                enabled = state.startEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomeSand,
                    contentColor = HomeMoss,
                    disabledContainerColor = HomeMossDeep,
                    disabledContentColor = Color(0xFFB4CDC0)
                )
            ) {
                Text(
                    text = if (state.allDone) "今日计划已完成" else "开始本组学习",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun PlanProgressRow(label: String, current: Int, goal: Int, color: Color) {
    val safeGoal = goal.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text("$current / $goal", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE5F0E8))
        }
        LinearProgressIndicator(
            progress = { (current.toFloat() / safeGoal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = Color(0x334D8C77)
        )
    }
}

@Composable
private fun TotalProgressCard(state: HomeUiState.Ready) {
    val completed = state.masteredWordCount + state.learningWordCount
    val fraction = if (state.totalWordCount == 0) 0f else completed.toFloat() / state.totalWordCount
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("已开始学习", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$completed / ${state.totalWordCount}", style = MaterialTheme.typography.headlineSmall, color = HomeInk)
                }
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = HomeMoss)
            }
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = HomeMoss,
                trackColor = Color(0xFFE2EAE2)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ProgressLegend("已掌握", state.masteredWordCount, HomeMoss)
                ProgressLegend("学习中", state.learningWordCount, Color(0xFFC88735))
                ProgressLegend("待学", state.unstartedWordCount, Color(0xFF66756D))
            }
        }
    }
}

@Composable
private fun ProgressLegend(label: String, value: Int, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = HomeInk)
    }
}

@Composable
private fun OverviewCard(
    modifier: Modifier,
    label: String,
    value: String,
    note: String,
    accent: Color
) {
    Card(
        modifier = modifier.heightIn(min = 126.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                Spacer(Modifier.width(28.dp).height(5.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = HomeInk)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeShortcutGrid(
    onOpenBooks: () -> Unit,
    onOpenMistakes: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ShortcutCard(Modifier.weight(1f), "词书", "选择学习内容", Icons.Outlined.Book, onOpenBooks)
        ShortcutCard(Modifier.weight(1f), "错词本", "回看薄弱词", Icons.Outlined.WarningAmber, onOpenMistakes)
    }
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ShortcutCard(Modifier.weight(1f), "搜索", "查找单词", Icons.Outlined.Search, onOpenSearch)
        ShortcutCard(Modifier.weight(1f), "统计", "查看进度", Icons.Outlined.MenuBook, onOpenStats)
    }
    Spacer(Modifier.height(12.dp))
    ShortcutCard(Modifier.fillMaxWidth(), "设置", "调整每日计划与偏好", Icons.Outlined.Settings, onOpenSettings)
}

@Composable
private fun ShortcutCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        color = Color(0xFFF9FBF7),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = Color(0xFFE4EEE5), shape = RoundedCornerShape(13.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = HomeMoss
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = HomeInk)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyHome(onOpenBooks: () -> Unit, onOpenSettings: () -> Unit) {
    HomeHeader(streakDays = 0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = HomeMoss)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("先选一本词书", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("建立今天的学习计划，从第一组单词开始。", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD9ECE1))
            Button(
                onClick = onOpenBooks,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HomeSand, contentColor = HomeMoss)
            ) {
                Text("选择词书")
            }
        }
    }
}
