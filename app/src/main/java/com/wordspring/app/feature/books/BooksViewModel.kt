package com.wordspring.app.feature.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.domain.model.WordBook
import com.wordspring.app.domain.model.BookLearningProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BooksUiState(
    val isLoading: Boolean = true,
    val books: List<WordBook> = emptyList(),
    val progressByBookId: Map<Long, BookLearningProgress> = emptyMap(),
    val errorMessage: String? = null
)

@HiltViewModel
class BooksViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    val state: StateFlow<BooksUiState> = contentRepository.observeBooks()
        .map { books ->
            val progress = contentRepository.getBookLearningProgress().associateBy { it.bookId }
            BooksUiState(isLoading = false, books = books, progressByBookId = progress)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BooksUiState())

    fun toggleEnabled(bookId: Long, enabled: Boolean) {
        viewModelScope.launch { contentRepository.setBookEnabled(bookId, enabled) }
    }

    fun hideBook(bookId: Long) {
        viewModelScope.launch { contentRepository.hideBook(bookId) }
    }

    fun createCustomBook(name: String, dailyNewLimit: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = contentRepository.createCustomBook(name, dailyNewLimit)
            onCreated(id)
        }
    }
}
