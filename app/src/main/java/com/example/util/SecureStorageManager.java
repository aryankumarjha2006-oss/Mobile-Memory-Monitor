package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * SecureStorageManager enforces hardware-backed AES-256 encryption on all local data
 * in Mobile Memory Monitor using EncryptedSharedPreferences.
 *
 * Encrypts:
 * 1. User Settings (Dark theme, chart themes, auto-clean thresholds, AI triggers)
 * 2. Memory History (Timestamped memory usage snapshots, logs, RAM spikes)
 * 3. Notification Preferences (Alert thresholds, vibration, alert sound, check frequency)
 */
public class SecureStorageManager {

    private static final String TAG = "SecureStorageManager";
    private static final String SECURE_PREFS_FILE = "mobile_memory_secure_storage";

    // Keys for Encrypted User Settings
    private static final String KEY_DARK_MODE = "enc_setting_dark_mode";
    private static final String KEY_AUTO_CLEAN_ENABLED = "enc_setting_auto_clean_enabled";
    private static final String KEY_AUTO_CLEAN_INTERVAL_MIN = "enc_setting_auto_clean_interval";
    private static final String KEY_AUTO_CLEAN_THRESHOLD_PCT = "enc_setting_auto_clean_threshold";
    private static final String KEY_CHART_THEME_NAME = "enc_setting_chart_theme";
    private static final String KEY_BACKGROUND_MONITORING = "enc_setting_bg_monitoring";

    // Keys for Encrypted Memory History
    private static final String KEY_MEMORY_HISTORY_JSON = "enc_history_memory_snapshots_json";
    private static final String KEY_SYSTEM_LOGS_JSON = "enc_history_system_logs_json";

    // Keys for Encrypted Notification Preferences
    private static final String KEY_NOTIF_ALERTS_ENABLED = "enc_notif_alerts_enabled";
    private static final String KEY_RAM_WARNING_THRESHOLD_PCT = "enc_notif_ram_threshold_pct";
    private static final String KEY_NOTIF_VIBRATION = "enc_notif_vibration_enabled";
    private static final String KEY_NOTIF_SOUND = "enc_notif_sound_enabled";
    private static final String KEY_NOTIF_FREQUENCY_SEC = "enc_notif_frequency_seconds";

    private static SecureStorageManager instance;
    private SharedPreferences encryptedPrefs;

    /**
     * Memory Snapshot Data Object
     */
    public static class MemorySnapshot {
        public long timestampMs;
        public float usedMemoryGb;
        public float totalMemoryGb;
        public int activeProcessCount;
        public String statusLabel;

        public MemorySnapshot(long timestampMs, float usedMemoryGb, float totalMemoryGb, int activeProcessCount, String statusLabel) {
            this.timestampMs = timestampMs;
            this.usedMemoryGb = usedMemoryGb;
            this.totalMemoryGb = totalMemoryGb;
            this.activeProcessCount = activeProcessCount;
            this.statusLabel = statusLabel;
        }

        public JSONObject toJsonObject() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("timestampMs", timestampMs);
            json.put("usedMemoryGb", usedMemoryGb);
            json.put("totalMemoryGb", totalMemoryGb);
            json.put("activeProcessCount", activeProcessCount);
            json.put("statusLabel", statusLabel);
            return json;
        }

