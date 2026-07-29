package com.wordspring.app.feature.books.bookdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.data.repository.ReviewRepository
import com.wordspring.app.domain.model.BookEntry
import com.wordspring.app.domain.model.Definition
import com.wordspring.app.domain.model.Word
import com.wordspring.app.domain.model.WordBook
import com.wordspring.app.domain.model.BookLearningProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** A flat row representing one word inside a book, for the detail list. */
data class BookWordRow(
    val entry: BookEntry,
    val word: Word?,
    val definitions: List<Definition>
) {
    val primaryMeaning: String? get() = definitions.firstOrNull()?.meaningZh
}

data class BookDetailUiState(
    val isLoading: Boolean = true,
    val book: WordBook? = null,
    val entryCount: Int = 0,
    val progress: BookLearningProgress? = null,
    val studiedCount: Int = 0,
    val words: List<BookWordRow> = emptyList(),
    val wordsLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val bookId: Long = savedStateHandle["bookId"] ?: 0L

    private val _studiedCount = MutableStateFlow(0)
    val studiedCount: StateFlow<Int> = _studiedCount.asStateFlow()

    private val _words = MutableStateFlow<List<BookWordRow>>(emptyList())
    private val _wordsLoading = MutableStateFlow(false)
    private val _progress = MutableStateFlow<BookLearningProgress?>(null)

    val state: StateFlow<BookDetailUiState> = contentRepository.observeBook(bookId)
        .combine(contentRepository.observeBookProgress(bookId)) { book, entryCount ->
            BookDetailUiState(isLoading = false, book = book, entryCount = entryCount)
        }
        .combine(_words) { base, words ->
            base.copy(words = words, wordsLoading = _wordsLoading.value)
        }
        .combine(_wordsLoading) { base, loading ->
            base.copy(wordsLoading = loading)
        }
        .combine(_progress) { base, progress ->
            base.copy(progress = progress)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookDetailUiState())

    init {
        refreshStudied()
        loadWords()
        loadProgress()
    }

    fun refreshStudied() {
        viewModelScope.launch {
            // v1: count review_cards in LEARNING or REVIEW stage as "studied"
            _studiedCount.value = 0
        }
    }

    fun loadWords() {
        viewModelScope.launch {
            _wordsLoading.value = true
            // A complete built-in book has thousands of entries. Load a responsive preview
            // rather than issuing word and definition queries for the entire collection.
            val entries = contentRepository.getBookEntries(bookId).take(WORD_LIST_PREVIEW_COUNT)
            val rows = entries.map { entry ->
                val word = contentRepository.getWord(entry.wordId)
                val defs = contentRepository.getDefinitions(entry.wordId)
                BookWordRow(entry = entry, word = word, definitions = defs)
            }
            _words.value = rows
            _wordsLoading.value = false
        }
    }

    private fun loadProgress() {
        viewModelScope.launch {
            _progress.value = contentRepository.getBookLearningProgress()
                .firstOrNull { it.bookId == bookId }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { contentRepository.setBookEnabled(bookId, enabled) }
    }

    fun setDailyLimit(limit: Int) {
        viewModelScope.launch { contentRepository.setBookDailyLimit(bookId, limit) }
    }

    fun setCompletionDate(targetEpochDay: Int?) {
        viewModelScope.launch { contentRepository.setBookTargetEpochDay(bookId, targetEpochDay) }
    }

    fun recommendedDailyNewCount(): Int {
        val progress = state.value.progress ?: return 0
        val targetDay = state.value.book?.targetEpochDay ?: return progress.newCount
        val days = (targetDay - LocalDate.now().toEpochDay().toInt()).coerceAtLeast(1)
        return (progress.newCount + days - 1) / days
    }

    private companion object {
        const val WORD_LIST_PREVIEW_COUNT = 100
    }
}
