package com.example.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.MainActivity;
import com.example.R;
import com.example.util.AppLockManager;
import com.example.util.ScreenProtectionManager;

import java.util.concurrent.Executor;

/**
 * App Lock Activity written in Java with Material Design and AndroidX BiometricPrompt.
 * Features: 4-Digit PIN Creation, Verification, Settings PIN Change, Fingerprint Biometric Unlock,
 * and FLAG_SECURE Screenshot / Screen Recording Prevention.
 */
public class AppLockActivity extends FragmentActivity {

    public static final String EXTRA_LOCK_MODE = "extra_lock_mode";
    public static final int MODE_CREATE_PIN = 101;
    public static final int MODE_ENTER_PIN = 102;
    public static final int MODE_CHANGE_PIN = 103;

    private int currentMode = MODE_ENTER_PIN;
    private AppLockManager lockManager;

    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvError;
    private LinearLayout llPinDots;
    private LinearLayout btnBiometricUnlock;
    private final View[] pinDots = new View[4];

    private final StringBuilder currentInputPin = new StringBuilder();
    private String tempFirstPin = null;
    private String tempOldPinForChange = null;
    private int changeStep = 1; // 1: Enter Old, 2: Enter New, 3: Confirm New
    private boolean biometricPromptAlreadyTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenProtectionManager.applyScreenProtection(this);
        setContentView(R.layout.activity_app_lock);

        lockManager = AppLockManager.getInstance(this);

        if (getIntent() != null && getIntent().hasExtra(EXTRA_LOCK_MODE)) {
            currentMode = getIntent().getIntExtra(EXTRA_LOCK_MODE, MODE_ENTER_PIN);
        } else {
            // Auto-detect mode
            if (!lockManager.isPinSet()) {
                currentMode = MODE_CREATE_PIN;
            } else {
                currentMode = MODE_ENTER_PIN;
            }
        }

        initViews();
        setupKeypadListeners();
        updateUiForCurrentState();

