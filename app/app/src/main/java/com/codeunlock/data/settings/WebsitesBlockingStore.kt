package com.codeunlock.data.settings

import android.content.Context

/**
 * Preferences store for website domain blocking in mobile browsers (Chrome, Firefox, Edge, etc.)
 */
object WebsitesBlockingStore {

    private const val PREFS_NAME = "codeunlock_websites_blocking"
    private const val KEY_ENABLED = "websites_blocking_enabled"
    private const val KEY_BLOCKED_DOMAINS = "blocked_domains"

    val DEFAULT_BLOCKED_DOMAINS = setOf(
        "instagram.com",
        "tiktok.com",
        "twitter.com",
        "x.com",
        "reddit.com",
        "facebook.com"
    )

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getBlockedDomains(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BLOCKED_DOMAINS, null) ?: DEFAULT_BLOCKED_DOMAINS
    }

    fun addDomain(context: Context, domain: String) {
        val current = getBlockedDomains(context).toMutableSet()
        val cleanDomain = domain.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
        if (cleanDomain.isNotBlank()) {
            current.add(cleanDomain)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, current).apply()
        }
    }

    fun removeDomain(context: Context, domain: String) {
        val current = getBlockedDomains(context).toMutableSet()
        current.remove(domain)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_BLOCKED_DOMAINS, current).apply()
    }

    fun isUrlBlocked(context: Context, urlOrText: String): Boolean {
        if (!isEnabled(context)) return false
        val lower = urlOrText.lowercase()
        return getBlockedDomains(context).any { domain ->
            lower.contains(domain)
        }
    }
}
