package com.example.util;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;

/**
 * Reusable utility class to monitor Android RAM usage in real time.
 */
public class MemoryUtils {

    /**
     * Data holder class containing real-time memory metrics in bytes and percentage.
     */
    public static class RamStats {
        public final long totalRamBytes;
        public final long availRamBytes;
        public final long usedRamBytes;
        public final int usedPercentage;

        public RamStats(long totalRamBytes, long availRamBytes, long usedRamBytes, int usedPercentage) {
            this.totalRamBytes = totalRamBytes;
            this.availRamBytes = availRamBytes;
            this.usedRamBytes = usedRamBytes;
            this.usedPercentage = usedPercentage;
        }

        public String getFormattedTotal() {
            return formatBytes(totalRamBytes);
        }

        public String getFormattedAvail() {
            return formatBytes(availRamBytes);
        }

        public String getFormattedUsed() {
            return formatBytes(usedRamBytes);
        }
    }

    /**
     * Listener callback interface triggered on every timer tick (every 1 second).
     */
    public interface MemoryUpdateListener {
        void onMemoryUpdate(RamStats stats);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private boolean isMonitoring = false;

    public MemoryUtils(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Queries ActivityManager.MemoryInfo to obtain current system memory stats.
     *
     * @return RamStats object containing Total RAM, Available RAM, Used RAM, and Used Percentage.
     */
    public RamStats getRamStats() {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo);
        }

        long totalRam = memoryInfo.totalMem;
        long availRam = memoryInfo.availMem;
        long usedRam = totalRam - availRam;

        int usedPercent = 0;
        if (totalRam > 0) {
            usedPercent = (int) ((usedRam * 100) / totalRam);
        }

        return new RamStats(totalRam, availRam, usedRam, usedPercent);
    }

    /**
     * Starts real-time periodic updates every second (1000 ms).
     *
     * @param listener Callback triggered with updated RamStats every 1 second.
     */
    public void startRealtimeMonitoring(final MemoryUpdateListener listener) {
        if (isMonitoring) return;
        isMonitoring = true;

        runnable = new Runnable() {
            @Override
            public void run() {
                if (!isMonitoring) return;

                RamStats stats = getRamStats();
                if (listener != null) {
                    listener.onMemoryUpdate(stats);
                }

                // Schedule next update in 1000 milliseconds (1 second)
                handler.postDelayed(this, 1000);
            }
        };

        handler.post(runnable);
        }

    /**
     * Stops the real-time monitoring task loop.
     */
    public void stopRealtimeMonitoring() {
        isMonitoring = false;
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    /**
     * Helper function to format raw byte values into human-readable strings (MB or GB).
     *
     * @param bytes Number of bytes to format.
     * @return Formatted string (e.g., "6.20 GB" or "850.0 MB").
     */
    public static String formatBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024.0) {
            return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.1f MB", mb);
        }
    }
}
