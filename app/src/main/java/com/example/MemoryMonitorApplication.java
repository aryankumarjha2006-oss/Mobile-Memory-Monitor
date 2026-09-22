package com.example;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.example.util.ScreenProtectionManager;

/**
 * Main Application class tracking app lifecycle and enforcing global FLAG_SECURE screen protection.
 */
public class MemoryMonitorApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private int activeActivityCount = 0;
    private boolean isChangingConfigurations = false;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        ScreenProtectionManager.applyScreenProtection(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (activeActivityCount == 0 && !isChangingConfigurations) {
            com.example.util.SessionManager.getInstance(this).touch();
        }
        activeActivityCount++;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        com.example.util.SessionManager.getInstance(this).touch();
    }

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {
        isChangingConfigurations = activity.isChangingConfigurations();
        activeActivityCount--;
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}
}
