package com.codeunlock.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.codeunlock.data.settings.ReelsBlockingStore

/**
 * ReelsShortsInterceptor — surgical sub-feature blocking inspired by Regain.
 *
 * Targets vertical short-form video feeds (Instagram Reels and YouTube Shorts)
 * without breaking normal app usage (messaging, searching, educational long-form videos).
 *
 * Includes Regain's signature "One-Video Rule":
 *   Allows the user to view 1 reel/short (e.g., shared via DM/link), but intercepts
 *   as soon as they swipe down/up into the infinite algorithmic feed.
 */
class ReelsShortsInterceptor(private val service: AccessibilityService) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastInterceptionTimestamp = 0L
    private var lastObservedPackage: String? = null
    private var videoScrollCount = 0

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName != PACKAGE_INSTAGRAM && packageName != PACKAGE_YOUTUBE) return

        val settings = ReelsBlockingStore.read(service.applicationContext)

        if (packageName != lastObservedPackage) {
            lastObservedPackage = packageName
            videoScrollCount = 0
        }

        when (packageName) {
            PACKAGE_INSTAGRAM -> {
                if (settings.reelsBlocked) {
                    inspectInstagram(event, settings)
                }
            }
            PACKAGE_YOUTUBE -> {
                if (settings.shortsBlocked) {
                    inspectYouTube(event, settings)
                }
            }
        }
    }

    private fun inspectInstagram(event: AccessibilityEvent, settings: ReelsBlockingStore.ReelsSettings) {
        val rootNode = service.rootInActiveWindow ?: return
        try {
            val isReelsVisible = hasMatchingNode(rootNode, INSTAGRAM_REELS_INDICATORS)

            if (isReelsVisible) {
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                    videoScrollCount++
                }

                val shouldIntercept = if (settings.oneVideoRule) {
                    videoScrollCount >= 1
                } else {
                    true
                }

                if (shouldIntercept) {
                    triggerInterception("Instagram Reels blocked")
                }
            }
        } finally {
            runCatching { rootNode.recycle() }
        }
    }

    private fun inspectYouTube(event: AccessibilityEvent, settings: ReelsBlockingStore.ReelsSettings) {
        val rootNode = service.rootInActiveWindow ?: return
        try {
            val isShortsVisible = hasMatchingNode(rootNode, YOUTUBE_SHORTS_INDICATORS)

            if (isShortsVisible) {
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                    videoScrollCount++
                }

                val shouldIntercept = if (settings.oneVideoRule) {
                    videoScrollCount >= 1
                } else {
                    true
                }

                if (shouldIntercept) {
                    triggerInterception("YouTube Shorts blocked")
                }
            }
        } finally {
            runCatching { rootNode.recycle() }
        }
    }

    private fun hasMatchingNode(root: AccessibilityNodeInfo, indicators: List<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var nodesExamined = 0
        val maxNodesToExamine = 60 // Keep traversal tight and fast

        try {
            while (queue.isNotEmpty() && nodesExamined < maxNodesToExamine) {
                val current = queue.removeFirst()
                nodesExamined++

                val viewId = current.viewIdResourceName?.lowercase().orEmpty()
                val contentDesc = current.contentDescription?.toString()?.lowercase().orEmpty()
                val text = current.text?.toString()?.lowercase().orEmpty()

                var matched = false
                for (indicator in indicators) {
                    if (viewId.contains(indicator) || contentDesc == indicator || text == indicator) {
                        matched = true
                        break
                    }
                }

                if (matched) {
                    if (current != root) runCatching { current.recycle() }
                    return true
                }

                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { child ->
                        queue.add(child)
                    }
                }

                if (current != root) {
                    runCatching { current.recycle() }
                }
            }
        } finally {
            while (queue.isNotEmpty()) {
                val rem = queue.removeFirst()
                if (rem != root) runCatching { rem.recycle() }
            }
        }

        return false
    }

    private fun triggerInterception(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastInterceptionTimestamp < DEBOUNCE_COOLDOWN_MS) return
        lastInterceptionTimestamp = now

        Log.i(TAG, "Surgical intercept: $reason")

        // Perform back action to eject from the shorts/reels viewer
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

        mainHandler.post {
            Toast.makeText(
                service.applicationContext,
                "🚫 $reason — solve a problem to unlock free time!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val TAG = "CodeUnlock.ReelsInter"
        private const val DEBOUNCE_COOLDOWN_MS = 1_200L

        const val PACKAGE_INSTAGRAM = "com.instagram.android"
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"

        private val INSTAGRAM_REELS_INDICATORS = listOf(
            "reel_viewer",
            "clips_video_container",
            "clips_viewer",
            "feed_tab_reels",
            "reels"
        )

        private val YOUTUBE_SHORTS_INDICATORS = listOf(
            "shorts_container",
            "reel_watch_fragment_container",
            "reel_recycler",
            "shorts_player_fragment",
            "shorts"
        )
    }
}
