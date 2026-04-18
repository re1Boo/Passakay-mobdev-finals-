package com.usc.passakay;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFirstName, etLastName, etStudentId, etEmail, etPassword, etConfirmPassword;
    private Spinner spDepartment, spCourse;
    private Button btnRegister;
    private TextView tvBackToLogin;
    private FirebaseAuth mAuth;
    private DatabaseReference db;

    private List<Department> departmentList = new ArrayList<>();
    private List<Course> courseList = new ArrayList<>();
    private int selectedDepartmentId = -1;
    private int selectedCourseId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        // Bind views
        etFirstName       = findViewById(R.id.et_first_name);
        etLastName        = findViewById(R.id.et_last_name);
        etStudentId       = findViewById(R.id.et_student_id);
        etEmail           = findViewById(R.id.et_email);
        etPassword        = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        spDepartment      = findViewById(R.id.sp_department);
        spCourse          = findViewById(R.id.sp_course);
        btnRegister       = findViewById(R.id.btn_register);
        tvBackToLogin     = findViewById(R.id.tv_back_to_login);

        loadDepartments();

        btnRegister.setOnClickListener(v -> handleRegister());
        tvBackToLogin.setOnClickListener(v -> finish());

        spDepartment.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    selectedDepartmentId = departmentList.get(position - 1).getDepartmentId();
                    // spCourse is usually not dependent on department in your seeder, but we can filter if needed.
                } else {
                    selectedDepartmentId = -1;
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    selectedCourseId = courseList.get(position - 1).getCourseId();
                } else {
                    selectedCourseId = -1;
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadDepartments() {
        db.child("departments").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                departmentList.clear();
                List<String> names = new ArrayList<>();
                names.add("Select Department");
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Department dept = ds.getValue(Department.class);
                    if (dept != null) {
                        departmentList.add(dept);
                        names.add(dept.getDepartmentName());
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(RegisterActivity.this,
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spDepartment.setAdapter(adapter);
                loadCourses();
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void loadCourses() {
        db.child("courses").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                courseList.clear();
                List<String> names = new ArrayList<>();
                names.add("Select Course");
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Course course = ds.getValue(Course.class);
                    if (course != null) {
                        courseList.add(course);
                        names.add(course.getCourseName());
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(RegisterActivity.this,
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spCourse.setAdapter(adapter);
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void handleRegister() {
        String firstName       = etFirstName.getText().toString().trim();
        String lastName        = etLastName.getText().toString().trim();
        String studentId       = etStudentId.getText().toString().trim();
        String email           = etEmail.getText().toString().trim();
        String password        = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (firstName.isEmpty()) { etFirstName.setError("Required"); etFirstName.requestFocus(); return; }
        if (lastName.isEmpty()) { etLastName.setError("Required"); etLastName.requestFocus(); return; }
        if (studentId.isEmpty()) { etStudentId.setError("Required"); etStudentId.requestFocus(); return; }
        if (selectedDepartmentId == -1) { Toast.makeText(this, "Select Department", Toast.LENGTH_SHORT).show(); return; }
        if (selectedCourseId == -1) { Toast.makeText(this, "Select Course", Toast.LENGTH_SHORT).show(); return; }
        if (email.isEmpty()) { etEmail.setError("Required"); etEmail.requestFocus(); return; }
        if (password.isEmpty()) { etPassword.setError("Required"); etPassword.requestFocus(); return; }
        if (!password.equals(confirmPassword)) { etConfirmPassword.setError("Mismatch"); etConfirmPassword.requestFocus(); return; }

        btnRegister.setEnabled(false);
        btnRegister.setText("Creating...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    saveUserToDatabase(uid, firstName, lastName, studentId, email, selectedDepartmentId, selectedCourseId);
                })
                .addOnFailureListener(e -> showError(e.getMessage()));
    }

    private void saveUserToDatabase(String uid, String fName, String lName, String sid, String mail, int deptId, int courseId) {
        User newUser = new User();
        newUser.setFirstName(fName);
        newUser.setLastName(lName);
        newUser.setStudentId(sid);
        newUser.setEmail(mail);
        newUser.setDepartmentId(deptId);
        newUser.setCourseId(courseId);
        newUser.setRole("passenger");
        newUser.setStatus("active");

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