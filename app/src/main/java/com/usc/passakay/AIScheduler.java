package com.usc.passakay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AIScheduler {

    private static final String TAG      = "AIScheduler";
    private static final long MIN_INTERVAL = 30_000;  // minimum 30 seconds between calls
    private static final String DB_URL =
        "https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/";

    private final Handler handler;
    private final AIShuttleManager aiManager;
    private final DatabaseReference db;
    private boolean isRunning   = false;
    private long lastRunTime    = 0;
    private int pendingChanges  = 0;

    public AIScheduler(Context context) {
        this.handler   = new Handler(Looper.getMainLooper());
        this.aiManager = new AIShuttleManager(context);
        this.db        = FirebaseDatabase.getInstance(DB_URL).getReference();
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        Log.d(TAG, "AI Scheduler started");

        // Watch for changes that should trigger AI
        watchForChanges();
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "AI Scheduler stopped");
    }

    // ─── Watch Firebase for changes ──────────────────────

    private void watchForChanges() {
        // Trigger AI when a new passenger joins the queue
        db.child("queue").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!isRunning) return;
                pendingChanges++;
                Log.d(TAG, "Queue changed — pending changes: " + pendingChanges);
                scheduleAIRun();
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });

        // Trigger AI when shuttle location changes
        db.child("shuttles").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!isRunning) return;
                pendingChanges++;
                Log.d(TAG, "Shuttle changed — pending changes: " + pendingChanges);
                scheduleAIRun();
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    // ─── Schedule AI run respecting minimum interval ─────

    private void scheduleAIRun() {
        long now          = System.currentTimeMillis();
        long timeSinceLast = now - lastRunTime;

        if (timeSinceLast >= MIN_INTERVAL) {
            // Enough time has passed → run immediately
            runAI();
        } else {
            // Too soon → wait until 30 seconds have passed
            long delay = MIN_INTERVAL - timeSinceLast;
            Log.d(TAG, "Waiting " + (delay / 1000) + "s before next AI run");
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(this::runAI, delay);
        }
    }

    private void runAI() {
        if (!isRunning) return;
        lastRunTime    = System.currentTimeMillis();
        pendingChanges = 0;
        Log.d(TAG, "Running AI cycle...");
        aiManager.runAIManagement();
    }
}
