package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DataSeeder seeder = new DataSeeder();
        seeder.seedAll();

        // ─── Check login first ───────────────────────────
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            // Not logged in → go to login screen
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return; // stop here, don't load the map
        }

        // ─── Logged in → load map ────────────────────────
        setContentView(R.layout.activity_main);

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng usc = new LatLng(10.3535, 123.9109);

        mMap.addMarker(new MarkerOptions()
                .position(usc)
                .title("University of San Carlos"));

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(usc, 17));
    }
}
