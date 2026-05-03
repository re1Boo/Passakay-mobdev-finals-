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
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
    }

    public void logScanEvent(String passengerUid, String qrContent, Runnable onSuccess, Consumer<String> onFailure) {
        if (qrContent == null) {
            onFailure.accept("QR Content is null");
            return;
        }

        String stopName = qrContent.replace(".com", "").trim();
        DatabaseReference locationRef = db.child("scans").child(qrContent.replace(".", "_"));

        long timestamp = System.currentTimeMillis();

        Map<String, Object> scanData = new HashMap<>();
        scanData.put("passengerUid", passengerUid);
        scanData.put("qrContent", qrContent);
        scanData.put("timestamp", timestamp);

        // Use passengerUid as the key to avoid duplicate scans and allow easy removal
        locationRef.child(passengerUid).setValue(scanData)
                .addOnSuccessListener(a -> {
                    Map<String, Object> userUpdates = new HashMap<>();
                    userUpdates.put("isWaiting", true);
                    userUpdates.put("waitingAt", stopName);
                    userUpdates.put("waitingStartTime", timestamp);
                    db.child("users").child(passengerUid).updateChildren(userUpdates);
                    
                    onSuccess.run();
                })
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public void addUser(User user, Runnable onSuccess, Consumer<String> onFailure) {
        db.child("users").child(String.valueOf(user.getUserId())).setValue(user)
                .addOnSuccessListener(a -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public void getUserByUid(String uid, Consumer<User> onSuccess, Consumer<String> onFailure) {
        db.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) onSuccess.accept(user);
                else onFailure.accept("User not found");
            }
            @Override public void onCancelled(DatabaseError error) { onFailure.accept(error.getMessage()); }
        });
    }
}
