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
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
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
import com.google.android.material.behavior.SwipeDismissBehavior;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
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
    private TextView tvUserLoc, tvNextStop, tvNearestStopDistance, tvAnnouncement;
    private LinearLayout layoutTripProgress;
    private ProgressBar progressBarTrip;
    private CardView cardStatus, cardAnnouncement;
    private TextInputLayout layoutDestination;
    private AutoCompleteTextView spinnerDestination;
    private String selectedDestination = "";
    private String lastDismissedAnnouncementId = "";
    
    private boolean isWaiting = false;
    private boolean isRiding = false;
    private String waitingAtStopName = "";
    private int ridingShuttleId = -1;
    private long outOfRadiusStartTime = 0;
    private User currentUserObj;

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

        cardStatus = findViewById(R.id.cardStatus);
        btnWaitingStatus = findViewById(R.id.btnWaitingStatus);
        btnCancelRide = findViewById(R.id.btnCancelRide);
        tvUserLoc = findViewById(R.id.tvUserLoc);
        tvNextStop = findViewById(R.id.tvNextStop);
        tvNearestStopDistance = findViewById(R.id.tvNearestStopDistance);
        layoutTripProgress = findViewById(R.id.layoutTripProgress);
        progressBarTrip = findViewById(R.id.progressBarTrip);
        cardAnnouncement = findViewById(R.id.cardAnnouncement);
        tvAnnouncement = findViewById(R.id.tvAnnouncement);
        
        layoutDestination = findViewById(R.id.layoutDestination);
        spinnerDestination = findViewById(R.id.spinnerDestination);

        mapView = findViewById(R.id.mapViewMain);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        recyclerShuttles = findViewById(R.id.recyclerShuttles);
        recyclerShuttles.setLayoutManager(new LinearLayoutManager(this));
        shuttleAdapter = new ShuttleAdapter(this, shuttleList, getSupportFragmentManager());
        recyclerShuttles.setAdapter(shuttleAdapter);

        btnWaitingStatus.setOnClickListener(v -> toggleWaitingStatus());
        btnCancelRide.setOnClickListener(v -> confirmCancelRide());
        
        FloatingActionButton fabScan = findViewById(R.id.fabScanQR);
        fabScan.setOnClickListener(v -> {
            if (isWaiting || isRiding) {
                startActivity(new Intent(this, ScannerActivity.class));
            } else if (selectedDestination.isEmpty()) {
                Toast.makeText(this, "Please select a destination before scanning!", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(this, ScannerActivity.class);
                intent.putExtra("destination", selectedDestination);
                startActivity(intent);
            }
        });

        findViewById(R.id.fabMyLocation).setOnClickListener(v -> panToMyLocation());
        findViewById(R.id.fabAIChat).setOnClickListener(v -> startActivity(new Intent(this, AIChatActivity.class)));

        View ivDismiss = findViewById(R.id.ivDismissAnnouncement);
        if (ivDismiss != null) {
            ivDismiss.setOnClickListener(v -> dismissAnnouncement());
        }

        setupSwipeToDismiss();

        loadShuttles();
        loadAnnouncements();
        setupBottomNav();
        startLocationUpdates();
        syncUserStatus();
        startStatusMonitor();
        listenForAIUpdates();

        spinnerDestination.setOnItemClickListener((parent, view, position, id) -> {
            selectedDestination = (String) parent.getItemAtPosition(position);
            saveDestinationToDB(selectedDestination);
        });
    }

    private void saveDestinationToDB(String dest) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            db.child("users").child(uid).child("selectedDestination").setValue(dest);
        }
    }

    private void listenForAIUpdates() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.child("users").child(uid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    // AI recommendation tips could go here
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void syncUserStatus() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.child("users").child(uid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentUserObj = snapshot.getValue(User.class);
                if (currentUserObj != null) {
                    isWaiting = currentUserObj.isWaiting;
                    isRiding = currentUserObj.isRiding;
                    waitingAtStopName = currentUserObj.waitingAt != null ? currentUserObj.waitingAt : "";
                    ridingShuttleId = currentUserObj.ridingShuttleId;
                    
                    if (currentUserObj.selectedDestination != null && !currentUserObj.selectedDestination.isEmpty()) {
                        selectedDestination = currentUserObj.selectedDestination;
                        spinnerDestination.setText(selectedDestination, false);
                    }
                    
                    updateStatusUI();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateStatusUI() {
        if (currentUserObj == null) return;

        // Hide destination picker if already waiting or riding
        if (isWaiting || isRiding) {
            layoutDestination.setVisibility(View.GONE);
        } else {
            layoutDestination.setVisibility(View.VISIBLE);
        }

        if (isRiding) {
            cardStatus.setCardBackgroundColor(Color.parseColor("#14D337")); // Green
            btnWaitingStatus.setText("ON RIDE");
            btnWaitingStatus.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50)); 
            layoutTripProgress.setVisibility(View.VISIBLE);
            btnCancelRide.setVisibility(View.VISIBLE);
            btnCancelRide.setText("CANCEL RIDE");
            
            if (ridingShuttleId != -1) {
                tvUserLoc.setText("Riding Shuttle " + ridingShuttleId);
            }
        } else if (isWaiting) {
            if ("assigned".equals(currentUserObj.allocationStatus) && currentUserObj.assignedShuttleId > 0) {
                // SHOW ASSIGNED DETAILS
                cardStatus.setCardBackgroundColor(Color.parseColor("#2196F3")); // Blue
                btnWaitingStatus.setText("ASSIGNED");
                btnWaitingStatus.setBackgroundTintList(ColorStateList.valueOf(0xFF1976D2));
                
                String busInfo = "Bus " + currentUserObj.assignedShuttleId;
                if (currentUserObj.assignedPlate != null && !currentUserObj.assignedPlate.isEmpty()) {
                    busInfo += " • " + currentUserObj.assignedPlate;
                }
                tvUserLoc.setText(busInfo);
                
                if (tvNearestStopDistance != null) {
                    tvNearestStopDistance.setText("Arriving in ~" + currentUserObj.estimatedWaitMinutes + " mins • Queue #" + currentUserObj.queuePosition);
                }
                
                layoutTripProgress.setVisibility(View.GONE);
                btnCancelRide.setVisibility(View.VISIBLE);
                btnCancelRide.setText("CANCEL WAITING");
            } else {
                // REGULAR WAITING
                cardStatus.setCardBackgroundColor(Color.parseColor("#FF5252")); // Red
                btnWaitingStatus.setText("WAITING");
                btnWaitingStatus.setBackgroundTintList(ColorStateList.valueOf(0xFFD32F2F));
                
                tvUserLoc.setText("Waiting at: " + waitingAtStopName);
                if (tvNearestStopDistance != null) tvNearestStopDistance.setText("In Queue... Waiting for AI Dispatch");
                
                layoutTripProgress.setVisibility(View.GONE);
                btnCancelRide.setVisibility(View.VISIBLE);
                btnCancelRide.setText("CANCEL WAITING");
            }
        } else {
            cardStatus.setCardBackgroundColor(Color.parseColor("#14D337")); // Green
            btnWaitingStatus.setText("NOT WAITING");
            btnWaitingStatus.setBackgroundTintList(ColorStateList.valueOf(0xFFFFEA08)); 
            layoutTripProgress.setVisibility(View.GONE);
            btnCancelRide.setVisibility(View.GONE);
            if (tvNearestStopDistance != null) tvNearestStopDistance.setText("");
            updateNearestStopLabel();
        }
    }

    private void updateNearestStopLabel() {
        if (userLat == 0 || allStops.isEmpty() || isWaiting || isRiding) return;

        ShuttleStop nearest = null;
        float minDist = Float.MAX_VALUE;

        for (ShuttleStop stop : allStops) {
            float[] results = new float[1];
            Location.distanceBetween(userLat, userLng, stop.getLatitude(), stop.getLongitude(), results);
            if (results[0] < minDist) {
                minDist = results[0];
                nearest = stop;
            }
        }

        if (nearest != null) {
            tvUserLoc.setText("Nearest Stop: " + nearest.getStopName());
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid != null) {
                db.child("users").child(uid).child("nearestStop").setValue(nearest.getStopName());
            }
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

        if (isWaiting && !waitingAtStopName.isEmpty()) {
            String stopKey = getScanKey(waitingAtStopName);
            db.child("scans").child(stopKey).child(uid).removeValue();
            new QueueManager().leaveQueue(waitingAtStopName);

            db.child("stopWaitingCounts").child(stopKey).runTransaction(new com.google.firebase.database.Transaction.Handler() {
                @NonNull
                @Override
                public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData mutableData) {
                    Integer count = mutableData.getValue(Integer.class);
                    if (count != null && count > 0) mutableData.setValue(count - 1);
                    return com.google.firebase.database.Transaction.success(mutableData);
                }
                @Override
                public void onComplete(com.google.firebase.database.DatabaseError databaseError, boolean b, com.google.firebase.database.DataSnapshot dataSnapshot) {}
            });
        }

        DatabaseReference ref = db.child("users").child(uid);
        Map<String, Object> updates = new HashMap<>();
        updates.put("isWaiting", false);
        updates.put("isRiding", false);
        updates.put("waitingAt", "");
        updates.put("ridingShuttleId", -1);
        updates.put("assignedShuttleId", -1);
        updates.put("assignedPlate", "");
        updates.put("assignedStop", "");
        updates.put("queuePosition", 0);
        updates.put("allocationStatus", "");
        updates.put("estimatedWaitMinutes", 0);
        updates.put("selectedDestination", "");
        
        ref.updateChildren(updates);
        Toast.makeText(this, "Status cleared", Toast.LENGTH_SHORT).show();
        
        selectedDestination = "";
        spinnerDestination.setText("", false);
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
                        updateNearestStopLabel();
                        if (shuttleAdapter != null) {
                            shuttleAdapter.updateUserLocation(userLat, userLng);
                        }
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
        if (!isWaiting && selectedDestination.isEmpty()) {
            Toast.makeText(this, "Please select a destination first!", Toast.LENGTH_SHORT).show();
            return;
        }
        updateWaitingStatusInDB(!isWaiting);
    }

    private void updateWaitingStatusInDB(boolean waiting) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            if (!waiting) {
                clearStatusInDB();
            } else {
                db.child("users").child(uid).child("isWaiting").setValue(true);
            }
        }
    }

    private long currentAnnouncementTimestamp = 0;

    private void dismissAnnouncement() {
        cardAnnouncement.setVisibility(View.GONE);
        lastDismissedAnnouncementId = currentAnnouncementTimestamp + "";
    }

    private void setupSwipeToDismiss() {
        final SwipeDismissBehavior<CardView> swipe = new SwipeDismissBehavior<>();
        swipe.setSwipeDirection(SwipeDismissBehavior.SWIPE_DIRECTION_ANY);
        swipe.setListener(new SwipeDismissBehavior.OnDismissListener() {
            @Override
            public void onDismiss(View view) {
                dismissAnnouncement();
            }
            @Override public void onDragStateChanged(int state) {}
        });

        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) cardAnnouncement.getLayoutParams();
        params.setBehavior(swipe);
    }

    private void loadAnnouncements() {
        if (cardAnnouncement == null || tvAnnouncement == null) return;

        db.child("announcements").child("current").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String message = snapshot.child("message").getValue(String.class);
                    String priority = snapshot.child("priority").getValue(String.class);
                    Long expiresAt = snapshot.child("expiresAt").getValue(Long.class);
                    long timestamp = snapshot.child("timestamp").getValue(Long.class) != null ? snapshot.child("timestamp").getValue(Long.class) : 0;
                    
                    currentAnnouncementTimestamp = timestamp;

                    // If the user already dismissed this specific announcement, keep it hidden
                    if (String.valueOf(timestamp).equals(lastDismissedAnnouncementId)) {
                        cardAnnouncement.setVisibility(View.GONE);
                        return;
                    }

                    if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                        cardAnnouncement.setVisibility(View.GONE);
                        return;
                    }

                    tvAnnouncement.setText(message);
                    tvAnnouncement.setSelected(true); // For marquee effect
                    cardAnnouncement.setVisibility(View.VISIBLE);

                    // Update Badge and Time if they exist in layout
                    View newBadge = findViewById(R.id.tvNewBadge);
                    if (newBadge != null) {
                        boolean isRecent = (System.currentTimeMillis() - timestamp) < (5 * 60 * 1000); // 5 mins
                        newBadge.setVisibility(isRecent ? View.VISIBLE : View.GONE);
                    }

                    TextView tvTime = findViewById(R.id.tvAnnouncementTime);
                    if (tvTime != null && timestamp > 0) {
                        tvTime.setText(android.text.format.DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS));
                    }

                    // Priority Colors
                    if ("warning".equals(priority)) {
                        cardAnnouncement.setCardBackgroundColor(Color.parseColor("#FFE0B2"));
                    } else if ("emergency".equals(priority)) {
                        cardAnnouncement.setCardBackgroundColor(Color.parseColor("#FFCDD2"));
                        // Optional pulse animation
                        android.view.animation.Animation pulse = new android.view.animation.AlphaAnimation(1.0f, 0.6f);
                        pulse.setDuration(800);
                        pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
                        pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
                        cardAnnouncement.startAnimation(pulse);
                    } else {
                        cardAnnouncement.setCardBackgroundColor(Color.parseColor("#FFF9C4"));
                        cardAnnouncement.clearAnimation();
                    }
                } else {
                    cardAnnouncement.setVisibility(View.GONE);
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
                        boolean isOnline = s.isActive() && s.getDriverId() != null && !s.getDriverId().isEmpty();
                        shuttleList.add(new ShuttleItem(String.valueOf(s.getShuttleId()), "Bus " + s.getShuttleId(), 
                            s.getDriverName(), s.getPlateNumber(), 5, isOnline, s.getCurrentLat(), s.getCurrentLng()));
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
        
        // Suggestion #2: Map Barrier (Campus Bounds)
        LatLng southWest = new LatLng(10.345, 123.900);
        LatLng northEast = new LatLng(10.365, 123.925);
        LatLngBounds campusBounds = new LatLngBounds(southWest, northEast);
        googleMap.setLatLngBoundsForCameraTarget(campusBounds);
        googleMap.setMinZoomPreference(15.0f);

        LatLng campus = new LatLng(USC_LAT, USC_LNG);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(campus, 16.5f));
        loadStopsOnMap();
    }

    private void loadStopsOnMap() {
        db.child("shuttleStops").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allStops.clear();
                List<String> stopNames = new ArrayList<>();
                for (DataSnapshot stopSnapshot : snapshot.getChildren()) {
                    ShuttleStop stop = stopSnapshot.getValue(ShuttleStop.class);
                    if (stop != null && stop.getLatitude() != 0) {
                        allStops.add(stop);
                        stopNames.add(stop.getStopName());
                        fetchWaitingCount(stop.getStopName());
                    }
                }
                
                // ✅ Populate Dropdown
                ArrayAdapter<String> adapter = new ArrayAdapter<>(PassengerHomeActivity.this,
                    android.R.layout.simple_dropdown_item_1line, stopNames);
                spinnerDestination.setAdapter(adapter);

                updateMapMarkers();
                updateNearestStopLabel();
                new Handler(Looper.getMainLooper()).postDelayed(() -> zoomToFitAll(), 1500); 
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchWaitingCount(String stopName) {
        String stopKey = getScanKey(stopName);
        db.child("stopWaitingCounts").child(stopKey).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer countObj = snapshot.getValue(Integer.class);
                int count = countObj != null ? countObj : 0;
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
        if (userLat != 0) {
            LatLng userPos = new LatLng(userLat, userLng);
            if (userMarker == null) {
                userMarker = googleMap.addMarker(new MarkerOptions().position(userPos)
                        .icon(bitmapDescriptorFromVector(this, R.drawable.ic_person, 28, 28)));
            } else {
                userMarker.setPosition(userPos);
            }
        }
        for (ShuttleItem s : shuttleList) {
            if (s.isAvailable() && s.getDriverLat() != 0) {
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
            int greenColor = Color.parseColor("#4CAF50");
            int greyColor  = Color.parseColor("#9E9E9E");
            int colorToApply = (count > 0) ? greenColor : greyColor;
            tvCount.setTextColor(colorToApply);
            if (viewDot != null) {
                Drawable background = viewDot.getBackground();
                if (background != null) background.mutate().setTint(colorToApply);
                else viewDot.setBackgroundColor(colorToApply);
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

    @Override public void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override public void onPause() { super.onPause(); if (mapView != null) { mapView.onPause(); if (fusedLocationClient != null) fusedLocationClient.removeLocationUpdates(locationCallback); } }
    @Override public void onDestroy() { super.onDestroy(); statusMonitorHandler.removeCallbacksAndMessages(null); if (mapView != null) mapView.onDestroy(); }
}