        // Auto-trigger Fingerprint prompt if biometrics is available and app is in ENTER_PIN mode
        if (currentMode == MODE_ENTER_PIN && isBiometricAvailable()) {
            llPinDots.postDelayed(this::showBiometricPrompt, 200);
        }
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tvAppLockTitle);
        tvSubtitle = findViewById(R.id.tvAppLockSubtitle);
        tvError = findViewById(R.id.tvPinError);
        llPinDots = findViewById(R.id.llPinDots);
        btnBiometricUnlock = findViewById(R.id.btnBiometricUnlock);

        pinDots[0] = findViewById(R.id.vDot1);
        pinDots[1] = findViewById(R.id.vDot2);
        pinDots[2] = findViewById(R.id.vDot3);
        pinDots[3] = findViewById(R.id.vDot4);
    }

    private void setupKeypadListeners() {
        int[] numKeyIds = {
                R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3,
                R.id.btnKey4, R.id.btnKey5, R.id.btnKey6, R.id.btnKey7,
                R.id.btnKey8, R.id.btnKey9
        };

        for (int i = 0; i < numKeyIds.length; i++) {
            final int digit = i;
            findViewById(numKeyIds[i]).setOnClickListener(v -> onDigitPressed(digit));
        }

        findViewById(R.id.btnKeyDelete).setOnClickListener(v -> onDeletePressed());
        findViewById(R.id.btnKeyClear).setOnClickListener(v -> onClearPressed());

        if (btnBiometricUnlock != null) {
            btnBiometricUnlock.setOnClickListener(v -> showBiometricPrompt());
        }
    }

    /**
     * Checks if fingerprint / biometric hardware is available and enrolled on the device.
     */
    private boolean isBiometricAvailable() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK
        );
        return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Launches AndroidX BiometricPrompt for fingerprint authentication.
     */
    private void showBiometricPrompt() {
        if (!isBiometricAvailable()) {
            showError("Biometric authentication is unavailable. Use 4-digit PIN.");
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // User closed prompt or canceled -> Fall back to PIN entry
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    showError("Biometric error: " + errString + ". Enter PIN below.");
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(AppLockActivity.this, "Fingerprint authenticated successfully!", Toast.LENGTH_SHORT).show();
                proceedToMainApp();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                showError("Fingerprint not recognized. Try again or enter PIN.");
                triggerHapticFeedback();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Unlock")
                .setSubtitle("Scan fingerprint to unlock Mobile Memory Monitor")
                .setNegativeButtonText("Use 4-Digit PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void onDigitPressed(int digit) {
        if (currentInputPin.length() < 4) {
            currentInputPin.append(digit);
            updatePinDots();
            tvError.setText("");

            if (currentInputPin.length() == 4) {
                llPinDots.postDelayed(this::processCompletePin, 150);
            }
        }
    }

    private void onDeletePressed() {
        if (currentInputPin.length() > 0) {
            currentInputPin.deleteCharAt(currentInputPin.length() - 1);
            updatePinDots();
            tvError.setText("");
        }
    }

    private void onClearPressed() {
        currentInputPin.setLength(0);
        updatePinDots();
        tvError.setText("");
    }

    private void updatePinDots() {
        int length = currentInputPin.length();
        for (int i = 0; i < 4; i++) {
            if (i < length) {
                pinDots[i].setBackgroundResource(R.drawable.bg_pin_dot_on);
            } else {
                pinDots[i].setBackgroundResource(R.drawable.bg_pin_dot_off);
            }
        }
    }

    private void updateUiForCurrentState() {
        currentInputPin.setLength(0);
        updatePinDots();

        // Manage Biometric button visibility
        if (btnBiometricUnlock != null) {
            if (currentMode == MODE_ENTER_PIN && isBiometricAvailable()) {
                btnBiometricUnlock.setVisibility(View.VISIBLE);
            } else {
                btnBiometricUnlock.setVisibility(View.GONE);
            }
        }

        switch (currentMode) {
            case MODE_CREATE_PIN:
                if (tempFirstPin == null) {
                    tvTitle.setText("Create a 4-Digit PIN");
                    tvSubtitle.setText("Choose a secure 4-digit PIN for App Lock");
                } else {
                    tvTitle.setText("Confirm Your PIN");
                    tvSubtitle.setText("Re-enter the same 4-digit PIN");
                }
                break;

            case MODE_ENTER_PIN:
                tvTitle.setText("Enter Your 4-Digit PIN");
                tvSubtitle.setText(isBiometricAvailable() ? "Touch fingerprint sensor or enter PIN" : "Unlock Memory Monitor application");
                break;

            case MODE_CHANGE_PIN:
                if (changeStep == 1) {
                    tvTitle.setText("Enter Current PIN");
                    tvSubtitle.setText("Verify your existing security PIN");
                } else if (changeStep == 2) {
                    tvTitle.setText("Enter New PIN");
                    tvSubtitle.setText("Choose a new 4-digit PIN");
                } else {
                    tvTitle.setText("Confirm New PIN");
                    tvSubtitle.setText("Re-enter your new 4-digit PIN");
                }
                break;
        }
    }

    private void processCompletePin() {
        String enteredPin = currentInputPin.toString();

        switch (currentMode) {
            case MODE_CREATE_PIN:
                handleCreatePinFlow(enteredPin);
                break;

            case MODE_ENTER_PIN:
                handleEnterPinFlow(enteredPin);
                break;

            case MODE_CHANGE_PIN:
                handleChangePinFlow(enteredPin);
                break;
        }
    }

    private void handleCreatePinFlow(String pin) {
        if (tempFirstPin == null) {
            tempFirstPin = pin;
            updateUiForCurrentState();
        } else {
            if (tempFirstPin.equals(pin)) {
                boolean saved = lockManager.savePin(pin);
                if (saved) {
                    Toast.makeText(this, "App Lock PIN created successfully!", Toast.LENGTH_SHORT).show();
                    proceedToMainApp();
                } else {
                    showError("Failed to save PIN. Try again.");
                    tempFirstPin = null;
                    updateUiForCurrentState();
                }
            } else {
                showError("PINs do not match. Please try again.");
                triggerHapticFeedback();
                tempFirstPin = null;
                updateUiForCurrentState();
            }
        }
    }

    private void handleEnterPinFlow(String pin) {
        if (lockManager.verifyPin(pin)) {
            proceedToMainApp();
        } else {
            showError("Incorrect PIN. Please try again.");
            triggerHapticFeedback();
            currentInputPin.setLength(0);
            updatePinDots();
        }
    }

    private void handleChangePinFlow(String pin) {
        if (changeStep == 1) {
            if (lockManager.verifyPin(pin)) {
                tempOldPinForChange = pin;
                changeStep = 2;
                updateUiForCurrentState();
            } else {
                showError("Incorrect current PIN.");
                triggerHapticFeedback();
                currentInputPin.setLength(0);
                updatePinDots();
            }
        } else if (changeStep == 2) {
            tempFirstPin = pin;
            changeStep = 3;
            updateUiForCurrentState();
        } else if (changeStep == 3) {
            if (tempFirstPin != null && tempFirstPin.equals(pin)) {
                boolean changed = lockManager.changePin(tempOldPinForChange, pin);
                if (changed) {
                    Toast.makeText(this, "PIN updated successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    showError("Failed to update PIN.");
                    changeStep = 1;
                    tempOldPinForChange = null;
                    tempFirstPin = null;
                    updateUiForCurrentState();
                }
            } else {
                showError("New PINs do not match. Restarting change.");
                triggerHapticFeedback();
                changeStep = 1;
                tempOldPinForChange = null;
                tempFirstPin = null;
                updateUiForCurrentState();
            }
        }
    }

    private void showError(String message) {
        tvError.setText(message);
        try {
            Animation shake = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left);
            llPinDots.startAnimation(shake);
        } catch (Exception e) {
            // Ignore animation fallback
        }
    }

    private void triggerHapticFeedback() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {
        }
    }

    private void proceedToMainApp() {
        lockManager.markUnlocked();
        com.example.util.SessionManager.getInstance(this).unlockSession();
        if (isTaskRoot()) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else {
            setResult(RESULT_OK);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        if (currentMode == MODE_CHANGE_PIN) {
            setResult(RESULT_CANCELED);
            finish();
        } else {
            // Prevent bypassing lock screen
            finishAffinity();
        }
    }
}
