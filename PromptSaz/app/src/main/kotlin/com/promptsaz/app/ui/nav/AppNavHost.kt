package com.promptsaz.app.ui.nav

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.promptsaz.app.MainActivity
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
import kotlinx.coroutines.launch

/**
 * Navigation shell: the three sections live in the unified app MENU (drawer)
 * — گفتگو / تولید پرامپت / تولید تصویر / تنظیمات / درباره ما — each section
 * with its ۵ تاریخچهٔ اخیر one tap away. No bottom bar.
 */
@Composable
fun AppNavHost(
    openSection: String? = null,
    onSectionHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val menuViewModel: AppMenuViewModel = hiltViewModel()
    val conversations by menuViewModel.conversations.collectAsStateWithLifecycle()
    val prompts by menuViewModel.prompts.collectAsStateWithLifecycle()
    val generations by menuViewModel.generations.collectAsStateWithLifecycle()

    /** Opens a section tab, preserving each tab's state. */
    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Opens a section tab WITHOUT restoring state, so the deep-link args land. */
    fun openTabWithArgs(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    // home-screen widget / app-shortcut deep link («پرامپت جدید» و …)
    LaunchedEffect(openSection) {
        when (openSection) {
            MainActivity.SECTION_CHAT -> openTab(Routes.CHAT_TAB)
            MainActivity.SECTION_IMAGE -> openTab(Routes.IMAGE_TAB)
            MainActivity.SECTION_PROMPT_NEW -> navController.navigate(Routes.HOME) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = false // always a FRESH prompt screen
            }
        }
        if (openSection != null) onSectionHandled()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppMenuDrawer(
                conversations = conversations,
                prompts = prompts,
                generations = generations,
                currentRoute = currentRoute,
                viewModel = menuViewModel,
                onOpenChat = { openTab(Routes.CHAT_TAB) },
                onOpenConversation = { id -> openTabWithArgs(Routes.chat(id)) },
                onOpenPromptTab = { openTab(Routes.HOME) },
                onOpenPrompt = { id ->
                    navController.navigate(Routes.promptDetail(id)) {
                        launchSingleTop = true
                    }
                },
                onOpenArchive = { navController.navigate(Routes.ARCHIVE) { launchSingleTop = true } },
                onOpenKb = { navController.navigate(Routes.KB) { launchSingleTop = true } },
                onOpenImage = { openTab(Routes.IMAGE_TAB) },
                onOpenGeneration = { id -> openTabWithArgs(Routes.image(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenAbout = { navController.navigate(Routes.ABOUT) { launchSingleTop = true } },
                onDismiss = { closeDrawer() },
            )
        },
    ) {
        // RTL-aware motion: pushed screens slide in from the left edge,
        // section switches just cross-fade — hierarchy vs siblings.
        val sectionRoutes = setOf(Routes.CHAT, Routes.HOME, Routes.IMAGE)

        fun isSectionSwitch(initial: NavBackStackEntry?, target: NavBackStackEntry?): Boolean {
            val from = initial?.destination?.route
            val to = target?.destination?.route
            return from in sectionRoutes && to in sectionRoutes
        }

        NavHost(
            navController = navController,
            startDestination = Routes.CHAT,
            modifier = Modifier.fillMaxWidth(),
            enterTransition = {
                if (isSectionSwitch(initialState, targetState)) {
                    fadeIn(tween(220))
                } else {
                    slideInHorizontally(pageSpring()) { -it / 3 } + fadeIn(tween(340))
                }
            },
            exitTransition = {
                if (isSectionSwitch(initialState, targetState)) {
                    fadeOut(tween(160))
                } else {
                    slideOutHorizontally(pageSpring()) { it / 4 } + fadeOut(tween(340))
                }
            },
            popEnterTransition = {
                slideInHorizontally(pageSpring()) { it / 4 } + fadeIn(tween(340))
            },
            popExitTransition = {
                slideOutHorizontally(pageSpring()) { -it / 3 } + fadeOut(tween(340))
            },
        ) {
            composable(
                route = Routes.CHAT,
                arguments = listOf(
                    navArgument("conversationId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) {
                ChatScreen(
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToClarify = { navController.navigate(Routes.CLARIFY) },
                    onOpenMenu = { scope.launch { drawerState.open() } },
                )
            }
            composable(
                route = Routes.IMAGE,
                arguments = listOf(
                    navArgument("generationId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) {
                ImageStudioScreen(
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CLARIFY) {
                ClarifyScreen(
                    onNavigateToResult = {
                        // pop CLARIFY away: from RESULT, back must land on HOME
                        // (the old stack caused a back-key ping-pong)
                        navController.navigate(Routes.RESULT) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.RESULT) {
                val goHome: () -> Unit = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
                ResultScreen(
                    onBack = goHome,
                    onNavigateHome = goHome,
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

/** Soft page spring: a hint of overshoot (0.9 damping) — ظریف, not bouncy. */
private fun pageSpring() = spring<IntOffset>(
    dampingRatio = 0.9f,
    stiffness = Spring.StiffnessMediumLow,
)
