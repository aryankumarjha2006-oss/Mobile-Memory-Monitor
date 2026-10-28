package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

/**
 * Utility class to post system alerts when RAM usage crosses the 60% threshold.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "ram_monitor_alerts_channel"
    private const val NOTIFICATION_ID = 9001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "High RAM Alert Channel"
            val descriptionText = "Immediate alerts when overall device RAM usage crosses 60%"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendHighRamAlert(context: Context, usedPercent: Int, candidateAppName: String? = null, candidatePackageName: String? = null) {
        createNotificationChannel(context)

        val intent = if (!candidatePackageName.isNullOrEmpty()) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$candidatePackageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_OPEN_TAB", 0)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "High RAM Usage Detected (${usedPercent}%)"
        val bodyText = if (!candidateAppName.isNullOrEmpty()) {
            "RAM usage reached $usedPercent%. Candidate: $candidateAppName. Tap to open App Info and manage."
        } else {
            "Your device RAM usage has exceeded 60%. Current usage: $usedPercent%."
        }

        val bigText = if (!candidateAppName.isNullOrEmpty()) {
            "RAM usage reached $usedPercent%. $candidateAppName ($candidatePackageName) was identified as a candidate, but Android requires you to force-stop it manually. Tap Open App Info to continue."
        } else {
            "Your device RAM usage has exceeded 60%. Current usage: $usedPercent%.\nHigh overall RAM usage indicates elevated system load. Tap to view memory status or manage applications."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // Permission POST_NOTIFICATIONS missing or denied
        }
    }
}
