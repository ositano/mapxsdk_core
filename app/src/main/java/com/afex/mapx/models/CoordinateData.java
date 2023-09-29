package com.afex.mapx.models;

import androidx.annotation.NonNull;

import java.util.List;

public class CoordinateData {
    private int id; // Unique identifier for the coordinate entry
    private List<List<Double>> coordinates; // JSON string or list representing latitude and longitude pairs
    private String datetime; // Timestamp or date-time string
    private String timeToMap; // Timestamp or date-time string
    private String hardWareSerialNumber; // Timestamp or date-time string
    private boolean hasSynced; // Flag indicating whether data has been sent to the server

    // Constructors, getters, and setters
    public CoordinateData() {
        // Default constructor
    }

    public CoordinateData(int id, List<List<Double>> coordinates, String datetime, String timeToMap, String hardWareSerialNumber, boolean hasSynced) {
        this.id = id;
        this.coordinates = coordinates;
        this.datetime = datetime;
        this.timeToMap = timeToMap;
        this.hasSynced = hasSynced;
        this.hardWareSerialNumber = hardWareSerialNumber;
    }

    // Getter and Setter methods for each field
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<List<Double>> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<List<Double>> coordinates) {
        this.coordinates = coordinates;
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    public String getTimeToMap() {
        return timeToMap;
    }

    public void setTimeToMap(String datetime) {
        this.timeToMap = datetime;
    }

    public boolean isHasSynced() {
        return hasSynced;
    }

    public void setHasSynced(boolean sentToServer) {
        this.hasSynced = sentToServer;
    }

    public String getHardWareSerialNumber() {
        return hardWareSerialNumber;
    }

    public void setHardWareSerialNumber(String hardWareSerialNumber) {
        this.hardWareSerialNumber = hardWareSerialNumber;
    }
    @NonNull
    @Override
    public String toString(){
        return String.format("{location_details: %s, hardware_serial_number: %s, date_created: %s, time_taken_to_map: %s}", coordinates.toString(), hardWareSerialNumber, datetime, timeToMap);
    }
}

