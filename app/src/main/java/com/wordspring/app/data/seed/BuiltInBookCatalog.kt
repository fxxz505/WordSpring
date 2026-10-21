package com.wordspring.app.data.seed

/**
 * Built-in offline books. Each book is exactly the ECDICT word list for one exam tag
 * (see tools/build_wordbooks.py); no padding words from other lists are mixed in.
 */
object BuiltInBookCatalog {
    data class Book(
        /** Folder under assets/books/ and stable identity of the book. */
        val assetFolder: String,
        /** Must equal metadata.json "name"; used to find the book in the database. */
        val name: String,
        /** ECDICT tag the list was generated from. */
        val examTag: String,
        /** Short label shown on chips and cards. */
        val shortLabel: String,
        val enabledByDefault: Boolean = false,
        /** Names used by earlier app versions, so existing installs upgrade in place. */
        val legacyNames: List<String> = emptyList()
    )

    val books = listOf(
        Book("zhongkao", "中考英语词汇", "zk", "中考"),
        Book("gaokao", "高考英语词汇", "gk", "高考", legacyNames = listOf("Gaokao Complete Vocabulary")),
        Book("cet4", "大学英语四级词汇", "cet4", "四级", enabledByDefault = true,
            legacyNames = listOf("CET-4 Complete Vocabulary")),
        Book("cet6", "大学英语六级词汇", "cet6", "六级", legacyNames = listOf("CET-6 Complete Vocabulary")),
        Book("kaoyan", "考研英语词汇", "ky", "考研"),
        Book("ielts", "雅思 IELTS 词汇", "ielts", "雅思"),
        Book("toefl", "托福 TOEFL 词汇", "toefl", "托福"),
        Book("gre", "GRE 词汇", "gre", "GRE")
    )

    /** Book new users start with. */
    val defaultBook: Book = books.first { it.enabledByDefault }

    /**
     * The old "WordSpring Complete 4500" / "Starter 1200" book was a copy of the CET-4 list plus
     * filler words. It is merged into [defaultBook] (keeping learning progress) and then removed.
     */
    val legacyStarterNames = listOf("WordSpring Complete 4500", "WordSpring Starter 1200")

    fun byName(name: String): Book? = books.firstOrNull { it.name == name || name in it.legacyNames }
}
