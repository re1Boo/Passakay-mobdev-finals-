package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ShuttleStopActivity extends BaseActivity implements OnMapReadyCallback {

    public static final String EXTRA_BUS_NAME   = "extra_bus_name";
    public static final String EXTRA_DRIVER_LAT = "extra_driver_lat";
    public static final String EXTRA_DRIVER_LNG = "extra_driver_lng";

    private static final double DEFAULT_LAT = 10.3541;
    private static final double DEFAULT_LNG = 123.9115;

    private MapView mapView;
    private GoogleMap googleMap;
    private double driverLat, driverLng;
    private String busName;
    private String shuttleId;

    private RecyclerView recyclerStops;
    private StopAdapter stopAdapter;
    private final List<StopItem> stopList = new ArrayList<>();
    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shuttle_stop);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        busName   = getIntent().getStringExtra(EXTRA_BUS_NAME);
        driverLat = getIntent().getDoubleExtra(EXTRA_DRIVER_LAT, DEFAULT_LAT);
        driverLng = getIntent().getDoubleExtra(EXTRA_DRIVER_LNG, DEFAULT_LNG);
        shuttleId = getIntent().getStringExtra("shuttleId");

        mapView = findViewById(R.id.mapViewStops);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        recyclerStops = findViewById(R.id.recyclerStops);
        recyclerStops.setLayoutManager(new LinearLayoutManager(this));
        stopAdapter = new StopAdapter(this, stopList);
        recyclerStops.setAdapter(stopAdapter);

        Button btnStopDriving = findViewById(R.id.btnStopDriving);
        btnStopDriving.setOnClickListener(v -> stopDriving());

        loadStopsFromFirebase();
        setupBottomNav();
    }

    private void stopDriving() {
        if (shuttleId == null) return;
        
        DatabaseReference shuttleRef = db.child("shuttles").child(shuttleId);
        shuttleRef.child("status").setValue("Standby");
        shuttleRef.child("driverId").setValue("");
        shuttleRef.child("driverName").setValue("No driver");

        Intent intent = new Intent(this, DriverDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadStopsFromFirebase() {
        db.child("shuttleStops").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stopList.clear();
                for (DataSnapshot stopSnapshot : snapshot.getChildren()) {
                    ShuttleStop stop = stopSnapshot.getValue(ShuttleStop.class);
                    if (stop != null) {
                        int waiting = 0; // Placeholder
                        stopList.add(new StopItem(
                                stop.getStopName(),
                                waiting,
                                324 // Placeholder distance
                        ));
                    }
                }
                stopAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ShuttleStopActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;
        MapsInitializer.initialize(this);

        LatLng shuttleLoc = new LatLng(driverLat, driverLng);
        googleMap.addMarker(new MarkerOptions()
                .position(shuttleLoc)
                .title(busName != null ? busName : "Shuttle")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(shuttleLoc, 16));
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // If driving, staying in this activity is preferred when "Home" is tapped,
                // but if they are already here, we do nothing or re-init.
                return true;
            } else if (id == R.id.nav_history) {
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override protected void onResume()  { super.onResume();  mapView.onResume(); }
    @Override protected void onPause()   { super.onPause();   mapView.onPause();  }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy();}
    @Override public    void onLowMemory()          { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        mapView.onSaveInstanceState(out);
    }
}