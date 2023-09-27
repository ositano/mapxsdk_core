package com.afex.mapx.models;

import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.TimeZone;

public class DeviceInfo {
    public static String getDeviceModel() {
        return Build.MODEL;
    }

    public static String getDeviceManufacturer() {
        return Build.MANUFACTURER;
    }

    public static String getDeviceBrand() {
        return Build.BRAND;
    }

    public static String getDeviceProduct() {
        return Build.PRODUCT;
    }

    public static String getDeviceHardware() {
        return Build.HARDWARE;
    }

    public static String getDeviceSerial() {
        return Build.SERIAL;
    }

    public static String getAndroidVersion() {
        return Build.VERSION.RELEASE;
    }

    public static int getAndroidSDKVersion() {
        return Build.VERSION.SDK_INT;
    }

    public static String getDeviceTimeZone() {
        TimeZone timeZone = TimeZone.getDefault();
        return timeZone.getID();
    }

    public static String getDeviceLanguage() {
        Locale locale = Locale.getDefault();
        return locale.getLanguage();
    }

    public static String getRAMSize() {
        try {
            File memInfoFile = new File("/proc/meminfo");
            BufferedReader br = new BufferedReader(new FileReader(memInfoFile));
            String line;

            while ((line = br.readLine()) != null) {
                if (line.contains("MemTotal:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        long totalMemoryKB = Long.parseLong(parts[1]);
                        long totalMemoryMB = totalMemoryKB / 1024;
                        return totalMemoryMB + " MB";
                    }
                }
            }

            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "N/A";
    }
}

