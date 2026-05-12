package com.usc.passakay;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AdminCreateDriverFragment extends Fragment {

    private EditText etFirstName, etLastName, etStudentId, etEmail, etPassword;
    private MaterialButton btnCreate;
    private DatabaseReference db;
    private FirebaseAuth mAuth;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_create_driver, container, false);

        etFirstName = view.findViewById(R.id.etFirstName);
        etLastName = view.findViewById(R.id.etLastName);
        etStudentId = view.findViewById(R.id.etStudentId);
        etEmail = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);
        btnCreate = view.findViewById(R.id.btnCreateDriver);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        mAuth = FirebaseAuth.getInstance();

        btnCreate.setOnClickListener(v -> handleCreateDriver());

        return view;
    }

    private void handleCreateDriver() {
        String fName = etFirstName.getText().toString().trim();
        String lName = etLastName.getText().toString().trim();
        String sId = etStudentId.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (fName.isEmpty() || lName.isEmpty() || sId.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCreate.setEnabled(false);
        btnCreate.setText("CREATING...");

        // Note: Creating a user with Firebase Auth on Android will sign out the current user (Admin).
        // For a real app, this should be done via Cloud Functions or a secondary Firebase app instance.
        // For simplicity here, we'll proceed, but warn that it might trigger a logout.
        
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    User newUser = new User();
                    newUser.setFirstName(fName);
                    newUser.setLastName(lName);
                    newUser.setStudentId(sId);
                    newUser.setEmail(email);
                    newUser.setRole("driver");
                    newUser.setStatus("active");
                    newUser.setAssignedShuttleId(-1);

                    db.child("users").child(uid).setValue(newUser)
                            .addOnSuccessListener(a -> {
                                Toast.makeText(getContext(), "Driver account created! Note: You have been signed out of admin.", Toast.LENGTH_LONG).show();
                                clearFields();
                                btnCreate.setEnabled(true);
                                btnCreate.setText("CREATE DRIVER ACCOUNT");
                                if (getActivity() != null) {
                                    getActivity().finish(); // Finish activity as we are now logged in as the driver
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), "Failed to save data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                btnCreate.setEnabled(true);
                                btnCreate.setText("CREATE DRIVER ACCOUNT");
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Auth Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnCreate.setEnabled(true);
                    btnCreate.setText("CREATE DRIVER ACCOUNT");
                });
    }

    private void clearFields() {
        etFirstName.setText("");
        etLastName.setText("");
        etStudentId.setText("");
        etEmail.setText("");
        etPassword.setText("");
    }
}
