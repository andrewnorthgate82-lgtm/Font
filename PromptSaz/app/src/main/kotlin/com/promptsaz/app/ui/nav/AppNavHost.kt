package com.promptsaz.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.promptsaz.app.ui.screens.about.AboutScreen
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
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
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
        // RTL-aware motion: pushed screens slide in from the left edge,
        // tab switches just cross-fade — hierarchy vs siblings.
        val tabRoutes = setOf(Routes.CHAT, Routes.HOME, Routes.IMAGE)
        fun isTabSwitch(
            initial: androidx.navigation.NavBackStackEntry?,
            target: androidx.navigation.NavBackStackEntry?,
        ): Boolean {
            val from = initial?.destination?.route
            val to = target?.destination?.route
            return from in tabRoutes && to in tabRoutes
        }

        NavHost(
            navController = navController,
            startDestination = Routes.CHAT,
            modifier = Modifier.padding(padding),
            enterTransition = {
                if (isTabSwitch(initialState, targetState)) {
                    fadeIn(tween(220))
                } else {
                    slideInHorizontally(tween(340)) { -it / 3 } + fadeIn(tween(340))
                }
            },
            exitTransition = {
                if (isTabSwitch(initialState, targetState)) {
                    fadeOut(tween(160))
                } else {
                    slideOutHorizontally(tween(340)) { it / 4 } + fadeOut(tween(340))
                }
            },
            popEnterTransition = {
                slideInHorizontally(tween(340)) { it / 4 } + fadeIn(tween(340))
            },
            popExitTransition = {
                slideOutHorizontally(tween(340)) { -it / 3 } + fadeOut(tween(340))
            },
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
                    onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
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
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
