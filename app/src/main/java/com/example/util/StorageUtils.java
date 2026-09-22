package com.example.util;

import android.os.Environment;
import android.os.StatFs;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;

/**
 * Utility class using StatFs to calculate internal storage metrics in bytes and GB,
 * and display the results directly in Android TextViews.
 */
public class StorageUtils {

    /**
     * Data class to store calculated storage metrics in bytes and gigabytes (GB).
     */
    public static class StorageInfo {
        public final long totalBytes;
        public final long availableBytes;
        public final long usedBytes;

        public final double totalGB;
        public final double availableGB;
        public final double usedGB;

        public StorageInfo(long totalBytes, long availableBytes, long usedBytes) {
            this.totalBytes = totalBytes;
            this.availableBytes = availableBytes;
            this.usedBytes = usedBytes;

            // 1 GB = 1024 * 1024 * 1024 bytes (1,073,741,824 bytes)
            double bytesInGb = 1024.0 * 1024.0 * 1024.0;
            this.totalGB = totalBytes / bytesInGb;
            this.availableGB = availableBytes / bytesInGb;
            this.usedGB = usedBytes / bytesInGb;
        }
    }

    /**
     * Reads internal storage stats using StatFs and returns a StorageInfo instance.
     *
     * @return StorageInfo with raw byte values and converted GB values.
     */
    public static StorageInfo getInternalStorageInfo() {
        // Step 1: Get the path to the internal data directory (/data)
        File path = Environment.getDataDirectory();

        // Step 2: Instantiate StatFs to inspect file system statistics for that path
        StatFs statFs = new StatFs(path.getPath());

        // Step 3: Retrieve block size and block counts in bytes
        long blockSize = statFs.getBlockSizeLong();
        long totalBlocks = statFs.getBlockCountLong();
        long availableBlocks = statFs.getAvailableBlocksLong();

        // Step 4: Calculate total, available, and used storage in bytes
        long totalBytes = totalBlocks * blockSize;
        long availableBytes = availableBlocks * blockSize;
        long usedBytes = totalBytes - availableBytes;

        return new StorageInfo(totalBytes, availableBytes, usedBytes);
    }

    /**
     * Calculates storage stats and updates the provided TextViews with formatted GB strings.
     *
     * @param totalTextView TextView to display Total Storage in GB
     * @param usedTextView TextView to display Used Storage in GB
     * @param availableTextView TextView to display Available Storage in GB
     */
    public static void displayStorageInTextViews(
            TextView totalTextView,
            TextView usedTextView,
            TextView availableTextView
    ) {
        StorageInfo info = getInternalStorageInfo();

        if (totalTextView != null) {
            totalTextView.setText(String.format(Locale.getDefault(), "Total Storage: %.2f GB", info.totalGB));
        }

        if (usedTextView != null) {
            usedTextView.setText(String.format(Locale.getDefault(), "Used Storage: %.2f GB", info.usedGB));
        }

        if (availableTextView != null) {
            availableTextView.setText(String.format(Locale.getDefault(), "Available Storage: %.2f GB", info.availableGB));
        }
    }
}
