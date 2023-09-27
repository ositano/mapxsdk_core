package com.afex.mapx;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.os.Handler;
import android.os.Message;

import android.util.Log;

import com.afex.mapx.models.MapXBluetoothMessage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A thread that connects to a remote device over Bluetooth, and reads/writes data
 * using message Handlers. A delimiter character is used to parse messages from a stream,
 * and must be implemented on the other side of the connection as well. If the connection
 * fails, the thread exits.
 */
public class ConnectDeviceThread extends Thread {

    // Tag for logging
    private static final String TAG = "ConnectDeviceThread";

    // Delimiter used to separate messages
    private static final char DELIMITER = '\n';

    // UUID that specifies a protocol for generic bluetooth serial communication
    private static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC address of remote Bluetooth device
    //private final String address;

    // Bluetooth socket of active connection
    private BluetoothSocket socket;
    private final BluetoothDevice bluetoothDevice;

    // Streams that we read from and write to
    private OutputStream outStream;
    private InputStream inStream;

    // Handlers used to pass data between threads
    //private final Handler readHandler;
    private final Handler handler;
    //private final Handler writeHandler;

    // Buffer used to parse messages
    private String rx_buffer = "";

    protected static final UUID SERIAL_PROFILE_UUID = UUID.fromString(
            "00001101-0000-1000-8000-00805F9B34FB"
    );

    /**
     * Constructor, takes in the MAC address of the remote Bluetooth device
     * and a Handler for received messages.
     *
     */
    public ConnectDeviceThread(Handler handler, BluetoothDevice bluetoothDevice) {
        this.handler = handler;
        this.bluetoothDevice = bluetoothDevice;

    }


    /**
     * Connect to a remote Bluetooth socket, or throw an exception if it fails.
     */
    @SuppressLint("MissingPermission")
    private void connect(boolean secure) {
        Log.i(TAG, "Attempting connection to " + bluetoothDevice.getAddress() + "...");
        try {
            socket = secure ? bluetoothDevice.createRfcommSocketToServiceRecord(SERIAL_PROFILE_UUID) :
                    bluetoothDevice.createInsecureRfcommSocketToServiceRecord(SERIAL_PROFILE_UUID);
            socket.connect();
            // Get input and output streams from the socket
            outStream = socket.getOutputStream();
            inStream = socket.getInputStream();

            Message readMsg = handler.obtainMessage(
                    MapXConstants.messageSocketConnected, -1, -1,
                    new MapXBluetoothMessage(
                            bluetoothDevice,
                            "Device connected successfully"
                    )
            );
            readMsg.sendToTarget();
            Log.i(TAG, "Connected successfully to " + bluetoothDevice.getAddress() + ".");
        } catch (IOException openException) {
            Message readMsg = handler.obtainMessage(
                    MapXConstants.messageSocketClosed, -1, -1,
                    new MapXBluetoothMessage(
                            bluetoothDevice,
                            "Bluetooth connection failed"
                    )
            );
            readMsg.sendToTarget();
            Log.e(TAG, "Bluetooth connect failed: " + bluetoothDevice.getAddress() + ": " + openException.getMessage());
            //close();
        }
    }

    /**
     * Disconnect the streams and socket.
     */
    private void disconnect() {
        Message readMsg = handler.obtainMessage(
                MapXConstants.messageSocketClosed, -1, -1,
                new MapXBluetoothMessage(
                        bluetoothDevice,
                        "Bluetooth disconnected"
                )
        );
        readMsg.sendToTarget();
        if (inStream != null) {
            try {inStream.close();} catch (Exception e) { e.printStackTrace(); }
        }

        if (outStream != null) {
            try {outStream.close();} catch (Exception e) { e.printStackTrace(); }
        }

        if (socket != null) {
            try {socket.close();} catch (Exception e) { e.printStackTrace(); }
        }
    }

    /**
     * Return data read from the socket, or a blank string.
     */
    private String read() {
        String s = "";
        try {
            // Check if there are bytes available
            if (inStream.available() > 0) {
                // Read bytes into a buffer
                byte[] inBuffer = new byte[1024];
                int bytesRead = inStream.read(inBuffer);

                // Convert read bytes into a string
                s = new String(inBuffer, StandardCharsets.US_ASCII);
                s = s.substring(0, bytesRead);
            }
        } catch (Exception e) {
            Message readMsg = handler.obtainMessage(
                    MapXConstants.messageReadFailed, -1, -1,
                    new MapXBluetoothMessage(
                            bluetoothDevice,
                            "Unable to read data from the device, disconnect and reconnect if persists"
                    )
            );
            readMsg.sendToTarget();
            Log.e(TAG, "Read failed!", e);
        }
        return s;
    }

    /**
     * Write data to the socket.
     */
    public void write(byte[] bytes) {
        try {
            String s = new String(bytes);
            // Add the delimiter
            s += DELIMITER;

            // Convert to bytes and write
            outStream.write(s.getBytes());
            Log.i(TAG, "[SENT] " + s);

            Message readMsg = handler.obtainMessage(
                    MapXConstants.messageWrite, -1, -1,
                    new MapXBluetoothMessage(
                            bluetoothDevice,
                            s
                    )
            );
            readMsg.sendToTarget();
        } catch (Exception e) {
            Message readMsg = handler.obtainMessage(
                    MapXConstants.messageWriteFailed, -1, -1,
                    new MapXBluetoothMessage(
                            bluetoothDevice,
                            "Unable to perform the requested operation. Disconnect and reconnect if persists"
                    )
            );
            readMsg.sendToTarget();
            Log.e(TAG, "Write failed!", e);
        }
    }

