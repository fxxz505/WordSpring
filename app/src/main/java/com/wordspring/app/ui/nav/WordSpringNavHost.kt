package com.wordspring.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.wordspring.app.R
import com.wordspring.app.feature.books.BooksScreen
import com.wordspring.app.feature.books.bookdetail.BookDetailScreen
import com.wordspring.app.feature.home.HomeScreen
import com.wordspring.app.feature.importflow.ImportCsvScreen
import com.wordspring.app.feature.mistakes.MistakesScreen
import com.wordspring.app.feature.onboarding.OnboardingScreen
import com.wordspring.app.feature.search.SearchScreen
import com.wordspring.app.feature.settings.SettingsScreen
import com.wordspring.app.feature.sync.SyncScreen
import com.wordspring.app.feature.stats.StatsScreen
import com.wordspring.app.feature.study.StudyScreen
import com.wordspring.app.feature.study.summary.StudySummaryScreen
import com.wordspring.app.feature.word.WordDetailScreen

private data class BottomItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val BOTTOM_ITEMS = listOf(
    BottomItem(Routes.HOME, R.string.home_nav_home, Icons.Outlined.MenuBook),
    BottomItem(Routes.BOOKS, R.string.home_nav_books, Icons.Outlined.Book),
    BottomItem(Routes.SEARCH, R.string.home_nav_search, Icons.Outlined.Search),
    BottomItem(Routes.STATS, R.string.home_nav_stats, Icons.Outlined.BarChart)
)

private val IMMERSIVE_ROUTES = setOf(
    Routes.STUDY,
    Routes.STUDY_SUMMARY,
    Routes.ONBOARDING
)

internal fun isImmersiveRoute(route: String?): Boolean {
    if (route == null) return false
    return route == Routes.ONBOARDING ||
        route.startsWith("study/") ||
        route.startsWith("study-summary/")
}

/** Keeps detail-route content above the root Scaffold navigation bar. */
internal fun detailScreenContentPadding(outerPadding: PaddingValues): PaddingValues = outerPadding

internal enum class BottomNavigationAction { None, ReturnToHome, Navigate }

internal fun bottomNavigationAction(currentRoute: String?, targetRoute: String): BottomNavigationAction = when {
    currentRoute == targetRoute -> BottomNavigationAction.None
    targetRoute == Routes.HOME -> BottomNavigationAction.ReturnToHome
    else -> BottomNavigationAction.Navigate
}

@Composable
fun WordSpringNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = !isImmersiveRoute(currentRoute)

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                WordSpringBottomBar(navController, currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinish = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    contentPadding = innerPadding,
                    onStartStudy = { sessionId -> navController.navigate(Routes.study(sessionId)) },
                    onOpenBooks = { navController.navigate(Routes.BOOKS) },
                    onOpenMistakes = { navController.navigate(Routes.MISTAKES) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenWord = { wordId -> navController.navigate(Routes.word(wordId)) }
                )
            }
            composable(
                route = Routes.STUDY,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { entry ->
                val sessionId = entry.arguments?.getString("sessionId") ?: return@composable
                StudyScreen(
                    sessionId = sessionId,
                    onExit = { navController.popBackStack() },
                    onComplete = { navController.navigate(Routes.studySummary(sessionId)) {
                        popUpTo(Routes.study(sessionId)) { inclusive = true }
                    } },
                    onOpenWord = { wordId -> navController.navigate(Routes.word(wordId)) }
                )
            }
            composable(
                route = Routes.STUDY_SUMMARY,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { entry ->
                val sessionId = entry.arguments?.getString("sessionId") ?: return@composable
                StudySummaryScreen(
                    sessionId = sessionId,
                    onBackHome = {
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                    onReviewMistakes = {
                        navController.navigate(Routes.MISTAKES)
                    }
                )
            }
            composable(Routes.BOOKS) {
                BooksScreen(
                    contentPadding = innerPadding,
                    onOpenBook = { bookId -> navController.navigate(Routes.bookDetail(bookId)) },
                    onImportCsv = { bookId -> navController.navigate(Routes.importCsv(bookId)) },
                    onOpenWord = { wordId -> navController.navigate(Routes.word(wordId)) }
                )
            }
            composable(
                route = Routes.BOOK_DETAIL,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                BookDetailScreen(
                    bookId = bookId,
                    contentPadding = detailScreenContentPadding(innerPadding),
                    onBack = { navController.popBackStack() },
                    onOpenWord = { wordId -> navController.navigate(Routes.word(wordId)) },
                    onImportCsv = { navController.navigate(Routes.importCsv(bookId)) }
                )
            }
            composable(
                route = Routes.WORD,
                arguments = listOf(navArgument("wordId") { type = NavType.LongType })
            ) { entry ->
                val wordId = entry.arguments?.getLong("wordId") ?: return@composable
                WordDetailScreen(
                    wordId = wordId,
                    contentPadding = detailScreenContentPadding(innerPadding),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    contentPadding = innerPadding,
                    onOpenWord = { wordId -> navController.navigate(Routes.word(wordId)) }
                )
            }
            composable(Routes.MISTAKES) {
                MistakesScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onOpenWord = { wordId -> navController.navigate(Routes.word(wordId)) },
                    onRelearn = { sessionId -> navController.navigate(Routes.study(sessionId)) }
                )
            }
            composable(Routes.STATS) {
                StatsScreen(contentPadding = innerPadding)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onOpenSync = { navController.navigate(Routes.SYNC) }
                )
            }
            composable(Routes.SYNC) {
                SyncScreen(
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.IMPORT_CSV,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType })
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                ImportCsvScreen(
                    bookId = bookId,
                    contentPadding = detailScreenContentPadding(innerPadding),
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun WordSpringBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        BOTTOM_ITEMS.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    when (bottomNavigationAction(currentRoute, item.route)) {
                        BottomNavigationAction.None -> Unit
                        // Shortcut pages are placed above home, so return to that existing root.
                        BottomNavigationAction.ReturnToHome -> {
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
                        BottomNavigationAction.Navigate -> {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResourceSafe(item.labelRes)) },
                alwaysShowLabel = true,
                modifier = Modifier.semantics { contentDescription = "" }
            )
        }
    }
}

@Composable
private fun stringResourceSafe(resId: Int): String = androidx.compose.ui.res.stringResource(resId)
