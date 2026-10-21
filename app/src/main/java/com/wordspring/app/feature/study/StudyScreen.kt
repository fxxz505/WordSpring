package com.wordspring.app.feature.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.QuestionType
import com.wordspring.app.ui.components.ErrorState
import com.wordspring.app.ui.components.LoadingState
import com.wordspring.app.ui.theme.LocalReduceMotion
import com.wordspring.app.ui.theme.gradeColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    sessionId: String,
    onExit: () -> Unit,
    onComplete: () -> Unit,
    onOpenWord: (Long) -> Unit,
    viewModel: StudyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ttsSpeaking by viewModel.ttsSpeaking.collectAsStateWithLifecycle()
    var showExitSheet by rememberSaveable { mutableStateOf(false) }

    BackHandler { showExitSheet = true }

    when (val s = state) {
        StudyUiState.Loading -> LoadingState(message = "正在加载单词卡…")
        StudyUiState.NotFound -> ErrorState(
            message = "没有找到这组学习，请返回首页重新开始。",
            onBack = onExit
        )
        is StudyUiState.Failed -> ErrorState(
            message = s.message,
            onRetry = { viewModel.load() },
            onBack = onExit
        )
        is StudyUiState.Completed -> {
            LaunchedEffect(s.session.id) { onComplete() }
            LoadingState(message = "正在生成总结…")
        }
        is StudyUiState.Active -> ActiveCard(
            active = s,
            onReveal = viewModel::revealAnswer,
            onSelfGrade = viewModel::selfGrade,
            onSubmitSpelling = viewModel::submitSpelling,
            onSpellingInputChanged = viewModel::updateSpellingInput,
            onSelectChoice = viewModel::selectChoice,
            onContinue = viewModel::continueToNext,
            onRetry = viewModel::retry,
            onPronounce = viewModel::pronounceCurrentWord,
            onInstallTts = viewModel::installTtsData,
            onOpenWord = onOpenWord,
            ttsSpeaking = ttsSpeaking,
            onBack = { showExitSheet = true }
        )
    }

    if (showExitSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showExitSheet = false },
            sheetState = sheetState
        ) {
            ExitSheetContent(
                onContinue = { showExitSheet = false },
                onQuit = {
                    viewModel.abandon()
                    showExitSheet = false
                    onExit()
                }
            )
        }
    }
}

@Composable
private fun ExitSheetContent(
    onContinue: () -> Unit,
    onQuit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("要继续学习吗？", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "进度已自动保存，下次可以从这张卡片继续。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("继续学习", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onQuit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text("先退出，进度已保存") }
    }
}

// ---------------------------------------------------------------------------
// Swipe state shared by the recall card and its grade buttons
// ---------------------------------------------------------------------------

