package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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

    private static final String TAG = "LoginActivity";
    private EditText etStudentId, etPassword;
    private Button btnLogin;
    private TextView btnCreateAccount;
    private FirebaseAuth mAuth;
    private DatabaseReference db;

    private int tapCount = 0;
    private static final int SECRET_TAPS = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_screen);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        mAuth.signOut();

        etStudentId      = findViewById(R.id.etStudentId);
        etPassword       = findViewById(R.id.etPassword);
        btnLogin         = findViewById(R.id.btnLogin);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        ImageView imgLogo = findViewById(R.id.imgLogo);

        btnLogin.setOnClickListener(v -> handleLogin());

        btnCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );

        imgLogo.setOnClickListener(v -> {
            tapCount++;
            if (tapCount >= SECRET_TAPS) {
                tapCount = 0;
                startActivity(new Intent(LoginActivity.this, AdminLoginActivity.class));
            }
        });
    }

    private void handleLogin() {
        String studentId = etStudentId.getText().toString().trim();
        String password  = etPassword.getText().toString().trim();

        if (studentId.isEmpty() || password.isEmpty()) {
            showError("Please fill in all fields");
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Verifying...");

        db.child("users").orderByChild("studentId").equalTo(studentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                User user = child.getValue(User.class);
                                if (user != null) {
                                    if ("blocked".equals(user.getStatus())) {
                                        showError("Your account is blocked.");
                                        return;
                                    }
                                    
                                    // If user is a driver, check device binding
                                    if ("driver".equals(user.getRole())) {
                                        checkDriverDevice(user, password);
                                    } else {
                                        loginWithEmail(user.getEmail(), password);
                                    }
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

    private void checkDriverDevice(User user, String password) {
        int assignedId = user.getAssignedShuttleId();
        if (assignedId <= 0) {
            showError("No shuttle assigned to you. Contact admin.");
            return;
        }

        String currentDeviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        db.child("shuttles").child(String.valueOf(assignedId))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Shuttle shuttle = snapshot.getValue(Shuttle.class);
                        if (shuttle != null) {
                            String registeredDeviceId = shuttle.getDeviceId();
                            
                            // If the shuttle has no device ID yet, the first login "binds" it (useful for setup)
                            // Or, require admin to set it. Here we check for match.
                            if (registeredDeviceId == null || registeredDeviceId.isEmpty()) {
                                // Optional: Bind device automatically on first login if you want
                                // db.child("shuttles").child(String.valueOf(assignedId)).child("deviceId").setValue(currentDeviceId);
                                showError("This shuttle is not bound to any device. Contact admin.");
                            } else if (!registeredDeviceId.equals(currentDeviceId)) {
                                showError("Unauthorized device. Please use the assigned phone for Shuttle #" + assignedId);
                            } else {
                                loginWithEmail(user.getEmail(), password);
                            }
                        } else {
                            showError("Assigned shuttle not found.");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showError("Error checking device.");
                    }
                });
    }

    private void loginWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> goToHome(authResult.getUser().getUid()))
                .addOnFailureListener(e -> showError("Invalid password"));
    }

    private void goToHome(String uid) {
        db.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    Intent intent;
                    switch (user.getRole()) {
                        case "driver":
                            intent = new Intent(LoginActivity.this, ShuttleStopActivity.class);
                            intent.putExtra("shuttleId", String.valueOf(user.getAssignedShuttleId()));
                            break;
                        case "admin":
                            intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
                            break;
                        default:
                            intent = new Intent(LoginActivity.this, PassengerHomeActivity.class);
                            break;
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        btnLogin.setEnabled(true);
        btnLogin.setText("Login");
    }
}
