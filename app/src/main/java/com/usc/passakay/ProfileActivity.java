package com.usc.passakay;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
    private String userStudentId = "";
    private int assignedShuttleId = -1;

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
                    tvPassword.setText("Password: " + currentUser.getEmail()); // Placeholder
                }
            } else {
                tvPassword.setText("Password: ••••••••");
            }
        });

        // Logout
        btnLogout.setOnClickListener(v -> logout());

        // Change password
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
    }

    private void loadUserData(String uid) {
        db.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    userRole = user.getRole();
                    userStudentId = user.getStudentId();
                    assignedShuttleId = user.getAssignedShuttleId();

                    tvFullName.setText(user.getFirstName() + " " + user.getLastName());
                    tvRole.setText(capitalize(userRole));
                    
                    if ("driver".equals(userRole)) {
                        tvStudentId.setText("Driver ID: " + user.getStudentId());
                        tvDepartment.setText("Assigned Shuttle: #" + assignedShuttleId);
                        tvCourse.setVisibility(View.GONE); // Hide course for drivers
                        setupBottomNav(R.menu.menu_driver);
                    } else if ("admin".equals(userRole)) {
                        tvStudentId.setText("Admin ID: " + user.getStudentId());
                        tvDepartment.setText("System Administrator");
                        tvCourse.setVisibility(View.GONE);
                        setupBottomNav(R.menu.menu_passenger); // Admins usually see full nav
                    } else {
                        tvStudentId.setText("ID Number: " + user.getStudentId());
                        loadDepartmentName(user.getDepartmentId());
                        loadCourseName(user.getCourseId());
                        setupBottomNav(R.menu.menu_passenger);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadDepartmentName(int id) {
        db.child("departments").child(String.valueOf(id)).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Department d = snapshot.getValue(Department.class);
                if (d != null) tvDepartment.setText("Department: " + d.getDepartmentName());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadCourseName(int id) {
        db.child("courses").child(String.valueOf(id)).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Course c = snapshot.getValue(Course.class);
                if (c != null) tvCourse.setText("Course: " + c.getCourseName());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupBottomNav(int menuRes) {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.getMenu().clear();
        nav.inflateMenu(menuRes);
        nav.setSelectedItemId(R.id.nav_profile);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                Intent intent;
                if ("driver".equals(userRole)) {
                    intent = new Intent(this, ShuttleStopActivity.class);
                    intent.putExtra("shuttleId", String.valueOf(assignedShuttleId));
                } else if ("admin".equals(userRole)) {
                    intent = new Intent(this, AdminDashboardActivity.class);
                } else {
                    intent = new Intent(this, PassengerHomeActivity.class);
                }
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });
    }

    private void showChangePasswordDialog() {
        // ... (Keep existing implementation)
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
