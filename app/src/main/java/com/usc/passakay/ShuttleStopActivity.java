package com.usc.passakay;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
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
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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
    private final List<Marker> stopMarkers = new ArrayList<>();

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

        FloatingActionButton fabMyLocation = findViewById(R.id.fabMyLocation);
        fabMyLocation.setOnClickListener(v -> panToMyLocation());

        loadStopsFromFirebase();
        setupBottomNav();
        startLocationUpdates();
    }

    private void panToMyLocation() {
        if (googleMap != null && (driverLat != 0 || driverLng != 0)) {
            LatLng myLoc = new LatLng(driverLat, driverLng);
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

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    driverLat = location.getLatitude();
                    driverLng = location.getLongitude();
                    updateShuttleLocationInDB(driverLat, driverLng);
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
                        .icon(bitmapDescriptorFromVector(this, R.drawable.ic_shuttle, 32, 32)));
            }
        }
    }

    private void loadStopsFromFirebase() {
        db.child("shuttleStops").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stopList.clear();
                // Clear existing stop markers from the map
                for (Marker marker : stopMarkers) {
                    marker.remove();
                }
                stopMarkers.clear();

                for (DataSnapshot stopSnapshot : snapshot.getChildren()) {
                    ShuttleStop stop = stopSnapshot.getValue(ShuttleStop.class);
                    if (stop != null) {
                        StopItem item = new StopItem(
                                stop.getStopName(),
                                0, // Initial count
                                324 // Placeholder distance
                        );
                        stopList.add(item);
                        
                        // Add marker to map with initial label
                        if (googleMap != null) {
                            Marker stopMarker = googleMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(stop.getLatitude(), stop.getLongitude()))
                                    .icon(BitmapDescriptorFactory.fromBitmap(createMarkerBitmap(item))));
                            if (stopMarker != null) {
                                stopMarker.setTag(item);
                                stopMarkers.add(stopMarker);
                            }
                        }

                        // Fetch actual waiting count from 'scans' node
                        fetchWaitingCount(stop.getStopName(), item);
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

    private Bitmap createMarkerBitmap(StopItem item) {
        View markerView = getLayoutInflater().inflate(R.layout.marker_stop, null);
        TextView tvName = markerView.findViewById(R.id.tvMarkerName);
        TextView tvWaiting = markerView.findViewById(R.id.tvMarkerWaiting);
        
        tvName.setText(item.getStopName());
        tvWaiting.setText(String.valueOf(item.getWaitingCount()));
        
        return getBitmapFromView(markerView);
    }

    private void fetchWaitingCount(String stopName, StopItem item) {
        String scanKey = stopName + "_com";
        if (stopName.equalsIgnoreCase("SAFAD")) {
            scanKey = "SAFAD Building_com";
        } else if (stopName.equalsIgnoreCase("Bunzel")) {
            scanKey = "Bunzel_com";
        }
        
        db.child("scans").child(scanKey).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = (int) snapshot.getChildrenCount();
                item.setWaitingCount(count);
                stopAdapter.notifyDataSetChanged();
                
                // Update specific marker icon
                if (googleMap != null) {
                    for (Marker marker : stopMarkers) {
                        if (item.equals(marker.getTag())) {
                            marker.setIcon(BitmapDescriptorFactory.fromBitmap(createMarkerBitmap(item)));
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
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
                .icon(bitmapDescriptorFromVector(this, R.drawable.ic_shuttle, 32, 32)));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(shuttleLoc, 16));

        refreshStopMarkers();
    }

    private void refreshStopMarkers() {
        if (googleMap == null) return;
        db.child("shuttleStops").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (Marker marker : stopMarkers) {
                    marker.remove();
                }
                stopMarkers.clear();

                for (DataSnapshot stopSnapshot : snapshot.getChildren()) {
                    ShuttleStop stop = stopSnapshot.getValue(ShuttleStop.class);
                    if (stop != null) {
                        StopItem matchedItem = null;
                        for (StopItem item : stopList) {
                            if (item.getStopName().equals(stop.getStopName())) {
                                matchedItem = item;
                                break;
                            }
                        }

                        if (matchedItem != null) {
                            Marker stopMarker = googleMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(stop.getLatitude(), stop.getLongitude()))
                                    .icon(BitmapDescriptorFactory.fromBitmap(createMarkerBitmap(matchedItem))));
                            if (stopMarker != null) {
                                stopMarker.setTag(matchedItem);
                                stopMarkers.add(stopMarker);
                            }
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
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
