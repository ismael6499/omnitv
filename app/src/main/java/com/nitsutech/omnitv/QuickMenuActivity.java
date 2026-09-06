package com.nitsutech.omnitv;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class QuickMenuActivity extends Activity {

    private static final String TAG = "QuickMenuActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (ButtonMappingService.instance != null) {
                QuickMenuOverlay.getInstance().show(ButtonMappingService.instance);
            } else {
                QuickMenuOverlay.getInstance().show(this);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch QuickMenuOverlay from activity", e);
        }
        finish();
    }
}