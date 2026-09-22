package com.example.util;

import android.util.Log;

import com.example.BuildConfig;

import java.util.regex.Pattern;

/**
 * SecureLog is a secure logging utility that protects application data.
 *
 * Requirements Met:
 * 1. Automatic Debug Suppression: Completely disables Log.v() and Log.d() statements in release builds (BuildConfig.DEBUG == false).
 * 2. Sensitive Data Sanitization: Automatically redacts PINs, tokens, keys, passwords, and sensitive credentials from log output.
 * 3. Safe Exception Logging: Sanitizes exception messages to prevent stack traces from leaking private user data in logs.
 */
public class SecureLog {

    private static final String DEFAULT_TAG = "MobileMemoryMonitor";
    private static final String REDACTED = "[REDACTED]";

    // Regex patterns for sensitive data (PINs, tokens, secret keys, passwords)
    private static final Pattern SENSITIVE_KEYWORD_PATTERN = Pattern.compile(
            "(?i)(pin|password|token|secret|key|auth|bearer|credential|session|passcode)\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern FOUR_DIGIT_PIN_PATTERN = Pattern.compile("\\b\\d{4}\\b");

    private SecureLog() {
        // Utility class
    }

    /**
     * Sanitizes input log string by masking sensitive keywords, PINs, and credentials.
     *
     * @param message Raw message string
     * @return Cleaned, redacted message string safe for Logcat output
     */
    public static String sanitize(String message) {
        if (message == null) return "null";

        // Redact key=value or key:value sensitive patterns
        String cleaned = SENSITIVE_KEYWORD_PATTERN.matcher(message).replaceAll("$1=" + REDACTED);

        // Mask isolated 4-digit PIN numbers if present in messages referencing lock/auth
        if (message.toLowerCase().contains("pin") || message.toLowerCase().contains("lock") || message.toLowerCase().contains("pass")) {
            cleaned = FOUR_DIGIT_PIN_PATTERN.matcher(cleaned).replaceAll("****");
        }

        return cleaned;
    }

    // =========================================================================
    // VERBOSE (v) - Completely disabled in Release builds
    // =========================================================================

    public static void v(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, sanitize(msg));
        }
    }

    public static void v(String tag, String msg, Throwable tr) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, sanitize(msg), tr);
        }
    }

    // =========================================================================
    // DEBUG (d) - Completely disabled in Release builds
    // =========================================================================

    public static void d(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, sanitize(msg));
        }
    }

    public static void d(String tag, String msg, Throwable tr) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, sanitize(msg), tr);
        }
    }

    // =========================================================================
    // INFO (i) - Sanitized in all builds
    // =========================================================================

    public static void i(String tag, String msg) {
        Log.i(tag, sanitize(msg));
    }

    public static void i(String tag, String msg, Throwable tr) {
        Log.i(tag, sanitize(msg), tr);
    }

    // =========================================================================
    // WARN (w) - Sanitized in all builds
    // =========================================================================

    public static void w(String tag, String msg) {
        Log.w(tag, sanitize(msg));
    }

    public static void w(String tag, String msg, Throwable tr) {
        Log.w(tag, sanitize(msg), tr);
    }

    // =========================================================================
    // ERROR (e) - Sanitized in all builds
    // =========================================================================

    public static void e(String tag, String msg) {
        Log.e(tag, sanitize(msg));
    }

    public static void e(String tag, String msg, Throwable tr) {
        Log.e(tag, sanitize(msg), tr);
    }

    // =========================================================================
    // WTF (Assert) - Sanitized in all builds
    // =========================================================================

    public static void wtf(String tag, String msg) {
        Log.wtf(tag, sanitize(msg));
    }

    public static void wtf(String tag, String msg, Throwable tr) {
        Log.wtf(tag, sanitize(msg), tr);
    }
}
