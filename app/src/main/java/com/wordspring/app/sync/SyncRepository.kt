package com.wordspring.app.sync

import kotlinx.coroutines.flow.StateFlow

/** Snapshot of the cloud-sync session for the UI. */
data class SyncUiState(
    val signedIn: Boolean = false,
    val email: String? = null,
    val lastSyncAt: Long? = null,
    val pendingEvents: Int = 0,
    val syncing: Boolean = false,
    val message: String? = null
)

/**
 * Optional cloud sync. The app never depends on it: without a session every feature
 * keeps working against the local Room database.
 */
interface SyncRepository {

    val state: StateFlow<SyncUiState>

    suspend fun register(email: String, password: String): Result<Unit>

    suspend fun login(email: String, password: String): Result<Unit>

    suspend fun logout()

    /** Pushes pending answers, then pulls remote card changes. Returns touched rows. */
    suspend fun syncNow(): Result<Int>

    suspend fun refreshPending()
}
