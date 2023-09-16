package com.afex.mapx

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.core.content.ContextCompat
import com.afex.mapxsdklicense.MapXLicense


private const val TAG = "MapXWrapper"


class MapXWrapper(private val context: Context, private val eventSink: EventEmitter) {

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

//    private val handler = Handler(Looper.getMainLooper()){
//        override fun handleMessage(msg: Message) {
//            // Handle the message here
//            val what = msg.what // Retrieve the message identifier
//            val obj = msg.obj // Retrieve the message object
//            // Your code to handle the message goes here
//        }
//    }

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
        return bluetoothAdapter.enable()
    }

    @SuppressLint("MissingPermission")
    fun bluetoothTurnOff(): Boolean{
        //checkLicense();
        return bluetoothAdapter.disable()
    }

    @SuppressLint("MissingPermission")
    fun getPairedDeviceList(): List<BluetoothDevice>{
        checkLicense();
        val bondedDevices = bluetoothAdapter.bondedDevices
        val connectedDevices = arrayListOf<BluetoothDevice>()
        for (profile in arrayOf(BluetoothProfile.GATT, BluetoothProfile.GATT_SERVER)) {
            connectedDevices.addAll(bluetoothManager.getConnectedDevices(profile))
        }

        bondedDevices?.map { bonded ->
            deviceToJson(bonded,
                connectedDevices.any { connected -> connected.address == bonded.address }
            )
        }
        return connectedDevices
    }

    @SuppressLint("MissingPermission")
    private fun deviceToJson(device: BluetoothDevice, connected: Boolean = false) = mapOf(
        "name" to device.name,
        "uuid" to device.address,
        "isConnected" to connected
    )

    @SuppressLint("MissingPermission")
    fun startScanning(serviceUUID: String) {
        if (bluetoothAdapter.isDiscovering) {
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

            ContextWrapper(context).registerReceiver(
                scanningReceiver,
                IntentFilter(action)
            )
        }
        bluetoothAdapter.startDiscovery()
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
            connections.remove(uuid)
            connectedUUID = null
            eventSink.success(
                mapOf(
                    "type" to "connection",
                    "data" to mapOf(
                        "event" to "disconnected",
                        "device" to deviceToJson(devices[uuid]!!)
                    )
                )
            )
            return
        }
    }

    fun writeData(uuid: String, data: ByteArray) {
        checkLicense();
        connections[uuid]?.write(data)
    }

    fun registerReceiver() {
        val filter = IntentFilter()
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
                eventSink.success(
                    mapOf(
                        "type" to "state",
                        "data" to intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)
                    )
                )
            }
            BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                //Log.d(TAG, "Bluetooth Discovery started")

                eventSink.success(
                    mapOf(
                        "type" to "scanningState",
                        "data" to true
                    )
                )
            }
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                //Log.d(TAG, "Bluetooth Discovery finished")
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

//                    Log.d(
//                        TAG,
//                        "Device Connected: ${deviceName ?: "noname"} [$deviceHardwareAddress]"
//                    )
                }
            }
            BluetoothDevice.ACTION_FOUND -> {
                (intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice)?.let { device ->
                    val deviceName = device.name ?: intent.getParcelableExtra(BluetoothDevice.EXTRA_NAME)
                    val deviceHardwareAddress = device.address // MAC address

                    if (serviceUUID == null || device.uuids == null || device.uuids!!.any { it.uuid.toString() == serviceUUID }) {
                        if (deviceName == null) return
                        if (devices[deviceHardwareAddress] != null) return

                        devices[deviceHardwareAddress] = device

                        //Log.d(TAG, "Device Found: $deviceName")

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

        return try {
            connection.start()
            connectedUUID = device.address
            true
        } catch (exception: Exception) {
            connectedUUID = null
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
}
