package com.usc.passakay;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SplashActivity extends BaseActivity {

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

        new Handler(Looper.getMainLooper()).postDelayed(this::checkHardwareBindingFirst, 3000);
    }

    /**
     * Priority 1: Check if THIS physical device is bound to a shuttle via App Instance ID.
     */
    private void checkHardwareBindingFirst() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            goToLogin();
            return;
        }

        final String myAppId = getAppInstanceId();
        final String uid = currentUser.getUid();

        // First, get user role. If they aren't a driver, they shouldn't be auto-deployed by hardware binding.
        db.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                if (!userSnapshot.exists()) {
                    mAuth.signOut();
                    goToLogin();
                    return;
                }

                User user = userSnapshot.getValue(User.class);
                if (user == null) {
                    goToLogin();
                    return;
                }

                if ("blocked".equals(user.getStatus())) {
                    mAuth.signOut();
                    goToLogin();
                    Toast.makeText(SplashActivity.this, "Your account is blocked.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // If user is a driver, check for hardware binding
                if ("driver".equals(user.getRole())) {
                    db.child("shuttles").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot shuttleSnap : snapshot.getChildren()) {
                                Shuttle s = shuttleSnap.getValue(Shuttle.class);
                                if (s != null && myAppId.equals(s.getDeviceId())) {
                                    autoDeployAndGo(uid, s.getShuttleId(), "Shuttle " + s.getShuttleId());
                                    return;
                                }
                            }
                            // Driver but phone not bound, use standard session logic (checks assignedShuttleId)
                            redirectBasedOnRole(user, uid);
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            redirectBasedOnRole(user, uid);
                        }
                    });
                } else {
                    // Not a driver (Admin or Passenger), ignore hardware binding and redirect
                    redirectBasedOnRole(user, uid);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                goToLogin();
            }
        });
    }

    private void redirectBasedOnRole(User user, String uid) {
        String role = user.getRole();
        if ("driver".equals(role)) {
            if (user.getAssignedShuttleId() > 0) {
                autoDeployAndGo(uid, user.getAssignedShuttleId(), "Shuttle " + user.getAssignedShuttleId());
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

    private void autoDeployAndGo(String uid, int shuttleId, String busLabel) {
        DatabaseReference shuttleRef = db.child("shuttles").child(String.valueOf(shuttleId));
        shuttleRef.child("status").setValue("Deployed");
        shuttleRef.child("driverId").setValue(uid);
        shuttleRef.child("active").setValue(true);

        Intent intent = new Intent(SplashActivity.this, ShuttleStopActivity.class);
        intent.putExtra("shuttleId", String.valueOf(shuttleId));
        intent.putExtra(ShuttleStopActivity.EXTRA_BUS_NAME, busLabel);
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
            if (dots[i] == null) continue;
            ObjectAnimator animator = ObjectAnimator.ofFloat(dots[i], "alpha", 0.2f, 1f, 0.2f);
            animator.setDuration(300);
            animator.setStartDelay(i * 200L);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.start();
        }
    }
}
