package com.usc.passakay;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminDriversFragment extends Fragment {

    private ListView listView;
    private DatabaseReference db;
    private List<User> allDrivers = new ArrayList<>();
    private List<String> driverNames = new ArrayList<>();
    private List<String> driverIds = new ArrayList<>();
    private List<Shuttle> shuttleList = new ArrayList<>();
    private ShuttleAdapter adminShuttleAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_drivers, container, false);

        listView = view.findViewById(R.id.listShuttles);
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // Initialize 5 shuttles if they don't exist, or just load them
        loadDriversAndShuttles();

        return view;
    }

    private void loadDriversAndShuttles() {
        // First, load all available drivers
        db.child("users").orderByChild("role").equalTo("driver")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        allDrivers.clear();
                        driverNames.clear();
                        driverIds.clear();

                        driverNames.add("No Driver");
                        driverIds.add("");

                        for (DataSnapshot child : snapshot.getChildren()) {
                            User user = child.getValue(User.class);
                            if (user != null) {
                                allDrivers.add(user);
                                driverNames.add(user.getFirstName() + " " + user.getLastName());
                                driverIds.add(child.getKey());
                            }
                        }
                        loadShuttles();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadShuttles() {
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                shuttleList.clear();
                // Ensure we have 5 shuttles
                for (int i = 1; i <= 5; i++) {
                    DataSnapshot shuttleSnap = snapshot.child(String.valueOf(i));
                    if (shuttleSnap.exists()) {
                        shuttleList.add(shuttleSnap.getValue(Shuttle.class));
                    } else {
                        // Create a default shuttle if it doesn't exist
                        Shuttle newShuttle = new Shuttle(i, "GWX-10" + i);
                        db.child("shuttles").child(String.valueOf(i)).setValue(newShuttle);
                        shuttleList.add(newShuttle);
                    }
                }
                setupListView();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupListView() {
        if (getContext() == null) return;

        ArrayAdapter<Shuttle> adapter = new ArrayAdapter<Shuttle>(getContext(),
                R.layout.item_admin_shuttle, shuttleList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_admin_shuttle, parent, false);
                }

                Shuttle shuttle = shuttleList.get(position);
                TextView tvName = convertView.findViewById(R.id.tvShuttleName);
                TextView tvPlate = convertView.findViewById(R.id.tvPlateNumber);
                Spinner spinnerDrivers = convertView.findViewById(R.id.spinnerDrivers);

                tvName.setText("Shuttle " + shuttle.getShuttleId());
                tvPlate.setText("Plate Number: " + shuttle.getPlateNumber());

                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getContext(),
                        android.R.layout.simple_spinner_item, driverNames);
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerDrivers.setAdapter(spinnerAdapter);

                // Set current driver in spinner
                int selectionIndex = 0;
                if (shuttle.getDriverId() != null && !shuttle.getDriverId().isEmpty()) {
                    selectionIndex = driverIds.indexOf(shuttle.getDriverId());
                    if (selectionIndex < 0) selectionIndex = 0;
                }
                spinnerDrivers.setSelection(selectionIndex, false);

                spinnerDrivers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        String selectedDriverId = driverIds.get(pos);
                        String selectedDriverName = driverNames.get(pos);

                        // Only update if it actually changed to avoid infinite loop from listener
                        if (!selectedDriverId.equals(shuttle.getDriverId())) {
                            updateShuttleDriver(shuttle.getShuttleId(), selectedDriverId, selectedDriverName);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

                return convertView;
            }
        };
        listView.setAdapter(adapter);
    }

    private void updateShuttleDriver(int shuttleId, String driverId, String driverName) {
        db.child("shuttles").child(String.valueOf(shuttleId)).child("driverId").setValue(driverId);
        db.child("shuttles").child(String.valueOf(shuttleId)).child("driverName").setValue(driverName)
                .addOnSuccessListener(a -> Toast.makeText(getContext(), "Driver assigned to Shuttle " + shuttleId, Toast.LENGTH_SHORT).show());
    }
}