package com.digitalwellbeingguard.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.digitalwellbeingguard.monitor.AppMonitorService
import com.digitalwellbeingguard.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PermissionState(
    val usageAccess: Boolean = false,
    val overlay: Boolean = false,
    val batteryOptimization: Boolean = false,
    val notification: Boolean = false
)

class MainViewModel : ViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val _hasPin = MutableStateFlow(false)
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()


    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _usageList = MutableStateFlow<List<com.digitalwellbeingguard.data.AppUsage>>(emptyList())
    val usageList: StateFlow<List<com.digitalwellbeingguard.data.AppUsage>> = _usageList.asStateFlow()

    private val usageRepository = com.digitalwellbeingguard.data.UsageRepository()
    
    // Feature 2: Dynamic Interval
    enum class WarningInterval(val millis: Long, val label: String) {
        SEC_30(30_000L, "30 Seconds"),
        MIN_1(60_000L, "1 Minute"),
        MIN_2(120_000L, "2 Minutes"),
        MIN_3(180_000L, "3 Minutes"),
        MIN_4(240_000L, "4 Minutes"),
        MIN_5(300_000L, "5 Minutes");
        
        companion object {
            fun fromMillis(millis: Long): WarningInterval {
                return entries.find { it.millis == millis } ?: MIN_5
            }
        }
    }
    
    private val _selectedInterval = MutableStateFlow(WarningInterval.MIN_5)
    val selectedInterval: StateFlow<WarningInterval> = _selectedInterval.asStateFlow()
    
        // Load saved interval on init
    fun loadSettings(context: Context) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val savedMillis = prefs.getLong("warning_interval", 300_000L)
        _selectedInterval.value = WarningInterval.fromMillis(savedMillis)
        
        loadPinState(context)

        // Load usage data
        loadUsageData(context)
    }

    fun loadPinState(context: Context) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val savedPin = prefs.getString("app_pin", null)
        _hasPin.value = !savedPin.isNullOrEmpty()
        if (_hasPin.value) {
            _isAppUnlocked.value = false // Lock app initially if pin exists
        } else {
            _isAppUnlocked.value = true // Unlocked if no pin
        }
    }

    fun setPin(context: Context, pin: String) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString("app_pin", pin).apply()
        _hasPin.value = true
        _isAppUnlocked.value = true
    }

    fun verifyAppPin(context: Context, pin: String): Boolean {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val savedPin = prefs.getString("app_pin", null)
        if (savedPin == pin) {
            _isAppUnlocked.value = true
            return true
        }
        return false
    }

    fun setWarningInterval(context: Context, interval: WarningInterval) {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putLong("warning_interval", interval.millis).apply()
        _selectedInterval.value = interval
        
        // Restart service if running to apply new interval
        if (isServiceRunning(context)) {
            stopMonitoring(context)
            // Small delay to ensure clean stop
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startMonitoring(context)
            }, 500)
        }
    }
    
    fun loadUsageData(context: Context) {
        // Run in background
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val usage = usageRepository.getAppsUsedMoreThan(context, 5 * 60 * 1000L) // 5 mins
            _usageList.value = usage
        }
    }

    private var permissionManager: PermissionManager? = null

    private fun getPermissionManager(context: Context): PermissionManager {
        return permissionManager ?: PermissionManager(context).also { permissionManager = it }
    }

    fun refreshPermissionState(context: Context) {
        val manager = getPermissionManager(context)
        _permissionState.value = PermissionState(
            usageAccess = manager.hasUsageAccess(context),
            overlay = manager.hasOverlayPermission(context),
            batteryOptimization = manager.isIgnoringBatteryOptimizations(context),
            notification = manager.hasNotificationPermission(context)
        )
        _isMonitoring.value = isServiceRunning(context)
        
        // Load saved interval
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val savedMillis = prefs.getLong("warning_interval", 300_000L)
        _selectedInterval.value = WarningInterval.fromMillis(savedMillis)

        loadPinState(context)
        
        // Also refresh usage data when resuming
        loadUsageData(context)
    }

    fun openUsageAccessSettings(context: Context) {
        getPermissionManager(context).openUsageAccessSettings(context)
    }

    fun openOverlaySettings(context: Context) {
        getPermissionManager(context).openOverlaySettings(context)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        getPermissionManager(context).openBatteryOptimizationSettings(context)
    }
    
    fun requestNotificationPermission(activity: android.app.Activity) {
        getPermissionManager(activity).requestNotificationPermission(activity)
    }

    fun canStartMonitoring(): Boolean {
        val state = _permissionState.value
        return state.usageAccess && state.overlay && state.batteryOptimization && state.notification
    }
    
    fun toggleMonitoring(context: Context) {
        if (_isMonitoring.value) {
            stopMonitoring(context)
        } else {
            startMonitoring(context)
        }
    }

    private fun startMonitoring(context: Context) {
        refreshPermissionState(context)
        if (canStartMonitoring()) {
            val intent = Intent(context, AppMonitorService::class.java)
            // FIX: Use ContextCompat for compatibility and safety
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
            _isMonitoring.value = true
        } else {
            Toast.makeText(context, "Grant all permissions first", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopMonitoring(context: Context) {
        val intent = Intent(context, AppMonitorService::class.java)
        context.stopService(intent)
        _isMonitoring.value = false
    }
    
    @Suppress("DEPRECATION") // getRunningServices is deprecated but still works for own services to check existence
    private fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (AppMonitorService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
