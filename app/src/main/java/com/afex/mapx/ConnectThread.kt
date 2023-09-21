package com.afex.mapx

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.concurrent.thread

private const val TAG = "ConnectThread"

@SuppressLint("MissingPermission")
class ConnectThread(private val handler: Handler, private val device: BluetoothDevice) : Thread() {

    private val messageRead: Int = 0
    private val messageWrite: Int = 1
    private val messageToast: Int = 2
    private val messageTakePoint = 3
    private val messageEndSession = 4
    private val messageReadBattery = 5
    private val messageShutDown = 6
    private val messageDeviceInfo = 7

    private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
    }

    private lateinit var mmInStream: InputStream
    private lateinit var mmOutStream: OutputStream
    private lateinit var mmBuffer: ByteArray // mmBuffer store for the stream

    override fun run() {
        mmSocket?.let { socket ->
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            socket.connect()

            mmInStream = socket.inputStream
            mmOutStream = socket.outputStream
            mmBuffer = ByteArray(1024)

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            thread { read() }
        }
    }

    private fun read() {
        var numBytes = 0 // bytes returned from read()
        //var readMessage = ""
        val readMessage = StringBuilder()
        var received : List<UByte> = listOf()
        // Keep listening to the InputStream until an exception occurs.

        //the first data stream from the gps is $
        //looking for something like this $,7.228485,9.143942,28/7/2023,6:22:50
        while (true) {
            // Read from the InputStream.
            numBytes = try {
                mmInStream.read(mmBuffer)
            } catch (e: IOException) {
                Log.d(TAG, "Input stream was disconnected", e)
                break
            }
            // Log.d(TAG, "read streams: $numBytes")
            //readMessage += String(mmBuffer, 0, numBytes)
            readMessage.append(String(mmBuffer, 0, numBytes))
            //Log.d(TAG, "  <<<*>>> read data: $readMessage")
            received = mmBuffer.sliceArray(IntRange(0, numBytes - 1)).map { it.toUByte() }
            //Log.d(TAG, "  <<< ${received.joinToString { "$it" }}")
            val lastMessage = String(mmBuffer, 0, numBytes);

            if (isNumber(lastMessage)){ ///likely a battery level
                val readMsg = handler.obtainMessage(
                    messageReadBattery, numBytes, -1,
                    MessageObject(
                        device,
                        received,
                        lastMessage
                    )
                )
                readMsg.sendToTarget()
            }else if (lastMessage.contains("MAPX")){
                val readMsg = handler.obtainMessage(
                    messageDeviceInfo, numBytes, -1,
                    MessageObject(
                        device,
                        received,
                        lastMessage
                    )
                )
                readMsg.sendToTarget()
            }else if (lastMessage == "OK"){
                val readMsg = handler.obtainMessage(
                    messageEndSession, numBytes, -1,
                    MessageObject(
                        device,
                        received,
                        lastMessage
                    )
                )
                readMsg.sendToTarget()
            }else{
                //Log.d(TAG, "  <<<*>>> read data 2: $lastMessage")
                if(hasCoordinatesFormat(lastMessage)  || hasCoordinatesWithHashFormat(lastMessage) || lastMessage == "#"){
                    //Log.d(TAG, "  <<<*>>> sending data 1 .... : $lastMessage")
                    val readMsg = handler.obtainMessage(
                        messageTakePoint, numBytes, -1,
                        MessageObject(
                            device,
                            received,
                            lastMessage
                        )
                    )
                    readMsg.sendToTarget()
                }

                // Send the obtained bytes to the UI activity.
                if(readMessage.toString().contains("\n") || readMessage.toString().contains("\r")){
                    val lines = readMessage.toString().split("[\r\n]+".toRegex()).toTypedArray()
                    //Log.d(TAG, "  <<<*>>> sending data 2 .... : ${lines[0]}")
                    if(hasCoordinatesFormat(lines[0]) || hasCoordinatesWithHashFormat(lines[0])) {
                        val readMsg = handler.obtainMessage(
                            messageRead, numBytes, -1,
                            MessageObject(
                                device,
                                received,
                                lines[0]
                            )
                        )
                        readMsg.sendToTarget()
                    }
                }
            }
        }
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

    fun write(bytes: ByteArray) {
        //Log.d(TAG, ">>>   ${bytes.joinToString { it.toUByte().toString() }}")

        try {
            mmOutStream.write(bytes)
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred when sending data", e)

            // Send a failure message back to the activity.
            val writeErrorMsg = handler.obtainMessage(messageToast)
            val bundle = Bundle().apply {
                putString("toast", "Couldn't send data to the other device")
            }
            writeErrorMsg.data = bundle
            handler.sendMessage(writeErrorMsg)
            return
        }

        // Share the sent message with the UI activity.
        val writtenMsg = handler.obtainMessage(
            messageWrite, -1, -1, mmBuffer
        )
        writtenMsg.sendToTarget()
    }

    // Closes the client socket and causes the thread to finish.
    fun cancel() {
        try {
            mmSocket?.close()
            //Log.d(TAG, "Connect thread - closed")
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the client socket", e)
        }
    }
}

data class MessageObject(val device: BluetoothDevice, val data: Any, val dataMessage: String)

