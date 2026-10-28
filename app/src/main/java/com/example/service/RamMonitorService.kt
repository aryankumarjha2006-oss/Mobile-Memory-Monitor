package com.example.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.db.RamTriggerEntity
import com.example.data.repository.MemoryRepository
import com.example.util.NotificationHelper
import com.example.util.RamThresholdDetector
import com.example.util.RootDetectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Foreground Service responsible for continuous background RAM monitoring.
 * Polls ActivityManager.MemoryInfo every 3 seconds and triggers 60% high-RAM alert
 * workflows immediately when device RAM usage crosses the 60% threshold.
 */
class RamMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var monitorJob: Job? = null
    private val thresholdDetector = RamThresholdDetector(thresholdPercent = 60)
    private lateinit var repository: MemoryRepository

    override fun onCreate() {
        super.onCreate()
        repository = MemoryRepository(applicationContext)
        createServiceNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopMonitoringService()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        startRamPollingLoop()
        _isServiceRunning.value = true

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoringService()
    }

    private fun startForegroundNotification() {
        val persistentNotification = buildServiceNotification(0)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
                if (fgsType != 0) {
                    startForeground(NOTIFICATION_ID_SERVICE, persistentNotification, fgsType)
                } else {
                    startForeground(NOTIFICATION_ID_SERVICE, persistentNotification)
                }
            } else {
                startForeground(NOTIFICATION_ID_SERVICE, persistentNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun startRamPollingLoop() {
        if (monitorJob?.isActive == true) return

        monitorJob = serviceScope.launch {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()

            while (isActive) {
                if (activityManager != null) {
                    activityManager.getMemoryInfo(memoryInfo)
                    val totalBytes = memoryInfo.totalMem
                    val availBytes = memoryInfo.availMem

                    val usedPercent = thresholdDetector.calculateRamPercent(totalBytes, availBytes)

                    // Immediate 60% threshold crossing check
                    val shouldAlert = thresholdDetector.processReading(usedPercent)
                    if (shouldAlert) {
                        handle60PercentThresholdTrigger(usedPercent)
                    }

                    // Update persistent foreground notification content
                    updateServiceNotification(usedPercent)
                }

                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private suspend fun handle60PercentThresholdTrigger(usedPercent: Int) {
        val apps = try {
            repository.getInstalledApps()
        } catch (e: Exception) {
            emptyList()
        }

        // Exclude this app itself and system apps
        val candidateApp = apps.filter {
            it.packageName != packageName && !it.isSystemApp
        }.maxByOrNull { maxOf(it.cacheBytes, it.sizeBytes, it.lastTimeUsedMs) }

        val isRooted = RootDetectionManager.getInstance().isDeviceRooted(this)

        val appName = candidateApp?.appName ?: "Background Process"
        val appPackage = candidateApp?.packageName ?: "system.candidate"

        val outcomeName: String
        val actionAttempted: String
        val message: String

        if (isRooted && candidateApp != null) {
            actionAttempted = "Root Force Stop Attempted"
            val stopped = try {
                val process = Runtime.getRuntime().exec("su -c am force-stop ${candidateApp.packageName}")
                process.waitFor() == 0
            } catch (_: Exception) {
                false
            }
            if (stopped) {
                outcomeName = "FORCE_STOP_CONFIRMED"
                message = "RAM usage reached $usedPercent%. $appName ($appPackage) was successfully force-stopped via root privileges."
            } else {
                outcomeName = "UNABLE_TO_STOP_APP"
                message = "RAM usage reached $usedPercent%. Failed to force-stop $appName via root. Manual force-stop required."
            }
        } else {
            actionAttempted = "Manual Force Stop Required"
            outcomeName = "MANUAL_FORCE_STOP_REQUIRED"
            message = "RAM usage reached $usedPercent%. $appName ($appPackage) was identified as a candidate, but Android requires you to force-stop it manually. Tap Open App Info to continue."
        }

        val triggerEvent = RamTriggerEntity(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            usedPercent = usedPercent,
            thresholdPercent = 60,
            appName = appName,
            packageName = appPackage,
            actionAttempted = actionAttempted,
            outcomeName = outcomeName,
            message = message
        )

        try {
            AppDatabase.getInstance(this).memoryDao().insertTriggerEvent(triggerEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist RAM trigger event", e)
        }

        NotificationHelper.sendHighRamAlert(
            context = this,
            usedPercent = usedPercent,
            candidateAppName = appName,
            candidatePackageName = appPackage
        )
    }

    private fun updateServiceNotification(usedPercent: Int) {
        val notification = buildServiceNotification(usedPercent)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID_SERVICE, notification)
    }

    private fun buildServiceNotification(usedPercent: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (usedPercent > 0) {
            "Monitoring RAM in background • Current: $usedPercent% (Threshold: 60%)"
        } else {
            "Monitoring RAM in background (Threshold: 60%)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Smart RAM Monitor Active")
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Smart RAM Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification indicating active background RAM monitoring service"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopMonitoringService() {
        monitorJob?.cancel()
        monitorJob = null
        thresholdDetector.reset()
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "RamMonitorService"
        const val CHANNEL_ID_SERVICE = "ram_monitor_service_channel"
        const val NOTIFICATION_ID_SERVICE = 8001
        const val POLLING_INTERVAL_MS = 3000L
        const val ACTION_STOP_SERVICE = "com.example.service.ACTION_STOP_RAM_MONITOR"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, RamMonitorService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start RamMonitorService", e)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RamMonitorService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                context.stopService(Intent(context, RamMonitorService::class.java))
            }
        }
    }
}
