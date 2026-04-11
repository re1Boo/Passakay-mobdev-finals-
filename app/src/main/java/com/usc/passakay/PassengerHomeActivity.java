package com.usc.passakay;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.LinearInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PassengerHomeActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "PassengerHome";
    private static final LatLng USC_TC = new LatLng(10.3541, 123.9115);
    private static final float DEFAULT_ZOOM = 16f;
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private GoogleMap mMap;
    private DatabaseReference dbRef;
    private HashMap<String, Marker> shuttleMarkers = new HashMap<>();
    private List<Marker> stopMarkers = new ArrayList<>();

    // =========================================================
    //  LIFECYCLE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger_home);

        initFirebase();
        initMap();
        setupLogoutButton();
    }

    // =========================================================
    //  INIT
    // =========================================================

    private void initFirebase() {
        dbRef = FirebaseDatabase.getInstance().getReference();
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "mapFragment is null — check your layout XML.");
        }
    }

    private void setupLogoutButton() {
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> logoutUser());
    }

    // =========================================================
    //  MAP READY
    // =========================================================

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(USC_TC, DEFAULT_ZOOM));

        // Map UI Controls
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(true);

        enableMyLocation();
        loadShuttleStops();
        trackLiveShuttles();
    }

    // =========================================================
    //  MY LOCATION
    // =========================================================

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            }
        }
    }

    // =========================================================
    //  FIREBASE — LOAD SHUTTLE STOPS
    // =========================================================

    private void loadShuttleStops() {
        dbRef.child("shuttleStops")
                .addValueEventListener(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d(TAG, "shuttleStops onDataChange fired. Child count: " + snapshot.getChildrenCount());

                        for (Marker m : stopMarkers) {
                            m.remove();
                        }
                        stopMarkers.clear();

                        if (!snapshot.exists()) {
                            Log.w(TAG, "No shuttleStops found in database.");
                            return;
                        }

                        int count = 0;
                        for (DataSnapshot stopSnapshot : snapshot.getChildren()) {
                            ShuttleStop stop = stopSnapshot.getValue(ShuttleStop.class);

                            if (stop == null) {
                                Log.w(TAG, "Skipping null entry: " + stopSnapshot.getKey());
                                continue;
                            }

                            LatLng position = new LatLng(stop.getLatitude(), stop.getLongitude());
                            Marker marker = mMap.addMarker(new MarkerOptions()
                                    .position(position)
                                    .title(stop.getStopName()));

                            stopMarkers.add(marker);
                            count++;
                            Log.d(TAG, "Marker placed: " + stop.getStopName());
                        }

                        Toast.makeText(PassengerHomeActivity.this,
                                count + " shuttle stop(s) loaded.",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "loadShuttleStops failed: " + error.getMessage());
                        Toast.makeText(PassengerHomeActivity.this,
                                "Failed to load stops: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // =========================================================
    //  FIREBASE — TRACK LIVE SHUTTLES
    // =========================================================

    private void trackLiveShuttles() {
        dbRef.child("shuttle_locations")
                .addValueEventListener(new ValueEventListener() {

                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            Log.w(TAG, "No active shuttles found.");
                            return;
                        }

                        for (DataSnapshot shuttleSnapshot : snapshot.getChildren()) {
                            String shuttleId = shuttleSnapshot.getKey();

                            Double lat = shuttleSnapshot.child("latitude").getValue(Double.class);
                            Double lng = shuttleSnapshot.child("longitude").getValue(Double.class);
                            String plate = shuttleSnapshot.child("plateNumber").getValue(String.class);

                            if (lat == null || lng == null || shuttleId == null) {
                                Log.w(TAG, "Skipping shuttle with incomplete data: " + shuttleId);
                                continue;
                            }

                            LatLng newPosition = new LatLng(lat, lng);

                            if (shuttleMarkers.containsKey(shuttleId)) {
                                // Smooth animation instead of teleporting
                                animateMarker(shuttleMarkers.get(shuttleId), newPosition);
                                Log.d(TAG, "Shuttle animating: " + shuttleId + " → " + lat + ", " + lng);
                            } else {
                                // New shuttle — create marker with custom icon
                                Marker marker = mMap.addMarker(new MarkerOptions()
                                        .position(newPosition)
                                        .title("Shuttle: " + (plate != null ? plate : shuttleId))
                                        .icon(BitmapDescriptorFactory.fromBitmap(
                                                Bitmap.createScaledBitmap(
                                                        BitmapFactory.decodeResource(getResources(), R.drawable.ic_shuttle),
                                                        100, 100, false))));

                                shuttleMarkers.put(shuttleId, marker);
                                Log.d(TAG, "New shuttle added: " + shuttleId);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "trackLiveShuttles failed: " + error.getMessage());
                    }
                });
    }

    // =========================================================
    //  ANIMATE MARKER
    // =========================================================

    private void animateMarker(Marker marker, LatLng toPosition) {
        LatLng startPosition = marker.getPosition();

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1500);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            double lat = startPosition.latitude +
                    (toPosition.latitude - startPosition.latitude) * fraction;
            double lng = startPosition.longitude +
                    (toPosition.longitude - startPosition.longitude) * fraction;
            marker.setPosition(new LatLng(lat, lng));
        });

        animator.start();
    }

    // =========================================================
    //  LOGOUT
    // =========================================================

    private void logoutUser() {
        FirebaseAuth.getInstance().signOut();

        Intent intent = new Intent(PassengerHomeActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}