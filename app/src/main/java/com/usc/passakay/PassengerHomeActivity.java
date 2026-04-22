package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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
            // Implement QR Scanning Logic here
        });

        // Load shuttles
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
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                shuttleList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Shuttle shuttle = child.getValue(Shuttle.class);
                    if (shuttle != null) {
                        // Create ShuttleItem with sample ETA and location
                        ShuttleItem item = new ShuttleItem(
                            String.valueOf(shuttle.getShuttleId()),
                            "Bus " + shuttle.getShuttleId(),
                            "Driver " + shuttle.getShuttleId(), 
                            shuttle.getPlateNumber(),
                            (int)(Math.random() * 10) + 1,
                            true,
                            10.3535,
                            123.9109
                        );
                        shuttleList.add(item);
                    }
                }

                // Add an offline shuttle for visual variety
                if (shuttleList.isEmpty()) {
                     shuttleList.add(new ShuttleItem("1", "Bus 1", "Juan Dela Cruz", "GWX 123", 5, true, 10.3521, 123.9123));
                }
                
                shuttleList.add(new ShuttleItem(
                    "offline",
                    "Bus " + (shuttleList.size() + 1),
                    "No Driver Assigned",
                    "---",
                    0,
                    false,
                    0, 0
                ));

                shuttleAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
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
