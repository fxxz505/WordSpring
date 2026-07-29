package com.wordspring.app.feature.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.prefs.SettingsRepository
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.data.repository.SessionRepository
import com.wordspring.app.domain.model.Definition
import com.wordspring.app.domain.model.Grade
import com.wordspring.app.domain.model.QuestionType
import com.wordspring.app.domain.model.ReviewCard
import com.wordspring.app.domain.model.SessionCardRef
import com.wordspring.app.domain.model.StudySession
import com.wordspring.app.domain.model.Word
import com.wordspring.app.domain.session.AnswerChecker
import com.wordspring.app.domain.tts.TtsController
import com.wordspring.app.domain.usecase.SubmitAnswerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Card finite-state machine per spec. */
enum class CardState {
    QUESTION,        // showing prompt; awaiting user action
    ANSWER_REVEALED, // RECALL only 鈥?answer shown, awaiting self-grade
    SUBMITTING,      // commit in flight, original content preserved
    FEEDBACK,        // correct answer + grade shown; "缁х画" available
    COMPLETE,        // transitioning to next card or summary
    ERROR            // load or save failed; retry available
}

data class CardUi(
    val cardId: Long,
    val bookEntryId: Long,
    val wordId: Long,
    val questionType: QuestionType,
    val word: Word?,
    val definitions: List<Definition>,
    val card: ReviewCard?
) {
    val primaryDefinition: Definition? get() = definitions.firstOrNull()
}

sealed interface StudyUiState {
    data object Loading : StudyUiState
    data object NotFound : StudyUiState
    data class Active(
        val session: StudySession,
        val index: Int,
        val total: Int,
        val etaMinutes: Int,
        val card: CardUi,
        val cardState: CardState,
        val lastGrade: Grade? = null,
        val lastIsCorrect: Boolean? = null,
        val spellingUserInput: String = "",
        val spellingResult: AnswerChecker.SpellingResult? = null,
        val choiceSelectedIndex: Int? = null,
        val choiceOptions: List<ChoiceOption> = emptyList(),
        val againStreak: Int = 0,
        val showSaving: Boolean = false,
        val errorMessage: String? = null,
        val ttsAvailable: Boolean = true,
        val advancedGrading: Boolean = false,
        // Cards answered AGAIN/HARD in the current round; appended as a new
        // round when the current queue ends so the user re-answers them.
        val wrongCards: List<SessionCardRef> = emptyList()
    ) : StudyUiState
    data class Completed(val session: StudySession) : StudyUiState
    data class Failed(val message: String) : StudyUiState
}