@Composable
private fun ActiveCard(
    active: StudyUiState.Active,
    onReveal: () -> Unit,
    onSelfGrade: (Grade) -> Unit,
    onSubmitSpelling: () -> Unit,
    onSpellingInputChanged: (String) -> Unit,
    onSelectChoice: (Int) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onPronounce: () -> Unit,
    onInstallTts: () -> Unit,
    onOpenWord: (Long) -> Unit,
    ttsSpeaking: Boolean,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val instanceKey = cardInstanceKey(active.card.cardId, active.index)
    val swipe = remember(instanceKey) { CardSwipeState() }
    val exitDistance = with(density) { 1_200.dp.toPx() }

    // After a failed save the card must come back into view so the user can retry.
    LaunchedEffect(instanceKey, active.cardState) {
        if (active.cardState != CardState.SUBMITTING && active.cardState != CardState.COMPLETE) swipe.reset()
    }

    val grade: (Grade) -> Unit = { g ->
        if (active.cardState == CardState.ANSWER_REVEALED) {
            onSelfGrade(g)
            scope.launch { swipe.flyOut(g, exitDistance) }
        }
    }
    val progress = studyGroupProgress(active.session.completedCount, active.session.queueSnapshot)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudyTopBar(
                progressCurrent = progress.current,
                progressTotal = active.total,
                etaMinutes = active.etaMinutes,
                onBack = onBack
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp)
                .imePadding()
        ) {
            Text(
                text = questionTypeLabel(active.card.questionType),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp)
            )

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (active.card.questionType) {
                    QuestionType.RECALL -> SwipeRecallCard(
                        active = active,
                        swipe = swipe,
                        ttsSpeaking = ttsSpeaking,
                        onReveal = onReveal,
                        onGrade = grade,
                        onPronounce = onPronounce,
                        onOpenWord = onOpenWord
                    )
                    QuestionType.SPELLING -> SpellingCard(
                        active = active,
                        ttsSpeaking = ttsSpeaking,
                        onInputChanged = onSpellingInputChanged,
                        onSubmit = onSubmitSpelling,
                        onPronounce = onPronounce
                    )
                    QuestionType.CHOICE -> ChoiceCard(
                        active = active,
                        ttsSpeaking = ttsSpeaking,
                        onSelect = onSelectChoice,
                        onPronounce = onPronounce
                    )
                }
            }

            if (!active.ttsAvailable) {
                TextButton(onClick = onInstallTts, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("未检测到英语语音，点此安装")
                }
            }

            BottomActionArea(
                active = active,
                onReveal = {
                    onReveal()
                    onPronounce()
                },
                onGrade = grade,
                onContinue = onContinue,
                onRetry = onRetry,
                onSubmitSpelling = onSubmitSpelling
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun StudyTopBar(
    progressCurrent: Int,
    progressTotal: Int,
    etaMinutes: Int,
    onBack: () -> Unit
) {
    val fraction = if (progressTotal <= 0) 0f else (progressCurrent - 1).coerceAtLeast(0) / progressTotal.toFloat()
    val animated by animateFloatAsState(fraction, label = "study-progress")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "$progressCurrent / $progressTotal",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "第 $progressCurrent 个，共 $progressTotal 个"
                }
            )
            Text(
                text = "约 $etaMinutes 分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Recall card
// ---------------------------------------------------------------------------

@Composable
private fun SwipeRecallCard(
    active: StudyUiState.Active,
    swipe: CardSwipeState,
    ttsSpeaking: Boolean,
    onReveal: () -> Unit,
    onGrade: (Grade) -> Unit,
    onPronounce: () -> Unit,
    onOpenWord: (Long) -> Unit
) {
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val instanceKey = cardInstanceKey(active.card.cardId, active.index)
    val isGrading = active.cardState == CardState.ANSWER_REVEALED
    val revealed = active.cardState != CardState.QUESTION
    val flipAngle by animateFloatAsState(
        targetValue = if (revealed) 0f else 180f,
        animationSpec = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessLow),
        label = "recall-card-flip"
    )
    val thresholdPx = with(density) { 96.dp.toPx() }
    val flingPx = with(density) { 900.dp.toPx() }
    val tone = swipeTone(swipe.x.value, swipe.y.value, isGrading)
    val dragProgress = (maxOf(abs(swipe.x.value), abs(swipe.y.value)) / thresholdPx).coerceIn(0f, 1f)
    val surface = MaterialTheme.colorScheme.surface
    val container = if (tone == null) surface else lerp(surface, tone.color, 0.28f * dragProgress)
    val borderColor = if (tone == null) MaterialTheme.colorScheme.outlineVariant else lerp(MaterialTheme.colorScheme.outlineVariant, tone.color, dragProgress)

    val gestureModifier = Modifier.recallCardGestures(
        instanceKey = instanceKey,
        cardState = active.cardState,
        swipe = swipe,
        thresholdPx = thresholdPx,
        flingVelocityPx = flingPx,
        onReveal = {
            onReveal()
            onPronounce()
        },
        onGrade = onGrade
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(cardFaceHeightFraction())
            .then(gestureModifier)
            .semantics {
                contentDescription = if (revealed) "单词卡背面，左滑记得，上滑模糊，右滑不记得" else "单词卡正面，轻点翻面"
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(swipe.x.value.roundToInt(), swipe.y.value.roundToInt()) }
                .rotate((swipe.x.value / with(density) { 72.dp.toPx() }).coerceIn(-8f, 8f))
                .shadow(6.dp, MaterialTheme.shapes.extraLarge, clip = false)
                .background(container, MaterialTheme.shapes.extraLarge)
                .border(1.dp, borderColor, MaterialTheme.shapes.extraLarge)
                .clip(MaterialTheme.shapes.extraLarge)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = flipAngle
                        scaleX = cardFaceScaleX(flipAngle)
                        cameraDistance = 12f * density.density
                    }
                    .padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_V)
            ) {
                if (!revealed) {
                    RecallCardFront(
                        word = active.card.word?.spelling.orEmpty(),
                        phonetic = active.card.word?.phonetic,
                        ttsAvailable = active.ttsAvailable,
                        ttsSpeaking = ttsSpeaking,
                        onPronounce = onPronounce
                    )
                } else {
                    RecallCardBack(
                        card = active.card,
                        ttsAvailable = recallCardPronunciationPlacement(active.cardState, active.ttsAvailable) != null,
                        ttsSpeaking = ttsSpeaking,
                        onPronounce = onPronounce,
                        onOpenWord = { onOpenWord(active.card.wordId) }
                    )
                }
                if (tone != null && dragProgress > 0.15f) {
                    SwipeLabel(
                        tone = tone,
                        modifier = Modifier.align(Alignment.TopEnd).alpha(dragProgress)
                    )
                }
            }
        }
    }
}

