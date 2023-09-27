package com.afex.mapx.models;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

public class MapXBluetoothMessage {
    private final BluetoothDevice device;
    private final String dataMessage;
    public MapXBluetoothMessage(BluetoothDevice device, String dataMessage){
        this.device = device;
        this.dataMessage = dataMessage;
    }

    @NonNull
    @SuppressLint("MissingPermission")
    @Override
    public String toString(){
        return String.format("%s - %s  - %s", device.getName(), device.getAddress(), dataMessage);
    }

    public BluetoothDevice getDevice(){
        return device;
    }

    public String getDataMessage(){
        return dataMessage;
    }
}
