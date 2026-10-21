package com.wordspring.app.sync

import androidx.room.withTransaction
import com.wordspring.app.data.TimeUtil
import com.wordspring.app.data.db.BookEntryDao
import com.wordspring.app.data.db.ReviewCardDao
import com.wordspring.app.data.db.ReviewEventDao
import com.wordspring.app.data.db.WordSpringDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val api: WordSpringApi,
    private val store: SyncStore,
    private val db: WordSpringDatabase,
    private val reviewEventDao: ReviewEventDao,
    private val reviewCardDao: ReviewCardDao,
    private val bookEntryDao: BookEntryDao
) : SyncRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SyncUiState())
    override val state: StateFlow<SyncUiState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(store.token, store.email, store.lastSyncAt) { token, email, lastSync ->
                SyncUiState(
                    signedIn = token != null,
                    email = email,
                    lastSyncAt = lastSync
                )
            }.collect { session ->
                _state.update {
                    it.copy(
                        signedIn = session.signedIn,
                        email = session.email,
                        lastSyncAt = session.lastSyncAt
                    )
                }
            }
        }
        scope.launch { refreshPending() }
    }

    override suspend fun register(email: String, password: String): Result<Unit> =
        authenticate(email, password) { api.register(RegisterRequest(email.trim(), password)) }

    override suspend fun login(email: String, password: String): Result<Unit> =
        authenticate(email, password) { api.login(LoginRequest(email.trim(), password)) }

    override suspend fun logout() {
        store.clearSession()
        _state.value = SyncUiState()
    }

    override suspend fun refreshPending() {
        val pending = reviewEventDao.countUnsynced()
        _state.update { it.copy(pendingEvents = pending) }
    }

    override suspend fun syncNow(): Result<Int> = withContext(Dispatchers.IO) {
        val token = store.currentToken()
            ?: return@withContext Result.failure(IllegalStateException("请先登录同步账号"))

        _state.update { it.copy(syncing = true, message = null) }
        try {
            val pushed = pushPending(token)
            val pulled = pullChanges(token)
            store.markSynced(System.currentTimeMillis())
            val pending = reviewEventDao.countUnsynced()
            _state.update {
                it.copy(
                    syncing = false,
                    pendingEvents = pending,
                    lastSyncAt = System.currentTimeMillis(),
                    message = "同步完成：上传 $pushed，拉取 $pulled"
                )
            }
            Result.success(pushed + pulled)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            _state.update {
                it.copy(syncing = false, message = "同步失败：${t.message ?: "网络异常"}")
            }
            Result.failure(t)
        }
    }

    private suspend fun authenticate(
        email: String,
        password: String,
        call: suspend () -> TokenResponse
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = call()
            store.saveSession(response.token, response.email)
            _state.update { it.copy(message = "已登录 ${response.email}") }
            refreshPending()
            Result.success(Unit)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            _state.update { it.copy(message = "登录失败：${t.message ?: "网络异常"}") }
            Result.failure(t)
        }
    }

    /**
     * Uploads unsynced answers together with the resulting card snapshot. The server dedupes
     * by answerId, so a retried batch is harmless.
     */
    private suspend fun pushPending(token: String): Int {
        val events = reviewEventDao.getUnsynced(MAX_BATCH)
        if (events.isEmpty()) return 0

        val payload = ArrayList<PushEvent>(events.size)
        for (event in events) {
            val card = reviewCardDao.getById(event.cardId) ?: continue
            val entry = bookEntryDao.getById(card.bookEntryId) ?: continue
            payload += PushEvent(
                answerId = event.answerId,
                cardUid = card.bookEntryId.toString(),
                bookUid = entry.bookId.toString(),
                grade = event.grade,
                questionType = event.questionType,
                answeredAt = event.answeredAt,
                localDate = TimeUtil.localDateString(event.answeredAt),
                durationMs = event.durationMs,
                card = CardSnapshot(
                    cardUid = card.bookEntryId.toString(),
                    bookUid = entry.bookId.toString(),
                    stage = card.stage,
                    dueAt = card.dueAt,
                    intervalDays = card.intervalDays,
                    ease = card.ease,
                    step = card.step,
                    version = card.version
                )
            )
        }
        if (payload.isEmpty()) return 0

        val response = api.push("Bearer $token", PushRequest(store.deviceId(), payload))
        reviewEventDao.markSynced(events.map { it.answerId })
        response.conflicts.forEach { applyServerCard(it.serverCard) }
        return response.accepted
    }

    private suspend fun pullChanges(token: String): Int {
        val since = store.cursor()
        val response = api.pull("Bearer $token", since)
        if (response.cards.isNotEmpty()) {
            db.withTransaction {
                response.cards.forEach { applyServerCard(it) }
            }
        }
        store.setCursor(maxOf(response.cursor, since))
        return response.cards.size
    }

    private suspend fun applyServerCard(snapshot: CardSnapshot) {
        val bookEntryId = snapshot.cardUid.toLongOrNull() ?: return
        reviewCardDao.applyServerCard(
            bookEntryId = bookEntryId,
            stage = snapshot.stage,
            dueAt = snapshot.dueAt,
            intervalDays = snapshot.intervalDays,
            ease = snapshot.ease,
            step = snapshot.step,
            updatedAt = System.currentTimeMillis(),
            version = snapshot.version
        )
    }

    private companion object {
        const val MAX_BATCH = 100
    }
}
