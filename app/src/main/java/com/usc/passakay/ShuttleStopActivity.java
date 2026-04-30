package com.usc.passakay;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
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
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1002;

    private MapView mapView;
    private GoogleMap googleMap;
    private double driverLat, driverLng;
    private String busName;
    private String shuttleId;

    private RecyclerView recyclerStops;
    private StopAdapter stopAdapter;
    private final List<StopItem> stopList = new ArrayList<>();
    private DatabaseReference db;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Marker driverMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shuttle_stop);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

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

        loadStopsFromFirebase();
        setupBottomNav();
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    updateShuttleLocationInDB(location.getLatitude(), location.getLongitude());
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void updateShuttleLocationInDB(double lat, double lng) {
        if (shuttleId != null) {
            db.child("shuttles").child(shuttleId).child("currentLat").setValue(lat);
            db.child("shuttles").child(shuttleId).child("currentLng").setValue(lng);
        }
        
        // Also update local marker
        if (googleMap != null) {
            LatLng newLoc = new LatLng(lat, lng);
            if (driverMarker != null) {
                driverMarker.setPosition(newLoc);
            } else {
                driverMarker = googleMap.addMarker(new MarkerOptions()
                        .position(newLoc)
                        .title(busName != null ? busName : "Shuttle")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
            }
            // googleMap.animateCamera(CameraUpdateFactory.newLatLng(newLoc));
        }
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        }

        LatLng shuttleLoc = new LatLng(driverLat, driverLng);
        driverMarker = googleMap.addMarker(new MarkerOptions()
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }

    @Override protected void onResume()  { super.onResume();  mapView.onResume(); }
    @Override protected void onPause()   { 
        super.onPause();   
        mapView.onPause();  
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy();}
    @Override public    void onLowMemory()          { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        mapView.onSaveInstanceState(out);
    }
}
