package com.afex.mapx.network;

import android.os.AsyncTask;
import android.util.Log;

import com.afex.mapx.AsyncTasks;
import com.afex.mapx.HashHelper;
import com.afex.mapx.models.CoordinateData;
import com.afex.mapx.models.DeviceInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

public class NetworkClientHelper {
    private static final String TAG = NetworkClientHelper.class.getSimpleName();

    // Define an interface to handle responses from the server
    public interface ResponseListener {
        void onResponse(String response);
        void onError(String error);
    }

    public static void sendDataToServer(boolean isDebug, String apiKey, String hashKey, CoordinateData coordinateData, ResponseListener listener) {
        new SendDataTask(isDebug, apiKey, hashKey, coordinateData, listener).execute();
    }

    private static class SendDataTask extends AsyncTasks {
        private final String apiKey;
        private final String secretKey;
        private final boolean isDebug;
        private final ResponseListener listener;
        private final CoordinateData coordinateData;

        /*
        {
            "time_taken_to_map": "00:0500",
            "device_details": {
                "model": "Samsung Galaxy A51",
                "android_version": "11R",
                "language": "English",
                "timezone": "GMT +1",
                "RAM": "8192MB"
            },
            "hardware_serial_number": "AFEXGPS0001",
            "location_details":  [
                [3.9582678, 7.3661434],
                [3.9503714, 7.3566947],
                [3.9684817, 7.3542261],
                [3.9714858, 7.3620575],
                [3.9582678, 7.3661434]
            ],
          }
         */
        SendDataTask(boolean isDebug, String apiKey, String secretKey, CoordinateData coordinateData, ResponseListener listener) {
            this.apiKey = apiKey;
            this.secretKey = secretKey;
            this.listener = listener;
            this.isDebug = isDebug;
            this.coordinateData = coordinateData;
        }

        private static JSONArray convertCoordinatesListToJSONArray(List<List<Double>> coordinatesList) {
            JSONArray jsonArray = new JSONArray();
            for (List<Double> coordinates : coordinatesList) {
                JSONArray innerArray = new JSONArray();
                for (Double coordinate : coordinates) {
                    innerArray.put(coordinate);
                }
                jsonArray.put(innerArray);
            }
            return jsonArray;
        }

        @Override
        public void onPreExecute(){

        }

        @Override
        public String doInBackground() {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            BufferedReader reader = null;

            try {
                JSONObject deviceObject = new JSONObject();
                deviceObject.put("model", DeviceInfo.getDeviceModel());
                deviceObject.put("android_version", DeviceInfo.getAndroidVersion());
                deviceObject.put("language", DeviceInfo.getDeviceLanguage());
                deviceObject.put("timezone", DeviceInfo.getDeviceTimeZone());
                deviceObject.put("RAM", DeviceInfo.getRAMSize());

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("time_taken_to_map", coordinateData.getTimeToMap());
                jsonObject.put("device_details", deviceObject);
                jsonObject.put("hardware_serial_number", coordinateData.getHardWareSerialNumber());
                jsonObject.put("location_details", convertCoordinatesListToJSONArray(coordinateData.getCoordinates()));

                URL url = new URL(isDebug ? "https://mapx.afex.dev/api/captures/add" : "https://mapx.afex.com/api/captures/add");
                connection = (HttpURLConnection) url.openConnection();

                String requestTs = ""+System.currentTimeMillis();
                String data = apiKey+ secretKey + requestTs;
                String hashKey = HashHelper.getSha256Hash(data);

                // Set up the HTTP POST request
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("api-key", apiKey);
                connection.setRequestProperty("hash-key", hashKey);
                connection.setRequestProperty("request-ts", requestTs);
                connection.setRequestProperty("Content-Type", "application/json");

                Log.d(TAG, connection.getRequestProperties().toString());
                Log.d(TAG, jsonObject.toString());
                // Write the request data to the server
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(jsonObject.toString().getBytes());
                outputStream.flush();
                outputStream.close();

                // Get the response from the server
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream = connection.getInputStream();
                    reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    return response.toString();
                } else {
                    return "HTTP Error Code: " + responseCode;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending data to server: " + e.getMessage(), e);
                return "Error sending data to server: " + e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error closing streams: " + e.getMessage(), e);
                }
            }
        }

        @Override
        public void onPostExecute(String response) {
            if (response != null) {
                if (response.startsWith("HTTP Error Code")) {
                    listener.onError(response);
                } else {
                    listener.onResponse(response);
                }
            } else {
                listener.onError("No response from server");
            }
        }
    }
}

