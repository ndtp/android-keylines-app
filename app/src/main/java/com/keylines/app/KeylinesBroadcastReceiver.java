package com.keylines.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.AndroidException;
import android.util.Log;

import com.keylines.app.overlay.KeylinesOverlay;

import static com.keylines.app.overlay.KeylinesOverlay.ACTION_UPDATE;

/**
 * Created by chrismack on 2017-09-03.
 */

public class KeylinesBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent newIntent = new Intent(context, KeylinesOverlay.class);
        String action = intent.getAction();
        newIntent.setAction(action);

        if (intent.getAction().equals(ACTION_UPDATE)) {
            newIntent.putExtra("data_keylines", intent.getSerializableExtra("data_keylines"));
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(newIntent);
        } else {
            context.startService(newIntent);
        }
    }
}
