package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends BaseActivity {

    private EditText etStudentId, etPassword;
    private Button btnLogin;
    private TextView btnCreateAccount;
    private FirebaseAuth mAuth;
    private DatabaseReference db;

    // Secret tap counter
    private int tapCount = 0;
    private static final int SECRET_TAPS = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_screen);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // ⚠️ Sign out any existing session when landing on login page
        mAuth.signOut();

        // Bind views
        etStudentId      = findViewById(R.id.etStudentId);
        etPassword       = findViewById(R.id.etPassword);
        btnLogin         = findViewById(R.id.btnLogin);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        ImageView imgLogo = findViewById(R.id.imgLogo);

        // Login button
        btnLogin.setOnClickListener(v -> handleLogin());

        // Create account
        btnCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );

        // Secret tap on logo → Admin login
        imgLogo.setOnClickListener(v -> {
            tapCount++;
            int remaining = SECRET_TAPS - tapCount;
            if (remaining <= 3 && remaining > 0) {
                Toast.makeText(this, remaining + " more...", Toast.LENGTH_SHORT).show();
            }
            if (tapCount >= SECRET_TAPS) {
                tapCount = 0;
                startActivity(new Intent(LoginActivity.this, AdminLoginActivity.class));
            }
        });
    }

    private void handleLogin() {
        String studentId = etStudentId.getText().toString().trim();
        String password  = etPassword.getText().toString().trim();

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

        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        db.child("users").orderByChild("studentId").equalTo(studentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                User user = child.getValue(User.class);
                                if (user != null) {
                                    // Block check
                                    if ("blocked".equals(user.getStatus())) {
                                        showError("Your account has been blocked. Please contact admin.");
                                        return;
                                    }
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
                .addOnFailureListener(e -> showError("Invalid password"));
    }

    private void goToHome(String uid) {
        db.child("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) {
                            Intent intent;
                            switch (user.getRole()) {
                                case "driver":
                                    intent = new Intent(LoginActivity.this, DriverHomeActivity.class);
                                    break;
                                case "admin":
                                    intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
                                    break;
                                default:
                                    // Redirect passengers to PassengerHomeActivity instead of MainActivity
                                    intent = new Intent(LoginActivity.this, PassengerHomeActivity.class);
                                    break;
                            }
                            intent.putExtra("userId", uid);
                            // ← Clear back stack so user can't go back to login
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            showError("User data not found");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showError("Failed to get user data: " + error.getMessage());
                    }
                });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        btnLogin.setEnabled(true);
        btnLogin.setText("Login");
    }
}