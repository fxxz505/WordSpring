package com.wordspring.app.data.seed

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuiltInBookCatalogTest {

    @Test
    fun `exam book catalog includes the requested study levels`() {
        val names = BuiltInBookCatalog.books.map { it.name }

        assertThat(names).containsAtLeast(
            "WordSpring Complete 4500",
            "CET-4 Complete Vocabulary",
            "CET-6 Complete Vocabulary",
            "Gaokao Complete Vocabulary"
        )
    }

    @Test
    fun `each built-in word book requires more than four thousand words`() {
        assertThat(BuiltInBookCatalog.books.map { it.minimumWordCount })
            .containsExactly(4500, 4500, 4500, 4500)
    }
}

