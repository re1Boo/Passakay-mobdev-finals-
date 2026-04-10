package com.usc.passakay;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class RegisterActivity extends AppCompatActivity {

    private EditText etStudentId, etEmail, etPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvBackToLogin;
    private FirebaseAuth mAuth;
    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance().getReference();

        // Bind views - Updated to match snake_case IDs in activity_register.xml
        etStudentId       = findViewById(R.id.et_student_id);
        etEmail           = findViewById(R.id.et_email);
        etPassword        = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnRegister       = findViewById(R.id.btn_register);
        tvBackToLogin     = findViewById(R.id.tv_back_to_login);

        btnRegister.setOnClickListener(v -> handleRegister());

        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void handleRegister() {
        String studentId       = etStudentId.getText().toString().trim();
        String email           = etEmail.getText().toString().trim();
        String password        = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (studentId.isEmpty()) {
            etStudentId.setError("Please enter your Student ID");
            etStudentId.requestFocus();
            return;
        }
        if (email.isEmpty()) {
            etEmail.setError("Please enter your email");
            etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Please enter a password");
            etPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        btnRegister.setEnabled(false);
        btnRegister.setText("Creating account...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    saveUserToDatabase(uid, studentId, email);
                })
                .addOnFailureListener(e -> showError(e.getMessage()));
    }

    private void saveUserToDatabase(String uid, String studentId, String email) {
        User newUser = new User();
        newUser.setStudentId(studentId);
        newUser.setEmail(email);
        newUser.setRole("passenger");
        newUser.setStatus("active");
        newUser.setDepartmentId(0);
        newUser.setCourseId(0);

        db.child("users").child(uid).setValue(newUser)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> showError("Failed to save user: " + e.getMessage()));
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        btnRegister.setEnabled(true);
        btnRegister.setText("Create Account");
    }
}
