package com.digitalwellbeingguard.notification

import android.app.NotificationManager
import android.content.Context
import android.app.NotificationChannel
import android.os.Build
import androidx.core.app.NotificationCompat
import android.app.Notification
import com.digitalwellbeingguard.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "digital_wellbeing_monitor_channel_v2" // FIX: Changed ID to force update
        const val CHANNEL_NAME = "Digital Wellbeing Monitor"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT // FIX: Changed from LOW to DEFAULT
            ).apply {
                description = "Notification to keep the monitoring service alive"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Digital Wellbeing Active")
            .setContentText("Monitoring usage...")
            .setSmallIcon(R.mipmap.ic_launcher) 
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // FIX: Changed from LOW to DEFAULT
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // FIX: Added category
            .setOngoing(true)
            .build()
    }

    fun updateNotification(time: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Digital Wellbeing Active")
            .setContentText("Monitoring usage... $time")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // FIX: Changed from LOW to DEFAULT
            .setCategory(NotificationCompat.CATEGORY_SERVICE) // FIX: Added category
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
