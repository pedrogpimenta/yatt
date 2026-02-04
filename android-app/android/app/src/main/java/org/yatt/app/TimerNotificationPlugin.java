// Copy this file to: android/app/src/main/java/org/yatt/app/TimerNotificationPlugin.java

package org.yatt.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "TimerNotification",
    permissions = {
        @Permission(
            alias = "notifications",
            strings = { Manifest.permission.POST_NOTIFICATIONS }
        )
    }
)
public class TimerNotificationPlugin extends Plugin {
    private static final String CHANNEL_ID = "yatt_timer_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private Handler handler;
    private Runnable updateRunnable;
    private long startTime;
    private String tag;
    private long dayTotalBase; // completed time today before current timer
    private boolean isRunning = false;

    @Override
    public void load() {
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Timer",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows running timer");
            channel.setShowBadge(false);
            
            NotificationManager notificationManager = getContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionForAlias("notifications", call, "permissionCallback");
                return;
            }
        }
        JSObject result = new JSObject();
        result.put("granted", true);
        call.resolve(result);
    }

    @PermissionCallback
    private void permissionCallback(PluginCall call) {
        JSObject result = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.put("granted", ContextCompat.checkSelfPermission(getContext(), 
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        } else {
            result.put("granted", true);
        }
        call.resolve(result);
    }

    @PluginMethod
    public void start(PluginCall call) {
        startTime = call.getLong("startTime", System.currentTimeMillis());
        tag = call.getString("tag", null);
        dayTotalBase = call.getLong("dayTotalBase", 0L);
        
        isRunning = true;
        startUpdating();
        
        // Update widget
        updateWidget();
        
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        isRunning = false;
        stopUpdating();
        
        NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
        
        // Update widget
        updateWidget();
        
        call.resolve();
    }

    @PluginMethod
    public void syncCredentials(PluginCall call) {
        String apiUrl = call.getString("apiUrl");
        String token = call.getString("token");
        
        android.content.SharedPreferences prefs = getContext().getSharedPreferences(
            "yatt_widget_prefs", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        if (apiUrl != null) {
            editor.putString("apiUrl", apiUrl);
        }
        if (token != null) {
            editor.putString("token", token);
        } else {
            editor.remove("token");
        }
        editor.apply();
        
        // Update widget
        updateWidget();
        
        call.resolve();
    }

    private void updateWidget() {
        Intent updateIntent = new Intent("org.yatt.app.UPDATE_WIDGET");
        updateIntent.setPackage(getContext().getPackageName());
        getContext().sendBroadcast(updateIntent);
    }

    private void startUpdating() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    updateNotification();
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(updateRunnable);
    }

    private void stopUpdating() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
    }

    private void updateNotification() {
        long elapsed = System.currentTimeMillis() - startTime;
        long dayTotal = dayTotalBase + elapsed;
        
        String title = tag != null ? tag : "Timer Running";
        String content = formatDuration(elapsed) + "  •  Today: " + formatDuration(dayTotal);

        Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(getContext().getPackageName());
        PendingIntent pendingIntent = PendingIntent.getActivity(
            getContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build();

        NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        return String.format("%02d:%02d", hours, minutes);
    }
}
