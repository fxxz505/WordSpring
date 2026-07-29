package com.wordspring.app.domain.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Android system TextToSpeech. Spec rule:
 *  - TTS 涓嶅彲鐢ㄦ椂涓嶈兘浼涓哄凡鎾斁銆? *  - 涓嶈嚜鍔ㄦ挱鏀撅紝浠呯敱鐢ㄦ埛涓诲姩鐐瑰嚮瑙﹀彂銆? *  - TTS 涓嶅彲鐢ㄦ椂闅愯棌鍗＄墖涓婄殑鍙戦煶鎸夐挳鎴栨樉绀轰笉鍙敤鐘舵€侊紝涓嶅脊绐楁墦鏂€? */
@Singleton
class TtsController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private var engine: TextToSpeech? = null
    private var pendingSpeed: Float = 1.0f
    private var initialized = false

    fun installTtsData() {
        val intent = android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val e = engine
                val language = e?.let {
                    selectEnglishLanguage(
                        it.setLanguage(Locale.US).toTtsResult(),
                        it.setLanguage(Locale.UK).toTtsResult(),
                        it.setLanguage(Locale.ENGLISH).toTtsResult()
                    )
                }
                _available.value = language != null
                language?.let { e?.setLanguage(it.locale) }
            } else {
                _available.value = false
            }
        }
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) { _speaking.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { _speaking.value = false }
            override fun onError(utteranceId: String?, errorCode: Int) { _speaking.value = false }
        })
    }

    fun setSpeed(speed: Float) {
        pendingSpeed = speed.coerceIn(0.5f, 2.0f)
    }

    /**
     * Returns true if speech was actually dispatched. Returns false if TTS is unavailable
     * 鈥?caller MUST reflect this in UI (hide button / show unavailable state).
     */
    fun speak(text: String): Boolean {
        ensureInitialized()
        val e = engine ?: return false
        if (!_available.value) return false
        e.setSpeechRate(pendingSpeed)
        _speaking.value = false
        val id = UUID.randomUUID().toString()
        val result = e.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        engine?.stop()
        _speaking.value = false
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        initialized = false
        _available.value = false
        _speaking.value = false
    }
}
enum class EnglishLanguage(val locale: Locale) {
    US(Locale.US),
    UK(Locale.UK),
    GENERIC(Locale.ENGLISH)
}

enum class TtsResult { AVAILABLE, MISSING_DATA, NOT_SUPPORTED }

fun selectEnglishLanguage(
    usResult: TtsResult,
    ukResult: TtsResult,
    genericResult: TtsResult
): EnglishLanguage? = when {
    usResult == TtsResult.AVAILABLE -> EnglishLanguage.US
    ukResult == TtsResult.AVAILABLE -> EnglishLanguage.UK
    genericResult == TtsResult.AVAILABLE -> EnglishLanguage.GENERIC
    else -> null
}

private fun Int.toTtsResult(): TtsResult = when (this) {
    TextToSpeech.LANG_MISSING_DATA -> TtsResult.MISSING_DATA
    TextToSpeech.LANG_NOT_SUPPORTED -> TtsResult.NOT_SUPPORTED
    else -> TtsResult.AVAILABLE
}

