package com.codeunlock.data.platform

import android.content.Context
import android.util.Log
import com.codeunlock.api.dto.CodeforcesStatsDto
import com.codeunlock.api.dto.LeetCodeStatsDto
import com.codeunlock.data.progress.DailyProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object PublicCodingPlatformClient {

    private const val TAG = "PublicCodingPlatform"
    private const val PREFS_CACHE = "codeunlock_platform_cache"
    private const val KEY_LEETCODE_CACHE = "cached_leetcode_"
    private const val KEY_CODEFORCES_CACHE = "cached_codeforces_"

    private val resilientDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: UnknownHostException) {
                when (hostname.lowercase(Locale.US)) {
                    "leetcode.com" -> listOf(
                        InetAddress.getByName("104.20.41.79"),
                        InetAddress.getByName("172.66.154.160")
                    )
                    "codeforces.com" -> listOf(
                        InetAddress.getByName("172.67.68.254"),
                        InetAddress.getByName("104.26.6.164")
                    )
                    "codeunlock-api.arka-cmd.workers.dev" -> listOf(
                        InetAddress.getByName("104.21.80.171"),
                        InetAddress.getByName("172.67.152.52")
                    )
                    else -> throw e
                }
            }
        }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(resilientDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchLeetCodeStats(context: Context, username: String): LeetCodeStatsDto? = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().removePrefix("@")
        if (cleanUsername.isBlank()) return@withContext null

        val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)

        try {
            val query = """
                query userProfileAndSubmissions(${'$'}username: String!) {
                  matchedUser(username: ${'$'}username) {
                    submitStats {
                      acSubmissionNum {
                        difficulty
                        count
                      }
                    }
                    profile {
                      reputation
                      ranking
                    }
                  }
                  userContestRanking(username: ${'$'}username) {
                    rating
                    globalRanking
                  }
                  recentAcSubmissionList(username: ${'$'}username, limit: 30) {
                    title
                    titleSlug
                    timestamp
                    statusDisplay
                    lang
                  }
                }
            """.trimIndent()

            val bodyJson = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().put("username", cleanUsername))
            }

            val request = Request.Builder()
                .url("https://leetcode.com/graphql")
                .header("Content-Type", "application/json")
                .header("User-Agent", "CodeUnlock-Android/2.0")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "LeetCode fetch failed: HTTP ${response.code}")
                    return@use readCachedLeetCode(prefs, cleanUsername)
                }

                val responseStr = response.body?.string().orEmpty()
                val json = JSONObject(responseStr)
                val data = json.optJSONObject("data") ?: return@use readCachedLeetCode(prefs, cleanUsername)
                val matchedUser = data.optJSONObject("matchedUser") ?: return@use readCachedLeetCode(prefs, cleanUsername)

                var totalSolved = 0
                var easySolved = 0
                var mediumSolved = 0
                var hardSolved = 0

                val acList = matchedUser.optJSONObject("submitStats")?.optJSONArray("acSubmissionNum")
                if (acList != null) {
                    for (i in 0 until acList.length()) {
                        val item = acList.optJSONObject(i) ?: continue
                        when (item.optString("difficulty")) {
                            "All" -> totalSolved = item.optInt("count", 0)
                            "Easy" -> easySolved = item.optInt("count", 0)
                            "Medium" -> mediumSolved = item.optInt("count", 0)
                            "Hard" -> hardSolved = item.optInt("count", 0)
                        }
                    }
                }

                val contest = data.optJSONObject("userContestRanking")
                val contestRating = contest?.optDouble("rating", 0.0) ?: 0.0
                val contestRank = contest?.optInt("globalRanking", 0) ?: 0
                val profileRank = matchedUser.optJSONObject("profile")?.optInt("ranking", 0) ?: 0
                val globalRanking = if (contestRank > 0) contestRank else profileRank

                // Calculate solves today
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDaySeconds = cal.timeInMillis / 1000L

                val solvedSlugsToday = mutableSetOf<String>()
                val submissions = data.optJSONArray("recentAcSubmissionList")
                if (submissions != null) {
                    for (i in 0 until submissions.length()) {
                        val sub = submissions.optJSONObject(i) ?: continue
                        val status = sub.optString("statusDisplay", "")
                        val ts = sub.optLong("timestamp", 0L)
                        if (ts >= startOfDaySeconds && status.equals("Accepted", ignoreCase = true)) {
                            val slug = sub.optString("titleSlug", "").trim()
                            if (slug.isNotBlank()) {
                                solvedSlugsToday.add(slug)
                            }
                        }
                    }
                }

                val solvedTodayCount = solvedSlugsToday.size
                DailyProgressStore.setPlatformSolvesToday(context, "leetcode", solvedTodayCount)

                val stats = LeetCodeStatsDto(
                    totalSolved = totalSolved,
                    easySolved = easySolved,
                    mediumSolved = mediumSolved,
                    hardSolved = hardSolved,
                    contestRating = contestRating,
                    globalRanking = globalRanking,
                    problemsSolvedToday = solvedTodayCount,
                    asOf = DailyProgressStore.getTodayKey()
                )

                // Cache to preferences
                cacheLeetCode(prefs, cleanUsername, stats)
                Log.d(TAG, "LeetCode fetched for $cleanUsername: total=$totalSolved, today=$solvedTodayCount")
                stats
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception fetching LeetCode for $cleanUsername: ${e.message}")
            readCachedLeetCode(prefs, cleanUsername)
        }
    }

    suspend fun fetchCodeforcesStats(context: Context, handle: String): CodeforcesStatsDto? = withContext(Dispatchers.IO) {
        val cleanHandle = handle.trim().removePrefix("@")
        if (cleanHandle.isBlank()) return@withContext null

        val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)

        try {
            // 1. User Info
            val infoReq = Request.Builder()
                .url("https://codeforces.com/api/user.info?handles=$cleanHandle")
                .header("User-Agent", "CodeUnlock-Android/2.0")
                .get()
                .build()

            var rating = 0
            var maxRating = 0
            var rank = "unrated"

            httpClient.newCall(infoReq).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string().orEmpty())
                    if (json.optString("status") == "OK") {
                        val resultArr = json.optJSONArray("result")
                        val user = resultArr?.optJSONObject(0)
                        if (user != null) {
                            rating = user.optInt("rating", 0)
                            maxRating = user.optInt("maxRating", 0)
                            rank = user.optString("rank", "unrated")
                        }
                    }
                }
            }

            // 2. Submissions for today
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDaySeconds = cal.timeInMillis / 1000L

            val statusReq = Request.Builder()
                .url("https://codeforces.com/api/user.status?handle=$cleanHandle&from=1&count=50")
                .header("User-Agent", "CodeUnlock-Android/2.0")
                .get()
                .build()

            var solvedTodayCount = 0
            httpClient.newCall(statusReq).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string().orEmpty())
                    if (json.optString("status") == "OK") {
                        val resultArr = json.optJSONArray("result")
                        val distinctSolved = mutableSetOf<String>()
                        if (resultArr != null) {
                            for (i in 0 until resultArr.length()) {
                                val item = resultArr.optJSONObject(i) ?: continue
                                val verdict = item.optString("verdict")
                                val ts = item.optLong("creationTimeSeconds", 0L)
                                if (ts >= startOfDaySeconds && verdict == "OK") {
                                    val prob = item.optJSONObject("problem")
                                    val name = prob?.optString("name") ?: "${prob?.optInt("contestId")}_${prob?.optString("index")}"
                                    if (name.isNotBlank()) distinctSolved.add(name)
                                }
                            }
                        }
                        solvedTodayCount = distinctSolved.size
                    }
                }
            }

            DailyProgressStore.setPlatformSolvesToday(context, "codeforces", solvedTodayCount)

            val stats = CodeforcesStatsDto(
                currentRating = rating,
                maxRating = maxRating,
                rank = rank,
                problemsSolved = 0,
                problemsSolvedToday = solvedTodayCount,
                contestCount = 0,
                asOf = DailyProgressStore.getTodayKey()
            )

            cacheCodeforces(prefs, cleanHandle, stats)
            Log.d(TAG, "Codeforces fetched for $cleanHandle: rating=$rating, rank=$rank, today=$solvedTodayCount")
            stats
        } catch (e: Exception) {
            Log.w(TAG, "Exception fetching Codeforces for $cleanHandle: ${e.message}")
            readCachedCodeforces(prefs, cleanHandle)
        }
    }

    suspend fun verifyLeetCodeSolve(username: String, problemSlug: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().removePrefix("@")
        val cleanSlug = problemSlug.trim().lowercase(Locale.US)
        if (cleanUsername.isBlank() || cleanSlug.isBlank()) return@withContext false

        try {
            val query = """
                query recentAcSubmissions(${'$'}username: String!) {
                  recentAcSubmissionList(username: ${'$'}username, limit: 15) {
                    titleSlug
                    timestamp
                    statusDisplay
                  }
                }
            """.trimIndent()

            val bodyJson = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().put("username", cleanUsername))
            }

            val request = Request.Builder()
                .url("https://leetcode.com/graphql")
                .header("Content-Type", "application/json")
                .header("User-Agent", "CodeUnlock-Android/2.0")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val json = JSONObject(response.body?.string().orEmpty())
                val submissions = json.optJSONObject("data")?.optJSONArray("recentAcSubmissionList") ?: return@use false

                val nowSec = System.currentTimeMillis() / 1000L
                val oneDayAgoSec = nowSec - 86400L

                for (i in 0 until submissions.length()) {
                    val sub = submissions.optJSONObject(i) ?: continue
                    val slug = sub.optString("titleSlug", "").trim().lowercase(Locale.US)
                    val status = sub.optString("statusDisplay", "")
                    val ts = sub.optLong("timestamp", 0L)

                    if (slug == cleanSlug && status.equals("Accepted", ignoreCase = true) && ts >= oneDayAgoSec) {
                        return@use true
                    }
                }
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed verifying LeetCode solve: ${e.message}")
            false
        }
    }

    private fun cacheLeetCode(prefs: android.content.SharedPreferences, username: String, stats: LeetCodeStatsDto) {
        val json = JSONObject().apply {
            put("totalSolved", stats.totalSolved)
            put("easySolved", stats.easySolved)
            put("mediumSolved", stats.mediumSolved)
            put("hardSolved", stats.hardSolved)
            put("contestRating", stats.contestRating)
            put("globalRanking", stats.globalRanking)
            put("problemsSolvedToday", stats.problemsSolvedToday)
            put("asOf", stats.asOf)
        }
        prefs.edit().putString(KEY_LEETCODE_CACHE + username.lowercase(Locale.US), json.toString()).apply()
    }

    private fun readCachedLeetCode(prefs: android.content.SharedPreferences, username: String): LeetCodeStatsDto? {
        val raw = prefs.getString(KEY_LEETCODE_CACHE + username.lowercase(Locale.US), null) ?: return null
        return try {
            val json = JSONObject(raw)
            LeetCodeStatsDto(
                totalSolved = json.optInt("totalSolved", 0),
                easySolved = json.optInt("easySolved", 0),
                mediumSolved = json.optInt("mediumSolved", 0),
                hardSolved = json.optInt("hardSolved", 0),
                contestRating = json.optDouble("contestRating", 0.0),
                globalRanking = json.optInt("globalRanking", 0),
                problemsSolvedToday = json.optInt("problemsSolvedToday", 0),
                asOf = json.optString("asOf", DailyProgressStore.getTodayKey())
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheCodeforces(prefs: android.content.SharedPreferences, handle: String, stats: CodeforcesStatsDto) {
        val json = JSONObject().apply {
            put("currentRating", stats.currentRating)
            put("maxRating", stats.maxRating)
            put("rank", stats.rank)
            put("problemsSolved", stats.problemsSolved)
            put("problemsSolvedToday", stats.problemsSolvedToday)
            put("contestCount", stats.contestCount)
            put("asOf", stats.asOf)
        }
        prefs.edit().putString(KEY_CODEFORCES_CACHE + handle.lowercase(Locale.US), json.toString()).apply()
    }

    private fun readCachedCodeforces(prefs: android.content.SharedPreferences, handle: String): CodeforcesStatsDto? {
        val raw = prefs.getString(KEY_CODEFORCES_CACHE + handle.lowercase(Locale.US), null) ?: return null
        return try {
            val json = JSONObject(raw)
            CodeforcesStatsDto(
                currentRating = json.optInt("currentRating", 0),
                maxRating = json.optInt("maxRating", 0),
                rank = json.optString("rank", "unrated"),
                problemsSolved = json.optInt("problemsSolved", 0),
                problemsSolvedToday = json.optInt("problemsSolvedToday", 0),
                contestCount = json.optInt("contestCount", 0),
                asOf = json.optString("asOf", DailyProgressStore.getTodayKey())
            )
        } catch (_: Exception) {
            null
        }
    }
}
