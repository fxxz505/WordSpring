package com.wordspring.app.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.wordspring.app.ui.components.EmptyState
import com.wordspring.app.ui.components.LoadingState

private val StatsCanvas = Color(0xFFF4F6F1)
private val StatsInk = Color(0xFF123229)
private val StatsMoss = Color(0xFF195543)

@Composable
fun StatsScreen(contentPadding: PaddingValues, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.isLoading -> LoadingState(message = "正在整理学习记录…")
        state.snapshot == null -> EmptyState(title = "还没有学习数据", message = "完成第一组学习后，这里会呈现你的成长轨迹。")
        else -> {
            val s = state.snapshot!!
            Column(
                modifier = Modifier.fillMaxSize().background(StatsCanvas).padding(contentPadding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                Text("学习统计", style = MaterialTheme.typography.headlineMedium, color = StatsInk)
                Text("把坚持变成看得见的进步。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = StatsMoss, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("连续学习", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("保持节奏，复习会越来越轻松。", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8ECE1))
                        }
                        Text("${s.streakDays} 天", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFFFD57D))
                    }
                }
                StatsSection("今天", listOf("复习" to (s.today?.reviewedCount ?: 0).toString(), "新词" to (s.today?.newCount ?: 0).toString()))
                StatsSection("近 7 天", listOf("复习" to s.reviewed7.toString(), "正确率" to percent(s.correct7, s.reviewed7), "新词" to s.new7.toString()))
                StatsSection("近 30 天", listOf("复习" to s.reviewed30.toString(), "正确率" to percent(s.correct30, s.reviewed30), "新词" to s.new30.toString()))
                WeeklyTrendCard(s.last7.map { it.reviewedCount })
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WeeklyTrendCard(values: List<Int>) {
    val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("近 7 天复习趋势", style = MaterialTheme.typography.titleMedium, color = StatsInk)
            if (values.isEmpty() || values.all { it == 0 }) {
                Text("完成今天的第一组学习后，这里会显示每日复习量。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(modifier = Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    values.takeLast(7).forEach { value ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = StatsMoss,
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                modifier = Modifier.width(20.dp).height((value.toFloat() / maximum * 60).coerceAtLeast(4f).dp)
                            ) {}
                            Text(value.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSection(title: String, values: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF7))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = StatsInk)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                values.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.titleLarge, color = StatsInk, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun percent(num: Int, denom: Int): String = if (denom == 0) "--" else "${(num.toDouble() / denom * 100).toInt()}%"
