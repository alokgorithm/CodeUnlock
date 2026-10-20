package com.codeunlock.data.usage

import android.content.Context
import java.util.Locale

enum class AppCategory(val id: String, val label: String) {
    DISTRACTING("distracting", "Distracting"),
    PRODUCTIVE("productive", "Productive"),
    OTHERS("others", "Others")
}

object AppCategoryStore {
    private const val PREFS_NAME = "codeunlock_app_categories"

    private val DEFAULT_DISTRACTING = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.instagram.android",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.facebook.katana",
        "com.reddit.frontpage",
        "com.netflix.mediaclient",
        "com.snapchat.android",
        "com.twitch.android.app",
        "com.pinterest",
        "com.discord",
        "com.valvesoftware.android.steam.community",
        "com.supercell.clashofclans",
        "com.supercell.clashroyale",
        "com.supercell.brawlstars",
        "com.dts.freefireth",
        "com.pubg.imobile",
        "com.king.candycrushsaga"
    )

    private val DEFAULT_PRODUCTIVE = setOf(
        "com.codeunlock",
        "com.google.android.gm",
        "com.google.android.apps.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.keep",
        "com.google.android.apps.tasks",
        "com.google.android.calendar",
        "com.google.android.googlequicksearchbox",
        "com.microsoft.office.outlook",
        "com.microsoft.office.word",
        "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint",
        "com.slack",
        "com.atlassian.android.jira.core",
        "com.duolingo",
        "org.coursera.android",
        "com.udemy.android",
        "com.github.android",
        "com.termux",
        "notion.id"
    )

    fun getCategory(context: Context, packageName: String, isRestricted: Boolean = false): AppCategory {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(packageName, null)
        if (saved != null) {
            return when (saved) {
                AppCategory.DISTRACTING.id -> AppCategory.DISTRACTING
                AppCategory.PRODUCTIVE.id -> AppCategory.PRODUCTIVE
                else -> AppCategory.OTHERS
            }
        }
        if (isRestricted) return AppCategory.DISTRACTING

        val lower = packageName.lowercase(Locale.ROOT)
        if (DEFAULT_DISTRACTING.contains(packageName) ||
            lower.contains("youtube") || lower.contains("instagram") ||
            lower.contains("tiktok") || lower.contains("twitter") ||
            lower.contains("reddit") || lower.contains("netflix") ||
            lower.contains("twitch") || lower.contains("game") ||
            lower.contains("clash")
        ) {
            return AppCategory.DISTRACTING
        }

        if (DEFAULT_PRODUCTIVE.contains(packageName) ||
            lower.contains("code") || lower.contains("leetcode") ||
            lower.contains("learn") || lower.contains("study") ||
            lower.contains("duolingo") || lower.contains("notion") ||
            lower.contains("docs") || lower.contains("slack")
        ) {
            return AppCategory.PRODUCTIVE
        }

        return AppCategory.OTHERS
    }

    fun setCategory(context: Context, packageName: String, category: AppCategory) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(packageName, category.id).apply()
    }
}
