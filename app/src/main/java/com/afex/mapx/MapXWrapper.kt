package com.afex.mapx

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.afex.mapxsdklicense.MapXLicense


private const val TAG = "MapXWrapper"


class MapXWrapper(private val appCompatActivity: AppCompatActivity, private val context: Context, private val eventSink: EventEmitter) {

    private val messageRead: Int = 0
    private val messageTakePoint = 3
    private val messageEndSession = 4
    private val messageReadBattery = 5
    private val messageShutDown = 6
    private val messageDeviceInfo = 7

    private lateinit var licenseKey: String
    private var isLicenseValid: Boolean? = null
    private var serviceUUID: String? = null
    private var connectedUUID: String? = null

    private var hasBluetoothPermissionBeenGranted = false
    private var hasLocationPermissionBeenGranted = false


    private var handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            // Handle the message here
            handleMessageFromBluetoothDevice(msg)
            // Your code to handle the message goes here
        }
    }
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothManager: BluetoothManager

    private val devices = mutableMapOf<String, BluetoothDevice>()
    private val connections = mutableMapOf<String, ConnectThread>()

    private val scanningReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleBluetoothEvent(intent)
        }
    }

    init {
        bluetoothManager = ContextCompat.getSystemService(
            context,
            BluetoothManager::class.java
        )!!
        bluetoothAdapter = bluetoothManager.adapter
        licenseKey = ""
    }

    private fun checkLicense(){
        if(isLicenseValid == null){
            throw MapXLicense.LicenseInitializationException("License key has not been initialized")
        }
        if(isLicenseValid == false){
            throw MapXLicense.InvalidLicenseException("Invalid license key")
        }
    }

    fun initialize(licenseKey: String): Boolean{
        this.licenseKey = licenseKey
        val licenseStatus = MapXLicense.isLicenseValid(licenseKey)
        this.isLicenseValid = licenseStatus
        return licenseStatus
    }


    /***************************************************************************************************************/
    fun hasBluetoothPermission(): Boolean{
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            hasBluetoothPermissionBeenGranted = false
            return false
        }
        hasBluetoothPermissionBeenGranted = true
        return true
    }

    fun hasLocationPermission(): Boolean{
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            hasLocationPermissionBeenGranted = false
            return false
        }
        hasLocationPermissionBeenGranted = true
        return true
    }

    fun requestBluetoothPermission(){
//        if (ActivityCompat.checkSelfPermission(
//                context,
//                Manifest.permission.BLUETOOTH
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            ble.verifyPermissionsAsync(
//                rationaleRequestCallback = {
//                    // Include your code to show an Alert or UI explaining why the permissions are required
//                    // Calling the function bellow if the user agrees to give the permissions
//                    //next()
//                },
//                callback = { granted ->
//                    hasBluetoothPermissionBeenGranted = granted
//                }
//            )
//        }
    }



    fun getLicenseAPIInfo():String{
        checkLicense();
        return MapXLicense.getLicenseAPIInfo(licenseKey)
    }

    fun getLicenseInfo():String{
        checkLicense();
        return MapXLicense.getLicenseInfo(licenseKey)
    }

    fun doneCapturing(uuid: String){
        val string = "OK"
        val data = string.toByteArray()
        connections[uuid]?.write(data)
    }

    fun bluetoothState(): Boolean{
        checkLicense();
        return bluetoothAdapter.isEnabled
    }

    @SuppressLint("MissingPermission")
    fun bluetoothTurnOn(): Boolean{
        checkLicense();
//        if (!bluetoothAdapter.isEnabled) {
//            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//            startActivityForResult(enableBtIntent, android.bluetooth.adapter.action.REQUEST_DISABLE)
//
//            android.bluetooth.adapter.action.REQUEST_DISABLE
//        }
        return bluetoothAdapter.enable()
    }

    @SuppressLint("MissingPermission")
    fun bluetoothTurnOff(): Boolean{
        //checkLicense();
        return bluetoothAdapter.disable()
    }

    @SuppressLint("MissingPermission")
    fun getPairedDeviceList(): List<List<BluetoothDevice>> {
        checkLicense();
        val bondedDevices = bluetoothAdapter.bondedDevices
        val connectedDevices = arrayListOf<BluetoothDevice>()
        for (profile in arrayOf(BluetoothProfile.GATT, BluetoothProfile.GATT_SERVER)) {
            connectedDevices.addAll(bluetoothManager.getConnectedDevices(profile))
        }

        Log.d(TAG, "connected devices: ${connectedDevices.size}")
        Log.d(TAG, "paired devices: ${bondedDevices.size}")
        return listOf(bondedDevices.toList(), connectedDevices)
    }

    @SuppressLint("MissingPermission")
    private fun deviceToJson(device: BluetoothDevice, connected: Boolean = false) = mapOf(
        "name" to device.name,
        "uuid" to device.address,
        "isConnected" to connected
    )

    @SuppressLint("MissingPermission")
    fun startScanning(serviceUUID: String) {
        Log.d(TAG, "Checking Bluetooth Discovery -> ${bluetoothAdapter.isDiscovering}")
        if (bluetoothAdapter.isDiscovering) {
            Log.d(TAG, "Cancelling Bluetooth Discovery .....")
            bluetoothAdapter.cancelDiscovery()
        }
        checkLicense();
        this.serviceUUID = serviceUUID
        for (action in arrayOf(
            BluetoothAdapter.ACTION_STATE_CHANGED,
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothAdapter.ACTION_DISCOVERY_STARTED,
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_BOND_STATE_CHANGED,
            BluetoothDevice.ACTION_FOUND
        )) {

            Log.d(TAG, "register different receivers.....")
            ContextWrapper(context).registerReceiver(
                scanningReceiver,
                IntentFilter(action)
            )
        }

        Log.d(TAG, "About to start Bluetooth Discovery")
        bluetoothAdapter.startDiscovery()
        Log.d(TAG, "Start Bluetooth Discovery started")
        //registerReceiver()
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
    }

    fun connectToDevice(uuid: String): Boolean {
        checkLicense();
        bluetoothAdapter.getRemoteDevice(uuid)?.let {
            return connectToDeviceInternal(it)
        }
        return false
    }

    fun disconnectDevice(uuid: String) {
        connections[uuid]?.let {
            it.cancel()
            Log.d(TAG, "Done closing socket ....")
            connections.remove(uuid)
            Log.d(TAG, "Done removing uuid from connections")
            connectedUUID = null
            Log.d(TAG, "We're good to go ....")

                bluetoothAdapter.getRemoteDevice(uuid)?.let {device ->
                    return eventSink.success(
                        mapOf(
                            "type" to "connection",
                            "data" to mapOf(
                                "event" to "disconnected",
                                "device" to deviceToJson(device)
                            )
                        )
                    )
                }

            return
        }
    }

    fun writeData(uuid: String, data: ByteArray) {
        checkLicense();
        connections[uuid]?.write(data)
    }

    private fun registerReceiver() {
        Log.d(TAG, "Registering Bluetooth events ....")
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        context.registerReceiver(scanningReceiver, filter)
    }

    fun unregisterReceiver() {
        context.unregisterReceiver(scanningReceiver)
    }

    @SuppressLint("MissingPermission")
    private fun handleBluetoothEvent(intent: Intent) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                Log.d(TAG, "Bluetooth state has changed....")
                eventSink.success(
                    mapOf(
                        "type" to "state",
                        "data" to intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)
                    )
                )
            }
            BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                Log.d(TAG, "Bluetooth Discovery started")
                eventSink.success(
                    mapOf(
                        "type" to "scanningState",
                        "data" to true
                    )
                )
            }
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                Log.d(TAG, "Bluetooth Discovery finished")
                eventSink.success(
                    mapOf(
                        "type" to "scanningState",
                        "data" to false
                    )
                )
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                (intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice)?.let { device ->
                    eventSink.success(
                        mapOf(
                            "type" to "connection",
                            "data" to mapOf(
                                "event" to "connected",
                                "device" to deviceToJson(device, true)
                            )
                        )
                    )

                    Log.d(
                        TAG,
                        "Device Connected: ${device.name ?: "no_name"} [${device.address}]"
                    )
                }
            }
            BluetoothDevice.ACTION_FOUND -> {
                Log.d(TAG, "Device a New Found ...")
                (intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice)?.let { device ->
                    val deviceName = device.name ?: intent.getParcelableExtra(BluetoothDevice.EXTRA_NAME)
                    val deviceHardwareAddress = device.address // MAC address

                    if (serviceUUID == null || device.uuids == null || device.uuids!!.any { it.uuid.toString() == serviceUUID }) {
                        if (deviceName == null) return
                        if (devices[deviceHardwareAddress] != null) return

                        devices[deviceHardwareAddress] = device

                        Log.d(TAG, "Device New Found: $deviceName")

                        eventSink.success(
                            mapOf(
                                "type" to "scanning",
                                "data" to devices.values.map {
                                    deviceToJson(it)
                                }.toList()
                            )
                        )
                    }
                }
            }
            // Handle other Bluetooth events as needed
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDeviceInternal(device: BluetoothDevice): Boolean {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        val connection = ConnectThread(handler, device)
        connections[device.address] = connection

        Log.d(TAG, "bluetooth device connected with address - ${device.address} - ${device.name}")

        return try {
            connection.run()
            connectedUUID = device.address
            Log.d(TAG, "bluetooth device connected")
            true
        } catch (exception: Exception) {
            connectedUUID = null
            Log.d(TAG, "unable to connect to bluetooth")
            Log.d(TAG, exception.localizedMessage ?: exception.toString())
            false
        }
    }

    fun handleMessageFromBluetoothDevice(msg: Message) {
        val message = msg.obj as MessageObject
        val data = (message.data as ArrayList<*>)
            .map { it as UByte }
            .map { it.toInt() }
        when (msg.what) {
            messageRead -> {
                // Handle received data
                eventSink.success(
                    mapOf(
                        "type" to "data",
                        "data" to mapOf(
                            "bytes" to data,
                            "device" to deviceToJson(message.device),
                            "coordinates" to message.dataMessage
                        )
                    )
                )
            }

            messageDeviceInfo -> {
                eventSink.success(
                    mapOf(
                        "type" to "deviceInfo",
                        "data" to message.dataMessage
                    )
                )
            }

            messageTakePoint -> {
                eventSink.success(
                    mapOf(
                        "type" to "takePoint",
                        "data" to mapOf(
                            "bytes" to data,
                            "device" to deviceToJson(message.device),
                            "response" to message.dataMessage
                        )
                    )
                )
            }

            messageEndSession -> {
                eventSink.success(
                    mapOf(
                        "type" to "endSession",
                        "data" to message.dataMessage
                    )
                )
            }

            messageReadBattery -> {
                eventSink.success(
                    mapOf(
                        "type" to "readBattery",
                        "data" to  message.dataMessage
                    )
                )
            }

            messageShutDown -> {
                eventSink.success(
                    mapOf(
                        "type" to "shutDown",
                        "data" to message.dataMessage
                    )
                )
            }
        }
        Log.d(TAG, "$msg")
    }

    fun releaseResources() {
        // Release resources and close Bluetooth connections
        connections.values.forEach { it.cancel() }
        connections.clear()
    }

    fun pingConnection(){
        val string = "PNG"
        val data = string.toByteArray()
        connections[connectedUUID]?.write(data)
    }

    fun getDeviceInfo(){
        val string = "GDI"
        val data = string.toByteArray()
        connections[connectedUUID]?.write(data)
    }

    fun takePoint(){
        val string = "TPT"
        val data = string.toByteArray()
        connections[connectedUUID]?.write(data)
    }

    fun endSession(){
        val string = "ESS"
        val data = string.toByteArray()
        connections[connectedUUID]?.write(data)
    }

    fun readBattery(){
        val string = "BAR"
        val data = string.toByteArray()
        connections[connectedUUID]?.write(data)
    }

    fun shutDown(){
        val string = "SHD"
        val data = string.toByteArray()
        connections[connectedUUID]?.write(data)
    }

    private fun isNumber(s: String?): Boolean {
        return if (s.isNullOrEmpty()) false else s.all { Character.isDigit(it) }
    }

    private fun hasTimeFormat(input: String): Boolean {
        val timeRegex = Regex("^\\d{1,2}:\\d{1,2}:\\d{1,2}$")
        return timeRegex.matches(input)
    }

    private fun hasCoordinatesFormat(input: String): Boolean{
        val coordinatesRegex = Regex("\\$,\\d+\\.\\d+,\\d+\\.\\d+,\\d{1,2}/\\d{1,2}/\\d{4},\\d{1,2}:\\d{1,2}:\\d{1,2}")
        return coordinatesRegex.matches(input)
    }

    private fun hasCoordinatesWithHashFormat(input: String): Boolean{
        val coordinatesRegex = Regex("\\#,\\$,\\d+\\.\\d+,\\d+\\.\\d+,\\d{1,2}/\\d{1,2}/\\d{4},\\d{1,2}:\\d{1,2}:\\d{1,2}")
        return coordinatesRegex.matches(input)
    }
}
