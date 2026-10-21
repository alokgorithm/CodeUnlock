package com.codeunlock.usage

import com.codeunlock.data.usage.AppCategory
import com.codeunlock.data.usage.UsageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsCategoryFilterTest {

    @Test
    fun testFormatMinutes() {
        assertEquals("0m", UsageManager.formatMinutes(0L))
        assertEquals("42m", UsageManager.formatMinutes(42L))
        assertEquals("1h 23m", UsageManager.formatMinutes(83L))
        assertEquals("2h", UsageManager.formatMinutes(120L))
        assertEquals("5h 45m", UsageManager.formatMinutes(345L))
    }

    @Test
    fun testCategoryFilterHeroTimeAndSubtitleSelection() {
        val totalDistractingMin = 42L
        val totalProductiveMin = 0L
        val totalOthersMin = 41L
        val totalMin = 83L
        val baseSubtitle = "Today - Wed, 21 Oct"

        // Test All Apps (null)
        val (allMin, allSuffix) = when (null as AppCategory?) {
            AppCategory.DISTRACTING -> totalDistractingMin to " • Distracting"
            AppCategory.PRODUCTIVE -> totalProductiveMin to " • Productive"
            AppCategory.OTHERS -> totalOthersMin to " • Others"
            null -> totalMin to ""
        }
        assertEquals(83L, allMin)
        assertEquals("1h 23m", UsageManager.formatMinutes(allMin))
        assertEquals("Today - Wed, 21 Oct", baseSubtitle + allSuffix)

        // Test Distracting Filter
        val (distMin, distSuffix) = when (AppCategory.DISTRACTING) {
            AppCategory.DISTRACTING -> totalDistractingMin to " • Distracting"
            AppCategory.PRODUCTIVE -> totalProductiveMin to " • Productive"
            AppCategory.OTHERS -> totalOthersMin to " • Others"
            null -> totalMin to ""
        }
        assertEquals(42L, distMin)
        assertEquals("42m", UsageManager.formatMinutes(distMin))
        assertEquals("Today - Wed, 21 Oct • Distracting", baseSubtitle + distSuffix)

        // Test Productive Filter
        val (prodMin, prodSuffix) = when (AppCategory.PRODUCTIVE) {
            AppCategory.DISTRACTING -> totalDistractingMin to " • Distracting"
            AppCategory.PRODUCTIVE -> totalProductiveMin to " • Productive"
            AppCategory.OTHERS -> totalOthersMin to " • Others"
            null -> totalMin to ""
        }
        assertEquals(0L, prodMin)
        assertEquals("0m", UsageManager.formatMinutes(prodMin))
        assertEquals("Today - Wed, 21 Oct • Productive", baseSubtitle + prodSuffix)

        // Test Others Filter
        val (othMin, othSuffix) = when (AppCategory.OTHERS) {
            AppCategory.DISTRACTING -> totalDistractingMin to " • Distracting"
            AppCategory.PRODUCTIVE -> totalProductiveMin to " • Productive"
            AppCategory.OTHERS -> totalOthersMin to " • Others"
            null -> totalMin to ""
        }
        assertEquals(41L, othMin)
        assertEquals("41m", UsageManager.formatMinutes(othMin))
        assertEquals("Today - Wed, 21 Oct • Others", baseSubtitle + othSuffix)
    }

    @Test
    fun testAppFilteringByCategory() {
        val apps = listOf(
            UsageManager.AppUsageDetail(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                durationSeconds = 2520L,
                durationFormatted = "42m",
                category = AppCategory.DISTRACTING,
                limitMinutes = 30,
                isBlocking = true
            ),
            UsageManager.AppUsageDetail(
                packageName = "com.google.android.gm",
                appName = "Gmail",
                durationSeconds = 600L,
                durationFormatted = "10m",
                category = AppCategory.PRODUCTIVE,
                limitMinutes = null,
                isBlocking = false
            ),
            UsageManager.AppUsageDetail(
                packageName = "com.android.chrome",
                appName = "Chrome",
                durationSeconds = 2460L,
                durationFormatted = "41m",
                category = AppCategory.OTHERS,
                limitMinutes = null,
                isBlocking = false
            )
        )

        // Filter Distracting
        val distractingOnly = apps.filter { it.category == AppCategory.DISTRACTING }
        assertEquals(1, distractingOnly.size)
        assertEquals("YouTube", distractingOnly.first().appName)

        // Filter Productive
        val productiveOnly = apps.filter { it.category == AppCategory.PRODUCTIVE }
        assertEquals(1, productiveOnly.size)
        assertEquals("Gmail", productiveOnly.first().appName)

        // Filter Others
        val othersOnly = apps.filter { it.category == AppCategory.OTHERS }
        assertEquals(1, othersOnly.size)
        assertEquals("Chrome", othersOnly.first().appName)

        // All Apps
        val allApps = apps.filter { true }
        assertEquals(3, allApps.size)
    }
}
