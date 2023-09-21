//package com.afex.mapx
//
//import android.annotation.SuppressLint
//import android.annotation.TargetApi
//import android.app.Activity
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.BluetoothDevice
//import android.bluetooth.BluetoothGatt
//import android.bluetooth.BluetoothGattCallback
//import android.bluetooth.BluetoothGattCharacteristic
//import android.bluetooth.BluetoothGattDescriptor
//import android.bluetooth.BluetoothGattService
//import android.bluetooth.BluetoothManager
//import android.bluetooth.BluetoothProfile
//import android.bluetooth.BluetoothStatusCodes
//import android.bluetooth.le.ScanCallback
//import android.bluetooth.le.ScanRecord
//import android.content.BroadcastReceiver
//import android.content.Context
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.Looper
//import android.os.ParcelUuid
//import android.util.Log
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import java.util.Locale
//import java.util.UUID
//import java.util.concurrent.ConcurrentHashMap
//
//
//private const val TAG = "MapXServer"
//
//class MapXServer (private val activity: Activity, private val context: Context, private val eventSink: EventEmitter) {
//
//    private val mBluetoothManager: BluetoothManager? = null
//    private val mBluetoothAdapter: BluetoothAdapter? = null
//
//    private val mConnectedDevices: Map<String, BluetoothGatt> = ConcurrentHashMap()
//    private val mMtu: Map<String, Int> = ConcurrentHashMap()
//    private val mAutoConnect: Map<String, Boolean> = ConcurrentHashMap()
//
//    private var lastEventId = 1452
//    private val operationsOnPermission: Map<Int, OperationOnPermission> = HashMap()
//
//    private val enableBluetoothRequestCode = 1879842617
//
//    private interface OperationOnPermission {
//        fun op(granted: Boolean, permission: String?)
//    }
//
//    fun uuid128(obj: UUID): String? {
//        val uuid = obj.toString()
//        return if (uuid.length == 4) {
//            // 16-bit uuid
//            String.format("0000%s-0000-1000-8000-00805F9B34FB", uuid).lowercase(Locale.getDefault())
//        } else if (uuid.length == 8) {
//            // 32-bit uuid
//            String.format("%s-0000-1000-8000-00805F9B34FB", uuid).lowercase(Locale.getDefault())
//        } else {
//            // 128-bit uuid
//            uuid.lowercase(Locale.getDefault())
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    fun getAdapterName(): String {
//        return if (mBluetoothAdapter != null) mBluetoothAdapter.name else "N/A"
//    }
//
//    @SuppressLint("MissingPermission")
//    fun getAdapterState(): Int{
//        return mBluetoothAdapter?.state ?: -1
//    }
//
//    @SuppressLint("MissingPermission")
//    fun turnOn(){
//    }
//
//    @SuppressLint("MissingPermission")
//    fun turnOff(){
//
//    }
//
//
//    //////////////////////////////////////////////////////////////////////////////////////
//    // ██████   ███████  ██████   ███    ███  ██  ███████  ███████  ██   ██████   ███    ██
//    // ██   ██  ██       ██   ██  ████  ████  ██  ██       ██       ██  ██    ██  ████   ██
//    // ██████   █████    ██████   ██ ████ ██  ██  ███████  ███████  ██  ██    ██  ██ ██  ██
//    // ██       ██       ██   ██  ██  ██  ██  ██       ██       ██  ██  ██    ██  ██  ██ ██
//    // ██       ███████  ██   ██  ██      ██  ██  ███████  ███████  ██   ██████   ██   ████
//
//    //////////////////////////////////////////////////////////////////////////////////////
//    // ██████   ███████  ██████   ███    ███  ██  ███████  ███████  ██   ██████   ███    ██
//    // ██   ██  ██       ██   ██  ████  ████  ██  ██       ██       ██  ██    ██  ████   ██
//    // ██████   █████    ██████   ██ ████ ██  ██  ███████  ███████  ██  ██    ██  ██ ██  ██
//    // ██       ██       ██   ██  ██  ██  ██  ██       ██       ██  ██  ██    ██  ██  ██ ██
//    // ██       ███████  ██   ██  ██      ██  ██  ███████  ███████  ██   ██████   ██   ████
//    fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String?>,
//        grantResults: IntArray
//    ): Boolean {
//        val operation = operationsOnPermission[requestCode]
//        return if (operation != null && grantResults.isNotEmpty()) {
//            operation.op(grantResults[0] == PackageManager.PERMISSION_GRANTED, permissions[0])
//            true
//        } else {
//            false
//        }
//    }
//
//    private fun ensurePermissions(permissions: List<String>, operation: OperationOnPermission) {
//        // only request permission we don't already have
//        val permissionsNeeded: MutableList<String> = ArrayList()
//        for (permission in permissions) {
//            if (ContextCompat.checkSelfPermission(context, permission)
//                != PackageManager.PERMISSION_GRANTED
//            ) {
//                permissionsNeeded.add(permission)
//            }
//        }
//
//        // no work to do?
//        if (permissionsNeeded.isEmpty()) {
//            operation.op(true, null)
//            return
//        }
//        askPermission(permissionsNeeded, operation)
//    }
//
//    private fun askPermission(permissionsNeeded: MutableList<String>, operation: OperationOnPermission) {
//        // Finished asking for permission? Call callback
//        if (permissionsNeeded.isEmpty()) {
//            operation.op(true, null)
//            return
//        }
//
//        val nextPermission = permissionsNeeded.removeAt(0)
//
//        operationsOnPermission[lastEventId] = { granted, perm ->
//            operationsOnPermission.remove(lastEventId)
//            if (!granted) {
//                operation.op(false, perm)
//                return@put
//            }
//            // Recursively ask for the next permission
//            askPermission(permissionsNeeded, operation)
//        }
//
//        ActivityCompat.requestPermissions(
//            activity,
//            arrayOf(nextPermission),
//            lastEventId
//        )
//
//        lastEventId++
//    }
//
//
//    //////////////////////////////////////////////
//    // ██████   ██       ███████
//    // ██   ██  ██       ██
//    // ██████   ██       █████
//    // ██   ██  ██       ██
//    // ██████   ███████  ███████
//    //
//    // ██    ██  ████████  ██  ██       ███████
//    // ██    ██     ██     ██  ██       ██
//    // ██    ██     ██     ██  ██       ███████
//    // ██    ██     ██     ██  ██            ██
//    //  ██████      ██     ██  ███████  ███████
//    //////////////////////////////////////////////
//
//    internal class ChrFound(var characteristic: BluetoothGattCharacteristic?, var error: String?)
//
//    private fun locateCharacteristic(
//        gatt: BluetoothGatt,
//        serviceId: String,
//        secondaryServiceId: String?,
//        characteristicId: String
//    ): ChrFound? {
//        // primary
//        val primaryService = getServiceFromArray(serviceId, gatt.services)
//            ?: return ChrFound(null, "service not found '$serviceId'")
//
//        // secondary
//        var secondaryService: BluetoothGattService? = null
//        if (!secondaryServiceId.isNullOrEmpty()) {
//            secondaryService = getServiceFromArray(serviceId, primaryService.includedServices)
//            if (secondaryService == null) {
//                return ChrFound(null, "secondaryService not found '$secondaryServiceId'")
//            }
//        }
//
//        // which service?
//        val service = secondaryService ?: primaryService
//
//        // characteristic
//        val characteristic = getCharacteristicFromArray(characteristicId, service.characteristics)
//            ?: return ChrFound(
//                null, "characteristic not found in service " +
//                        "(chr: '" + characteristicId + "' svc: '" + serviceId + "')"
//            )
//        return ChrFound(characteristic, null)
//    }
//
//    private fun getServiceFromArray(
//        uuid: String,
//        array: List<BluetoothGattService>
//    ): BluetoothGattService? {
//        for (s in array) {
//            if (uuid128(s.uuid) == uuid) {
//                return s
//            }
//        }
//        return null
//    }
//
//    private fun getCharacteristicFromArray(
//        uuid: String,
//        array: List<BluetoothGattCharacteristic>
//    ): BluetoothGattCharacteristic? {
//        for (c in array) {
//            if (uuid128(c.uuid) == uuid) {
//                return c
//            }
//        }
//        return null
//    }
//
//    private fun getDescriptorFromArray(
//        uuid: String,
//        array: List<BluetoothGattDescriptor>
//    ): BluetoothGattDescriptor? {
//        for (d in array) {
//            if (uuid128(d.uuid) == uuid) {
//                return d
//            }
//        }
//        return null
//    }
//
//    private fun getMaxPayload(remoteId: String, writeType: Int, allowLongWrite: Boolean): Int {
//        // 512 this comes from the BLE spec. Characteritics should not
//        // be longer than 512. Android also enforces this as the maximum in internal code.
//        val maxAttrLen = 512
//
//        // if no response, we can only write up to MTU-3.
//        // This is the same limitation as iOS, and ensures transfer reliability.
//        return if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE || !allowLongWrite) {
//
//            // get mtu
//            var mtu = mMtu[remoteId]
//            if (mtu == null) {
//                mtu = 23 // 23 is the minumum MTU, as per the BLE spec
//            }
//            Math.min(mtu - 3, maxAttrLen)
//        } else {
//            // if using withResponse, android will auto split up to the maxAttrLen.
//            maxAttrLen
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun disconnectAllDevices(alsoClose: Boolean) {
//        Log.d(TAG, "[FBP-Android] disconnectAllDevices")
//
//        // request disconnections
//        for (gatt in mConnectedDevices.values) {
//            val remoteId = gatt.device.address
//            Log.d(TAG, "[FBP-Android] calling disconnect: $remoteId")
//            gatt.disconnect()
//        }
//
//        // close all devices?
//        if (alsoClose) {
//            Log.d(TAG, "[FBP-Android] closeAllDevices")
//            for (gatt in mConnectedDevices.values) {
//                val remoteId = gatt.device.address
//                Log.d(TAG, "[FBP-Android] calling close: $remoteId")
//                gatt.close()
//            }
//        }
//        mConnectedDevices.clear()
//        mMtu.clear()
//    }
//
//
//    /////////////////////////////////////////////////////////////////////////////////////
//    //  █████   ██████    █████   ██████   ████████  ███████  ██████
//    // ██   ██  ██   ██  ██   ██  ██   ██     ██     ██       ██   ██
//    // ███████  ██   ██  ███████  ██████      ██     █████    ██████
//    // ██   ██  ██   ██  ██   ██  ██          ██     ██       ██   ██
//    // ██   ██  ██████   ██   ██  ██          ██     ███████  ██   ██
//    //
//    // ██████   ███████   ██████  ███████  ██  ██    ██  ███████  ██████
//    // ██   ██  ██       ██       ██       ██  ██    ██  ██       ██   ██
//    // ██████   █████    ██       █████    ██  ██    ██  █████    ██████
//    // ██   ██  ██       ██       ██       ██   ██  ██   ██       ██   ██
//    // ██   ██  ███████   ██████  ███████  ██    ████    ███████  ██   ██
//
//    /////////////////////////////////////////////////////////////////////////////////////
//    //  █████   ██████    █████   ██████   ████████  ███████  ██████
//    // ██   ██  ██   ██  ██   ██  ██   ██     ██     ██       ██   ██
//    // ███████  ██   ██  ███████  ██████      ██     █████    ██████
//    // ██   ██  ██   ██  ██   ██  ██          ██     ██       ██   ██
//    // ██   ██  ██████   ██   ██  ██          ██     ███████  ██   ██
//    //
//    // ██████   ███████   ██████  ███████  ██  ██    ██  ███████  ██████
//    // ██   ██  ██       ██       ██       ██  ██    ██  ██       ██   ██
//    // ██████   █████    ██       █████    ██  ██    ██  █████    ██████
//    // ██   ██  ██       ██       ██       ██   ██  ██   ██       ██   ██
//    // ██   ██  ███████   ██████  ███████  ██    ████    ███████  ██   ██
//    private val mBluetoothAdapterStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            val action = intent.action
//
//            // no change?
//            if (action == null || BluetoothAdapter.ACTION_STATE_CHANGED == action == false) {
//                return
//            }
//            val adapterState =
//                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
//            log(
//                LogLevel.DEBUG,
//                "[FBP-Android] OnAdapterStateChanged: " + adapterStateString(adapterState)
//            )
//
//            // disconnect all devices
//            if (adapterState == BluetoothAdapter.STATE_TURNING_OFF ||
//                adapterState == BluetoothAdapter.STATE_OFF
//            ) {
//                disconnectAllDevices(false /* alsoClose? */)
//            }
//
//            // see: BmBluetoothAdapterState
//            val map = HashMap<String, Any?>()
//            map["adapter_state"] = bmAdapterStateEnum(adapterState)
//            invokeMethodUIThread("OnAdapterStateChanged", map)
//        }
//    }
//
//    /////////////////////////////////////////////////////////////////////////////////////
//    // ██████    ██████   ███    ██  ██████
//    // ██   ██  ██    ██  ████   ██  ██   ██
//    // ██████   ██    ██  ██ ██  ██  ██   ██
//    // ██   ██  ██    ██  ██  ██ ██  ██   ██
//    // ██████    ██████   ██   ████  ██████
//    //
//    // ██████   ███████   ██████  ███████  ██  ██    ██  ███████  ██████
//    // ██   ██  ██       ██       ██       ██  ██    ██  ██       ██   ██
//    // ██████   █████    ██       █████    ██  ██    ██  █████    ██████
//    // ██   ██  ██       ██       ██       ██   ██  ██   ██       ██   ██
//    // ██   ██  ███████   ██████  ███████  ██    ████    ███████  ██   ██
//
//
//    /////////////////////////////////////////////////////////////////////////////////////
//    // ██████    ██████   ███    ██  ██████
//    // ██   ██  ██    ██  ████   ██  ██   ██
//    // ██████   ██    ██  ██ ██  ██  ██   ██
//    // ██   ██  ██    ██  ██  ██ ██  ██   ██
//    // ██████    ██████   ██   ████  ██████
//    //
//    // ██████   ███████   ██████  ███████  ██  ██    ██  ███████  ██████
//    // ██   ██  ██       ██       ██       ██  ██    ██  ██       ██   ██
//    // ██████   █████    ██       █████    ██  ██    ██  █████    ██████
//    // ██   ██  ██       ██       ██       ██   ██  ██   ██       ██   ██
//    // ██   ██  ███████   ██████  ███████  ██    ████    ███████  ██   ██
//    private val mBluetoothBondStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
//        override fun onReceive(context: Context, intent: Intent) {
//            val action = intent.action
//
//            // no change?
//            if (action == null || action == BluetoothDevice.ACTION_BOND_STATE_CHANGED == false) {
//                return
//            }
//            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
//            val cur = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
//            val prev = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
//            log(
//                LogLevel.DEBUG,
//                "[FBP-Android] OnBondStateChanged: " + bondStateString(cur) + " prev: " + bondStateString(
//                    prev
//                )
//            )
//            val remoteId = device!!.address
//            val lost = cur == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDED
//            val fail = cur == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDING
//
//            // see: BmBondStateResponse
//            val map = HashMap<String, Any?>()
//            map["remote_id"] = remoteId
//            map["bond_state"] = bmBondStateEnum(cur)
//            map["bond_failed"] =
//                cur == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDING
//            map["bond_lost"] =
//                cur == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDED
//            invokeMethodUIThread("OnBondStateChanged", map)
//        }
//    }
//
//    /////////////////////////////////////////////////////////////////////////////
//    // ███████   ██████   █████   ███    ██
//    // ██       ██       ██   ██  ████   ██
//    // ███████  ██       ███████  ██ ██  ██
//    //      ██  ██       ██   ██  ██  ██ ██
//    // ███████   ██████  ██   ██  ██   ████
//    //
//    //  ██████   █████   ██       ██       ██████    █████    ██████  ██   ██
//    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
//    // ██       ███████  ██       ██       ██████   ███████  ██       █████
//    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
//    //  ██████  ██   ██  ███████  ███████  ██████   ██   ██   ██████  ██   ██
//
//    /////////////////////////////////////////////////////////////////////////////
//    // ███████   ██████   █████   ███    ██
//    // ██       ██       ██   ██  ████   ██
//    // ███████  ██       ███████  ██ ██  ██
//    //      ██  ██       ██   ██  ██  ██ ██
//    // ███████   ██████  ██   ██  ██   ████
//    //
//    //  ██████   █████   ██       ██       ██████    █████    ██████  ██   ██
//    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
//    // ██       ███████  ██       ██       ██████   ███████  ██       █████
//    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
//    //  ██████  ██   ██  ███████  ███████  ██████   ██   ██   ██████  ██   ██
//    private var scanCallback: ScanCallback? = null
//
//    private fun getScanCallback(): ScanCallback? {
//        if (scanCallback == null) {
//            scanCallback = object : ScanCallback() {
//                override fun onScanResult(callbackType: Int, result: ScanResult) {
//                    log(LogLevel.VERBOSE, "[FBP-Android] onScanResult")
//                    super.onScanResult(callbackType, result)
//                    val device: BluetoothDevice = result.getDevice()
//
//                    // see BmScanResult
//                    val rr = bmScanResult(device, result)
//
//                    // see BmScanResponse
//                    val response = HashMap<String, Any?>()
//                    response["result"] = rr
//                    invokeMethodUIThread("OnScanResponse", response)
//                }
//
//                override fun onBatchScanResults(results: List<ScanResult?>?) {
//                    super.onBatchScanResults(results)
//                }
//
//                override fun onScanFailed(errorCode: Int) {
//                    log(
//                        LogLevel.ERROR,
//                        "[FBP-Android] onScanFailed: " + scanFailedString(errorCode)
//                    )
//                    super.onScanFailed(errorCode)
//
//                    // see: BmScanFailed
//                    val failed = HashMap<String, Any>()
//                    failed["success"] = 0
//                    failed["error_code"] = errorCode
//                    failed["error_string"] = scanFailedString(errorCode)
//
//                    // see BmScanResponse
//                    val response = HashMap<String, Any?>()
//                    response["failed"] = failed
//                    invokeMethodUIThread("OnScanResponse", response)
//                }
//            }
//        }
//        return scanCallback
//    }
//
//    /////////////////////////////////////////////////////////////////////////////
//    //  ██████    █████   ████████  ████████
//    // ██        ██   ██     ██        ██
//    // ██   ███  ███████     ██        ██
//    // ██    ██  ██   ██     ██        ██
//    //  ██████   ██   ██     ██        ██
//    //
//    //  ██████   █████   ██       ██       ██████    █████    ██████  ██   ██
//    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
//    // ██       ███████  ██       ██       ██████   ███████  ██       █████
//    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
//    //  ██████  ██   ██  ███████  ███████  ██████   ██   ██   ██████  ██   ██
//
//    /////////////////////////////////////////////////////////////////////////////
//    //  ██████    █████   ████████  ████████
//    // ██        ██   ██     ██        ██
//    // ██   ███  ███████     ██        ██
//    // ██    ██  ██   ██     ██        ██
//    //  ██████   ██   ██     ██        ██
//    //
//    //  ██████   █████   ██       ██       ██████    █████    ██████  ██   ██
//    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
//    // ██       ███████  ██       ██       ██████   ███████  ██       █████
//    // ██       ██   ██  ██       ██       ██   ██  ██   ██  ██       ██  ██
//    //  ██████  ██   ██  ███████  ███████  ██████   ██   ██   ██████  ██   ██
//    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
//        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
//            log(
//                LogLevel.DEBUG, "[FBP-Android] onConnectionStateChange: status: " + status +
//                        " (" + hciStatusString(status) + ")" +
//                        " newState: " + connectionStateString(newState)
//            )
//
//            // android never uses this callback with enums values of CONNECTING or DISCONNECTING,
//            // (theyre only used for gatt.getConnectionState()), but just to be
//            // future proof, explicitly ignore anything else. CoreBluetooth is the same way.
//            if (newState != BluetoothProfile.STATE_CONNECTED &&
//                newState != BluetoothProfile.STATE_DISCONNECTED
//            ) {
//                return
//            }
//            val remoteId = gatt.device.address
//
//            // connected?
//            if (newState == BluetoothProfile.STATE_CONNECTED) {
//                // add to connected devices
//                mConnectedDevices.put(remoteId, gatt)
//
//                // default minimum mtu
//                mMtu.put(remoteId, 23)
//            }
//
//            // disconnected?
//            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
//
//                // remove from connected devices
//                mConnectedDevices.remove(remoteId)
//
//                // we cannot call 'close' for autoconnect
//                // because it prevents autoconnect from working
//                if (mAutoConnect[remoteId] == null || mAutoConnect[remoteId] == false) {
//                    // it is important to close after disconnection, otherwise we will
//                    // quickly run out of bluetooth resources, preventing new connections
//                    gatt.close()
//                } else {
//                    log(LogLevel.DEBUG, "[FBP-Android] autoconnect is true. skipping gatt.close()")
//                }
//            }
//
//            // see: BmConnectionStateResponse
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = remoteId
//            response["connection_state"] = bmConnectionStateEnum(newState)
//            response["disconnect_reason_code"] = status
//            response["disconnect_reason_string"] = hciStatusString(status)
//            invokeMethodUIThread("OnConnectionStateChanged", response)
//        }
//
//        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
//            log(
//                LogLevel.DEBUG,
//                "[FBP-Android] onServicesDiscovered: count: " + gatt.services.size + " status: " + status
//            )
//            val services: MutableList<Any> = ArrayList()
//            for (s in gatt.services) {
//                services.add(bmBluetoothService(gatt.device, s, gatt))
//            }
//
//            // see: BmDiscoverServicesResult
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = gatt.device.address
//            response["services"] = services
//            response["success"] = if (status == BluetoothGatt.GATT_SUCCESS) 1 else 0
//            response["error_code"] = status
//            response["error_string"] = gattErrorString(status)
//            invokeMethodUIThread("OnDiscoverServicesResult", response)
//        }
//
//        override fun onCharacteristicChanged(
//            gatt: BluetoothGatt,
//            characteristic: BluetoothGattCharacteristic
//        ) {
//            // this callback is only for notifications & indications
//            log(
//                LogLevel.DEBUG,
//                "[FBP-Android] onCharacteristicChanged: uuid: " + uuid128(characteristic.uuid)
//            )
//            val pair = getServicePair(gatt, characteristic)
//
//            // see: BmOnCharacteristicReceived
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = gatt.device.address
//            response["service_uuid"] = uuid128(pair.primary!!)
//            response["secondary_service_uuid"] =
//                if (pair.secondary != null) uuid128(pair.secondary!!) else null
//            response["characteristic_uuid"] = uuid128(characteristic.uuid)
//            response["value"] = bytesToHex(characteristic.value)
//            response["success"] = 1
//            response["error_code"] = 0
//            response["error_string"] = gattErrorString(0)
//            invokeMethodUIThread("OnCharacteristicReceived", response)
//        }
//
//        override fun onCharacteristicRead(
//            gatt: BluetoothGatt,
//            characteristic: BluetoothGattCharacteristic,
//            status: Int
//        ) {
//            // this callback is only for explicit characteristic reads
//            log(
//                LogLevel.DEBUG,
//                "[FBP-Android] onCharacteristicRead: uuid: " + uuid128(characteristic.uuid) + " status: " + status
//            )
//            val pair = getServicePair(gatt, characteristic)
//
//            // see: BmOnCharacteristicReceived
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = gatt.device.address
//            response["service_uuid"] = uuid128(pair.primary!!)
//            response["secondary_service_uuid"] =
//                if (pair.secondary != null) uuid128(pair.secondary!!) else null
//            response["characteristic_uuid"] = uuid128(characteristic.uuid)
//            response["value"] = bytesToHex(characteristic.value)
//            response["success"] = if (status == BluetoothGatt.GATT_SUCCESS) 1 else 0
//            response["error_code"] = status
//            response["error_string"] = gattErrorString(status)
//            invokeMethodUIThread("OnCharacteristicReceived", response)
//        }
//
//        override fun onCharacteristicWrite(
//            gatt: BluetoothGatt,
//            characteristic: BluetoothGattCharacteristic,
//            status: Int
//        ) {
//            log(
//                LogLevel.DEBUG,
//                "[FBP-Android] onCharacteristicWrite: uuid: " + uuid128(characteristic.uuid) + " status: " + status
//            )
//
//            // For "writeWithResponse", onCharacteristicWrite is called after the remote sends back a write response.
//            // For "writeWithoutResponse", onCharacteristicWrite is called as long as there is still space left
//            // in android's internal buffer. When the buffer is full, it delays calling onCharacteristicWrite
//            // until there is at least ~50% free space again.
//            val pair = getServicePair(gatt, characteristic)
//
//            // see: BmOnCharacteristicWritten
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = gatt.device.address
//            response["service_uuid"] = uuid128(pair.primary!!)
//            response["secondary_service_uuid"] =
//                if (pair.secondary != null) uuid128(pair.secondary!!) else null
//            response["characteristic_uuid"] = uuid128(characteristic.uuid)
//            response["success"] = if (status == BluetoothGatt.GATT_SUCCESS) 1 else 0
//            response["error_code"] = status
//            response["error_string"] = gattErrorString(status)
//            invokeMethodUIThread("OnCharacteristicWritten", response)
//        }
//
//        @TargetApi(33)
//        override fun onDescriptorRead(
//            gatt: BluetoothGatt,
//            descriptor: BluetoothGattDescriptor,
//            status: Int,
//            value: ByteArray
//        ) {
//            log(
//                LogLevel.DEBUG,
//                "[FBP-Android] onDescriptorRead: uuid: " + uuid128(descriptor.uuid) + " status: " + status
//            )
//            val pair = getServicePair(gatt, descriptor.characteristic)
//
//            // see: BmOnDescriptorRead
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = gatt.device.address
//            response["service_uuid"] = uuid128(pair.primary!!)
//            response["secondary_service_uuid"] =
//                if (pair.secondary != null) uuid128(pair.secondary!!) else null
//            response["characteristic_uuid"] = uuid128(descriptor.characteristic.uuid)
//            response["descriptor_uuid"] = uuid128(descriptor.uuid)
//            response["value"] = bytesToHex(value)
//            response["success"] = if (status == BluetoothGatt.GATT_SUCCESS) 1 else 0
//            response["error_code"] = status
//            response["error_string"] = gattErrorString(status)
//            invokeMethodUIThread("OnDescriptorRead", response)
//        }
//
//        override fun onDescriptorRead(
//            gatt: BluetoothGatt,
//            descriptor: BluetoothGattDescriptor,
//            status: Int
//        ) {
//            log(
//                LogLevel.DEBUG,
//                "[FBP-Android] onDescriptorRead: uuid: " + uuid128(descriptor.uuid) + " status: " + status
//            )
//            val pair = getServicePair(gatt, descriptor.characteristic)
//
//            // this was deprecated in API level 33 because the api makes it look like
//            // you could always call getValue on a descriptor. But in reality, this
//            // only works after a *read* has been made, not a *write*.
//            val value = descriptor.value
//
//            // see: BmOnDescriptorRead
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = gatt.device.address
//            response["service_uuid"] = uuid128(pair.primary!!)
//            response["secondary_service_uuid"] =
//                if (pair.secondary != null) uuid128(pair.secondary!!) else null
//            response["characteristic_uuid"] = uuid128(descriptor.characteristic.uuid)
//            response["descriptor_uuid"] = uuid128(descriptor.uuid)
//            response["value"] = bytesToHex(value)
//            response["success"] = if (status == BluetoothGatt.GATT_SUCCESS) 1 else 0
//            response["error_code"] = status
//            response["error_string"] = gattErrorString(status)
//            invokeMethodUIThread("OnDescriptorRead", response)
//        }
//
//        override fun onDescriptorWrite(
//            gatt: BluetoothGatt,
//            descriptor: BluetoothGattDescriptor,
//            status: Int
//        ) {
//            log(
//                LogLevel.DEBUG,
//                "[FBP-Android] onDescriptorWrite: uuid: " + uuid128(descriptor.uuid) + " status: " + status
//            )
//            val pair = getServicePair(gatt, descriptor.characteristic)
//
//            // see: BmOnDescriptorWrite
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = gatt.device.address
//            response["service_uuid"] = uuid128(pair.primary!!)
//            response["secondary_service_uuid"] =
//                if (pair.secondary != null) uuid128(pair.secondary!!) else null
//            response["characteristic_uuid"] = uuid128(descriptor.characteristic.uuid)
//            response["descriptor_uuid"] = uuid128(descriptor.uuid)
//            response["success"] = if (status == BluetoothGatt.GATT_SUCCESS) 1 else 0
//            response["error_code"] = status
//            response["error_string"] = gattErrorString(status)
//            invokeMethodUIThread("OnDescriptorWrite", response)
//        }
//
//        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
//            log(LogLevel.DEBUG, "[FBP-Android] onReliableWriteCompleted: status: $status")
//        }
//
//        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
//            log(LogLevel.DEBUG, "[FBP-Android] onReadRemoteRssi: rssi: $rssi status: $status")
//
//            // see: BmReadRssiResult
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = gatt.device.address
//            response["rssi"] = rssi
//            response["success"] = if (status == BluetoothGatt.GATT_SUCCESS) 1 else 0
//            response["error_code"] = status
//            response["error_string"] = gattErrorString(status)
//            invokeMethodUIThread("OnReadRssiResult", response)
//        }
//
//        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
//            log(LogLevel.DEBUG, "[FBP-Android] onMtuChanged: mtu: $mtu status: $status")
//            val remoteId = gatt.device.address
//
//            // remember mtu
//            mMtu.put(remoteId, mtu)
//
//            // see: BmMtuChangedResponse
//            val response = HashMap<String, Any?>()
//            response["remote_id"] = remoteId
//            response["mtu"] = mtu
//            response["success"] = if (status == BluetoothGatt.GATT_SUCCESS) 1 else 0
//            response["error_code"] = status
//            response["error_string"] = gattErrorString(status)
//            invokeMethodUIThread("OnMtuChanged", response)
//        }
//    } // BluetoothGattCallback
//
//
//    //////////////////////////////////////////////////////////////////////
//    // ███    ███  ███████   ██████
//    // ████  ████  ██       ██
//    // ██ ████ ██  ███████  ██   ███
//    // ██  ██  ██       ██  ██    ██
//    // ██      ██  ███████   ██████
//    //
//    // ██   ██  ███████  ██       ██████   ███████  ██████   ███████
//    // ██   ██  ██       ██       ██   ██  ██       ██   ██  ██
//    // ███████  █████    ██       ██████   █████    ██████   ███████
//    // ██   ██  ██       ██       ██       ██       ██   ██       ██
//    // ██   ██  ███████  ███████  ██       ███████  ██   ██  ███████
//
//    //////////////////////////////////////////////////////////////////////
//    // ███    ███  ███████   ██████
//    // ████  ████  ██       ██
//    // ██ ████ ██  ███████  ██   ███
//    // ██  ██  ██       ██  ██    ██
//    // ██      ██  ███████   ██████
//    //
//    // ██   ██  ███████  ██       ██████   ███████  ██████   ███████
//    // ██   ██  ██       ██       ██   ██  ██       ██   ██  ██
//    // ███████  █████    ██       ██████   █████    ██████   ███████
//    // ██   ██  ██       ██       ██       ██       ██   ██       ██
//    // ██   ██  ███████  ███████  ██       ███████  ██   ██  ███████
//    fun bmAdvertisementData(result: ScanResult): HashMap<String, Any?> {
//        val min = Int.MIN_VALUE
//        val adv: ScanRecord = result.getScanRecord()
//        val localName = if (adv != null) adv.deviceName else null
//        val connectable = if (adv != null) adv.advertiseFlags and 0x2 > 0 else false
//        val txPower = adv?.txPowerLevel ?: min
//        val manufData = if (adv != null) adv.manufacturerSpecificData else null
//        val serviceUuids = if (adv != null) adv.serviceUuids else null
//        val serviceData = if (adv != null) adv.serviceData else null
//
//        // Manufacturer Specific Data
//        val manufDataB = HashMap<Int, String?>()
//        if (manufData != null) {
//            for (i in 0 until manufData.size()) {
//                val key = manufData.keyAt(i)
//                val value = manufData.valueAt(i)
//                manufDataB[key] = bytesToHex(value)
//            }
//        }
//
//        // Service Data
//        val serviceDataB = HashMap<String?, Any?>()
//        if (serviceData != null) {
//            for ((key, value): Map.Entry<ParcelUuid, ByteArray> in serviceData) {
//                serviceDataB[uuid128(key.uuid)] = bytesToHex(value)
//            }
//        }
//
//        // Service UUIDs
//        val serviceUuidsB: MutableList<String?> = ArrayList()
//        if (serviceUuids != null) {
//            for (s in serviceUuids) {
//                serviceUuidsB.add(uuid128(s.uuid))
//            }
//        }
//        val map = HashMap<String, Any?>()
//        map["local_name"] = localName
//        map["connectable"] = connectable
//        map["tx_power_level"] = if (txPower != min) txPower else null
//        map["manufacturer_data"] = if (manufData != null) manufDataB else null
//        map["service_data"] = if (serviceData != null) serviceDataB else null
//        map["service_uuids"] = if (serviceUuids != null) serviceUuidsB else null
//        return map
//    }
//
//    fun bmScanResult(device: BluetoothDevice, result: ScanResult): HashMap<String, Any> {
//        val map = HashMap<String, Any>()
//        map["device"] = bmBluetoothDevice(device)
//        map["rssi"] = result.getRssi()
//        map["advertisement_data"] = bmAdvertisementData(result)
//        return map
//    }
//
//    fun bmBluetoothDevice(device: BluetoothDevice): HashMap<String, Any> {
//        val map = HashMap<String, Any>()
//        map["remote_id"] = device.address
//        if (device.name != null) {
//            map["local_name"] = device.name
//        }
//        map["type"] = device.type
//        return map
//    }
//
//    fun bmBluetoothService(
//        device: BluetoothDevice,
//        service: BluetoothGattService,
//        gatt: BluetoothGatt
//    ): HashMap<String, Any?> {
//        val characteristics: MutableList<Any> = ArrayList()
//        for (c in service.characteristics) {
//            characteristics.add(bmBluetoothCharacteristic(device, c, gatt))
//        }
//        val includedServices: MutableList<Any> = ArrayList()
//        for (included in service.includedServices) {
//            // service includes itself?
//            if (included.uuid == service.uuid) {
//                continue  // skip, infinite recursion
//            }
//            includedServices.add(bmBluetoothService(device, included, gatt))
//        }
//        val map = HashMap<String, Any?>()
//        map["remote_id"] = device.address
//        map["service_uuid"] = uuid128(service.uuid)
//        map["is_primary"] = if (service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY) 1 else 0
//        map["characteristics"] = characteristics
//        map["included_services"] = includedServices
//        return map
//    }
//
//    fun bmBluetoothCharacteristic(
//        device: BluetoothDevice,
//        characteristic: BluetoothGattCharacteristic,
//        gatt: BluetoothGatt
//    ): HashMap<String, Any?> {
//        val pair = getServicePair(gatt, characteristic)
//        val descriptors: MutableList<Any> = ArrayList()
//        for (d in characteristic.descriptors) {
//            descriptors.add(bmBluetoothDescriptor(device, d))
//        }
//        val map = HashMap<String, Any?>()
//        map["remote_id"] = device.address
//        map["service_uuid"] = uuid128(pair.primary!!)
//        map["secondary_service_uuid"] =
//            if (pair.secondary != null) uuid128(pair.secondary!!) else null
//        map["characteristic_uuid"] = uuid128(characteristic.uuid)
//        map["descriptors"] = descriptors
//        map["properties"] = bmCharacteristicProperties(characteristic.properties)
//        return map
//    }
//
//    fun bmBluetoothDescriptor(
//        device: BluetoothDevice,
//        descriptor: BluetoothGattDescriptor
//    ): HashMap<String, Any?> {
//        val map = HashMap<String, Any?>()
//        map["remote_id"] = device.address
//        map["descriptor_uuid"] = uuid128(descriptor.uuid)
//        map["characteristic_uuid"] = uuid128(descriptor.characteristic.uuid)
//        map["service_uuid"] = uuid128(descriptor.characteristic.service.uuid)
//        return map
//    }
//
//    fun bmCharacteristicProperties(properties: Int): HashMap<String, Any>? {
//        val props = HashMap<String, Any>()
//        props["broadcast"] = if (properties and 1 != 0) 1 else 0
//        props["read"] = if (properties and 2 != 0) 1 else 0
//        props["write_without_response"] = if (properties and 4 != 0) 1 else 0
//        props["write"] = if (properties and 8 != 0) 1 else 0
//        props["notify"] = if (properties and 16 != 0) 1 else 0
//        props["indicate"] = if (properties and 32 != 0) 1 else 0
//        props["authenticated_signed_writes"] = if (properties and 64 != 0) 1 else 0
//        props["extended_properties"] = if (properties and 128 != 0) 1 else 0
//        props["notify_encryption_required"] = if (properties and 256 != 0) 1 else 0
//        props["indicate_encryption_required"] = if (properties and 512 != 0) 1 else 0
//        return props
//    }
//
//    fun bmConnectionStateEnum(cs: Int): Int {
//        return when (cs) {
//            BluetoothProfile.STATE_DISCONNECTED -> 0
//            BluetoothProfile.STATE_CONNECTED -> 1
//            else -> 0
//        }
//    }
//
//    fun bmAdapterStateEnum(`as`: Int): Int {
//        return when (`as`) {
//            BluetoothAdapter.STATE_OFF -> 6
//            BluetoothAdapter.STATE_ON -> 4
//            BluetoothAdapter.STATE_TURNING_OFF -> 5
//            BluetoothAdapter.STATE_TURNING_ON -> 3
//            else -> 0
//        }
//    }
//
//    fun bmBondStateEnum(bs: Int): Int {
//        return when (bs) {
//            BluetoothDevice.BOND_NONE -> 0
//            BluetoothDevice.BOND_BONDING -> 1
//            BluetoothDevice.BOND_BONDED -> 2
//            else -> 0
//        }
//    }
//
//    fun bmConnectionPriorityParse(value: Int): Int {
//        return when (value) {
//            0 -> BluetoothGatt.CONNECTION_PRIORITY_BALANCED
//            1 -> BluetoothGatt.CONNECTION_PRIORITY_HIGH
//            2 -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
//            else -> BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
//        }
//    }
//
//    class ServicePair {
//        var primary: UUID? = null
//        var secondary: UUID? = null
//    }
//
//    fun getServicePair(
//        gatt: BluetoothGatt,
//        characteristic: BluetoothGattCharacteristic
//    ): ServicePair {
//        val result = ServicePair()
//        val service = characteristic.service
//
//        // is this a primary service?
//        if (service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY) {
//            result.primary = service.uuid
//            return result
//        }
//
//        // Otherwise, iterate all services until we find the primary service
//        for (primary in gatt.services) {
//            for (secondary in primary.includedServices) {
//                if (secondary.uuid == service.uuid) {
//                    result.primary = primary.uuid
//                    result.secondary = secondary.uuid
//                    return result
//                }
//            }
//        }
//        return result
//    }
//
//    //////////////////////////////////////////
//    // ██    ██ ████████  ██  ██       ███████
//    // ██    ██    ██     ██  ██       ██
//    // ██    ██    ██     ██  ██       ███████
//    // ██    ██    ██     ██  ██            ██
//    //  ██████     ██     ██  ███████  ███████
//
//    //////////////////////////////////////////
//    // ██    ██ ████████  ██  ██       ███████
//    // ██    ██    ██     ██  ██       ██
//    // ██    ██    ██     ██  ██       ███████
//    // ██    ██    ██     ██  ██            ██
//    //  ██████     ██     ██  ███████  ███████
//    private fun log(level: LogLevel, message: String) {
//        if (level.ordinal <= logLevel.ordinal()) {
//            Log.d(TAG, message)
//        }
//    }
//
//    private fun invokeMethodUIThread(method: String, data: HashMap<String, Any?>) {
//        Handler(Looper.getMainLooper()).post {
//            //Could already be teared down at this moment
//            if (methodChannel != null) {
//                methodChannel.invokeMethod(method, data)
//            } else {
//                Log.w(
//                    TAG,
//                    "invokeMethodUIThread: tried to call method on closed channel: $method"
//                )
//            }
//        }
//    }
//
//    private fun hexToBytes(s: String?): ByteArray? {
//        if (s == null) {
//            return ByteArray(0)
//        }
//        val len = s.length
//        val data = ByteArray(len / 2)
//        var i = 0
//        while (i < len) {
//            data[i / 2] = ((s[i].digitToIntOrNull(16) ?: -1 shl 4)
//            + s[i + 1].digitToIntOrNull(16)!! ?: -1).toByte()
//            i += 2
//        }
//        return data
//    }
//
//    private fun bytesToHex(bytes: ByteArray?): String? {
//        if (bytes == null) {
//            return ""
//        }
//        val sb = StringBuilder()
//        for (b in bytes) {
//            sb.append(String.format("%02x", b))
//        }
//        return sb.toString()
//    }
//
//    private fun connectionStateString(cs: Int): String {
//        return when (cs) {
//            BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
//            BluetoothProfile.STATE_CONNECTING -> "connecting"
//            BluetoothProfile.STATE_CONNECTED -> "connected"
//            BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
//            else -> "UNKNOWN_CONNECTION_STATE ($cs)"
//        }
//    }
//
//    private fun adapterStateString(`as`: Int): String {
//        return when (`as`) {
//            BluetoothAdapter.STATE_OFF -> "off"
//            BluetoothAdapter.STATE_ON -> "on"
//            BluetoothAdapter.STATE_TURNING_OFF -> "turningOff"
//            BluetoothAdapter.STATE_TURNING_ON -> "turningOn"
//            else -> "UNKNOWN_ADAPTER_STATE ($`as`)"
//        }
//    }
//
//    private fun bondStateString(bs: Int): String {
//        return when (bs) {
//            BluetoothDevice.BOND_BONDING -> "bonding"
//            BluetoothDevice.BOND_BONDED -> "bonded"
//            BluetoothDevice.BOND_NONE -> "bond-none"
//            else -> "UNKNOWN_BOND_STATE ($bs)"
//        }
//    }
//
//    private fun gattErrorString(value: Int): String? {
//        return when (value) {
//            BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
//            BluetoothGatt.GATT_CONNECTION_CONGESTED -> "GATT_CONNECTION_CONGESTED"
//            BluetoothGatt.GATT_FAILURE -> "GATT_FAILURE"
//            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION"
//            BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION -> "GATT_INSUFFICIENT_AUTHORIZATION"
//            BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION"
//            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE_LENGTH"
//            BluetoothGatt.GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET"
//            BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
//            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
//            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
//            else -> "UNKNOWN_GATT_ERROR ($value)"
//        }
//    }
//
//    private fun bluetoothStatusString(value: Int): String? {
//        return when (value) {
//            BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED -> "ERROR_BLUETOOTH_NOT_ALLOWED"
//            BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED -> "ERROR_BLUETOOTH_NOT_ENABLED"
//            BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED -> "ERROR_DEVICE_NOT_BONDED"
//            BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED -> "ERROR_GATT_WRITE_NOT_ALLOWED"
//            BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> "ERROR_GATT_WRITE_REQUEST_BUSY"
//            BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION -> "ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION"
//            BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND -> "ERROR_PROFILE_SERVICE_NOT_BOUND"
//            BluetoothStatusCodes.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
//            BluetoothStatusCodes.FEATURE_NOT_SUPPORTED -> "FEATURE_NOT_SUPPORTED"
//            BluetoothStatusCodes.FEATURE_SUPPORTED -> "FEATURE_SUPPORTED"
//            BluetoothStatusCodes.SUCCESS -> "SUCCESS"
//            else -> "UNKNOWN_BLE_ERROR ($value)"
//        }
//    }
//
//    private fun scanFailedString(value: Int): String {
//        return when (value) {
//            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED"
//            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"
//            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_FEATURE_UNSUPPORTED"
//            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_INTERNAL_ERROR"
//            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES"
//            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "SCAN_FAILED_SCANNING_TOO_FREQUENTLY"
//            else -> "UNKNOWN_SCAN_ERROR ($value)"
//        }
//    }
//
//
//    // Defined in the Bluetooth Standard, Volume 1, Part F, 1.3 HCI Error Code, pages 364-377.
//    // See https://www.bluetooth.org/docman/handlers/downloaddoc.ashx?doc_id=478726,
//    private fun hciStatusString(value: Int): String {
//        return when (value) {
//            0x00 -> "SUCCESS"
//            0x01 -> "UNKNOWN_COMMAND" // The controller does not understand the HCI Command Packet OpCode that the Host sent.
//            0x02 -> "UNKNOWN_CONNECTION_IDENTIFIER" // The connection identifier used is unknown
//            0x03 -> "HARDWARE_FAILURE" // A hardware failure has occurred
//            0x04 -> "PAGE_TIMEOUT" // a page timed out because of the Page Timeout configuration parameter.
//            0x05 -> "AUTHENTICATION_FAILURE" // Pairing or authentication failed. This could be due to an incorrect PIN or Link Key.
//            0x06 -> "PIN_OR_KEY_MISSING" // Pairing failed because of a missing PIN
//            0x07 -> "MEMORY_FULL" // The Controller has run out of memory to store new parameters.
//            0x08 -> "CONNECTION_TIMEOUT" // The link supervision timeout has expired for a given connection.
//            0x09 -> "CONNECTION_LIMIT_EXCEEDED" // The Controller is already at its limit of the number of connections it can support.
//            0x0A -> "MAX_NUM_OF_CONNECTIONS_EXCEEDED" // The Controller has reached the limit of connections
//            0x0B -> "CONNECTION_ALREADY_EXISTS" // A connection to this device already exists
//            0x0C -> "COMMAND_DISALLOWED" // The command requested cannot be executed by the Controller at this time.
//            0x0D -> "CONNECTION_REJECTED_LIMITED_RESOURCES" // A connection was rejected due to limited resources.
//            0x0E -> "CONNECTION_REJECTED_SECURITY_REASONS" // A connection was rejected due to security, e.g. aauth or pairing.
//            0x0F -> "CONNECTION_REJECTED_UNACCEPTABLE_MAC_ADDRESS" // connection rejected, this device does not accept the BD_ADDR
//            0x10 -> "CONNECTION_ACCEPT_TIMEOUT_EXCEEDED" // Connection Accept Timeout exceeded for this connection attempt.
//            0x11 -> "UNSUPPORTED_PARAMETER_VALUE" // A feature or parameter value in the HCI command is not supported.
//            0x12 -> "INVALID_COMMAND_PARAMETERS" // At least one of the HCI command parameters is invalid.
//            0x13 -> "REMOTE_USER_TERMINATED_CONNECTION" // The user on the remote device terminated the connection.
//            0x14 -> "REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES" // remote device terminated connection due to low resources.
//            0x15 -> "REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF" // The remote device terminated the connection due to power off
//            0x16 -> "CONNECTION_TERMINATED_BY_LOCAL_HOST" // The local device terminated the connection.
//            0x17 -> "REPEATED_ATTEMPTS" // The Controller is disallowing auth because of too quick attempts.
//            0x18 -> "PAIRING_NOT_ALLOWED" // The device does not allow pairing
//            0x19 -> "UNKNOWN_LMP_PDU" // The Controller has received an unknown LMP OpCode.
//            0x1A -> "UNSUPPORTED_REMOTE_FEATURE" // The remote device does not support feature for the issued command or LMP PDU.
//            0x1B -> "SCO_OFFSET_REJECTED" // The offset requested in the LMP_SCO_link_req PDU has been rejected.
//            0x1C -> "SCO_INTERVAL_REJECTED" // The interval requested in the LMP_SCO_link_req PDU has been rejected.
//            0x1D -> "SCO_AIR_MODE_REJECTED" // The air mode requested in the LMP_SCO_link_req PDU has been rejected.
//            0x1E -> "INVALID_LMP_OR_LL_PARAMETERS" // Some LMP PDU / LL Control PDU parameters were invalid.
//            0x1F -> "UNSPECIFIED" // No other error code specified is appropriate to use
//            0x20 -> "UNSUPPORTED_LMP_OR_LL_PARAMETER_VALUE" // An LMP PDU or an LL Control PDU contains a value that is not supported
//            0x21 -> "ROLE_CHANGE_NOT_ALLOWED" // a Controller will not allow a role change at this time.
//            0x22 -> "LMP_OR_LL_RESPONSE_TIMEOUT" // An LMP transaction failed to respond within the LMP response timeout
//            0x23 -> "LMP_OR_LL_ERROR_TRANS_COLLISION" // An LMP transaction or LL procedure has collided with the same transaction
//            0x24 -> "LMP_PDU_NOT_ALLOWED" // A Controller sent an LMP PDU with an OpCode that was not allowed.
//            0x25 -> "ENCRYPTION_MODE_NOT_ACCEPTABLE" // The requested encryption mode is not acceptable at this time.
//            0x26 -> "LINK_KEY_CANNOT_BE_EXCHANGED" // A link key cannot be changed because a fixed unit key is being used.
//            0x27 -> "REQUESTED_QOS_NOT_SUPPORTED" // The requested Quality of Service is not supported.
//            0x28 -> "INSTANT_PASSED" // The LMP PDU or LL PDU instant has already passed
//            0x29 -> "PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED" // It was not possible to pair as a unit key is not supported.
//            0x2A -> "DIFFERENT_TRANSACTION_COLLISION" // An LMP transaction or LL Procedure collides with an ongoing transaction.
//            0x2B -> "UNDEFINED_0x2B" // Undefined error code
//            0x2C -> "QOS_UNACCEPTABLE_PARAMETER" // The quality of service parameters could not be accepted at this time.
//            0x2D -> "QOS_REJECTED" // The specified quality of service parameters cannot be accepted. negotiation should be terminated
//            0x2E -> "CHANNEL_CLASSIFICATION_NOT_SUPPORTED" // The Controller cannot perform channel assessment. not supported.
//            0x2F -> "INSUFFICIENT_SECURITY" // The HCI command or LMP PDU sent is only possible on an encrypted link.
//            0x30 -> "PARAMETER_OUT_OF_RANGE" // A parameter in the HCI command is outside of valid range
//            0x31 -> "UNDEFINED_0x31" // Undefined error
//            0x32 -> "ROLE_SWITCH_PENDING" // A Role Switch is pending, sothe HCI command or LMP PDU is rejected
//            0x33 -> "UNDEFINED_0x33" // Undefined error
//            0x34 -> "RESERVED_SLOT_VIOLATION" // Synchronous negotiation terminated with negotiation state set to Reserved Slot Violation.
//            0x35 -> "ROLE_SWITCH_FAILED" // A role switch was attempted but it failed and the original piconet structure is restored.
//            0x36 -> "INQUIRY_RESPONSE_TOO_LARGE" // The extended inquiry response is too large to fit in packet supported by Controller.
//            0x37 -> "SECURE_SIMPLE_PAIRING_NOT_SUPPORTED" // Host does not support Secure Simple Pairing, but receiving Link Manager does.
//            0x38 -> "HOST_BUSY_PAIRING" // The Host is busy with another pairing operation. The receiving device should retry later.
//            0x39 -> "CONNECTION_REJECTED_NO_SUITABLE_CHANNEL" // Controller could not calculate an appropriate value for Channel selection.
//            0x3A -> "CONTROLLER_BUSY" // The Controller was busy and unable to process the request.
//            0x3B -> "UNACCEPTABLE_CONNECTION_PARAMETERS" // The remote device terminated connection, unacceptable connection parameters.
//            0x3C -> "ADVERTISING_TIMEOUT" // Advertising completed. Or for directed advertising, no connection was created.
//            0x3D -> "CONNECTION_TERMINATED_MIC_FAILURE" // Connection terminated because Message Integrity Check failed on received packet.
//            0x3E -> "CONNECTION_FAILED_ESTABLISHMENT" // The LL initiated a connection but the connection has failed to be established.
//            0x3F -> "MAC_CONNECTION_FAILED" // The MAC of the 802.11 AMP was requested to connect to a peer, but the connection failed.
//            0x40 -> "COARSE_CLOCK_ADJUSTMENT_REJECTED" // The master is unable to make a coarse adjustment to the piconet clock.
//            0x41 -> "TYPE0_SUBMAP_NOT_DEFINED" // The LMP PDU is rejected because the Type 0 submap is not currently defined.
//            0x42 -> "UNKNOWN_ADVERTISING_IDENTIFIER" // A command was sent from the Host but the Advertising or Sync handle does not exist.
//            0x43 -> "LIMIT_REACHED" // The number of operations requested has been reached and has indicated the completion of the activity
//            0x44 -> "OPERATION_CANCELLED_BY_HOST" // A request to the Controller issued by the Host and still pending was successfully canceled.
//            0x45 -> "PACKET_TOO_LONG" // An attempt was made to send or receive a packet that exceeds the maximum allowed packet length.
//            0x85 -> "ANDROID_SPECIFIC_ERROR" // Additional Android specific errors
//            0x101 -> "FAILURE_REGISTERING_CLIENT" //  max of 30 clients has been reached.
//            else -> "UNKNOWN_HCI_ERROR ($value)"
//        }
//    }
//
//
//    internal enum class LogLevel {
//        NONE,  // 0
//        ERROR,  // 1
//        WARNING,  // 2
//        INFO,  // 3
//        DEBUG,  // 4
//        VERBOSE // 5
//    }
//
//
//}