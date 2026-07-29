package com.wordspring.app.data

import java.text.Normalizer
import java.util.Locale

/**
 * Normalization helper for word deduplication within a book.
 *
 * Spec rule: "同一词书中按标准化 spelling 去重."
 * We use NFKC + lowercase + trim. This does NOT strip interior spaces, so "ice cream" and
 * "icecream" remain distinct words.
 */
object NormalizerUtil {

    fun normalize(spelling: String): String {
        if (spelling.isBlank()) return ""
        val trimmed = spelling.trim()
        val nfkcd = Normalizer.normalize(trimmed, Normalizer.Form.NFKC)
        return nfkcd.lowercase(Locale.ROOT)
    }
}
