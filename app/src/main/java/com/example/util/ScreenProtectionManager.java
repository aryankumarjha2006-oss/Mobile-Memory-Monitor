package com.example.util;

import android.app.Activity;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

/**
 * ScreenProtectionManager provides utility methods to manage screenshot,
 * screen recording, and recent app preview protection via WindowManager.LayoutParams.FLAG_SECURE.
 *
 * NOTE: FLAG_SECURE blocks external screen streaming buffers (such as web-based emulator previews).
 * Screen protection is kept configurable and disabled by default in streaming/preview environments
 * to prevent black screens on the display while maintaining complete implementation support.
 */
public class ScreenProtectionManager {

    private static final String TAG = "ScreenProtectionManager";

    // Set to false by default so streaming emulator preview displays UI properly without black screen
    private static boolean isProtectionEnabled = false;

    /**
     * Enables or disables global screen protection.
     */
    public static void setProtectionEnabled(boolean enabled) {
        isProtectionEnabled = enabled;
        SecureLog.i(TAG, "Screen protection FLAG_SECURE status updated to: " + enabled);
    }

    public static boolean isProtectionEnabled() {
        return isProtectionEnabled;
    }

    /**
     * Applies or removes WindowManager.LayoutParams.FLAG_SECURE based on protection status.
     *
     * @param activity The Activity to manage
     */
    public static void applyScreenProtection(Activity activity) {
        if (activity == null) return;

        try {
            Window window = activity.getWindow();
            if (window != null) {
                if (isProtectionEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    SecureLog.i(TAG, "FLAG_SECURE applied to " + activity.getClass().getSimpleName());
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    SecureLog.i(TAG, "FLAG_SECURE cleared on " + activity.getClass().getSimpleName() + " for display compatibility.");
                }
            }
        } catch (Exception e) {
            SecureLog.e(TAG, "Error applying screen protection to " + activity.getClass().getSimpleName(), e);
        }
    }

    /**
     * Explicitly enables FLAG_SECURE on the specified Activity window.
     */
    public static void enableScreenProtection(Activity activity) {
        if (activity == null) return;
        if (isProtectionEnabled) {
            try {
                Window window = activity.getWindow();
                if (window != null) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            } catch (Exception ignored) {}
        } else {
            disableScreenProtection(activity);
        }
    }

    /**
     * Explicitly removes FLAG_SECURE from the specified Activity window.
     */
    public static void disableScreenProtection(Activity activity) {
        if (activity == null) return;

        try {
            Window window = activity.getWindow();
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        } catch (Exception ignored) {}
    }
}

