package com.usc.passakay;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileActivity extends BaseActivity {

    private TextView tvFullName, tvRole, tvStudentId;
    private TextView tvDepartment, tvCourse, tvPassword;
    private ImageView ivTogglePassword;
    private CircleImageView imgProfile;
    private Button btnChangePassword, btnLogout;
    private DatabaseReference db;
    private boolean isPasswordVisible = false;
    private String userRole = "";
    private String currentUid = "";

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processAndUploadImage(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // Bind views
        imgProfile        = findViewById(R.id.imgProfile);
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
            currentUid = currentUser.getUid();
            loadUserData(currentUid);
        }

        // Image Click -> Pick new photo
        imgProfile.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // Toggle password visibility
        ivTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                if (currentUser != null) {
                    tvPassword.setText("Password: " + currentUser.getEmail());
                }
                ivTogglePassword.setImageResource(R.drawable.ic_launcher_foreground);
            } else {
                tvPassword.setText("Password: ••••••••");
                ivTogglePassword.setImageResource(R.drawable.ic_launcher_foreground);
            }
        });

        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnLogout.setOnClickListener(v -> logout());
        setupBottomNav();
    }

    private void loadUserData(String uid) {
        db.child("users").child(uid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    userRole = user.getRole();
                    tvFullName.setText(user.getFirstName() + " " + user.getLastName());
                    tvRole.setText(capitalize(user.getRole()));
                    tvStudentId.setText("ID Number: " + user.getStudentId());

                    // Load Profile Image (Handling Base64 string)
                    String photoStr = user.getProfileImageUrl();
                    if (photoStr != null && !photoStr.isEmpty()) {
                        try {
                            byte[] imageBytes = Base64.decode(photoStr, Base64.DEFAULT);
                            Glide.with(ProfileActivity.this)
                                    .asBitmap()
                                    .load(imageBytes)
                                    .placeholder(R.drawable.ic_default_profile)
                                    .into(imgProfile);
                        } catch (Exception e) {
                            imgProfile.setImageResource(R.drawable.ic_default_profile);
                        }
                    }

                    loadDepartmentName(user.getDepartmentId());
                    loadCourseName(user.getCourseId());
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void processAndUploadImage(Uri uri) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Processing...");
        pd.setMessage("Compressing image for database...");
        pd.show();

        new Thread(() -> {
            try {
                // 1. Convert Uri to Bitmap
                InputStream imageStream = getContentResolver().openInputStream(uri);
                Bitmap originalBitmap = BitmapFactory.decodeStream(imageStream);

                // 2. Resize Bitmap (Important to keep DB small!)
                int width = 200;
                int height = (int) (originalBitmap.getHeight() * (200.0 / originalBitmap.getWidth()));
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);

                // 3. Compress to JPEG
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] b = baos.toByteArray();

                // 4. Encode to Base64 String
                String encodedImage = Base64.encodeToString(b, Base64.DEFAULT);

                // 5. Save string to Realtime Database
                runOnUiThread(() -> {
                    db.child("users").child(currentUid).child("profileImageUrl").setValue(encodedImage)
                            .addOnSuccessListener(aVoid -> {
                                pd.dismiss();
                                Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                pd.dismiss();
                                Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    pd.dismiss();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void loadDepartmentName(int departmentId) {
        db.child("departments").child(String.valueOf(departmentId))
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Department dept = snapshot.getValue(Department.class);
                    if (dept != null) tvDepartment.setText("Department: " + dept.getDepartmentName());
                }
                @Override public void onCancelled(DatabaseError error) {}
            });
    }

    private void loadCourseName(int courseId) {
        db.child("courses").child(String.valueOf(courseId))
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Course course = snapshot.getValue(Course.class);
                    if (course != null) tvCourse.setText("Course: " + course.getCourseName());
                }
                @Override public void onCancelled(DatabaseError error) {}
            });
    }

    private void showChangePasswordDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Change Password");
        android.widget.EditText etNewPassword = new android.widget.EditText(this);
        etNewPassword.setHint("Enter new password");
        etNewPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etNewPassword.setPadding(32, 16, 32, 16);
        builder.setView(etNewPassword);
        builder.setPositiveButton("Change", (dialog, which) -> {
            String newPassword = etNewPassword.getText().toString().trim();
            if (newPassword.length() < 6) {
                Toast.makeText(this, "Short password", Toast.LENGTH_SHORT).show();
                return;
            }
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                user.updatePassword(newPassword).addOnSuccessListener(a -> Toast.makeText(this, "Success", Toast.LENGTH_SHORT).show());
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
                if ("driver".equals(userRole)) intent = new Intent(this, DriverDashboardActivity.class);
                else if ("admin".equals(userRole)) intent = new Intent(this, AdminDashboardActivity.class);
                else intent = new Intent(this, PassengerHomeActivity.class);
                startActivity(intent);
                finish();
                return true;
            }
            return id == R.id.nav_history || id == R.id.nav_profile;
        });
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}