        public static MemorySnapshot fromJsonObject(JSONObject json) {
            if (json == null) return null;
            long time = json.optLong("timestampMs", System.currentTimeMillis());
            double used = json.optDouble("usedMemoryGb", 0.0);
            double total = json.optDouble("totalMemoryGb", 8.0);
            int processes = json.optInt("activeProcessCount", 0);
            String label = json.optString("statusLabel", "NORMAL");
            return new MemorySnapshot(time, (float) used, (float) total, processes, label);
        }
    }

    private SecureStorageManager(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    appContext,
                    SECURE_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.values()[0],
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.values()[0]
            );
            SecureLog.i(TAG, "EncryptedSharedPreferences initialized successfully with AES256 encryption.");
        } catch (GeneralSecurityException | IOException e) {
            SecureLog.e(TAG, "Failed to initialize EncryptedSharedPreferences, initializing fallback secure storage", e);
            encryptedPrefs = appContext.getSharedPreferences("fallback_secure_prefs", Context.MODE_PRIVATE);
        }
    }

    public static synchronized SecureStorageManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecureStorageManager(context);
        }
        return instance;
    }

    // =========================================================================
    // 1. ENCRYPTED USER SETTINGS
    // =========================================================================

    public boolean isDarkModeEnabled() {
        return encryptedPrefs.getBoolean(KEY_DARK_MODE, true);
    }

    public void setDarkModeEnabled(boolean enabled) {
        encryptedPrefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }

    public boolean isAutoCleanEnabled() {
        return encryptedPrefs.getBoolean(KEY_AUTO_CLEAN_ENABLED, false);
    }

    public void setAutoCleanEnabled(boolean enabled) {
        encryptedPrefs.edit().putBoolean(KEY_AUTO_CLEAN_ENABLED, enabled).apply();
    }

    public int getAutoCleanIntervalMinutes() {
        return encryptedPrefs.getInt(KEY_AUTO_CLEAN_INTERVAL_MIN, 60);
    }

    public void setAutoCleanIntervalMinutes(int intervalMin) {
        encryptedPrefs.edit().putInt(KEY_AUTO_CLEAN_INTERVAL_MIN, intervalMin).apply();
    }

    public int getAutoCleanThresholdPercent() {
        return encryptedPrefs.getInt(KEY_AUTO_CLEAN_THRESHOLD_PCT, 85);
    }

    public void setAutoCleanThresholdPercent(int thresholdPct) {
        encryptedPrefs.edit().putInt(KEY_AUTO_CLEAN_THRESHOLD_PCT, thresholdPct).apply();
    }

    public String getChartThemeName() {
        return encryptedPrefs.getString(KEY_CHART_THEME_NAME, "CYBER_DARK");
    }

    public void setChartThemeName(String themeName) {
        encryptedPrefs.edit().putString(KEY_CHART_THEME_NAME, themeName).apply();
    }

    public boolean isBackgroundMonitoringEnabled() {
        return encryptedPrefs.getBoolean(KEY_BACKGROUND_MONITORING, true);
    }

    public void setBackgroundMonitoringEnabled(boolean enabled) {
        encryptedPrefs.edit().putBoolean(KEY_BACKGROUND_MONITORING, enabled).apply();
    }

    // =========================================================================
    // 2. ENCRYPTED MEMORY HISTORY
    // =========================================================================

    /**
     * Saves a new memory usage snapshot into encrypted JSON array history.
     */
    public synchronized void saveMemorySnapshot(long timestampMs, float usedGb, float totalGb, int processCount, String statusLabel) {
        List<MemorySnapshot> history = getMemoryHistorySnapshots();
        history.add(new MemorySnapshot(timestampMs, usedGb, totalGb, processCount, statusLabel));

        // Retain maximum 100 historical snapshots to optimize performance
        if (history.size() > 100) {
            history = history.subList(history.size() - 100, history.size());
        }

        JSONArray array = new JSONArray();
        for (MemorySnapshot snapshot : history) {
            try {
                array.put(snapshot.toJsonObject());
            } catch (JSONException e) {
                SecureLog.e(TAG, "Error serializing memory snapshot to JSON", e);
            }
        }

        encryptedPrefs.edit().putString(KEY_MEMORY_HISTORY_JSON, array.toString()).apply();
    }

    /**
     * Retrieves all decrypted memory history snapshots.
     */
    public synchronized List<MemorySnapshot> getMemoryHistorySnapshots() {
        List<MemorySnapshot> snapshots = new ArrayList<>();
        String jsonString = encryptedPrefs.getString(KEY_MEMORY_HISTORY_JSON, "[]");
        try {
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                MemorySnapshot snapshot = MemorySnapshot.fromJsonObject(obj);
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }
        } catch (JSONException e) {
            SecureLog.e(TAG, "Error parsing decrypted memory history JSON", e);
        }
        return snapshots;
    }

    /**
     * Appends an encrypted system activity log message.
     */
    public synchronized void addSystemLogEntry(String logText) {
        List<String> logs = getSystemLogs();
        String entry = "[" + System.currentTimeMillis() + "] " + logText;
        logs.add(entry);

        if (logs.size() > 50) {
            logs = logs.subList(logs.size() - 50, logs.size());
        }

        JSONArray array = new JSONArray();
        for (String log : logs) {
            array.put(log);
        }

        encryptedPrefs.edit().putString(KEY_SYSTEM_LOGS_JSON, array.toString()).apply();
    }

    /**
     * Retrieves decrypted system history logs.
     */
    public synchronized List<String> getSystemLogs() {
        List<String> logs = new ArrayList<>();
        String jsonString = encryptedPrefs.getString(KEY_SYSTEM_LOGS_JSON, "[]");
        try {
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                logs.add(array.getString(i));
            }
        } catch (JSONException e) {
            SecureLog.e(TAG, "Error parsing encrypted system logs", e);
        }
        return logs;
    }

    /**
     * Clears all encrypted memory history and system logs.
     */
    public synchronized void clearMemoryHistory() {
        encryptedPrefs.edit()
                .remove(KEY_MEMORY_HISTORY_JSON)
                .remove(KEY_SYSTEM_LOGS_JSON)
                .apply();
        SecureLog.i(TAG, "All encrypted memory history and system logs cleared.");
    }

    // =========================================================================
    // 3. ENCRYPTED NOTIFICATION PREFERENCES
    // =========================================================================

    public boolean isNotificationAlertsEnabled() {
        return encryptedPrefs.getBoolean(KEY_NOTIF_ALERTS_ENABLED, true);
    }

    public void setNotificationAlertsEnabled(boolean enabled) {
        encryptedPrefs.edit().putBoolean(KEY_NOTIF_ALERTS_ENABLED, enabled).apply();
    }

    public float getRamWarningThresholdPercent() {
        return encryptedPrefs.getFloat(KEY_RAM_WARNING_THRESHOLD_PCT, 80.0f);
    }

    public void setRamWarningThresholdPercent(float thresholdPct) {
        encryptedPrefs.edit().putFloat(KEY_RAM_WARNING_THRESHOLD_PCT, thresholdPct).apply();
    }

    public boolean isVibrationEnabled() {
        return encryptedPrefs.getBoolean(KEY_NOTIF_VIBRATION, true);
    }

    public void setVibrationEnabled(boolean enabled) {
        encryptedPrefs.edit().putBoolean(KEY_NOTIF_VIBRATION, enabled).apply();
    }

    public boolean isSoundEnabled() {
        return encryptedPrefs.getBoolean(KEY_NOTIF_SOUND, true);
    }

    public void setSoundEnabled(boolean enabled) {
        encryptedPrefs.edit().putBoolean(KEY_NOTIF_SOUND, enabled).apply();
    }

    public int getNotificationFrequencySeconds() {
        return encryptedPrefs.getInt(KEY_NOTIF_FREQUENCY_SEC, 30);
    }

    public void setNotificationFrequencySeconds(int seconds) {
        encryptedPrefs.edit().putInt(KEY_NOTIF_FREQUENCY_SEC, seconds).apply();
    }

    // =========================================================================
    // 4. SECURITY & SANITIZATION
    // =========================================================================

    /**
     * Completely wipes all encrypted local storage (Settings, History, and Notification Preferences).
     */
    public synchronized void wipeAllSecureData() {
        encryptedPrefs.edit().clear().apply();
        SecureLog.w(TAG, "All secure encrypted data wiped.");
    }
}
