package com.usc.passakay;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminDriversFragment extends Fragment {

    private RecyclerView recyclerView;
    private DriverAdapter adapter;
    private DatabaseReference db;
    private FirebaseAuth mAuth;
    private List<User> driverList = new ArrayList<>();
    private List<String> uidList  = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_drivers, container, false);

        recyclerView = view.findViewById(R.id.recyclerDrivers);
        MaterialButton btnAddDriver = view.findViewById(R.id.btnAddDriver);
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        mAuth = FirebaseAuth.getInstance();

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DriverAdapter();
        recyclerView.setAdapter(adapter);

        loadDrivers();
        btnAddDriver.setOnClickListener(v -> showAddDriverDialog());

        return view;
    }

    private void loadDrivers() {
        db.child("users").orderByChild("role").equalTo("driver")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        driverList.clear();
                        uidList.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            User user = child.getValue(User.class);
                            if (user != null) {
                                driverList.add(user);
                                uidList.add(child.getKey());
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void showAddDriverDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_driver, null);

        EditText etFirstName = dialogView.findViewById(R.id.etFirstName);
        EditText etLastName  = dialogView.findViewById(R.id.etLastName);
        EditText etStudentId = dialogView.findViewById(R.id.etStudentId);
        EditText etEmail     = dialogView.findViewById(R.id.etEmail);
        EditText etPassword  = dialogView.findViewById(R.id.etPassword);

        new AlertDialog.Builder(getContext())
                .setTitle("Add New Driver")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String firstName = etFirstName.getText().toString().trim();
                    String lastName  = etLastName.getText().toString().trim();
                    String studentId = etStudentId.getText().toString().trim();
                    String email     = etEmail.getText().toString().trim();
                    String password  = etPassword.getText().toString().trim();

                    if (firstName.isEmpty() || lastName.isEmpty() || studentId.isEmpty() || email.isEmpty() || password.isEmpty()) {
                        Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createDriver(firstName, lastName, studentId, email, password);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createDriver(String firstName, String lastName, String studentId, String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    User driver = new User();
                    driver.setFirstName(firstName);
                    driver.setLastName(lastName);
                    driver.setStudentId(studentId);
                    driver.setEmail(email);
                    driver.setRole("driver");
                    driver.setStatus("active");
                    driver.setDepartmentId(0);
                    driver.setCourseId(0);

                    db.child("users").child(uid).setValue(driver)
                            .addOnSuccessListener(a -> Toast.makeText(getContext(), "Driver added!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Auth Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void removeDriver(String uid) {
        new AlertDialog.Builder(getContext())
                .setTitle("Remove Driver")
                .setMessage("Delete this driver account from the system?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.child("users").child(uid).removeValue()
                            .addOnSuccessListener(a -> Toast.makeText(getContext(), "Driver removed", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    class DriverAdapter extends RecyclerView.Adapter<DriverAdapter.DriverViewHolder> {
        @NonNull
        @Override
        public DriverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new DriverViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull DriverViewHolder holder, int position) {
            User driver = driverList.get(position);
            String uid = uidList.get(position);

            holder.tvName.setText(driver.getFirstName() + " " + driver.getLastName());
            holder.tvId.setText("ID: " + driver.getStudentId());
            holder.tvRole.setText("DRIVER");
            holder.tvStatus.setText(driver.getStatus().toUpperCase());

            holder.btnToggle.setText("REMOVE");
            holder.btnToggle.setTextColor(0xFFE53935);
            holder.btnToggle.setOnClickListener(v -> removeDriver(uid));
        }

        @Override
        public int getItemCount() { return driverList.size(); }

        class DriverViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvId, tvRole, tvStatus;
            MaterialButton btnToggle;
            DriverViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvId = itemView.findViewById(R.id.tvStudentId);
                tvRole = itemView.findViewById(R.id.tvRole);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                btnToggle = itemView.findViewById(R.id.btnToggle);
            }
        }
    }
}