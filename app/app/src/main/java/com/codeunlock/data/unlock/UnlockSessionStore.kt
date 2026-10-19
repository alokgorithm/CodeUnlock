package com.codeunlock.data.unlock

import android.content.Context
import com.codeunlock.api.dto.UnlockSessionDto
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backend-mirrored local unlock-session cache for the Android app.
 *
 * This store is intentionally synchronous and SharedPreferences-based because it
 * is read from UI, navigation, and accessibility/service code paths.
 *
 * Important design rules:
 * - The backend is the source of truth in live mode
 * - This store mirrors backend-issued unlock sessions for fast local reads
 * - Multiple active unlock sessions are supported
 * - Package-specific unlocks and generic/global unlocks are both supported
 *
 * Local grant helpers are reserved for backend-mirrored sessions and tests.
 */
object UnlockSessionStore {

    private const val PREFS_NAME = "codeunlock_unlocks"

    private const val KEY_ACTIVE_SESSIONS_JSON = "active_sessions_json"
    private const val KEY_LAST_EMERGENCY_EPOCH_DAY = "last_emergency_epoch_day"
    private const val KEY_LAST_CODING_EPOCH_DAY = "last_coding_epoch_day"
    private const val KEY_FREE_UNLOCK_PREFIX = "free_unlock_epoch_day_"

    const val DEFAULT_CODING_UNLOCK_MINUTES = 30
    const val DEFAULT_EMERGENCY_UNLOCK_MINUTES = 30
    const val DEFAULT_FREE_UNLOCK_MINUTES = 1

    enum class UnlockType {
        CODING,
        EMERGENCY,
        FREE;

        companion object {
            fun fromRaw(raw: String?): UnlockType {
                return when (raw?.trim()?.uppercase(Locale.US)) {
                    "EMERGENCY" -> EMERGENCY
                    "FREE" -> FREE
                    else -> CODING
                }
            }
        }
    }

