package com.codeunlock.service.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.codeunlock.ui.MainActivity
import com.codeunlock.ui.navigation.AppRoute
import kotlin.math.abs

/**
 * Floating sidebar/pill overlay that displays the active Focus Timer
 * over all applications while a Focus Session is running.
 *
 * - Draggable along the screen edges with snap-to-edge docking.
 * - Tap brings the user directly to CodeUnlock's Focus Timer screen.
 * - Live updating countdown timer and status dot (running/paused/break).
 */
object FloatingFocusTimerOverlay {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var timerTextView: TextView? = null
    private var statusDotView: View? = null
    private var iconTextView: TextView? = null
    private var isShown = false
    private var isSuppressed = false

    private val mainHandler = Handler(Looper.getMainLooper())

    fun setSuppressed(suppressed: Boolean) {
        mainHandler.post {
            isSuppressed = suppressed
            overlayView?.visibility = if (suppressed) View.GONE else View.VISIBLE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context, initialTimeText: String = "25:00") {
        mainHandler.post {
            if (isShown || !Settings.canDrawOverlays(context)) return@post

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            windowManager = wm

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                val metrics = context.resources.displayMetrics
                // Default position docked near top-right edge
                x = metrics.widthPixels - dpToPx(context, 125f)
                y = dpToPx(context, 160f)
            }

            // Create Container Layout
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                elevation = dpToPx(context, 8f).toFloat()

                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(context, 20f).toFloat()
                    setColor(Color.parseColor("#E6121826")) // Frosted deep slate
                    setStroke(dpToPx(context, 1.5f), Color.parseColor("#3B82F6")) // Electric blue border
                }
                background = bg
                setPadding(
                    dpToPx(context, 10f),
                    dpToPx(context, 6f),
                    dpToPx(context, 12f),
                    dpToPx(context, 6f)
                )
            }

            // Status Dot View
            val dot = View(context).apply {
                val dotBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#10B981")) // Green for active
                }
                background = dotBg
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(context, 8f),
                    dpToPx(context, 8f)
                ).apply {
                    setMargins(0, 0, dpToPx(context, 6f), 0)
                }
            }
            statusDotView = dot
            container.addView(dot)

            // Timer Text View
            val timerText = TextView(context).apply {
                text = initialTimeText
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.MONOSPACE
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            timerTextView = timerText
            container.addView(timerText)

            // Icon Text View
            val iconText = TextView(context).apply {
                text = " ⏱️"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            iconTextView = iconText
            container.addView(iconText)

            // Touch & Drag Handling
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isDragging = false

            container.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isDragging = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try {
                                wm.updateViewLayout(overlayView, params)
                            } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Tap event: launch CodeUnlock directly into Focus Timer screen
                            openFocusTimerScreen(context)
                        } else {
                            // Snap to nearest screen side (left or right)
                            val metrics = context.resources.displayMetrics
                            val screenWidth = metrics.widthPixels
                            val midPoint = screenWidth / 2
                            params.x = if (params.x + view.width / 2 < midPoint) {
                                dpToPx(context, 6f)
                            } else {
                                screenWidth - view.width - dpToPx(context, 6f)
                            }
                            try {
                                wm.updateViewLayout(overlayView, params)
                            } catch (_: Exception) {}
                        }
                        true
                    }
                    else -> false
                }
            }

            try {
                wm.addView(container, params)
                overlayView = container
                isShown = true
                if (isSuppressed) {
                    container.visibility = View.GONE
                }
            } catch (e: Exception) {
                isShown = false
                overlayView = null
            }
        }
    }

    fun update(timeText: String, isPaused: Boolean = false, isBreakTime: Boolean = false) {
        mainHandler.post {
            if (!isShown || overlayView == null) return@post
            if (isSuppressed) {
                overlayView?.visibility = View.GONE
            }

            timerTextView?.text = timeText

            val dotColor = when {
                isBreakTime -> Color.parseColor("#06B6D4") // Cyan for break
                isPaused -> Color.parseColor("#F59E0B")    // Amber for paused
                else -> Color.parseColor("#10B981")        // Emerald green for active
            }

            (statusDotView?.background as? GradientDrawable)?.setColor(dotColor)
            iconTextView?.text = if (isBreakTime) " ☕" else if (isPaused) " ⏸️" else " ⏱️"
        }
    }

    fun dismiss() {
        mainHandler.post {
            if (!isShown && overlayView == null) return@post
            try {
                if (overlayView?.isAttachedToWindow == true) {
                    windowManager?.removeView(overlayView)
                }
            } catch (_: Exception) {}
            overlayView = null
            timerTextView = null
            statusDotView = null
            iconTextView = null
            isShown = false
        }
    }

    private fun openFocusTimerScreen(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_INITIAL_ROUTE, AppRoute.Focus.route)
        }
        context.startActivity(intent)
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
