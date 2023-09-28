package com.afex.mapx.storage;

import static com.afex.mapx.MapXConstants.API_KEY;
import static com.afex.mapx.MapXConstants.IS_LICENSE_VALID;
import static com.afex.mapx.MapXConstants.LAST_NAME_KEY;
import static com.afex.mapx.MapXConstants.LAST_UUID_KEY;
import static com.afex.mapx.MapXConstants.LICENSE_KEY;
import static com.afex.mapx.MapXConstants.PREFS_NAME;
import static com.afex.mapx.MapXConstants.SECRET_KEY;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesHelper {

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


    public void saveLicenseKey(String deviceName){;
        sharedPreferences.edit().putString(LICENSE_KEY, deviceName).apply();
    }

    public String getLicenseKey() {
        return sharedPreferences.getString(LICENSE_KEY, null);
    }


    public void setLicenseValid(boolean status){;
        sharedPreferences.edit().putBoolean(IS_LICENSE_VALID, status).apply();
    }

    public boolean isLicenseValid() {
        return sharedPreferences.getBoolean(IS_LICENSE_VALID, false);
    }
}

