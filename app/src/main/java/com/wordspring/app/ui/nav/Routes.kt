package com.wordspring.app.ui.nav

/**
 * All navigation routes. Routes use argument placeholders in the nav-compose style.
 * The study route is a top-level沉浸 route that hides the bottom bar.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"

    const val STUDY = "study/{sessionId}"
    const val STUDY_SUMMARY = "study-summary/{sessionId}"
    fun study(sessionId: String) = "study/$sessionId"
    fun studySummary(sessionId: String) = "study-summary/$sessionId"

    const val BOOKS = "books"
    const val BOOK_DETAIL = "book/{bookId}"
    fun bookDetail(bookId: Long) = "book/$bookId"

    const val WORD = "word/{wordId}"
    fun word(wordId: Long) = "word/$wordId"

    const val SEARCH = "search"
    const val MISTAKES = "mistakes"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val SYNC = "sync"

    const val IMPORT_CSV = "import-csv/{bookId}"
    fun importCsv(bookId: Long) = "import-csv/$bookId"
}
