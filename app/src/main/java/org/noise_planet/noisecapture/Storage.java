/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

package org.noise_planet.noisecapture;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.BaseColumns;

import java.text.DateFormat;
import java.util.Date;

/**
 * Handle database schema creation and upgrade
 */
public class Storage extends SQLiteOpenHelper {
    // Untranslated Tags in the same order as string.xml used when exporting to zip file
    public static final String[] TAGS = {"test", "urban", "nature", "work area",
            "air-traffic", "crowd", "rain", "indoor", "traffic", "two-wheeled", "heavy vehicle",
            "animated", "silent", "birds", "noisy", "big street", "small street"};
    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 6;
    public static final String DATABASE_NAME = "Storage.db";
    private static final String ACTIVATE_FOREIGN_KEY = "PRAGMA foreign_keys=ON;";

    public Storage(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ACTIVATE_FOREIGN_KEY);
        db.execSQL(CREATE_RECORD);
        db.execSQL(CREATE_LEQ);
        db.execSQL(CREATE_LEQ_VALUE);
        db.execSQL(CREATE_RECORD_TAG);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade queries
        // Do not use static table and column names
        if(oldVersion == 1) {
            // Upgrade from 1 to version 2
            // Add record length attribute
            if (!db.isReadOnly()) {
                db.execSQL("ALTER TABLE record ADD COLUMN time_length INTEGER");
            }
            oldVersion = 2;
        }
        if(oldVersion == 2) {
            // Add gps speed and bearing attribute
            if (!db.isReadOnly()) {
                db.execSQL("ALTER TABLE leq ADD COLUMN speed FLOAT");
                db.execSQL("ALTER TABLE leq ADD COLUMN bearing FLOAT");
            }
            oldVersion = 3;
        }
        if(oldVersion == 3) {
            if(db.isReadOnly()) {
                // New feature, user input
                db.execSQL("ALTER TABLE leq ADD COLUMN description TEXT");
                db.execSQL("ALTER TABLE leq ADD COLUMN pleasantness SMALLINT DEFAULT 2");
                db.execSQL("ALTER TABLE leq ADD COLUMN photo_miniature BLOB");
                db.execSQL("ALTER TABLE leq ADD COLUMN photo_uri TEXT");
                db.execSQL( "CREATE TABLE record_tag(tag_id INTEGER PRIMARY KEY, record_id INTEGER, " +
                            "PRIMARY KEY(tag_id, record_id) " +
                            "FOREIGN KEY(record_id) REFERENCES  record(record_id) ON DELETE CASCADE);");
            }
            oldVersion = 4;
        }
        if(oldVersion == 4) {
            db.execSQL("ALTER TABLE record_tag ADD COLUMN tag_system_name TEXT");
            oldVersion = 5;
        }
        if(oldVersion == 5) {
            // Copy content to new table
            db.execSQL("ALTER TABLE record rename to record_old;");
            db.execSQL( "CREATE TABLE record(record_id INTEGER PRIMARY KEY, record_utc LONG," +
                    " upload_id TEXT, leq_mean FLOAT, time_length INTEGER, description TEXT," +
                    " photo_uri TEXT, pleasantness SMALLINT DEFAULT 2);");
            db.execSQL("INSERT INTO record SELECT record_id , record_utc ,upload_id , leq_mean ," +
                    " time_length , description ,photo_uri , pleasantness from record_old;");
            db.execSQL("DROP TABLE IF EXISTS record_old;");
            oldVersion = 6;
        }
    }


    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            // Enable foreign key constraints
            db.execSQL(ACTIVATE_FOREIGN_KEY);
        }
    }

    private static Double getDouble(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getDouble(colIndex);
            }
        } else {
            return null;
        }
    }

    private static Long getLong(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getLong(colIndex);
            }
        } else {
            return null;
        }
    }

    private static Integer getInt(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return (int) cursor.getLong(colIndex);
            }
        } else {
            return null;
        }
    }

    private static Float getFloat(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getFloat(colIndex);
            }
        } else {
            return null;
        }
    }

    private static String getString(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getString(colIndex);
            }
        } else {
            return null;
        }
    }
    private static Bitmap getBitmap(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                byte[] byteData = cursor.getBlob(colIndex);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;
                return BitmapFactory.decodeByteArray(byteData, 0, byteData.length, options);
            }
        } else {
            return null;
        }
    }

    public static class Record implements BaseColumns {
        public static final String TABLE_NAME = "record";
        public static final String COLUMN_ID = "record_id";
        public static final String COLUMN_UTC = "record_utc";
        public static final String COLUMN_UPLOAD_ID = "upload_id";
        public static final String COLUMN_LEQ_MEAN = "leq_mean";
        public static final String COLUMN_TIME_LENGTH = "time_length";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_PLEASANTNESS = "pleasantness";
        public static final String COLUMN_PHOTO_URI = "photo_uri";

        private int id;
        private long utc;
        private String uploadId;
        private float leqMean;
        private int timeLength;
        private String description;
        private Integer pleasantness;
        private Uri photoUri;

        public Record(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_ID)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_UTC)),
                    cursor.getString(cursor.getColumnIndex(COLUMN_UPLOAD_ID)),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_LEQ_MEAN)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_TIME_LENGTH)));
            description = getString(cursor, COLUMN_DESCRIPTION);
            String uriString = getString(cursor, COLUMN_PHOTO_URI);
            if(uriString != null && !uriString.isEmpty()) {
                photoUri = Uri.parse(uriString);
            }
            pleasantness = getInt(cursor, COLUMN_PLEASANTNESS);
        }

        public Record(int id, long utc, String uploadId, float leqMean, int timeLength) {
            this.id = id;
            this.utc = utc;
            this.uploadId = uploadId;
            this.leqMean = leqMean;
            this.timeLength = timeLength;
        }

        public String getDescription() {
            return description;
        }

        public Integer getPleasantness() {
            return pleasantness;
        }

        public Uri getPhotoUri() {
            return photoUri;
        }

        /**
         * @return Record length in seconds
         */
        public int getTimeLength() {
            return timeLength;
        }

        /**
         * @return Upload identifier, empty if not uploaded
         */
        public String getUploadId() {
            return uploadId;
        }

        /**
         * @return Local storage identifier
         */
        public int getId() {
            return id;
        }

        /**
         * @return Record time
         */
        public long getUtc() {
            return utc;
        }

        public String getUtcDate() {
            return DateFormat.getDateTimeInstance().format(new Date(utc));
        }

        public float getLeqMean() {
            return leqMean;
        }
    }

    public static final String CREATE_RECORD = "CREATE TABLE " + Record.TABLE_NAME +
            "("+Record.COLUMN_ID +" INTEGER PRIMARY KEY, " +
            Record.COLUMN_UTC +" LONG, " +
            Record.COLUMN_UPLOAD_ID + " TEXT, " +
            Record.COLUMN_LEQ_MEAN + " FLOAT, " +
            Record.COLUMN_TIME_LENGTH + " INTEGER, " +
            Record.COLUMN_DESCRIPTION + " TEXT, " +
            Record.COLUMN_PHOTO_URI + " TEXT, " +
            Record.COLUMN_PLEASANTNESS + " SMALLINT)";


    public static class Leq implements BaseColumns {
        public static final String TABLE_NAME = "leq";
        public static final String COLUMN_RECORD_ID = "record_id";
        public static final String COLUMN_LEQ_ID = "leq_id";
        public static final String COLUMN_LEQ_UTC = "leq_utc";
        public static final String COLUMN_LATITUDE = "latitude";
        public static final String COLUMN_LONGITUDE = "longitude";
        public static final String COLUMN_ALTITUDE = "altitude";
        public static final String COLUMN_ACCURACY = "accuracy"; // location precision estimation
        public static final String COLUMN_SPEED = "speed"; // device speed estimation
        public static final String COLUMN_BEARING = "bearing"; // device orientation estimation
        public static final String COLUMN_LOCATION_UTC = "location_utc"; // date of last obtained location

        private int recordId;
        private int leqId;
        private long leqUtc;
        private double latitude;
        private double longitude;
        private Double altitude;
        private Float speed;
        private Float bearing;
        private float accuracy;
        private long locationUTC;

        /**
         * @param recordId Record id or -1 if unknown
         * @param leqId
         * @param leqUtc
         * @param latitude
         * @param longitude
         * @param altitude
         * @param speed
         * @param bearing
         * @param accuracy
         * @param locationUTC
         */
        public Leq(int recordId, int leqId, long leqUtc, double latitude, double longitude,
                   Double altitude, Float speed, Float bearing, float accuracy, long locationUTC) {
            this.recordId = recordId;
            this.leqId = leqId;
            this.leqUtc = leqUtc;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.speed = speed;
            this.bearing = bearing;
            this.accuracy = accuracy;
            this.locationUTC = locationUTC;
        }

        public Leq(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_RECORD_ID)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_LEQ_ID)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_LEQ_UTC)),
                    cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE)),
                    getDouble(cursor, COLUMN_ALTITUDE),
                    getFloat(cursor, COLUMN_SPEED),
                    getFloat(cursor, COLUMN_BEARING),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_ACCURACY)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_LOCATION_UTC)));
        }

        public int getRecordId() {
            return recordId;
        }

        public int getLeqId() {
            return leqId;
        }

        public long getLeqUtc() {
            return leqUtc;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public Double getAltitude() {
            return altitude;
        }

        public float getAccuracy() {
            return accuracy;
        }

        /**
         * @return Device speed in ground m/s
         */
        public Float getSpeed() {
            return speed;
        }

        /**
         * @return Device orientation
         */
        public Float getBearing() {
            return bearing;
        }

        public long getLocationUTC() {
            return locationUTC;
        }
    }

    public static final String CREATE_LEQ = "CREATE TABLE " + Leq.TABLE_NAME + "(" +
            Leq.COLUMN_RECORD_ID + " INTEGER, " +
            Leq.COLUMN_LEQ_ID + " INTEGER PRIMARY KEY, " +
            Leq.COLUMN_LEQ_UTC + " LONG, " +
            Leq.COLUMN_LATITUDE + " DOUBLE, " +
            Leq.COLUMN_LONGITUDE + " DOUBLE, " +
            Leq.COLUMN_BEARING + " FLOAT, " +
            Leq.COLUMN_ALTITUDE + " DOUBLE, " +
            Leq.COLUMN_SPEED + " FLOAT, " +
            Leq.COLUMN_ACCURACY + " FLOAT, " +
            Leq.COLUMN_LOCATION_UTC + " LONG, " +
            "FOREIGN KEY(" + Leq.COLUMN_RECORD_ID + ") REFERENCES record("+Record.COLUMN_ID+") ON DELETE CASCADE)";

    public static final class LeqValue implements BaseColumns {
        public static final String TABLE_NAME = "leq_value";
        public static final String COLUMN_LEQ_ID = "leq_id";
        public static final String COLUMN_FREQUENCY = "frequency";
        public static final String COLUMN_SPL = "spl"; // Spl value in dB(A)

        private final int leqId;
        private final int frequency;
        private final float spl;

        public LeqValue(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_LEQ_ID)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_FREQUENCY)),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_SPL)));
        }

        /**
         * @param leqId Leq Id or -1 if unknown
         * @param frequency Frequency in Hertz
         * @param spl Sound pressure value in dB(A)
         */
        public LeqValue(int leqId, int frequency, float spl) {
            this.leqId = leqId;
            this.frequency = frequency;
            this.spl = spl;
        }

        public int getLeqId() {
            return leqId;
        }

        public int getFrequency() {
            return frequency;
        }

        public float getSpl() {
            return spl;
        }
    }

    public static final String CREATE_LEQ_VALUE = "CREATE TABLE " + LeqValue.TABLE_NAME + "(" +
            LeqValue.COLUMN_LEQ_ID +" INTEGER, " +
            LeqValue.COLUMN_FREQUENCY +" INTEGER, " +
            LeqValue.COLUMN_SPL +" FLOAT, " +
            "PRIMARY KEY("+LeqValue.COLUMN_LEQ_ID +", "+LeqValue.COLUMN_FREQUENCY +"), " +
            "FOREIGN KEY("+LeqValue.COLUMN_LEQ_ID +") REFERENCES leq("+Leq.COLUMN_LEQ_ID+") ON DELETE CASCADE);";

    public static final class RecordTag {
        public static final String TABLE_NAME = "record_tag";
        public static final String COLUMN_TAG_ID = "tag_id";
        public static final String COLUMN_TAG_SYSTEM_NAME = "tag_system_name";
        public static final String COLUMN_RECORD_ID = "record_id";
    }

    public static final String CREATE_RECORD_TAG = "CREATE TABLE " + RecordTag.TABLE_NAME + "(" +
            RecordTag.COLUMN_TAG_ID + " INTEGER PRIMARY KEY, " +
            RecordTag.COLUMN_TAG_SYSTEM_NAME + " TEXT, " +
            RecordTag.COLUMN_RECORD_ID + " INTEGER, " +
            "FOREIGN KEY(" + RecordTag.COLUMN_RECORD_ID + ") REFERENCES " + Record.TABLE_NAME +
            "(" + Record.COLUMN_ID + ") ON DELETE CASCADE);";

}
