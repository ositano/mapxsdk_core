//package com.afex.mapx;
//
//
//import android.bluetooth.BluetoothAdapter;
//import android.bluetooth.BluetoothGattCharacteristic;
//import android.bluetooth.le.ScanResult;
//import android.content.Context;
//import android.os.Handler;
//import android.util.Log;
//
//import com.afex.mapxsdklicense.MapXLicense;
//import com.welie.blessed.BluetoothBytesParser;
//import com.welie.blessed.BluetoothCentralManager;
//import com.welie.blessed.BluetoothCentralManagerCallback;
//import com.welie.blessed.BluetoothPeripheral;
//import com.welie.blessed.BluetoothPeripheralCallback;
//import com.welie.blessed.BondState;
//import com.welie.blessed.ConnectionPriority;
//import com.welie.blessed.ConnectionState;
//import com.welie.blessed.GattStatus;
//import com.welie.blessed.HciStatus;
//
//import com.welie.blessed.PhyOptions;
//import com.welie.blessed.PhyType;
//import com.welie.blessed.ScanFailure;
//import com.welie.blessed.WriteType;
//
//import org.jetbrains.annotations.NotNull;
//
//import java.nio.ByteOrder;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.UUID;
//import java.util.regex.Pattern;
//
//import static com.welie.blessed.BluetoothBytesParser.asHexString;
//
//public class MapXBluetoothService {
//
//    public static final String TAG = "MapXBluetoothService";
//
////    // UUIDs for the Device Information service (DIS)
////    private static final UUID DIS_SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");
////    private static final UUID MANUFACTURER_NAME_CHARACTERISTIC_UUID = UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
////    private static final UUID MODEL_NUMBER_CHARACTERISTIC_UUID = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");
//
//
//    private static final UUID GENERIC_SERVICE_UUID = UUID.fromString("00001800-0000-1000-8000-00805F9B34FB");
//    private static final UUID GENERIC_CHARACTERISTICS_UUID = UUID.fromString("00001801-0000-1000-8000-00805F9B34FB");
//    // Local variables
//    public BluetoothCentralManager central;
//    private static MapXBluetoothService instance = null;
//    private final Context context;
//
//    private final EventEmitter emitter;
//    private final Handler handler = new Handler();
//
//    private String licenseKey = "";
//    private boolean isLicenseValid = false;
//
//    private void checkLicense() throws MapXLicense.LicenseInitializationException, MapXLicense.InvalidLicenseException {
//        if(licenseKey.isEmpty()){
//            throw new MapXLicense.LicenseInitializationException("License key has not been initialized");
//        }
//        if(!isLicenseValid){
//            throw new MapXLicense.InvalidLicenseException("Invalid license key");
//        }
//    }
//
//    public boolean initialize(String licenseKey){
//        this.licenseKey = licenseKey;
//        boolean licenseStatus = MapXLicense.INSTANCE.isLicenseValid(licenseKey);
//        this.isLicenseValid = licenseStatus;
//        return licenseStatus;
//    }
//
//    public String getLicenseAPIInfo() throws MapXLicense.LicenseInitializationException, MapXLicense.InvalidLicenseException {
//        checkLicense();
//        return MapXLicense.INSTANCE.getLicenseAPIInfo(licenseKey);
//    }
//
//    public String getLicenseInfo() throws MapXLicense.LicenseInitializationException, MapXLicense.InvalidLicenseException {
//        checkLicense();
//        return MapXLicense.INSTANCE.getLicenseInfo(licenseKey);
//    }
//
//    // Callback for peripherals
//    private final BluetoothPeripheralCallback peripheralCallback = new BluetoothPeripheralCallback() {
//        @Override
//        public void onServicesDiscovered(@NotNull BluetoothPeripheral peripheral) {
//            // Request a higher MTU, iOS always asks for 185
//            peripheral.requestMtu(185);
//
//            // Request a new connection priority
//            peripheral.requestConnectionPriority(ConnectionPriority.HIGH);
//
//            peripheral.setPreferredPhy(PhyType.LE_2M, PhyType.LE_2M, PhyOptions.S2);
//
//            // Read manufacturer and model number from the Device Information Service
//            peripheral.readCharacteristic(GENERIC_SERVICE_UUID, GENERIC_CHARACTERISTICS_UUID);
//            peripheral.readPhy();
//
//            Map<String, Object> emitObject = new HashMap<>();
//            emitObject.put("type", "ble_scanning_found");
//            emitObject.put("data", deviceToJson(peripheral));
//            emitter.success(emitObject);
//        }
//
//        @Override
//        public void onNotificationStateUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
//            if (status == GattStatus.SUCCESS) {
//                final boolean isNotifying = peripheral.isNotifying(characteristic);
//                String msg = String.format("SUCCESS: Notify set to '%s' for %s", isNotifying, characteristic.getUuid());
//                Log.d(TAG, msg);
//            } else {
//                String msg = String.format("ERROR: Changing notification state failed for %s (%s)", characteristic.getUuid(), status);
//                Log.d(TAG, msg);
//            }
//        }
//
//        @Override
//        public void onCharacteristicWrite(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
//            if (status == GattStatus.SUCCESS) {
//                    String msg = String.format("SUCCESS: Writing <%s> to <%s>", asHexString(value), characteristic.getUuid());
//                    Log.d(TAG, msg);
//            } else {
//                String msg = String.format("ERROR: Failed writing <%s> to <%s> (%s)", asHexString(value), characteristic.getUuid(), status);
//                Log.d(TAG, msg);
//            }
//        }
//
//        @Override
//        public void onCharacteristicUpdate(@NotNull BluetoothPeripheral peripheral, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
//            if (status != GattStatus.SUCCESS) return;
//
//            UUID characteristicUUID = characteristic.getUuid();
//            BluetoothBytesParser parser = new BluetoothBytesParser(value);
//            String messageFromDevice = parser.getStringValue();
//
//            if (characteristicUUID.equals(GENERIC_CHARACTERISTICS_UUID)) {
//                if (isNumeric(messageFromDevice)){
//                    Map<String, Object> emitObject = new HashMap<>();
//                    emitObject.put("type", "ble_readBattery");
//                    emitObject.put("data", messageFromDevice);
//                    emitter.success(emitObject);///likely a battery level
//                }else if (messageFromDevice.contains("MAPX")){
//                    Map<String, Object> emitObject = new HashMap<>();
//                    emitObject.put("type", "ble_deviceInfo");
//                    emitObject.put("data", messageFromDevice);
//                    emitter.success(emitObject);///likely the device info
//                }else if (messageFromDevice.equals("OK")){
//                    Map<String, Object> emitObject = new HashMap<>();
//                    emitObject.put("type", "ble_endSession");
//                    emitObject.put("data", messageFromDevice);
//                    emitter.success(emitObject);
//                }else {
//                    //Log.d(TAG, "  <<<*>>> read data 2: $lastMessage")
//                    if (hasCoordinatesFormat(messageFromDevice) || hasCoordinatesWithHashFormat(messageFromDevice) || messageFromDevice.equals("#")) {
//                        //Log.d(TAG, "  <<<*>>> sending data 1 .... : $lastMessage")
//                        Map<String, Object> dataObject = new HashMap<>();
//                        dataObject.put("device", deviceToJson(peripheral));
//                        dataObject.put("response", messageFromDevice);
//                        Map<String, Object> emitObject = new HashMap<>();
//                        emitObject.put("type", "ble_takePoint");
//                        emitObject.put("data", dataObject);
//                        emitter.success(emitObject);
//                    }
//
//                    // Send the obtained bytes to the UI activity.
//                    if (messageFromDevice.contains("\n") || messageFromDevice.contains("\r")) {
//                        String reg = "[\r\n]+";
//                        String[] lines = messageFromDevice.split(reg);
//                        //Log.d(TAG, "  <<<*>>> sending data 2 .... : ${lines[0]}")
//                        if (hasCoordinatesFormat(lines[0]) || hasCoordinatesWithHashFormat(lines[0])) {
//                            Map<String, Object> dataObject = new HashMap<>();
//                            dataObject.put("device", deviceToJson(peripheral));
//                            dataObject.put("response", messageFromDevice);
//                            Map<String, Object> emitObject = new HashMap<>();
//                            emitObject.put("type", "ble_takePoint");
//                            emitObject.put("data", dataObject);
//                            emitter.success(emitObject);
//                        }
//                    }
//                }
//            }
//        }
//
//        @Override
//        public void onMtuChanged(@NotNull BluetoothPeripheral peripheral, int mtu, @NotNull GattStatus status) {
//            //Log.d(TAG, "new MTU set: %d", mtu);
//        }
//    };
//
//
//    public void pingConnection(@NotNull String uuid) {
//        BluetoothCentralManager central = instance.central;
//        BluetoothPeripheral peripheral =  central.getPeripheral(uuid);
//        BluetoothBytesParser parser = new BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN);
//        parser.setString("PNG");
//        peripheral.writeCharacteristic(GENERIC_SERVICE_UUID, GENERIC_CHARACTERISTICS_UUID, parser.getValue(), WriteType.WITH_RESPONSE);
//    }
//
//    public void getDeviceInfo(@NotNull String uuid) {
//        BluetoothCentralManager central = instance.central;
//        BluetoothPeripheral peripheral =  central.getPeripheral(uuid);
//        BluetoothBytesParser parser = new BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN);
//        parser.setString("GDI");
//        peripheral.writeCharacteristic(GENERIC_SERVICE_UUID, GENERIC_CHARACTERISTICS_UUID, parser.getValue(), WriteType.WITH_RESPONSE);
//    }
//
//    public void takePoint(@NotNull String uuid) {
//        BluetoothCentralManager central = instance.central;
//        BluetoothPeripheral peripheral =  central.getPeripheral(uuid);
//        BluetoothBytesParser parser = new BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN);
//        parser.setString("TPT");
//        peripheral.writeCharacteristic(GENERIC_SERVICE_UUID, GENERIC_CHARACTERISTICS_UUID, parser.getValue(), WriteType.WITH_RESPONSE);
//    }
//
//    public void endSession(@NotNull String uuid) {
//        BluetoothCentralManager central = instance.central;
//        BluetoothPeripheral peripheral =  central.getPeripheral(uuid);
//        BluetoothBytesParser parser = new BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN);
//        parser.setString("ESS");
//        peripheral.writeCharacteristic(GENERIC_SERVICE_UUID, GENERIC_CHARACTERISTICS_UUID, parser.getValue(), WriteType.WITH_RESPONSE);
//    }
//
//    public static synchronized MapXBluetoothService getInstance(Context context, EventEmitter emitter) {
//        if (instance == null) {
//            instance = new MapXBluetoothService(context, emitter);
//        }
//        return instance;
//    }
//
//    public MapXBluetoothService(Context context, EventEmitter emitter) {
//        this.context = context;
//        this.emitter = emitter;
//
//        // Create BluetoothCentral
//        // Callback for central
//        // Reconnect to this device when it becomes available again
//        // Create a bond immediately to avoid double pairing popups
//        // Bluetooth is on now, start scanning again
//        // Scan for peripherals with a certain service UUIDs
//        BluetoothCentralManagerCallback bluetoothCentralManagerCallback = new BluetoothCentralManagerCallback() {
//            @Override
//            public void onConnectedPeripheral(@NotNull BluetoothPeripheral peripheral) {
//                String msg = String.format("connected to '%s'", peripheral.getName());
//                Log.d(TAG, msg);
//
//                Map<String, Object> dataObject = new HashMap<>();
//                dataObject.put("event", "connected");
//                dataObject.put("device", deviceToJson(peripheral));
//                Map<String, Object> emitObject = new HashMap<>();
//                emitObject.put("type", "connection");
//                emitObject.put("data", dataObject);
//                emitter.success(emitObject);
//            }
//
//            @Override
//            public void onConnectionFailed(@NotNull BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
//                String msg = String.format("connection '%s' failed with status %s", peripheral.getName(), status);
//                Log.d(TAG, msg);
//                Map<String, Object> dataObject = new HashMap<>();
//                dataObject.put("event", "failed");
//                dataObject.put("device", deviceToJson(peripheral));
//                Map<String, Object> emitObject = new HashMap<>();
//                emitObject.put("type", "connection");
//                emitObject.put("data", dataObject);
//                emitter.success(emitObject);
//            }
//
//            @Override
//            public void onDisconnectedPeripheral(@NotNull final BluetoothPeripheral peripheral, final @NotNull HciStatus status) {
//                String msg = String.format("disconnected '%s' with status %s", peripheral.getName(), status);
//                Log.d(TAG, msg);
//
//                Map<String, Object> dataObject = new HashMap<>();
//                dataObject.put("event", "disconnected");
//                dataObject.put("device", deviceToJson(peripheral));
//                Map<String, Object> emitObject = new HashMap<>();
//                emitObject.put("type", "connection");
//                emitObject.put("data", dataObject);
//                emitter.success(emitObject);
//
//                // Reconnect to this device when it becomes available again
//                handler.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        central.autoConnectPeripheral(peripheral, peripheralCallback);
//                    }
//                }, 5000);
//            }
//
//            @Override
//            public void onDiscoveredPeripheral(@NotNull BluetoothPeripheral peripheral, @NotNull ScanResult scanResult) {
//                String msg = String.format("Found peripheral '%s'", peripheral.getName());
//                Log.d(TAG, msg);
//                central.stopScan();
//
//                if (peripheral.getName().contains("Contour") && peripheral.getBondState() == BondState.NONE) {
//                    // Create a bond immediately to avoid double pairing popups
//                    central.createBond(peripheral, peripheralCallback);
//                } else {
//                    central.connectPeripheral(peripheral, peripheralCallback);
//                }
//            }
//
//            @Override
//            public void onBluetoothAdapterStateChanged(int state) {
//                String msg = String.format("bluetooth adapter changed state to %d", state);
//                Log.d(TAG, msg);
//                Map<String, Object> emitObject = new HashMap<>();
//                emitObject.put("type", "state");
//                emitObject.put("data", state);
//                emitter.success(emitObject);
//                if (state == BluetoothAdapter.STATE_ON) {
//                    // Bluetooth is on now, start scanning again
//                    // Scan for peripherals with a certain service UUIDs
//                    central.startPairingPopupHack();
//                    startScan();
//                }
//            }
//
//            @Override
//            public void onScanFailed(@NotNull ScanFailure scanFailure) {
//                String msg = String.format("scanning failed with error %s", scanFailure);
//                Log.d(TAG, msg);
//                Map<String, Object> emitObject = new HashMap<>();
//                emitObject.put("type", "scanningState");
//                emitObject.put("data", false);
//                emitter.success(emitObject);
//            }
//        };
//        central = new BluetoothCentralManager(context, bluetoothCentralManagerCallback, new Handler());
//        // Scan for peripherals with a certain service UUIDs
//        central.startPairingPopupHack();
//        startScan();
//    }
//
//    private void startScan() {
//        Map<String, Object> emitObject = new HashMap<>();
//        emitObject.put("type", "scanningState");
//        emitObject.put("data", true);
//        emitter.success(emitObject);
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                central.scanForPeripheralsWithServices(new UUID[]{GENERIC_SERVICE_UUID}
//                );
//            }
//        },1000);
//    }
//
//    private final Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");
//    private boolean isNumeric(String strNum) {
//        if (strNum == null) {
//            return false;
//        }
//        return pattern.matcher(strNum).matches();
//    }
//
//    private boolean hasTimeFormat(String input) {
//        Pattern timeRegex = Pattern.compile("^\\d{1,2}:\\d{1,2}:\\d{1,2}$");
//        return timeRegex.matcher(input).matches();
//    }
//
//    private boolean hasCoordinatesFormat(String input){
//        Pattern coordinatesRegex = Pattern.compile("\\$,\\d+\\.\\d+,\\d+\\.\\d+,\\d{1,2}/\\d{1,2}/\\d{4},\\d{1,2}:\\d{1,2}:\\d{1,2}");
//        return coordinatesRegex.matcher(input).matches();
//    }
//
//    private boolean hasCoordinatesWithHashFormat(String input){
//        Pattern coordinatesRegex = Pattern.compile("\\#,\\$,\\d+\\.\\d+,\\d+\\.\\d+,\\d{1,2}/\\d{1,2}/\\d{4},\\d{1,2}:\\d{1,2}:\\d{1,2}");
//        return coordinatesRegex.matcher(input).matches();
//    }
//    private HashMap<String, Object> deviceToJson(BluetoothPeripheral device){
//        HashMap<String, Object> json = new HashMap<>();
//        json.put("name", device.getName());
//        json.put("uuid", device.getAddress());
//        json.put("isConnected", device.getState().value == ConnectionState.CONNECTED.value);
//        return json;
//    }
//}
