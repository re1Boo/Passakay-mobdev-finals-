package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
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
    private TextView tvAnnouncement;

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

        tvAnnouncement = findViewById(R.id.tvAnnouncement);
        checkIfAlreadyDriving();

        recyclerShuttles = findViewById(R.id.recyclerShuttles);
        recyclerShuttles.setLayoutManager(new LinearLayoutManager(this));
        // Fixed: Pass getSupportFragmentManager() to match the ShuttleAdapter constructor
        shuttleAdapter = new ShuttleAdapter(this, shuttleList, getSupportFragmentManager());
        recyclerShuttles.setAdapter(shuttleAdapter);

        loadShuttles();
        loadAnnouncements();
        
        findViewById(R.id.cardAnnouncement).setOnClickListener(v -> showAnnouncementHistory());
        
        setupBottomNav();
    }

    private void showAnnouncementHistory() {
        db.child("announcements").child("history").limitToLast(10).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                java.util.List<java.util.Map<String, Object>> historyData = new java.util.ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) ds.getValue();
                    if (map != null) historyData.add(0, map);
                }
                AnnouncementHistoryAdapter adapter = new AnnouncementHistoryAdapter(DriverDashboardActivity.this, historyData, null);
                
                RecyclerView rv = new RecyclerView(DriverDashboardActivity.this);
                rv.setLayoutManager(new LinearLayoutManager(DriverDashboardActivity.this));
                rv.setAdapter(adapter);
                rv.setPadding(20, 20, 20, 20);

                new android.app.AlertDialog.Builder(DriverDashboardActivity.this)
                        .setTitle("Recent Announcements")
                        .setView(rv)
                        .setPositiveButton("Close", null)
                        .show();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadAnnouncements() {
        db.child("announcements").child("current").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String message = snapshot.child("message").getValue(String.class);
                    String priority = snapshot.child("priority").getValue(String.class);
                    Long expiresAt = snapshot.child("expiresAt").getValue(Long.class);

                    // Check for expiry
                    if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                        findViewById(R.id.cardAnnouncement).setVisibility(android.view.View.GONE);
                        return;
                    }

                    tvAnnouncement.setText(message);
                    tvAnnouncement.setSelected(true);
                    findViewById(R.id.cardAnnouncement).setVisibility(android.view.View.VISIBLE);

                    // Show "NEW" badge if less than 2 minutes old
                    long currentTimestamp = snapshot.child("timestamp").getValue(Long.class) != null ? snapshot.child("timestamp").getValue(Long.class) : 0;
                    if (currentTimestamp > 0 && (System.currentTimeMillis() - currentTimestamp) < (2 * 60 * 1000)) {
                        findViewById(R.id.tvNewBadge).setVisibility(android.view.View.VISIBLE);
                    } else {
                        findViewById(R.id.tvNewBadge).setVisibility(android.view.View.GONE);
                    }

                    // Show relative time in the banner
                    if (currentTimestamp > 0) {
                        TextView tvTime = findViewById(R.id.tvAnnouncementTime);
                        tvTime.setText(android.text.format.DateUtils.getRelativeTimeSpanString(currentTimestamp, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS));
                    }

                    // Change color based on priority
                    androidx.cardview.widget.CardView card = findViewById(R.id.cardAnnouncement);
                    if ("warning".equals(priority)) {
                        card.setCardBackgroundColor(android.graphics.Color.parseColor("#FFE0B2"));
                    } else if ("emergency".equals(priority)) {
                        card.setCardBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"));
                        
                        android.view.animation.Animation pulse = new android.view.animation.AlphaAnimation(1.0f, 0.6f);
                        pulse.setDuration(800);
                        pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
                        pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
                        card.startAnimation(pulse);
                    } else {
                        card.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF9C4"));
                        card.clearAnimation();
                    }
                } else {
                    findViewById(R.id.cardAnnouncement).setVisibility(android.view.View.GONE);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
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
