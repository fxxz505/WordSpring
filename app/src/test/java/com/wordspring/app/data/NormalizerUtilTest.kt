package com.wordspring.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure unit tests for word normalization (M08 deduplication helper).
 */
class NormalizerUtilTest {

    @Test
    fun normalize_trimsWhitespace() {
        assertThat(NormalizerUtil.normalize("  apple  ")).isEqualTo("apple")
    }

    @Test
    fun normalize_lowercases() {
        assertThat(NormalizerUtil.normalize("APPLE")).isEqualTo("apple")
    }

    @Test
    fun normalize_mixedCase() {
        assertThat(NormalizerUtil.normalize("ApPlE")).isEqualTo("apple")
    }

    @Test
    fun normalize_emptyString_returnsEmpty() {
        assertThat(NormalizerUtil.normalize("")).isEqualTo("")
    }

    @Test
    fun normalize_blankString_returnsEmpty() {
        assertThat(NormalizerUtil.normalize("   ")).isEqualTo("")
    }

    @Test
    fun normalize_preservesInteriorSpaces() {
        // Spec rule: 不忽略短语内部空格
        assertThat(NormalizerUtil.normalize("ice cream")).isEqualTo("ice cream")
    }

    @Test
    fun normalize_appliesNFKC() {
        // Half-width vs full-width: NFKC should normalize ﬁ ligature and full-width chars.
        val result = NormalizerUtil.normalize("ＡＰＰＬＥ")
        assertThat(result).isEqualTo("apple")
    }

    @Test
    fun normalize_deduplicationWorks() {
        val a = NormalizerUtil.normalize("Apple")
        val b = NormalizerUtil.normalize("APPLE")
        val c = NormalizerUtil.normalize("apple")
        assertThat(a).isEqualTo(b)
        assertThat(b).isEqualTo(c)
    }
}
