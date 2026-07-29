package com.wordspring.app.feature.word

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.domain.model.Definition
import com.wordspring.app.domain.model.ReviewCard
import com.wordspring.app.domain.model.Word
import com.wordspring.app.domain.model.WordBook
import com.wordspring.app.domain.tts.TtsController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordDetailUiState(
    val isLoading: Boolean = true,
    val word: Word? = null,
    val definitions: List<Definition> = emptyList(),
    val card: ReviewCard? = null,
    val books: List<WordBook> = emptyList(),
    val isFavorite: Boolean = false,
    val isDifficult: Boolean = false,
    val ttsAvailable: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
    private val reviewRepository: ReviewRepository,
    private val ttsController: TtsController,
    private val userWordFlagDao: com.wordspring.app.data.db.UserWordFlagDao
) : ViewModel() {

    private val wordId: Long = savedStateHandle["wordId"] ?: 0L

    private val _state = MutableStateFlow(WordDetailUiState())
    val state: StateFlow<WordDetailUiState> = _state.asStateFlow()

    init {
        ttsController.ensureInitialized()
        viewModelScope.launch {
            ttsController.available.collect { available ->
                _state.value = _state.value.copy(ttsAvailable = available)
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val word = contentRepository.getWord(wordId)
            val defs = contentRepository.getDefinitions(wordId)
            val flag = userWordFlagDao.getByWord(wordId)
            _state.value = WordDetailUiState(
                isLoading = false,
                word = word,
                definitions = defs,
                isFavorite = flag?.isFavorite == true,
                isDifficult = flag?.isDifficult == true,
                ttsAvailable = ttsController.available.value
            )
        }
    }

    fun pronounce() {
        val word = _state.value.word ?: return
        ttsController.speak(word.spelling)
    }

    fun installTtsData() = ttsController.installTtsData()

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = _state.value
            val now = TimeUtil.nowMillis()
            userWordFlagDao.upsert(
                com.wordspring.app.data.db.UserWordFlagEntity(
                    id = 0L,
                    wordId = wordId,
                    isFavorite = !current.isFavorite,
                    isDifficult = current.isDifficult,
                    updatedAt = now
                )
            )
            _state.value = current.copy(isFavorite = !current.isFavorite)
        }
    }

    fun toggleDifficult() {
        viewModelScope.launch {
            val current = _state.value
            val now = TimeUtil.nowMillis()
            userWordFlagDao.setDifficult(wordId, !current.isDifficult, now)
            _state.value = current.copy(isDifficult = !current.isDifficult)
        }
    }

    fun relearn() {
        viewModelScope.launch {
            val card = _state.value.card ?: return@launch
            reviewRepository.relearnCard(card.id, TimeUtil.nowMillis())
            load()
        }
    }
}


