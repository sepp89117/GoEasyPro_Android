package com.sepp89117.goeasypro_android;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.sepp89117.goeasypro_android.gopro.GoProDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the two notification types requested:
 * 1) A silent, ongoing "Connected" notification listing all connected cameras.
 * 2) A non-silent "Disconnected" notification for a specific camera when it disconnects unexpectedly.
 */
public final class NotificationHelper {

    private NotificationHelper() {
    }

    public static final String CHANNEL_CONNECTED_ID = "gopro_bt_connected";
    public static final String CHANNEL_DISCONNECTED_ID = "gopro_bt_disconnected";

    private static final int NOTIF_ID_CONNECTED = 1001;
    private static final int NOTIF_ID_DISCONNECTED_BASE = 2000;

    public static void ensureChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
        if (nm == null) return;

        // Silent "connected" channel (blocked by default)
        NotificationChannel connected = new NotificationChannel(
                CHANNEL_CONNECTED_ID,
                context.getString(R.string.str_notif_channel_connected_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        connected.setDescription(context.getString(R.string.str_notif_channel_connected_desc));
        connected.setShowBadge(false);

        // Alerting "disconnected" channel (blocked by default)
        NotificationChannel disconnected = new NotificationChannel(
                CHANNEL_DISCONNECTED_ID,
                context.getString(R.string.str_notif_channel_disconnected_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        disconnected.setDescription(context.getString(R.string.str_notif_channel_disconnected_desc));
        disconnected.setShowBadge(true);

        nm.createNotificationChannel(connected);
        nm.createNotificationChannel(disconnected);
    }

    /** Call whenever connection states may have changed. */
    public static void refreshConnectedNotification(@NonNull Context context, @NonNull List<GoProDevice> devices) {
        if (!notificationsAllowed(context)) return;
        ensureChannels(context);

        List<String> connectedNames = new ArrayList<>();
        for (GoProDevice d : devices) {
            if (d != null && d.isBtConnected()) {
                String name = (d.displayName != null && !d.displayName.isEmpty()) ? d.displayName : d.btDeviceName;
                if (name != null && !name.isEmpty()) connectedNames.add(name);
            }
        }

        NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
        if (nm == null) return;

        if (connectedNames.isEmpty()) {
            nm.cancel(NOTIF_ID_CONNECTED);
            return;
        }

        String title = context.getString(R.string.str_notif_connected_title);
        String body = join(connectedNames, "\n");

        PendingIntent contentIntent = buildOpenAppPendingIntent(context);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_CONNECTED_ID)
                .setSmallIcon(R.drawable.ic_baseline_bluetooth_24)
                .setContentTitle(title)
                .setContentText(join(connectedNames, ", "))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(contentIntent)
                .build();

        nm.notify(NOTIF_ID_CONNECTED, notification);
    }

    /** Convenience overload for this app (device list is held in {@link MyApplication}). */
    public static void refreshConnectedNotification(@NonNull Context context) {
        if (context.getApplicationContext() instanceof MyApplication) {
            MyApplication app = (MyApplication) context.getApplicationContext();
            refreshConnectedNotification(context, app.getGoProDevices());
        }
    }

    /** Shows a per-camera disconnected notification (non-silent). */
    public static void showDisconnectedNotification(@NonNull Context context, @NonNull String cameraName, @NonNull String macAddress) {
        if (!notificationsAllowed(context)) return;
        ensureChannels(context);

        NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
        if (nm == null) return;

        PendingIntent contentIntent = buildOpenAppPendingIntent(context);

        int id = NOTIF_ID_DISCONNECTED_BASE + Math.abs(macAddress.hashCode() % 100000);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_DISCONNECTED_ID)
                .setSmallIcon(R.drawable.disconnect_symbol)
                .setContentTitle(context.getString(R.string.str_notif_disconnected_title))
                .setContentText(cameraName)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(contentIntent)
                .build();

        nm.notify(id, notification);
    }

    private static boolean notificationsAllowed(@NonNull Context context) {
        // System-level notifications switch (all notifications) and Android 13+ runtime permission.
        NotificationManager nm = ContextCompat.getSystemService(context, NotificationManager.class);
        if (nm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !nm.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private static PendingIntent buildOpenAppPendingIntent(@NonNull Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(intent);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return stackBuilder.getPendingIntent(0, flags);
    }

    private static String join(@NonNull List<String> items, @NonNull String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
