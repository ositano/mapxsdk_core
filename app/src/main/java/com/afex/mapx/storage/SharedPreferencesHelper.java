package com.afex.mapx.storage;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesHelper {
    private static final String PREFS_NAME = "MyAppPreferences";

    private static final String LAST_UUID_KEY = "last_uuids";
    private static final String LAST_NAME_KEY = "last_name";
    private static final String API_KEY = "010101";
    private static final String SECRET_KEY = "100100";

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

    public void saveLastConnectedDeviceName(String deviceName){;
        sharedPreferences.edit().putString(LAST_NAME_KEY, deviceName).apply();
    }

    public String getLastConnectedDeviceName() {
        return sharedPreferences.getString(LAST_NAME_KEY, null);
    }

    public void saveApiKey(String deviceName){;
        sharedPreferences.edit().putString(API_KEY, deviceName).apply();
    }

    public String getApiKey() {
        return sharedPreferences.getString(API_KEY, null);
    }

    public void saveSecretKey(String deviceName){;
        sharedPreferences.edit().putString(SECRET_KEY, deviceName).apply();
    }

    public String getSecretKey() {
        return sharedPreferences.getString(SECRET_KEY, null);
    }
}

