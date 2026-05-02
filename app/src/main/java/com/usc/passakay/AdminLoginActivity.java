package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AdminLoginActivity extends AppCompatActivity {

    private static final String TAG = "AdminLoginActivity";
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private FirebaseAuth mAuth;
    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin   = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("Enter email");
            etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Enter password");
            etPassword.requestFocus();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Log.d(TAG, "Auth success, checking role for UID: " + uid);
                    
                    db.child("users").child(uid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot snapshot) {
                                    if (snapshot.exists()) {
                                        User user = snapshot.getValue(User.class);
                                        if (user != null && "admin".equals(user.getRole())) {
                                            Log.d(TAG, "Admin role confirmed");
                                            startActivity(new Intent(AdminLoginActivity.this, AdminDashboardActivity.class));
                                            finish();
                                        } else {
                                            Log.w(TAG, "User exists but is not an admin. Role: " + (user != null ? user.getRole() : "null"));
                                            showError("You are not authorized as admin");
                                            mAuth.signOut();
                                        }
                                    } else {
                                        Log.e(TAG, "User data not found in Realtime Database");
                                        showError("User data not found");
                                        mAuth.signOut();
                                    }
                                }
                                @Override
                                public void onCancelled(DatabaseError error) {
                                    Log.e(TAG, "Database error: " + error.getMessage());
                                    showError("Database error: " + error.getMessage());
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Login failed: " + e.getMessage());
                    // Show the ACTUAL error from Firebase
                    showError("Login failed: " + e.getMessage());
                });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        btnLogin.setEnabled(true);
        btnLogin.setText("Login");
    }
}
