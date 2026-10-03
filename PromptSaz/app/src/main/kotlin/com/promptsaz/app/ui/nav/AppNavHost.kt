package com.promptsaz.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.promptsaz.app.ui.screens.archive.ArchiveScreen
import com.promptsaz.app.ui.screens.chat.ChatScreen
import com.promptsaz.app.ui.screens.clarify.ClarifyScreen
import com.promptsaz.app.ui.screens.home.HomeScreen
import com.promptsaz.app.ui.screens.image.ImageStudioScreen
import com.promptsaz.app.ui.screens.kb.KbBrowserScreen
import com.promptsaz.app.ui.screens.kb.KbDomainScreen
import com.promptsaz.app.ui.screens.result.ResultScreen
import com.promptsaz.app.ui.screens.detail.PromptDetailScreen
import com.promptsaz.app.ui.screens.settings.SettingsScreen

private data class TabItem(
    val route: String,
    val labelFa: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * Three-mode app (ChatGPT-like): گفتگو / تولید پرامپت / تولید تصویر as
 * bottom-bar tabs; every other screen pushes on top without the bar.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val tabs = listOf(
        TabItem(Routes.CHAT, "گفتگو", Icons.Outlined.Forum, Icons.Rounded.Forum),
        TabItem(Routes.HOME, "تولید پرامپت", Icons.Outlined.EditNote, Icons.Rounded.EditNote),
        TabItem(Routes.IMAGE, "تولید تصویر", Icons.Outlined.Image, Icons.Rounded.Image),
    )
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.labelFa,
                                )
                            },
                            label = { Text(tab.labelFa) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CHAT,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CHAT) {
                ChatScreen(
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToClarify = { navController.navigate(Routes.CLARIFY) },
                    onNavigateToArchive = { navController.navigate(Routes.ARCHIVE) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToKb = { navController.navigate(Routes.KB) },
                )
            }
            composable(Routes.IMAGE) {
                ImageStudioScreen(
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
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
}
