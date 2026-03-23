package com.raven.veto.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.raven.veto.R
import com.raven.veto.data.AnkiRepository
import com.raven.veto.data.AppRepository
import com.raven.veto.ui.screens.BlockingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("AccessibilityPolicy")
@AndroidEntryPoint
class VetoAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var ankiRepository: AnkiRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var totalDueCards = Int.MAX_VALUE // Default to max so we block until loaded

    // Removed usageJob and disconnectJob variables that are no longer used the same way
    // or simplified.
    private var usageJob: kotlinx.coroutines.Job? = null
    private var disconnectJob: kotlinx.coroutines.Job? = null
    private var debounceJob: kotlinx.coroutines.Job? = null
    private var currentBlockedPackage: String? = null
    private var currentWindowPackage: String? = null
    private var lastBlockTime: Long = 0

    private val notificationId = 1001
    private val channelId = "veto_usage_channel"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VetoAccessibility", "Service connected")
        createNotificationChannel()

        // Initialize with cached stats immediately to avoid false positive blocks on startup
        val cachedStats = ankiRepository.getCachedStats()
        totalDueCards = cachedStats.totalDue
        // reviewsToday is removed from stats
        Log.d("VetoAccessibility", "Initialized with cached stats: Due=$totalDueCards")

        serviceScope.launch {
            // AnkiRepository now updates AppRepository balance automatically.
            // We just need to track totalDueCards for the 'block immediately if <= 0 balance' logic
            ankiRepository.getAnkiStats().collectLatest { stats ->
                totalDueCards = stats.totalDue
                Log.d("VetoAccessibility", "Stats updated: Due=$totalDueCards")

                updatePersistentNotification()

                // Re-evaluate if we need to block/unblock current window
                withContext(Dispatchers.Main) {
                    currentWindowPackage?.let { pkg ->
                        checkAndHandleBlock(pkg)
                    }
                }
            }
        }

        serviceScope.launch {
            appRepository.availableTimeFlow.collectLatest {
                updatePersistentNotification()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // 1. Detect if we are in AnkiDroid and something happened (e.g. content changed, clicked)
        //    This means user likely answered a card.
        if (packageName == "com.ichi2.anki") {
            // Filter relevant events to avoid excessive triggers
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                scheduleAnkiUpdate()
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Track raw window state first
            if (packageName != this.packageName) {
                currentWindowPackage = packageName
                // If we just switched TO Anki, also schedule an update to be sure
                if (packageName == "com.ichi2.anki") {
                    scheduleAnkiUpdate()
                }
            }

            // Ignore system packages and Veto itself for blocking logic
            if (packageName == this.packageName) return

            checkAndHandleBlock(packageName)
        }
    }

    private fun scheduleAnkiUpdate() {
        // Debounce: cancel previous pending update, start new one
        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            // Wait for animations/DB writes to settle
            kotlinx.coroutines.delay(500L)

            // Perform the check
            val stats = ankiRepository.forceCheck()
            if (totalDueCards != stats.totalDue) {
                totalDueCards = stats.totalDue
                // updatePersistentNotification() is called inside checks/flows usually,
                // but let's force it here too just in case.
                withContext(Dispatchers.Main) {
                    updatePersistentNotification()
                }
            }
        }
    }

    private fun checkAndHandleBlock(packageName: String) {
        val blockedApps = appRepository.blockedAppsFlow.value
        val isBlockedApp = blockedApps.contains(packageName)

        if (isBlockedApp) {
            cancelDisconnectTimer()
            currentBlockedPackage = packageName

            // Check Available Balance
            val availableMillis = appRepository.getAvailableTimeMillis()

            if (availableMillis < 5000L) { // Allow tiny threshold (< 5s)
                // Quota exceeded or empty
                stopUsageTimer()
                blockApp(packageName, totalDueCards, availableMillis.toInt() / 60000)
            } else {
                // Has balance, start consuming IF this is actually the current top window
                // (Prevents starting timer while in Anki just because we checked state)
                if (currentWindowPackage == packageName) {
                    startUsageTimer(packageName)
                }
            }
        } else {
            // Not a blocked app - wait before stopping timer (grace period)
            startDisconnectTimer()
        }
    }

    private fun startUsageTimer(packageName: String) {
        // If already tracking this package, do nothing
        if (usageJob?.isActive == true && currentBlockedPackage == packageName) return

        // If tracking another package, stop it first
        stopUsageTimer()

        Log.d("VetoAccessibility", "Starting usage timer for $packageName")
        currentBlockedPackage = packageName

        // Initial notification update handled by flow, but ensure we are fresh
        updatePersistentNotification()

        usageJob = serviceScope.launch {
            val updateInterval = 5000L // 5 seconds

            while (true) {
                // Check usage rapidly
                kotlinx.coroutines.delay(updateInterval)

                // Stop if window changed to something decidedly different
                // Note: currentBlockedPackage is maintained by checkAndHandleBlock
                // But we should also respect currentWindowPackage to be safe
                if (currentWindowPackage != packageName && currentWindowPackage != null) {
                    // Grace period handled by disconnect timer, but if we are here,
                    // it means we are consuming time while potentially not in app.
                    // Let's rely on standard logic: checkAndHandleBlock handles switching.
                    Log.d("VetoAccessibility", "Usage timer detected window change to $currentWindowPackage, assuming handled by events.")
                }

                // Still in blocked app?
                if (currentBlockedPackage != packageName) break

                // Consume time
                appRepository.consumeTime(updateInterval)

                val availableMillis = appRepository.getAvailableTimeMillis()

                // Notification updates via flow observation in onServiceConnected

                if (availableMillis <= 0) {
                    // Time's up!
                    // Notification will be updated by flow (to 0 mins)
                    withContext(Dispatchers.Main) {
                        blockApp(
                            packageName,
                            totalDueCards,
                            0 // 0 minutes available
                        )
                    }
                    stopUsageTimer()
                    break
                }
            }
        }
    }

    private fun updatePersistentNotification() {
        if (totalDueCards == 0) {
            cancelNotification()
            return
        }

        val availableMillis = appRepository.getAvailableTimeMillis()
        val minutesLeft = (availableMillis / 60000).toInt()
        // Show " < 1 minute" if strict 0 minutes but some seconds left
        val displayMinutes = if (minutesLeft == 0 && availableMillis > 0) 1 else minutesLeft

        updateNotification(displayMinutes, totalDueCards)
    }

    private fun startDisconnectTimer() {
        if (disconnectJob?.isActive == true) return
        if (usageJob?.isActive != true) return // Nothing to disconnect

        Log.d("VetoAccessibility", "Starting disconnect timer")
        disconnectJob = serviceScope.launch {
            kotlinx.coroutines.delay(10000L) // 10 seconds grace period for transient windows (system UI, etc)
            stopUsageTimer()
            currentBlockedPackage = null
        }
    }

    private fun cancelDisconnectTimer() {
        if (disconnectJob?.isActive == true) {
            disconnectJob?.cancel()
            disconnectJob = null
            Log.d("VetoAccessibility", "Disconnect timer cancelled")
        }
    }

    private fun stopUsageTimer() {
        cancelDisconnectTimer()
        if (usageJob?.isActive == true) {
            Log.d("VetoAccessibility", "Stopping usage timer")
            usageJob?.cancel()
            usageJob = null
            // Do not cancel notification here, it should be persistent if cards are due
        }
    }

    private fun createNotificationChannel() {
        val name = "Veto Usage Tracking"
        val descriptionText = "Shows remaining time for blocked apps"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification(minutesLeft: Int, cardsDue: Int) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val contentText = "Time: ${minutesLeft}m available | Due: $cardsDue cards"

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Fallback icon
            .setContentTitle("Veto Active")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            Log.e("VetoAccessibility", "Notification permission not granted")
        }
    }

    private fun cancelNotification() {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    private fun blockApp(packageName: String, due: Int, availableMinutes: Int) {
        val now = java.time.Instant.now().toEpochMilli()
        if (now - lastBlockTime < 1000) {
            Log.d("VetoAccessibility", "Debouncing block for $packageName")
            return
        }
        lastBlockTime = now

        Log.d(
            "VetoAccessibility",
            "Blocking: $packageName. Due: $due, Available Minutes: $availableMinutes"
        )

        val intent =
            BlockingActivity.createIntent(applicationContext, packageName, due)
        intent.flags =
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d("VetoAccessibility", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