    data class UnlockSession(
        val id: String,
        val appPackage: String? = null,
        val unlockType: UnlockType,
        val grantedAtMillis: Long,
        val expiresAtMillis: Long,
        val durationMinutes: Int,
        val isActiveFromServer: Boolean = true
    ) {
        val isActive: Boolean
            get() = isActiveAt(System.currentTimeMillis())

        fun isActiveAt(nowMillis: Long): Boolean {
            return isActiveFromServer && expiresAtMillis > nowMillis
        }

        fun remainingMinutes(nowMillis: Long): Int {
            if (!isActiveAt(nowMillis)) return 0
            val remainingMillis = expiresAtMillis - nowMillis
            return ((remainingMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
        }

        fun matchesPackage(packageName: String): Boolean {
            val normalizedTarget = normalizePackageKey(packageName)
            val normalizedSessionPackage = appPackage?.let(::normalizePackageKey)
            return normalizedSessionPackage == null || normalizedSessionPackage == normalizedTarget
        }
    }

    data class UnlockAvailability(
        val activeSession: UnlockSession?,
        val activeSessions: List<UnlockSession>,
        val hasActiveUnlock: Boolean,
        val remainingMinutes: Int,
        val canUseEmergencyUnlock: Boolean,
        val canUseFreeUnlock: Boolean
    )

    fun readActiveSessions(context: Context): List<UnlockSession> {
        val now = System.currentTimeMillis()
        val active = readSessionsInternal(context)
            .filter { it.isActiveAt(now) }
            .sortedByDescending { it.expiresAtMillis }

        persistSessions(context, active)
        return active
    }

    fun readActiveSession(
        context: Context,
        appPackage: String? = null
    ): UnlockSession? {
        val sessions = readActiveSessions(context)
        return selectBestMatchingSession(
            sessions = sessions,
            appPackage = appPackage,
            nowMillis = System.currentTimeMillis()
        )
    }

    fun getAvailability(
        context: Context,
        appPackage: String
    ): UnlockAvailability {
        val now = System.currentTimeMillis()
        val activeSessions = readActiveSessions(context)
        val activeSession = selectBestMatchingSession(
            sessions = activeSessions,
            appPackage = appPackage,
            nowMillis = now
        )

        return UnlockAvailability(
            activeSession = activeSession,
            activeSessions = activeSessions,
            hasActiveUnlock = activeSession != null,
            remainingMinutes = activeSession?.remainingMinutes(now) ?: 0,
            canUseEmergencyUnlock = canUseEmergencyUnlock(context),
            canUseFreeUnlock = canUseFreeUnlock(context, appPackage)
        )
    }

    fun hasActiveUnlock(
        context: Context,
        appPackage: String? = null
    ): Boolean {
        return readActiveSession(context, appPackage) != null
    }

    fun getRemainingUnlockMinutes(
        context: Context,
        appPackage: String? = null
    ): Int {
        val session = readActiveSession(context, appPackage) ?: return 0
        return session.remainingMinutes(System.currentTimeMillis())
    }

    /**
     * Replace the local mirror with the backend's current active unlock set.
     */
    fun replaceWithServerUnlocks(
        context: Context,
        sessions: List<UnlockSessionDto>
    ) {
        val mapped = sessions.mapNotNull(::fromDto)
            .filter { it.isActive }
            .sortedByDescending { it.expiresAtMillis }

        persistSessions(context, mapped)
    }

    /**
     * Upsert a single backend-issued unlock into the local mirror.
     */
    fun cacheServerUnlock(
        context: Context,
        session: UnlockSessionDto?
    ) {
        val mapped = fromDto(session) ?: return
        val current = readSessionsInternal(context).toMutableList()
        val existingIndex = current.indexOfFirst { it.id == mapped.id }

        if (existingIndex >= 0) {
            current[existingIndex] = mapped
        } else {
            current.add(mapped)
        }

        val active = current
            .filter { it.isActive }
            .sortedByDescending { it.expiresAtMillis }

        persistSessions(context, active)
    }

    /**
     * Remove expired sessions from the local mirror.
     */
    fun pruneExpired(context: Context) {
        persistSessions(context, readActiveSessions(context))
    }

    /**
     * Local helper; production flows should prefer backend-issued sessions.
     */
    fun grantCodingUnlock(
        context: Context,
        appPackage: String? = null,
        durationMinutes: Int = DEFAULT_CODING_UNLOCK_MINUTES
    ): UnlockSession {
        val now = System.currentTimeMillis()
        val normalizedDuration = durationMinutes.coerceAtLeast(1)

        val session = UnlockSession(
            id = "local_coding_$now",
            appPackage = appPackage?.takeIf { it.isNotBlank() },
            unlockType = UnlockType.CODING,
            grantedAtMillis = now,
            expiresAtMillis = now + normalizedDuration * 60_000L,
            durationMinutes = normalizedDuration,
            isActiveFromServer = true
        )

        upsertLocalSession(context, session)
        prefs(context).edit()
            .putLong(KEY_LAST_CODING_EPOCH_DAY, currentEpochDay(now))
            .apply()

        return session
    }

    /**
     * Local helper; production flows should prefer backend-issued sessions.
     */
    fun grantEmergencyUnlock(
        context: Context,
        appPackage: String? = null,
        durationMinutes: Int = DEFAULT_EMERGENCY_UNLOCK_MINUTES
    ): UnlockSession? {
        if (!canUseEmergencyUnlock(context)) return null

        val now = System.currentTimeMillis()
        val normalizedDuration = durationMinutes.coerceAtLeast(1)

        val session = UnlockSession(
            id = "local_emergency_$now",
            appPackage = appPackage?.takeIf { it.isNotBlank() },
            unlockType = UnlockType.EMERGENCY,
            grantedAtMillis = now,
            expiresAtMillis = now + normalizedDuration * 60_000L,
            durationMinutes = normalizedDuration,
            isActiveFromServer = true
        )

        upsertLocalSession(context, session)
        prefs(context).edit()
            .putLong(KEY_LAST_EMERGENCY_EPOCH_DAY, currentEpochDay(now))
            .apply()

        return session
    }

    /**
     * Local helper; production flows should prefer backend-issued sessions.
     */
    fun grantFreeUnlock(
        context: Context,
        appPackage: String,
        durationMinutes: Int = DEFAULT_FREE_UNLOCK_MINUTES
    ): UnlockSession? {
        val normalizedPackage = normalizePackageKey(appPackage)
        if (!canUseFreeUnlock(context, normalizedPackage)) return null

        val now = System.currentTimeMillis()
        val normalizedDuration = durationMinutes.coerceAtLeast(1)

        val session = UnlockSession(
            id = "local_free_${normalizedPackage}_$now",
            appPackage = normalizedPackage,
            unlockType = UnlockType.FREE,
            grantedAtMillis = now,
            expiresAtMillis = now + normalizedDuration * 60_000L,
            durationMinutes = normalizedDuration,
            isActiveFromServer = true
        )

        upsertLocalSession(context, session)
        prefs(context).edit()
            .putLong(freeUnlockKey(normalizedPackage), currentEpochDay(now))
            .apply()

        return session
    }

    fun canUseEmergencyUnlock(context: Context): Boolean {
        val today = currentEpochDay(System.currentTimeMillis())
        val lastUsed = prefs(context).getLong(KEY_LAST_EMERGENCY_EPOCH_DAY, Long.MIN_VALUE)
        return lastUsed != today
    }

    fun canUseFreeUnlock(
        context: Context,
        appPackage: String
    ): Boolean {
        val normalizedPackage = normalizePackageKey(appPackage)
        val today = currentEpochDay(System.currentTimeMillis())
        val lastUsed = prefs(context).getLong(freeUnlockKey(normalizedPackage), Long.MIN_VALUE)
        return lastUsed != today
    }

    fun clearActiveSession(
        context: Context,
        sessionId: String
    ) {
        val updated = readSessionsInternal(context)
            .filterNot { it.id == sessionId }

        persistSessions(context, updated)
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    internal fun selectBestMatchingSession(
        sessions: List<UnlockSession>,
        appPackage: String?,
        nowMillis: Long
    ): UnlockSession? {
        val activeSessions = sessions.filter { it.isActiveAt(nowMillis) }

        if (appPackage.isNullOrBlank()) {
            return activeSessions.maxByOrNull { it.expiresAtMillis }
        }

        return activeSessions
            .filter { it.matchesPackage(appPackage) }
            .maxByOrNull { it.expiresAtMillis }
    }

    private fun upsertLocalSession(
        context: Context,
        session: UnlockSession
    ) {
        val current = readSessionsInternal(context).toMutableList()
        val existingIndex = current.indexOfFirst { it.id == session.id }

        if (existingIndex >= 0) {
            current[existingIndex] = session
        } else {
            current.add(session)
        }

        val active = current
            .filter { it.isActive }
            .sortedByDescending { it.expiresAtMillis }

        persistSessions(context, active)
    }

    private fun persistSessions(
        context: Context,
        sessions: List<UnlockSession>
    ) {
        val jsonArray = JSONArray()
        sessions
            .distinctBy { it.id }
            .forEach { session ->
                jsonArray.put(
                    JSONObject()
                        .put("id", session.id)
                        .put("app_package", session.appPackage)
                        .put("unlock_type", session.unlockType.name)
                        .put("granted_at_millis", session.grantedAtMillis)
                        .put("expires_at_millis", session.expiresAtMillis)
                        .put("duration_minutes", session.durationMinutes)
                        .put("is_active_from_server", session.isActiveFromServer)
                )
            }

        prefs(context).edit()
            .putString(KEY_ACTIVE_SESSIONS_JSON, jsonArray.toString())
            .apply()
    }

    private fun readSessionsInternal(context: Context): List<UnlockSession> {
        val raw = prefs(context).getString(KEY_ACTIVE_SESSIONS_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val id = json.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val expiresAtMillis = json.optLong("expires_at_millis", 0L)
                    if (expiresAtMillis <= 0L) continue

                    add(
                        UnlockSession(
                            id = id,
                            appPackage = json.optString("app_package").takeIf { it.isNotBlank() },
                            unlockType = UnlockType.fromRaw(json.optString("unlock_type")),
                            grantedAtMillis = json.optLong("granted_at_millis", 0L),
                            expiresAtMillis = expiresAtMillis,
                            durationMinutes = json.optInt("duration_minutes", 0).coerceAtLeast(0),
                            isActiveFromServer = json.optBoolean("is_active_from_server", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun fromDto(dto: UnlockSessionDto?): UnlockSession? {
        dto ?: return null

        val id = dto.id.trim().ifBlank { return null }
        val expiresAtMillis = parseInstantMillis(dto.expiresAt) ?: return null
        val grantedAtMillis = parseInstantMillis(dto.grantedAt) ?: (expiresAtMillis - dto.durationMinutes * 60_000L)

        return UnlockSession(
            id = id,
            appPackage = dto.appPackage?.trim()?.takeIf { it.isNotBlank() },
            unlockType = UnlockType.fromRaw(dto.unlockType),
            grantedAtMillis = grantedAtMillis,
            expiresAtMillis = expiresAtMillis,
            durationMinutes = dto.durationMinutes.coerceAtLeast(0),
            isActiveFromServer = dto.isActive ?: true
        )
    }

    private fun parseInstantMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun currentEpochDay(nowMillis: Long): Long = nowMillis / 86_400_000L

    private fun freeUnlockKey(appPackage: String): String =
        KEY_FREE_UNLOCK_PREFIX + normalizePackageKey(appPackage)

    private fun normalizePackageKey(appPackage: String): String =
        appPackage.trim().lowercase(Locale.US)
}
