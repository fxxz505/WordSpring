package com.wordspring.app.data.seed

/** Built-in offline books. Each collection contains more than 4,000 entries. */
object BuiltInBookCatalog {
    data class Book(
        val name: String,
        val assetFolder: String,
        val enabledByDefault: Boolean,
        val minimumWordCount: Int = 4500
    )

    val books = listOf(
        Book("WordSpring Complete 4500", "starter_1200", true),
        Book("CET-4 Complete Vocabulary", "cet4_core", false),
        Book("CET-6 Complete Vocabulary", "cet6_core", false),
        Book("Gaokao Complete Vocabulary", "gaokao_core", false)
    )
}

