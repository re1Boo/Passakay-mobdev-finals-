package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
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
        ImageView imgMascot = findViewById(R.id.imgMascot);
        ImageView ivTogglePassword = findViewById(R.id.ivTogglePassword);

        final boolean[] isVisible = {false};
        ivTogglePassword.setOnClickListener(v -> {
            isVisible[0] = !isVisible[0];
            if (isVisible[0]) {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivTogglePassword.setImageResource(R.drawable.ic_eye_on);
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivTogglePassword.setImageResource(R.drawable.ic_eye_off);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        btnLogin.setOnClickListener(v -> handleLogin());

        findViewById(R.id.btnCreateAccount).setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );

        imgMascot.setOnClickListener(v -> {
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
                    @Override public void onCancelled(DatabaseError error) { showError("Database error"); }
                });
    }

    private void checkDriverDevice(User user, String password) {
        int assignedId = user.getAssignedShuttleId();
        if (assignedId <= 0) {
            showError("No shuttle assigned. Contact admin.");
            return;
        }

        // FIX: Use getAppInstanceId() to match Admin binding logic
        String currentDeviceId = getAppInstanceId();

        db.child("shuttles").child(String.valueOf(assignedId))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Shuttle shuttle = snapshot.getValue(Shuttle.class);
                        if (shuttle != null) {
                            String registeredDeviceId = shuttle.getDeviceId();
                            if (registeredDeviceId == null || registeredDeviceId.isEmpty()) {
                                showError("Shuttle not bound to any device. Contact admin.");
                            } else if (!registeredDeviceId.equals(currentDeviceId)) {
                                showError("Unauthorized device. Please use assigned phone for Shuttle #" + assignedId);
                            } else {
                                loginWithEmail(user.getEmail(), password);
                            }
                        } else {
                            showError("Assigned shuttle not found.");
                        }
                    }
                    @Override public void onCancelled(DatabaseError error) { showError("Error checking device."); }
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
                    if ("driver".equals(user.getRole())) {
                        intent = new Intent(LoginActivity.this, ShuttleStopActivity.class);
                        intent.putExtra("shuttleId", String.valueOf(user.getAssignedShuttleId()));
                    } else if ("admin".equals(user.getRole())) {
                        intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
                    } else {
                        intent = new Intent(LoginActivity.this, PassengerHomeActivity.class);
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        btnLogin.setEnabled(true);
        btnLogin.setText("Login");
    }
}
