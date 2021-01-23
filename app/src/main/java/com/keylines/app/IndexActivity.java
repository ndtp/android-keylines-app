package com.keylines.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;

import com.keylines.app.overlay.KeylinesOverlay;
import com.keylines.app.overlay.RulerOverlay;
import com.keylines.app.prefs.Preferences;
import com.keylines.lib.KeylinesData;
import com.keylines.lib.KeylinesServiceUtility;

import androidx.appcompat.app.AppCompatActivity;

public class IndexActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_OVERLAY_PERMISSION_FOR_KEYLINES = 1;
    private static final int REQUEST_OVERLAY_PERMSSISION_FOR_RULER = 2;

    private boolean keylinesActive = false;
    private Button keylinesTurnOnButton;
    private Button keylinesTurnOffButton;

    private boolean rulerActive = false;
    private Button rulerTurnOnButton;
    private Button rulerTurnOffButton;

    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overlay_index);

        keylinesTurnOnButton = (Button) findViewById(R.id.keylines_turn_on);
        keylinesTurnOnButton.setOnClickListener(this);
        keylinesTurnOffButton = (Button) findViewById(R.id.keylines_turn_off);
        keylinesTurnOffButton.setOnClickListener(this);

        rulerTurnOnButton = (Button) findViewById(R.id.ruler_turn_on);
        rulerTurnOnButton.setOnClickListener(this);
        rulerTurnOffButton = (Button) findViewById(R.id.ruler_turn_off);
        rulerTurnOffButton.setOnClickListener(this);

        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                updateState();
            }
        };

        Preferences.getSharedPreferences(this).registerOnSharedPreferenceChangeListener(listener);
        updateState();
    }

    private void updateState() {
        keylinesActive = Preferences.KeylinePreferences.getKeylinesActive(this, false);
        keylinesTurnOnButton.setVisibility(keylinesActive ? View.GONE : View.VISIBLE);
        keylinesTurnOffButton.setVisibility(keylinesActive ? View.VISIBLE : View.GONE);
        rulerActive = Preferences.RulerPreferences.getRulerActive(this, false);
        rulerTurnOnButton.setVisibility(rulerActive ? View.GONE : View.VISIBLE);
        rulerTurnOffButton.setVisibility(rulerActive ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean handled = false;
        if (requestCode == REQUEST_OVERLAY_PERMISSION_FOR_KEYLINES) {
            checkForPermissionAfterDelay(this::startKeylinesService);
            handled = true;
        } else if (requestCode == REQUEST_OVERLAY_PERMSSISION_FOR_RULER) {
            checkForPermissionAfterDelay(this::startRulerService);
            handled = true;
        }

        if (!handled) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Settings.canDrawOverlays() returns false for a while even if the user granted permission :(
     *
     * @param starter the service to start
     */
    protected void checkForPermissionAfterDelay(final ServiceStarter starter) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Settings.canDrawOverlays(IndexActivity.this)) {
                    starter.start();
                }
            }
        }, 5000);
    }


    @Override
    public void onClick(View v) {
        if (v == keylinesTurnOnButton && !keylinesActive) {
            if (Settings.canDrawOverlays(this)) {
                startKeylinesService();
            } else {
                Intent closeDialogsIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                sendBroadcast(closeDialogsIntent);
                Intent newIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(newIntent, REQUEST_OVERLAY_PERMISSION_FOR_KEYLINES);
            }
        } else if (v == keylinesTurnOffButton && keylinesActive) {
            stopKeylinesService();
        } else if (v == rulerTurnOnButton && !rulerActive) {
            if (Settings.canDrawOverlays(this)) {
                startRulerService();
            } else {
                Intent closeDialogsIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                sendBroadcast(closeDialogsIntent);
                Intent newIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(newIntent, REQUEST_OVERLAY_PERMSSISION_FOR_RULER);
            }
        } else if (v == rulerTurnOffButton && rulerActive) {
            stopRulerService();
        }
    }

    private KeylinesData getKeylinesData() {
        KeylinesData gld = new KeylinesData();

        // Add filled area with edge line on left side of the screen
        int dp = 16;
        gld.addKeylineArea(0, dp);

        // Add filled area with edge line on right side of the screen
        Resources res = getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        float pixelsFromRightEdge = dp * dm.density;
        float pixelsFromLeftEdge = dm.widthPixels - pixelsFromRightEdge;
        float DPFromLeftEdge = pixelsFromLeftEdge / dm.density;
        gld.addKeylineArea(DPFromLeftEdge, dp);
        gld.addKeyline(DPFromLeftEdge);

        return gld;
    }

    private void startKeylinesService() {
        Intent updateIntent = KeylinesServiceUtility.updateKeylines(getKeylinesData());
        this.sendBroadcast(updateIntent);
        keylinesActive = true;
    }

    private void stopKeylinesService() {
        Intent newIntent = new Intent(this, KeylinesOverlay.class);
        stopService(newIntent);
        Preferences.KeylinePreferences.setKeylinesActive(this, false);
    }

    private void startRulerService() {
        Intent newIntent = new Intent(this, RulerOverlay.class);
        this.startService(newIntent);
        rulerActive = true;
    }

    private void stopRulerService() {
        Intent newIntent = new Intent(this, RulerOverlay.class);
        stopService(newIntent);
        Preferences.RulerPreferences.setRulerActive(this, false);
    }

    protected interface ServiceStarter {
        void start();
    }
}
