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
import android.widget.Button;
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_drivers, container, false);
        listView = view.findViewById(R.id.listShuttles);
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        loadDriversAndShuttles();
        return view;
    }

    private void loadDriversAndShuttles() {
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
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadShuttles() {
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                shuttleList.clear();
                for (int i = 1; i <= 5; i++) {
                    DataSnapshot shuttleSnap = snapshot.child(String.valueOf(i));
                    if (shuttleSnap.exists()) {
                        shuttleList.add(shuttleSnap.getValue(Shuttle.class));
                    } else {
                        Shuttle newShuttle = new Shuttle(i, "GWX-10" + i);
                        db.child("shuttles").child(String.valueOf(i)).setValue(newShuttle);
                        shuttleList.add(newShuttle);
                    }
                }
                setupListView();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupListView() {
        if (getContext() == null || !(getActivity() instanceof BaseActivity)) return;

        final String currentDeviceId = ((BaseActivity) getActivity()).getAppInstanceId();

        ArrayAdapter<Shuttle> adapter = new ArrayAdapter<Shuttle>(getContext(),
                R.layout.item_admin_shuttle, shuttleList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_admin_shuttle, parent, false);
                }

                Shuttle shuttle = shuttleList.get(position);
                TextView tvName = convertView.findViewById(R.id.tvShuttleName);
                TextView tvPlate = convertView.findViewById(R.id.tvPlateNumber);
                Spinner spinnerDrivers = convertView.findViewById(R.id.spinnerDrivers);
                Button btnBind = convertView.findViewById(R.id.btnBindDevice);
                
                String boundDeviceId = shuttle.getDeviceId();
                boolean isBound = boundDeviceId != null && !boundDeviceId.isEmpty();
                boolean isBoundToMe = currentDeviceId.equals(boundDeviceId);

                tvName.setText("Shuttle " + shuttle.getShuttleId());
                
                if (isBoundToMe) {
                    tvPlate.setText("Plate: " + shuttle.getPlateNumber() + " (Bound to THIS phone)");
                    btnBind.setText("Unbind THIS Phone");
                } else if (isBound) {
                    tvPlate.setText("Plate: " + shuttle.getPlateNumber() + " (Bound to other device)");
                    btnBind.setText("Unbind Remote Device");
                } else {
                    tvPlate.setText("Plate: " + shuttle.getPlateNumber() + " (Unbound)");
                    btnBind.setText("Bind This Phone");
                }

                btnBind.setOnClickListener(v -> {
                    if (isBoundToMe || isBound) {
                        // REMOTE ACCESS: Clear binding regardless of which device it is
                        clearDeviceBinding(shuttle.getShuttleId());
                    } else {
                        bindCurrentDevice(shuttle.getShuttleId(), currentDeviceId);
                    }
                });

                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getContext(),
                        android.R.layout.simple_spinner_item, driverNames);
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerDrivers.setAdapter(spinnerAdapter);

                int selectionIndex = driverIds.indexOf(shuttle.getDriverId());
                spinnerDrivers.setSelection(selectionIndex < 0 ? 0 : selectionIndex, false);

                spinnerDrivers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        String newDriverId = driverIds.get(pos);
                        if (!newDriverId.equals(shuttle.getDriverId())) {
                            updateShuttleAssignment(shuttle, newDriverId, driverNames.get(pos));
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });

                return convertView;
            }
        };
        listView.setAdapter(adapter);
    }

    private void bindCurrentDevice(int shuttleId, String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) return;

        // Ensure this app instance is not bound to any other shuttle
        for (Shuttle s : shuttleList) {
            if (instanceId.equals(s.getDeviceId())) {
                db.child("shuttles").child(String.valueOf(s.getShuttleId())).child("deviceId").setValue("");
            }
        }

        db.child("shuttles").child(String.valueOf(shuttleId)).child("deviceId").setValue(instanceId)
                .addOnSuccessListener(a -> Toast.makeText(getContext(), "Bound this device to Shuttle " + shuttleId, Toast.LENGTH_SHORT).show());
    }

    private void clearDeviceBinding(int shuttleId) {
        db.child("shuttles").child(String.valueOf(shuttleId)).child("deviceId").setValue("")
            .addOnSuccessListener(a -> Toast.makeText(getContext(), "Device binding cleared remotely", Toast.LENGTH_SHORT).show());
    }

    private void updateShuttleAssignment(Shuttle shuttle, String newDriverId, String newDriverName) {
        int shuttleId = shuttle.getShuttleId();
        
        if (shuttle.getDriverId() != null && !shuttle.getDriverId().isEmpty()) {
            db.child("users").child(shuttle.getDriverId()).child("assignedShuttleId").setValue(-1);
        }
        
        if (!newDriverId.isEmpty()) {
            db.child("users").child(newDriverId).child("assignedShuttleId").setValue(shuttleId);
        }

        DatabaseReference shuttleRef = db.child("shuttles").child(String.valueOf(shuttleId));
        shuttleRef.child("driverId").setValue(newDriverId);
        shuttleRef.child("driverName").setValue(newDriverName);
        shuttleRef.child("status").setValue(newDriverId.isEmpty() ? "Standby" : "Deployed");
        shuttleRef.child("active").setValue(!newDriverId.isEmpty());
        
        Toast.makeText(getContext(), "Assignment updated", Toast.LENGTH_SHORT).show();
    }
}
