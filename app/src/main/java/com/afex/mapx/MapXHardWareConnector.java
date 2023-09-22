// Copyright 2023, Charles Weinberger & Paul DeMarco.
// All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.afex.mapx;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.lang.reflect.Method;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MapXHardWareConnector implements  ActivityCompat.OnRequestPermissionsResultCallback
{
    private static final String TAG = "MapXHardWareConnector";

    private LogLevel logLevel = LogLevel.DEBUG;

    private Context context;
    private Activity activity;
    private EventEmitter emitter;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    static final private String CCCD = "00002902-0000-1000-8000-00805f9b34fb";

    private final Map<String, BluetoothGatt> mConnectedDevices = new ConcurrentHashMap<>();
    private final Map<String, Integer> mMtu = new ConcurrentHashMap<>();
    private final Map<String, Boolean> mAutoConnect = new ConcurrentHashMap<>();

    private int lastEventId = 1452;
    private final Map<Integer, OperationOnPermission> operationsOnPermission = new HashMap<>();

    private final int enableBluetoothRequestCode = 1879842617;

    private interface OperationOnPermission {
        void op(boolean granted, String permission);
    }

    public MapXHardWareConnector(Context context, Activity activity, EventEmitter emitter) {
        this.context = context;
        this.activity = activity;
        this.emitter = emitter;

        IntentFilter filterAdapter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        this.context.registerReceiver(mBluetoothAdapterStateReceiver, filterAdapter);

        IntentFilter filterBond = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        this.context.registerReceiver(mBluetoothBondStateReceiver, filterBond);

        // initialize adapter
        if (mBluetoothAdapter == null) {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  initializing BluetoothAdapter");
            mBluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager != null ? mBluetoothManager.getAdapter() : null;
        }
    }

    public void releaseResources(){
        disconnectAllDevices(true /* alsoClose? */);

        context.unregisterReceiver(mBluetoothBondStateReceiver);
        context.unregisterReceiver(mBluetoothAdapterStateReceiver);
        context = null;

        mBluetoothAdapter = null;
        mBluetoothManager = null;
    }

    public String uuid128(UUID obj)
    {
        String uuid = obj.toString();

        if (uuid.length() == 4)
        {
            // 16-bit uuid
            return String.format("0000%s-0000-1000-8000-00805F9B34FB", uuid).toLowerCase();
        } 
        else if (uuid.length() == 8)
        {
            // 32-bit uuid
            return String.format("%s-0000-1000-8000-00805F9B34FB", uuid).toLowerCase();
        }
        else
        {
            // 128-bit uuid
            return uuid.toLowerCase();
        }
    }


    ////////////////////////////////////////////////////////////
    // ███    ███  ███████  ████████  ██   ██   ██████   ██████
    // ████  ████  ██          ██     ██   ██  ██    ██  ██   ██
    // ██ ████ ██  █████       ██     ███████  ██    ██  ██   ██
    // ██  ██  ██  ██          ██     ██   ██  ██    ██  ██   ██
    // ██      ██  ███████     ██     ██   ██   ██████   ██████
    //
    //  ██████   █████   ██       ██
    // ██       ██   ██  ██       ██
    // ██       ███████  ██       ██
    // ██       ██   ██  ██       ██
    //  ██████  ██   ██  ███████  ███████


    public boolean isBluetoothAdapterAvailable(){
        return mBluetoothAdapter != null;
    }

    @SuppressLint("MissingPermission")
    public String getBluetoothAdapterName(){
        return mBluetoothAdapter != null ? mBluetoothAdapter.getName() : "N/A";
    }

    public boolean getBluetoothAdapterState(){
        int adapterState = -1; // unknown
        try {
            adapterState = mBluetoothAdapter.getState();
        } catch (Exception ignored) {

        }
        return adapterState == BluetoothAdapter.STATE_ON;
    }

    @SuppressLint("MissingPermission")
    public void turnOnBluetoothAdapter(){
        ArrayList<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 31) { // Android 12 (October 2021)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (Build.VERSION.SDK_INT <= 30) { // Android 11 (September 2020)
            permissions.add(Manifest.permission.BLUETOOTH);
        }

        ensurePermissions(permissions, (granted, perm) -> {
            if (mBluetoothAdapter.isEnabled()) {
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "adapterState");
                emitObject.put("data", true);
                emitter.success(emitObject);
                return;
            }

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableBtIntent, enableBluetoothRequestCode);
            return;
        });
    }

    @SuppressLint("MissingPermission")
    public void turnOffBluetoothAdapter(){
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) { // Android 12 (October 2021)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (Build.VERSION.SDK_INT <= 30) { // Android 11 (September 2020)
            permissions.add(Manifest.permission.BLUETOOTH);
        }
        ensurePermissions(permissions, (granted, perm) -> {

            if (!mBluetoothAdapter.isEnabled()) {
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "adapterState");
                emitObject.put("data", false);
                emitter.success(emitObject);
                return;
            }

            // this is deprecated in API level 33.
            boolean disabled = mBluetoothAdapter.disable();
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "adapterState");
            emitObject.put("data", disabled);
            emitter.success(emitObject);
            return;
        });
    }

    @SuppressLint("MissingPermission")
    public void startBluetoothAdapterScan(){
        ArrayList<String> permissions = new ArrayList<>();

        /*
          static const lowPower = ScanMode(0);
          static const balanced = ScanMode(1);
          static const lowLatency = ScanMode(2);
          static const opportunistic = ScanMode(-1);
         */
        // see: BmScanSettings
        List<String> serviceUuids = new ArrayList<>();//(List<String>) payloads.get("service_uuids");
        List<String> macAddresses = new ArrayList<>();//(List<String>) payloads.get("mac_addresses");
        boolean allowDuplicates =    false;// (boolean) payloads.get("allow_duplicates");
        int scanMode =          1;//          (int) payloads.get("android_scan_mode");
        boolean usesFineLocation = true;//(boolean) payloads.get("android_uses_fine_location");

        if (Build.VERSION.SDK_INT >= 31) { // Android 12 (October 2021)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            if (usesFineLocation) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            // it is unclear why this is needed, but some phones throw a
            // SecurityException AdapterService getRemoteName, without it
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (Build.VERSION.SDK_INT <= 30) { // Android 11 (September 2020)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        ensurePermissions(permissions, (granted, perm) -> {

            if (!granted) {
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "adapterScanning");
                emitObject.put("status", false);
                emitObject.put("error", String.format("Grant %s permission", perm));
                emitter.success(emitObject);
                return;
            }

            BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            if(scanner == null) {
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "adapterScanning");
                emitObject.put("status", false);
                emitObject.put("error", "check if the current device bluetooth is turned on");
                emitter.success(emitObject);
                return;
            }

            ScanSettings settings;
            if (Build.VERSION.SDK_INT >= 26) { // Android 8.0 (August 2017)
                settings = new ScanSettings.Builder()
                        .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                        .setLegacy(false)
                        .setScanMode(scanMode)
                        .build();
            } else {
                settings = new ScanSettings.Builder()
                        .setScanMode(scanMode).build();
            }

            List<ScanFilter> filters = new ArrayList<>();

            for (int i = 0; i < macAddresses.size(); i++) {
                String macAddress = macAddresses.get(i);
                ScanFilter f = new ScanFilter.Builder().setDeviceAddress(macAddress).build();
                filters.add(f);
            }

            for (int i = 0; i < serviceUuids.size(); i++) {
                String uuid = serviceUuids.get(i);
                ScanFilter f = new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuid)).build();
                filters.add(f);
            }
            scanner.startScan(filters, settings, getScanCallback());
        });
    }

    @SuppressLint("MissingPermission")
    public void stopBluetoothAdapterScan(){
        BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
        if(scanner != null) {
            scanner.stopScan(getScanCallback());
        }
        Map<String, Object> emitObject = new HashMap<>();
        emitObject.put("type", "adapterScanning");
        emitObject.put("status", false);
        emitObject.put("error", "Stopped scanning");
        emitter.success(emitObject);
    }

    @SuppressLint("MissingPermission")
    public void getConnectedSystemDevices(){
        ArrayList<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= 31) { // Android 12 (October 2021)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        ensurePermissions(permissions, (granted, perm) -> {
            if (!granted) {
                List<String> deviceList = new ArrayList<>();
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "connectedDevices");
                emitObject.put("status", false);
                emitObject.put("data", deviceList);
                emitObject.put("error", String.format("Grant %s permission", perm));
                emitter.success(emitObject);
                return;
            }

            // this includes devices connected by other apps
            List<BluetoothDevice> devices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);

            List<HashMap<String, Object>> devList = new ArrayList<HashMap<String, Object>>();
            for (BluetoothDevice d : devices) {
                devList.add(bmBluetoothDevice(d));
            }


            List<String> deviceList = new ArrayList<>();
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "connectedDevices");
            emitObject.put("status", true);
            emitObject.put("data", deviceList);
            emitObject.put("error", "");
            emitter.success(emitObject);
        });
    }

    @SuppressLint("MissingPermission")
    public void connectToBluetoothDevice(String macAddress, boolean autoConnect){
        BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAddress);
        HashMap<String, Object> deviceMap = bmBluetoothDevice(bluetoothDevice);
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) { // Android 12 (October 2021)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }


        ensurePermissions(permissions, (granted, perm) -> {
            if (!granted) {
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "adapterConnection");
                emitObject.put("data", deviceMap);
                emitObject.put("status", false);
                emitObject.put("error", String.format("Grant %s permission", perm));
                emitter.success(emitObject);
                return;
            }

            // remember autoconnect
            mAutoConnect.put(macAddress, autoConnect);

            // already connected?
            BluetoothGatt gatt = mConnectedDevices.get(macAddress);
            if (gatt != null) {
                log(LogLevel.DEBUG, "MapXHardWareConnector -  already connected");
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "adapterConnection");
                emitObject.put("status", true);
                emitObject.put("data", deviceMap);
                emitter.success(emitObject);
                return;
            }

            // connect with new gatt
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(macAddress);
            if (Build.VERSION.SDK_INT >= 23) { // Android 6.0 (October 2015)
                gatt = device.connectGatt(context, autoConnect, mGattCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                gatt = device.connectGatt(context, autoConnect, mGattCallback);
            }

            // error check
            if (gatt == null) {
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "adapterConnection");
                emitObject.put("status", false);
                emitObject.put("data", deviceMap);
                emitObject.put("error", "Unable to connect");
                emitter.success(emitObject);
                return;
            }

            log(LogLevel.DEBUG, "MapXHardWareConnector -  established connection");
            deviceMap.put("isConnected", true);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "adapterConnection");
            emitObject.put("status", true);
            emitObject.put("data", deviceMap);
            emitter.success(emitObject);
        });
    }

    @SuppressLint("MissingPermission")
    public boolean disconnectFromBluetoothDevice(String macAddress){
        BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(macAddress);
        HashMap<String, Object> deviceMap = bmBluetoothDevice(bluetoothDevice);
        // already disconnected?
        BluetoothGatt gatt = mConnectedDevices.get(macAddress);
        if (gatt == null) {
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "Disconnect - device already disconnected");
            emitter.success(emitObject);

            Map<String, Object> responseObject = new HashMap<>();
            responseObject.put("type", "adapterConnection");
            responseObject.put("status", false);
            responseObject.put("data", deviceMap);
            emitter.success(responseObject);
            log(LogLevel.DEBUG, "MapXHardWareConnector -  already disconnected");// no work to do
            return true;
        }

        // calling disconnect explicitly turns off autoconnect.
        // this allows gatt resources to be reclaimed
        mAutoConnect.put(macAddress, false);
        gatt.disconnect();

        Map<String, Object> emitObject = new HashMap<>();
        emitObject.put("type", "adapterConnection");
        emitObject.put("status", false);
        emitObject.put("data", deviceMap);
        emitter.success(emitObject);
        return true;
    }

    @SuppressLint("MissingPermission")
    public boolean discoverServices(String macAddress){
        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(macAddress);
        if(gatt == null) {
            //result.error("discoverServices", "device is disconnected", null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "discoverServices - device already disconnected");
            emitter.success(emitObject);
            return false;
        }

        // discover services
        //result.error("discoverServices", "gatt.discoverServices() returned false", null);
        return gatt.discoverServices();
    }

    @SuppressLint("MissingPermission")
    public boolean readCharacteristics(HashMap<String, Object> payloads){
        // see: BmReadCharacteristicRequest
        String remoteId =             (String) payloads.get("mac_address");
        String serviceUuid =          (String) payloads.get("service_uuid");
        String secondaryServiceUuid = (String) payloads.get("secondary_service_uuid");
        String characteristicUuid =   (String) payloads.get("characteristic_uuid");

        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(remoteId);
        if(gatt == null) {
            //result.error("readCharacteristic", "device is disconnected", null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "ReadCharacteristics - device already disconnected");
            emitter.success(emitObject);
            return false;
        }

        // find characteristic
        ChrFound found = locateCharacteristic(gatt, serviceUuid, secondaryServiceUuid, characteristicUuid);
        if (found.error != null) {
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "ReadCharacteristics - "+found.error);
            emitter.success(emitObject);
            //result.error("readCharacteristic", found.error, null);
            return false;
        }

        BluetoothGattCharacteristic characteristic = found.characteristic;

        // check readable
        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "Read characteristic - The READ property is not supported by this BLE characteristic");
            emitter.success(emitObject);
            //result.error("readCharacteristic", "The READ property is not supported by this BLE characteristic", null);
            return false;
        }

        // read
        //result.error("readCharacteristic", "gatt.readCharacteristic() returned false", null);
        return gatt.readCharacteristic(characteristic);
    }

    @SuppressLint("MissingPermission")
    public boolean writeCharacteristics(HashMap<String, Object> payloads){
        // see: BmWriteCharacteristicRequest
        String remoteId =             (String) payloads.get("mac_address");
        String serviceUuid =          (String) payloads.get("service_uuid");
        String secondaryServiceUuid = (String) payloads.get("secondary_service_uuid");
        String characteristicUuid =   (String) payloads.get("characteristic_uuid");
        String value =                (String) payloads.get("value");
        int writeTypeInt =               (int) payloads.get("write_type");
        boolean allowLongWrite =        ((int) payloads.get("allow_long_write")) != 0;

        int writeType = writeTypeInt == 0 ?
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT :
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;

        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(remoteId);
        if(gatt == null) {
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "WriteCharacteristics - device already disconnected");
            emitter.success(emitObject);
            //result.error("writeCharacteristic", "device is disconnected", null);
            return false;
        }

        // find characteristic
        ChrFound found = locateCharacteristic(gatt, serviceUuid, secondaryServiceUuid, characteristicUuid);
        if (found.error != null) {
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "WriteCharacteristics - "+found.error);
            emitter.success(emitObject);
            //result.error("writeCharacteristic", found.error, null);
            return false;
        }

        BluetoothGattCharacteristic characteristic = found.characteristic;

        // check writeable
        if(writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "WriteCharacteristics - device already disconnected");
                emitter.success(emitObject);
                //result.error("writeCharacteristic", "The WRITE_NO_RESPONSE property is not supported by this BLE characteristic", null);
                return false;
            }
        } else {
            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) {
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "WriteCharacteristics - device already disconnected");
                emitter.success(emitObject);
                //result.error("writeCharacteristic", "The WRITE property is not supported by this BLE characteristic", null);
                return false;
            }
        }

        // check maximum payload
        int maxLen = getMaxPayload(remoteId, writeType, allowLongWrite);
        int dataLen = hexToBytes(value).length;
        if (dataLen > maxLen) {
            String t = writeTypeInt == 0 ? "withResponse" : "withoutResponse";
            String a = allowLongWrite ? ", allowLongWrite" : ", noLongWrite";
            String b = writeTypeInt == 0 ? a : "";
            String s = "data longer than allowed. dataLen: " + dataLen + " > max: " + maxLen + " (" + t + b +")";
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "WriteCharacteristics - "+s);
            emitter.success(emitObject);
            //result.error("writeCharacteristic", s, null);
            return false;
        }

        // write characteristic
        if (Build.VERSION.SDK_INT >= 33) { // Android 13 (August 2022)

            int rv = gatt.writeCharacteristic(characteristic, hexToBytes(value), writeType);

            if (rv != BluetoothStatusCodes.SUCCESS) {
                String s = "gatt.writeCharacteristic() returned " + rv + " : " + bluetoothStatusString(rv);
                //result.error("writeCharacteristic", s, null);
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "WriteCharacteristics - "+s);
                emitter.success(emitObject);
                return false;
            }

        } else {
            // set value
            if(!characteristic.setValue(hexToBytes(value))) {
                //result.error("writeCharacteristic", "characteristic.setValue() returned false", null);
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "WriteCharacteristics - characteristic set value returned false");
                emitter.success(emitObject);
                return false;
            }

            // Write type
            characteristic.setWriteType(writeType);

            // Write Char
            //result.error("writeCharacteristic", "gatt.writeCharacteristic() returned false", null);
            return gatt.writeCharacteristic(characteristic);
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    public Map<String, Object> getPairedDevices(){
        final Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();

        List<HashMap<String,Object>> devList = new ArrayList<HashMap<String,Object>>();
        for (BluetoothDevice d : bondedDevices) {
            devList.add(bmBluetoothDevice(d));
        }

        HashMap<String, Object> response = new HashMap<String, Object>();
        response.put("devices", devList);

        Map<String, Object> emitObject = new HashMap<>();
        emitObject.put("type", "adapterPairedDevices");
        emitObject.put("status", true);
        emitObject.put("data", devList);
        emitObject.put("error", "");
        return emitObject;
    }

    @SuppressLint("MissingPermission")
    public Map<String, Object> getDevicePairedState(String macAddress){
        // get bond state
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(macAddress);

        // see: BmBondStateResponse
        HashMap<String, Object> response = new HashMap<>();
        response.put("uuid", macAddress);
        response.put("bond_state", bmBondStateEnum(device.getBondState()));
        response.put("bond_failed", false);
        response.put("bond_lost", false);

        Map<String, Object> emitObject = new HashMap<>();
        emitObject.put("type", "adapterDevicePairedState");
        emitObject.put("data", response);
        emitObject.put("error", "");

        return emitObject;
    }

    @SuppressLint("MissingPermission")
    public boolean pairDevice(String macAddress){

        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(macAddress);
        if(gatt == null) {
            //result.error("createBond", "device is disconnected", null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "pairDevice - device already disconnected");
            emitter.success(emitObject);
            return false;
        }

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(macAddress);

        // already bonded?
        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {

            // see: BmBondStateResponse
            HashMap<String, Object> response = new HashMap<>();
            response.put("mac_Address", macAddress);
            response.put("bond_state", bmBondStateEnum(BluetoothDevice.BOND_BONDED));
            response.put("bond_failed", false);
            response.put("bond_lost", false);

            // the dart code always waits on this
            //invokeMethodUIThread("OnBondStateChanged", response);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "onBondStateChanged");
            emitObject.put("data", response);
            emitter.success(emitObject);

            return true;
        }

        // bond
        //result.error("createBond", "device.createBond() returned false", null);
        return device.createBond();
    }

    @SuppressLint("MissingPermission")
    public boolean unPairDevice(String macAddress) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(macAddress);

        // already removed?
        if (device.getBondState() == BluetoothDevice.BOND_NONE) {

            // see: BmBondStateResponse
            HashMap<String, Object> response = new HashMap<>();
            response.put("mac_address", macAddress);
            response.put("bond_state", bmBondStateEnum(BluetoothDevice.BOND_NONE));
            response.put("bond_failed", false);
            response.put("bond_lost", false);

            // the dart code always waits on this
            //invokeMethodUIThread("OnBondStateChanged", response);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "onBondStateChanged");
            emitObject.put("data", response);
            emitter.success(emitObject);

            return true;
        }

        Method removeBondMethod = device.getClass().getMethod("removeBond");
        //result.error("removeBond", "device.removeBond() returned false", null);
        return (boolean) removeBondMethod.invoke(device);
    }

    @SuppressLint("MissingPermission")
    public boolean readRSSI(String macAddress){
        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(macAddress);
        if(gatt == null) {
            //result.error("readRssi", "device is disconnected", null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "ReaRSSI - device already disconnected");
            emitter.success(emitObject);
            return false;
        }

        // read rssi
        //result.error("readRssi", "gatt.readRemoteRssi() returned false", null);
        return gatt.readRemoteRssi();
    }

    @SuppressLint("MissingPermission")
    public boolean readDescriptor(HashMap<String, Object> payloads){
        // see: BmReadDescriptorRequest
        String remoteId =             (String) payloads.get("mac_address");
        String serviceUuid =          (String) payloads.get("service_uuid");
        String secondaryServiceUuid = (String) payloads.get("secondary_service_uuid");
        String characteristicUuid =   (String) payloads.get("characteristic_uuid");
        String descriptorUuid =       (String) payloads.get("descriptor_uuid");

        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(remoteId);
        if(gatt == null) {
            //result.error("readDescriptor", "device is disconnected", null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "ReadDescriptor - device already disconnected");
            emitter.success(emitObject);
            return false;
        }

        // find characteristic
        ChrFound found = locateCharacteristic(gatt, serviceUuid, secondaryServiceUuid, characteristicUuid);
        if (found.error != null) {
            //result.error("readDescriptor", found.error, null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "ReadDescriptor - "+found.error);
            emitter.success(emitObject);
            return false;
        }

        BluetoothGattCharacteristic characteristic = found.characteristic;

        // find descriptor
        BluetoothGattDescriptor descriptor = getDescriptorFromArray(descriptorUuid, characteristic.getDescriptors());
        if(descriptor == null) {
            String s = "descriptor not found on characteristic. (desc: " + descriptorUuid + " chr: " + characteristicUuid + ")";
            //result.error("writeDescriptor", s, null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "ReadDescriptor - "+s);
            emitter.success(emitObject);
            return false;
        }

        // read descriptor
        //result.error("readDescriptor", "gatt.readDescriptor() returned false", null);
        return gatt.readDescriptor(descriptor);
    }

    @SuppressLint("MissingPermission")
    public boolean writeDescriptor(HashMap<String, Object> payloads){
        // see: BmWriteDescriptorRequest
        String remoteId =             (String) payloads.get("remote_id");
        String serviceUuid =          (String) payloads.get("service_uuid");
        String secondaryServiceUuid = (String) payloads.get("secondary_service_uuid");
        String characteristicUuid =   (String) payloads.get("characteristic_uuid");
        String descriptorUuid =       (String) payloads.get("descriptor_uuid");
        String value =                (String) payloads.get("value");

        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(remoteId);
        if(gatt == null) {
            //result.error("writeDescriptor", "device is disconnected", null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "WriteDescriptor - device already disconnected");
            emitter.success(emitObject);
            return false;
        }

        // find characteristic
        ChrFound found = locateCharacteristic(gatt, serviceUuid, secondaryServiceUuid, characteristicUuid);
        if (found.error != null) {
            //result.error("writeDescriptor", found.error, null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "WriteDescriptor - "+found.error);
            emitter.success(emitObject);
            return false;
        }

        BluetoothGattCharacteristic characteristic = found.characteristic;

        // find descriptor
        BluetoothGattDescriptor descriptor = getDescriptorFromArray(descriptorUuid, characteristic.getDescriptors());
        if(descriptor == null) {
            String s = "descriptor not found on characteristic. (desc: " + descriptorUuid + " chr: " + characteristicUuid + ")";
            //result.error("writeDescriptor", s, null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "WriteDescriptor - "+s);
            emitter.success(emitObject);
            return false;
        }

        // check mtu
        int mtu = mMtu.get(remoteId);
        if ((mtu-3) < hexToBytes(value).length) {
            String s = "data longer than mtu allows. dataLength: " +
                    hexToBytes(value).length + "> max: " + (mtu-3);
            //result.error("writeDescriptor", s, null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "WriteDescriptor - "+s);
            emitter.success(emitObject);
            return false;
        }

        // write descriptor
        if (Build.VERSION.SDK_INT >= 33) { // Android 13 (August 2022)

            int rv = gatt.writeDescriptor(descriptor, hexToBytes(value));
            if (rv != BluetoothStatusCodes.SUCCESS) {
                String s = "gatt.writeDescriptor() returned " + rv + " : " + bluetoothStatusString(rv);
                //result.error("writeDescriptor", s, null);
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "WriteDescriptor - "+s);
                emitter.success(emitObject);
                return false;
            }

        } else {

            // Set descriptor
            if(!descriptor.setValue(hexToBytes(value))){
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "WriteDescriptor - descriptor set value returned false");
                emitter.success(emitObject);
                //result.error("writeDescriptor", "descriptor.setValue() returned false", null);
                return false;
            }

            // Write descriptor
            //result.error("writeDescriptor", "gatt.writeDescriptor() returned false", null);
            return gatt.writeDescriptor(descriptor);
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    public void setNotification(HashMap<String, Object> payloads){
        // see: BmSetNotificationRequest
        String remoteId =             (String) payloads.get("remote_id");
        String serviceUuid =          (String) payloads.get("service_uuid");
        String secondaryServiceUuid = (String) payloads.get("secondary_service_uuid");
        String characteristicUuid =   (String) payloads.get("characteristic_uuid");
        boolean enable =             (boolean) payloads.get("enable");

        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(remoteId);
        if(gatt == null) {
            //result.error("setNotification", "device is disconnected", null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "Notification - device already disconnected");
            emitter.success(emitObject);
            return;
        }

        // find characteristic
        ChrFound found = locateCharacteristic(gatt, serviceUuid, secondaryServiceUuid, characteristicUuid);
        if (found.error != null) {
            //result.error("setNotification", found.error, null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "Notification - "+found.error);
            emitter.success(emitObject);
            return;
        }

        BluetoothGattCharacteristic characteristic = found.characteristic;

        // configure local Android device to listen for characteristic changes
        if(!gatt.setCharacteristicNotification(characteristic, enable)){
            //result.error("setNotification", "gatt.setCharacteristicNotification(" + enable + ") returned false", null);
            Map<String, Object> emitObject = new HashMap<>();
            emitObject.put("type", "debug_message");
            emitObject.put("data", "Notification - gatt set characteristics notification enabled: "+enable);
            emitter.success(emitObject);
            return;
        }

        // find cccd descriptor
        BluetoothGattDescriptor cccd = getDescriptorFromArray(CCCD, characteristic.getDescriptors());
        if(cccd == null) {
            // Some ble devices do not actually need their CCCD updated.
            // thus setCharacteristicNotification() is all that is required to enable notifications.
            // The arduino "bluno" devices are an example.
            String uuid = uuid128(characteristic.getUuid());
            log(LogLevel.WARNING, "MapXHardWareConnector -  CCCD descriptor for characteristic not found: " + uuid);
            //result.success(null);
            return;
        }

        byte[] descriptorValue = null;

        // determine value to write
        if(enable) {

            boolean canNotify = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
            boolean canIndicate = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0;

            if(!canIndicate && !canNotify) {
                //result.error("setNotification", "neither NOTIFY nor INDICATE properties are supported by this BLE characteristic", null);
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "Notification - device already disconnected");
                emitter.success(emitObject);
                return;
            }

            // If a characteristic supports both notifications and indications,
            // we'll use notifications. This matches how CoreBluetooth works on iOS.
            if(canIndicate) {descriptorValue = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;}
            if(canNotify)   {descriptorValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;}

        } else {
            descriptorValue  = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
        }

        // write descriptor
        if (Build.VERSION.SDK_INT >= 33) { // Android 13 (August 2022)

            int rv = gatt.writeDescriptor(cccd, descriptorValue);
            if (rv != BluetoothStatusCodes.SUCCESS) {
                String s = "gatt.writeDescriptor() returned " + rv + " : " + bluetoothStatusString(rv);
                //result.error("setNotification", s, null);
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "Notification - device already disconnected");
                emitter.success(emitObject);
                return;
            }

        } else {

            // set new value
            if (!cccd.setValue(descriptorValue)) {
                //result.error("setNotification", "cccd.setValue() returned false", null);
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "Notification - device already disconnected");
                emitter.success(emitObject);
                return;
            }

            // update notifications on remote BLE device
            if (!gatt.writeDescriptor(cccd)) {
                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "debug_message");
                emitObject.put("data", "Notification - device already disconnected");
                emitter.success(emitObject);
                //result.error("setNotification", "gatt.writeDescriptor() returned false", null);
                return;
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void requestMtu(HashMap<String, Object> payloads){
        // see: BmMtuChangeRequest
        String remoteId = (String) payloads.get("mac_address");
        int mtu =            (int) payloads.get("mtu");

        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(remoteId);
        if(gatt == null) {
            //result.error("requestMtu", "device is disconnected", null);
            return;
        }

        // request mtu
        if(!gatt.requestMtu(mtu)) {
            //result.error("requestMtu", "gatt.requestMtu() returned false", null);
            return;
        }
    }

    @SuppressLint("MissingPermission")
    public void requestConnectionPriority(HashMap<String, Object> payloads){
        // see: BmConnectionPriorityRequest
        String remoteId =     (String) payloads.get("mac_address");
        int connectionPriority = (int) payloads.get("connection_priority");

        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(remoteId);
        if(gatt == null) {
            //result.error("requestConnectionPriority", "device is disconnected", null);
            return;
        }

        int cpInteger = bmConnectionPriorityParse(connectionPriority);

        // request priority
        if(!gatt.requestConnectionPriority(cpInteger)) {
            //result.error("requestConnectionPriority", "gatt.requestConnectionPriority() returned false", null);
            return;
        }
    }

    public void clearGattCache(String macAddress) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(macAddress);
        if(gatt == null) {
            //result.error("clearGattCache", "device is disconnected", null);
            return;
        }

        final Method refreshMethod = gatt.getClass().getMethod("refresh");
        refreshMethod.invoke(gatt);
    }

    @SuppressLint("MissingPermission")
    public void setPreferredPhy(HashMap<String, Object> payloads){
        if(Build.VERSION.SDK_INT < 26) { // Android 8.0 (August 2017)
            //result.error("setPreferredPhy", "Only supported on devices >= API 26. This device == " + Build.VERSION.SDK_INT, null);
            return;
        }

        // see: BmPreferredPhy
        String remoteId = (String) payloads.get("remote_id");
        int txPhy =          (int) payloads.get("tx_phy");
        int rxPhy =          (int) payloads.get("rx_phy");
        int phyOptions =     (int) payloads.get("phy_options");

        // check connection
        BluetoothGatt gatt = mConnectedDevices.get(remoteId);
        if(gatt == null) {
            //result.error("setPreferredPhy", "device is disconnected", null);
            return;
        }

        // set preferred phy
        gatt.setPreferredPhy(txPhy, rxPhy, phyOptions);
    }


    //////////////////////////////////////////////////////////////////////////////////////
    // ██████   ███████  ██████   ███    ███  ██  ███████  ███████  ██   ██████   ███    ██
    // ██   ██  ██       ██   ██  ████  ████  ██  ██       ██       ██  ██    ██  ████   ██
    // ██████   █████    ██████   ██ ████ ██  ██  ███████  ███████  ██  ██    ██  ██ ██  ██
    // ██       ██       ██   ██  ██  ██  ██  ██       ██       ██  ██  ██    ██  ██  ██ ██
    // ██       ███████  ██   ██  ██      ██  ██  ███████  ███████  ██   ██████   ██   ████

    boolean onRequestPermissionsResultValue = false;
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                         String[] permissions,
                                            int[] grantResults)
    {
        OperationOnPermission operation = operationsOnPermission.get(requestCode);

        if (operation != null && grantResults.length > 0) {
            operation.op(grantResults[0] == PackageManager.PERMISSION_GRANTED, permissions[0]);
            onRequestPermissionsResultValue = true;
            //return true;
        } else {
            onRequestPermissionsResultValue = false;
            //return false;
        }
    }

    private void ensurePermissions(List<String> permissions, OperationOnPermission operation)
    {
        // only request permission we don't already have
        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (permission != null && ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        // no work to do?
        if (permissionsNeeded.isEmpty()) {
            operation.op(true, null);
            return;
        }

        askPermission(permissionsNeeded, operation);
    }

    private void askPermission(List<String> permissionsNeeded, OperationOnPermission operation)
    {
        // finished asking for permission? call callback
        if (permissionsNeeded.isEmpty()) {
            operation.op(true, null);
            return;
        }

        String nextPermission = permissionsNeeded.remove(0);

        operationsOnPermission.put(lastEventId, (granted, perm) -> {
            operationsOnPermission.remove(lastEventId);
            if (!granted) {
                operation.op(false, perm);
                return;
            }
            // recursively ask for next permission
            askPermission(permissionsNeeded, operation);
        });

        ActivityCompat.requestPermissions(
                activity,
                new String[]{nextPermission},
                lastEventId);

        lastEventId++;
    }

    //////////////////////////////////////////////
    // ██████   ██       ███████
    // ██   ██  ██       ██
    // ██████   ██       █████
    // ██   ██  ██       ██
    // ██████   ███████  ███████
    //
    // ██    ██  ████████  ██  ██       ███████
    // ██    ██     ██     ██  ██       ██
    // ██    ██     ██     ██  ██       ███████
    // ██    ██     ██     ██  ██            ██
    //  ██████      ██     ██  ███████  ███████

    class ChrFound {
        public BluetoothGattCharacteristic characteristic;
        public String error;

        public ChrFound(BluetoothGattCharacteristic characteristic, String error) {
            this.characteristic = characteristic;
            this.error = error;
        }
    }

    private ChrFound locateCharacteristic(BluetoothGatt gatt,
                                                 String serviceId,
                                                 String secondaryServiceId,
                                                 String characteristicId)
    {
        // primary
        BluetoothGattService primaryService = getServiceFromArray(serviceId, gatt.getServices());
        if(primaryService == null) {
            return new ChrFound(null, "service not found '" + serviceId + "'");
        }

        // secondary
        BluetoothGattService secondaryService = null;
        if(secondaryServiceId != null && secondaryServiceId.length() > 0) {
            secondaryService = getServiceFromArray(serviceId, primaryService.getIncludedServices());
            if(secondaryService == null) {
                return new ChrFound(null, "secondaryService not found '" + secondaryServiceId + "'");
            }
        }

        // which service?
        BluetoothGattService service = (secondaryService != null) ? secondaryService : primaryService;

        // characteristic
        BluetoothGattCharacteristic characteristic = getCharacteristicFromArray(characteristicId, service.getCharacteristics());
        if(characteristic == null) {
            return new ChrFound(null, "characteristic not found in service " + 
                "(chr: '" + characteristicId + "' svc: '" + serviceId + "')");
        }

        return new ChrFound(characteristic, null);
    }

    private BluetoothGattService getServiceFromArray(String uuid, List<BluetoothGattService> array)
    {
        for (BluetoothGattService s : array) {
            if (uuid128(s.getUuid()).equals(uuid)) {
                return s;
            }
        }
        return null;
    }

    private BluetoothGattCharacteristic getCharacteristicFromArray(String uuid, List<BluetoothGattCharacteristic> array)
    {
        for (BluetoothGattCharacteristic c : array) {
            if (uuid128(c.getUuid()).equals(uuid)) {
                return c;
            }
        }
        return null;
    }

    private BluetoothGattDescriptor getDescriptorFromArray(String uuid, List<BluetoothGattDescriptor> array)
    {
        for (BluetoothGattDescriptor d : array) {
            if (uuid128(d.getUuid()).equals(uuid)) {
                return d;
            }
        }
        return null;
    }

    private int getMaxPayload(String remoteId, int writeType, boolean allowLongWrite)
    {
        // 512 this comes from the BLE spec. Characteritics should not 
        // be longer than 512. Android also enforces this as the maximum in internal code.
        int maxAttrLen = 512; 

        // if no response, we can only write up to MTU-3. 
        // This is the same limitation as iOS, and ensures transfer reliability.
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE || allowLongWrite == false) {

            // get mtu
            Integer mtu = mMtu.get(remoteId);
            if (mtu == null) {
                mtu = 23; // 23 is the minumum MTU, as per the BLE spec
            }

            return Math.min(mtu - 3, maxAttrLen);

        } else {
            // if using withResponse, android will auto split up to the maxAttrLen.
            return maxAttrLen;
        }
    }

    @SuppressLint("MissingPermission")
    private void disconnectAllDevices(boolean alsoClose)
    {
        Log.d(TAG, "MapXHardWareConnector -  disconnectAllDevices");

        // request disconnections
        for (BluetoothGatt gatt : mConnectedDevices.values()) {
            if(gatt != null) {
                String remoteId = gatt.getDevice().getAddress();
                Log.d(TAG, "MapXHardWareConnector -  calling disconnect: " + remoteId);
                gatt.disconnect();
            }
        }

        // close all devices?
        if (alsoClose) {
            Log.d(TAG, "MapXHardWareConnector -  closeAllDevices");
            for (BluetoothGatt gatt : mConnectedDevices.values()) {
                if(gatt != null) {
                    String remoteId = gatt.getDevice().getAddress();
                    Log.d(TAG, "MapXHardWareConnector -  calling close: " + remoteId);
                    gatt.close();
                }
            }
        }


        mConnectedDevices.clear();
        mMtu.clear();
    }

    /////////////////////////////////////////////////////////////////////////////////////
    //  █████   ██████    █████   ██████   ████████  ███████  ██████
    // ██   ██  ██   ██  ██   ██  ██   ██     ██     ██       ██   ██
    // ███████  ██   ██  ███████  ██████      ██     █████    ██████
    // ██   ██  ██   ██  ██   ██  ██          ██     ██       ██   ██
    // ██   ██  ██████   ██   ██  ██          ██     ███████  ██   ██
    //
    // ██████   ███████   ██████  ███████  ██  ██    ██  ███████  ██████
    // ██   ██  ██       ██       ██       ██  ██    ██  ██       ██   ██
    // ██████   █████    ██       █████    ██  ██    ██  █████    ██████
    // ██   ██  ██       ██       ██       ██   ██  ██   ██       ██   ██
    // ██   ██  ███████   ██████  ███████  ██    ████    ███████  ██   ██

    private final BroadcastReceiver mBluetoothAdapterStateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            // no change?
            if (action == null || !BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                return;
            }

            final int adapterState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

            log(LogLevel.DEBUG, "MapXHardWareConnector -  OnAdapterStateChanged: " + adapterStateString(adapterState));

            // disconnect all devices
            if (adapterState == BluetoothAdapter.STATE_TURNING_OFF || 
                adapterState == BluetoothAdapter.STATE_OFF) {
                disconnectAllDevices(false  /* alsoClose? */);
            }
            
            // see: BmBluetoothAdapterState
            HashMap<String, Object> map = new HashMap<>();
            map.put("adapter_state", bmAdapterStateEnum(adapterState));

            if(adapterStateString(adapterState).equals("on") || adapterStateString(adapterState).equals("off")) {
                log(LogLevel.DEBUG, "MapXHardWareConnector log -  OnAdapterStateChanged: " + adapterStateString(adapterState));

                Map<String, Object> emitObject = new HashMap<>();
                emitObject.put("type", "adapterState");
                boolean stateOn = adapterStateString(adapterState).equals("on");
                emitObject.put("data", stateOn);
                emitter.success(emitObject);
            }
            //invokeMethodUIThread("OnAdapterStateChanged", map);
        }
    };

    /////////////////////////////////////////////////////////////////////////////////////
    // ██████    ██████   ███    ██  ██████
    // ██   ██  ██    ██  ████   ██  ██   ██
    // ██████   ██    ██  ██ ██  ██  ██   ██
    // ██   ██  ██    ██  ██  ██ ██  ██   ██
    // ██████    ██████   ██   ████  ██████
    //
    // ██████   ███████   ██████  ███████  ██  ██    ██  ███████  ██████
    // ██   ██  ██       ██       ██       ██  ██    ██  ██       ██   ██
    // ██████   █████    ██       █████    ██  ██    ██  █████    ██████
    // ██   ██  ██       ██       ██       ██   ██  ██   ██       ██   ██
    // ██   ██  ███████   ██████  ███████  ██    ████    ███████  ██   ██


    private final BroadcastReceiver mBluetoothBondStateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            // no change?
            if (action == null || action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED) == false) {
                return;
            }

            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            final int cur = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            final int prev = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

            log(LogLevel.DEBUG, "MapXHardWareConnector -  OnBondStateChanged: " + bondStateString(cur) + " prev: " + bondStateString(prev));

            String remoteId = device.getAddress();

            boolean lost = cur == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDED;
            boolean fail = cur == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDING;

            // see: BmBondStateResponse
            HashMap<String, Object> map = new HashMap<>();
            map.put("remote_id", remoteId);
            map.put("bond_state", bmBondStateEnum(cur));
            map.put("bond_failed", cur == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDING);
            map.put("bond_lost", cur == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDED);

            invokeMethodUIThread("OnBondStateChanged", map);
        }
    };

    /////////////////////////////////////////////////////////////////////////////
    // ███████   ██████   █████   ███    ██
    // ██       ██       ██   ██  ████   ██
    // ███████  ██       ███████  ██ ██  ██
    //      ██  ██       ██   ██  ██  ██ ██
    // ███████   ██████  ██   ██  ██   ████
    //
    //  ██████   █████   ██       ██       ██████    █████    ██████  ██   ██
    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
    // ██       ███████  ██       ██       ██████   ███████  ██       █████
    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
    //  ██████  ██   ██  ███████  ███████  ██████   ██   ██   ██████  ██   ██

    private ScanCallback scanCallback;

    private ScanCallback getScanCallback()
    {
        if(scanCallback == null) {

            scanCallback = new ScanCallback()
            {
                @Override
                public void onScanResult(int callbackType, ScanResult result)
                {
                    log(LogLevel.VERBOSE, "MapXHardWareConnector -  onScanResult");

                    super.onScanResult(callbackType, result);

                    BluetoothDevice device = result.getDevice();

                    // see BmScanResult
                    HashMap<String, Object> rr = deviceScanResult(device, result);

                    // see BmScanResponse
                    //HashMap<String, Object> response = new HashMap<>();
                    //response.put("result", rr);

                    Map<String, Object> emitObject = new HashMap<>();
                    emitObject.put("type", "adapterScanning");
                    emitObject.put("status", true);
                    emitObject.put("data", rr);
                    emitter.success(emitObject);

                    //invokeMethodUIThread("OnScanResponse", response);
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results)
                {
                    super.onBatchScanResults(results);
                }

                @Override
                public void onScanFailed(int errorCode)
                {
                    log(LogLevel.ERROR, "MapXHardWareConnector -  onScanFailed: " + scanFailedString(errorCode));

                    super.onScanFailed(errorCode);

                    // see: BmScanFailed
                    HashMap<String, Object> failed = new HashMap<>();
                    failed.put("success", 0);
                    failed.put("error_code", errorCode);
                    failed.put("error_string", scanFailedString(errorCode));

                    // see BmScanResponse
                    HashMap<String, Object> response = new HashMap<>();
                    response.put("failed", failed);

                    Map<String, Object> emitObject = new HashMap<>();
                    emitObject.put("type", "adapterScanning");
                    emitObject.put("status", false);
                    emitObject.put("error", scanFailedString(errorCode));
                    emitter.success(emitObject);
                    //invokeMethodUIThread("OnScanResponse", response);
                }
            };
        }
        return scanCallback;
    }

    /////////////////////////////////////////////////////////////////////////////
    //  ██████    █████   ████████  ████████
    // ██        ██   ██     ██        ██
    // ██   ███  ███████     ██        ██
    // ██    ██  ██   ██     ██        ██
    //  ██████   ██   ██     ██        ██
    //
    //  ██████   █████   ██       ██       ██████    █████    ██████  ██   ██
    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
    // ██       ███████  ██       ██       ██████   ███████  ██       █████
    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
    //  ██████  ██   ██  ███████  ███████  ██████   ██   ██   ██████  ██   ██
    @SuppressLint("MissingPermission")
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onConnectionStateChange: status: " + status +
                " (" + hciStatusString(status) + ")" +
                " newState: " + connectionStateString(newState));

            // android never uses this callback with enums values of CONNECTING or DISCONNECTING,
            // (theyre only used for gatt.getConnectionState()), but just to be
            // future proof, explicitly ignore anything else. CoreBluetooth is the same way.
            if(newState != BluetoothProfile.STATE_CONNECTED &&
               newState != BluetoothProfile.STATE_DISCONNECTED) {
                return;
            }

            String remoteId = gatt.getDevice().getAddress();

            // connected?
            if(newState == BluetoothProfile.STATE_CONNECTED) {
                // add to connected devices
                mConnectedDevices.put(remoteId, gatt);

                // default minimum mtu
                mMtu.put(remoteId, 23); 
            }

            // disconnected?
            if(newState == BluetoothProfile.STATE_DISCONNECTED) {

                // remove from connected devices
                mConnectedDevices.remove(remoteId);

                // we cannot call 'close' for autoconnect
                // because it prevents autoconnect from working
                if (mAutoConnect.get(remoteId) == null || mAutoConnect.get(remoteId) == false) {
                    // it is important to close after disconnection, otherwise we will 
                    // quickly run out of bluetooth resources, preventing new connections
                    gatt.close();
                } else {
                    log(LogLevel.DEBUG, "MapXHardWareConnector -  autoconnect is true. skipping gatt.close()");
                }
            }

            // see: BmConnectionStateResponse
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", remoteId);
            response.put("connection_state", bmConnectionStateEnum(newState));
            response.put("disconnect_reason_code", status);
            response.put("disconnect_reason_string", hciStatusString(status));

            invokeMethodUIThread("OnConnectionStateChanged", response);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onServicesDiscovered: count: " + gatt.getServices().size() + " status: " + status);

            List<Object> services = new ArrayList<Object>();
            for(BluetoothGattService s : gatt.getServices()) {
                services.add(bmBluetoothService(gatt.getDevice(), s, gatt));
            }

            // see: BmDiscoverServicesResult
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", gatt.getDevice().getAddress());
            response.put("services", services);
            response.put("success", status == BluetoothGatt.GATT_SUCCESS ? 1 : 0);
            response.put("error_code", status);
            response.put("error_string", gattErrorString(status));

            invokeMethodUIThread("OnDiscoverServicesResult", response);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            // this callback is only for notifications & indications
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onCharacteristicChanged: uuid: " + uuid128(characteristic.getUuid()));

            ServicePair pair = getServicePair(gatt, characteristic);

            // see: BmOnCharacteristicReceived
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", gatt.getDevice().getAddress());
            response.put("service_uuid", uuid128(pair.primary));
            response.put("secondary_service_uuid", pair.secondary != null ? uuid128(pair.secondary) : null);
            response.put("characteristic_uuid", uuid128(characteristic.getUuid()));
            response.put("value", bytesToHex(characteristic.getValue()));
            response.put("success", 1);
            response.put("error_code", 0);
            response.put("error_string", gattErrorString(0));

            invokeMethodUIThread("OnCharacteristicReceived", response);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            // this callback is only for explicit characteristic reads
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onCharacteristicRead: uuid: " + uuid128(characteristic.getUuid()) + " status: " + status);

            ServicePair pair = getServicePair(gatt, characteristic);

            // see: BmOnCharacteristicReceived
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", gatt.getDevice().getAddress());
            response.put("service_uuid", uuid128(pair.primary));
            response.put("secondary_service_uuid", pair.secondary != null ? uuid128(pair.secondary) : null);
            response.put("characteristic_uuid", uuid128(characteristic.getUuid()));
            response.put("value", bytesToHex(characteristic.getValue()));
            response.put("success", status == BluetoothGatt.GATT_SUCCESS ? 1 : 0);
            response.put("error_code", status);
            response.put("error_string", gattErrorString(status));

            invokeMethodUIThread("OnCharacteristicReceived", response);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onCharacteristicWrite: uuid: " + uuid128(characteristic.getUuid()) + " status: " + status);

            // For "writeWithResponse", onCharacteristicWrite is called after the remote sends back a write response. 
            // For "writeWithoutResponse", onCharacteristicWrite is called as long as there is still space left 
            // in android's internal buffer. When the buffer is full, it delays calling onCharacteristicWrite 
            // until there is at least ~50% free space again. 

            ServicePair pair = getServicePair(gatt, characteristic);

            // see: BmOnCharacteristicWritten
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", gatt.getDevice().getAddress());
            response.put("service_uuid", uuid128(pair.primary));
            response.put("secondary_service_uuid", pair.secondary != null ? uuid128(pair.secondary) : null);
            response.put("characteristic_uuid", uuid128(characteristic.getUuid()));
            response.put("success", status == BluetoothGatt.GATT_SUCCESS ? 1 : 0);
            response.put("error_code", status);
            response.put("error_string", gattErrorString(status));

            invokeMethodUIThread("OnCharacteristicWritten", response);
        }

        @Override
        @TargetApi(33)
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status, byte[] value)
        {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onDescriptorRead: uuid: " + uuid128(descriptor.getUuid()) + " status: " + status);

            ServicePair pair = getServicePair(gatt, descriptor.getCharacteristic());

            // see: BmOnDescriptorRead
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", gatt.getDevice().getAddress());
            response.put("service_uuid", uuid128(pair.primary));
            response.put("secondary_service_uuid", pair.secondary != null ? uuid128(pair.secondary) : null);
            response.put("characteristic_uuid", uuid128(descriptor.getCharacteristic().getUuid()));
            response.put("descriptor_uuid", uuid128(descriptor.getUuid()));
            response.put("value", bytesToHex(value));
            response.put("success", status == BluetoothGatt.GATT_SUCCESS ? 1 : 0);
            response.put("error_code", status);
            response.put("error_string", gattErrorString(status));

            invokeMethodUIThread("OnDescriptorRead", response);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
        {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onDescriptorRead: uuid: " + uuid128(descriptor.getUuid()) + " status: " + status);

            ServicePair pair = getServicePair(gatt, descriptor.getCharacteristic());

            // this was deprecated in API level 33 because the api makes it look like
            // you could always call getValue on a descriptor. But in reality, this
            // only works after a *read* has been made, not a *write*.
            byte[] value = descriptor.getValue();

            // see: BmOnDescriptorRead
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", gatt.getDevice().getAddress());
            response.put("service_uuid", uuid128(pair.primary));
            response.put("secondary_service_uuid", pair.secondary != null ? uuid128(pair.secondary) : null);
            response.put("characteristic_uuid", uuid128(descriptor.getCharacteristic().getUuid()));
            response.put("descriptor_uuid", uuid128(descriptor.getUuid()));
            response.put("value", bytesToHex(value));
            response.put("success", status == BluetoothGatt.GATT_SUCCESS ? 1 : 0);
            response.put("error_code", status);
            response.put("error_string", gattErrorString(status));

            invokeMethodUIThread("OnDescriptorRead", response);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
        {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onDescriptorWrite: uuid: " + uuid128(descriptor.getUuid()) + " status: " + status);

            ServicePair pair = getServicePair(gatt, descriptor.getCharacteristic());

            // see: BmOnDescriptorWrite
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", gatt.getDevice().getAddress());
            response.put("service_uuid", uuid128(pair.primary));
            response.put("secondary_service_uuid", pair.secondary != null ? uuid128(pair.secondary) : null);
            response.put("characteristic_uuid", uuid128(descriptor.getCharacteristic().getUuid()));
            response.put("descriptor_uuid", uuid128(descriptor.getUuid()));
            response.put("success", status == BluetoothGatt.GATT_SUCCESS ? 1 : 0);
            response.put("error_code", status);
            response.put("error_string", gattErrorString(status));

            invokeMethodUIThread("OnDescriptorWrite", response);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status)
        {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onReliableWriteCompleted: status: " + status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status)
        {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onReadRemoteRssi: rssi: " + rssi + " status: " + status);

            // see: BmReadRssiResult
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", gatt.getDevice().getAddress());
            response.put("rssi", rssi);
            response.put("success", status == BluetoothGatt.GATT_SUCCESS ? 1 : 0);
            response.put("error_code", status);
            response.put("error_string", gattErrorString(status));

            invokeMethodUIThread("OnReadRssiResult", response);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status)
        {
            log(LogLevel.DEBUG, "MapXHardWareConnector -  onMtuChanged: mtu: " + mtu + " status: " + status);

            String remoteId = gatt.getDevice().getAddress();

            // remember mtu
            mMtu.put(remoteId, mtu);

            // see: BmMtuChangedResponse
            HashMap<String, Object> response = new HashMap<>();
            response.put("remote_id", remoteId);
            response.put("mtu", mtu);
            response.put("success", status == BluetoothGatt.GATT_SUCCESS ? 1 : 0);
            response.put("error_code", status);
            response.put("error_string", gattErrorString(status));

            invokeMethodUIThread("OnMtuChanged", response);
        }
    }; // BluetoothGattCallback

    //////////////////////////////////////////////////////////////////////
    // ███    ███  ███████   ██████      
    // ████  ████  ██       ██           
    // ██ ████ ██  ███████  ██   ███     
    // ██  ██  ██       ██  ██    ██     
    // ██      ██  ███████   ██████ 
    //     
    // ██   ██  ███████  ██       ██████   ███████  ██████   ███████ 
    // ██   ██  ██       ██       ██   ██  ██       ██   ██  ██      
    // ███████  █████    ██       ██████   █████    ██████   ███████ 
    // ██   ██  ██       ██       ██       ██       ██   ██       ██ 
    // ██   ██  ███████  ███████  ██       ███████  ██   ██  ███████ 

    HashMap<String, Object> bmAdvertisementData(ScanResult result) {

        int min = Integer.MIN_VALUE;

        ScanRecord adv = result.getScanRecord();

        String                  localName    = adv != null ?  adv.getDeviceName()                : null;
        boolean                 connectable  = adv != null ? (adv.getAdvertiseFlags() & 0x2) > 0 : false;
        int                     txPower      = adv != null ?  adv.getTxPowerLevel()              : min;
        SparseArray<byte[]>     manufData    = adv != null ?  adv.getManufacturerSpecificData()  : null;
        List<ParcelUuid>        serviceUuids = adv != null ?  adv.getServiceUuids()              : null;
        Map<ParcelUuid, byte[]> serviceData  = adv != null ?  adv.getServiceData()               : null;

        // Manufacturer Specific Data
        HashMap<Integer, String> manufDataB = new HashMap<Integer, String>();
        if(manufData != null) {
            for (int i = 0; i < manufData.size(); i++) {
                int key = manufData.keyAt(i);
                byte[] value = manufData.valueAt(i);
                manufDataB.put(key, bytesToHex(value));
            }
        }

        // Service Data
        HashMap<String, Object> serviceDataB = new HashMap<>();
        if(serviceData != null) {
            for (Map.Entry<ParcelUuid, byte[]> entry : serviceData.entrySet()) {
                ParcelUuid key = entry.getKey();
                byte[] value = entry.getValue();
                serviceDataB.put(uuid128(key.getUuid()), bytesToHex(value));
            }
        }

        // Service UUIDs
        List<String> serviceUuidsB = new ArrayList<String>();
        if(serviceUuids != null) {
            for (ParcelUuid s : serviceUuids) {
                serviceUuidsB.add(uuid128(s.getUuid()));
            }
        }

        HashMap<String, Object> map = new HashMap<>();
        map.put("local_name",        localName);
        map.put("connectable",       connectable);
        map.put("tx_power_level",    txPower      != min  ? txPower       : null);
        map.put("manufacturer_data", manufData    != null ? manufDataB    : null);
        map.put("service_data",      serviceData  != null ? serviceDataB  : null);
        map.put("service_uuids",     serviceUuids != null ? serviceUuidsB : null);
        return map;
    }

    HashMap<String, Object> bmScanResult(BluetoothDevice device, ScanResult result) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("device", bmBluetoothDevice(device));
        map.put("rssi", result.getRssi());
        map.put("advertisement_data", bmAdvertisementData(result));
        return map;
    }

    @SuppressLint("MissingPermission")
    HashMap<String, Object> deviceScanResult(BluetoothDevice device, ScanResult result) {

        HashMap<String, Object> map = new HashMap<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            map.put("isConnectable", result.isConnectable());
        }else{
            map.put("isConnectable", false);
        }
        map.put("rssi", result.getRssi());
        map.put("uuid", device.getAddress());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            map.put("name", device.getAlias());
        }else{
            map.put("name", device.getName());
        }
        map.put("isConnected", device.getBondState() == BluetoothDevice.BOND_BONDED);
        map.put("type", device.getType());
        return map;
    }

    @SuppressLint("MissingPermission")
    HashMap<String, Object> bmBluetoothDevice(BluetoothDevice device) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("uuid", device.getAddress());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            map.put("name", device.getAlias());
        }else{
            map.put("name", device.getName());
        }
        map.put("isConnected", device.getBondState() == BluetoothDevice.BOND_BONDED);
        map.put("isConnectable", true);
        map.put("type", device.getType());
        return map;
    }

    HashMap<String, Object> bmBluetoothService(BluetoothDevice device, BluetoothGattService service, BluetoothGatt gatt) {

        List<Object> characteristics = new ArrayList<Object>();
        for(BluetoothGattCharacteristic c : service.getCharacteristics()) {
            characteristics.add(bmBluetoothCharacteristic(device, c, gatt));
        }

        List<Object> includedServices = new ArrayList<Object>();
        for(BluetoothGattService included : service.getIncludedServices()) {
            // service includes itself?
            if (included.getUuid().equals(service.getUuid())) {
                continue; // skip, infinite recursion
            }
            includedServices.add(bmBluetoothService(device, included, gatt));
        }

        HashMap<String, Object> map = new HashMap<>();
        map.put("mac_address", device.getAddress());
        map.put("service_uuid", uuid128(service.getUuid()));
        map.put("is_primary", service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY ? 1 : 0);
        map.put("characteristics", characteristics);
        map.put("included_services", includedServices);
        return map;
    }

    HashMap<String, Object> bmBluetoothCharacteristic(BluetoothDevice device, BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {

        ServicePair pair = getServicePair(gatt, characteristic);

        List<Object> descriptors = new ArrayList<Object>();
        for(BluetoothGattDescriptor d : characteristic.getDescriptors()) {
            descriptors.add(bmBluetoothDescriptor(device, d));
        }

        HashMap<String, Object> map = new HashMap<>();
        map.put("remote_id", device.getAddress());
        map.put("service_uuid", uuid128(pair.primary));
        map.put("secondary_service_uuid", pair.secondary != null ? uuid128(pair.secondary) : null);
        map.put("characteristic_uuid", uuid128(characteristic.getUuid()));
        map.put("descriptors", descriptors);
        map.put("properties", bmCharacteristicProperties(characteristic.getProperties()));
        return map;
    }

    HashMap<String, Object> bmBluetoothDescriptor(BluetoothDevice device, BluetoothGattDescriptor descriptor) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("remote_id", device.getAddress());
        map.put("descriptor_uuid", uuid128(descriptor.getUuid()));
        map.put("characteristic_uuid", uuid128(descriptor.getCharacteristic().getUuid()));
        map.put("service_uuid", uuid128(descriptor.getCharacteristic().getService().getUuid()));
        return map;
    }

    HashMap<String, Object> bmCharacteristicProperties(int properties) {
        HashMap<String, Object> props = new HashMap<>();
        props.put("broadcast",                      (properties & 1)   != 0 ? 1 : 0);
        props.put("read",                           (properties & 2)   != 0 ? 1 : 0);
        props.put("write_without_response",         (properties & 4)   != 0 ? 1 : 0);
        props.put("write",                          (properties & 8)   != 0 ? 1 : 0);
        props.put("notify",                         (properties & 16)  != 0 ? 1 : 0);
        props.put("indicate",                       (properties & 32)  != 0 ? 1 : 0);
        props.put("authenticated_signed_writes",    (properties & 64)  != 0 ? 1 : 0);
        props.put("extended_properties",            (properties & 128) != 0 ? 1 : 0);
        props.put("notify_encryption_required",     (properties & 256) != 0 ? 1 : 0);
        props.put("indicate_encryption_required",   (properties & 512) != 0 ? 1 : 0);
        return props;
    }

    static int bmConnectionStateEnum(int cs) {
        switch (cs) {
            case BluetoothProfile.STATE_DISCONNECTED:  return 0;
            case BluetoothProfile.STATE_CONNECTED:     return 1;
            default:                                   return 0;
        }
    }

    static int bmAdapterStateEnum(int as) {
        switch (as) {
            case BluetoothAdapter.STATE_OFF:          return 6;
            case BluetoothAdapter.STATE_ON:           return 4;
            case BluetoothAdapter.STATE_TURNING_OFF:  return 5;
            case BluetoothAdapter.STATE_TURNING_ON:   return 3;
            default:                                  return 0; 
        }
    }

    static int bmBondStateEnum(int bs) {
        switch (bs) {
            case BluetoothDevice.BOND_NONE:    return 0;
            case BluetoothDevice.BOND_BONDING: return 1;
            case BluetoothDevice.BOND_BONDED:  return 2;
            default:                           return 0; 
        }
    }

    static int bmConnectionPriorityParse(int value) {
        switch(value) {
            case 0: return BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
            case 1: return BluetoothGatt.CONNECTION_PRIORITY_HIGH;
            case 2: return BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
            default: return BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
        }
    }

    public static class ServicePair {
        public UUID primary;
        public UUID secondary;
    }

    static ServicePair getServicePair(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

        ServicePair result = new ServicePair();

        BluetoothGattService service = characteristic.getService();

        // is this a primary service?
        if(service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY) {
            result.primary = service.getUuid();
            return result;
        } 

        // Otherwise, iterate all services until we find the primary service
        for(BluetoothGattService primary : gatt.getServices()) {
            for(BluetoothGattService secondary : primary.getIncludedServices()) {
                if(secondary.getUuid().equals(service.getUuid())) {
                    result.primary = primary.getUuid();
                    result.secondary = secondary.getUuid();
                    return result;
                }
            }
        }

        return result;
    }

    //////////////////////////////////////////
    // ██    ██ ████████  ██  ██       ███████
    // ██    ██    ██     ██  ██       ██
    // ██    ██    ██     ██  ██       ███████
    // ██    ██    ██     ██  ██            ██
    //  ██████     ██     ██  ███████  ███████

    private void log(LogLevel level, String message)
    {
        if(level.ordinal() <= logLevel.ordinal()) {
            Log.d(TAG, message);
        }
    }

    private void invokeMethodUIThread(final String method, HashMap<String, Object> data)
    {
        new Handler(Looper.getMainLooper()).post(() -> {
            //Could already be teared down at this moment
//            if (methodChannel != null) {
//                methodChannel.invokeMethod(method, data);
//            } else {
//                Log.w(TAG, "invokeMethodUIThread: tried to call method on closed channel: " + method);
//            }
        });
    }

    private static byte[] hexToBytes(String s) {
        if (s == null) {
            return new byte[0];
        }
        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                + Character.digit(s.charAt(i+1), 16));
        }

        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String connectionStateString(int cs) {
        switch (cs) {
            case BluetoothProfile.STATE_DISCONNECTED:  return "disconnected";
            case BluetoothProfile.STATE_CONNECTING:    return "connecting";
            case BluetoothProfile.STATE_CONNECTED:     return "connected";
            case BluetoothProfile.STATE_DISCONNECTING: return "disconnecting";
            default:                                   return "UNKNOWN_CONNECTION_STATE (" + cs + ")";
        }
    }

    private static String adapterStateString(int as) {
        switch (as) {
            case BluetoothAdapter.STATE_OFF:          return "off";
            case BluetoothAdapter.STATE_ON:           return "on";
            case BluetoothAdapter.STATE_TURNING_OFF:  return "turningOff";
            case BluetoothAdapter.STATE_TURNING_ON:   return "turningOn";
            default:                                  return "UNKNOWN_ADAPTER_STATE (" + as + ")";
        }
    }

    private static String bondStateString(int bs) {
        switch (bs) {
            case BluetoothDevice.BOND_BONDING: return "bonding";
            case BluetoothDevice.BOND_BONDED:  return "bonded";
            case BluetoothDevice.BOND_NONE:    return "bond-none";
            default:                           return "UNKNOWN_BOND_STATE (" + bs + ")";
        }
    }

    private static String gattErrorString(int value) {
        switch(value) {
            case BluetoothGatt.GATT_SUCCESS                     : return "GATT_SUCCESS";
            case BluetoothGatt.GATT_CONNECTION_CONGESTED        : return "GATT_CONNECTION_CONGESTED";
            case BluetoothGatt.GATT_FAILURE                     : return "GATT_FAILURE";
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION : return "GATT_INSUFFICIENT_AUTHENTICATION";
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION  : return "GATT_INSUFFICIENT_AUTHORIZATION";
            case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION     : return "GATT_INSUFFICIENT_ENCRYPTION";
            case BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH    : return "GATT_INVALID_ATTRIBUTE_LENGTH";
            case BluetoothGatt.GATT_INVALID_OFFSET              : return "GATT_INVALID_OFFSET";
            case BluetoothGatt.GATT_READ_NOT_PERMITTED          : return "GATT_READ_NOT_PERMITTED";
            case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED       : return "GATT_REQUEST_NOT_SUPPORTED";
            case BluetoothGatt.GATT_WRITE_NOT_PERMITTED         : return "GATT_WRITE_NOT_PERMITTED";
            default: return "UNKNOWN_GATT_ERROR (" + value + ")";
        }
    }

    private static String bluetoothStatusString(int value) {
        switch(value) {
            case BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED                : return "ERROR_BLUETOOTH_NOT_ALLOWED";
            case BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED                : return "ERROR_BLUETOOTH_NOT_ENABLED";
            case BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED                    : return "ERROR_DEVICE_NOT_BONDED";
            case BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED               : return "ERROR_GATT_WRITE_NOT_ALLOWED";
            case BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY              : return "ERROR_GATT_WRITE_REQUEST_BUSY";
            case BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION : return "ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION";
            case BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND            : return "ERROR_PROFILE_SERVICE_NOT_BOUND";
            case BluetoothStatusCodes.ERROR_UNKNOWN                              : return "ERROR_UNKNOWN";
            //case BluetoothStatusCodes.FEATURE_NOT_CONFIGURED                     : return "FEATURE_NOT_CONFIGURED";
            case BluetoothStatusCodes.FEATURE_NOT_SUPPORTED                      : return "FEATURE_NOT_SUPPORTED";
            case BluetoothStatusCodes.FEATURE_SUPPORTED                          : return "FEATURE_SUPPORTED";
            case BluetoothStatusCodes.SUCCESS                                    : return "SUCCESS";
            default: return "UNKNOWN_BLE_ERROR (" + value + ")";
        }
    }

    private static String scanFailedString(int value) {
        switch(value) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED                : return "SCAN_FAILED_ALREADY_STARTED";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED: return "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED            : return "SCAN_FAILED_FEATURE_UNSUPPORTED";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR                 : return "SCAN_FAILED_INTERNAL_ERROR";
            case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES      : return "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES";
            case ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY        : return "SCAN_FAILED_SCANNING_TOO_FREQUENTLY";
            default: return "UNKNOWN_SCAN_ERROR (" + value + ")";
        }
    }


    // Defined in the Bluetooth Standard, Volume 1, Part F, 1.3 HCI Error Code, pages 364-377.
    // See https://www.bluetooth.org/docman/handlers/downloaddoc.ashx?doc_id=478726,
    private static String hciStatusString(int value) {
         switch(value) {
            case 0x00: return "SUCCESS";
            case 0x01: return "UNKNOWN_COMMAND"; // The controller does not understand the HCI Command Packet OpCode that the Host sent.
            case 0x02: return "UNKNOWN_CONNECTION_IDENTIFIER"; // The connection identifier used is unknown
            case 0x03: return "HARDWARE_FAILURE"; // A hardware failure has occurred
            case 0x04: return "PAGE_TIMEOUT"; // a page timed out because of the Page Timeout configuration parameter.
            case 0x05: return "AUTHENTICATION_FAILURE"; // Pairing or authentication failed. This could be due to an incorrect PIN or Link Key.
            case 0x06: return "PIN_OR_KEY_MISSING"; // Pairing failed because of a missing PIN
            case 0x07: return "MEMORY_FULL"; // The Controller has run out of memory to store new parameters.
            case 0x08: return "CONNECTION_TIMEOUT"; // The link supervision timeout has expired for a given connection.
            case 0x09: return "CONNECTION_LIMIT_EXCEEDED"; // The Controller is already at its limit of the number of connections it can support.
            case 0x0A: return "MAX_NUM_OF_CONNECTIONS_EXCEEDED"; // The Controller has reached the limit of connections
            case 0x0B: return "CONNECTION_ALREADY_EXISTS"; // A connection to this device already exists 
            case 0x0C: return "COMMAND_DISALLOWED"; // The command requested cannot be executed by the Controller at this time.
            case 0x0D: return "CONNECTION_REJECTED_LIMITED_RESOURCES"; // A connection was rejected due to limited resources.
            case 0x0E: return "CONNECTION_REJECTED_SECURITY_REASONS"; // A connection was rejected due to security, e.g. aauth or pairing.
            case 0x0F: return "CONNECTION_REJECTED_UNACCEPTABLE_MAC_ADDRESS"; // connection rejected, this device does not accept the BD_ADDR
            case 0x10: return "CONNECTION_ACCEPT_TIMEOUT_EXCEEDED"; // Connection Accept Timeout exceeded for this connection attempt.
            case 0x11: return "UNSUPPORTED_PARAMETER_VALUE"; // A feature or parameter value in the HCI command is not supported.
            case 0x12: return "INVALID_COMMAND_PARAMETERS"; // At least one of the HCI command parameters is invalid.
            case 0x13: return "REMOTE_USER_TERMINATED_CONNECTION"; // The user on the remote device terminated the connection.
            case 0x14: return "REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES"; // remote device terminated connection due to low resources.
            case 0x15: return "REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF"; // The remote device terminated the connection due to power off
            case 0x16: return "CONNECTION_TERMINATED_BY_LOCAL_HOST"; // The local device terminated the connection.
            case 0x17: return "REPEATED_ATTEMPTS"; // The Controller is disallowing auth because of too quick attempts.
            case 0x18: return "PAIRING_NOT_ALLOWED"; // The device does not allow pairing
            case 0x19: return "UNKNOWN_LMP_PDU"; // The Controller has received an unknown LMP OpCode.
            case 0x1A: return "UNSUPPORTED_REMOTE_FEATURE"; // The remote device does not support feature for the issued command or LMP PDU.
            case 0x1B: return "SCO_OFFSET_REJECTED"; // The offset requested in the LMP_SCO_link_req PDU has been rejected.
            case 0x1C: return "SCO_INTERVAL_REJECTED"; // The interval requested in the LMP_SCO_link_req PDU has been rejected.
            case 0x1D: return "SCO_AIR_MODE_REJECTED"; // The air mode requested in the LMP_SCO_link_req PDU has been rejected.
            case 0x1E: return "INVALID_LMP_OR_LL_PARAMETERS"; // Some LMP PDU / LL Control PDU parameters were invalid.
            case 0x1F: return "UNSPECIFIED"; // No other error code specified is appropriate to use
            case 0x20: return "UNSUPPORTED_LMP_OR_LL_PARAMETER_VALUE"; // An LMP PDU or an LL Control PDU contains a value that is not supported
            case 0x21: return "ROLE_CHANGE_NOT_ALLOWED"; // a Controller will not allow a role change at this time.
            case 0x22: return "LMP_OR_LL_RESPONSE_TIMEOUT"; // An LMP transaction failed to respond within the LMP response timeout
            case 0x23: return "LMP_OR_LL_ERROR_TRANS_COLLISION"; // An LMP transaction or LL procedure has collided with the same transaction
            case 0x24: return "LMP_PDU_NOT_ALLOWED"; // A Controller sent an LMP PDU with an OpCode that was not allowed.
            case 0x25: return "ENCRYPTION_MODE_NOT_ACCEPTABLE"; // The requested encryption mode is not acceptable at this time.
            case 0x26: return "LINK_KEY_CANNOT_BE_EXCHANGED"; // A link key cannot be changed because a fixed unit key is being used.
            case 0x27: return "REQUESTED_QOS_NOT_SUPPORTED"; // The requested Quality of Service is not supported.
            case 0x28: return "INSTANT_PASSED"; // The LMP PDU or LL PDU instant has already passed
            case 0x29: return "PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED"; // It was not possible to pair as a unit key is not supported.
            case 0x2A: return "DIFFERENT_TRANSACTION_COLLISION"; // An LMP transaction or LL Procedure collides with an ongoing transaction.
            case 0x2B: return "UNDEFINED_0x2B"; // Undefined error code
            case 0x2C: return "QOS_UNACCEPTABLE_PARAMETER"; // The quality of service parameters could not be accepted at this time.
            case 0x2D: return "QOS_REJECTED"; // The specified quality of service parameters cannot be accepted. negotiation should be terminated
            case 0x2E: return "CHANNEL_CLASSIFICATION_NOT_SUPPORTED"; // The Controller cannot perform channel assessment. not supported.
            case 0x2F: return "INSUFFICIENT_SECURITY"; // The HCI command or LMP PDU sent is only possible on an encrypted link.
            case 0x30: return "PARAMETER_OUT_OF_RANGE"; // A parameter in the HCI command is outside of valid range
            case 0x31: return "UNDEFINED_0x31"; // Undefined error
            case 0x32: return "ROLE_SWITCH_PENDING"; // A Role Switch is pending, sothe HCI command or LMP PDU is rejected
            case 0x33: return "UNDEFINED_0x33"; // Undefined error
            case 0x34: return "RESERVED_SLOT_VIOLATION"; // Synchronous negotiation terminated with negotiation state set to Reserved Slot Violation.
            case 0x35: return "ROLE_SWITCH_FAILED"; // A role switch was attempted but it failed and the original piconet structure is restored.
            case 0x36: return "INQUIRY_RESPONSE_TOO_LARGE"; // The extended inquiry response is too large to fit in packet supported by Controller.
            case 0x37: return "SECURE_SIMPLE_PAIRING_NOT_SUPPORTED"; // Host does not support Secure Simple Pairing, but receiving Link Manager does.
            case 0x38: return "HOST_BUSY_PAIRING"; // The Host is busy with another pairing operation. The receiving device should retry later.
            case 0x39: return "CONNECTION_REJECTED_NO_SUITABLE_CHANNEL"; // Controller could not calculate an appropriate value for Channel selection.
            case 0x3A: return "CONTROLLER_BUSY"; // The Controller was busy and unable to process the request.
            case 0x3B: return "UNACCEPTABLE_CONNECTION_PARAMETERS"; // The remote device terminated connection, unacceptable connection parameters.
            case 0x3C: return "ADVERTISING_TIMEOUT"; // Advertising completed. Or for directed advertising, no connection was created.
            case 0x3D: return "CONNECTION_TERMINATED_MIC_FAILURE"; // Connection terminated because Message Integrity Check failed on received packet.
            case 0x3E: return "CONNECTION_FAILED_ESTABLISHMENT"; // The LL initiated a connection but the connection has failed to be established.
            case 0x3F: return "MAC_CONNECTION_FAILED"; // The MAC of the 802.11 AMP was requested to connect to a peer, but the connection failed.
            case 0x40: return "COARSE_CLOCK_ADJUSTMENT_REJECTED"; // The master is unable to make a coarse adjustment to the piconet clock.
            case 0x41: return "TYPE0_SUBMAP_NOT_DEFINED"; // The LMP PDU is rejected because the Type 0 submap is not currently defined.
            case 0x42: return "UNKNOWN_ADVERTISING_IDENTIFIER"; // A command was sent from the Host but the Advertising or Sync handle does not exist.
            case 0x43: return "LIMIT_REACHED"; // The number of operations requested has been reached and has indicated the completion of the activity
            case 0x44: return "OPERATION_CANCELLED_BY_HOST"; // A request to the Controller issued by the Host and still pending was successfully canceled.
            case 0x45: return "PACKET_TOO_LONG"; // An attempt was made to send or receive a packet that exceeds the maximum allowed packet length.
            case 0x85: return "ANDROID_SPECIFIC_ERROR"; // Additional Android specific errors
            case 0x101: return "FAILURE_REGISTERING_CLIENT"; //  max of 30 clients has been reached.
            default: return "UNKNOWN_HCI_ERROR (" + value + ")";
         }
    }


    enum LogLevel
    {
        NONE,    // 0
        ERROR,   // 1
        WARNING, // 2
        INFO,    // 3
        DEBUG,   // 4
        VERBOSE  // 5
    }
}