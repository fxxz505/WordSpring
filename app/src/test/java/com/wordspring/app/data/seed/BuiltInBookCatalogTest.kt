package com.wordspring.app.data.seed

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuiltInBookCatalogTest {

    @Test
    fun `catalog covers every common exam level`() {
        assertThat(BuiltInBookCatalog.books.map { it.examTag })
            .containsExactly("zk", "gk", "cet4", "cet6", "ky", "ielts", "toefl", "gre").inOrder()
    }

    @Test
    fun `names and folders are unique`() {
        assertThat(BuiltInBookCatalog.books.map { it.name }.toSet()).hasSize(BuiltInBookCatalog.books.size)
        assertThat(BuiltInBookCatalog.books.map { it.assetFolder }.toSet()).hasSize(BuiltInBookCatalog.books.size)
    }

    @Test
    fun `exactly one default book and it is CET-4`() {
        assertThat(BuiltInBookCatalog.books.count { it.enabledByDefault }).isEqualTo(1)
        assertThat(BuiltInBookCatalog.defaultBook.examTag).isEqualTo("cet4")
    }

    @Test
    fun `old english book names resolve to the new books`() {
        assertThat(BuiltInBookCatalog.byName("CET-4 Complete Vocabulary")?.assetFolder).isEqualTo("cet4")
        assertThat(BuiltInBookCatalog.byName("CET-6 Complete Vocabulary")?.assetFolder).isEqualTo("cet6")
        assertThat(BuiltInBookCatalog.byName("Gaokao Complete Vocabulary")?.assetFolder).isEqualTo("gaokao")
        assertThat(BuiltInBookCatalog.byName("大学英语四级词汇")?.assetFolder).isEqualTo("cet4")
        assertThat(BuiltInBookCatalog.byName("unknown")).isNull()
    }
}
