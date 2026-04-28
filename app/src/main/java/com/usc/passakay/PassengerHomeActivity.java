package com.usc.passakay;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.List;

public class PassengerHomeActivity extends BaseActivity {

    private static final double USC_LAT = 10.3541;
    private static final double USC_LNG = 123.9115;

    private RecyclerView recyclerShuttles;
    private ShuttleAdapter shuttleAdapter;
    private List<ShuttleItem> shuttleList = new ArrayList<>();
    private DatabaseReference db;
    private MaterialButton btnWaitingStatus;
    private boolean isWaiting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_home);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // UI Elements
        btnWaitingStatus = findViewById(R.id.btnWaitingStatus);
        FloatingActionButton fabScanQR = findViewById(R.id.fabScanQR);

        // Setup RecyclerView
        recyclerShuttles = findViewById(R.id.recyclerShuttles);
        recyclerShuttles.setLayoutManager(new LinearLayoutManager(this));

        // ✅ Pass getSupportFragmentManager() so maps can load
        shuttleAdapter = new ShuttleAdapter(this, shuttleList, getSupportFragmentManager());
        recyclerShuttles.setAdapter(shuttleAdapter);

        // Waiting Status Toggle
        btnWaitingStatus.setOnClickListener(v -> toggleWaitingStatus());

        // QR Scan FAB
        fabScanQR.setOnClickListener(v -> startQRScanner());

        // Load all shuttles
        loadShuttles();

        // Setup bottom nav
        setupBottomNav();
    }

    private void startQRScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan a shuttle stop QR code");
        integrator.setCameraId(0);  // Use a specific camera of the device
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
            } else {
                handleQRResult(result.getContents());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleQRResult(String contents) {
        // Assume the QR code contains the stop ID or name
        Toast.makeText(this, "Scanned: " + contents, Toast.LENGTH_LONG).show();
        
        // Example: Auto-toggle waiting status if they scan a stop
        if (!isWaiting) {
            toggleWaitingStatus();
        }
        
        // You could also log this to the database
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.child("users").child(uid).child("lastScannedStop").setValue(contents);
    }

    private void toggleWaitingStatus() {
        isWaiting = !isWaiting;
        if (isWaiting) {
            btnWaitingStatus.setText("WAITING");
            btnWaitingStatus.setBackgroundTintList(ColorStateList.valueOf(0xFFF44336)); // Red
            updateWaitingStatusInDB(true);
            Toast.makeText(this, "Status: Waiting for shuttle", Toast.LENGTH_SHORT).show();
        } else {
            btnWaitingStatus.setText("NOT WAITING");
            btnWaitingStatus.setBackgroundTintList(ColorStateList.valueOf(0xFFFFEA08)); // Yellow
            updateWaitingStatusInDB(false);
            Toast.makeText(this, "Status: Not waiting", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateWaitingStatusInDB(boolean waiting) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.child("users").child(uid).child("isWaiting").setValue(waiting);
    }

    private void loadShuttles() {
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    populateInitialData();
                    return;
                }

                shuttleList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Shuttle shuttle = child.getValue(Shuttle.class);
                    if (shuttle != null) {
                        double lat = (shuttle.getCurrentLat() != 0) ? shuttle.getCurrentLat() : USC_LAT;
                        double lng = (shuttle.getCurrentLng() != 0) ? shuttle.getCurrentLng() : USC_LNG;

                        ShuttleItem item = new ShuttleItem(
                            String.valueOf(shuttle.getShuttleId()),
                            "Bus " + shuttle.getShuttleId(),
                            shuttle.getDriverName() != null ? shuttle.getDriverName() : "No Driver",
                            shuttle.getPlateNumber(),
                            shuttle.isActive() ? calculateETA(lat, lng) : 0,
                            shuttle.isActive(),
                            lat,
                            lng
                        );
                        item.setCurrentPassengers(shuttle.getCurrentPassengers());
                        item.setCapacity(shuttle.getCapacity() > 0 ? shuttle.getCapacity() : 30);
                        item.setLastUpdated(shuttle.getLastUpdated() != null ? shuttle.getLastUpdated() : "Offline");
                        
                        shuttleList.add(item);
                    }
                }
                shuttleAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void populateInitialData() {
        db.child("shuttles").child("1").setValue(new Shuttle(1, "GWX-101"));
        db.child("shuttles").child("2").setValue(new Shuttle(2, "GWX-102"));
        db.child("shuttles").child("3").setValue(new Shuttle(3, "GWX-103"));
    }

    private int calculateETA(double lat, double lng) {
        if (lat == 0) return 0;
        return (int)(Math.random() * 8) + 2;
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_history) {
                return true;
            } else if (id == R.id.nav_profile) {
                Intent intent = new Intent(this, ProfileActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }
}
