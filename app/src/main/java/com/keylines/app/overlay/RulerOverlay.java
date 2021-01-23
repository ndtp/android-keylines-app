package com.keylines.app.overlay;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.keylines.app.NotificationHelper;
import com.keylines.app.R;
import com.keylines.app.RulerBroadcastReceiver;
import com.keylines.app.overlay.ruler.BasicAreaPainter;
import com.keylines.app.overlay.ruler.BasicRulerView;
import com.keylines.app.prefs.Preferences;

import androidx.appcompat.widget.AppCompatTextView;


/**
 * Created by chris on 2017-08-15.
 */

public class RulerOverlay extends Service {

    private static final int NOTIFICATION_ID = RulerOverlay.class.hashCode();

    private static final String ACTION_HIDE = "com.keylines.action.RULER_HIDE";
    private static final String ACTION_SHOW = "com.keylines.action.RULER_SHOW";
    public static final String ACTION_KILL = "com.keylines.action.KILL_RULER";

    private WindowManager windowManager;
    private WindowManager.LayoutParams labelParams;
    private WindowManager.LayoutParams areaTouchTargetParams;
    private WindowManager.LayoutParams topHandleTouchTargetParams;
    private WindowManager.LayoutParams bottomHandleTouchTargetParams;
    private WindowManager.LayoutParams rulerViewParams;

    private View topHandle;
    private View bottomHandle;
    private AppCompatTextView labelView;
    private FrameLayout areaTouchTarget;
    private View topHandleTouchTarget;
    private View bottomHandleTouchTarget;
    private BasicRulerView rulerView;
    private BasicAreaPainter areaPainter;

    Rect windowRect;

    float logicalDensity;

    private int currentOrientation;

    private int distanceBetweenRulerBoundaryAndHandle;

    private int minimumHeightOfAreaInPixels;

    private NotificationHelper notificationHelper;

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
        if (intent == null || intent.getAction() == null) {
            return;
        }

        switch (intent.getAction()) {
            case ACTION_HIDE:
                removeViewsFromWindow();
                updateNotification(false);
                break;
            case ACTION_SHOW:
                addViewsToWindow();
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
        if (rulerViewParams != null) {
            hideRuler();
        }
        Preferences.RulerPreferences.setRulerActive(this, false);
    }

    private void start() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        final Resources res = getResources();
        // TODO handle orientation changes
        currentOrientation = res.getConfiguration().orientation;

        DisplayMetrics dm = res.getDisplayMetrics();
        logicalDensity = dm.density;
        minimumHeightOfAreaInPixels = (int) (2 * logicalDensity);

        createViews();
        addViewsToWindow();

        startForeground(NOTIFICATION_ID, getPersistentNotification(true));
        updateNotification(true);

