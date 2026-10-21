package com.wordspring.app.sync

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class TokenResponse(val token: String, val userId: Long, val email: String)

@Serializable
data class PushRequest(val deviceId: String, val events: List<PushEvent>)

@Serializable
data class PushEvent(
    val answerId: String,
    val cardUid: String,
    val bookUid: String,
    val grade: String,
    val questionType: String,
    val answeredAt: Long,
    val localDate: String,
    val durationMs: Long,
    val card: CardSnapshot
)

@Serializable
data class CardSnapshot(
    val cardUid: String,
    val bookUid: String,
    val stage: String,
    val dueAt: Long,
    val intervalDays: Double,
    val ease: Double,
    val step: Int,
    val version: Long,
    val deleted: Boolean = false
)

@Serializable
data class Conflict(val cardUid: String, val serverCard: CardSnapshot)

@Serializable
data class PushResponse(
    val accepted: Int,
    val duplicates: Int,
    val conflicts: List<Conflict> = emptyList(),
    val serverTime: Long
)

@Serializable
data class PullResponse(val cards: List<CardSnapshot> = emptyList(), val cursor: Long)
