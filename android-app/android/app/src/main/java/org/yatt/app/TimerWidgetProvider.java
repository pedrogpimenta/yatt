package org.yatt.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TimerWidgetProvider extends AppWidgetProvider {
    
    public static final String ACTION_TOGGLE = "org.yatt.app.TOGGLE_TIMER";
    public static final String ACTION_UPDATE = "org.yatt.app.UPDATE_WIDGET";
    public static final String PREFS_NAME = "yatt_widget_prefs";
    
    private static Handler handler;
    private static Runnable updateRunnable;
    private static boolean isUpdating = false;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        startPeriodicUpdates(context);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        startPeriodicUpdates(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        stopPeriodicUpdates();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            toggleTimer(context);
        } else if (ACTION_UPDATE.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(context, TimerWidgetProvider.class));
            for (int appWidgetId : appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId);
            }
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.timer_widget);
        
        // Set up click listener for play/pause button
        Intent toggleIntent = new Intent(context, TimerWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(
            context, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.play_pause_button, togglePendingIntent);
        
        // Load cached state
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isRunning = prefs.getBoolean("isRunning", false);
        String currentTimer = prefs.getString("currentTimer", "00:00");
        String dayTotal = prefs.getString("dayTotal", "00:00");
        
        // Update views
        views.setTextViewText(R.id.current_timer, currentTimer);
        views.setTextViewText(R.id.day_total, dayTotal);
        views.setImageViewResource(R.id.play_pause_button, 
            isRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        
        appWidgetManager.updateAppWidget(appWidgetId, views);
        
        // Fetch latest data from API
        fetchTimerData(context);
    }

    private void startPeriodicUpdates(Context context) {
        if (isUpdating) return;
        isUpdating = true;
        
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isUpdating) {
                    Intent updateIntent = new Intent(context, TimerWidgetProvider.class);
                    updateIntent.setAction(ACTION_UPDATE);
                    context.sendBroadcast(updateIntent);
                    handler.postDelayed(this, 60000); // Update every minute
                }
            }
        };
        handler.post(updateRunnable);
    }

    private void stopPeriodicUpdates() {
        isUpdating = false;
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    private void toggleTimer(Context context) {
        executor.execute(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String apiUrl = prefs.getString("apiUrl", null);
                String token = prefs.getString("token", null);
                boolean isRunning = prefs.getBoolean("isRunning", false);
                String runningTimerId = prefs.getString("runningTimerId", null);
                
                if (apiUrl == null || token == null) {
                    return;
                }
                
                if (isRunning && runningTimerId != null) {
                    // Stop timer
                    makeRequest(apiUrl + "/timers/" + runningTimerId + "/stop", "POST", token, null);
                } else {
                    // Start timer
                    makeRequest(apiUrl + "/timers", "POST", token, "{}");
                }
                
                // Fetch updated data
                fetchTimerData(context);
                
                // Update widget
                new Handler(Looper.getMainLooper()).post(() -> {
                    Intent updateIntent = new Intent(context, TimerWidgetProvider.class);
                    updateIntent.setAction(ACTION_UPDATE);
                    context.sendBroadcast(updateIntent);
                });
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void fetchTimerData(Context context) {
        executor.execute(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String apiUrl = prefs.getString("apiUrl", null);
                String token = prefs.getString("token", null);
                
                if (apiUrl == null || token == null) {
                    return;
                }
                
                String response = makeRequest(apiUrl + "/timers", "GET", token, null);
                if (response == null) return;
                
                JSONArray timers = new JSONArray(response);
                
                long now = System.currentTimeMillis();
                long todayStart = getTodayStart();
                long dayTotalMs = 0;
                long currentTimerMs = 0;
                boolean isRunning = false;
                String runningTimerId = null;
                
                for (int i = 0; i < timers.length(); i++) {
                    JSONObject timer = timers.getJSONObject(i);
                    long startTime = parseTime(timer.getString("start_time"));
                    
                    if (startTime >= todayStart) {
                        if (timer.isNull("end_time")) {
                            // Running timer
                            isRunning = true;
                            runningTimerId = timer.getString("id");
                            currentTimerMs = now - startTime;
                            dayTotalMs += currentTimerMs;
                        } else {
                            long endTime = parseTime(timer.getString("end_time"));
                            dayTotalMs += endTime - startTime;
                        }
                    }
                }
                
                // Save to prefs
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isRunning", isRunning);
                editor.putString("runningTimerId", runningTimerId);
                editor.putString("currentTimer", formatDuration(currentTimerMs));
                editor.putString("dayTotal", formatDuration(dayTotalMs));
                editor.apply();
                
                // Create final copies for lambda
                final long finalCurrentTimerMs = currentTimerMs;
                final long finalDayTotalMs = dayTotalMs;
                final boolean finalIsRunning = isRunning;
                
                // Update widget on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                        new ComponentName(context, TimerWidgetProvider.class));
                    
                    for (int appWidgetId : appWidgetIds) {
                        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.timer_widget);
                        views.setTextViewText(R.id.current_timer, formatDuration(finalCurrentTimerMs));
                        views.setTextViewText(R.id.day_total, formatDuration(finalDayTotalMs));
                        views.setImageViewResource(R.id.play_pause_button, 
                            finalIsRunning ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                        
                        // Re-set click listener
                        Intent toggleIntent = new Intent(context, TimerWidgetProvider.class);
                        toggleIntent.setAction(ACTION_TOGGLE);
                        PendingIntent togglePendingIntent = PendingIntent.getBroadcast(
                            context, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        views.setOnClickPendingIntent(R.id.play_pause_button, togglePendingIntent);
                        
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                    }
                });
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private String makeRequest(String urlString, String method, String token, String body) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            if (body != null && !method.equals("GET")) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private long parseTime(String isoTime) {
        try {
            return java.time.Instant.parse(isoTime).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private long getTodayStart() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return String.format("%02d:%02d", hours, minutes);
    }
}
