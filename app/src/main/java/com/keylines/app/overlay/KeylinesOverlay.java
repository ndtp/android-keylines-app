package com.keylines.app.overlay;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import com.keylines.app.KeylinesBroadcastReceiver;
import com.keylines.app.NotificationHelper;
import com.keylines.app.R;
import com.keylines.app.prefs.Preferences;
import com.keylines.lib.KeylinesData;

import java.util.List;

/**
 * Created by chris on 2017-08-06.
 */

public class KeylinesOverlay extends Service {

    public static final String ACTION_UPDATE = "com.keylines.lib.action.UPDATE_KEYLINES";
    public static final String ACTION_HIDE = "com.keylines.lib.action.HIDE_KEYLINES";
    public static final String ACTION_SHOW = "com.keylines.lib.action.SHOW_KEYLINES";
    public static final String ACTION_KILL = "com.keylines.action.KILL_KEYLINES";

    private static final int NOTIFICATION_ID = KeylinesOverlay.class.hashCode();

    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private KeylineOverlay keylinesOverlay;
    private KeylinesData keylinesData;

    private NotificationHelper notificationHelper;

    private int currentOrientation;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setTheme(R.style.AppTheme);
        notificationHelper = new NotificationHelper(this);
        if (Settings.canDrawOverlays(this)) {
            start();
        } else {
            stopSelf();
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    private void handleCommand(Intent intent) {
        if (!Settings.canDrawOverlays(this)) {
            return;
        }

        switch (intent.getAction()) {
            case ACTION_UPDATE:
                if (intent.getAction().equals(ACTION_UPDATE)) {
                    keylinesData = (KeylinesData) intent.getSerializableExtra("data_keylines");
                    keylinesOverlay.setKeylinesData(keylinesData);
                }
                break;
            case ACTION_HIDE:
                keylinesOverlay.setHideKeylines(true);
                updateNotification(false);
                break;
            case ACTION_SHOW:
                keylinesOverlay.setHideKeylines(false);
                updateNotification(true);
                break;
            case ACTION_KILL:
                this.stopSelf();
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (keylinesOverlay != null) {
            hideOverlay();
        }
        Preferences.KeylinePreferences.setKeylinesActive(this, false);
    }

    private void start() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        final Resources res = getResources();
        // TODO: handle orientation changes
        currentOrientation = res.getConfiguration().orientation;

        int typeFlag;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            typeFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else{
            typeFlag = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }

        overlayParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                typeFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        keylinesOverlay = getKeylinesOverlay();

        windowManager.addView(keylinesOverlay, overlayParams);

        keylinesOverlay.setAlpha(0f);
        keylinesOverlay.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                keylinesOverlay.animate().alpha(1f);
                keylinesOverlay.getViewTreeObserver().removeOnPreDrawListener(this);
                return false;
            }
        });

        startForeground(NOTIFICATION_ID, getPersistentNotification(true));
        updateNotification(true);

        Preferences.KeylinePreferences.setKeylinesActive(this, true);
    }

    private KeylineOverlay getKeylinesOverlay() {
        return new KeylineOverlay(getBaseContext());
    }

    private Notification getPersistentNotification(boolean actionIsHide) {
        String hideShowActionText = getString(actionIsHide ? R.string.keylines_hide : R.string.keylines_show);
        Intent hideShowIntent = new Intent(this, KeylinesBroadcastReceiver.class);
        hideShowIntent.setAction(actionIsHide ? ACTION_HIDE : ACTION_SHOW);
        PendingIntent pendingHideShowIntent = PendingIntent.getBroadcast(this, 0, hideShowIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent killIntent = new Intent(this, KeylinesBroadcastReceiver.class);
        killIntent.setAction(ACTION_KILL);
        PendingIntent pendingKillIntent = PendingIntent.getBroadcast(this, 0, killIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return notificationHelper.getNotification(getString(R.string.keylines_title), getString(R.string.keyline_service_is_running), hideShowActionText, pendingHideShowIntent, pendingKillIntent).build();
    }

    private void hideOverlay() {
        keylinesOverlay.animate().alpha(0f).withEndAction(new Runnable() {
            @Override
            public void run() {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        removeViewIfAttached(keylinesOverlay);
                        keylinesOverlay = null;
                    }
                });
            }
        });
    }

    private void removeViewIfAttached(View v) {
        if (v.isAttachedToWindow()) {
            windowManager.removeView(v);
        }
    }

    static class KeylineOverlay extends View {
        private Paint keylinePaint;
        private Paint keyAreaPaint;
        private Paint midpointPaint;

        private float density;

        private boolean hideKeylines = false;

        private KeylinesData keylinesData;

        public KeylineOverlay(Context context) {
            super(context);

            density = getResources().getDisplayMetrics().density;
            keylinePaint = new Paint();
            keylinePaint.setColor(this.getResources().getColor(R.color.keyline));
            keylinePaint.setStrokeWidth(1.5f * density);

            keyAreaPaint = new Paint();
            keyAreaPaint.setColor(this.getResources().getColor(R.color.keyline));
            keyAreaPaint.setAlpha(20);

            midpointPaint = new Paint();
            midpointPaint.setColor(this.getResources().getColor(R.color.keyline));
            midpointPaint.setAlpha(100);
        }

        public void setKeylinesData(KeylinesData keylinesData) {
            this.keylinesData = keylinesData;
            invalidate();
        }

        public void setHideKeylines(boolean hideKeylines) {
            if (this.hideKeylines != hideKeylines) {
                this.hideKeylines = hideKeylines;
                this.invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawKeylines(canvas);
        }

        private void drawKeylines(Canvas canvas) {
            if (keylinesData == null || hideKeylines) {
                return;
            }

            final int height = getHeight();
            List<KeylinesData.Keyline> keylines = keylinesData.getKeylines();
            for(KeylinesData.Keyline keyline : keylines) {
                if (keyline instanceof KeylinesData.KeylineArea) {
                    KeylinesData.KeylineArea kla = (KeylinesData.KeylineArea) keyline;
                    canvas.drawRect(DPToPixels(kla.getPosition()), 0, DPToPixels(kla.getPosition() + kla.getWidth()), height, keyAreaPaint);
                } else {
                    canvas.drawLine(DPToPixels(keyline.getPosition()), 0, DPToPixels(keyline.getPosition()), height, keylinePaint);
                }
            }
        }

        private float DPToPixels(float dp) {
            return dp * density;
        }
    }

    private void updateNotification(boolean actionIsHide) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, getPersistentNotification(actionIsHide));
    }
}
