package com.usc.passakay;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import androidx.annotation.NonNull;

public class QueueManager {

    private static final String TAG = "QueueManager";
    private static final String DB_URL =
        "https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private static final int SHUTTLE_CAPACITY = 16;

    private final DatabaseReference db;

    public QueueManager() {
        db = FirebaseDatabase.getInstance(DB_URL).getReference();
    }

    // ─── Add passenger to queue ──────────────────────────

    public void joinQueue(String stopName, Runnable onSuccess,
                          Consumer<String> onFailure) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            onFailure.accept("Not logged in");
            return;
        }

        String queueKey = getQueueKey(stopName);

        // Check if already in queue
        db.child("queue").child(queueKey).child(uid)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        Log.d(TAG, "Already in queue at " + stopName);
                        onSuccess.run();
                        return;
                    }

                    // Add to queue with server timestamp
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("uid",               uid);
                    entry.put("stopName",           stopName);
                    entry.put("timestamp",          ServerValue.TIMESTAMP);
                    entry.put("assignedShuttleId",  -1);
                    entry.put("assignedPlate",      "");
                    entry.put("status",             "waiting");

                    db.child("queue").child(queueKey).child(uid)
                        .setValue(entry)
                        .addOnSuccessListener(a -> {
                            Log.d(TAG, "Joined queue at " + stopName);
                            onSuccess.run();
                        })
                        .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    onFailure.accept(error.getMessage());
                }
            });
    }

    // ✅ Fix 2: Add fallback direct allocation
    public void joinQueueAndAllocate(String stopName,
                                  Consumer<AssignmentResult> onAssigned,
                                  Consumer<String> onFailure) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            onFailure.accept("Not logged in");
            return;
        }

        String queueKey = getQueueKey(stopName);

        Map<String, Object> entry = new HashMap<>();
        entry.put("uid",              uid);
        entry.put("stopName",         stopName);
        entry.put("timestamp",        ServerValue.TIMESTAMP);
        entry.put("assignedShuttleId", -1);
        entry.put("assignedPlate",    "");
        entry.put("status",           "waiting");

        db.child("queue").child(queueKey).child(uid)
            .setValue(entry)
            .addOnSuccessListener(a -> {
                Log.d(TAG, "Joined queue at " + stopName);
                // Find nearest active shuttle and allocate directly
                findAndAllocateShuttle(uid, stopName, onAssigned, onFailure);
            })
            .addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    private void findAndAllocateShuttle(String uid, String stopName,
                                         Consumer<AssignmentResult> onAssigned,
                                         Consumer<String> onFailure) {
        db.child("shuttles").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Shuttle bestShuttle   = null;
                int lowestPassengers  = Integer.MAX_VALUE;

                for (DataSnapshot child : snapshot.getChildren()) {
                    Shuttle s = child.getValue(Shuttle.class);
                    if (s != null && s.isActive()) {
                        int passengers = s.getCurrentPassengers();
                        if (passengers < SHUTTLE_CAPACITY &&
                            passengers < lowestPassengers) {
                            lowestPassengers = passengers;
                            bestShuttle = s;
                        }
                    }
                }

                if (bestShuttle == null) {
                    onFailure.accept("No available shuttles right now");
                    return;
                }

                final Shuttle allocated = bestShuttle;

                // Get queue position
                getQueueForStop(stopName, queue -> {
                    int position = 1;
                    for (int i = 0; i < queue.size(); i++) {
                        if (queue.get(i).getUid().equals(uid)) {
                            position = i + 1;
                            break;
                        }
                    }

                    final int finalPosition = position;
                    // Default to 5 mins if no ETA set
                    int eta = 5; 

                    // Assign passenger
                    String queueKey = getQueueKey(stopName);

                    // Update queue entry
                    db.child("queue").child(queueKey).child(uid).child("assignedShuttleId").setValue(allocated.getShuttleId());
                    db.child("queue").child(queueKey).child(uid).child("assignedPlate").setValue(allocated.getPlateNumber());
                    db.child("queue").child(queueKey).child(uid).child("status").setValue("assigned");
                    db.child("queue").child(queueKey).child(uid).child("queuePosition").setValue(finalPosition);

                    // Update user node
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("assignedShuttleId", allocated.getShuttleId());
                    updates.put("assignedPlate", allocated.getPlateNumber());
                    updates.put("assignedStop", stopName);
                    updates.put("queuePosition", finalPosition);
                    updates.put("estimatedWaitMinutes", eta);
                    updates.put("allocationStatus", "assigned");
                    updates.put("isWaiting", true);
                    updates.put("waitingAt", stopName);

                    db.child("users").child(uid).updateChildren(updates);

                    // Update shuttle count
                    db.child("shuttles")
                        .child(String.valueOf(allocated.getShuttleId()))
                        .child("currentPassengers")
                        .setValue(allocated.getCurrentPassengers() + 1);

                    Log.d(TAG, "Directly allocated " + uid + " to shuttle " + allocated.getShuttleId());

                    // Return result
                    AssignmentResult result = new AssignmentResult(
                        allocated.getShuttleId(),
                        allocated.getPlateNumber(),
                        stopName,
                        finalPosition,
                        eta
                    );
                    onAssigned.accept(result);

                }, error -> onFailure.accept(error));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onFailure.accept(error.getMessage());
            }
        });
    }

    // ─── Remove passenger from queue ─────────────────────

    public void leaveQueue(String stopName) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String queueKey = getQueueKey(stopName);
        db.child("queue").child(queueKey).child(uid).removeValue()
            .addOnSuccessListener(a -> Log.d(TAG, "Left queue at " + stopName))
            .addOnFailureListener(e -> Log.e(TAG, "Error leaving queue: " + e.getMessage()));
    }

    // ─── Get sorted queue for a stop ─────────────────────

    public void getQueueForStop(String stopName,
                                 Consumer<List<QueueEntry>> onSuccess,
                                 Consumer<String> onFailure) {
        String queueKey = getQueueKey(stopName);

        db.child("queue").child(queueKey)
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<QueueEntry> queue = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        QueueEntry entry = child.getValue(QueueEntry.class);
                        if (entry != null) {
                            queue.add(entry);
                        }
                    }

                    // Sort by timestamp — first in, first out
                    Collections.sort(queue,
                        Comparator.comparingLong(QueueEntry::getTimestamp));

                    onSuccess.accept(queue);
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    onFailure.accept(error.getMessage());
                }
            });
    }

    // ─── Allocate passengers to shuttle ──────────────────

    public void allocatePassengersToShuttle(int shuttleId, String plateNumber,
                                             String stopName, int etaMinutes,
                                             Runnable onComplete) {
        String queueKey = getQueueKey(stopName);

        // Get current passengers on shuttle
        db.child("shuttles").child(String.valueOf(shuttleId))
            .child("currentPassengers")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int currentPassengers = snapshot.getValue(Integer.class) != null
                        ? snapshot.getValue(Integer.class) : 0;
                    int availableSlots = SHUTTLE_CAPACITY - currentPassengers;

                    if (availableSlots <= 0) {
                        Log.d(TAG, "Shuttle " + shuttleId + " is full");
                        onComplete.run();
                        return;
                    }

                    // Get sorted queue
                    getQueueForStop(stopName, queue -> {
                        int slotsToFill = Math.min(availableSlots, queue.size());
                        Log.d(TAG, "Allocating " + slotsToFill +
                            " passengers to shuttle " + shuttleId);

                        for (int i = 0; i < slotsToFill; i++) {
                            QueueEntry entry = queue.get(i);
                            assignPassengerToShuttle(
                                entry.getUid(),
                                shuttleId,
                                plateNumber,
                                stopName,
                                i + 1,  // position in queue
                                etaMinutes
                            );
                        }

                        // Update shuttle passenger count
                        db.child("shuttles").child(String.valueOf(shuttleId))
                            .child("currentPassengers")
                            .setValue(currentPassengers + slotsToFill);

                        onComplete.run();

                    }, error -> {
                        Log.e(TAG, "Queue error: " + error);
                        onComplete.run();
                    });
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    onComplete.run();
                }
            });
    }

    // ─── Assign individual passenger to shuttle ──────────

    public void assignPassengerToShuttle(String uid, int shuttleId,
                                           String plateNumber, String stopName,
                                           int position, int etaMinutes) {
        String queueKey = getQueueKey(stopName);

        // Update queue entry
        db.child("queue").child(queueKey).child(uid)
            .child("assignedShuttleId").setValue(shuttleId);
        db.child("queue").child(queueKey).child(uid)
            .child("assignedPlate").setValue(plateNumber);
        db.child("queue").child(queueKey).child(uid)
            .child("status").setValue("assigned");
        db.child("queue").child(queueKey).child(uid)
            .child("queuePosition").setValue(position);

        // Update user node so passenger sees it on status card
        Map<String, Object> updates = new HashMap<>();
        updates.put("assignedShuttleId", shuttleId);
        updates.put("assignedPlate", plateNumber);
        updates.put("assignedStop", stopName);
        updates.put("queuePosition", position);
        updates.put("allocationStatus", "assigned");
        updates.put("estimatedWaitMinutes", etaMinutes);

        db.child("users").child(uid).updateChildren(updates);

        Log.d(TAG, "Assigned passenger " + uid +
            " (position " + position + ") to shuttle " +
            shuttleId + " (" + plateNumber + ")");
    }

    // ─── Listen for passenger's assignment ───────────────

    public void listenForAssignment(Consumer<AssignmentResult> onAssigned) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.child("users").child(uid)
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    User user = snapshot.getValue(User.class);
                    if (user != null &&
                        user.getAssignedShuttleId() > 0 &&
                        user.getAssignedPlate() != null &&
                        !user.getAssignedPlate().isEmpty()) {

                        AssignmentResult result = new AssignmentResult(
                            user.getAssignedShuttleId(),
                            user.getAssignedPlate(),
                            user.getAssignedStop(),
                            user.getQueuePosition(),
                            user.getEstimatedWaitMinutes()
                        );
                        onAssigned.accept(result);
                    }
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
    }

    // ─── Helper ──────────────────────────────────────────

    private String getQueueKey(String stopName) {
        return stopName.replace(" ", "_").replace("/", "_");
    }
}
