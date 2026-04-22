package com.usc.passakay;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * DRIVER HOME ACTIVITY - Initial Setup
 * This activity handles the driver's ability to select a shuttle and start/end a trip.
 * 
 * TO THE NEXT DEVELOPER:
 * 1. IMPLEMENT REAL GPS: Use FusedLocationProviderClient to get real-time location.
 * 2. FOREGROUND SERVICE: Move location updates to a Foreground Service so it keeps 
 *    tracking even if the app is minimized.
 * 3. PERMISSIONS: Ensure ACCESS_FINE_LOCATION and POST_NOTIFICATIONS (for Android 13+) 
 *    permissions are handled.
 */
public class DriverHomeActivity extends BaseActivity {

    private Spinner spinnerShuttles;
    private MaterialButton btnStartTrip, btnEndTrip;
    private LinearLayout layoutSelection, layoutActiveTrip;
    private TextView tvActiveBus;
    
    private DatabaseReference db;
    private String currentUserId;
    private List<Shuttle> availableShuttles = new ArrayList<>();
    private String selectedShuttleId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_home);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        currentUserId = FirebaseAuth.getInstance().getUid();

        // Initialize UI
        spinnerShuttles = findViewById(R.id.spinnerShuttles);
        btnStartTrip = findViewById(R.id.btnStartTrip);
        btnEndTrip = findViewById(R.id.btnEndTrip);
        layoutSelection = findViewById(R.id.layoutSelection);
        layoutActiveTrip = findViewById(R.id.layoutActiveTrip);
        tvActiveBus = findViewById(R.id.tvActiveBus);

        // 1. Initialize starter shuttles in DB if they don't exist
        initializeStarterShuttles();

        // 2. Load shuttles for the Spinner
        loadShuttlesIntoSpinner();

        // 3. Handle Start Trip
        btnStartTrip.setOnClickListener(v -> startTrip());

        // 4. Handle End Trip
        btnEndTrip.setOnClickListener(v -> endTrip());
    }

    private void initializeStarterShuttles() {
        db.child("shuttles").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Add 3 static starter shuttles
                    db.child("shuttles").child("1").setValue(new Shuttle(1, "GWX-101"));
                    db.child("shuttles").child("2").setValue(new Shuttle(2, "GWX-102"));
                    db.child("shuttles").child("3").setValue(new Shuttle(3, "GWX-103"));
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void loadShuttlesIntoSpinner() {
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                availableShuttles.clear();
                List<String> busNames = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Shuttle s = child.getValue(Shuttle.class);
                    if (s != null && (!s.isActive() || (s.getCurrentDriverId() != null && s.getCurrentDriverId().equals(currentUserId)))) {
                        availableShuttles.add(s);
                        busNames.add("Bus " + s.getShuttleId() + " (" + s.getPlateNumber() + ")");
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(DriverHomeActivity.this, 
                        android.R.layout.simple_spinner_item, busNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerShuttles.setAdapter(adapter);
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void startTrip() {
        int pos = spinnerShuttles.getSelectedItemPosition();
        if (pos < 0) return;

        Shuttle selected = availableShuttles.get(pos);
        selectedShuttleId = String.valueOf(selected.getShuttleId());

        // Update Firebase: This is the critical link to the Passenger Side
        DatabaseReference shuttleRef = db.child("shuttles").child(selectedShuttleId);
        shuttleRef.child("active").setValue(true);
        shuttleRef.child("currentDriverId").setValue(currentUserId);
        shuttleRef.child("lastUpdated").setValue("Just started");
        
        // MOCK LOCATION: NEXT DEVELOPER - replace this with real FusedLocationProvider updates
        shuttleRef.child("currentLat").setValue(10.3521); 
        shuttleRef.child("currentLng").setValue(123.9123);

        showActiveUI(selected.getShuttleId());
        Toast.makeText(this, "Trip Started! Passengers can now see you.", Toast.LENGTH_SHORT).show();
    }

    private void endTrip() {
        if (selectedShuttleId == null) return;

        // Update Firebase: Stops the shuttle from appearing on the Passenger side
        DatabaseReference shuttleRef = db.child("shuttles").child(selectedShuttleId);
        shuttleRef.child("active").setValue(false);
        shuttleRef.child("currentDriverId").setValue(null);

        showSelectionUI();
        selectedShuttleId = null;
        Toast.makeText(this, "Trip Ended.", Toast.LENGTH_SHORT).show();
    }

    private void showActiveUI(int busId) {
        layoutSelection.setVisibility(View.GONE);
        layoutActiveTrip.setVisibility(View.VISIBLE);
        tvActiveBus.setText("Active: Bus " + busId);
    }

    private void showSelectionUI() {
        layoutSelection.setVisibility(View.VISIBLE);
        layoutActiveTrip.setVisibility(View.GONE);
    }
}
