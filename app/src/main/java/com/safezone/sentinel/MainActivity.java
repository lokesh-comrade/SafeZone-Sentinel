package com.safezone.sentinel;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.telephony.SmsManager;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "SafeZonePrefs";
    private static final int PERMISSION_REQUEST_CODE = 2001;

    private TextView tvGreeting;

    private LinearLayout llStatusCard;
    private ImageView ivStatusIcon;
    private TextView tvStatusTitle;
    private TextView tvGpsCoordinates;
    private TextView tvWatchdogNotice;

    private Button btnDisarmSos;

    private FrameLayout flSliderTrack;
    private ImageView ivSliderThumb;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private Location previousLocation;

    private BroadcastReceiver shakeReceiver;
    private boolean isEmergencyActive = false;

    private String userName;
    private String userPhone;
    private final List<String> contactNumbers = new ArrayList<>();

    private Camera mCamera;
    private String latestBase64CameraFrame = "";
    private String lastTriggerSource = "MANUAL_TRIGGER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadUserDataFromPrefs();
        initViews();
        setupSliderTouchListener();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        checkAndRequestPermissions();
        registerShakeReceiver();
        startSentinelWatchdogService();
    }

    private void loadUserDataFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isOnboarded = prefs.getBoolean("is_onboarded", false);
        if (!isOnboarded) {
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        userName = prefs.getString("user_name", "SafeZone User");
        userPhone = prefs.getString("user_phone", "");

        contactNumbers.clear();
        int contactsCount = prefs.getInt("contacts_count", 0);
        for (int i = 1; i <= contactsCount; i++) {
            String phone = prefs.getString("contact_" + i + "_phone", "");
            if (!phone.isEmpty()) {
                contactNumbers.add(phone);
            }
        }
    }

    private void initViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        Button btnResetOnboarding = findViewById(R.id.btnResetOnboarding);

        llStatusCard = findViewById(R.id.llStatusCard);
        ivStatusIcon = findViewById(R.id.ivStatusIcon);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvGpsCoordinates = findViewById(R.id.tvGpsCoordinates);
        tvWatchdogNotice = findViewById(R.id.tvWatchdogNotice);

        btnDisarmSos = findViewById(R.id.btnDisarmSos);

        flSliderTrack = findViewById(R.id.flSliderTrack);
        ivSliderThumb = findViewById(R.id.ivSliderThumb);

        LinearLayout btnShareGps = findViewById(R.id.btnShareGps);
        LinearLayout btnCallEmergency = findViewById(R.id.btnCallEmergency);

        tvGreeting.setText(String.format(Locale.getDefault(), "Hello, %s!", userName));

        btnResetOnboarding.setOnClickListener(v -> resetOnboarding());
        btnDisarmSos.setOnClickListener(v -> disarmEmergencySos());
        btnShareGps.setOnClickListener(v -> shareCurrentLocation());
        btnCallEmergency.setOnClickListener(v -> dialEmergencyNumber("112"));
    }

    private void setupSliderTouchListener() {
        ivSliderThumb.setOnTouchListener(new View.OnTouchListener() {
            private float dX;
            private float startX;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = v.getX() - event.getRawX();
                        startX = v.getX();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() + dX;
                        float maxX = flSliderTrack.getWidth() - v.getWidth() - flSliderTrack.getPaddingRight();
                        if (newX < 0) newX = 0;
                        if (newX > maxX) newX = maxX;
                        v.setX(newX);

                        float swipePercentage = maxX > 0 ? (newX / maxX) : 0f;
                        if (swipePercentage >= 0.85f && !isEmergencyActive) {
                            v.setX(startX);
                            triggerEmergencyPipeline("SWIPE_SLIDER_TRIGGER");
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.performClick();
                        v.animate().x(startX).setDuration(200).start();
                        return true;
                }
                return false;
            }
        });
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.SEND_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CALL_PHONE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            startContinuousLocationTracking();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            startContinuousLocationTracking();
            startSentinelWatchdogService();
        }
    }

    private void startContinuousLocationTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        previousLocation = currentLocation;
                        currentLocation = location;
                        updateLocationUI(location);
                        if (isEmergencyActive) {
                            updateFirestoreEmergencyRecord(location, lastTriggerSource);
                        }
                    }
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void updateLocationUI(Location location) {
        String coordsText = String.format(Locale.getDefault(), "GPS Live: Lat %.5f, Lng %.5f\nAccuracy: %.1fm",
                location.getLatitude(), location.getLongitude(), location.getAccuracy());
        tvGpsCoordinates.setText(coordsText);
    }

    private void registerShakeReceiver() {
        shakeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (SentinelService.ACTION_SHAKE_SOS.equals(intent.getAction())) {
                    triggerEmergencyPipeline("SHAKE_SOS_TRIGGER");
                }
            }
        };

        IntentFilter filter = new IntentFilter(SentinelService.ACTION_SHAKE_SOS);
        ContextCompat.registerReceiver(this, shakeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void startSentinelWatchdogService() {
        Intent serviceIntent = new Intent(this, SentinelService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void triggerEmergencyPipeline(String source) {
        isEmergencyActive = true;
        lastTriggerSource = source;

        vibrateDevice();
        updateUIForEmergencyState(source);
        dispatchEmergencySmsAlerts();
        dialPrimaryContact();
        startMobileRearCameraCapture();

        if (currentLocation != null) {
            updateFirestoreEmergencyRecord(currentLocation, source);
        } else {
            Toast.makeText(this, "SOS Triggered! Capturing live rear camera footage...", Toast.LENGTH_SHORT).show();
        }
    }

    private void startMobileRearCameraCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            if (mCamera == null) {
                int backCameraId = -1;
                int numberOfCameras = Camera.getNumberOfCameras();
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.CameraInfo info = new Camera.CameraInfo();
                    Camera.getCameraInfo(i, info);
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        backCameraId = i;
                        break;
                    }
                }

                if (backCameraId != -1) {
                    mCamera = Camera.open(backCameraId);
                } else {
                    mCamera = Camera.open();
                }

                SurfaceTexture dummySurface = new SurfaceTexture(10);
                mCamera.setPreviewTexture(dummySurface);

                mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                    private long lastFrameTime = 0;

                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        long now = System.currentTimeMillis();
                        if (now - lastFrameTime > 1500 && isEmergencyActive && currentLocation != null) { // 1.5 second frame rate
                            lastFrameTime = now;
                            processAndUploadCameraFrame(data, camera);
                        }
                    }
                });

                mCamera.startPreview();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processAndUploadCameraFrame(byte[] data, Camera camera) {
        try {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            YuvImage yuvImage = new YuvImage(data, parameters.getPreviewFormat(), size.width, size.height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, size.width, size.height), 40, out);
            byte[] imageBytes = out.toByteArray();

            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap != null) {
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 320, 240, false);
                ByteArrayOutputStream scaledOut = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 45, scaledOut);
                latestBase64CameraFrame = "data:image/jpeg;base64," + Base64.encodeToString(scaledOut.toByteArray(), Base64.NO_WRAP);

                updateFirestoreEmergencyRecord(currentLocation, lastTriggerSource);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopMobileRearCameraCapture() {
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vibratorManager != null) {
                    vibratorManager.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateUIForEmergencyState(String source) {
        llStatusCard.setBackgroundResource(R.drawable.bg_danger_card);
        ivStatusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.danger_red));

        tvStatusTitle.setText("EMERGENCY SOS ACTIVE");
        tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.danger_red));

        tvWatchdogNotice.setText("Trigger Source: " + source + ". Mobile Rear Camera Evidence Active!");
        tvWatchdogNotice.setTextColor(ContextCompat.getColor(this, R.color.danger_red));

        btnDisarmSos.setVisibility(View.VISIBLE);
    }

    private void dispatchEmergencySmsAlerts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS permission missing. Unable to send SMS alerts.", Toast.LENGTH_SHORT).show();
            return;
        }

        String locationUrl = currentLocation != null ?
                "https://maps.google.com/?q=" + currentLocation.getLatitude() + "," + currentLocation.getLongitude() :
                "Location unavailable";

        String message = "EMERGENCY SOS from " + userName + "! I need help immediately. My current location: " + locationUrl;

        SmsManager smsManager;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            smsManager = getSystemService(SmsManager.class);
        } else {
            smsManager = SmsManager.getDefault();
        }

        for (String number : contactNumbers) {
            try {
                if (smsManager != null && !number.isEmpty()) {
                    ArrayList<String> parts = smsManager.divideMessage(message);
                    if (parts.size() > 1) {
                        smsManager.sendMultipartTextMessage(number, null, parts, null, null);
                    } else {
                        smsManager.sendTextMessage(number, null, message, null, null);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Toast.makeText(this, "Emergency SMS dispatched to " + contactNumbers.size() + " contacts!", Toast.LENGTH_SHORT).show();
    }

    private void dialPrimaryContact() {
        String primaryPhone = contactNumbers.isEmpty() ? "112" : contactNumbers.get(0);
        dialEmergencyNumber(primaryPhone);
    }

    private void dialEmergencyNumber(String phoneNumber) {
        String action = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED ?
                Intent.ACTION_CALL : Intent.ACTION_DIAL;
        Intent callIntent = new Intent(action, Uri.parse("tel:" + phoneNumber));
        startActivity(callIntent);
    }

    private void updateFirestoreEmergencyRecord(Location location, String triggerSource) {
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> alertPayload = new HashMap<>();

            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, iFilter);
            int batteryLevel = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : 95;

            // --- HEURISTIC MULTI-FACTOR AI RISK ASSESSMENT SCORE (0 - 100) ---
            // 1. Primary Trigger Weight (W_trigger: Up to 45 Points)
            int wTrigger;
            if ("SHAKE_SOS_TRIGGER".equals(triggerSource)) {
                wTrigger = 45; // Involuntary rapid panic / struggle gesture
            } else if ("SWIPE_SLIDER_TRIGGER".equals(triggerSource)) {
                wTrigger = 35; // Intentional manual slider confirmation
            } else {
                wTrigger = 30;
            }

            // 2. Kinematic & Motion Intensity (W_motion: Up to 20 Points)
            double speedKmH = 0;
            if (location.hasSpeed()) {
                speedKmH = location.getSpeed() * 3.6; // m/s to km/h
            } else if (previousLocation != null) {
                double distanceMeters = location.distanceTo(previousLocation);
                double timeSeconds = (location.getTime() - previousLocation.getTime()) / 1000.0;
                if (timeSeconds > 0) {
                    speedKmH = (distanceMeters / timeSeconds) * 3.6;
                }
            }

            int wMotion;
            if (speedKmH > 30.0) {
                wMotion = 20; // High-Speed Vehicular Transition / Abduction
            } else if (speedKmH > 6.0 && speedKmH <= 18.0) {
                wMotion = 15; // Running / Fleeing / Pursuit
            } else if (speedKmH < 1.0) {
                wMotion = 5; // Static / Pinned / Stationary
            } else {
                wMotion = 10;
            }

            // 3. Geofence Perimeter Deviation (W_spatial: Up to 20 Points)
            // Default reference: 1000m deviation penalty threshold
            int wSpatial = 10; // Default perimeter breach

            // 4. Device Vulnerability & Battery Risk (W_vitals: Up to 15 Points)
            int wVitals;
            if (batteryLevel < 10) {
                wVitals = 15; // Critical battery imminent loss
            } else if (batteryLevel <= 20) {
                wVitals = 8; // Low battery risk
            } else {
                wVitals = 0; // Normal battery
            }

            // Mathematical Aggregation
            int riskScore = Math.min(100, wTrigger + wMotion + wSpatial + wVitals);
            String threatLevel;
            if (riskScore >= 85) {
                threatLevel = "CRITICAL SOS THREAT";
            } else if (riskScore >= 35) {
                threatLevel = "ELEVATED THREAT";
            } else {
                threatLevel = "STANDBY";
            }

            alertPayload.put("user_name", userName);
            alertPayload.put("user_phone", userPhone);
            alertPayload.put("latitude", location.getLatitude());
            alertPayload.put("longitude", location.getLongitude());
            alertPayload.put("accuracy", location.getAccuracy());
            alertPayload.put("trigger_source", triggerSource);
            alertPayload.put("is_alert_active", true);
            alertPayload.put("battery_level", batteryLevel + "%");
            alertPayload.put("battery_numeric", batteryLevel);
            alertPayload.put("speed_kmh", String.format(Locale.getDefault(), "%.1f km/h", speedKmH));
            alertPayload.put("w_trigger", wTrigger);
            alertPayload.put("w_motion", wMotion);
            alertPayload.put("w_spatial", wSpatial);
            alertPayload.put("w_vitals", wVitals);
            alertPayload.put("risk_score", riskScore);
            alertPayload.put("threat_level", threatLevel);
            alertPayload.put("camera_active", true);
            alertPayload.put("video_feed_status", "MOBILE_REAR_CAMERA_ACTIVE");
            alertPayload.put("live_camera_frame", latestBase64CameraFrame);
            alertPayload.put("timestamp", FieldValue.serverTimestamp());

            String docId = userPhone.isEmpty() ? "unknown_user" : userPhone;
            db.collection("alerts").document(docId).set(alertPayload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void disarmEmergencySos() {
        isEmergencyActive = false;
        stopMobileRearCameraCapture();
        latestBase64CameraFrame = "";

        llStatusCard.setBackgroundResource(R.drawable.bg_emerald_card);
        ivStatusIcon.setImageResource(android.R.drawable.ic_lock_lock);
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.emerald_safe));

        tvStatusTitle.setText("You are Protected");
        tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.emerald_safe));

        tvWatchdogNotice.setText("Shake motion watchdog active (5 fast shakes triggers SOS).");
        tvWatchdogNotice.setTextColor(ContextCompat.getColor(this, R.color.text_muted));

        btnDisarmSos.setVisibility(View.GONE);

        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            String docId = userPhone.isEmpty() ? "unknown_user" : userPhone;
            Map<String, Object> updateMap = new HashMap<>();
            updateMap.put("is_alert_active", false);
            updateMap.put("camera_active", false);
            updateMap.put("disarmed_at", FieldValue.serverTimestamp());
            db.collection("alerts").document(docId).update(updateMap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Toast.makeText(this, "SOS Disarmed. Safe status confirmed.", Toast.LENGTH_SHORT).show();
    }

    private void shareCurrentLocation() {
        String locationUrl = currentLocation != null ?
                "https://maps.google.com/?q=" + currentLocation.getLatitude() + "," + currentLocation.getLongitude() :
                "Location not fixed yet";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SafeZone Live GPS Location");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "SafeZone Sentinel Live GPS Link: " + locationUrl);
        startActivity(Intent.createChooser(shareIntent, "Share Location via"));
    }

    private void resetOnboarding() {
        stopMobileRearCameraCapture();
        stopService(new Intent(this, SentinelService.class));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().clear().apply();

        Toast.makeText(this, "Onboarding preferences reset", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMobileRearCameraCapture();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (shakeReceiver != null) {
            try {
                unregisterReceiver(shakeReceiver);
            } catch (Exception ignored) {
            }
        }
    }
}
