package com.wordspring.app.feature.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordspring.app.R
import com.wordspring.app.domain.model.Definition
import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.QuestionType
import com.wordspring.app.ui.components.ErrorState
import com.wordspring.app.ui.components.LoadingState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import android.view.MotionEvent

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
        StudyUiState.Loading -> LoadingState(message = "Loading study card...")
        StudyUiState.NotFound -> ErrorState(
            message = "Study session was not found. Return home and start again.",
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
        Text(
            "Continue studying?",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your progress is saved. You can continue from this card later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors()
        ) { Text("继续学习", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onQuit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) { Text("先退出，进度已保存") }
    }
}

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
    ttsSpeaking: Boolean,
    onBack: () -> Unit
) {
    val cardWord = active.card.word
    val primaryDef = active.card.primaryDefinition
    val studyBackground = Color(0xFF123A31)

    Scaffold(
        containerColor = studyBackground,
        topBar = {
            StudyTopBar(
                progressCurrent = studyGroupProgress(
                    completedCount = active.session.completedCount,
                    queue = active.session.queueSnapshot
                ).current,
                progressTotal = active.total,
                correctCount = active.session.completedCount,
                etaMinutes = active.etaMinutes,
                onBack = onBack
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .imePadding()
        ) {
            // Question type chip
            Text(
                text = questionTypeLabel(active.card.questionType),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFB9D3C4),
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            // Body 鈥?vertically centered, no scroll for primary flow per spec.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (active.card.questionType) {
                    QuestionType.RECALL -> SwipeRecallCard(
                        active = active,
                        word = cardWord?.spelling ?: "",
                        phonetic = cardWord?.phonetic,
                        primaryDef = primaryDef,
                        ttsAvailable = active.ttsAvailable,
                        ttsSpeaking = ttsSpeaking,
                        onReveal = onReveal,
                        onSelfGrade = onSelfGrade,
                        onPronounce = onPronounce,
                        onInstallTts = onInstallTts
                    )
                    QuestionType.SPELLING -> SpellingCard(
                        active = active,
                        primaryDef = primaryDef,
                        onInputChanged = onSpellingInputChanged,
                        onSubmit = onSubmitSpelling
                    )
                    QuestionType.CHOICE -> ChoiceCard(
                        active = active,
                        word = cardWord?.spelling ?: "",
                        phonetic = cardWord?.phonetic,
                        ttsAvailable = active.ttsAvailable,
                        onSelect = onSelectChoice,
                        onPronounce = onPronounce
                    )
                }
            }

            if (!active.ttsAvailable) {
                TextButton(onClick = onInstallTts) { Text("安装英语语音") }
            }

            // Fixed bottom action area (鈮?64dp, button 鈮?48dp)
            BottomActionArea(
                active = active,
                onReveal = onReveal,
                onContinue = onContinue,
                onRetry = onRetry,
                onSubmitSpelling = onSubmitSpelling
            )
            // Respect nav bar inset
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun StudyTopBar(
    progressCurrent: Int,
    progressTotal: Int,
    correctCount: Int,
    etaMinutes: Int,
    onBack: () -> Unit
) {
    val muted = Color(0xFFB9D3C4)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = "杩斿洖" }
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$progressCurrent / $progressTotal",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "正确 $correctCount",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
        }
        Text(
            text = "约 $etaMinutes 分钟",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SwipeRecallCard(
    active: StudyUiState.Active,
    word: String,
    phonetic: String?,
    primaryDef: Definition?,
    ttsAvailable: Boolean,
    ttsSpeaking: Boolean,
    onReveal: () -> Unit,
    onSelfGrade: (Grade) -> Unit,
    onPronounce: () -> Unit,
    onInstallTts: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val instanceKey = cardInstanceKey(active.card.cardId, active.index)
    val offsetX = remember(instanceKey) { Animatable(0f) }
    val offsetY = remember(instanceKey) { Animatable(0f) }
    val isGrading = active.cardState == CardState.ANSWER_REVEALED
    val revealed = active.cardState != CardState.QUESTION
    val flipAngle by animateFloatAsState(
        targetValue = if (revealed) 0f else 180f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "recall-card-flip"
    )
    val thresholdPx = with(density) { 96.dp.toPx() }
    val feedback = swipeFeedback(offsetX.value, offsetY.value, isGrading)
    var dragOffset by remember(instanceKey) { mutableStateOf(Offset.Zero) }
    var touchStart by remember(instanceKey) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(instanceKey) {
        val neutral = nextCardOffset(offsetX.value, offsetY.value)
        offsetX.snapTo(neutral.x)
        offsetY.snapTo(neutral.y)
    }

    val gestureModifier = if (!isGrading) {
        Modifier.pointerInput(instanceKey) {
            detectTapGestures(onTap = {
                onReveal()
                onPronounce()
            })
        }
    } else Modifier

    // The fixed outer box owns every pointer event. The moving visual card is
    // deliberately inside it, so it cannot move its own gesture hit target away.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(cardFaceHeightFraction())
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .rotate((offsetX.value / with(density) { 72.dp.toPx() }).coerceIn(-8f, 8f))
                .background(feedback.container, MaterialTheme.shapes.extraLarge)
                .border(1.dp, feedback.border, MaterialTheme.shapes.extraLarge)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = flipAngle
                        scaleX = cardFaceScaleX(flipAngle)
                        cameraDistance = 12f * density.density
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 42.dp),
                ) {
                    if (isGrading && (abs(offsetX.value) > 16f || abs(offsetY.value) > 16f)) {
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SwipeFeedbackLabel(feedback, visible = true)
                        }
                    }
                    if (active.cardState == CardState.QUESTION) {
                        RecallCardFront(
                            word = word,
                            phonetic = phonetic,
                            ttsAvailable = ttsAvailable,
                            ttsSpeaking = ttsSpeaking,
                            onPronounce = onPronounce
                        )
                    } else {
                        RecallCardBack(
                            word = word,
                            phonetic = phonetic,
                            primaryDef = primaryDef
                        )
                    }
                }
            }
        }
        // This transparent sibling sits above the visual card while grading.
        // It guarantees that a drag beginning on text has the same owner as
        // one beginning in empty card space.
        if (isGrading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                touchStart = Offset(event.x, event.y)
                                dragOffset = Offset.Zero
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                dragOffset = Offset(event.x - touchStart.x, event.y - touchStart.y)
                                scope.launch {
                                    offsetX.snapTo(dragOffset.x)
                                    offsetY.snapTo(dragOffset.y)
                                }
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                val grade = resolveSwipeGrade(dragOffset.x, dragOffset.y, thresholdPx)
                                scope.launch {
                                    if (grade == null) {
                                        offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    } else {
                                        val exitDistance = with(density) { 1_420.dp.toPx() }
                                        when (grade) {
                                            Grade.AGAIN -> offsetX.animateTo(exitDistance, spring(stiffness = Spring.StiffnessMedium))
                                            Grade.HARD -> offsetY.animateTo(-exitDistance, spring(stiffness = Spring.StiffnessMedium))
                                            Grade.GOOD -> offsetX.animateTo(-exitDistance, spring(stiffness = Spring.StiffnessMedium))
                                            Grade.EASY -> Unit
                                        }
                                        onSelfGrade(grade)
                                    }
                                }
                                true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                scope.launch {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                true
                            }
                            else -> true
                        }
                }
            )
        }
        // The swipe layer consumes revealed-card drags. Keep the control above it
        // for reliable taps, but apply the card's exact motion so it remains part
        // of the card rather than looking like a fixed screen-level button.
        if (recallCardPronunciationPlacement(active.cardState, ttsAvailable) != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                    .rotate((offsetX.value / with(density) { 72.dp.toPx() }).coerceIn(-8f, 8f))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 54.dp)
                ) {
                    PronunciationButton(onClick = onPronounce, enabled = true, speaking = ttsSpeaking)
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

internal enum class RecallCardWordHeaderAnchor { STABLE_CENTER }

internal fun recallCardWordHeaderAnchor(cardState: CardState): RecallCardWordHeaderAnchor =
    when (cardState) {
        CardState.QUESTION, CardState.ANSWER_REVEALED -> RecallCardWordHeaderAnchor.STABLE_CENTER
        else -> RecallCardWordHeaderAnchor.STABLE_CENTER
    }

@Composable
private fun RecallCardFront(
    word: String,
    phonetic: String?,
    ttsAvailable: Boolean,
    ttsSpeaking: Boolean,
    onPronounce: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RecallCardWordHeader(
            word = word,
            phonetic = phonetic,
            modifier = Modifier.align(Alignment.Center),
            ttsAvailable = ttsAvailable,
            ttsSpeaking = ttsSpeaking,
            onPronounce = onPronounce
        )
    }
}

