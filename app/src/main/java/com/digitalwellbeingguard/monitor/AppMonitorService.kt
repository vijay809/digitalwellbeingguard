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
    private var sessionStartTime: Long = 0L
    private var warningTriggered = false
    private var warningIntervalMs: Long = 60_000L // Default 1 min if prefs fail
    private var nextTriggerTime: Long = 0L
    private var warningCount = 0

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()
        overlayManager = OverlayManager(this)
        
        // Load interval preference
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        warningIntervalMs = prefs.getLong("warning_interval", 300_000L) // Default 5 mins (300,000)
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
            sessionStartTime = System.currentTimeMillis()
            nextTriggerTime = warningIntervalMs // First trigger target
            
            while (isActive) {
                val foregroundApp = getForegroundApp()
                
                if (foregroundApp != null && foregroundApp != lastApp) {
                    if (foregroundApp == packageName) {
                        // Ignore self - continue previous session
                        Log.d("AppMonitor", "Ignoring self: $packageName")
                    } else if (isMonitoredApp(foregroundApp)) {
                        // App changed to another monitored app (or new session)
                        lastApp = foregroundApp
                        sessionStartTime = System.currentTimeMillis()
                        // Reset trigger logic for new session
                        nextTriggerTime = warningIntervalMs
                        warningTriggered = false
                        warningCount = 0
                        
                        Log.d("AppMonitor", "Session started for: $foregroundApp. Interval: ${warningIntervalMs}ms")
                    } else {
                        lastApp = null 
                        Log.d("AppMonitor", "Session ended (Launcher/System/Excluded)")
                    }
                }

                if (lastApp != null) {
                    val elapsedTime = System.currentTimeMillis() - sessionStartTime
                    val formattedTime = formatTime(elapsedTime)
                    notificationHelper.updateNotification(formattedTime)

                    if (overlayManager.isOverlayShowing()) {
                        launch(Dispatchers.Main) {
                            overlayManager.updateTimer(formattedTime)
                        }
                    }

                    // Check against nextTriggerTime
                    if (elapsedTime >= nextTriggerTime && !warningTriggered) {
                         launch(Dispatchers.Main) {
                             if (!overlayManager.isOverlayShowing()) {
                                  val label = getIntervalLabel(warningIntervalMs)
                                  val message = "You've been using apps for $label."
                                  val delaySeconds = warningCount * 10
                                  overlayManager.showOverlay(formattedTime, message, delaySeconds) {
                                      // On Continue Clicked
                                      warningTriggered = false
                                      warningCount++
                                      // DO NOT Reset sessionStartTime
                                      
                                      // Catch up if multiple intervals passed (e.g. ignored for long time)
                                      while (nextTriggerTime <= elapsedTime) {
                                          nextTriggerTime += warningIntervalMs
                                      }
                                  }
                             }
                         }
                         warningTriggered = true
                    }
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
        return packageName != this.packageName && 
               !isLauncher(packageName) && 
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
