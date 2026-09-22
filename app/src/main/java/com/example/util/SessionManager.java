package com.example.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.UUID;

/**
 * Reusable Secure SessionManager for Mobile Memory Monitor.
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String SECURE_SESSION_PREFS = "mobile_memory_session_prefs";

    // Session State Encrypted Keys
    private static final String KEY_SESSION_TOKEN = "enc_session_token";
    private static final String KEY_USER_ID = "enc_session_user_id";
    private static final String KEY_LAST_ACTIVITY_TIME = "enc_session_last_activity";
    private static final String KEY_SESSION_LOCKED = "enc_session_is_locked";
    private static final String KEY_SESSION_START_TIME = "enc_session_start_time";

    // Inactivity Auto-Lock Timeout: 2 minutes (120,000 milliseconds)
    public static final long INACTIVITY_TIMEOUT_MS = 2 * 60 * 1000L;

    private static SessionManager instance;
    private SharedPreferences encryptedPrefs;
    private final Context appContext;

    private SessionManager(Context context) {
        this.appContext = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    appContext,
                    SECURE_SESSION_PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.values()[0],
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.values()[0]
            );
            SecureLog.i(TAG, "EncryptedSharedPreferences initialized successfully for SessionManager.");
        } catch (GeneralSecurityException | IOException e) {
            SecureLog.e(TAG, "Failed to initialize EncryptedSharedPreferences for SessionManager, fallback used", e);
            encryptedPrefs = appContext.getSharedPreferences("fallback_session_prefs", Context.MODE_PRIVATE);
        }
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public synchronized String startNewSession(String userId) {
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        encryptedPrefs.edit()
                .putString(KEY_SESSION_TOKEN, token)
                .putString(KEY_USER_ID, userId)
                .putLong(KEY_SESSION_START_TIME, now)
                .putLong(KEY_LAST_ACTIVITY_TIME, now)
                .putBoolean(KEY_SESSION_LOCKED, false)
                .apply();

        SecureLog.i(TAG, "New secure session started for user: " + userId + " [token=" + token + "]");
        return token;
    }

    public synchronized void updateUserActivity() {
        if (isSessionLocked()) {
            return;
        }
        long now = System.currentTimeMillis();
        encryptedPrefs.edit().putLong(KEY_LAST_ACTIVITY_TIME, now).apply();
    }

    public void touch() {
        updateUserActivity();
    }

    public synchronized boolean isSessionExpired() {
        if (isSessionLocked()) {
            return true;
        }

        long lastActivity = encryptedPrefs.getLong(KEY_LAST_ACTIVITY_TIME, 0L);
        if (lastActivity == 0L) {
            return false;
        }

        long inactivityDuration = System.currentTimeMillis() - lastActivity;
        return inactivityDuration >= INACTIVITY_TIMEOUT_MS;
    }

    public synchronized void lockSession() {
        encryptedPrefs.edit().putBoolean(KEY_SESSION_LOCKED, true).apply();
    }

    public synchronized boolean isSessionLocked() {
        return encryptedPrefs.getBoolean(KEY_SESSION_LOCKED, false);
    }

    public synchronized void unlockSession() {
        long now = System.currentTimeMillis();
        encryptedPrefs.edit()
                .putBoolean(KEY_SESSION_LOCKED, false)
                .putLong(KEY_LAST_ACTIVITY_TIME, now)
                .apply();
    }

    public synchronized boolean checkAndEnforceSessionLock(Activity activity) {
        return false;
    }

    public synchronized void saveSessionDataSecurely(String key, String value) {
        encryptedPrefs.edit().putString("custom_session_" + key, value).apply();
    }

    public synchronized String getSecureSessionData(String key, String defaultValue) {
        return encryptedPrefs.getString("custom_session_" + key, defaultValue);
    }

    public synchronized String getSessionToken() {
        return encryptedPrefs.getString(KEY_SESSION_TOKEN, null);
    }

    public synchronized String getUserId() {
        return encryptedPrefs.getString(KEY_USER_ID, "default_user");
    }

    public synchronized long getRemainingInactivityTimeMs() {
        if (isSessionLocked()) return 0L;
        long lastActivity = encryptedPrefs.getLong(KEY_LAST_ACTIVITY_TIME, System.currentTimeMillis());
        long elapsed = System.currentTimeMillis() - lastActivity;
        return Math.max(0L, INACTIVITY_TIMEOUT_MS - elapsed);
    }

    public synchronized void clearSession() {
        encryptedPrefs.edit()
                .remove(KEY_SESSION_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_LAST_ACTIVITY_TIME)
                .remove(KEY_SESSION_START_TIME)
                .putBoolean(KEY_SESSION_LOCKED, true)
                .apply();
        SecureLog.i(TAG, "Session cleared and invalidated.");
    }
}
