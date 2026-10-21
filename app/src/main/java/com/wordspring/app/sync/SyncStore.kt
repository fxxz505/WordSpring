package com.wordspring.app.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync")

/**
 * Persists the sync session. Kept separate from [com.wordspring.app.data.prefs.SettingsRepository]
 * so that "logout" can wipe account state without touching learning settings.
 */
@Singleton
class SyncStore @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val EMAIL = stringPreferencesKey("email")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
        val CURSOR = longPreferencesKey("pull_cursor")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    private val prefs = context.syncDataStore.data

    val token: Flow<String?> = prefs.map { it[Keys.TOKEN] }
    val email: Flow<String?> = prefs.map { it[Keys.EMAIL] }
    val lastSyncAt: Flow<Long?> = prefs.map { it[Keys.LAST_SYNC] }

    suspend fun currentToken(): String? = token.first()

    suspend fun saveSession(token: String, email: String) {
        context.syncDataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.EMAIL] = email
        }
    }

    suspend fun clearSession() {
        context.syncDataStore.edit {
            it.remove(Keys.TOKEN)
            it.remove(Keys.EMAIL)
            it.remove(Keys.LAST_SYNC)
            it.remove(Keys.CURSOR)
        }
    }

    suspend fun cursor(): Long = prefs.first()[Keys.CURSOR] ?: 0L

    suspend fun setCursor(value: Long) {
        context.syncDataStore.edit { it[Keys.CURSOR] = value }
    }

    suspend fun markSynced(now: Long) {
        context.syncDataStore.edit { it[Keys.LAST_SYNC] = now }
    }

    /** Stable per-install identifier so the server can attribute pushes to a device. */
    suspend fun deviceId(): String {
        val existing = prefs.first()[Keys.DEVICE_ID]
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        context.syncDataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }
}
