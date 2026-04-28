package com.usc.passakay;

import android.util.Log;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RealtimeDBHelper {

    private final DatabaseReference db;
    private static final String TAG = "RealtimeDBHelper";

    public RealtimeDBHelper() {
        // Ensure this URL matches your Firebase Console exactly
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
    }

    // ─── USER ───────────────────────────────────────────

    public void addUser(User user, Runnable onSuccess, Consumer<String> onFailure) {
        db.child("users").child(String.valueOf(user.getUserId())).setValue(user)
                .addOnSuccessListener(a -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public void getUser(int userId, Consumer<User> onSuccess, Consumer<String> onFailure) {
        db.child("users").child(String.valueOf(userId))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) onSuccess.accept(user);
                        else onFailure.accept("User not found");
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        onFailure.accept(error.getMessage());
                    }
                });
    }

    public void getUserByStudentId(String studentId, Consumer<User> onSuccess, Consumer<String> onFailure) {
        db.child("users").orderByChild("studentId").equalTo(studentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot child : snapshot.getChildren()) {
                                User user = child.getValue(User.class);
                                if (user != null) {
                                    onSuccess.accept(user);
                                    return;
                                }
                            }
                        } else {
                            onFailure.accept("Student ID not found");
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        onFailure.accept(error.getMessage());
                    }
                });
    }

    public void getUserByUid(String uid, Consumer<User> onSuccess, Consumer<String> onFailure) {
        db.child("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) {
                            onSuccess.accept(user);
                        } else {
                            onFailure.accept("User not found");
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        onFailure.accept(error.getMessage());
                    }
                });
    }

    public void saveUserWithUid(String uid, User user, Runnable onSuccess, Consumer<String> onFailure) {
        db.child("users").child(uid).setValue(user)
                .addOnSuccessListener(a -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    // ─── COURSE ─────────────────────────────────────────

    public void addCourse(Course course, Runnable onSuccess, Consumer<String> onFailure) {
        db.child("courses").child(String.valueOf(course.getCourseId())).setValue(course)
                .addOnSuccessListener(a -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public void getAllCourses(Consumer<List<Course>> onSuccess, Consumer<String> onFailure) {
        db.child("courses")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Course> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Course course = child.getValue(Course.class);
                            if (course != null) list.add(course);
                        }
                        onSuccess.accept(list);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        onFailure.accept(error.getMessage());
                    }
                });
    }

    // ─── DEPARTMENT ─────────────────────────────────────

    public void addDepartment(Department dept, Runnable onSuccess, Consumer<String> onFailure) {
        db.child("departments").child(String.valueOf(dept.getDepartmentId())).setValue(dept)
                .addOnSuccessListener(a -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public void getAllDepartments(Consumer<List<Department>> onSuccess, Consumer<String> onFailure) {
        db.child("departments")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Department> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Department dept = child.getValue(Department.class);
                            if (dept != null) list.add(dept);
                        }
                        onSuccess.accept(list);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        onFailure.accept(error.getMessage());
                    }
                });
    }

    // ─── SHUTTLE ─────────────────────────────────────────

    public void addShuttle(Shuttle shuttle, Runnable onSuccess, Consumer<String> onFailure) {
        db.child("shuttles").child(String.valueOf(shuttle.getShuttleId())).setValue(shuttle)
                .addOnSuccessListener(a -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    // ─── SHUTTLE STOPS ───────────────────────────────────

    public void addShuttleStop(ShuttleStop stop, Runnable onSuccess, Consumer<String> onFailure) {
        db.child("shuttleStops").child(String.valueOf(stop.getStopId())).setValue(stop)
                .addOnSuccessListener(a -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public void getAllShuttleStops(Consumer<List<ShuttleStop>> onSuccess, Consumer<String> onFailure) {
        db.child("shuttleStops")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<ShuttleStop> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            ShuttleStop stop = child.getValue(ShuttleStop.class);
                            if (stop != null) list.add(stop);
                        }
                        onSuccess.accept(list);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        onFailure.accept(error.getMessage());
                    }
                });
    }

    // ─── TRIP ────────────────────────────────────────────

    public void addTrip(Trip trip, Runnable onSuccess, Consumer<String> onFailure) {
        db.child("trips").child(String.valueOf(trip.getTripId())).setValue(trip)
                .addOnSuccessListener(a -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public void getTrip(int tripId, Consumer<Trip> onSuccess, Consumer<String> onFailure) {
        db.child("trips").child(String.valueOf(tripId))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        Trip trip = snapshot.getValue(Trip.class);
                        if (trip != null) onSuccess.accept(trip);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        onFailure.accept(error.getMessage());
                    }
                });
    }

    public void getTripsByDriver(int driverId, Consumer<List<Trip>> onSuccess, Consumer<String> onFailure) {
        db.child("trips").orderByChild("driverId").equalTo(driverId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Trip> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Trip trip = child.getValue(Trip.class);
                            if (trip != null) list.add(trip);
                        }
                        onSuccess.accept(list);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        onFailure.accept(error.getMessage());
                    }
                });
    }

    public void getTripsByPassenger(String passengerId, Consumer<List<Trip>> onSuccess, Consumer<String> onFailure) {
        db.child("trips").orderByChild("passengerId").equalTo(passengerId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Trip> list = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Trip trip = child.getValue(Trip.class);
                            if (trip != null) list.add(trip);
                        }
                        onSuccess.accept(list);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        onFailure.accept(error.getMessage());
                    }
                });
    }

    public void logScanEvent(String passengerUid, String qrContent, Runnable onSuccess, Consumer<String> onFailure) {
        if (qrContent == null) {
            onFailure.accept("QR Content is null");
            return;
        }
        
        String content = qrContent.trim();
        DatabaseReference locationRef;
        
        if (content.equals("SAFAD.com") || content.equals("SAS.com") || content.equals("BUNZEL.com")) {
            locationRef = db.child("scans").child(content.replace(".", "_"));
        } else {
            locationRef = db.child("scans").child("OTHER");
        }

        String scanId = locationRef.push().getKey();
        long timestamp = System.currentTimeMillis();

        Map<String, Object> scanData = new HashMap<>();
        scanData.put("passengerUid", passengerUid);
        scanData.put("qrContent", qrContent);
        scanData.put("timestamp", timestamp);

        if (scanId != null) {
            locationRef.child(scanId).setValue(scanData)
                    .addOnSuccessListener(a -> {
                        Log.d(TAG, "Successfully logged scan to: " + locationRef.getPath().toString());
                        onSuccess.run();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to log scan: " + e.getMessage());
                        onFailure.accept(e.getMessage());
                    });
        }
    }
}
