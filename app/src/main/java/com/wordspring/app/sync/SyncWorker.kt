package com.wordspring.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background sync. Only runs when a session exists — [SyncRepository.syncNow] fails fast
 * otherwise, which keeps the app fully offline until the user opts in.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > 3) return Result.failure()
        return if (syncRepository.syncNow().isSuccess) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "cloud-sync"
    }
}
