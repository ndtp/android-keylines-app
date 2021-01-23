package com.keylines.app.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by chris on 2017-08-06.
 */

public class Preferences {
    private static final String PREFERENCES_FILE = "com.keylines.app_preferences";

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_FILE, 0);
    }

    private static void putBoolean(Context context, String key, boolean value) {
        getSharedPreferences(context).edit().putBoolean(key, value).apply();
    }

    private static boolean getBoolean(Context context, String key, boolean defValue) {
        return getSharedPreferences(context).getBoolean(key, defValue);
    }

    public static class KeylinePreferences {
        public static final String KEY_KEYLINES_ACTIVE = "keylines_active";
        public static void setKeylinesActive(Context context, boolean active) {
            putBoolean(context, KEY_KEYLINES_ACTIVE, active);
        }
        public static boolean getKeylinesActive(Context context, boolean defValue) {
            return getBoolean(context, KEY_KEYLINES_ACTIVE, defValue);
        }

        public static final String KEY_KEYLINES_VISIBLE = "keylines_visible";
        public static void setKeyKeylinesVisible(Context context, boolean visible) {
            putBoolean(context, KEY_KEYLINES_VISIBLE, visible);
        }
        public static boolean getKeylinesVisible(Context context, boolean defValue) {
            return getBoolean(context, KEY_KEYLINES_VISIBLE, defValue);
        }
    }

    public static class RulerPreferences {
        public static final String KEY_RULER_ACTIVE = "ruler_active";

        public static void setRulerActive(Context context, boolean active) {
            putBoolean(context, KEY_RULER_ACTIVE, active);
        }

        public static boolean getRulerActive(Context context, boolean defValue) {
            return getBoolean(context, KEY_RULER_ACTIVE, defValue);
        }
    }
}