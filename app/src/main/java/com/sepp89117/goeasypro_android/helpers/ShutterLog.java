package com.sepp89117.goeasypro_android.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ShutterLog {
    public static final String PREF_NAME = "ShutterLogPref";
    public static final String LOG_KEY = "shutter_log";

    public static void logShutter(Context context, String cameraName, String shutterAction) {
        List<ShutterLogEntry> logEntries = getShutterLog(context);
        ShutterLogEntry entry = new ShutterLogEntry(cameraName, shutterAction);
        logEntries.add(entry);
        saveShutterLog(context, logEntries);
    }

    @SuppressWarnings("unchecked")
    public static List<ShutterLogEntry> getShutterLog(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String serializedLog = prefs.getString(LOG_KEY, "");

        try {
            if (!serializedLog.isEmpty()) {
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(Base64.decode(serializedLog, Base64.DEFAULT));
                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
                Object object = objectInputStream.readObject();
                objectInputStream.close();

                if (object instanceof List<?>) {
                    List<?> tempList = (List<?>) object;
                    if (!tempList.isEmpty() && tempList.get(0) instanceof ShutterLogEntry) {
                        return (List<ShutterLogEntry>) tempList;
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    private static void saveShutterLog(Context context, List<ShutterLogEntry> logEntries) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(logEntries);
            objectOutputStream.close();

            String serializedLog = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
            editor.putString(LOG_KEY, serializedLog);
            editor.apply();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteShutterLogEntry(Context context, int position) {
        List<ShutterLog.ShutterLogEntry> logEntries = ShutterLog.getShutterLog(context);

        if (position >= 0 && position < logEntries.size()) {
            logEntries.remove(position);
            saveShutterLog(context, logEntries);
        }
    }

    public static class ShutterLogEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String dateTime;
        private final String cameraName;
        private final String shutterAction;

        public ShutterLogEntry(String cameraName, String shutterAction) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            this.dateTime = sdf.format(new Date());
            this.cameraName = cameraName;
            this.shutterAction = shutterAction;
        }

        public String getDateTime() {
            return dateTime;
        }

        public String getCameraName() {
            return cameraName;
        }

        public String getShutterAction() {
            return shutterAction;
        }
    }
}
