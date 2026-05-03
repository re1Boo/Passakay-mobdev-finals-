package com.usc.passakay;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class NotificationService extends Service {
    private static final String TAG = "NotificationService";
    private static final String CHANNEL_ID = "PassakayNotifications";
    private DatabaseReference scansRef;
    private ChildEventListener scansListener;
    private final Map<String, Long> lastKnownPassengerCount = new HashMap<>();
    private boolean isInitialLoadComplete = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        scansRef = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("scans");
        
        // Start foreground immediately to satisfy system requirements
        startForeground(1, getPersistentNotification("Monitoring passenger scans..."));
        
        // Load initial state first to avoid notification flood for existing passengers
        scansRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot stopSnap : snapshot.getChildren()) {
                    lastKnownPassengerCount.put(stopSnap.getKey(), stopSnap.getChildrenCount());
                }
                isInitialLoadComplete = true;
                listenForScans();
                Log.d(TAG, "Initial load complete. Listening for new scans.");
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isInitialLoadComplete = true;
                listenForScans();
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Passenger Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications when a passenger scans a QR code");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private android.app.Notification getPersistentNotification(String message) {
        Intent intent = new Intent(this, DriverDashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Passakay Driver Active")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_shuttle)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void listenForScans() {
        scansListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if (isInitialLoadComplete) {
                    handleScanChange(snapshot);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if (isInitialLoadComplete) {
                    handleScanChange(snapshot);
                }
            }

            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                lastKnownPassengerCount.put(snapshot.getKey(), snapshot.getChildrenCount());
            }
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        scansRef.addChildEventListener(scansListener);
    }

    private void handleScanChange(DataSnapshot stopSnapshot) {
        String stopKey = stopSnapshot.getKey();
        if (stopKey == null) return;

        long currentCount = stopSnapshot.getChildrenCount();
        Long lastCount = lastKnownPassengerCount.get(stopKey);
        
        if (lastCount == null) lastCount = 0L;

        if (currentCount > lastCount) {
            String stopName = stopKey.replace("_com", "").replace("_", " ");
            showPushNotification("Passenger Waiting", "Someone just scanned at " + stopName);
        }
        
        lastKnownPassengerCount.put(stopKey, currentCount);
    }

    private void showPushNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = (int) System.currentTimeMillis();

        // Clicking the notification opens the Dashboard which redirects to the active ShuttleStopActivity
        Intent intent = new Intent(this, DriverDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shuttle)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{1000, 1000, 1000});

        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scansRef != null && scansListener != null) {
            scansRef.removeEventListener(scansListener);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
