package com.wordspring.app.feature.importflow

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.data.imp.CsvImporter
import com.wordspring.app.data.imp.CsvParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class ImportCsvUiState(
    val isLoading: Boolean = false,
    val preview: CsvParser.ParseResult? = null,
    val fileUri: Uri? = null,
    val fileContent: String = "",
    val isCommitting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ImportCsvViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val csvImporter: CsvImporter
) : ViewModel() {

    val bookId: Long = savedStateHandle["bookId"] ?: 0L

    private val _state = MutableStateFlow(ImportCsvUiState())
    val state: StateFlow<ImportCsvUiState> = _state.asStateFlow()

    fun parseFile(uri: Uri, content: String) {
        _state.value = _state.value.copy(isLoading = true, fileUri = uri, fileContent = content, errorMessage = null)
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) { CsvParser.parse(content) }
            _state.value = _state.value.copy(isLoading = false, preview = parsed)
        }
    }

    fun confirmImport() {
        val parsed = _state.value.preview ?: return
        val content = _state.value.fileContent
        _state.value = _state.value.copy(isCommitting = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val targetBookId = if (bookId > 0) bookId else null
                csvImporter.import(parsed, targetBookId, null)
                _state.value = _state.value.copy(
                    isCommitting = false,
                    successMessage = "成功导入 ${parsed.validCount} 条"
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.value = _state.value.copy(
                    isCommitting = false,
                    errorMessage = "导入失败：${t.message}"
                )
            }
        }
    }
}
