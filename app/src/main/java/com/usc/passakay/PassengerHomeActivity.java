package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class PassengerHomeActivity extends BaseActivity {

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

        shuttleAdapter = new ShuttleAdapter(this, shuttleList);
        recyclerShuttles.setAdapter(shuttleAdapter);

        // Waiting Status Toggle
        btnWaitingStatus.setOnClickListener(v -> toggleWaitingStatus());

        // QR Scan FAB
        fabScanQR.setOnClickListener(v -> {
            Toast.makeText(this, "Opening QR Scanner...", Toast.LENGTH_SHORT).show();
        });

        // Load all shuttles and distinguish between active/inactive
        loadShuttles();

        // Setup bottom nav
        setupBottomNav();
    }

    private void toggleWaitingStatus() {
        isWaiting = !isWaiting;
        if (isWaiting) {
            btnWaitingStatus.setText("WAITING");
            btnWaitingStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336)); // Red
            Toast.makeText(this, "Status: Waiting for shuttle", Toast.LENGTH_SHORT).show();
        } else {
            btnWaitingStatus.setText("NOT WAITING");
            btnWaitingStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2E7D32)); // Green
            Toast.makeText(this, "Status: Not waiting", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadShuttles() {
        // We load ALL shuttles so the passenger can see what's available vs what's offline (like Bus 4 in your ref)
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // POPULATE INITIAL DATA if database is empty
                    populateInitialData();
                    return;
                }

                shuttleList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Shuttle shuttle = child.getValue(Shuttle.class);
                    if (shuttle != null) {
                        // Map Firebase Shuttle to UI ShuttleItem
                        ShuttleItem item = new ShuttleItem(
                            String.valueOf(shuttle.getShuttleId()),
                            "Bus " + shuttle.getShuttleId(),
                            shuttle.isActive() ? "Driver Active" : "No Driver",
                            shuttle.getPlateNumber(),
                            shuttle.isActive() ? calculateETA(shuttle.getCurrentLat(), shuttle.getCurrentLng()) : 0,
                            shuttle.isActive(), // isAvailable
                            shuttle.getCurrentLat(),
                            shuttle.getCurrentLng()
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
        // Create 4 initial buses. We set one to Active so you can see the tracking UI immediately.
        db.child("shuttles").child("1").setValue(new Shuttle(1, "GWX-101"));
        db.child("shuttles").child("2").setValue(new Shuttle(2, "GWX-102"));
        db.child("shuttles").child("3").setValue(new Shuttle(3, "GWX-103"));
        
        // Let's make Bus 4 active with mock coordinates for demo purposes
        Shuttle activeBus = new Shuttle(4, "GWX-104");
        activeBus.setActive(true);
        activeBus.setCurrentLat(10.3521);
        activeBus.setCurrentLng(123.9123);
        activeBus.setCurrentPassengers(12);
        activeBus.setCapacity(30);
        activeBus.setLastUpdated("Just now");
        db.child("shuttles").child("4").setValue(activeBus);
    }

    private int calculateETA(double lat, double lng) {
        // Mock ETA calculation
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