data class ChoiceOption(
    val text: String,
    val isCorrect: Boolean,
    val definition: Definition?
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val contentRepository: ContentRepository,
    private val reviewRepository: ReviewRepository,
    private val submitAnswerUseCase: SubmitAnswerUseCase,
    private val settingsRepository: SettingsRepository,
    private val ttsController: TtsController
) : ViewModel() {

    private val sessionId: String = savedStateHandle["sessionId"] ?: ""

    private val _state = MutableStateFlow<StudyUiState>(StudyUiState.Loading)
    val state: StateFlow<StudyUiState> = _state.asStateFlow()
    val ttsSpeaking: StateFlow<Boolean> = ttsController.speaking

    init {
        ttsController.ensureInitialized()
        viewModelScope.launch {
            ttsController.available.collect { available ->
                val current = _state.value as? StudyUiState.Active ?: return@collect
                if (current.ttsAvailable != available) _state.value = current.copy(ttsAvailable = available)
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val session = sessionRepository.getSession(sessionId)
            if (session == null) {
                _state.value = StudyUiState.NotFound
                return@launch
            }
            if (session.currentIndex >= session.queueSnapshot.size) {
                _state.value = StudyUiState.Completed(session)
                return@launch
            }
            val ttsAvailable = ttsController.available.value
            val active = buildActive(session, session.currentIndex, ttsAvailable)
            _state.value = active
        }
    }

    private suspend fun buildActive(session: StudySession, index: Int, ttsAvailable: Boolean): StudyUiState.Active {
        val ref = session.queueSnapshot[index]
        val word = contentRepository.getWord(ref.wordId)
        val definitions = contentRepository.getDefinitions(ref.wordId)
        val card = contentRepository.getOrCreateReviewCard(ref.bookEntryId, TimeUtil.nowMillis())
        val options = if (ref.questionType == QuestionType.CHOICE) {
            buildChoiceOptions(definitions)
        } else emptyList()
        val progress = studyGroupProgress(session.completedCount, session.queueSnapshot)
        val total = progress.total
        val etaMinutes = ((total - progress.current) * 10 + 59) / 60 // ~10s per card, rounded up
        val advanced = settingsRepository.advancedGrading.first()
        return StudyUiState.Active(
            session = session,
            index = index,
            total = total,
            etaMinutes = etaMinutes.coerceAtLeast(1),
            card = CardUi(ref.cardId, ref.bookEntryId, ref.wordId, ref.questionType, word, definitions, card),
            cardState = CardState.QUESTION,
            ttsAvailable = ttsAvailable,
            choiceOptions = options,
            advancedGrading = advanced
        )
    }

    private fun buildChoiceOptions(correctDefinitions: List<Definition>): List<ChoiceOption> {
        val correct = correctDefinitions.firstOrNull() ?: return emptyList()
        // We need 3 distractors. Pull from the same book 鈥?but we don't have them in scope here
        // without an extra query. v1: reuse other definitions of the same word as distractors;
        // if not enough, repeat the correct option text in a shuffled form so the user still
        // has a meaningful 4-way choice. This is intentionally simple.
        val distractors = correctDefinitions.drop(1).take(3).map { ChoiceOption(it.meaningZh, false, it) }
        val correctOption = ChoiceOption(correct.meaningZh, true, correct)
        val combined = (distractors + correctOption).shuffled()
        // Ensure we always return 4 options. If we have fewer distractors, pad with variants
        // of the correct option to keep the layout consistent.
        if (combined.size < 4) {
            val padded = combined.toMutableList()
            while (padded.size < 4) {
                padded.add(ChoiceOption("No distractor", false, null))
            }
            return padded
        }
        return combined
    }

    // ---- User actions (intent-style) ----

    /** RECALL: user pressed "show answer". */
    fun revealAnswer() {
        val current = _state.value as? StudyUiState.Active ?: return
        if (current.cardState != CardState.QUESTION) return
        _state.value = current.copy(cardState = CardState.ANSWER_REVEALED)
    }

    /** RECALL self-grade or any flow that produces a Grade directly. */
    fun selfGrade(grade: Grade) {
        val current = _state.value as? StudyUiState.Active ?: return
        if (current.cardState != CardState.ANSWER_REVEALED && current.cardState != CardState.FEEDBACK) return
        submitGrade(grade, current)
    }

    /** SPELLING: user pressed submit. */
    fun submitSpelling() {
        val current = _state.value as? StudyUiState.Active ?: return
        if (current.cardState != CardState.QUESTION) return
        val word = current.card.word ?: return
        val result = AnswerChecker.checkSpelling(current.spellingUserInput, word.spelling)
        val grade = AnswerChecker.gradeFromSpelling(result)
        _state.value = current.copy(
            spellingResult = result,
            cardState = CardState.SUBMITTING
        )
        submitGrade(grade, current, spellingResultOverride = result)
    }

    fun updateSpellingInput(value: String) {
        val current = _state.value as? StudyUiState.Active ?: return
        _state.value = current.copy(spellingUserInput = value)
    }

    /** CHOICE: user picked an option index. */
    fun selectChoice(index: Int) {
        val current = _state.value as? StudyUiState.Active ?: return
        if (current.cardState != CardState.QUESTION) return
        val options = current.choiceOptions
        if (index !in options.indices) return
        val selected = options[index]
        val grade = AnswerChecker.gradeFromChoice(selected.isCorrect)
        _state.value = current.copy(
            choiceSelectedIndex = index,
            cardState = CardState.SUBMITTING
        )
        submitGrade(grade, current)
    }

    fun pronounceCurrentWord() {
        val current = _state.value as? StudyUiState.Active ?: return
        val word = current.card.word ?: return
        viewModelScope.launch {
            val speed = settingsRepository.ttsSpeed.first()
            ttsController.setSpeed(speed)
            ttsController.speak(word.spelling)
        }
    }

    fun installTtsData() = ttsController.installTtsData()

    override fun onCleared() {
        ttsController.stop()
        super.onCleared()
    }

    /** FEEDBACK 鈫?next card. */
    fun continueToNext() {
        val current = _state.value as? StudyUiState.Active ?: return
        if (current.cardState != CardState.FEEDBACK && current.cardState != CardState.ERROR) return
        val nextIndex = current.index + 1
        if (nextIndex >= current.session.queueSnapshot.size) {
            // Reached the end of the current queue. If there are pending wrong
            // cards (requeued from AGAIN/HARD in this session), keep going; if
            // not, complete the session.
            viewModelScope.launch {
                val fresh = sessionRepository.getSession(current.session.id) ?: current.session
                if (fresh.queueSnapshot.size > nextIndex) {
                    // A requeue happened (shouldn't normally with new logic, but
                    // keep for safety). Advance to the new card.
                    val active = buildActive(fresh, nextIndex, ttsController.available.value)
                    _state.value = active
                } else if (fresh.currentIndex < fresh.queueSnapshot.size) {
                    // Cursor hasn't reached the end yet (race); follow cursor.
                    val active = buildActive(fresh, fresh.currentIndex, ttsController.available.value)
                    _state.value = active
                } else {
                    sessionRepository.completeSession(current.session.id, TimeUtil.nowMillis())
                    _state.value = StudyUiState.Completed(current.session)
                }
            }
            return
        }
        viewModelScope.launch {
            val active = buildActive(current.session, nextIndex, ttsController.available.value)
            _state.value = active
        }
    }

    fun retry() {
        val current = _state.value as? StudyUiState.Active ?: return
        _state.value = current.copy(cardState = CardState.QUESTION, errorMessage = null)
    }

    private fun submitGrade(
        grade: Grade,
        current: StudyUiState.Active,
        spellingResultOverride: AnswerChecker.SpellingResult? = null
    ) {
        _state.value = current.copy(cardState = CardState.SUBMITTING, showSaving = false)
        viewModelScope.launch {
            // Spec: show "姝ｅ湪淇濆瓨" only if save takes > 500ms
            val savingShown = async {
                delay(500)
                val s = _state.value as? StudyUiState.Active
                if (s != null && s.cardState == CardState.SUBMITTING) {
                    _state.value = s.copy(showSaving = true)
                }
            }
            val started = TimeUtil.nowMillis()
            val result = submitAnswerUseCase(
                cardId = current.card.cardId,
                bookEntryId = current.card.bookEntryId,
                grade = grade,
                questionType = current.card.questionType,
                durationMs = 0L,
                sessionId = current.session.id,
                now = TimeUtil.nowMillis()
            )
            savingShown.cancel()
            val elapsed = TimeUtil.nowMillis() - started
            val isCorrect = grade != Grade.AGAIN
            val newAgainStreak = if (grade == Grade.AGAIN) current.againStreak + 1 else 0

            when (result) {
                is com.wordspring.app.data.repository.AnswerResult.AlreadyCommitted -> {
                    val updatedSession = sessionRepository.getSession(current.session.id) ?: current.session
                    advanceAfterSaved(current, updatedSession, current.wrongCards)
                }
                is com.wordspring.app.data.repository.AnswerResult.Committed -> {
                    settingsRepository.recordReviewDay(TimeUtil.todayLocalDate())
                    val isAgainOrHard = grade == Grade.AGAIN || grade == Grade.HARD
                    // Reload session (cursor advanced by submitAnswer; completedCount
                    // only incremented on GOOD/EASY).
                    var updatedSession = sessionRepository.getSession(current.session.id) ?: current.session

                    // Track wrong cards within the current round for end-of-round requeue.
                    val newWrongCards = if (isAgainOrHard) {
                        current.wrongCards + SessionCardRef(
                            cardId = current.card.cardId,
                            bookEntryId = current.card.bookEntryId,
                            wordId = current.card.wordId,
                            questionType = current.card.questionType
                        )
                    } else {
                        current.wrongCards
                    }

                    // If we just answered the last card of the current queue and
                    // there are pending wrong cards, append them as a new round so
                    // the user must re-answer them correctly. (涓嶈儗鍗曡瘝 style)
                    // Reset wrongCards for the new round.
                    val (finalSession, finalWrongCards) = if (
                        updatedSession.currentIndex >= updatedSession.queueSnapshot.size &&
                        newWrongCards.isNotEmpty()
                    ) {
                        for (ref in newWrongCards) {
                            sessionRepository.requeueCard(updatedSession.id, ref)
                        }
                        val s = sessionRepository.getSession(updatedSession.id) ?: updatedSession
                        s to emptyList()
                    } else {
                        updatedSession to newWrongCards
                    }

                    advanceAfterSaved(current, finalSession, finalWrongCards)
                }
                is com.wordspring.app.data.repository.AnswerResult.Failed -> {
                    // Spec: keep card & user input; prompt retry. Don't switch to next card.
                    _state.value = current.copy(
                        cardState = CardState.ERROR,
                        errorMessage = "保存失败，请重试",
                        showSaving = false,
                        // Restore to a state where the user can retry the submit.
                        // We keep choiceSelectedIndex and spellingUserInput intact.
                        spellingResult = spellingResultOverride ?: current.spellingResult
                    )
                }
            }
        }
    }

    private suspend fun advanceAfterSaved(
        current: StudyUiState.Active,
        updatedSession: StudySession,
        wrongCards: List<SessionCardRef>
    ) {
        delay(120)
        if (updatedSession.currentIndex >= updatedSession.queueSnapshot.size) {
            sessionRepository.completeSession(current.session.id, TimeUtil.nowMillis())
            _state.value = StudyUiState.Completed(updatedSession)
        } else {
            _state.value = buildActive(
                session = updatedSession,
                index = updatedSession.currentIndex,
                ttsAvailable = ttsController.available.value
            ).copy(wrongCards = wrongCards)
        }
    }

    /** User asked to exit. Spec: keep going or save-and-quit. Cursor already advanced by submit. */
    fun abandon() {
        viewModelScope.launch {
            sessionRepository.abandonSession(sessionId, TimeUtil.nowMillis())
        }
    }
}

