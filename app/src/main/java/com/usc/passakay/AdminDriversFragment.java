package com.usc.passakay;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminDriversFragment extends Fragment {

    private ListView listView;
    private Button btnAddDriver;
    private DatabaseReference db;
    private FirebaseAuth mAuth;
    private List<User> driverList = new ArrayList<>();
    private List<String> uidList  = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_drivers, container, false);

        listView     = view.findViewById(R.id.listDrivers);
        btnAddDriver = view.findViewById(R.id.btnAddDriver);
        db           = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        mAuth        = FirebaseAuth.getInstance();

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
                        setupListView();
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void setupListView() {
        ArrayAdapter<User> adapter = new ArrayAdapter<User>(getContext(),
                R.layout.item_user, driverList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_user, parent, false);
                }
                User driver = driverList.get(position);

                TextView tvName      = convertView.findViewById(R.id.tvName);
                TextView tvStudentId = convertView.findViewById(R.id.tvStudentId);
                TextView tvRole      = convertView.findViewById(R.id.tvRole);
                TextView tvStatus    = convertView.findViewById(R.id.tvStatus);
                Button btnToggle     = convertView.findViewById(R.id.btnToggle);

                tvName.setText(driver.getFirstName() + " " + driver.getLastName());
                tvStudentId.setText("ID: " + driver.getStudentId());
                tvRole.setText("Role: driver");
                tvStatus.setText("Status: " + driver.getStatus());
                btnToggle.setText("Remove");
                btnToggle.setBackgroundColor(0xFFE53935);
                btnToggle.setOnClickListener(v -> removeDriver(uidList.get(position)));

                return convertView;
            }
        };
        listView.setAdapter(adapter);
    }

    private void showAddDriverDialog() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_driver, null);

        EditText etFirstName = dialogView.findViewById(R.id.etFirstName);
        EditText etLastName  = dialogView.findViewById(R.id.etLastName);
        EditText etStudentId = dialogView.findViewById(R.id.etStudentId);
        EditText etEmail     = dialogView.findViewById(R.id.etEmail);
        EditText etPassword  = dialogView.findViewById(R.id.etPassword);

        new AlertDialog.Builder(getContext())
                .setTitle("Add Driver")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String firstName = etFirstName.getText().toString().trim();
                    String lastName  = etLastName.getText().toString().trim();
                    String studentId = etStudentId.getText().toString().trim();
                    String email     = etEmail.getText().toString().trim();
                    String password  = etPassword.getText().toString().trim();

                    if (firstName.isEmpty() || lastName.isEmpty() ||
                            studentId.isEmpty() || email.isEmpty() || password.isEmpty()) {
                        Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    createDriver(firstName, lastName, studentId, email, password);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createDriver(String firstName, String lastName,
                              String studentId, String email, String password) {
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
                            .addOnSuccessListener(a -> Toast.makeText(getContext(),
                                    "Driver added successfully!", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(getContext(),
                                    "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(),
                        "Failed to create driver: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void removeDriver(String uid) {
        new AlertDialog.Builder(getContext())
                .setTitle("Remove Driver")
                .setMessage("Are you sure you want to remove this driver?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    db.child("users").child(uid).removeValue()
                            .addOnSuccessListener(a -> Toast.makeText(getContext(),
                                    "Driver removed", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(getContext(),
                                    "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}