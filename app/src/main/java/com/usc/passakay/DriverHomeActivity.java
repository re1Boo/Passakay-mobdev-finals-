package com.usc.passakay;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
 * DRIVER HOME ACTIVITY
 * This activity handles the driver's ability to select a shuttle and end a trip.
 * The selection is now handled via RecyclerView cards.
 */
public class DriverHomeActivity extends BaseActivity {

    private RecyclerView recyclerShuttles;
    private ShuttleAdapter shuttleAdapter;
    private MaterialButton btnEndTrip;
    private LinearLayout layoutSelection, layoutActiveTrip;
    private TextView tvActiveBus;
    
    private DatabaseReference db;
    private String currentUserId;
    private List<ShuttleItem> shuttleList = new ArrayList<>();
    private String selectedShuttleId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_home);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        currentUserId = FirebaseAuth.getInstance().getUid();

        // Initialize UI
        recyclerShuttles = findViewById(R.id.recyclerShuttles);
        recyclerShuttles.setLayoutManager(new LinearLayoutManager(this));
        
        // Fixed: Pass getSupportFragmentManager() as the third argument
        shuttleAdapter = new ShuttleAdapter(this, shuttleList, getSupportFragmentManager());

        recyclerShuttles.setAdapter(shuttleAdapter);

        btnEndTrip = findViewById(R.id.btnEndTrip);
        layoutSelection = findViewById(R.id.layoutSelection);
        layoutActiveTrip = findViewById(R.id.layoutActiveTrip);
        tvActiveBus = findViewById(R.id.tvActiveBus);

        // 1. Initialize starter shuttles in DB if they don't exist
        initializeStarterShuttles();

        // 2. Load shuttles for the list
        loadShuttles();

        // 3. Handle End Trip
        btnEndTrip.setOnClickListener(v -> endTrip());
        
        checkIfAlreadyDriving();
    }

    private void initializeStarterShuttles() {
        db.child("shuttles").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    db.child("shuttles").child("1").setValue(new Shuttle(1, "GWX-101"));
                    db.child("shuttles").child("2").setValue(new Shuttle(2, "GWX-102"));
                    db.child("shuttles").child("3").setValue(new Shuttle(3, "GWX-103"));
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadShuttles() {
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                shuttleList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Shuttle s = child.getValue(Shuttle.class);
                    if (s != null) {
                        boolean isStandby = "Standby".equals(s.getStatus());
                        boolean isAvailable = !"Unavailable".equals(s.getStatus());

                        ShuttleItem item = new ShuttleItem(
                                String.valueOf(s.getShuttleId()),
                                "Bus " + s.getShuttleId(),
                                s.getDriverName(),
                                s.getPlateNumber(),
                                0,
                                isAvailable,
                                isStandby,
                                10.3541, 123.9115
                        );
                        shuttleList.add(item);
                    }
                }
                shuttleAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void checkIfAlreadyDriving() {
        db.child("shuttles").orderByChild("currentDriverId").equalTo(currentUserId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                Shuttle shuttle = child.getValue(Shuttle.class);
                                if (shuttle != null && shuttle.isActive()) {
                                    selectedShuttleId = String.valueOf(shuttle.getShuttleId());
                                    showActiveUI(shuttle.getShuttleId());
                                    return;
                                }
                            }
                        }
                        showSelectionUI();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void endTrip() {
        if (selectedShuttleId == null) return;

        DatabaseReference shuttleRef = db.child("shuttles").child(selectedShuttleId);
        shuttleRef.child("active").setValue(false);
        shuttleRef.child("currentDriverId").setValue(null);
        shuttleRef.child("status").setValue("Standby");
        shuttleRef.child("driverName").setValue("No driver");
        shuttleRef.child("driverId").setValue("");

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
