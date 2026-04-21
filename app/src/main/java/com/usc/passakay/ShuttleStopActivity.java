package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the map + list of shuttle stops for a given bus.
 * Launched when the driver taps the green "Standby" button on the dashboard.
 *
 * Expected Intent extras:
 *   - EXTRA_BUS_NAME   (String)  – e.g. "Bus 2"
 *   - EXTRA_DRIVER_LAT (double)  – shuttle's current latitude  (optional, falls back to USC)
 *   - EXTRA_DRIVER_LNG (double)  – shuttle's current longitude (optional, falls back to USC)
 */
public class ShuttleStopActivity extends BaseActivity implements OnMapReadyCallback {

    public static final String EXTRA_BUS_NAME   = "extra_bus_name";
    public static final String EXTRA_DRIVER_LAT = "extra_driver_lat";
    public static final String EXTRA_DRIVER_LNG = "extra_driver_lng";

    // USC campus center (fallback)
    private static final double DEFAULT_LAT = 10.3157;
    private static final double DEFAULT_LNG = 123.8854;

    private MapView mapView;
    private GoogleMap googleMap;
    private double driverLat, driverLng;
    private String busName;

    private RecyclerView recyclerStops;
    private StopAdapter stopAdapter;
    private final List<StopItem> stopList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shuttle_stop);

        // Read intent extras
        busName   = getIntent().getStringExtra(EXTRA_BUS_NAME);
        driverLat = getIntent().getDoubleExtra(EXTRA_DRIVER_LAT, DEFAULT_LAT);
        driverLng = getIntent().getDoubleExtra(EXTRA_DRIVER_LNG, DEFAULT_LNG);

        // Map
        mapView = findViewById(R.id.mapViewStops);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Stops RecyclerView
        recyclerStops = findViewById(R.id.recyclerStops);
        recyclerStops.setLayoutManager(new LinearLayoutManager(this));
        stopAdapter = new StopAdapter(this, stopList);
        recyclerStops.setAdapter(stopAdapter);

        loadFakeStops();
        setupBottomNav();
    }

    // TODO: Replace with real Firebase / backend data
    private void loadFakeStops() {
        stopList.add(new StopItem("Portal Terminal",  4, 324));
        stopList.add(new StopItem("Bunzel Building",  0, 324));
        stopList.add(new StopItem("SAFAD Building",   4, 324));
        stopList.add(new StopItem("SAS Building",     4, 324));
        stopList.add(new StopItem("SHCP Building",    0, 324));
        stopAdapter.notifyDataSetChanged();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;
        MapsInitializer.initialize(this);

        LatLng shuttleLoc = new LatLng(driverLat, driverLng);
        googleMap.addMarker(new MarkerOptions()
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
                startActivity(new Intent(this, DriverDashboardActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                // TODO: navigate to history
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    // ── MapView lifecycle forwarding ──────────────────────────────────────────

    @Override protected void onResume()  { super.onResume();  mapView.onResume(); }
    @Override protected void onPause()   { super.onPause();   mapView.onPause();  }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy();}
    @Override public    void onLowMemory()          { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        mapView.onSaveInstanceState(out);
    }
}