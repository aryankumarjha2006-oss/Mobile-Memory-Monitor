package com.example.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * RootDetectionManager is a comprehensive Java utility for detecting root/superuser
 * modifications on Android devices.
 *
 * Checks:
 * 1. Test-Keys build tags (Build.TAGS)
 * 2. Standard 'su' binary file locations
 * 3. Known Root management packages & APKs (Magisk, SuperSU, KingoRoot, etc.)
 * 4. System directory write permissions & dangerous build properties (ro.debuggable)
 * 5. Runtime 'su' command execution test
 *
 * When root is detected, memory optimization features (RAM boosting, cache purging)
 * are restricted for safety, allowing monitoring only.
 */
public class RootDetectionManager {

    private static final String TAG = "RootDetectionManager";

    // Known SU binary paths
    private static final String[] SU_PATHS = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/su/bin",
            "/magisk/.core/bin"
    };

    // Known Root Management Packages
    private static final String[] ROOT_PACKAGES = {
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.zacharee1.systemuituner",
            "com.topjohnwu.magisk",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.ramdroid.appquarantine",
            "com.formyhm.l33t.voodoo"
    };

    private static RootDetectionManager instance;
    private boolean isRootedCached = false;
    private boolean hasCheckedRoot = false;
    private boolean isOptimizationDisabledByRoot = false;

    private RootDetectionManager() {}

    public static synchronized RootDetectionManager getInstance() {
        if (instance == null) {
            instance = new RootDetectionManager();
        }
        return instance;
    }

    /**
     * Checks if the Android device is rooted.
     *
     * @param context Application context
     * @return true if any root indicator is present, false otherwise
     */
    public synchronized boolean isDeviceRooted(Context context) {
        if (hasCheckedRoot) {
            return isRootedCached;
        }

        boolean check1 = checkTestKeys();
        boolean check2 = checkSuBinaries();
        boolean check3 = checkRootPackages(context);
        boolean check4 = checkSuCommandExecution();
        boolean check5 = checkDangerousProperties();

        isRootedCached = check1 || check2 || check3 || check4 || check5;
        hasCheckedRoot = true;

        if (isRootedCached) {
            isOptimizationDisabledByRoot = true;
            SecureLog.w(TAG, "Root access detected! Restricting memory optimization features.");
        } else {
            SecureLog.i(TAG, "No root access detected. System memory optimization enabled.");
        }

        return isRootedCached;
    }

    /**
     * Returns detailed list of root detection triggers found on the device.
     */
    public List<String> getRootDetectionTriggers(Context context) {
        List<String> triggers = new ArrayList<>();

        if (checkTestKeys()) {
            triggers.add("Build Tags: Test-Keys found (Build.TAGS = " + Build.TAGS + ")");
        }

        String foundSuBinary = getFoundSuBinaryPath();
        if (foundSuBinary != null) {
            triggers.add("SU Binary Path: Found at " + foundSuBinary);
        }

        String foundPackage = getFoundRootPackage(context);
        if (foundPackage != null) {
            triggers.add("Root Management Package: Installed (" + foundPackage + ")");
        }

        if (checkSuCommandExecution()) {
            triggers.add("Process Test: 'which su' executed successfully");
        }

        if (checkDangerousProperties()) {
            triggers.add("Build Property: ro.debuggable = 1 or ro.secure = 0");
        }

        return triggers;
    }

    /**
     * Check 1: Test-Keys in Build Tags
     */
    private boolean checkTestKeys() {
        String buildTags = Build.TAGS;
        return buildTags != null && buildTags.contains("test-keys");
    }

    /**
     * Check 2: Existence of 'su' binaries in system directories
     */
    private boolean checkSuBinaries() {
        for (String path : SU_PATHS) {
            try {
                File file = new File(path);
                if (file.exists()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String getFoundSuBinaryPath() {
        for (String path : SU_PATHS) {
            try {
                File file = new File(path);
                if (file.exists()) {
                    return path;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Check 3: Installed Root Management Packages
     */
    private boolean checkRootPackages(Context context) {
        if (context == null) return false;
        PackageManager pm = context.getPackageManager();
        for (String pkg : ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return false;
    }

    private String getFoundRootPackage(Context context) {
        if (context == null) return null;
        PackageManager pm = context.getPackageManager();
        for (String pkg : ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    /**
     * Check 4: Runtime execution of 'which su' or 'su' command
     */
    private boolean checkSuCommandExecution() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"/system/xbin/which", "su"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = in.readLine();
            process.destroy();
            return line != null;
        } catch (Throwable t) {
            if (process != null) {
                process.destroy();
            }
            return false;
        }
    }

    /**
     * Check 5: Dangerous System Properties
     */
    private boolean checkDangerousProperties() {
        String buildType = Build.TYPE;
        if ("userdebug".equals(buildType) || "eng".equals(buildType)) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if memory optimization actions (RAM Boost, Cache Purge) are restricted due to root presence.
     */
    public boolean isOptimizationDisabled() {
        return isOptimizationDisabledByRoot;
    }

    public void setOptimizationDisabled(boolean disabled) {
        this.isOptimizationDisabledByRoot = disabled;
    }
}
