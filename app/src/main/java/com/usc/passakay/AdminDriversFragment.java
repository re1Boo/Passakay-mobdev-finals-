package com.usc.passakay;

import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminDriversFragment extends Fragment {

    private RecyclerView recyclerView;
    private ShuttleManagementAdapter adapter;
    private DatabaseReference db;
    private List<String> driverNames = new ArrayList<>();
    private List<String> driverIds = new ArrayList<>();
    private List<Shuttle> shuttleList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_drivers, container, false);
        
        recyclerView = view.findViewById(R.id.recyclerShuttles);
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ShuttleManagementAdapter();
        recyclerView.setAdapter(adapter);

        loadDriversAndShuttles();
        return view;
    }

    private void loadDriversAndShuttles() {
        db.child("users").orderByChild("role").equalTo("driver")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        driverNames.clear();
                        driverIds.clear();
                        driverNames.add("No Driver");
                        driverIds.add("");

                        for (DataSnapshot child : snapshot.getChildren()) {
                            User user = child.getValue(User.class);
                            if (user != null) {
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
                for (DataSnapshot shuttleSnap : snapshot.getChildren()) {
                    Shuttle s = shuttleSnap.getValue(Shuttle.class);
                    if (s != null) {
                        shuttleList.add(s);
                    }
                }
                // Fallback for initial data
                if (shuttleList.isEmpty()) {
                    for (int i = 1; i <= 3; i++) {
                        Shuttle newShuttle = new Shuttle(i, "GWX-10" + i);
                        db.child("shuttles").child(String.valueOf(i)).setValue(newShuttle);
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    class ShuttleManagementAdapter extends RecyclerView.Adapter<ShuttleManagementAdapter.ShuttleViewHolder> {
        @NonNull
        @Override
        public ShuttleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_shuttle, parent, false);
            return new ShuttleViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ShuttleViewHolder holder, int position) {
            Shuttle shuttle = shuttleList.get(position);
            holder.tvName.setText("Shuttle " + shuttle.getShuttleId());
            
            boolean isBound = shuttle.getDeviceId() != null && !shuttle.getDeviceId().isEmpty();
            holder.tvPlate.setText("Plate: " + shuttle.getPlateNumber() + (isBound ? " (Bound)" : " (Unbound)"));
            holder.btnBind.setText(isBound ? "UNBIND" : "BIND");

            holder.btnBind.setOnClickListener(v -> {
                if (isBound) clearDeviceBinding(shuttle.getShuttleId());
                else bindCurrentDevice(shuttle.getShuttleId());
            });

            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, driverNames);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            holder.spinnerDrivers.setAdapter(spinnerAdapter);

            int selectionIndex = driverIds.indexOf(shuttle.getDriverId());
            holder.spinnerDrivers.setSelection(selectionIndex < 0 ? 0 : selectionIndex, false);

            holder.spinnerDrivers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    String newDriverId = driverIds.get(pos);
                    if (!newDriverId.equals(shuttle.getDriverId())) {
                        updateShuttleAssignment(shuttle, newDriverId, driverNames.get(pos));
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        @Override
        public int getItemCount() { return shuttleList.size(); }

        class ShuttleViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPlate;
            Spinner spinnerDrivers;
            MaterialButton btnBind;
            ShuttleViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tvShuttleName);
                tvPlate = v.findViewById(R.id.tvPlateNumber);
                spinnerDrivers = v.findViewById(R.id.spinnerDrivers);
                btnBind = v.findViewById(R.id.btnBindDevice);
            }
        }
    }

    private void bindCurrentDevice(int shuttleId) {
        if (getContext() == null) return;
        String androidId = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        db.child("shuttles").child(String.valueOf(shuttleId)).child("deviceId").setValue(androidId)
                .addOnSuccessListener(a -> Toast.makeText(getContext(), "Bound to Shuttle " + shuttleId, Toast.LENGTH_SHORT).show());
    }

    private void clearDeviceBinding(int shuttleId) {
        db.child("shuttles").child(String.valueOf(shuttleId)).child("deviceId").setValue("")
            .addOnSuccessListener(a -> Toast.makeText(getContext(), "Device binding cleared", Toast.LENGTH_SHORT).show());
    }

    private void updateShuttleAssignment(Shuttle shuttle, String newDriverId, String newDriverName) {
        String oldDriverId = shuttle.getDriverId();
        int shuttleId = shuttle.getShuttleId();

        if (oldDriverId != null && !oldDriverId.isEmpty()) {
            db.child("users").child(oldDriverId).child("assignedShuttleId").setValue(-1);
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
