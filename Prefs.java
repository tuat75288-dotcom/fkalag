package com.kestrel.lag;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String NAME = "kestrel_lag";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static int lagMs(Context c) {
        return get(c).getInt("lag_ms", 300);
    }

    public static int openMs(Context c) {
        return get(c).getInt("open_ms", 700);
    }

    public static boolean enabled(Context c) {
        return get(c).getBoolean("enabled", false);
    }

    public static void setEnabled(Context c, boolean b) {
        get(c).edit().putBoolean("enabled", b).apply();
    }

    public static void setLagMs(Context c, int v) {
        get(c).edit().putInt("lag_ms", v).apply();
    }

    public static void setOpenMs(Context c, int v) {
        get(c).edit().putInt("open_ms", v).apply();
    }
}
