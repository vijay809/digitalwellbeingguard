package com.digitalwellbeingguard.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.Calendar

data class AppUsage(
    val packageName: String,
    val appName: String,
    val totalTime: Long,
    val appIcon: Drawable?
)

class UsageRepository {

    fun getAppsUsedMoreThan(context: Context, durationMillis: Long): List<AppUsage> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStatsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        
        return usageStatsMap.values
            .filter { it.totalTimeInForeground > durationMillis }
            .mapNotNull { stats ->
                val packageName = stats.packageName
                if (shouldIncludeApp(packageName, context)) {
                    val appUsage = try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val icon = packageManager.getApplicationIcon(appInfo)
                        
                        AppUsage(packageName, appName, stats.totalTimeInForeground, icon)
                    } catch (e: PackageManager.NameNotFoundException) {
                        // Fallback if package not found (rare for usage stats)
                         AppUsage(packageName, packageName, stats.totalTimeInForeground, null)
                    }
                    appUsage
                } else {
                    null
                }
            }
            .sortedByDescending { it.totalTime }
    }

    private fun shouldIncludeApp(packageName: String, context: Context): Boolean {
        // Exclude self
        if (packageName == context.packageName) return false
        // Exclude system UI
        if (packageName == "com.android.systemui") return false
        // Exclude Launcher
        return !isLauncher(packageName, context)
    }

    private fun isLauncher(packageName: String, context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }
}
