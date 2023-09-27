package com.afex.mapx.storage;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.afex.mapx.models.CoordinateData;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SqliteHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "mapx_coordinates.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "coordinates";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_COORDINATES = "coordinates";
    private static final String COLUMN_TIME_TO_MAP = "time_to_map";
    private static final String COLUMN_HARDWARE_SERIAL_NUMBER = "hardware_serial_number";
    private static final String COLUMN_DATETIME = "datetime";
    private static final String COLUMN_SENT_TO_SERVER = "has_synced";

    public SqliteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_COORDINATES + " TEXT, " +
                COLUMN_TIME_TO_MAP + " TEXT, " +
                COLUMN_HARDWARE_SERIAL_NUMBER + " TEXT, " +
                COLUMN_DATETIME + " TEXT, " +
                COLUMN_SENT_TO_SERVER + " INTEGER)";
        db.execSQL(createTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public long insertCoordinate(CoordinateData coordinateData) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        // Convert the coordinates list to a JSON string
        Gson gson = new Gson();
        String coordinatesJson = gson.toJson(coordinateData.getCoordinates());
        int itemId = coordinateData.getCoordinates().hashCode();
        values.put(COLUMN_ID, itemId);
        values.put(COLUMN_COORDINATES, coordinatesJson);
        values.put(COLUMN_DATETIME, coordinateData.getDatetime());
        values.put(COLUMN_TIME_TO_MAP, coordinateData.getTimeToMap());
        values.put(COLUMN_HARDWARE_SERIAL_NUMBER, coordinateData.getHardWareSerialNumber());
        values.put(COLUMN_SENT_TO_SERVER, coordinateData.isHasSynced() ? 1 : 0);
        long id = db.insert(TABLE_NAME, null, values);
        db.close();
        return id;
    }

    @SuppressLint("Range")
    public List<CoordinateData> getAllCoordinates() {
        List<CoordinateData> coordinatesList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_NAME, null, null, null, null, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
                    String coordinatesJson = cursor.getString(cursor.getColumnIndex(COLUMN_COORDINATES));

                    // Convert the JSON string back to a list of coordinates
                    Gson gson = new Gson();
                    Type listType = new TypeToken<List<List<Double>>>(){}.getType();
                    List<List<Double>> coordinates = gson.fromJson(coordinatesJson, listType);

                    String timeToMap = cursor.getString(cursor.getColumnIndex(COLUMN_TIME_TO_MAP));
                    String hardwareSerialNumber = cursor.getString(cursor.getColumnIndex(COLUMN_HARDWARE_SERIAL_NUMBER));
                    String datetime = cursor.getString(cursor.getColumnIndex(COLUMN_DATETIME));
                    boolean sentToServer = cursor.getInt(cursor.getColumnIndex(COLUMN_SENT_TO_SERVER)) == 1;

                    CoordinateData coordinateData = new CoordinateData(id, coordinates, datetime, timeToMap, hardwareSerialNumber, sentToServer);
                    coordinatesList.add(coordinateData);
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        db.close();
        return coordinatesList;
    }

    public boolean updateSentToServerStatus(int id, boolean sentToServer) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_SENT_TO_SERVER, sentToServer ? 1 : 0);

        int rowsAffected = db.update(TABLE_NAME, values, COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)});
        db.close();
        return rowsAffected > 0;
    }

    public boolean deleteCoordinate(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = db.delete(TABLE_NAME, COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)});
        db.close();
        return rowsAffected > 0;
    }
}

