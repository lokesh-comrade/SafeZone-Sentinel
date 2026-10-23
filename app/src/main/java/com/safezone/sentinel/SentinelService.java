package com.safezone.sentinel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class SentinelService extends Service implements SensorEventListener {

    public static final String ACTION_SHAKE_SOS = "com.safezone.SHAKE_SOS_TRIGGER";

    private static final String CHANNEL_ID = "safezone_sentinel_channel";
    private static final int NOTIFICATION_ID = 1001;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private int shakeCount = 0;
    private long lastShakeTime = 0;
    private long firstShakeTime = 0;

    private static final float SHAKE_THRESHOLD_GRAVITY = 22.0f;
    private static final int SHAKE_DEBOUNCE_MS = 300;
    private static final int SHAKE_WINDOW_MS = 2500;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        startForegroundServiceNotification();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SafeZone Sentinel Watchdog",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background monitoring for motion triggers and location protection");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundServiceNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeZone Protection Active")
                .setContentText("Monitoring shake watchdog & background location.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            double acceleration = Math.sqrt(x * x + y * y + z * z);
            double deltaGravity = Math.abs(acceleration - SensorManager.GRAVITY_EARTH);

            if (deltaGravity > SHAKE_THRESHOLD_GRAVITY) {
                long now = System.currentTimeMillis();

                if (now - lastShakeTime >= SHAKE_DEBOUNCE_MS) {
                    if (shakeCount == 0 || (now - firstShakeTime > SHAKE_WINDOW_MS)) {
                        shakeCount = 1;
                        firstShakeTime = now;
                    } else {
                        shakeCount++;
                    }
                    lastShakeTime = now;

                    if (shakeCount >= 5) {
                        triggerShakeSosBroadcast();
                        shakeCount = 0;
                        lastShakeTime = now + 3000; // Cooldown after triggering
                    }
                }
            }
        }
    }

    private void triggerShakeSosBroadcast() {
        Intent broadcastIntent = new Intent(ACTION_SHAKE_SOS);
        broadcastIntent.setPackage(getPackageName());
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
}
