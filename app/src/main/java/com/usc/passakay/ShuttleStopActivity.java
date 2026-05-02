package com.usc.passakay;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShuttleStopActivity extends BaseActivity implements OnMapReadyCallback {

    private static final String TAG = "ShuttleStopActivity";
    public static final String EXTRA_BUS_NAME   = "extra_bus_name";
    public static final String EXTRA_DRIVER_LAT = "extra_driver_lat";
    public static final String EXTRA_DRIVER_LNG = "extra_driver_lng";

    private static final double DEFAULT_LAT = 10.3541;
    private static final double DEFAULT_LNG = 123.9115;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1002;
    private static final float MAP_ZOOM_LEVEL = 18.5f;

    private MapView mapView;
    private GoogleMap googleMap;
    private double driverLat, driverLng;
    private String busName, shuttleId;

    private RecyclerView recyclerStops;
    private StopAdapter stopAdapter;
    private final List<StopItem> stopList = new ArrayList<>();
    private DatabaseReference db;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Marker driverMarker;
    private final List<Marker> stopMarkers = new ArrayList<>();

    // Notification UI
    private CardView cardNotification;
    private TextView tvNotificationText;
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private Runnable hideNotificationRunnable;
    private final Map<String, Integer> lastKnownCounts = new HashMap<>();
    private boolean isFirstLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shuttle_stop);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        busName   = getIntent().getStringExtra(EXTRA_BUS_NAME);
        driverLat = getIntent().getDoubleExtra(EXTRA_DRIVER_LAT, 10.3541);
        driverLng = getIntent().getDoubleExtra(EXTRA_DRIVER_LNG, 123.9115);
        shuttleId = getIntent().getStringExtra("shuttleId");

        // Notification UI
        cardNotification = findViewById(R.id.cardNotification);
        tvNotificationText = findViewById(R.id.tvNotificationText);

        mapView = findViewById(R.id.mapViewStops);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        recyclerStops = findViewById(R.id.recyclerStops);
        recyclerStops.setLayoutManager(new LinearLayoutManager(this));
        
        // Pass the pickup logic to the adapter
        stopAdapter = new StopAdapter(this, stopList, this::pickUpPassengers);
        recyclerStops.setAdapter(stopAdapter);

        FloatingActionButton fabMyLocation = findViewById(R.id.fabMyLocation);
        if (fabMyLocation != null) {
            fabMyLocation.setOnClickListener(v -> panToMyLocation());
        }
        
        View btnStopDriving = findViewById(R.id.btnStopDriving);
        if (btnStopDriving != null) {
            btnStopDriving.setOnClickListener(v -> stopDriving());
        }

        loadData();
        setupBottomNav();
        startLocationUpdates();
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> isFirstLoad = false, 3000);
    }

    private void pickUpPassengers(StopItem stopItem) {
        String stopName = stopItem.getStopName();
        String scanKey = getScanKey(stopName);
        
        db.child("scans").child(scanKey).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Picked up all passengers at " + stopName, Toast.LENGTH_SHORT).show();
                    // Also clear waiting status for individual users matched to this stop if needed
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error during pickup: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private String getScanKey(String stopName) {
        String scanKey = stopName.replace(".", "_").replace(" ", "_") + "_com";
        if (stopName.equalsIgnoreCase("SAFAD")) {
            scanKey = "SAFAD Building_com";
        } else if (stopName.equalsIgnoreCase("Bunzel")) {
            scanKey = "Bunzel_com";
        }
        return scanKey;
    }

    private void showPopUpNotification(String message) {
        if (isFirstLoad) return;
        
        notificationHandler.removeCallbacks(hideNotificationRunnable);
        tvNotificationText.setText(message);
        
        if (cardNotification.getVisibility() != View.VISIBLE) {
            cardNotification.setVisibility(View.VISIBLE);
            cardNotification.setAlpha(0f);
            cardNotification.animate().alpha(1f).setDuration(300).start();
        }

        hideNotificationRunnable = () -> {
            cardNotification.animate().alpha(0f).setDuration(500).withEndAction(() -> {
                cardNotification.setVisibility(View.GONE);
            }).start();
        };
        notificationHandler.postDelayed(hideNotificationRunnable, 5000);
    }

    private void panToMyLocation() {
        if (googleMap != null && (driverLat != 0 || driverLng != 0)) {
            LatLng myLoc = new LatLng(driverLat, driverLng);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLoc, MAP_ZOOM_LEVEL));
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
        
        if (googleMap != null) {
            LatLng newLoc = new LatLng(lat, lng);
            if (driverMarker != null) {
                driverMarker.setPosition(newLoc);
            } else {
                driverMarker = googleMap.addMarker(new MarkerOptions()
                        .position(newLoc)
                        .title(busName != null ? busName : "Shuttle")
                        .icon(bitmapDescriptorFromVector(this, R.drawable.ic_shuttle, 28, 28)));
            }
        }
    }

    private void loadData() {
        db.child("shuttleStops").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stopList.clear();
                for (Marker marker : stopMarkers) {
                    marker.remove();
                }
                stopMarkers.clear();

                for (DataSnapshot stopSnap : snapshot.getChildren()) {
                    ShuttleStop stop = stopSnap.getValue(ShuttleStop.class);
                    if (stop != null) {
                        int distance = calculateDistance(driverLat, driverLng, stop.getLatitude(), stop.getLongitude());
                        StopItem item = new StopItem(stop.getStopName(), 0, distance);
                        stopList.add(item);
                        
                        if (googleMap != null) {
                            Marker stopMarker = googleMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(stop.getLatitude(), stop.getLongitude()))
                                    .icon(BitmapDescriptorFactory.fromBitmap(createMarkerBitmap(item))));
                            if (stopMarker != null) {
                                stopMarker.setTag(item);
                                stopMarkers.add(stopMarker);
                            }
                        }
                        fetchWaitingCount(stop.getStopName(), item);
                    }
                }
                
                // Sort stops logically: Bunzel -> ... -> Among Balay
                stopList.sort((s1, s2) -> Integer.compare(getRouteOrder(s1.getStopName()), getRouteOrder(s2.getStopName())));
                stopAdapter.notifyDataSetChanged();
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

    private Bitmap createMarkerBitmap(StopItem item) {
        View markerView = getLayoutInflater().inflate(R.layout.marker_stop, null);
        TextView tvName = markerView.findViewById(R.id.tvMarkerName);
        TextView tvWaiting = markerView.findViewById(R.id.tvMarkerWaiting);
        
        tvName.setText(item.getStopName());
        tvWaiting.setText(String.valueOf(item.getWaitingCount()));
        
        return getBitmapFromView(markerView);
    }

    private void fetchWaitingCount(String stopName, StopItem item) {
        String scanKey = getScanKey(stopName);
        
        db.child("scans").child(scanKey).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = (int) snapshot.getChildrenCount();
                
                Integer lastCount = lastKnownCounts.get(stopName);
                if (lastCount != null && count > lastCount) {
                    showPopUpNotification(count + " passengers waiting at " + stopName + " stop");
                }
                lastKnownCounts.put(stopName, count);

                item.setWaitingCount(count);
                stopAdapter.notifyDataSetChanged();
                
                if (googleMap != null) {
                    for (Marker marker : stopMarkers) {
                        if (item.equals(marker.getTag())) {
                            marker.setIcon(BitmapDescriptorFactory.fromBitmap(createMarkerBitmap(item)));
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
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
                .icon(bitmapDescriptorFromVector(this, R.drawable.ic_shuttle, 28, 28)));
        
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(shuttleLoc, MAP_ZOOM_LEVEL));
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

                for (DataSnapshot stopSnap : snapshot.getChildren()) {
                    ShuttleStop stop = stopSnap.getValue(ShuttleStop.class);
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
        if (bottomNav == null) return;
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

    @Override protected void onResume()  { super.onResume();  if (mapView != null) mapView.onResume(); }
    @Override protected void onPause()   { 
        super.onPause();   
        if (mapView != null) mapView.onPause();  
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
    @Override protected void onDestroy() { super.onDestroy(); if (mapView != null) mapView.onDestroy();}
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (mapView != null) mapView.onSaveInstanceState(out);
    }
}
