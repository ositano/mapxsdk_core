package com.afex.mapx;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesHelper {
    private static final String PREFS_NAME = "MyAppPreferences";

    private static final String LAST_UUID_KEY = "last_uuids";

    private final SharedPreferences sharedPreferences;

    public SharedPreferencesHelper(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveLastConnectedDevice(String uuid){;
        sharedPreferences.edit().putString(LAST_UUID_KEY, uuid).apply();
    }

    public String getLastConnectedDevice() {
        return sharedPreferences.getString(LAST_UUID_KEY, null);
    }
}

