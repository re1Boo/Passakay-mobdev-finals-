package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class DriverDashboardActivity extends BaseActivity {

    private RecyclerView recyclerShuttles;
    private ShuttleAdapter shuttleAdapter;
    private List<ShuttleItem> shuttleList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Redirect to login if not logged in
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_driver_dashboard);

        // Setup RecyclerView
        recyclerShuttles = findViewById(R.id.recyclerShuttles);
        recyclerShuttles.setLayoutManager(new LinearLayoutManager(this));
        shuttleAdapter = new ShuttleAdapter(this, shuttleList);
        recyclerShuttles.setAdapter(shuttleAdapter);

        loadFakeShuttles();
        setupBottomNav();
    }

    // TODO: Replace with real Firebase data when ready
    // ShuttleItem(shuttleId, busName, driverName, plateNumber, eta,
    //             isAvailable, isStandby, driverLat, driverLng)
    private void loadFakeShuttles() {
        // Bus 1 – Deployed (available, not standby)
        shuttleList.add(new ShuttleItem(
                "1", "Bus 1", "John Doe", "ABC 1234",
                5, true, false, 10.3157, 123.8854));

        // Bus 2 – Standby (green button → tapping opens ShuttleStopsActivity)
        shuttleList.add(new ShuttleItem(
                "2", "Bus 2", "No driver", "DEF 5678",
                0, true, true, 10.3160, 123.8860));

        // Bus 3 – Deployed
        shuttleList.add(new ShuttleItem(
                "3", "Bus 3", "Jane Smith", "GHI 9012",
                10, true, false, 10.3200, 123.8900));

        // Bus 4 – Deployed (no driver but still shows yellow badge per design)
        shuttleList.add(new ShuttleItem(
                "4", "Bus 4", "No driver", "JKL 3456",
                0, true, false, 0, 0));

        shuttleAdapter.notifyDataSetChanged();
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_history) {
                // TODO: navigate to history screen
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }
}