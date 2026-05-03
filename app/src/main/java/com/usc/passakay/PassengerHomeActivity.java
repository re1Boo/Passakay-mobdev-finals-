package com.usc.passakay;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
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

public class PassengerHomeActivity extends BaseActivity implements OnMapReadyCallback {

    private static final double USC_LAT = 10.3541;
    private static final double USC_LNG = 123.9115;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private MapView mapView;
    private GoogleMap googleMap;
    private RecyclerView recyclerShuttles;
    private ShuttleAdapter shuttleAdapter;
    private final List<ShuttleItem> shuttleList = new ArrayList<>();
    private DatabaseReference db;
    
    private MaterialButton btnWaitingStatus, btnCancelRide;
    private TextView tvUserLoc, tvNextStop, tvAnnouncement;
    private LinearLayout layoutTripProgress;
    private ProgressBar progressBarTrip;
    
    private boolean isWaiting = false;
    private boolean isRiding = false;
    private String waitingAtStopName = "";
    private int ridingShuttleId = -1;
    private long outOfRadiusStartTime = 0;
    private String lastDismissedAnnouncementId = "";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private double userLat = 0, userLng = 0;

    private final List<ShuttleStop> allStops = new ArrayList<>();
    private final Map<String, Marker> stopMarkers = new HashMap<>();
    private final Map<String, Marker> shuttleMarkers = new HashMap<>();
    private Marker userMarker;
    private final Map<String, Integer> stopWaitingCounts = new HashMap<>();
    private final Handler statusMonitorHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_home);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        btnWaitingStatus = findViewById(R.id.btnWaitingStatus);
        btnCancelRide = findViewById(R.id.btnCancelRide);
        tvUserLoc = findViewById(R.id.tvUserLoc);
        tvNextStop = findViewById(R.id.tvNextStop);
        tvAnnouncement = findViewById(R.id.tvAnnouncement);
        layoutTripProgress = findViewById(R.id.layoutTripProgress);
        progressBarTrip = findViewById(R.id.progressBarTrip);

        mapView = findViewById(R.id.mapViewMain);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        recyclerShuttles = findViewById(R.id.recyclerShuttles);
        recyclerShuttles.setLayoutManager(new LinearLayoutManager(this));
        shuttleAdapter = new ShuttleAdapter(this, shuttleList, getSupportFragmentManager());
        recyclerShuttles.setAdapter(shuttleAdapter);

        btnWaitingStatus.setOnClickListener(v -> toggleWaitingStatus());
        btnCancelRide.setOnClickListener(v -> confirmCancelRide());
        findViewById(R.id.fabScanQR).setOnClickListener(v -> startActivity(new Intent(this, ScannerActivity.class)));
        findViewById(R.id.fabMyLocation).setOnClickListener(v -> panToMyLocation());

        loadAnnouncements();
        loadShuttles();
        setupBottomNav();
        startLocationUpdates();
        syncUserStatus();
        startStatusMonitor();
    }

    private void syncUserStatus() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.child("users").child(uid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    isWaiting = user.isWaiting;
                    isRiding = user.isRiding;
                    waitingAtStopName = user.waitingAt != null ? user.waitingAt : "";
                    ridingShuttleId = user.ridingShuttleId;
                    updateStatusUI();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateStatusUI() {
        if (isRiding) {
            btnWaitingStatus.setText("RIDING...");
            btnWaitingStatus.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50)); 
            layoutTripProgress.setVisibility(View.VISIBLE);
            btnCancelRide.setVisibility(View.VISIBLE);
            btnCancelRide.setText("CANCEL RIDE");
        } else if (isWaiting) {
            btnWaitingStatus.setText("WAITING");
            btnWaitingStatus.setBackgroundTintList(ColorStateList.valueOf(0xFFF44336)); 
            layoutTripProgress.setVisibility(View.GONE);
            btnCancelRide.setVisibility(View.VISIBLE);
            btnCancelRide.setText("CANCEL WAITING");
        } else {
            btnWaitingStatus.setText("NOT WAITING");
            btnWaitingStatus.setBackgroundTintList(ColorStateList.valueOf(0xFFFFEA08)); 
            layoutTripProgress.setVisibility(View.GONE);
            btnCancelRide.setVisibility(View.GONE);
        }
    }

    private void confirmCancelRide() {
        String title = isRiding ? "Cancel Ride" : "Cancel Waiting";
        String message = isRiding ? "Are you sure you want to cancel your current ride?" : "Are you sure you want to stop waiting?";
        
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Yes", (dialog, which) -> clearStatusInDB())
                .setNegativeButton("No", null)
                .show();
    }

    private void startStatusMonitor() {
        statusMonitorHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAutoStatusTransitions();
                statusMonitorHandler.postDelayed(this, 10000);
            }
        }, 10000);
    }

    private void checkAutoStatusTransitions() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        if (isWaiting && !waitingAtStopName.isEmpty()) {
            ShuttleStop stop = findStopByName(waitingAtStopName);
            if (stop != null) {
                float[] dist = new float[1];
                Location.distanceBetween(userLat, userLng, stop.getLatitude(), stop.getLongitude(), dist);
                if (dist[0] > 50) {
                    if (outOfRadiusStartTime == 0) outOfRadiusStartTime = System.currentTimeMillis();
                    else if (System.currentTimeMillis() - outOfRadiusStartTime > 180000) {
                        clearStatusInDB();
                        Toast.makeText(this, "Waiting status cleared (Left stop area)", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    outOfRadiusStartTime = 0;
                }
            }
        }

        if (isRiding && ridingShuttleId != -1) {
            db.child("shuttles").child(String.valueOf(ridingShuttleId)).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Shuttle s = snapshot.getValue(Shuttle.class);
                    if (s != null) {
                        float[] dist = new float[1];
                        Location.distanceBetween(userLat, userLng, s.getCurrentLat(), s.getCurrentLng(), dist);
                        if (dist[0] > 150) { 
                            clearStatusInDB();
                            Toast.makeText(PassengerHomeActivity.this, "Trip finished", Toast.LENGTH_SHORT).show();
                        } else {
                            updateTripProgress(s);
                        }
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void updateTripProgress(Shuttle s) {
        String[] route = {"Bunzel", "SAFAD", "PE", "MR"};
        ShuttleStop nearest = null;
        float minDist = Float.MAX_VALUE;
        int stopIdx = 0;

        for (int i = 0; i < route.length; i++) {
            ShuttleStop stop = findStopByName(route[i]);
            if (stop != null) {
                float[] d = new float[1];
                Location.distanceBetween(s.getCurrentLat(), s.getCurrentLng(), stop.getLatitude(), stop.getLongitude(), d);
                if (d[0] < minDist) {
                    minDist = d[0];
                    nearest = stop;
                    stopIdx = i;
                }
            }
        }

        if (nearest != null) {
            int progress = (stopIdx * 25) + 10;
            progressBarTrip.setProgress(progress);
            String next = route[(stopIdx + 1) % route.length];
            tvNextStop.setText("Approaching: " + next);
        }
    }

    private void clearStatusInDB() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // If waiting at a stop, remove from scans
        if (isWaiting && !waitingAtStopName.isEmpty()) {
            db.child("scans").child(getScanKey(waitingAtStopName)).child(uid).removeValue();
        }

        DatabaseReference ref = db.child("users").child(uid);
        ref.child("isWaiting").setValue(false);
        ref.child("isRiding").setValue(false);
        ref.child("waitingAt").setValue("");
        ref.child("ridingShuttleId").setValue(-1);
        
        Toast.makeText(this, "Status cleared", Toast.LENGTH_SHORT).show();
    }

    private ShuttleStop findStopByName(String name) {
        for (ShuttleStop s : allStops) {
            if (s.getStopName().toLowerCase().contains(name.toLowerCase())) return s;
        }
        return null;
    }

    private void panToMyLocation() {
        if (googleMap != null && userLat != 0) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(userLat, userLng), 17.5f));
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location.getLatitude() != 0) {
                        userLat = location.getLatitude();
                        userLng = location.getLongitude();
                        updateUserLocationInDB(userLat, userLng);
                    }
                }
            }
        };
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void updateUserLocationInDB(double lat, double lng) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.child("users").child(uid).child("currentLat").setValue(lat);
            db.child("users").child(uid).child("currentLng").setValue(lng);
        }
        updateMapMarkers();
    }

    private void toggleWaitingStatus() {
        if (isRiding) return;
        updateWaitingStatusInDB(!isWaiting);
    }

    private void updateWaitingStatusInDB(boolean waiting) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.child("users").child(uid).child("isWaiting").setValue(waiting);
            if (!waiting) {
                if (!waitingAtStopName.isEmpty()) {
                    db.child("scans").child(getScanKey(waitingAtStopName)).child(uid).removeValue();
                }
                db.child("users").child(uid).child("waitingAt").setValue("");
            }
        }
    }

    private void loadAnnouncements() {
        View card = findViewById(R.id.cardAnnouncement);
        View btnDismiss = findViewById(R.id.ivDismissAnnouncement);
        
        if (btnDismiss != null && card != null) {
            btnDismiss.setOnClickListener(v -> {
                Log.d("Announcement", "Dismiss clicked. Message: " + (tvAnnouncement != null ? tvAnnouncement.getText() : "null"));
                card.setVisibility(View.GONE);
                if (tvAnnouncement != null) {
                    lastDismissedAnnouncementId = tvAnnouncement.getText().toString();
                }
            });
            
            // Also allow clicking the card to see history
            card.setOnClickListener(v -> showAnnouncementHistory());
        }

        db.child("announcements").child("current").addValueEventListener(new ValueEventListener() {
            private String lastMessage = "";

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String message = snapshot.child("message").getValue(String.class);
                    String priority = snapshot.child("priority").getValue(String.class);
                    Long expiresAt = snapshot.child("expiresAt").getValue(Long.class);

                    if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                        if (card != null) card.setVisibility(View.GONE);
                        return;
                    }

                    // Reset dismissal if it's a completely NEW message compared to the last one we saw
                    if (message != null && !message.equals(lastMessage)) {
                        lastDismissedAnnouncementId = "";
                        lastMessage = message;
                        
                        // Vibrate for new messages
                        android.os.Vibrator v = (android.os.Vibrator) getSystemService(android.content.Context.VIBRATOR_SERVICE);
                        if (v != null) v.vibrate(300);
                    }

                    // If user manually dismissed THIS exact message, keep it hidden
                    if (message != null && message.equals(lastDismissedAnnouncementId)) {
                        Log.d("Announcement", "Keeping hidden because it was manually dismissed.");
                        if (card != null) card.setVisibility(View.GONE);
                        return;
                    }

                    if (tvAnnouncement != null) {
                        tvAnnouncement.setText(message);
                        tvAnnouncement.setSelected(true);
                    }
                    
                    if (card != null) {
                        Log.d("Announcement", "Showing announcement card.");
                        card.setVisibility(View.VISIBLE);
                    }

                    long currentTimestamp = snapshot.child("timestamp").getValue(Long.class) != null ? snapshot.child("timestamp").getValue(Long.class) : 0;
                    if (findViewById(R.id.tvNewBadge) != null) {
                        if (currentTimestamp > 0 && (System.currentTimeMillis() - currentTimestamp) < (2 * 60 * 1000)) {
                            findViewById(R.id.tvNewBadge).setVisibility(android.view.View.VISIBLE);
                        } else {
                            findViewById(R.id.tvNewBadge).setVisibility(android.view.View.GONE);
                        }
                    }

                    if (currentTimestamp > 0 && findViewById(R.id.tvAnnouncementTime) != null) {
                        TextView tvTime = findViewById(R.id.tvAnnouncementTime);
                        tvTime.setText(android.text.format.DateUtils.getRelativeTimeSpanString(currentTimestamp, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS));
                    }

                    if (card instanceof androidx.cardview.widget.CardView) {
                        androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) card;
                        if ("warning".equals(priority)) {
                            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FFE0B2")); 
                        } else if ("emergency".equals(priority)) {
                            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FFCDD2")); 
                            android.view.animation.Animation pulse = new android.view.animation.AlphaAnimation(1.0f, 0.6f);
                            pulse.setDuration(800);
                            pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
                            pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
                            cardView.startAnimation(pulse);
                        } else {
                            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF9C4")); 
                            cardView.clearAnimation();
                        }
                    }
                } else {
                    if (findViewById(R.id.cardAnnouncement) != null)
                        findViewById(R.id.cardAnnouncement).setVisibility(android.view.View.GONE);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void showAnnouncementHistory() {
        db.child("announcements").child("history").limitToLast(10).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                java.util.List<java.util.Map<String, Object>> historyData = new java.util.ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) ds.getValue();
                    if (map != null) historyData.add(0, map); 
                }

                AnnouncementHistoryAdapter adapter = new AnnouncementHistoryAdapter(PassengerHomeActivity.this, historyData, null);
                
                RecyclerView rv = new RecyclerView(PassengerHomeActivity.this);
                rv.setLayoutManager(new LinearLayoutManager(PassengerHomeActivity.this));
                rv.setAdapter(adapter);
                rv.setPadding(20, 20, 20, 20);

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(PassengerHomeActivity.this);
                builder.setTitle("Recent Announcements");
                builder.setView(rv);
                builder.setPositiveButton("Close", null);
                builder.show();
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void loadShuttles() {
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                shuttleList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Shuttle s = child.getValue(Shuttle.class);
                    if (s != null && s.getCurrentLat() != 0) {
                        shuttleList.add(new ShuttleItem(String.valueOf(s.getShuttleId()), "Bus " + s.getShuttleId(), 
                            s.getDriverName(), s.getPlateNumber(), 5, s.isActive(), s.getCurrentLat(), s.getCurrentLng()));
                    }
                }
                shuttleAdapter.notifyDataSetChanged();
                updateMapMarkers();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        }
        
        // Initial center on campus
        LatLng campus = new LatLng(USC_LAT, USC_LNG);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(campus, 16.5f));
        
        loadStopsOnMap();
    }

    private void loadStopsOnMap() {
        db.child("shuttleStops").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allStops.clear();
                for (DataSnapshot stopSnapshot : snapshot.getChildren()) {
                    ShuttleStop stop = stopSnapshot.getValue(ShuttleStop.class);
                    if (stop != null && stop.getLatitude() != 0) {
                        allStops.add(stop);
                        fetchWaitingCount(stop.getStopName());
                    }
                }
                updateMapMarkers();
                new Handler(Looper.getMainLooper()).postDelayed(() -> zoomToFitAll(), 1500); 
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchWaitingCount(String stopName) {
        db.child("scans").child(getScanKey(stopName)).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = (int) snapshot.getChildrenCount();
                stopWaitingCounts.put(stopName, count);
                updateMapMarkers();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String getScanKey(String stopName) {
        if (stopName.equalsIgnoreCase("SAFAD")) return "SAFAD Building_com";
        if (stopName.equalsIgnoreCase("Bunzel")) return "Bunzel_com";
        return stopName + "_com";
    }

    private void zoomToFitAll() {
        if (googleMap == null || allStops.isEmpty()) return;
        
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        int validPoints = 0;
        for (ShuttleStop stop : allStops) {
            if (stop.getLatitude() != 0) {
                builder.include(new LatLng(stop.getLatitude(), stop.getLongitude()));
                validPoints++;
            }
        }
        
        // Only include stops to keep map centered on campus even if user is far away
        if (validPoints == 0) {
             googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(USC_LAT, USC_LNG), 16.5f));
             return;
        }

        LatLngBounds bounds = builder.build();
        try {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100)); 
        } catch (IllegalStateException e) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.getCenter(), 16.5f));
        }
    }

    private void updateMapMarkers() {
        if (googleMap == null) return;
        
        // 1. Update Stop Markers
        for (ShuttleStop stop : allStops) {
            int count = stopWaitingCounts.getOrDefault(stop.getStopName(), 0);
            Bitmap icon = createMarkerBitmap(stop.getStopName(), count);
            Marker existing = stopMarkers.get(stop.getStopName());
            if (existing != null) {
                existing.setIcon(BitmapDescriptorFactory.fromBitmap(icon));
            } else {
                Marker m = googleMap.addMarker(new MarkerOptions()
                        .position(new LatLng(stop.getLatitude(), stop.getLongitude()))
                        .icon(BitmapDescriptorFactory.fromBitmap(icon)));
                stopMarkers.put(stop.getStopName(), m);
            }
        }

        // 2. Update User Marker
        if (userLat != 0) {
            LatLng userPos = new LatLng(userLat, userLng);
            if (userMarker == null) {
                userMarker = googleMap.addMarker(new MarkerOptions().position(userPos)
                        .icon(bitmapDescriptorFromVector(this, R.drawable.ic_person, 28, 28)));
            } else {
                userMarker.setPosition(userPos);
            }
        }

        // 3. Update Shuttle Markers
        for (ShuttleItem s : shuttleList) {
            if (s.isAvailable()) {
                LatLng pos = new LatLng(s.getDriverLat(), s.getDriverLng());
                Marker existing = shuttleMarkers.get(s.getShuttleId());
                if (existing != null) {
                    existing.setPosition(pos);
                } else {
                    Marker m = googleMap.addMarker(new MarkerOptions().position(pos)
                            .icon(bitmapDescriptorFromVector(this, R.drawable.ic_shuttle, 28, 28)));
                    shuttleMarkers.put(s.getShuttleId(), m);
                }
            } else {
                Marker existing = shuttleMarkers.remove(s.getShuttleId());
                if (existing != null) existing.remove();
            }
        }
    }

    private Bitmap createMarkerBitmap(String name, int count) {
        View markerView = getLayoutInflater().inflate(R.layout.marker_stop, null);
        TextView tvName = markerView.findViewById(R.id.tvMarkerName);
        TextView tvCount = markerView.findViewById(R.id.tvMarkerWaiting);
        View viewDot = markerView.findViewById(R.id.viewDot);

        if (tvName != null) tvName.setText(name);
        if (tvCount != null) {
            tvCount.setText(String.valueOf(count));
            
            // Apply color logic: Green if > 0, Grey if 0
            int greenColor = Color.parseColor("#4CAF50");
            int greyColor  = Color.parseColor("#9E9E9E");
            int colorToApply = (count > 0) ? greenColor : greyColor;
            
            tvCount.setTextColor(colorToApply);
            if (viewDot != null) {
                Drawable background = viewDot.getBackground();
                if (background != null) {
                    background.mutate().setTint(colorToApply);
                } else {
                    viewDot.setBackgroundColor(colorToApply);
                }
            }
        }

        return getBitmapFromView(markerView);
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.getMenu().clear();
        nav.inflateMenu(R.menu.menu_passenger);
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                Toast.makeText(this, "History coming soon!", Toast.LENGTH_SHORT).show();
                return true;
            }
            return id == R.id.nav_home;
        });
    }

    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); if (mapView != null) { mapView.onPause(); if (fusedLocationClient != null) fusedLocationClient.removeLocationUpdates(locationCallback); } }
    @Override protected void onDestroy() { super.onDestroy(); statusMonitorHandler.removeCallbacksAndMessages(null); if (mapView != null) mapView.onDestroy(); }
}
