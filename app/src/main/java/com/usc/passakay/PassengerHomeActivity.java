package com.usc.passakay;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
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
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
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

public class PassengerHomeActivity extends BaseActivity implements OnMapReadyCallback {

    private static final double USC_LAT = 10.3541;
    private static final double USC_LNG = 123.9115;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private MapView mapView;
    private GoogleMap googleMap;
    private RecyclerView recyclerShuttles;
    private ShuttleAdapter shuttleAdapter;
    private List<ShuttleItem> shuttleList = new ArrayList<>();
    private DatabaseReference db;
    private MaterialButton btnWaitingStatus;
    private TextView tvUserLoc;
    private boolean isWaiting = false;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private double userLat = 0, userLng = 0;

    private List<ShuttleStop> allStops = new ArrayList<>();
    private Handler stopUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable stopUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_home);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // UI Elements
        btnWaitingStatus = findViewById(R.id.btnWaitingStatus);
        tvUserLoc = findViewById(R.id.tvUserLoc);
        FloatingActionButton fabScanQR = findViewById(R.id.fabScanQR);
        FloatingActionButton fabMyLocation = findViewById(R.id.fabMyLocation);

        // Setup Map
        mapView = findViewById(R.id.mapViewMain);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Setup RecyclerView
        recyclerShuttles = findViewById(R.id.recyclerShuttles);
        recyclerShuttles.setLayoutManager(new LinearLayoutManager(this));
        shuttleAdapter = new ShuttleAdapter(this, shuttleList, getSupportFragmentManager());
        recyclerShuttles.setAdapter(shuttleAdapter);

        // Waiting Status Toggle
        btnWaitingStatus.setOnClickListener(v -> toggleWaitingStatus());

        // QR Scan FAB
        fabScanQR.setOnClickListener(v -> startQRScanner());

        // My Location FAB
        fabMyLocation.setOnClickListener(v -> panToMyLocation());

        // Load all shuttles
        loadShuttles();

        // Setup bottom nav
        setupBottomNav();

        // Start location updates
        startLocationUpdates();

        // Initialize nearest stop update runnable
        stopUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateNearestStop();
                stopUpdateHandler.postDelayed(this, 10000); // 10 seconds
            }
        };
    }

    private void panToMyLocation() {
        if (googleMap != null && userLat != 0 && userLng != 0) {
            LatLng myLoc = new LatLng(userLat, userLng);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLoc, 17));
        } else {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    updateUserLocationInDB(location.getLatitude(), location.getLongitude());
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void updateUserLocationInDB(double lat, double lng) {
        this.userLat = lat;
        this.userLng = lng;
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.child("users").child(uid).child("currentLat").setValue(lat);
            db.child("users").child(uid).child("currentLng").setValue(lng);
        }
        updateMapMarkers(); // Refresh map with new user location
        updateNearestStop(); // Check for nearest stop on every location update too
    }

    private void updateNearestStop() {
        if (allStops.isEmpty() || userLat == 0 || userLng == 0) return;

        ShuttleStop nearest = null;
        float minDistance = Float.MAX_VALUE;

        for (ShuttleStop stop : allStops) {
            float[] results = new float[1];
            Location.distanceBetween(userLat, userLng, stop.getLatitude(), stop.getLongitude(), results);
            if (results[0] < minDistance) {
                minDistance = results[0];
                nearest = stop;
            }
        }

        if (nearest != null && tvUserLoc != null) {
            tvUserLoc.setText(nearest.getStopName());
        }
    }

    private void startQRScanner() {
        Intent intent = new Intent(this, ScannerActivity.class);
        startActivity(intent);
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
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.child("users").child(uid).child("isWaiting").setValue(waiting);
        }
    }

    private void loadShuttles() {
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
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
                        shuttleList.add(item);
                    }
                }
                shuttleAdapter.notifyDataSetChanged();
                updateMapMarkers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private int calculateETA(double lat, double lng) {
        return (int)(Math.random() * 8) + 2;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        }
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        LatLng uscLocation = new LatLng(USC_LAT, USC_LNG);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(uscLocation, 16));
        loadStopsOnMap();
    }

    private void loadStopsOnMap() {
        db.child("shuttleStops").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allStops.clear();
                for (DataSnapshot stopSnapshot : snapshot.getChildren()) {
                    ShuttleStop stop = stopSnapshot.getValue(ShuttleStop.class);
                    if (stop != null) {
                        allStops.add(stop);
                        if (googleMap != null) {
                            googleMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(stop.getLatitude(), stop.getLongitude()))
                                    .title(stop.getStopName())
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                        }
                    }
                }
                updateNearestStop();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateMapMarkers() {
        if (googleMap == null) return;
        googleMap.clear();
        // Redraw stops
        for (ShuttleStop stop : allStops) {
            googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(stop.getLatitude(), stop.getLongitude()))
                    .title(stop.getStopName())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }
        
        // Use ic_person for user location - scaled to 32dp
        if (userLat != 0 && userLng != 0) {
            googleMap.addMarker(new MarkerOptions()
                    .position(new LatLng(userLat, userLng))
                    .title("My Location")
                    .icon(bitmapDescriptorFromVector(this, R.drawable.ic_person, 32, 32)));
        }

        // Use ic_shuttle for buses - scaled to 32dp
        for (ShuttleItem shuttle : shuttleList) {
            if (shuttle.isAvailable()) {
                googleMap.addMarker(new MarkerOptions()
                        .position(new LatLng(shuttle.getDriverLat(), shuttle.getDriverLng()))
                        .title(shuttle.getBusName())
                        .icon(bitmapDescriptorFromVector(this, R.drawable.ic_shuttle, 32, 32)));
            }
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
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

    @Override protected void onResume() { 
        super.onResume(); 
        if (mapView != null) mapView.onResume();
        stopUpdateHandler.post(stopUpdateRunnable);
    }

    @Override protected void onPause() { 
        super.onPause(); 
        if (mapView != null) { 
            mapView.onPause(); 
            fusedLocationClient.removeLocationUpdates(locationCallback); 
        }
        stopUpdateHandler.removeCallbacks(stopUpdateRunnable);
    }

    @Override protected void onDestroy() { super.onDestroy(); if (mapView != null) mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
}