internal enum class RecallCardPronunciationPlacement { WITH_WORD }

internal fun recallCardPronunciationPlacement(
    cardState: CardState,
    ttsAvailable: Boolean
): RecallCardPronunciationPlacement? =
    if (cardState == CardState.ANSWER_REVEALED && ttsAvailable) RecallCardPronunciationPlacement.WITH_WORD else null

@Composable
private fun RecallCardFront(
    word: String,
    phonetic: String?,
    ttsAvailable: Boolean,
    ttsSpeaking: Boolean,
    onPronounce: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = word,
                fontSize = wordFontSize(word, large = true),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (!phonetic.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(phonetic, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            if (ttsAvailable) PronunciationButton(onClick = onPronounce, speaking = ttsSpeaking)
        }
        Text(
            "想一想它的意思，再轻点翻面",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecallCardBack(
    card: CardUi,
    ttsAvailable: Boolean,
    ttsSpeaking: Boolean,
    onPronounce: () -> Unit,
    onOpenWord: () -> Unit
) {
    val word = card.word?.spelling.orEmpty()
    val senses = card.senses
    // The back face never scrolls (vertical drags grade the card), so long entries are clipped
    // per line and the full list lives on the word detail page.
    val maxLinesPerSense = when {
        senses.size <= 2 -> 3
        senses.size <= 4 -> 2
        else -> 1
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // Header: word + phonetic, pronunciation on the right.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = word,
                    fontSize = wordFontSize(word, large = false),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                card.word?.phonetic?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (ttsAvailable) PronunciationButton(onClick = onPronounce, speaking = ttsSpeaking)
        }
        Spacer(Modifier.height(20.dp))

        // Meanings sit in their own tinted panel so the card reads as header / meanings / forms
        // instead of a few lines floating at the top of an empty card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (senses.isEmpty()) {
                Text("暂无释义", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            senses.take(MAX_SENSE_ROWS).forEach { sense ->
                SenseRow(sense, maxLines = maxLinesPerSense, textSize = 19.sp)
            }
            if (senses.size > MAX_SENSE_ROWS) {
                Text(
                    "还有 ${senses.size - MAX_SENSE_ROWS} 种词性，查看单词详情",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val primary = card.primaryDefinition
        primary?.example?.takeIf { it.isNotBlank() }?.let { example ->
            Spacer(Modifier.height(16.dp))
            Text(
                example,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            primary.translation?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (card.forms.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "词形变化",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                card.forms.forEach { FormChip(it) }
            }
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenWord, modifier = Modifier.align(Alignment.End)) {
            Text("单词详情")
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** Inner padding of the recall card; generous so text stays clear of the rounded corners. */
private val CARD_PADDING_H = 28.dp
private val CARD_PADDING_V = 28.dp

private const val MAX_SENSE_ROWS = 5

@Composable
private fun SenseRow(sense: SenseLine, maxLines: Int = Int.MAX_VALUE, textSize: androidx.compose.ui.unit.TextUnit = 18.sp) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        if (sense.pos != null) {
            PosChip(sense.pos)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = sense.text,
            fontSize = textSize,
            lineHeight = textSize * 1.35f,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PosChip(pos: String) {
    Text(
        text = pos,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .padding(top = 3.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
            .widthIn(min = 44.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun FormChip(form: WordFormItem) {
    Row(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(form.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(form.value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private data class SwipeTone(val label: String, val color: Color, val icon: ImageVector)

@Composable
private fun swipeTone(offsetX: Float, offsetY: Float, enabled: Boolean): SwipeTone? {
    if (!enabled || (abs(offsetX) < 1f && abs(offsetY) < 1f)) return null
    val colors = gradeColors()
    return when (swipeVisualTone(offsetX, offsetY)) {
        SwipeVisualTone.HARD_ORANGE -> SwipeTone("模糊", colors.fuzzy, Icons.Filled.KeyboardArrowUp)
        SwipeVisualTone.AGAIN_RED -> SwipeTone("不记得", colors.forgot, Icons.AutoMirrored.Filled.KeyboardArrowRight)
        SwipeVisualTone.GOOD_GREEN -> SwipeTone("记得", colors.remembered, Icons.AutoMirrored.Filled.KeyboardArrowLeft)
        SwipeVisualTone.NEUTRAL -> null
    }
}

@Composable
private fun SwipeLabel(tone: SwipeTone, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(tone.color, RoundedCornerShape(100.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(tone.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(tone.label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

// ---------------------------------------------------------------------------
// Spelling card
// ---------------------------------------------------------------------------

@Composable
private fun SpellingCard(
    active: StudyUiState.Active,
    ttsSpeaking: Boolean,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onPronounce: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val isFeedback = active.cardState == CardState.FEEDBACK
    val isLocked = active.cardState != CardState.QUESTION
    val answer = active.card.word?.spelling.orEmpty()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        CardSurface {
            Text(
                "根据释义拼写单词",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            active.card.senses.take(MAX_SENSE_ROWS).forEach { sense ->
                SenseRow(sense, maxLines = 3, textSize = 20.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (answer.isNotEmpty() && !isFeedback) {
                Text(
                    "提示：${answer.first()}${" _".repeat((answer.length - 1).coerceAtLeast(0))}（${answer.length} 个字母）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = active.spellingUserInput,
            onValueChange = { if (!isLocked) onInputChanged(it) },
            label = { Text("输入英文单词") },
            singleLine = true,
            enabled = !isLocked,
            isError = active.spellingResult?.isCorrect == false,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                if (active.cardState == CardState.QUESTION && active.spellingUserInput.isNotBlank()) onSubmit()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "拼写输入框" }
        )

        val result = active.spellingResult
        if (isFeedback && result != null) {
            Spacer(Modifier.height(12.dp))
            FeedbackBanner(correct = result.isCorrect)
            Spacer(Modifier.height(12.dp))
            CardSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            result.correctAnswer,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (result.isCorrect) gradeColors().remembered else MaterialTheme.colorScheme.onSurface
                        )
                        active.card.word?.phonetic?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (active.ttsAvailable) PronunciationButton(onClick = onPronounce, speaking = ttsSpeaking)
                }
                if (!result.isCorrect) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "你的拼写：${result.userAnswer.ifBlank { "（空）" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    result.hint?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Choice card
// ---------------------------------------------------------------------------

@Composable
private fun ChoiceCard(
    active: StudyUiState.Active,
    ttsSpeaking: Boolean,
    onSelect: (Int) -> Unit,
    onPronounce: () -> Unit
) {
    val word = active.card.word?.spelling.orEmpty()
    val isLocked = active.cardState != CardState.QUESTION
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = word,
            fontSize = wordFontSize(word, large = false),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        active.card.word?.phonetic?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (active.ttsAvailable) {
            Spacer(Modifier.height(8.dp))
            PronunciationButton(onClick = onPronounce, speaking = ttsSpeaking)
        }
        Spacer(Modifier.height(20.dp))
        Text("选择正确释义", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        active.choiceOptions.forEachIndexed { index, option ->
            val isSelected = active.choiceSelectedIndex == index
            val stateLabel = when {
                isLocked && option.isCorrect -> "正确答案"
                isLocked && isSelected -> "你的选择"
                else -> ""
            }
            ChoiceOptionRow(
                text = option.text,
                stateLabel = stateLabel,
                isSelected = isSelected,
                showCorrect = isLocked && option.isCorrect,
                showWrong = isLocked && isSelected && !option.isCorrect,
                enabled = !isLocked,
                onClick = { onSelect(index) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .semantics {
                        role = Role.RadioButton
                        stateDescription = if (isSelected) "已选择" else "未选择"
                        contentDescription = "选项 ${index + 1}：${option.text}。$stateLabel"
                    }
            )
        }

        if (active.cardState == CardState.FEEDBACK) {
            Spacer(Modifier.height(12.dp))
            FeedbackBanner(correct = active.lastIsCorrect == true)
            if (active.card.senses.size > 1) {
                Spacer(Modifier.height(12.dp))
                CardSurface {
                    Text("全部释义", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    active.card.senses.take(MAX_SENSE_ROWS).forEach {
                        SenseRow(it, maxLines = 2, textSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceOptionRow(
    text: String,
    stateLabel: String,
    isSelected: Boolean,
    showCorrect: Boolean,
    showWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val grade = gradeColors()
    val borderColor = when {
        showCorrect -> grade.remembered
        showWrong -> grade.forgot
        isSelected -> colors.primary
        else -> colors.outlineVariant
    }
    val bgColor = when {
        showCorrect -> grade.remembered.copy(alpha = 0.14f)
        showWrong -> grade.forgot.copy(alpha = 0.14f)
        isSelected -> colors.primary.copy(alpha = 0.08f)
        else -> colors.surface
    }
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(bgColor)
            .border(if (showCorrect || showWrong) 2.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCorrect) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = grade.remembered)
            Spacer(Modifier.width(8.dp))
        } else if (showWrong) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = grade.forgot)
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (stateLabel.isNotBlank()) {
                Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, MaterialTheme.shapes.large, clip = false)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(20.dp)
    ) { content() }
}

@Composable
private fun PronunciationButton(onClick: () -> Unit, speaking: Boolean) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
    ) {
        Icon(
            if (speaking) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = "发音",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun FeedbackBanner(correct: Boolean) {
    val color = if (correct) gradeColors().remembered else gradeColors().forgot
    val icon = if (correct) Icons.Filled.Check else Icons.Filled.Close
    val text = if (correct) "回答正确" else "答错了，这个单词稍后会再次出现"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun BottomActionArea(
    active: StudyUiState.Active,
    onReveal: () -> Unit,
    onGrade: (Grade) -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onSubmitSpelling: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp).heightIn(min = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (active.showSaving) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在保存…", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
        active.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        val type = active.card.questionType
        when (active.cardState) {
            CardState.QUESTION -> when (type) {
                QuestionType.RECALL -> PrimaryButton(text = "显示释义", onClick = onReveal)
                QuestionType.SPELLING -> PrimaryButton(
                    text = "提交",
                    onClick = onSubmitSpelling,
                    enabled = active.spellingUserInput.isNotBlank()
                )
                QuestionType.CHOICE -> Text(
                    "点选一个选项作答",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CardState.ANSWER_REVEALED -> GradeRow(onGrade = onGrade, advanced = active.advancedGrading)
            CardState.SUBMITTING, CardState.COMPLETE -> Box(Modifier.heightIn(min = 56.dp))
            CardState.FEEDBACK -> PrimaryButton(text = "继续", onClick = onContinue)
            CardState.ERROR -> PrimaryButton(text = "重试", onClick = onRetry)
        }
    }
}

/** Tap alternative to swiping, laid out in the same direction as the gestures. */
@Composable
private fun GradeRow(onGrade: (Grade) -> Unit, advanced: Boolean) {
    val colors = gradeColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GradeButton("记得", "← 左滑", Icons.AutoMirrored.Filled.KeyboardArrowLeft, colors.remembered, Modifier.weight(1f)) { onGrade(Grade.GOOD) }
        GradeButton("模糊", "↑ 上滑", Icons.Filled.KeyboardArrowUp, colors.fuzzy, Modifier.weight(1f)) { onGrade(Grade.HARD) }
        GradeButton("不记得", "右滑 →", Icons.AutoMirrored.Filled.KeyboardArrowRight, colors.forgot, Modifier.weight(1f)) { onGrade(Grade.AGAIN) }
        if (advanced) {
            GradeButton("太简单", "点按", Icons.Filled.KeyboardArrowDown, colors.remembered, Modifier.weight(1f)) { onGrade(Grade.EASY) }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    hint: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("primary_button"),
        colors = ButtonDefaults.buttonColors()
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

private fun wordFontSize(word: String, large: Boolean) = when {
    word.length > 16 -> if (large) 30.sp else 24.sp
    word.length > 11 -> if (large) 36.sp else 28.sp
    else -> if (large) 46.sp else 34.sp
}

private fun questionTypeLabel(type: QuestionType): String = when (type) {
    QuestionType.RECALL -> "词义回忆"
    QuestionType.SPELLING -> "拼写"
    QuestionType.CHOICE -> "选择题"
}
