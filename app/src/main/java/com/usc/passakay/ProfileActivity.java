package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileActivity extends BaseActivity {

    private TextView tvFullName, tvRole, tvStudentId;
    private TextView tvDepartment, tvCourse, tvPassword;
    private ImageView ivTogglePassword;
    private Button btnChangePassword, btnLogout;
    private DatabaseReference db;
    private boolean isPasswordVisible = false;
    private String userRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // Bind views
        tvFullName        = findViewById(R.id.tvFullName);
        tvRole            = findViewById(R.id.tvRole);
        tvStudentId       = findViewById(R.id.tvStudentId);
        tvDepartment      = findViewById(R.id.tvDepartment);
        tvCourse          = findViewById(R.id.tvCourse);
        tvPassword        = findViewById(R.id.tvPassword);
        ivTogglePassword  = findViewById(R.id.ivTogglePassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnLogout         = findViewById(R.id.btnLogout);

        // Load user data
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            loadUserData(currentUser.getUid());
        }

        // Toggle password visibility
        ivTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                if (currentUser != null) {
                    tvPassword.setText("Password: " + currentUser.getEmail()); // Using email as placeholder for password
                }
                ivTogglePassword.setImageResource(R.drawable.ic_launcher_foreground); // Placeholder
            } else {
                tvPassword.setText("Password: ••••••••");
                ivTogglePassword.setImageResource(R.drawable.ic_launcher_foreground); // Placeholder
            }
        });

        // Change password
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        // Logout
        btnLogout.setOnClickListener(v -> logout());

        // Bottom nav
        setupBottomNav();
    }

    private void loadUserData(String uid) {
        db.child("users").child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        userRole = user.getRole();
                        tvFullName.setText(user.getFirstName() + " " + user.getLastName());
                        tvRole.setText(capitalize(user.getRole()));
                        tvStudentId.setText("ID Number: " + user.getStudentId());

                        // Load department name
                        loadDepartmentName(user.getDepartmentId());

                        // Load course name
                        loadCourseName(user.getCourseId());
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {}
            });
    }

    private void loadDepartmentName(int departmentId) {
        db.child("departments").child(String.valueOf(departmentId))
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Department dept = snapshot.getValue(Department.class);
                    if (dept != null) {
                        tvDepartment.setText("Department: " + dept.getDepartmentName());
                    }
                }
                @Override
                public void onCancelled(DatabaseError error) {}
            });
    }

    private void loadCourseName(int courseId) {
        db.child("courses").child(String.valueOf(courseId))
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Course course = snapshot.getValue(Course.class);
                    if (course != null) {
                        tvCourse.setText("Course: " + course.getCourseName());
                    }
                }
                @Override
                public void onCancelled(DatabaseError error) {}
            });
    }

    private void showChangePasswordDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Change Password");

        android.widget.EditText etNewPassword = new android.widget.EditText(this);
        etNewPassword.setHint("Enter new password");
        etNewPassword.setInputType(
            android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        etNewPassword.setPadding(32, 16, 32, 16);
        builder.setView(etNewPassword);

        builder.setPositiveButton("Change", (dialog, which) -> {
            String newPassword = etNewPassword.getText().toString().trim();
            if (newPassword.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                user.updatePassword(newPassword)
                    .addOnSuccessListener(a ->
                        Toast.makeText(this, "Password changed successfully!", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Intent intent;
                if ("driver".equals(userRole)) {
                    intent = new Intent(this, DriverDashboardActivity.class);
                } else if ("admin".equals(userRole)) {
                    intent = new Intent(this, AdminDashboardActivity.class);
                } else {
                    intent = new Intent(this, PassengerHomeActivity.class);
                }
                startActivity(intent);
                finish();
                return true;
            } else if (id == R.id.nav_history) {
                return true;
            } else if (id == R.id.nav_profile) {
                return true;
            }
            return false;
        });
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}