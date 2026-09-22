package com.example.util;

import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Helper class that monitors Android RAM usage and sends a system notification
 * whenever RAM usage exceeds 90%.
 */
public class HighMemoryNotificationHelper {

    // Unique ID and Name for the Android 8.0+ Notification Channel
    private static final String CHANNEL_ID = "high_memory_alert_channel";
    private static final String CHANNEL_NAME = "High Memory Alerts";
    private static final String CHANNEL_DESCRIPTION = "Notifications sent when RAM usage exceeds 90%";
    private static final int NOTIFICATION_ID = 1001;

    private final Context context;
    private final NotificationManager notificationManager;

    /**
     * Constructor initializing the NotificationManager and creating the Notification Channel.
     *
     * @param context Application or Activity context
     */
    public HighMemoryNotificationHelper(Context context) {
        // Step 1: Use application context to prevent memory leaks
        this.context = context.getApplicationContext();

        // Step 2: Retrieve the system NotificationManager service
        this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Step 3: Register the notification channel required for Android 8.0+ (API 26+)
        createNotificationChannel();
    }

    /**
     * Creates a NotificationChannel for Android 8.0 (API Level 26) and above.
     * Required for notifications to display on modern Android versions.
     */
    private void createNotificationChannel() {
        // Notification channels are only supported and required on Android 8.0 (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Step 1: Set importance to HIGH so the alert pops up as a heads-up notification
            int importance = NotificationManager.IMPORTANCE_HIGH;

            // Step 2: Construct the NotificationChannel with ID, name, and importance
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance);
            channel.setDescription(CHANNEL_DESCRIPTION);

            // Step 3: Register the channel with the Android system NotificationManager
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Checks current RAM usage percentage via ActivityManager.
     * If RAM usage exceeds 90%, triggers the high memory notification.
     *
     * @return true if notification was triggered (> 90%), false otherwise.
     */
    public boolean checkRamAndNotify() {
        // Step 1: Get system ActivityManager service to query RAM state
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        }

        // Step 2: Calculate total, available, and used RAM in bytes
        long totalRam = memoryInfo.totalMem;
        long availRam = memoryInfo.availMem;
        long usedRam = totalRam - availRam;

        // Step 3: Compute current RAM usage percentage
        int usedPercentage = 0;
        if (totalRam > 0) {
            usedPercentage = (int) ((usedRam * 100) / totalRam);
        }

        // Step 4: Check encrypted notification threshold from SecureStorageManager
        SecureStorageManager secureStorage = SecureStorageManager.getInstance(context);
        if (!secureStorage.isNotificationAlertsEnabled()) {
            return false; // User disabled alerts in encrypted settings
        }

        float encryptedThreshold = secureStorage.getRamWarningThresholdPercent();
        if (usedPercentage >= encryptedThreshold) {
            sendHighMemoryNotification();
            secureStorage.saveMemorySnapshot(System.currentTimeMillis(), (float) usedRam / (1024 * 1024 * 1024), (float) totalRam / (1024 * 1024 * 1024), 0, "HIGH_RAM_WARNING");
            secureStorage.addSystemLogEntry("RAM Warning Triggered: " + usedPercentage + "% used (threshold: " + encryptedThreshold + "%)");
            return true;
        }

        return false;
    }

    /**
     * Constructs and posts the system notification alerting the user of high memory usage (>90%).
     */
    public void sendHighMemoryNotification() {
        // Step 1: Check runtime notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted by user, skip posting notification
                return;
            }
        }

        // Step 2: Build the notification with required title, message, icon, and encrypted user preferences
        SecureStorageManager secureStorage = SecureStorageManager.getInstance(context);
        int defaults = 0;
        if (secureStorage.isSoundEnabled()) defaults |= NotificationCompat.DEFAULT_SOUND;
        if (secureStorage.isVibrationEnabled()) defaults |= NotificationCompat.DEFAULT_VIBRATE;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // System warning alert icon
                .setContentTitle("High Memory Usage")            // Exact title requested
                .setContentText("Your phone memory is almost full.") // Exact message requested
                .setPriority(NotificationCompat.PRIORITY_HIGH)    // High priority for Android 7.1 and lower
                .setDefaults(defaults)                            // Encrypted sound and vibration settings
                .setAutoCancel(true);                             // Automatically dismiss when tapped

        // Step 3: Dispatch the notification using NotificationManager
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }
}
