package com.wordspring.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A loading state. Provides a polite Chinese hint and is announced by TalkBack. */
@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String = "正在加载…") {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * An empty state with optional icon, title, message and a primary action.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.sizeIn(maxWidth = 96.dp, maxHeight = 96.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * A recoverable error state. The message names the next step the user can take.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    retryLabel: String = "重试",
    backLabel: String = "返回"
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive; contentDescription = message },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "出了点问题",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(retryLabel) }
            Spacer(Modifier.height(8.dp))
        }
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(backLabel) }
        }
    }
}
