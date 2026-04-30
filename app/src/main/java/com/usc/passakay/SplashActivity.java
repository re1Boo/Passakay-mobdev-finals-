package com.usc.passakay;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SplashActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        View dot1 = findViewById(R.id.dot1);
        View dot2 = findViewById(R.id.dot2);
        View dot3 = findViewById(R.id.dot3);

        animateDots(dot1, dot2, dot3);

        // Run seeder once if needed to populate assignments
        // DataSeeder seeder = new DataSeeder();
        // seeder.seedAll();

        new Handler(Looper.getMainLooper()).postDelayed(this::checkUserSession, 3000);
    }

    private void checkUserSession() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            db.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) {
                            if ("blocked".equals(user.getStatus())) {
                                mAuth.signOut();
                                goToLogin();
                                Toast.makeText(SplashActivity.this, "Your account is blocked.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            redirectBasedOnRole(user, uid);
                        } else {
                            goToLogin();
                        }
                    } else {
                        mAuth.signOut();
                        goToLogin();
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    goToLogin();
                }
            });
        } else {
            goToLogin();
        }
    }

    private void redirectBasedOnRole(User user, String uid) {
        String role = user.getRole();
        if ("driver".equals(role)) {
            // Driver uses assigned shuttle logic
            if (user.getAssignedShuttleId() > 0) {
                autoDeployAndGo(user, uid, user.getAssignedShuttleId());
            } else {
                mAuth.signOut();
                goToLogin();
                Toast.makeText(this, "No shuttle assigned.", Toast.LENGTH_SHORT).show();
            }
        } else if ("admin".equals(role)) {
            startActivity(new Intent(SplashActivity.this, AdminDashboardActivity.class));
            finish();
        } else {
            startActivity(new Intent(SplashActivity.this, PassengerHomeActivity.class));
            finish();
        }
    }

    private void autoDeployAndGo(User user, String uid, int shuttleId) {
        DatabaseReference shuttleRef = db.child("shuttles").child(String.valueOf(shuttleId));
        shuttleRef.child("status").setValue("Deployed");
        shuttleRef.child("driverId").setValue(uid);
        shuttleRef.child("driverName").setValue(user.getFirstName() + " " + user.getLastName());
        shuttleRef.child("active").setValue(true);

        Intent intent = new Intent(SplashActivity.this, ShuttleStopActivity.class);
        intent.putExtra("shuttleId", String.valueOf(shuttleId));
        intent.putExtra(ShuttleStopActivity.EXTRA_BUS_NAME, "Shuttle " + shuttleId);
        startActivity(intent);
        finish();
    }

    private void goToLogin() {
        startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        finish();
    }

    private void animateDots(View dot1, View dot2, View dot3) {
        View[] dots = {dot1, dot2, dot3};
        for (int i = 0; i < dots.length; i++) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(dots[i], "alpha", 0.2f, 1f, 0.2f);
            animator.setDuration(300);
            animator.setStartDelay(i * 200L);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.start();
        }
    }
}
