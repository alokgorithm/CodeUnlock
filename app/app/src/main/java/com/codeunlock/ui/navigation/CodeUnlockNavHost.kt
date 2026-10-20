package com.codeunlock.ui.navigation

import android.app.Activity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeunlock.service.FocusTimerService
import com.codeunlock.ui.MainActivity
import com.codeunlock.ui.screens.dashboard.DashboardRoute
import com.codeunlock.ui.screens.feature.BadgesRoute
import com.codeunlock.ui.screens.feature.ProfileRoute
import com.codeunlock.ui.screens.feature.RankingsRoute
import com.codeunlock.ui.screens.feature.SocialRoute
import com.codeunlock.ui.screens.feature.TracksRoute
import com.codeunlock.ui.screens.feature.WellbeingRoute
import com.codeunlock.ui.screens.lock.LockRoute
import com.codeunlock.ui.screens.login.LoginRoute
import com.codeunlock.ui.screens.permissions.PermissionSetupRoute
import com.codeunlock.ui.screens.problem.ProblemRoute
import com.codeunlock.ui.screens.focus.FocusTimerRoute
import com.codeunlock.ui.screens.sprint.FocusRoomRoute
import com.codeunlock.ui.screens.settings.SettingsRoute

// Routes that show the bottom navigation bar
private val bottomNavRoutes = setOf(
    AppRoute.Dashboard.route,
    AppRoute.Tracks.route,
    AppRoute.Badges.route,
    AppRoute.Profile.route,
)

