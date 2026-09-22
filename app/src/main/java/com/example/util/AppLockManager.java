package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Secure App Lock Manager using EncryptedSharedPreferences.
 * Manages 4-digit PIN storage, verification, change flows, and 2-minute background timeout auto-lock.
 */
public class AppLockManager {

    private static final String TAG = "AppLockManager";
    private static final String PREF_FILE_NAME = "app_lock_secure_prefs";
    private static final String KEY_PIN = "encrypted_app_pin";
    private static final String KEY_LAST_BACKGROUND_TIME = "last_background_timestamp";

    // Auto-lock timeout in milliseconds (2 minutes = 120,000 ms)
    public static final long BACKGROUND_LOCK_TIMEOUT_MS = 2 * 60 * 1000L;

    private static AppLockManager instance;
    private SharedPreferences prefs;
    private boolean isAppCurrentlyLocked = true;

    private AppLockManager(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            prefs = EncryptedSharedPreferences.create(
                    appContext,
                    PREF_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.values()[0],
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.values()[0]
            );
        } catch (GeneralSecurityException | IOException e) {
            SecureLog.e(TAG, "Failed to initialize EncryptedSharedPreferences, using standard prefs fallback", e);
            prefs = appContext.getSharedPreferences("app_lock_fallback_prefs", Context.MODE_PRIVATE);
        }
    }

    public static synchronized AppLockManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppLockManager(context);
        }
        return instance;
    }

    /**
     * Checks if a 4-digit PIN has been created by the user.
     */
    public boolean isPinSet() {
        String pin = prefs.getString(KEY_PIN, null);
        return pin != null && pin.length() == 4;
    }

    /**
     * Stores a new 4-digit PIN securely.
     */
    public boolean savePin(String pin) {
        if (pin == null || pin.length() != 4 || !pin.matches("\\d{4}")) {
            return false;
        }
        boolean saved = prefs.edit().putString(KEY_PIN, pin).commit();
        if (saved) {
            isAppCurrentlyLocked = false;
        }
        return saved;
    }

    /**
     * Verifies if the entered PIN matches the stored encrypted PIN.
     */
    public boolean verifyPin(String enteredPin) {
        if (!isPinSet() || enteredPin == null) {
            return false;
        }
        String storedPin = prefs.getString(KEY_PIN, "");
        boolean isValid = storedPin.equals(enteredPin);
        if (isValid) {
            markUnlocked();
        }
        return isValid;
    }

    /**
     * Changes the existing PIN after validating the old PIN.
     */
    public boolean changePin(String oldPin, String newPin) {
        if (!verifyPin(oldPin)) {
            return false;
        }
        return savePin(newPin);
    }

    /**
     * Removes the stored PIN and disables app lock.
     */
    public void removePin() {
        prefs.edit().remove(KEY_PIN).remove(KEY_LAST_BACKGROUND_TIME).apply();
        isAppCurrentlyLocked = false;
    }

    /**
     * Records the timestamp when the app enters background.
     */
    public void onAppBackgrounded() {
        long now = System.currentTimeMillis();
        prefs.edit().putLong(KEY_LAST_BACKGROUND_TIME, now).apply();
    }

    /**
     * Checks if the app should require PIN lock based on 2-minute background duration or locked state.
     */
    public boolean shouldLockApp() {
        if (!isPinSet()) {
            return false;
        }

        if (isAppCurrentlyLocked) {
            return true;
        }

        long lastBackgroundTime = prefs.getLong(KEY_LAST_BACKGROUND_TIME, 0L);
        if (lastBackgroundTime > 0) {
            long timeInBackground = System.currentTimeMillis() - lastBackgroundTime;
            if (timeInBackground >= BACKGROUND_LOCK_TIMEOUT_MS) {
                isAppCurrentlyLocked = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Marks the current session as unlocked.
     */
    public void markUnlocked() {
        isAppCurrentlyLocked = false;
        prefs.edit().putLong(KEY_LAST_BACKGROUND_TIME, 0L).apply();
    }

    /**
     * Forces immediate locking of the app.
     */
    public void lockApp() {
        isAppCurrentlyLocked = true;
    }

    public boolean isAppCurrentlyLocked() {
        return isAppCurrentlyLocked;
    }
}
