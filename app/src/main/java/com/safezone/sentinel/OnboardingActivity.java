package com.safezone.sentinel;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "SafeZonePrefs";

    private int currentStep = 1;

    private TextView tvStepTitle;
    private TextView tvStepSubheader;
    private ProgressBar pbStepProgress;

    private LinearLayout llStep1;
    private LinearLayout llStep2;
    private LinearLayout llStep3;

    private EditText etUserName;
    private EditText etUserPhone;

    private LinearLayout llContactsContainer;
    private Button btnAddContact;

    private EditText etHomeZone;
    private EditText etCampusZone;
    private Button btnPinHomeGps;
    private Button btnPinCampusGps;

    private Button btnNextStep;

    private FusedLocationProviderClient fusedLocationClient;
    private double homeLat = 0.0, homeLng = 0.0;
    private double campusLat = 0.0, campusLng = 0.0;

    private final List<View> contactViewsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        setupStep1();
        setupStep2DefaultContacts();
        setupListeners();
        setupBackPressedHandler();
        updateStepUI();
    }

    private void initViews() {
        tvStepTitle = findViewById(R.id.tvStepTitle);
        tvStepSubheader = findViewById(R.id.tvStepSubheader);
        pbStepProgress = findViewById(R.id.pbStepProgress);

        llStep1 = findViewById(R.id.llStep1);
        llStep2 = findViewById(R.id.llStep2);
        llStep3 = findViewById(R.id.llStep3);

        etUserName = findViewById(R.id.etUserName);
        etUserPhone = findViewById(R.id.etUserPhone);

        llContactsContainer = findViewById(R.id.llContactsContainer);
        btnAddContact = findViewById(R.id.btnAddContact);

        etHomeZone = findViewById(R.id.etHomeZone);
        etCampusZone = findViewById(R.id.etCampusZone);
        btnPinHomeGps = findViewById(R.id.btnPinHomeGps);
        btnPinCampusGps = findViewById(R.id.btnPinCampusGps);

        btnNextStep = findViewById(R.id.btnNextStep);
    }

    private void setupStep1() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etUserName.setText(prefs.getString("user_name", ""));
        etUserPhone.setText(prefs.getString("user_phone", ""));
    }

    private void setupStep2DefaultContacts() {
        llContactsContainer.removeAllViews();
        contactViewsList.clear();

        addContactSlot("Father", "");
        addContactSlot("Mother", "");
        addContactSlot("Emergency Services", "112");
    }

    private void addContactSlot(String defaultName, String defaultPhone) {
        if (contactViewsList.size() >= 5) {
            Toast.makeText(this, "Maximum 5 emergency contacts allowed", Toast.LENGTH_SHORT).show();
            return;
        }

        View itemView = LayoutInflater.from(this).inflate(R.layout.item_contact_input, llContactsContainer, false);
        EditText etContactName = itemView.findViewById(R.id.etContactName);
        EditText etContactPhone = itemView.findViewById(R.id.etContactPhone);
        ImageButton btnRemoveContact = itemView.findViewById(R.id.btnRemoveContact);

        etContactName.setText(defaultName);
        etContactPhone.setText(defaultPhone);

        btnRemoveContact.setOnClickListener(v -> {
            if (contactViewsList.size() <= 3) {
                Toast.makeText(OnboardingActivity.this, "Minimum 3 emergency contacts required", Toast.LENGTH_SHORT).show();
            } else {
                llContactsContainer.removeView(itemView);
                contactViewsList.remove(itemView);
                reindexContactLabels();
            }
        });

        llContactsContainer.addView(itemView);
        contactViewsList.add(itemView);
        reindexContactLabels();
    }

    private void reindexContactLabels() {
        for (int i = 0; i < contactViewsList.size(); i++) {
            View view = contactViewsList.get(i);
            TextView tvLabel = view.findViewById(R.id.tvContactLabel);
            if (tvLabel != null) {
                tvLabel.setText("CONTACT #" + (i + 1));
            }
        }
    }

    private void setupListeners() {
        btnAddContact.setOnClickListener(v -> addContactSlot("", ""));

        btnPinHomeGps.setOnClickListener(v -> pinCurrentLocation(true));
        btnPinCampusGps.setOnClickListener(v -> pinCurrentLocation(false));

        btnNextStep.setOnClickListener(v -> {
            if (currentStep == 1) {
                if (validateStep1()) {
                    currentStep = 2;
                    updateStepUI();
                }
            } else if (currentStep == 2) {
                if (validateStep2()) {
                    currentStep = 3;
                    updateStepUI();
                }
            } else if (currentStep == 3) {
                if (validateStep3()) {
                    completeOnboarding();
                }
            }
        });
    }

    private void pinCurrentLocation(boolean isHome) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                String gpsStr = String.format(Locale.getDefault(), "Lat: %.5f, Lng: %.5f", location.getLatitude(), location.getLongitude());
                if (isHome) {
                    homeLat = location.getLatitude();
                    homeLng = location.getLongitude();
                    etHomeZone.setText(gpsStr);
                    Toast.makeText(this, "Home GPS Pinned Successfully!", Toast.LENGTH_SHORT).show();
                } else {
                    campusLat = location.getLatitude();
                    campusLng = location.getLongitude();
                    etCampusZone.setText(gpsStr);
                    Toast.makeText(this, "Campus GPS Pinned Successfully!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Unable to fix GPS. Please ensure Location is enabled.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep > 1) {
                    currentStep--;
                    updateStepUI();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private boolean validateStep1() {
        String name = etUserName.getText().toString().trim();
        String phone = etUserPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etUserName.setError("Please enter your name");
            etUserName.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(phone) || phone.length() < 10) {
            etUserPhone.setError("Please enter a valid 10-digit phone number");
            etUserPhone.requestFocus();
            return false;
        }

        return true;
    }

    private boolean validateStep2() {
        if (contactViewsList.size() < 3) {
            Toast.makeText(this, "Please configure at least 3 emergency contacts", Toast.LENGTH_LONG).show();
            return false;
        }

        for (int i = 0; i < contactViewsList.size(); i++) {
            View itemView = contactViewsList.get(i);
            EditText etName = itemView.findViewById(R.id.etContactName);
            EditText etPhone = itemView.findViewById(R.id.etContactPhone);

            String cName = etName.getText().toString().trim();
            String cPhone = etPhone.getText().toString().trim();

            if (TextUtils.isEmpty(cName)) {
                etName.setError("Name required");
                etName.requestFocus();
                return false;
            }

            if (TextUtils.isEmpty(cPhone) || cPhone.length() < 3) {
                etPhone.setError("Valid number required (min 3 digits)");
                etPhone.requestFocus();
                return false;
            }
        }

        return true;
    }

    private boolean validateStep3() {
        String homeZone = etHomeZone.getText().toString().trim();
        String campusZone = etCampusZone.getText().toString().trim();

        if (TextUtils.isEmpty(homeZone)) {
            etHomeZone.setError("Home safe zone required");
            etHomeZone.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(campusZone)) {
            etCampusZone.setError("Campus / workplace safe zone required");
            etCampusZone.requestFocus();
            return false;
        }

        return true;
    }

    private void updateStepUI() {
        llStep1.setVisibility(View.GONE);
        llStep2.setVisibility(View.GONE);
        llStep3.setVisibility(View.GONE);

        if (currentStep == 1) {
            llStep1.setVisibility(View.VISIBLE);
            tvStepTitle.setText("Step 1 of 3");
            tvStepSubheader.setText("Personal Details");
            pbStepProgress.setProgress(1);
            btnNextStep.setText("Next: Emergency Contacts");
        } else if (currentStep == 2) {
            llStep2.setVisibility(View.VISIBLE);
            tvStepTitle.setText("Step 2 of 3");
            tvStepSubheader.setText("Emergency Contacts");
            pbStepProgress.setProgress(2);
            btnNextStep.setText("Next: Safe Zones");
        } else if (currentStep == 3) {
            llStep3.setVisibility(View.VISIBLE);
            tvStepTitle.setText("Step 3 of 3");
            tvStepSubheader.setText("Safe Zones");
            pbStepProgress.setProgress(3);
            btnNextStep.setText("Complete Onboarding & Protect");
        }
    }

    private void completeOnboarding() {
        String userName = etUserName.getText().toString().trim();
        String userPhone = etUserPhone.getText().toString().trim();
        String homeZone = etHomeZone.getText().toString().trim();
        String campusZone = etCampusZone.getText().toString().trim();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("user_name", userName);
        editor.putString("user_phone", userPhone);
        editor.putString("home_zone", homeZone);
        editor.putString("campus_zone", campusZone);
        editor.putFloat("home_lat", (float) homeLat);
        editor.putFloat("home_lng", (float) homeLng);
        editor.putFloat("campus_lat", (float) campusLat);
        editor.putFloat("campus_lng", (float) campusLng);
        editor.putInt("contacts_count", contactViewsList.size());

        List<Map<String, String>> contactsListForFirestore = new ArrayList<>();

        for (int i = 0; i < contactViewsList.size(); i++) {
            View itemView = contactViewsList.get(i);
            EditText etName = itemView.findViewById(R.id.etContactName);
            EditText etPhone = itemView.findViewById(R.id.etContactPhone);

            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            editor.putString("contact_" + (i + 1) + "_name", name);
            editor.putString("contact_" + (i + 1) + "_phone", phone);

            Map<String, String> contactMap = new HashMap<>();
            contactMap.put("name", name);
            contactMap.put("phone", phone);
            contactsListForFirestore.add(contactMap);
        }

        editor.putBoolean("is_onboarded", true);
        editor.apply();

        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> userPayload = new HashMap<>();
            userPayload.put("user_name", userName);
            userPayload.put("user_phone", userPhone);
            userPayload.put("home_zone", homeZone);
            userPayload.put("campus_zone", campusZone);
            userPayload.put("home_lat", homeLat);
            userPayload.put("home_lng", homeLng);
            userPayload.put("campus_lat", campusLat);
            userPayload.put("campus_lng", campusLng);
            userPayload.put("contacts_count", contactViewsList.size());
            userPayload.put("contacts", contactsListForFirestore);
            userPayload.put("updated_at", FieldValue.serverTimestamp());

            db.collection("users").document(userPhone).set(userPayload);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Toast.makeText(this, "SafeZone Onboarding Complete!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(OnboardingActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
