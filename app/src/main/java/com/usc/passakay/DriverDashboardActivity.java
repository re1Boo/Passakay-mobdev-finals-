package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class DriverDashboardActivity extends BaseActivity {

    private RecyclerView recyclerShuttles;
    private ShuttleAdapter shuttleAdapter;
    private List<ShuttleItem> shuttleList = new ArrayList<>();
    private DatabaseReference db;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUserId = currentUser.getUid();

        setContentView(R.layout.activity_driver_dashboard);
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        checkIfAlreadyDriving();

        recyclerShuttles = findViewById(R.id.recyclerShuttles);
        recyclerShuttles.setLayoutManager(new LinearLayoutManager(this));
        
        // Fixed: Pass getSupportFragmentManager() as the third argument
        shuttleAdapter = new ShuttleAdapter(this, shuttleList, getSupportFragmentManager());
        recyclerShuttles.setAdapter(shuttleAdapter);

        loadShuttles();
        setupBottomNav();
    }

    private void checkIfAlreadyDriving() {
        db.child("shuttles").orderByChild("driverId").equalTo(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                Shuttle shuttle = child.getValue(Shuttle.class);
                                if (shuttle != null && "Deployed".equals(shuttle.getStatus())) {
                                    Intent intent = new Intent(DriverDashboardActivity.this, ShuttleStopActivity.class);
                                    intent.putExtra(ShuttleStopActivity.EXTRA_BUS_NAME, "Bus " + shuttle.getShuttleId());
                                    intent.putExtra("shuttleId", String.valueOf(shuttle.getShuttleId()));
                                    startActivity(intent);
                                    finish();
                                    return;
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadShuttles() {
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                shuttleList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Shuttle shuttle = child.getValue(Shuttle.class);
                    if (shuttle != null) {
                        boolean isStandby = "Standby".equals(shuttle.getStatus());
                        boolean isAvailable = !"Unavailable".equals(shuttle.getStatus());

                        ShuttleItem item = new ShuttleItem(
                                String.valueOf(shuttle.getShuttleId()),
                                "Bus " + shuttle.getShuttleId(),
                                shuttle.getDriverName(),
                                shuttle.getPlateNumber(),
                                0,
                                isAvailable,
                                isStandby,
                                10.3541, 123.9115 // Default USC
                        );
                        shuttleList.add(item);
                    }
                }
                shuttleAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }
}
