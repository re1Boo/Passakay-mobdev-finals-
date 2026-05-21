package com.usc.passakay;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.gms.maps.model.LatLngBounds;
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
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1003;

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

        // Mark shuttle as active immediately when driver starts
        if (shuttleId != null) {
            db.child("shuttles").child(shuttleId).child("active").setValue(true);
            db.child("shuttles").child(shuttleId).child("status").setValue("Deployed");
        }

        cardNotification = findViewById(R.id.cardNotification);
        tvNotificationText = findViewById(R.id.tvNotificationText);

        mapView = findViewById(R.id.mapViewStops);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        recyclerStops = findViewById(R.id.recyclerStops);
        recyclerStops.setLayoutManager(new LinearLayoutManager(this));

        stopAdapter = new StopAdapter(this, stopList, this::pickUpPassengers);
        recyclerStops.setAdapter(stopAdapter);

        findViewById(R.id.fabMyLocation).setOnClickListener(v -> zoomToFitMarkers());
        
        View btnStopDriving = findViewById(R.id.btnStopDriving);
        if (btnStopDriving != null) {
            btnStopDriving.setOnClickListener(v -> stopDriving());
        }

        loadData();
        setupBottomNav();
        startLocationUpdates();
        startNotificationService();

        new Handler(Looper.getMainLooper()).postDelayed(() -> isFirstLoad = false, 3000);
    }

    private void startNotificationService() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
        
        Intent serviceIntent = new Intent(this, NotificationService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void pickUpPassengers(StopItem stopItem) {
        if (stopItem.getDistanceMeters() > 100) {
            Toast.makeText(this, "You must be closer to the stop to pick up passengers", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = stopItem.getWaitingCount();
        if (count == 0) {
            Toast.makeText(this, "No passengers waiting at this stop.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Pickup")
                .setMessage("Are you sure you want to board all " + count + " passengers at " + stopItem.getStopName() + "?")
                .setPositiveButton("Board All", (dialog, which) -> executeBoarding(stopItem))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeBoarding(StopItem stopItem) {
        String stopName = stopItem.getStopName();
        String stopKey = getScanKey(stopName);

        // 1. Get everyone who scanned the QR code at this stop
        db.child("scans").child(stopKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot scanSnapshot) {
                // 2. Fetch all users to check their current proximity to the shuttle
                db.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot usersSnapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        int boarded = 0;
                        int leftBehind = 0;

                        for (DataSnapshot userInScan : scanSnapshot.getChildren()) {
                            String uid = userInScan.getKey();
                            if (uid == null || uid.equals("_count")) continue;

                            DataSnapshot userDetails = usersSnapshot.child(uid);
                            Double pLat = userDetails.child("currentLat").getValue(Double.class);
                            Double pLng = userDetails.child("currentLng").getValue(Double.class);

                            // Only board those physically near the shuttle (within 40 meters)
                            boolean isNear = false;
                            if (pLat != null && pLng != null) {
                                float[] dist = new float[1];
                                Location.distanceBetween(driverLat, driverLng, pLat, pLng, dist);
                                if (dist[0] <= 40) isNear = true;
                            }

                            if (isNear) {
                                updates.put("/users/" + uid + "/isWaiting", false);
                                updates.put("/users/" + uid + "/waitingAt", "");
                                updates.put("/users/" + uid + "/isRiding", true);
                                if (shuttleId != null) {
                                    try { updates.put("/users/" + uid + "/ridingShuttleId", Integer.parseInt(shuttleId)); }
                                    catch (NumberFormatException ignored) {}
                                }
                                // Remove from scan list
                                updates.put("/scans/" + stopKey + "/" + uid, null);
                                boarded++;
                            } else {
                                leftBehind++;
                            }
                        }

                        if (boarded == 0) {
                            Toast.makeText(ShuttleStopActivity.this, "No passengers near the shuttle to board.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Update the waiting count for the stop based only on those who boarded
                        final int finalBoarded = boarded;
                        final int finalLeftBehind = leftBehind;
                        
                        db.child("stopWaitingCounts").child(stopKey).runTransaction(new com.google.firebase.database.Transaction.Handler() {
                            @NonNull
                            @Override
                            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData mutableData) {
                                Integer count = mutableData.getValue(Integer.class);
                                if (count != null) {
                                    mutableData.setValue(Math.max(0, count - finalBoarded));
                                }
                                return com.google.firebase.database.Transaction.success(mutableData);
                            }
                            @Override
                            public void onComplete(com.google.firebase.database.DatabaseError e, boolean b, com.google.firebase.database.DataSnapshot s) {
                                db.updateChildren(updates).addOnSuccessListener(aVoid -> {
                                    String msg = "Boarded " + finalBoarded + " passengers.";
                                    if (finalLeftBehind > 0) msg += " " + finalLeftBehind + " left behind (Too far).";
                                    Toast.makeText(ShuttleStopActivity.this, msg, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String getScanKey(String stopName) {
        if (stopName.equalsIgnoreCase("SAFAD")) return "SAFAD Building_com";
        if (stopName.equalsIgnoreCase("Bunzel")) return "Bunzel_com";
        return stopName + "_com";
    }

    private void showPopUpNotification(String message) {
        if (isFirstLoad) return;
        notificationHandler.removeCallbacks(hideNotificationRunnable);
        tvNotificationText.setText(message);
        cardNotification.setVisibility(View.VISIBLE);
        cardNotification.setAlpha(0f);
        cardNotification.animate().alpha(1f).setDuration(300).start();
        hideNotificationRunnable = () -> cardNotification.animate().alpha(0f).setDuration(500).withEndAction(() -> cardNotification.setVisibility(View.GONE)).start();
        notificationHandler.postDelayed(hideNotificationRunnable, 5000);
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location loc : locationResult.getLocations()) {
                    if (loc.getLatitude() != 0) { // Filter invalid GPS fixes
                        driverLat = loc.getLatitude(); driverLng = loc.getLongitude();
                        updateShuttleLocationInDB(driverLat, driverLng);
                    }
                }
            }
        };
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void updateShuttleLocationInDB(double lat, double lng) {
        if (shuttleId != null && lat != 0) {
            db.child("shuttles").child(shuttleId).child("currentLat").setValue(lat);
            db.child("shuttles").child(shuttleId).child("currentLng").setValue(lng);
        }
        if (googleMap != null && lat != 0) {
            LatLng newLoc = new LatLng(lat, lng);
            if (driverMarker != null) driverMarker.setPosition(newLoc);
            else driverMarker = googleMap.addMarker(new MarkerOptions().position(newLoc).icon(bitmapDescriptorFromVector(this, R.drawable.ic_shuttle, 28, 28)));
        }
    }

    private void loadData() {
        db.child("shuttleStops").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (Marker m : stopMarkers) m.remove();
                stopMarkers.clear();
                stopList.clear();
                for (DataSnapshot stopSnapshot : snapshot.getChildren()) {
                    ShuttleStop stop = stopSnapshot.getValue(ShuttleStop.class);
                    if (stop != null && stop.getLatitude() != 0) {
                        int distance = calculateDistance(driverLat, driverLng, stop.getLatitude(), stop.getLongitude());
                        StopItem item = new StopItem(stop.getStopName(), 0, distance);
                        stopList.add(item);
                        Marker sm = googleMap.addMarker(new MarkerOptions().position(new LatLng(stop.getLatitude(), stop.getLongitude()))
                                .icon(BitmapDescriptorFactory.fromBitmap(createMarkerBitmap(item))));
                        if (sm != null) { sm.setTag(item); stopMarkers.add(sm); }
                        fetchWaitingCount(stop.getStopName(), item);
                    }
                }
                
                // Sort stops logically: Bunzel -> ... -> Among Balay
                stopList.sort((s1, s2) -> Integer.compare(getRouteOrder(s1.getStopName()), getRouteOrder(s2.getStopName())));
                stopAdapter.notifyDataSetChanged();
                new Handler(Looper.getMainLooper()).postDelayed(() -> zoomToFitMarkers(), 1000);
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

    private void zoomToFitMarkers() {
        if (googleMap == null || stopMarkers.isEmpty()) return;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        int count = 0;
        for (Marker m : stopMarkers) { 
            if (m.getPosition().latitude != 0) {
                builder.include(m.getPosition()); count++; 
            }
        }
        // Removed driverMarker from bounds to keep map focused on campus/stops
        if (count > 0) {
            LatLngBounds bounds = builder.build();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120));
        }
    }

    private Bitmap createMarkerBitmap(StopItem item) {
        View markerView = getLayoutInflater().inflate(R.layout.marker_stop, null);
        TextView tvName = markerView.findViewById(R.id.tvMarkerName);
        TextView tvCount = markerView.findViewById(R.id.tvMarkerWaiting);
        View viewDot = markerView.findViewById(R.id.viewDot);
        ImageView ivPin = markerView.findViewById(R.id.ivMarkerPin);

        tvName.setText(item.getStopName());
        tvCount.setText(String.valueOf(item.getWaitingCount()));

        // Red if has waiting (>0), Green if no waiting (0)
        int redColor = Color.parseColor("#F44336");
        int greenColor = Color.parseColor("#4CAF50");
        int colorToApply = (item.getWaitingCount() > 0) ? redColor : greenColor;

        tvCount.setTextColor(colorToApply);
        if (viewDot != null) {
            Drawable background = viewDot.getBackground();
            if (background != null) {
                background.mutate().setTint(colorToApply);
            } else {
                viewDot.setBackgroundColor(colorToApply);
            }
        }

        if (ivPin != null) {
            ivPin.setColorFilter(colorToApply);
        }

        return getBitmapFromView(markerView);
    }

    private void fetchWaitingCount(String stopName, StopItem item) {
        String stopKey = getScanKey(stopName);
        db.child("stopWaitingCounts").child(stopKey).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer countObj = snapshot.getValue(Integer.class);
                int count = countObj != null ? countObj : 0;
                
                if (lastKnownCounts.get(stopName) != null && count > lastKnownCounts.get(stopName)) {
                    showPopUpNotification(count + " waiting at " + stopName);
                }
                lastKnownCounts.put(stopName, count);
                item.setWaitingCount(count);
                stopAdapter.notifyDataSetChanged();
                for (Marker m : stopMarkers) {
                    if (item.equals(m.getTag())) m.setIcon(BitmapDescriptorFactory.fromBitmap(createMarkerBitmap(item)));
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void stopDriving() {
        if (shuttleId == null) return;
        
        // Stop the background notification service
        stopService(new Intent(this, NotificationService.class));

        DatabaseReference shuttleRef = db.child("shuttles").child(shuttleId);
        shuttleRef.child("status").setValue("Standby");
        shuttleRef.child("driverId").setValue("");
        shuttleRef.child("driverName").setValue("No driver");
        shuttleRef.child("active").setValue(false); // Mark shuttle as offline

        startActivity(new Intent(this, DriverDashboardActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;
        MapsInitializer.initialize(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) googleMap.setMyLocationEnabled(true);
        
        applyCampusMapConstraints(googleMap);

        LatLng shuttleLoc = new LatLng(driverLat, driverLng);
        driverMarker = googleMap.addMarker(new MarkerOptions().position(shuttleLoc).icon(bitmapDescriptorFromVector(this, R.drawable.ic_shuttle, 28, 28)));
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> zoomToFitMarkers(), 1500);
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.getMenu().clear();
        nav.inflateMenu(R.menu.menu_driver);
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override public void onResume() { super.onResume(); mapView.onResume(); }
    @Override public void onPause() { super.onPause(); mapView.onPause(); if (fusedLocationClient != null) fusedLocationClient.removeLocationUpdates(locationCallback); }
    @Override public void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
