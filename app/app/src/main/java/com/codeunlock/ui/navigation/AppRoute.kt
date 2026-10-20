package com.codeunlock.ui.navigation

import android.net.Uri

sealed class AppRoute(val route: String) {
    data object Login : AppRoute("login")
    data object Permissions : AppRoute("permissions")
    data object Dashboard : AppRoute("dashboard")
    data object Tracks : AppRoute("tracks")
    data object Badges : AppRoute("badges")
    data object Profile : AppRoute("profile")
    data object Wellbeing : AppRoute("wellbeing")
    data object UsageStats : AppRoute("usage_stats")
    data object AppLimits : AppRoute("app_limits")
    data object Rankings : AppRoute("rankings")
    data object Social : AppRoute("social")
    data object Focus : AppRoute("focus")
    data object SprintRooms : AppRoute("sprint_rooms")
    data object Settings : AppRoute("settings")
    data object Problem : AppRoute("problem/{problemId}") {
        fun createRoute(problemId: Int): String = "problem/$problemId"
    }

    data object LockOverlay : AppRoute("lock/{appName}") {
        fun createRoute(appName: String): String = "lock/${Uri.encode(appName)}"
    }
}
