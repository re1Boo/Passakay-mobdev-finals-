package com.usc.passakay;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DataSeeder {

    private final DatabaseReference db;
    private final FirebaseAuth mAuth;
    private static final String TAG = "DataSeeder";

    public DataSeeder() {
        db    = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        mAuth = FirebaseAuth.getInstance();
    }

    public void seedAll() {
        seedDepartments();
        seedCourses();
        seedShuttles();
        seedShuttleStops();
        seedUsers();
    }

    private void seedDepartments() {
        Department[] departments = {
                new Department(1, "School of Arts and Sciences"),
                new Department(2, "School of Engineering"),
                new Department(3, "School of Business and Economics"),
                new Department(4, "School of Fine Arts and Design"),
                new Department(5, "School of Health Care Professions"),
                new Department(6, "School of Law and Governance")
        };
        for (Department dept : departments) {
            db.child("departments").child(String.valueOf(dept.getDepartmentId())).setValue(dept);
        }
    }

    private void seedCourses() {
        for (int i = 1; i <= 10; i++) {
            Course c = new Course(i, "Course " + i);
            db.child("courses").child(String.valueOf(i)).setValue(c);
        }
    }

    private void seedShuttles() {
        for (int i = 1; i <= 5; i++) {
            Shuttle shuttle = new Shuttle(i, "PLT-00" + i);
            shuttle.setCapacity(30);
            shuttle.setDeviceId(""); // Empty means not yet bound to a phone
            db.child("shuttles").child(String.valueOf(i)).setValue(shuttle);
        }
    }

    private void seedShuttleStops() {
        ShuttleStop[] stops = {
                new ShuttleStop(1, "Bunzel", 10.3519, 123.9135),
                new ShuttleStop(2, "SAFAD", 10.3528, 123.9105)
        };
        for (ShuttleStop stop : stops) {
            db.child("shuttleStops").child(String.valueOf(stop.getStopId())).setValue(stop);
        }
    }

    private void seedUsers() {
        // Admin
        createUser("admin@passakay.com", "admin123456", "00000001", "admin", -1);
        
        // Drivers - Assigned to Shuttle 1 and 2
        createUser("driver1@passakay.com", "password123", "D001", "driver", 1);
        createUser("driver2@passakay.com", "password123", "D002", "driver", 2);
        
        // Passenger
        createUser("passenger@passakay.com", "password123", "21101234", "passenger", -1);
    }

    private void createUser(String email, String password, String studentId, String role, int assignedShuttleId) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    saveUserData(authResult.getUser().getUid(), email, studentId, role, assignedShuttleId);
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        mAuth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener(authResult -> 
                                    saveUserData(authResult.getUser().getUid(), email, studentId, role, assignedShuttleId));
                    }
                });
    }

    private void saveUserData(String uid, String email, String studentId, String role, int assignedShuttleId) {
        User user = new User();
        user.setEmail(email);
        user.setStudentId(studentId);
        user.setRole(role);
        user.setStatus("active");
        user.setFirstName(role.substring(0, 1).toUpperCase() + role.substring(1));
        user.setLastName("User");
        user.setAssignedShuttleId(assignedShuttleId);

        db.child("users").child(uid).setValue(user)
                .addOnSuccessListener(a -> Log.d(TAG, "User seeded: " + email))
                .addOnFailureListener(e -> Log.e(TAG, "Seed failed: " + e.getMessage()));
    }
}
