package com.wordspring.app.feature.study.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.ui.components.ErrorState
import com.wordspring.app.ui.components.LoadingState

@Composable
fun StudySummaryScreen(sessionId: String, onBackHome: () -> Unit, onReviewMistakes: () -> Unit, viewModel: StudySummaryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.isLoading -> LoadingState(message = "正在生成学习总结…")
        state.errorMessage != null -> ErrorState(message = state.errorMessage!!, onRetry = viewModel::load, onBack = onBackHome)
        else -> SummaryContent(state, onBackHome, onReviewMistakes)
    }
}

@Composable
private fun SummaryContent(state: SummaryUiState, onBackHome: () -> Unit, onReviewMistakes: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6F1)).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF195543)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFFFFD57D), modifier = Modifier.height(72.dp).width(72.dp))
                Spacer(Modifier.height(12.dp))
                Text("这一组完成得很好", style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryMetric("完成", "${state.completedCount} / ${state.totalCount}")
                    SummaryMetric("正确率", "${state.correctRate}%")
                    SummaryMetric("用时", "${(state.durationSeconds / 60).coerceAtLeast(1)} 分钟")
                }
            }
        }
        if (state.remainingDue > 0) {
            Spacer(Modifier.height(14.dp))
            Text("还有 ${state.remainingDue} 张待复习卡片，稍后再来巩固。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        if (state.remainingDue > 0) {
            OutlinedButton(onClick = onReviewMistakes, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("查看错词") }
            Spacer(Modifier.height(10.dp))
        }
        Button(onClick = onBackHome, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF195543))) { Text("返回首页") }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFD8ECE1))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
