package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private DatabaseReference db;
    private BottomNavigationView bottomNav;
    private List<ShuttleStop> shuttleStops = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check session
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // Initialize bottom nav
        bottomNav = findViewById(R.id.bottom_nav);
        setupBottomNav();

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupBottomNav() {
        if (bottomNav == null) return;
        bottomNav.setVisibility(View.VISIBLE);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // already on home
                return true;
            } else if (id == R.id.nav_history) {
                // TODO: go to history
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // USC Cebu coordinates
        LatLng usc = new LatLng(10.3535, 123.9109);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(usc, 17));

        // Map settings
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Load shuttle stops after map is ready
        loadShuttleStops();
    }

    private void loadShuttleStops() {
        db.child("shuttleStops")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    shuttleStops.clear();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        ShuttleStop stop = child.getValue(ShuttleStop.class);
                        if (stop != null) {
                            shuttleStops.add(stop);
                            addStopMarker(stop);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            });
    }

    private void addStopMarker(ShuttleStop stop) {
        if (mMap == null) return;

        LatLng position = new LatLng(stop.getLatitude(), stop.getLongitude());
        mMap.addMarker(new MarkerOptions()
                .position(position)
                .title(stop.getStopName())
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
    }
}