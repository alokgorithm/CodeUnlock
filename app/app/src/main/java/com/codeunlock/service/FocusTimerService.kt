package com.codeunlock.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.codeunlock.R
import com.codeunlock.audio.AmbientSoundManager
import com.codeunlock.data.focus.FocusSessionRecord
import com.codeunlock.data.focus.FocusSessionStore
import com.codeunlock.data.notification.NotificationHelper
import com.codeunlock.data.unlock.UnlockSessionStore
import com.codeunlock.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FocusTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var targetEndTimeMillis: Long = 0L
    private var pausedRemainingMillis: Long = 0L
    private var startTimestampMillis: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CodeUnlock:FocusTimerWakeLock")
        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours safety cap
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val durationSec = intent.getLongExtra(EXTRA_DURATION_SECONDS, 25 * 60L)
                val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_POMODORO
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "Dynamic Programming"
                startTimer(durationSec, mode, subject)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }

        return START_STICKY
    }

    private fun startTimer(durationSec: Long, mode: String, subject: String) {
        _activeMode.value = mode
        _activeSubject.value = subject
        _isPaused.value = false
        _isBreakTime.value = false
        _totalDurationSeconds.value = durationSec
        _remainingSeconds.value = durationSec
        _elapsedSeconds.value = 0L

        startTimestampMillis = System.currentTimeMillis()
        targetEndTimeMillis = SystemClock.elapsedRealtime() + (durationSec * 1000L)
        _isRunning.value = true

        startForeground(NotificationHelper.ID_FOCUS_TIMER, buildNotification("Focusing: $subject", formatTime(durationSec)))

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && _isRunning.value) {
                delay(1000L)
                if (!_isPaused.value) {
                    val now = SystemClock.elapsedRealtime()
                    val remainingMs = targetEndTimeMillis - now
                    val remainingSec = (remainingMs / 1000L).coerceAtLeast(0L)
                    _remainingSeconds.value = remainingSec
                    _elapsedSeconds.value = (_totalDurationSeconds.value - remainingSec).coerceAtLeast(0L)

                    updateNotification("Focusing: ${_activeSubject.value}", formatTime(remainingSec))

                    if (remainingSec <= 0L) {
                        handleTimerFinished()
                        break
                    }
                }
            }
        }
    }

    private fun handleTimerFinished() {
        // Record completed focus session
        FocusSessionStore.recordSession(
            context = applicationContext,
            session = FocusSessionRecord(
                mode = _activeMode.value,
                subjectTag = _activeSubject.value,
                durationSeconds = _totalDurationSeconds.value
            )
        )

        if (_activeMode.value == MODE_POMODORO && !_isBreakTime.value) {
            // Enter 5-minute break mode!
            _isBreakTime.value = true
            _totalDurationSeconds.value = 5 * 60L
            _remainingSeconds.value = 5 * 60L
            targetEndTimeMillis = SystemClock.elapsedRealtime() + (5 * 60 * 1000L)

            // Grant 5 minutes free break time
            UnlockSessionStore.grantCodingUnlock(applicationContext, null, 5)

            updateNotification("Break Time! ☕", "5:00 — Phone Unlocked for Break")

            serviceScope.launch {
                while (isActive && _isBreakTime.value) {
                    delay(1000L)
                    val now = SystemClock.elapsedRealtime()
                    val remainingSec = ((targetEndTimeMillis - now) / 1000L).coerceAtLeast(0L)
                    _remainingSeconds.value = remainingSec
                    updateNotification("Break Time! ☕", formatTime(remainingSec))
                    if (remainingSec <= 0L) {
                        stopTimer()
                        break
                    }
                }
            }
        } else {
            stopTimer()
        }
    }

    private fun pauseTimer() {
        if (!_isRunning.value || _isPaused.value) return
        _isPaused.value = true
        pausedRemainingMillis = targetEndTimeMillis - SystemClock.elapsedRealtime()
        updateNotification("Paused: ${_activeSubject.value}", formatTime(_remainingSeconds.value))
    }

    private fun resumeTimer() {
        if (!_isRunning.value || !_isPaused.value) return
        _isPaused.value = false
        targetEndTimeMillis = SystemClock.elapsedRealtime() + pausedRemainingMillis
        updateNotification("Focusing: ${_activeSubject.value}", formatTime(_remainingSeconds.value))
    }

    private fun stopTimer() {
        _isRunning.value = false
        _isPaused.value = false
        _isBreakTime.value = false
        _remainingSeconds.value = 0L
        _elapsedSeconds.value = 0L
        timerJob?.cancel()

        AmbientSoundManager.stopSound()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(title: String, timeText: String) {
        val notification = buildNotification(title, timeText)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NotificationHelper.ID_FOCUS_TIMER, notification)
    }

    private fun buildNotification(title: String, timeText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (_isPaused.value) {
            val resumeIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_RESUME }
            val resumePendingIntent = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(0, "Resume", resumePendingIntent).build()
        } else {
            val pauseIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(0, "Pause", pausePendingIntent).build()
        }

        val stopIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopAction = NotificationCompat.Action.Builder(0, "Stop", stopPendingIntent).build()

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_FOCUS_TIMER)
            .setContentTitle(title)
            .setContentText("Remaining: $timeText")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun formatTime(sec: Long): String {
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
    }

    companion object {
        const val ACTION_START = "com.codeunlock.action.START_FOCUS"
        const val ACTION_PAUSE = "com.codeunlock.action.PAUSE_FOCUS"
        const val ACTION_RESUME = "com.codeunlock.action.RESUME_FOCUS"
        const val ACTION_STOP = "com.codeunlock.action.STOP_FOCUS"

        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_SUBJECT = "extra_subject"

        const val MODE_POMODORO = "pomodoro"
        const val MODE_DEEP_FOCUS = "deep_focus"
        const val MODE_STOPWATCH = "stopwatch"

        // Observable state for UI
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        private val _isBreakTime = MutableStateFlow(false)
        val isBreakTime: StateFlow<Boolean> = _isBreakTime.asStateFlow()

        private val _remainingSeconds = MutableStateFlow(0L)
        val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

        private val _elapsedSeconds = MutableStateFlow(0L)
        val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

        private val _totalDurationSeconds = MutableStateFlow(0L)
        val totalDurationSeconds: StateFlow<Long> = _totalDurationSeconds.asStateFlow()

        private val _activeMode = MutableStateFlow(MODE_POMODORO)
        val activeMode: StateFlow<String> = _activeMode.asStateFlow()

        private val _activeSubject = MutableStateFlow("Dynamic Programming")
        val activeSubject: StateFlow<String> = _activeSubject.asStateFlow()

        fun start(context: Context, durationSec: Long, mode: String, subject: String) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION_SECONDS, durationSec)
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_SUBJECT, subject)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).apply { action = ACTION_RESUME }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
