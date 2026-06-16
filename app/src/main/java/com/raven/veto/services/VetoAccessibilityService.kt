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
import com.raven.veto.domain.ConsumeTimeUseCase
import com.raven.veto.domain.GetAvailableTimeUseCase
import com.raven.veto.domain.GetBlockedAppsUseCase
import com.raven.veto.ui.screens.BlockingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.provider.Settings
import com.raven.veto.data.local.PreferencesManager
import com.raven.veto.data.local.VetoDao

@SuppressLint("AccessibilityPolicy")
@AndroidEntryPoint
class VetoAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var consumeTimeUseCase: ConsumeTimeUseCase

    @Inject
    lateinit var getAvailableTimeUseCase: GetAvailableTimeUseCase

    @Inject
    lateinit var vetoDao: VetoDao

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var getBlockedAppsUseCase: GetBlockedAppsUseCase

    @Inject
    lateinit var ankiRepository: AnkiRepository
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var totalDueCards = Int.MAX_VALUE // Default to max so we block until loaded

    @Volatile
    private var currentAvailableMillis = 0L

    @Volatile
    private var currentBlockedApps: Set<String> = emptySet()

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

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var lastWarnedMinute: Int = -1
    private var strictModeEnabled: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VetoAccessibility", "Service connected")
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Initialize with cached stats immediately to avoid false positive blocks on startup
        val cachedStats = ankiRepository.getCachedStats()
        totalDueCards = cachedStats.totalDue
        // reviewsToday is removed from stats
        Log.d("VetoAccessibility", "Initialized with cached stats: Due=$totalDueCards")

        // Initial check and periodic hourly check for daily allowance
        serviceScope.launch {
            while (true) {
                preferencesManager.applyDailyAllowanceIfNeeded()
                kotlinx.coroutines.delay(60 * 60 * 1000L) // 1 hour
            }
        }

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
            getAvailableTimeUseCase().collectLatest { millis ->
                currentAvailableMillis = millis
                updatePersistentNotification()
            }
        }

        serviceScope.launch {
            getBlockedAppsUseCase().collectLatest { apps ->
                currentBlockedApps = apps
            }
        }

        serviceScope.launch {
            preferencesManager.strictModeFlow.collectLatest { enabled ->
                strictModeEnabled = enabled
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
            val packageName = event.packageName?.toString() ?: return
            
            // Settings Blockade (Strict Mode)
            if (packageName == "com.android.settings" && strictModeEnabled && totalDueCards > 0) {
                val allText = StringBuilder()
                fun collectAllText(node: android.view.accessibility.AccessibilityNodeInfo?) {
                    if (node == null) return
                    node.text?.let { allText.append(it).append(" ") }
                    node.contentDescription?.let { allText.append(it).append(" ") }
                    for (i in 0 until node.childCount) {
                        collectAllText(node.getChild(i))
                    }
                }
                collectAllText(rootInActiveWindow)
                event.text.forEach { allText.append(it).append(" ") }
                
                val textLower = allText.toString().lowercase()
                val hasVeto = textLower.contains("veto")
                
                val isAppInfo = hasVeto && (textLower.contains("force stop") || textLower.contains("uninstall") || textLower.contains("clear data"))
                val isDeviceAdmin = hasVeto && (textLower.contains("deactivate") || textLower.contains("remove active admin"))
                val isAccessibility = hasVeto && (textLower.contains("use veto") || textLower.contains("stop veto"))

                if (isAppInfo || isDeviceAdmin || isAccessibility) {
                    Log.d("VetoAccessibility", "Strict Mode blocked Settings access to Veto")
                    android.widget.Toast.makeText(this, "Strict Mode: Finish Anki cards first!", android.widget.Toast.LENGTH_SHORT).show()
                    blockApp(packageName, totalDueCards, 0, isStrictModeBlock = true)
                    return
                }
            }

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
        val isBlockedApp = currentBlockedApps.contains(packageName)

        if (isBlockedApp) {
            cancelDisconnectTimer()
            currentBlockedPackage = packageName

            // Check Available Balance
            val availableMillis = currentAvailableMillis

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
                consumeTimeUseCase(packageName, updateInterval)

                val availableMillis = currentAvailableMillis
                val profile = vetoDao.getAppProfile(packageName)
                val multiplier = profile?.costMultiplier ?: 1.0f
                val effectiveMinutesLeft = (availableMillis / (multiplier * 60000)).toInt()

                if (effectiveMinutesLeft in 1..5) {
                    withContext(Dispatchers.Main) {
                        showCatWarning(effectiveMinutesLeft)
                    }
                }

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

        val availableMillis = currentAvailableMillis
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
            lastWarnedMinute = -1 // Reset warning state
            withContext(Dispatchers.Main) {
                removeCatWarning()
            }
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

    private fun blockApp(packageName: String, due: Int, availableMinutes: Int, isStrictModeBlock: Boolean = false) {
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
            BlockingActivity.createIntent(applicationContext, packageName, due, isStrictModeBlock)
        intent.flags =
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        removeCatWarning()
        lastWarnedMinute = -1
    }

    private fun showCatWarning(minutesLeft: Int) {
        if (!Settings.canDrawOverlays(this)) return

        // Prevent showing the same warning multiple times for the same minute
        if (lastWarnedMinute == minutesLeft) return
        lastWarnedMinute = minutesLeft

        // Remove existing if any
        removeCatWarning()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        val density = resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = dp(16) // Margin right
        params.y = dp(64) // Margin bottom

        // Create Layout Programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#E61A1A1A")) // 90% opaque dark background
            cornerRadius = dp(24).toFloat()
        }
        layout.background = bg

        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.cute_cat_warning)
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply {
                bottomMargin = dp(12)
            }
        }

        val textView = TextView(this).apply {
            text = "Meow! Only ${minutesLeft}m left!"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setSingleLine(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        layout.addView(imageView)
        layout.addView(textView)

        overlayView = layout
        
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e("VetoAccessibility", "Failed to add overlay", e)
        }

        // Dismiss after 3.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            removeCatWarning()
        }, 3500)
    }

    private fun removeCatWarning() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore if not attached
            }
            overlayView = null
        }
    }

    override fun onInterrupt() {
        Log.d("VetoAccessibility", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
