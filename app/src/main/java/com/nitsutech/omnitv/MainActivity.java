package com.nitsutech.omnitv;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start persistent notification service
        Intent serviceIntent = new Intent(this, NotificationService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 101);
            } else {
                checkAccessibilityAndToggle();
            }
        } else {
            checkAccessibilityAndToggle();
        }
    }

    private void checkAccessibilityAndToggle() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable 'OmniTV' in Accessibility Settings for remote button support",
                    Toast.LENGTH_LONG).show();
        }
        performToggleAndFinish();
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + ButtonMappingService.class.getName();
        String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(service);
    }

    private void performToggleAndFinish() {
        ToggleUtils.toggleGrayscale(this);
        // Immediately close the activity to keep it headless
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // After permission dialog, run the toggle logic and finish
        performToggleAndFinish();
    }
}