@Composable
private fun RecallCardBack(
    word: String,
    phonetic: String?,
    primaryDef: Definition?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RecallCardWordHeader(
            word = word,
            phonetic = phonetic,
            modifier = Modifier.align(Alignment.Center),
            ttsAvailable = false,
            ttsSpeaking = false,
            onPronounce = {},
            reservePronunciationSpace = true
        )
        if (primaryDef != null) {
            val definitionDisplay = remember(primaryDef) { DefinitionDisplayFormatter.format(primaryDef) }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                definitionDisplay.lines.forEachIndexed { index, line ->
                    Text(
                        text = line,
                        fontSize = 22.sp,
                        fontWeight = if (index == 0) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index < definitionDisplay.lines.lastIndex) Spacer(Modifier.height(8.dp))
                }
                if (!primaryDef.example.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(primaryDef.example, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    if (!primaryDef.translation.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(primaryDef.translation, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecallCardWordHeader(
    word: String,
    phonetic: String?,
    modifier: Modifier,
    ttsAvailable: Boolean,
    ttsSpeaking: Boolean,
    onPronounce: () -> Unit,
    reservePronunciationSpace: Boolean = true
) {
    val mainWordSize = if (word.length > 12) 36.sp else 44.sp
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = word,
            fontSize = mainWordSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Front: $word"
                liveRegion = LiveRegionMode.Polite
            }
        )
        if (!phonetic.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(phonetic, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        if (ttsAvailable) {
            PronunciationButton(onClick = onPronounce, enabled = true, speaking = ttsSpeaking)
        } else if (reservePronunciationSpace) {
            Spacer(Modifier.height(52.dp))
        }
    }
}
private data class SwipeFeedback(
    val label: String,
    val container: Color,
    val border: Color,
    val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun swipeFeedback(
    offsetX: Float,
    offsetY: Float,
    enabled: Boolean
): SwipeFeedback {
    val base = SwipeFeedback(
        label = "翻开卡片", container = Color(0xFFFFFCF5), border = Color(0xFFF1E8D9),
        accent = Color(0xFF1A5A4A), icon = Icons.Filled.KeyboardArrowDown
    )
    if (!enabled || (abs(offsetX) < 1f && abs(offsetY) < 1f)) return base
    return when (swipeVisualTone(offsetX, offsetY)) {
        SwipeVisualTone.HARD_ORANGE -> SwipeFeedback(
            label = "模糊", container = Color(0xFFFFBE6B), border = Color(0xFFE87500),
            accent = Color(0xFF963C00), icon = Icons.Filled.KeyboardArrowUp
        )
        SwipeVisualTone.AGAIN_RED -> SwipeFeedback(
            label = "Not remembered", container = Color(0xFFFF8B82), border = Color(0xFFD52F24),
            accent = Color(0xFF8F1812), icon = Icons.Filled.KeyboardArrowRight
        )
        SwipeVisualTone.GOOD_GREEN -> SwipeFeedback(
            label = "记得", container = Color(0xFF78D99B), border = Color(0xFF16834A),
            accent = Color(0xFF075C30), icon = Icons.Filled.KeyboardArrowLeft
        )
        SwipeVisualTone.NEUTRAL -> base
    }
}

@Composable
private fun SwipeFeedbackLabel(feedback: SwipeFeedback, visible: Boolean) {
    if (!visible) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(feedback.accent.copy(alpha = 0.14f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(feedback.icon, contentDescription = null, tint = feedback.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(feedback.label, style = MaterialTheme.typography.labelLarge, color = feedback.accent)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SwipeHintRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SwipeHint("记得", "左滑", Color(0xFF8ED4A4), Icons.Filled.KeyboardArrowLeft)
        SwipeHint("模糊", "上滑", Color(0xFFF4B66F), Icons.Filled.KeyboardArrowUp)
        SwipeHint("不记得", "右滑", Color(0xFFFFA29B), Icons.Filled.KeyboardArrowRight)
    }
}

@Composable
private fun SwipeHint(
    label: String,
    direction: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(3.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
            Text(direction, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB9D3C4))
        }
    }
}

@Composable
private fun SpellingCard(
    active: StudyUiState.Active,
    primaryDef: Definition?,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val isFeedback = active.cardState == CardState.FEEDBACK || active.cardState == CardState.ERROR
    val isSubmitting = active.cardState == CardState.SUBMITTING

    // Show Chinese definition as the prompt.
    if (primaryDef != null) {
        val definitionDisplay = remember(primaryDef) { DefinitionDisplayFormatter.format(primaryDef) }
        Text(
            text = definitionDisplay.lines.joinToString(separator = "\n"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (!primaryDef.example.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = primaryDef.example,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = active.spellingUserInput,
        onValueChange = if (!isFeedback && !isSubmitting) onInputChanged else ({ }),
        label = { Text("输入英文单词") },
        singleLine = true,
        enabled = !isFeedback && !isSubmitting,
        isError = active.spellingResult?.isCorrect == false,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            keyboard?.hide()
            if (active.cardState == CardState.QUESTION) onSubmit()
        }),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Spelling input" }
    )

    if (isFeedback) {
        val result = active.spellingResult
        if (result != null) {
            Spacer(Modifier.height(12.dp))
            if (!result.isCorrect) {
                Text(
                    text = "正确拼写：${result.correctAnswer}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!result.hint.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        result.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FeedbackBanner(correct = result.isCorrect)
        }
    }
}

@Composable
private fun ChoiceCard(
    active: StudyUiState.Active,
    word: String,
    phonetic: String?,
    ttsAvailable: Boolean,
    onSelect: (Int) -> Unit,
    onPronounce: () -> Unit
) {
    val mainWordSize = if (word.length > 12) 26.sp else 32.sp
    Text(
        text = word,
        fontSize = mainWordSize,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    if (!phonetic.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(phonetic, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (ttsAvailable) {
        Spacer(Modifier.height(8.dp))
        PronunciationButton(onClick = onPronounce, enabled = active.cardState == CardState.QUESTION, speaking = false)
    }
    Spacer(Modifier.height(20.dp))
    Text(
        "选择正确释义",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))

    val isLocked = active.cardState != CardState.QUESTION
    active.choiceOptions.forEachIndexed { index, option ->
        val isSelected = active.choiceSelectedIndex == index
        val stateLabel = when {
            isLocked && option.isCorrect -> "正确答案"
            isLocked && isSelected && !option.isCorrect -> "你选的（错误）"
            else -> ""
        }
        ChoiceOptionRow(
            text = option.text,
            stateLabel = stateLabel,
            isSelected = isSelected,
            isLocked = isLocked,
            showCorrect = isLocked && option.isCorrect,
            showWrong = isLocked && isSelected && !option.isCorrect,
            onClick = { if (!isLocked) onSelect(index) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .semantics {
                    role = Role.RadioButton
                    stateDescription = if (isSelected) "Selected" else "Not selected"
                    contentDescription = "选项 ${index + 1}：${option.text}。$stateLabel"
                }
        )
    }

    if (active.cardState == CardState.FEEDBACK) {
        Spacer(Modifier.height(12.dp))
        FeedbackBanner(correct = active.lastIsCorrect == true)
    }
}

@Composable
private fun ChoiceOptionRow(
    text: String,
    stateLabel: String,
    isSelected: Boolean,
    isLocked: Boolean,
    showCorrect: Boolean,
    showWrong: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        showCorrect -> MaterialTheme.colorScheme.tertiary
        showWrong -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val bgColor = when {
        showCorrect -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        showWrong -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .background(bgColor, MaterialTheme.shapes.medium)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(enabled = !isLocked, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCorrect) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(8.dp))
        } else if (showWrong) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (stateLabel.isNotBlank()) {
                Text(
                    stateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PronunciationButton(onClick: () -> Unit, enabled: Boolean, speaking: Boolean) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(52.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
            .semantics { contentDescription = "发音" }
    ) {
        Icon(if (speaking) Icons.Filled.VolumeUp else Icons.Filled.PlayArrow, contentDescription = null)
    }
}

@Composable
private fun FeedbackBanner(correct: Boolean) {
    val color = if (correct) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val icon = if (correct) Icons.Filled.Check else Icons.Filled.Close
    val text = if (correct) "回答正确" else "这个单词会更快再次出现"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                contentDescription = if (correct) "回答正确" else "回答错误：$text"
                liveRegion = LiveRegionMode.Assertive
            },
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
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onSubmitSpelling: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (active.showSaving) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在保存…", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
        when (active.cardState) {
            CardState.QUESTION -> {
                if (active.card.questionType == QuestionType.SPELLING) {
                    PrimaryButton(text = "提交", onClick = onSubmitSpelling)
                }
                // For CHOICE, no primary button at QUESTION 鈥?selecting an option commits.
            }
            CardState.ANSWER_REVEALED -> Unit
            CardState.SUBMITTING -> {
                // Keep original primary button position occupied (spec: 鈮?8dp jump)
                Box(modifier = Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    Text("…", style = MaterialTheme.typography.titleMedium)
                }
            }
            CardState.FEEDBACK -> Unit
            CardState.ERROR -> {
                PrimaryButton(text = "重试", onClick = onRetry)
            }
            CardState.COMPLETE -> { /* nothing 鈥?transitioning */ }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("primary_button"),
        colors = ButtonDefaults.buttonColors()
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SwipeActionPrompt() {
    Text(
        text = "拖动卡片来选择记忆程度",
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFFE2F1E7),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    )
}

@Composable
private fun LegacyGradeRow(onSelfGrade: (Grade) -> Unit, advanced: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GradeButton(label = "不记得", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)) {
            onSelfGrade(Grade.AGAIN)
        }
        GradeButton(label = "模糊", color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f)) {
            onSelfGrade(Grade.HARD)
        }
        GradeButton(label = "记得", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f)) {
            onSelfGrade(Grade.GOOD)
        }
        if (advanced) {
            GradeButton(label = "熟练", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)) {
                onSelfGrade(Grade.EASY)
            }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Text(label, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

private fun questionTypeLabel(type: QuestionType): String = when (type) {
    QuestionType.RECALL -> "词义回忆"
    QuestionType.SPELLING -> "拼写"
    QuestionType.CHOICE -> "选择题"
}




