package com.promptsaz.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.promptsaz.app.ui.screens.archive.ArchiveScreen
import com.promptsaz.app.ui.screens.clarify.ClarifyScreen
import com.promptsaz.app.ui.screens.home.HomeScreen
import com.promptsaz.app.ui.screens.kb.KbBrowserScreen
import com.promptsaz.app.ui.screens.kb.KbDomainScreen
import com.promptsaz.app.ui.screens.result.ResultScreen
import com.promptsaz.app.ui.screens.detail.PromptDetailScreen
import com.promptsaz.app.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToClarify = { navController.navigate(Routes.CLARIFY) },
                onNavigateToArchive = { navController.navigate(Routes.ARCHIVE) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToKb = { navController.navigate(Routes.KB) },
            )
        }
        composable(Routes.CLARIFY) {
            ClarifyScreen(
                onNavigateToResult = { navController.navigate(Routes.RESULT) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                onBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        composable(Routes.ARCHIVE) {
            ArchiveScreen(
                onBack = { navController.popBackStack() },
                onOpenPrompt = { id -> navController.navigate(Routes.promptDetail(id)) },
            )
        }
        composable(
            route = Routes.PROMPT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            PromptDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.KB) {
            KbBrowserScreen(
                onBack = { navController.popBackStack() },
                onOpenDomain = { domainId -> navController.navigate(Routes.kbDomain(domainId)) },
            )
        }
        composable(
            route = Routes.KB_DOMAIN,
            arguments = listOf(navArgument("domainId") { type = NavType.StringType }),
        ) {
            KbDomainScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