@Composable
fun CodeUnlockNavHost(
    initialRoute: String? = null
) {
    val navViewModel: CodeUnlockNavViewModel = hiltViewModel()
    val sessionState by navViewModel.sessionState.collectAsStateWithLifecycle()
    val onboardingState by navViewModel.onboardingState.collectAsStateWithLifecycle()
    val authUiState by navViewModel.authUiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    // Offline-first: start at Permissions or Dashboard (never Login)
    val startDestination = navViewModel.resolveStartDestination(initialRoute)

    val isFocusRunning by FocusTimerService.isRunning.collectAsStateWithLifecycle()
    val pendingRoute by MainActivity.pendingRoute.collectAsStateWithLifecycle()

    // Handle pending route from notifications, overlay clicks, or focus resume
    LaunchedEffect(pendingRoute) {
        val target = pendingRoute
        if (!target.isNullOrBlank()) {
            MainActivity.pendingRoute.value = null
            if (backStackEntry?.destination?.route != target) {
                navController.navigate(target) {
                    launchSingleTop = true
                }
            }
        }
    }

    // Auto-open Focus Timer when returning to Dashboard if focus session is active
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(isFocusRunning, currentRoute) {
        if (isFocusRunning && currentRoute == AppRoute.Dashboard.route) {
            navController.navigate(AppRoute.Focus.route) {
                launchSingleTop = true
            }
        }
    }

    // Auto-redirect after login
    LaunchedEffect(sessionState.isAuthenticated, backStackEntry?.destination?.route) {
        val current = backStackEntry?.destination?.route
        if (current.isNullOrEmpty()) return@LaunchedEffect

        // After successful login from Login screen, go back to Dashboard
        if (sessionState.isAuthenticated && current == AppRoute.Login.route) {
            navController.navigate(AppRoute.Dashboard.route) {
                popUpTo(AppRoute.Login.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val showBottomNav = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo(AppRoute.Dashboard.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = if (showBottomNav) Modifier.padding(innerPadding) else Modifier
        ) {
            // Permissions setup — FIRST screen on fresh install (no login needed)
            composable(AppRoute.Permissions.route) {
                PermissionSetupRoute(
                    onContinue = {
                        navViewModel.markOnboardingComplete()
                        navController.navigate(AppRoute.Dashboard.route) {
                            popUpTo(AppRoute.Permissions.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Dashboard — main screen (works offline without login)
            composable(AppRoute.Dashboard.route) {
                DashboardRoute(
                    onOpenProblem = { problemId ->
                        navController.navigate(AppRoute.Problem.createRoute(problemId))
                    },
                    onOpenTracks = { navController.navigate(AppRoute.Tracks.route) },
                    onOpenBadges = { navController.navigate(AppRoute.Badges.route) },
                    onOpenProfile = { navController.navigate(AppRoute.Profile.route) },
                    onOpenWellbeing = { navController.navigate(AppRoute.Wellbeing.route) },
                    onOpenRankings = { navController.navigate(AppRoute.Rankings.route) },
                    onOpenSocial = { navController.navigate(AppRoute.Social.route) },
                    onOpenFocus = { navController.navigate(AppRoute.Focus.route) },
                    onOpenSprintRooms = { navController.navigate(AppRoute.SprintRooms.route) },
                    onManageRestrictedApps = {
                        navController.navigate(AppRoute.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = {
                        navController.navigate(AppRoute.Settings.route) {
                            launchSingleTop = true
                        }
                    },
                    onLogin = {
                        navController.navigate(AppRoute.Login.route) {
                            launchSingleTop = true
                        }
                    },
                    onLogout = {
                        navViewModel.logout()
                    },
                    isLoggedIn = sessionState.isAuthenticated
                )
            }

            // Login — optional, accessible from Dashboard
            composable(AppRoute.Login.route) {
                LoginRoute(
                    onGoogleSignIn = {
                        val activity = (navController.context as? Activity)
                            ?: return@LoginRoute
                        navViewModel.signInWithGoogle(activity)
                    },
                    message = authUiState.message,
                    isLoading = authUiState.isSigningIn
                )
            }

            composable(AppRoute.Tracks.route) {
                TracksRoute(
                    onBack = { navController.popBackStack() },
                    onOpenProblem = { navController.navigate(AppRoute.Problem.createRoute(it)) }
                )
            }

            composable(AppRoute.Badges.route) {
                BadgesRoute(onBack = { navController.popBackStack() })
            }

            composable(AppRoute.Profile.route) {
                ProfileRoute(onBack = { navController.popBackStack() }, onOpenSettings = { navController.navigate(AppRoute.Settings.route) })
            }

            composable(AppRoute.Wellbeing.route) {
                WellbeingRoute(onBack = { navController.popBackStack() })
            }

            composable(AppRoute.Rankings.route) {
                RankingsRoute(onBack = { navController.popBackStack() })
            }

            composable(AppRoute.Social.route) {
                SocialRoute(onBack = { navController.popBackStack() })
            }

            composable(AppRoute.Settings.route) {
                SettingsRoute(onBack = { navController.popBackStack() })
            }

            composable(AppRoute.Focus.route) {
                FocusTimerRoute(
                    onBack = { navController.popBackStack() },
                    onOpenProblem = { navController.navigate(AppRoute.Problem.createRoute(it)) }
                )
            }

            composable(AppRoute.SprintRooms.route) {
                FocusRoomRoute(
                    onBack = { navController.popBackStack() },
                    onOpenProblem = { navController.navigate(AppRoute.Problem.createRoute(it)) }
                )
            }

            composable(
                route = AppRoute.Problem.route,
                arguments = listOf(
                    navArgument("problemId") {
                        type = NavType.IntType
                    }
                )
            ) {
                ProblemRoute(
                    onBack = { navController.popBackStack() },
                    onVerified = {
                        navController.navigate(AppRoute.Dashboard.route) {
                            popUpTo(AppRoute.Dashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = AppRoute.LockOverlay.route,
                arguments = listOf(
                    navArgument("appName") {
                        type = NavType.StringType
                    }
                )
            ) { entry ->
                LockRoute(
                    appName = entry.arguments?.getString("appName").orEmpty(),
                    onSolveNow = {
                        navController.navigate(AppRoute.Problem.createRoute(1))
                    },
                    onBackToDashboard = {
                        navController.navigate(AppRoute.Dashboard.route) {
                            popUpTo(AppRoute.Dashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