        Preferences.RulerPreferences.setRulerActive(this, true);
    }

    private void createViews() {
        final Resources res = getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        logicalDensity = dm.density;
        minimumHeightOfAreaInPixels = (int) (2 * logicalDensity);

        int rulerInitialHeight = res.getDimensionPixelSize(R.dimen.ruler_starting_size);

        int typeFlag;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            typeFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else{
            typeFlag = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }

        int systemAlertTypeFlag;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            systemAlertTypeFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else{
            systemAlertTypeFlag = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }

        //region Create area touch target view and params
        if (areaTouchTargetParams == null) {
            areaTouchTargetParams = new WindowManager.LayoutParams(
                    dm.widthPixels, rulerInitialHeight,
                    systemAlertTypeFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            areaTouchTargetParams.gravity = Gravity.TOP | Gravity.LEFT;
            areaTouchTargetParams.x = 0;
            areaTouchTargetParams.y = (dm.heightPixels / 2) - (rulerInitialHeight / 2);
        }

        areaTouchTarget = getAreaTouchTarget();
        areaTouchTarget.setOnTouchListener(areaOnTouchListener);
        areaTouchTarget.setAlpha(0f);
        //endregion

        int handleSize = res.getDimensionPixelSize(R.dimen.ruler_handle_size);
        distanceBetweenRulerBoundaryAndHandle = res.getDimensionPixelSize(R.dimen.ruler_distance_between_ruler_area_and_handle);

        //region Create top handle
        topHandle = View.inflate(this, R.layout.ruler_handle_top, null);
        ViewGroup.LayoutParams topHandleFrameParams = new ViewGroup.LayoutParams(handleSize, handleSize);
        topHandle.setLayoutParams(topHandleFrameParams);
        //endregion

        //region Create bottom handle
        bottomHandle = View.inflate(this, R.layout.ruler_handle_top, null);
        ViewGroup.LayoutParams bottomHandleFrameParams = new ViewGroup.LayoutParams(handleSize, handleSize);
        bottomHandle.setLayoutParams(bottomHandleFrameParams);
        //endregion

        int topHandleX;
        int topHandleY;
        int bottomHandleX;
        int bottomHandleY;

        if (topHandleTouchTargetParams == null) {
            topHandleX = (dm.widthPixels / 2) - (handleSize / 2);
            topHandleY = (dm.heightPixels / 2) - (rulerInitialHeight / 2) - distanceBetweenRulerBoundaryAndHandle - handleSize;
            bottomHandleX = topHandleX;
            bottomHandleY = (dm.heightPixels / 2) + (rulerInitialHeight / 2) + distanceBetweenRulerBoundaryAndHandle;
        } else {
            topHandleX = topHandleTouchTargetParams.x;
            topHandleY = topHandleTouchTargetParams.y;
            bottomHandleX = bottomHandleTouchTargetParams.x;
            bottomHandleY = bottomHandleTouchTargetParams.y;
        }

        //region Create area painter
        areaPainter = new BasicAreaPainter(this);
        final ViewGroup.LayoutParams areaPainterParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        areaPainter.setLayoutParams(areaPainterParams);
        areaPainter.setArea(topHandleY + handleSize + distanceBetweenRulerBoundaryAndHandle, bottomHandleY - distanceBetweenRulerBoundaryAndHandle);
        //endregion

        //region Create ruler view
        if (rulerViewParams == null) {
            rulerViewParams = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    typeFlag,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            rulerViewParams.gravity = Gravity.TOP | Gravity.LEFT;
        }

        rulerView = new BasicRulerView(this, areaPainter, topHandle, bottomHandle);
        rulerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rulerView.updateTopHandlePosition(topHandleX, topHandleY, false);
        rulerView.updateBottomHandlePosition(bottomHandleX, bottomHandleY, false);
        //endregion

        //region Create top handle touch target
        if (topHandleTouchTargetParams == null) {
            topHandleTouchTargetParams = new WindowManager.LayoutParams(
                    handleSize, handleSize,
                    systemAlertTypeFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            topHandleTouchTargetParams.gravity = Gravity.TOP | Gravity.LEFT;
            topHandleTouchTargetParams.x = topHandleX;
            topHandleTouchTargetParams.y = topHandleY;
        }

        topHandleTouchTarget = View.inflate(this, R.layout.ruler_handle_top, null);
        topHandleTouchTarget.setAlpha(0f);
        topHandleTouchTarget.setOnTouchListener(topHandleOnTouchListener);

        //endregion

        //region Create bottom handle touch target
        if (bottomHandleTouchTargetParams == null) {
            bottomHandleTouchTargetParams = new WindowManager.LayoutParams(
                    handleSize, handleSize,
                    systemAlertTypeFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            bottomHandleTouchTargetParams.gravity = Gravity.TOP | Gravity.LEFT;
            bottomHandleTouchTargetParams.x = topHandleX;
            bottomHandleTouchTargetParams.y = bottomHandleY;
        }

        bottomHandleTouchTarget = View.inflate(this, R.layout.ruler_handle_top, null);
        bottomHandleTouchTarget.setAlpha(0f);
        bottomHandleTouchTarget.setOnTouchListener(bottomHandleOnTouchListener);

        //endregion

        //region Create distance label
        if (labelParams == null) {
            labelParams = new WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    typeFlag,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            labelParams.gravity = Gravity.TOP | Gravity.RIGHT;
        }

        labelView = (AppCompatTextView) View.inflate(this, R.layout.distance_label, null);

        labelView.setAlpha(0);
        labelView.setPadding(0, getResources().getDimensionPixelSize(R.dimen.app_padding_large), getResources().getDimensionPixelSize(R.dimen.app_padding_large), 0);
        labelView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                labelView.animate().alpha(1f);
                labelView.getViewTreeObserver().removeOnPreDrawListener(this);
                return false;
            }
        });

        updateLabel();
        //endregion

        rulerView.setRulerVisualsListener(new BasicRulerView.BasicRulerVisualsListener() {
            @Override
            public void topHandleMoved(int x, int y) {
                topHandleTouchTargetParams.x = x;
                topHandleTouchTargetParams.y = y;
                windowManager.updateViewLayout(topHandleTouchTarget, topHandleTouchTargetParams);

                if (!areaTouchTarget.isPressed()) {
                    areaTouchTargetParams.y = topHandleTouchTargetParams.y + topHandleTouchTargetParams.height + distanceBetweenRulerBoundaryAndHandle;
                    areaTouchTargetParams.height = bottomHandleTouchTargetParams.y - distanceBetweenRulerBoundaryAndHandle - areaTouchTargetParams.y;
                    if (areaTouchTargetParams.height < minimumHeightOfAreaInPixels) {
                        areaTouchTargetParams.height = minimumHeightOfAreaInPixels;
                    }
                    windowManager.updateViewLayout(areaTouchTarget, areaTouchTargetParams);

                    rulerView.setArea(areaTouchTargetParams.y, areaTouchTargetParams.y + areaTouchTargetParams.height);
                    updateLabel();
                }
            }

            @Override
            public void bottomHandleMoved(int x, int y) {
                bottomHandleTouchTargetParams.x = x;
                bottomHandleTouchTargetParams.y = y;
                windowManager.updateViewLayout(bottomHandleTouchTarget, bottomHandleTouchTargetParams);

                if (!areaTouchTarget.isPressed()) {
                    areaTouchTargetParams.y = topHandleTouchTargetParams.y + topHandleTouchTargetParams.height + distanceBetweenRulerBoundaryAndHandle;
                    areaTouchTargetParams.height = bottomHandleTouchTargetParams.y - distanceBetweenRulerBoundaryAndHandle - areaTouchTargetParams.y;
                    windowManager.updateViewLayout(areaTouchTarget, areaTouchTargetParams);

                    rulerView.setArea(areaTouchTargetParams.y, areaTouchTargetParams.y + areaTouchTargetParams.height);
                    updateLabel();
                }
            }
        });
    }

    private void addViewsToWindow() {
        windowManager.addView(rulerView, rulerViewParams);
        windowManager.addView(areaTouchTarget, areaTouchTargetParams);
        windowManager.addView(topHandleTouchTarget, topHandleTouchTargetParams);
        windowManager.addView(bottomHandleTouchTarget, bottomHandleTouchTargetParams);
        windowManager.addView(labelView, labelParams);
    }

    private void removeViewsFromWindow() {
        windowManager.removeView(rulerView);
        windowManager.removeView(areaTouchTarget);
        windowManager.removeView(topHandleTouchTarget);
        windowManager.removeView(bottomHandleTouchTarget);
        windowManager.removeView(labelView);
    }

    private Notification getPersistentNotification(boolean actionIsHide) {
        String hideShowActionText = getString(actionIsHide ? R.string.ruler_hide : R.string.ruler_show);
        Intent hideShowIntent = new Intent(this, RulerBroadcastReceiver.class);
        hideShowIntent.setAction(actionIsHide ? ACTION_HIDE : ACTION_SHOW);
        PendingIntent pendingHideShowIntent = PendingIntent.getBroadcast(this, 0, hideShowIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent killIntent = new Intent(this, RulerBroadcastReceiver.class);
        killIntent.setAction(ACTION_KILL);
        PendingIntent pendingKillIntent = PendingIntent.getBroadcast(this, 0, killIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return notificationHelper.getNotification(getString(R.string.ruler_title), getString(R.string.ruler_service_is_running), hideShowActionText, pendingHideShowIntent, pendingKillIntent).build();
    }

    private void showRuler() {
        createViews();
        addViewsToWindow();
        updateNotification(true);
    }

    private FrameLayout getAreaTouchTarget() {
        return (FrameLayout) View.inflate(this, R.layout.ruler_area, null);
    }

    private void hideRuler() {
        if (areaTouchTarget == null) {
            return;
        }

        areaTouchTarget.animate().alpha(0f).withEndAction(new Runnable() {
            @Override
            public void run() {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        removeViewIfAttached(areaTouchTarget);
                        areaTouchTarget = null;
                    }
                });
            }
        });

        labelView.animate().alpha(0f).withEndAction(new Runnable() {
            @Override
            public void run() {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        removeViewIfAttached(labelView);
                        labelView = null;
                    }
                });
            }
        });

        rulerView.animate().alpha(0f).withEndAction(new Runnable() {
            @Override
            public void run() {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        removeViewIfAttached(rulerView);
                        rulerView = null;
                    }
                });
            }
        });

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                removeViewIfAttached(topHandleTouchTarget);
                topHandleTouchTarget = null;
            }
        });

        handler.post(new Runnable() {
            @Override
            public void run() {
                removeViewIfAttached(bottomHandleTouchTarget);
                bottomHandleTouchTarget = null;
            }
        });
    }

    private void removeViewIfAttached(View v) {
        if (v.isAttachedToWindow()) {
            windowManager.removeView(v);
        }
    }

    private View.OnTouchListener areaOnTouchListener = new View.OnTouchListener() {
        int lastY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // brighten ruler
                    areaTouchTarget.setPressed(true);
                    lastY = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    updateRulerPosition(0, (int) event.getRawY() - lastY);
                    lastY = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_UP:
                    areaTouchTarget.setPressed(false);
                    break;
            }

            return true;
        }
    };

    private View.OnTouchListener topHandleOnTouchListener = new View.OnTouchListener() {

        boolean lockedToXAxis = false;
        boolean lockedToYAxis = false;
        int initialX, initialY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // brighten ruler
                    initialX = (int) event.getRawX();
                    initialY = (int) event.getRawY();
                    topHandle.setPressed(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    // getRawY() is in screen coordinates while
                    int windowY = translateFromScreenCoordinatesToWindow((int) event.getRawY());
                    int diffX = initialX - (int) event.getRawX();
                    int diffY = initialY - (int) event.getRawY();

                    if (!lockedToXAxis
                            && !lockedToYAxis
                            && (Math.abs(diffX) > 10 || Math.abs(diffY) > 10)) {
                        if (Math.abs(diffX) > Math.abs(diffY)) {
                            lockedToXAxis = true;
                        } else {
                            lockedToYAxis = true;
                        }
                    }

                    if (lockedToXAxis) {
                        topHandleDraggedTo((int) event.getRawX(), topHandleTouchTargetParams.y + topHandleTouchTarget.getHeight() / 2);
                    } else if (lockedToYAxis){
                        topHandleDraggedTo((int) event.getRawX(), windowY);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    lockedToXAxis = false;
                    lockedToYAxis = false;
                    topHandle.setPressed(false);
                    break;
            }

            return true;
        }
    };

    private View.OnTouchListener bottomHandleOnTouchListener = new View.OnTouchListener() {

        boolean lockedToXAxis = false;
        boolean lockedToYAxis = false;
        int initialX, initialY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // brighten ruler
                    initialX = (int) event.getRawX();
                    initialY = (int) event.getRawY();
                    bottomHandle.setPressed(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    // getRawY() is in screen coordinates while
                    int windowY = translateFromScreenCoordinatesToWindow((int) event.getRawY());
                    int diffX = initialX - (int) event.getRawX();
                    int diffY = initialY - (int) event.getRawY();

                    if (!lockedToXAxis
                            && !lockedToYAxis
                            && (Math.abs(diffX) > 10 || Math.abs(diffY) > 10)) {
                        if (Math.abs(diffX) > Math.abs(diffY)) {
                            lockedToXAxis = true;
                        } else {
                            lockedToYAxis = true;
                        }
                    }

                    if (lockedToXAxis) {
                        bottomHandleDraggedTo((int) event.getRawX(), bottomHandleTouchTargetParams.y + bottomHandleTouchTarget.getHeight() / 2);
                    } else if (lockedToYAxis){
                        bottomHandleDraggedTo((int) event.getRawX(), windowY);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    lockedToXAxis = false;
                    lockedToYAxis = false;
                    bottomHandle.setPressed(false);
                    areaTouchTargetParams.height = bottomHandleTouchTargetParams.y - distanceBetweenRulerBoundaryAndHandle - areaTouchTargetParams.y;
                    windowManager.updateViewLayout(areaTouchTarget, areaTouchTargetParams);
                    break;
            }

            return true;
        }
    };

    private int translateFromScreenCoordinatesToWindow(int screenY) {
        if (windowRect == null) {
            getWindowRect();
        }

        return screenY - windowRect.top;
    }

    private void getWindowRect() {
        if (windowRect == null) {
            windowRect = new Rect();
            rulerView.getWindowVisibleDisplayFrame(windowRect);
        }
    }

    private void updateRulerPosition(int dx, int dy) {
        if (windowRect == null) {
            getWindowRect();
        }

        areaTouchTargetParams.y += dy;
        areaTouchTargetParams.y = Math.max(0, Math.min((windowRect.bottom - windowRect.top) - areaTouchTargetParams.height, areaTouchTargetParams.y));
        windowManager.updateViewLayout(areaTouchTarget, areaTouchTargetParams);

        rulerView.setArea(areaTouchTargetParams.y, areaTouchTargetParams.y + areaTouchTargetParams.height);
        rulerView.updateTopHandlePosition(topHandleTouchTargetParams.x, areaTouchTargetParams.y - distanceBetweenRulerBoundaryAndHandle - topHandleTouchTargetParams.height, true);
        rulerView.updateBottomHandlePosition(bottomHandleTouchTargetParams.x, areaTouchTargetParams.y + areaTouchTargetParams.height + distanceBetweenRulerBoundaryAndHandle, true);
    }

    private void topHandleDraggedTo(int x, int y) {
        x = x - topHandleTouchTargetParams.width / 2;
        y = y - topHandleTouchTargetParams.height / 2;

        // Check for max and mins
        if (windowRect == null) {
            getWindowRect();
        }
        x = Math.max(0, Math.min(windowRect.right - topHandleTouchTargetParams.width, x));
        y = Math.max(0, Math.min(windowRect.bottom - windowRect.top - topHandleTouchTargetParams.height - distanceBetweenRulerBoundaryAndHandle - minimumHeightOfAreaInPixels, y));

        rulerView.updateTopHandlePosition(x, y, true);

        int distanceBetweenTopAndBottonHandles = bottomHandleTouchTargetParams.y - distanceBetweenRulerBoundaryAndHandle*2 - topHandleTouchTargetParams.height - y;

        if ( distanceBetweenTopAndBottonHandles < minimumHeightOfAreaInPixels) {
            rulerView.updateBottomHandlePosition(bottomHandleTouchTargetParams.x, y + topHandleTouchTargetParams.height + distanceBetweenRulerBoundaryAndHandle*2 + minimumHeightOfAreaInPixels, true);
        }
    }

    private void bottomHandleDraggedTo(int x, int y) {
        x = x - bottomHandleTouchTargetParams.width / 2;
        y = y - bottomHandleTouchTargetParams.height / 2;

        // Check for max and mins
        if (windowRect == null) {
            getWindowRect();
        }
        x = Math.max(0, Math.min(windowRect.right - bottomHandleTouchTargetParams.width, x));
        y = Math.max(distanceBetweenRulerBoundaryAndHandle + minimumHeightOfAreaInPixels, Math.min(windowRect.bottom - windowRect.top - bottomHandleTouchTargetParams.height, y));

        rulerView.updateBottomHandlePosition(x, y, true);

        int distanceBetweenTopAndBottonHandles = y - distanceBetweenRulerBoundaryAndHandle*2 - topHandleTouchTargetParams.height - topHandleTouchTargetParams.y;
        if (distanceBetweenTopAndBottonHandles < minimumHeightOfAreaInPixels) {
            rulerView.updateTopHandlePosition(topHandleTouchTargetParams.x, y - distanceBetweenRulerBoundaryAndHandle*2 - topHandleTouchTargetParams.height - minimumHeightOfAreaInPixels, true);
        }
    }

    private void updateLabel() {
        int currentHeightDp = (int) ((areaTouchTargetParams.height / logicalDensity) + 0.5f);
        labelView.setText(Integer.toString(currentHeightDp));
    }

    private void updateNotification(boolean actionIsHide) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, getPersistentNotification(actionIsHide));
    }
}