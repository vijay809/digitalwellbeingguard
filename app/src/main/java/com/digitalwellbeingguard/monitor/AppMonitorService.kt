package com.digitalwellbeingguard.monitor

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.digitalwellbeingguard.notification.NotificationHelper
import com.digitalwellbeingguard.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppMonitorService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var overlayManager: OverlayManager
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var lastApp: String? = null
    private var warningIntervalMs: Long = 60_000L // Default 1 min if prefs fail
    private var monitoredApps: Set<String> = emptySet()

    private val sessionStates = mutableMapOf<String, AppSessionState>()
    private var sessionTimeoutMs: Long = 60 * 60 * 1000L // Default 60 minutes

    data class AppSessionState(
        var accumulatedTimeMs: Long = 0L,
        var lastActiveTime: Long = 0L,
        var warningCount: Int = 0,
        var nextTriggerTimeMs: Long = 0L,
        var warningTriggered: Boolean = false
    )

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()
        overlayManager = OverlayManager(this)
        
        // Load interval preference
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        warningIntervalMs = prefs.getLong("warning_interval", 300_000L) // Default 5 mins (300,000)
        sessionTimeoutMs = prefs.getLong("refresh_interval", 60 * 60_000L) // Default 60 mins
        monitoredApps = prefs.getStringSet("explicit_monitored_apps", emptySet()) ?: emptySet()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.buildForegroundNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
        
        startMonitoringLoop()
        
        return START_STICKY
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, AppMonitorService::class.java)
        restartIntent.setPackage(packageName)
        startForegroundService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }
    
    private fun startMonitoringLoop() {
        serviceScope.launch {
            var lastLoopTime = System.currentTimeMillis()
            
            while (isActive) {
                val now = System.currentTimeMillis()
                val delta = now - lastLoopTime
                lastLoopTime = now
                
                val foregroundApp = getForegroundApp()
                
                // Cleanup old sessions
                val iterator = sessionStates.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.key != foregroundApp && (now - entry.value.lastActiveTime) > sessionTimeoutMs) {
                        Log.d("AppMonitor", "Session expired for ${entry.key}")
                        iterator.remove()
                    }
                }
                
                if (foregroundApp != null && isMonitoredApp(foregroundApp)) {
                    val state = sessionStates.getOrPut(foregroundApp) {
                        Log.d("AppMonitor", "Session started for: $foregroundApp. Interval: ${warningIntervalMs}ms")
                        AppSessionState(
                            accumulatedTimeMs = 0L,
                            lastActiveTime = now,
                            warningCount = 0,
                            nextTriggerTimeMs = warningIntervalMs,
                            warningTriggered = false
                        )
                    }
                    
                    if (lastApp != foregroundApp) {
                        Log.d("AppMonitor", "Resuming session for: $foregroundApp")
                        lastApp = foregroundApp
                    }
                    
                    // Accumulate time (cap delta at 5000ms to avoid sleep jumps)
                    if (delta in 0..5000) {
                        state.accumulatedTimeMs += delta
                    }
                    state.lastActiveTime = now

                    val formattedTime = formatTime(state.accumulatedTimeMs)
                    notificationHelper.updateNotification(formattedTime)

                    if (overlayManager.isOverlayShowing()) {
                        launch(Dispatchers.Main) {
                            overlayManager.updateTimer(formattedTime)
                        }
                    }

                    // Check trigger
                    if (state.accumulatedTimeMs >= state.nextTriggerTimeMs && !state.warningTriggered) {
                        state.warningTriggered = true
                        launch(Dispatchers.Main) {
                            if (!overlayManager.isOverlayShowing()) {
                                val label = getIntervalLabel(warningIntervalMs)
                                val message = "You've been using apps for $label."
                                val delaySeconds = state.warningCount * 10
                                overlayManager.showOverlay(formattedTime, message, delaySeconds) { isExcluded ->
                                    if (isExcluded) {
                                        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this@AppMonitorService)
                                        val excluded = prefs.getStringSet("explicit_excluded_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
                                        excluded.add(foregroundApp)
                                        
                                        val monitored = prefs.getStringSet("explicit_monitored_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
                                        monitored.remove(foregroundApp)
                                        
                                        prefs.edit()
                                            .putStringSet("explicit_excluded_apps", excluded)
                                            .putStringSet("explicit_monitored_apps", monitored)
                                            .apply()
                                            
                                        monitoredApps = monitored
                                        lastApp = null
                                        sessionStates.remove(foregroundApp)
                                        notificationHelper.updateNotification("Monitoring app usage...")
                                    } else {
                                        state.warningTriggered = false
                                        state.warningCount++
                                        state.nextTriggerTimeMs = state.accumulatedTimeMs + warningIntervalMs
                                    }
                                }
                            }
                        }
                    }
                } else if (foregroundApp == packageName) {
                    // Ignore self
                } else {
                    if (lastApp != null) {
                        notificationHelper.updateNotification("Monitoring app usage...")
                        Log.d("AppMonitor", "Session backgrounded")
                    }
                    lastApp = null 
                }

                delay(1000)
            }
        }
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000 

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()
        
        var latestPackage: String? = null
        var latestTime = 0L
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (event.timeStamp > latestTime) {
                    latestTime = event.timeStamp
                    latestPackage = event.packageName
                }
            }
        }
        
        return latestPackage
    }

    private fun isMonitoredApp(packageName: String): Boolean {
        return monitoredApps.contains(packageName) &&
               packageName != this.packageName && 
               packageName != "com.android.systemui"
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun getIntervalLabel(millis: Long): String {
        return when (millis) {
            30_000L -> "30 seconds"
            60_000L -> "1 minute"
            120_000L -> "2 minutes"
            180_000L -> "3 minutes"
            240_000L -> "4 minutes"
            300_000L -> "5 minutes"
            else -> "${millis / 60_000L} minutes"
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        overlayManager.removeOverlay()
    }
}
