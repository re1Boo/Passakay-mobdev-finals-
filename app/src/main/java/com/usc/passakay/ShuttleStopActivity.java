package com.usc.passakay;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ShuttleStopActivity extends BaseActivity implements OnMapReadyCallback {

    private static final String TAG = "ShuttleStopActivity";
    public static final String EXTRA_BUS_NAME   = "extra_bus_name";
    public static final String EXTRA_DRIVER_LAT = "extra_driver_lat";
    public static final String EXTRA_DRIVER_LNG = "extra_driver_lng";

    private MapView mapView;
    private GoogleMap googleMap;
    private double driverLat, driverLng;
    private String busName, shuttleId;

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
        driverLat = getIntent().getDoubleExtra(EXTRA_DRIVER_LAT, 10.3541);
        driverLng = getIntent().getDoubleExtra(EXTRA_DRIVER_LNG, 123.9115);
        shuttleId = getIntent().getStringExtra("shuttleId");

        mapView = findViewById(R.id.mapViewStops);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        recyclerStops = findViewById(R.id.recyclerStops);
        recyclerStops.setLayoutManager(new LinearLayoutManager(this));
        stopAdapter = new StopAdapter(this, stopList);
        recyclerStops.setAdapter(stopAdapter);

        findViewById(R.id.btnStopDriving).setOnClickListener(v -> stopDriving());

        loadData();
        setupBottomNav();
    }

    private void loadData() {
        // Step 1: Listen for Stops
        db.child("shuttleStops").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot stopSnapshot) {
                stopList.clear();
                for (DataSnapshot ds : stopSnapshot.getChildren()) {
                    ShuttleStop stop = ds.getValue(ShuttleStop.class);
                    if (stop != null) {
                        int distance = calculateDistance(driverLat, driverLng, stop.getLatitude(), stop.getLongitude());
                        stopList.add(new StopItem(stop.getStopName(), 0, distance));
                    }
                }

                // Sort stops logically: Portal -> Dorm -> PE -> ... -> Bunzel
                stopList.sort((s1, s2) -> Integer.compare(getRouteOrder(s1.getStopName()), getRouteOrder(s2.getStopName())));

                // Step 2: Listen for Users and update the counts in stopList
                loadWaitingCounts();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private int calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, results);
        return (int) results[0];
    }

    private int getRouteOrder(String stopName) {
        if (stopName == null) return 99;
        String name = stopName.toLowerCase();
        if (name.contains("bunzel")) return 1;
        if (name.contains("portal")) return 2;
        if (name.contains("dorm"))   return 3;
        if (name.contains("pe"))     return 4;
        if (name.contains("shcp"))   return 5;
        if (name.contains("lrc"))    return 6;
        if (name.contains("mr"))     return 7;
        if (name.contains("safad"))  return 8;
        if (name.contains("chapel")) return 9;
        if (name.contains("among"))  return 10;
        return 99;
    }

    private void loadWaitingCounts() {
        db.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                // Reset counts for all stops before recalculating
                for (StopItem item : stopList) item.setWaitingCount(0);

                for (DataSnapshot ds : userSnapshot.getChildren()) {
                    Boolean isWaiting = ds.child("isWaiting").getValue(Boolean.class);
                    String userStop = ds.child("lastScannedStop").getValue(String.class);

                    if (isWaiting != null && isWaiting && userStop != null) {
                        for (StopItem stopItem : stopList) {
                            if (isMatch(userStop, stopItem.getStopName())) {
                                stopItem.setWaitingCount(stopItem.getWaitingCount() + 1);
                                Log.d(TAG, "Matched passenger to stop: " + stopItem.getStopName());
                            }
                        }
                    }
                }
                stopAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private boolean isMatch(String s1, String s2) {
        if (s1 == null || s2 == null) return false;
        String v1 = s1.toLowerCase().trim();
        String v2 = s2.toLowerCase().trim();
        return v1.contains(v2) || v2.contains(v1);
    }

    private void stopDriving() {
        if (shuttleId == null) return;
        DatabaseReference shuttleRef = db.child("shuttles").child(shuttleId);
        shuttleRef.child("status").setValue("Standby");
        shuttleRef.child("driverId").setValue("");
        shuttleRef.child("driverName").setValue("No driver");
        startActivity(new Intent(this, DriverDashboardActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    @Override public void onMapReady(GoogleMap map) {
        this.googleMap = map;
        LatLng loc = new LatLng(driverLat, driverLng);
        googleMap.addMarker(new MarkerOptions().position(loc).title(busName != null ? busName : "Shuttle").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 16));
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) startActivity(new Intent(this, ProfileActivity.class));
            return true;
        });
    }

    @Override protected void onResume()  { super.onResume();  mapView.onResume(); }
    @Override protected void onPause()   { super.onPause();   mapView.onPause();  }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy();}
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}