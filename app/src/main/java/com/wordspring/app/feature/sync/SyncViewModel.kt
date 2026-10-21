package com.wordspring.app.feature.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordspring.app.sync.SyncRepository
import com.wordspring.app.sync.SyncUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepository: SyncRepository
) : ViewModel() {

    val state: StateFlow<SyncUiState> = syncRepository.state

    var email by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    fun onEmailChange(value: String) {
        email = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun login() {
        viewModelScope.launch { syncRepository.login(email, password) }
    }

    fun register() {
        viewModelScope.launch { syncRepository.register(email, password) }
    }

    fun syncNow() {
        viewModelScope.launch { syncRepository.syncNow() }
    }

    fun logout() {
        viewModelScope.launch { syncRepository.logout() }
    }

    fun refreshPending() {
        viewModelScope.launch { syncRepository.refreshPending() }
    }
}
