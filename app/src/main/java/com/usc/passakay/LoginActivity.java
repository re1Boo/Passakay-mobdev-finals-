package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    private EditText etStudentId, etPassword;
    private Button btnLogin;
    private TextView btnCreateAccount;
    private FirebaseAuth mAuth;
    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_screen);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        // FIXED: Using specific regional URL for asia-southeast1
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // Bind views
        etStudentId = findViewById(R.id.etStudentId);
        etPassword  = findViewById(R.id.etPassword);
        btnLogin    = findViewById(R.id.btnLogin);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);

        // Check if already logged in
        if (mAuth.getCurrentUser() != null) {
            goToHome(mAuth.getCurrentUser().getUid());
        }

        btnLogin.setOnClickListener(v -> handleLogin());

        btnCreateAccount.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void handleLogin() {
        String studentId = etStudentId.getText().toString().trim();
        String password  = etPassword.getText().toString().trim();

        // Validate inputs
        if (studentId.isEmpty()) {
            etStudentId.setError("Please enter your Student ID");
            etStudentId.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Please enter your password");
            etPassword.requestFocus();
            return;
        }

        // Show loading
        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        // Find email by studentId in database
        db.child("users").orderByChild("studentId").equalTo(studentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Get the user's email
                            for (DataSnapshot child : snapshot.getChildren()) {
                                User user = child.getValue(User.class);
                                if (user != null) {
                                    // Login with email and password
                                    loginWithEmail(user.getEmail(), password);
                                }
                            }
                        } else {
                            showError("Student ID not found");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showError("Database error: " + error.getMessage());
                    }
                });
    }

    private void loginWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    goToHome(uid);
                })
                .addOnFailureListener(e -> {
                    showError("Invalid password");
                });
    }

    private void goToHome(String uid) {
        // Check user role then redirect
        db.child("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) {
                            Intent intent;
                            if ("driver".equals(user.getRole())) {
                                intent = new Intent(LoginActivity.this, DriverHomeActivity.class);
                            } else {
                                intent = new Intent(LoginActivity.this, PassengerHomeActivity.class);
                            }
                            intent.putExtra("userId", uid);
                            startActivity(intent);
                            finish(); // prevent going back to login
                        } else {
                            // Fallback to MainActivity if user data not found
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showError("Failed to get user data");
                    }
                });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        btnLogin.setEnabled(true);
        btnLogin.setText("Login");
    }
}