    /**
     * Pass a message to the read handler.
     */
    private void sendToReadHandler(String s) {
        respondToBluetoothMessage(s);
        Log.i(TAG, "[RECV] " + s);
    }

    /**
     * Send complete messages from the rx_buffer to the read handler.
     */
    private void parseMessages() {

        // Find the first delimiter in the buffer
        int inx = rx_buffer.indexOf(DELIMITER);

        // If there is none, exit
        if (inx == -1)
            return;

        // Get the complete message
        String s = rx_buffer.substring(0, inx);

        // Remove the message from the buffer
        rx_buffer = rx_buffer.substring(inx + 1);

        // Send to read handler
        sendToReadHandler(s);

        // Look for more complete messages
        parseMessages();
    }

    /**
     * Entry point when thread.start() is called.
     */
    public void run() {
        // Attempt to connect and exit the thread if it failed
        try {
            connect(true);
            sendToReadHandler("CONNECTED");
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect!", e);
            sendToReadHandler("CONNECTION FAILED");
            disconnect();
            return;
        }

        // Loop continuously, reading data, until thread.interrupt() is called
        while (!this.isInterrupted()) {

            // Make sure things haven't gone wrong
            if ((inStream == null) || (outStream == null)) {
                Log.e(TAG, "Lost bluetooth connection!");
                break;
            }

            // Read data and add it to the buffer
            String s = read();
            if (s.length() > 0)
                rx_buffer += s;

            // Look for complete messages
            parseMessages();
        }

        // If thread is interrupted, close connections
        disconnect();
        sendToReadHandler("DISCONNECTED");
    }

        public void close() {
        if (inStream != null) {
            try {
                inStream.close();
            } catch (IOException exception) {
                Log.w(TAG, "Bluetooth input stream close error: " + bluetoothDevice.getAddress(), exception);
            }

            inStream = null;
        }

        if (outStream != null) {
            try {
                outStream.close();
            } catch (IOException exception) {
                Log.w(TAG, "Bluetooth output stream close error: " + bluetoothDevice.getAddress(), exception);
            }

            outStream = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException exception) {
                Log.w(TAG, "Bluetooth socket close error: " + bluetoothDevice.getAddress(), exception);
            }
            socket = null;
        }
    }

    private static boolean isNumber(String s) {
        return s != null && !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    private static boolean hasTimeFormat(String input) {
        String timeRegex = "^\\d{1,2}:\\d{1,2}:\\d{1,2}$";
        return input.matches(timeRegex);
    }

    private static boolean hasCoordinatesFormat(String input) {
        String coordinatesRegex = "\\$,\\d+\\.\\d+,\\d+\\.\\d+,\\d{1,2}/\\d{1,2}/\\d{4},\\d{1,2}:\\d{1,2}:\\d{1,2}";
        return input.matches(coordinatesRegex);
    }

    private static boolean hasCoordinatesWithHashFormat(String input) {
        String coordinatesRegex = "\\#,\\$,\\d+\\.\\d+,\\d+\\.\\d+,\\d{1,2}/\\d{1,2}/\\d{4},\\d{1,2}:\\d{1,2}:\\d{1,2}";
        return input.matches(coordinatesRegex);
    }

    private void respondToBluetoothMessage(String lastMessage){
        if (isNumber(lastMessage)){ ///likely a battery level
            Message readMsg = handler.obtainMessage(
                    MapXConstants.messageReadBattery, -1, -1,
                    new MapXBluetoothMessage(
                            bluetoothDevice,
                            lastMessage
                    )
            );
            readMsg.sendToTarget();
        }
        else if (lastMessage.contains("MAPX")){
            Message readMsg = handler.obtainMessage(
                    MapXConstants.messageDeviceSerialNumber, -1, -1,
                    new MapXBluetoothMessage(
                            bluetoothDevice,
                            lastMessage
                    )
            );
            readMsg.sendToTarget();
        }else if (lastMessage.contains("NRTK")){
            Message readMsg = handler.obtainMessage(
                    MapXConstants.messageDeviceModelNumber, -1, -1,
                    new MapXBluetoothMessage(
                            bluetoothDevice,
                            lastMessage
                    )
            );
            readMsg.sendToTarget();
        }else if (lastMessage.equals("OK")){
            Message readMsg = handler.obtainMessage(
                    MapXConstants.messageEndSession, -1, -1,
                    new MapXBluetoothMessage(
                            bluetoothDevice,
                            lastMessage
                    )
            );
            readMsg.sendToTarget();
        }else{
            //Log.d(TAG, "  <<<*>>> read data 2: $lastMessage")
            if(hasCoordinatesFormat(lastMessage)  || hasCoordinatesWithHashFormat(lastMessage)){
                //Log.d(TAG, "  <<<*>>> sending data 1 .... : $lastMessage")
                Message readMsg = handler.obtainMessage(
                        MapXConstants.messageTakePoint, -1, -1,
                        new MapXBluetoothMessage(
                                bluetoothDevice,
                                lastMessage
                        )
                );
                readMsg.sendToTarget();
            }

        }
    }

}