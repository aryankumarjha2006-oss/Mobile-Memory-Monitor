package com.example.util;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import java.util.List;

/**
 * RootWarningDialog displays a security dialog when root privileges or
 * superuser binaries are detected on the Android device.
 *
 * Requirements Met:
 * - Shows warning dialog on root detection
 * - Explains memory optimization features are restricted
 * - Confirms passive memory monitoring remains operational
 */
public class RootWarningDialog {

    /**
     * Displays a Warning Dialog explaining root restrictions to the user.
     *
     * @param context Context required to display the dialog
     */
    public static void showRootWarningDialog(Context context) {
        if (context == null) return;

        RootDetectionManager rootManager = RootDetectionManager.getInstance();
        List<String> triggers = rootManager.getRootDetectionTriggers(context);

        StringBuilder detailsBuilder = new StringBuilder();
        if (!triggers.isEmpty()) {
            detailsBuilder.append("\n\nDetected Triggers:\n");
            for (String trigger : triggers) {
                detailsBuilder.append("• ").append(trigger).append("\n");
            }
        }

        String message = "⚠️ Root Access Detected!\n\n" +
                "Your device appears to have superuser / root privileges. " +
                "To prevent system instability or unintended OS crashes, memory optimization features " +
                "(RAM Boost, Cache Purge, Auto-Clean) have been disabled.\n\n" +
                "Mobile Memory Monitor will continue to operate in Monitoring-Only Mode." +
                detailsBuilder.toString();

        try {
            new AlertDialog.Builder(context)
                    .setTitle("Security Warning - Device Rooted")
                    .setMessage(message)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setCancelable(false)
                    .setPositiveButton("I Understand (Monitoring Only)", (dialog, which) -> dialog.dismiss())
                    .show();
        } catch (Exception e) {
            // Ignore if activity is finishing or context is invalid
        }
    }
}

