package com.wordspring.app.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.repository.ContentRepository
import com.wordspring.app.domain.model.Word
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Word> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun updateQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        if (q.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), hasSearched = false)
            return
        }
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val results = contentRepository.searchWords(q, 50)
            _state.value = _state.value.copy(results = results, isLoading = false, hasSearched = true)
        }
    }
}
