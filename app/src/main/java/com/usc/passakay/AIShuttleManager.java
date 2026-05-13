package com.usc.passakay;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import androidx.annotation.NonNull;

public class AIShuttleManager {

    private static final String TAG = "AIShuttleManager";
    private static final String DB_URL =
        "https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/";

    // ✅ Read key from BuildConfig
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
        "gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;

    private final DatabaseReference db;
    private final OkHttpClient client;

    public AIShuttleManager(Context context) {
        this.db = FirebaseDatabase.getInstance(DB_URL).getReference();
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    // ─── MAIN ────────────────────────────────────────────

    public void runAIManagement() {
        Log.d(TAG, "AI Cycle: Starting data collection...");
        collectAllData(data -> {
            if (data != null) {
                Log.d(TAG, "AI Cycle: Data collected. Calling Gemini...");
                askGeminiForDecisions(data, decisions -> {
                    if (decisions != null) {
                        Log.d(TAG, "AI Cycle: Decisions received. Applying to DB...");
                        applyDecisions(decisions);
                    } else {
                        Log.e(TAG, "AI Cycle: Failed to get decisions from Gemini.");
                    }
                });
            } else {
                Log.w(TAG, "AI Cycle: No data to process (possibly no waiting users).");
            }
        });
    }

    // ─── STEP 1: Collect data ─────────────────────────────

    private void collectAllData(Consumer<JSONObject> onComplete) {
        JSONObject data = new JSONObject();

        db.child("shuttles").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot shuttleSnap) {
                try {
                    JSONArray shuttles = new JSONArray();
                    for (DataSnapshot child : shuttleSnap.getChildren()) {
                        Shuttle s = child.getValue(Shuttle.class);
                        if (s != null && s.isActive()) {
                            JSONObject obj = new JSONObject();
                            obj.put("shuttleId",         s.getShuttleId());
                            obj.put("plateNumber",       s.getPlateNumber());
                            obj.put("isActive",          s.isActive());
                            obj.put("currentLat",        s.getCurrentLat());
                            obj.put("currentLng",        s.getCurrentLng());
                            obj.put("currentPassengers", s.getCurrentPassengers());
                            obj.put("capacity",          16); 
                            obj.put("driverName",        s.getDriverName() != null ? s.getDriverName() : "Unknown");
                            shuttles.put(obj);
                        }
                    }
                    data.put("shuttles", shuttles);
                } catch (JSONException e) {
                    Log.e(TAG, "Collection Error (Shuttles): " + e.getMessage());
                }

                db.child("shuttleStops").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot stopSnap) {
                        try {
                            JSONArray stops = new JSONArray();
                            for (DataSnapshot child : stopSnap.getChildren()) {
                                ShuttleStop stop = child.getValue(ShuttleStop.class);
                                if (stop != null) {
                                    JSONObject obj = new JSONObject();
                                    obj.put("stopId",   stop.getStopId());
                                    obj.put("stopName", stop.getStopName());
                                    obj.put("lat",      stop.getLatitude());
                                    obj.put("lng",      stop.getLongitude());
                                    stops.put(obj);
                                }
                            }
                            data.put("stops", stops);
                        } catch (JSONException e) {
                            Log.e(TAG, "Collection Error (Stops): " + e.getMessage());
                        }

                        db.child("users").orderByChild("isWaiting").equalTo(true)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot userSnap) {
                                    try {
                                        JSONArray waitingUsers = new JSONArray();
                                        for (DataSnapshot child : userSnap.getChildren()) {
                                            User user = child.getValue(User.class);
                                            if (user != null) {
                                                JSONObject obj = new JSONObject();
                                                obj.put("uid",              child.getKey());
                                                obj.put("waitingAt",        user.getWaitingAt() != null ? user.getWaitingAt() : "Unknown");
                                                obj.put("nearestStop",      user.getNearestStop() != null ? user.getNearestStop() : "Unknown");
                                                obj.put("allocationStatus", user.getAllocationStatus() != null ? user.getAllocationStatus() : "waiting");
                                                obj.put("lat",              user.getCurrentLat());
                                                obj.put("lng",              user.getCurrentLng());
                                                waitingUsers.put(obj);
                                            }
                                        }
                                        
                                        if (waitingUsers.length() == 0) {
                                            onComplete.accept(null);
                                            return;
                                        }

                                        data.put("waitingPassengers", waitingUsers);

                                        java.util.Calendar cal = java.util.Calendar.getInstance();
                                        data.put("currentHour",   cal.get(java.util.Calendar.HOUR_OF_DAY));
                                        data.put("currentMinute", cal.get(java.util.Calendar.MINUTE));

                                        onComplete.accept(data);

                                    } catch (JSONException e) {
                                        Log.e(TAG, "Collection Error (Users): " + e.getMessage());
                                        onComplete.accept(null);
                                    }
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) { onComplete.accept(null); }
                            });
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { onComplete.accept(null); }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { onComplete.accept(null); }
        });
    }

    // ─── STEP 2: Ask Gemini ───────────────────────────────

    private void askGeminiForDecisions(JSONObject data, Consumer<JSONObject> onComplete) {
        try {
            String prompt = buildPrompt(data);

            JSONObject requestBody = new JSONObject();
            JSONArray contents     = new JSONArray();
            JSONObject content     = new JSONObject();
            JSONArray parts        = new JSONArray();
            JSONObject part        = new JSONObject();

            part.put("text", prompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature",      0.1); 
            generationConfig.put("maxOutputTokens",  1000);
            requestBody.put("generationConfig", generationConfig);

            Request request = new Request.Builder()
                .url(GEMINI_URL)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
                ))
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Gemini Request Failed: " + e.getMessage());
                    onComplete.accept(null);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        if (response.body() == null) {
                            onComplete.accept(null);
                            return;
                        }
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        
                        JSONArray candidates = jsonResponse.optJSONArray("candidates");
                        if (candidates == null || candidates.length() == 0) {
                            Log.e(TAG, "No candidates in Gemini response: " + responseBody);
                            onComplete.accept(null);
                            return;
                        }
                        
                        String text = candidates.getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");

                        // Robust JSON extraction
                        if (text.contains("{")) {
                            text = text.substring(text.indexOf("{"), text.lastIndexOf("}") + 1);
                        }

                        JSONObject decisions = new JSONObject(text);
                        onComplete.accept(decisions);

                    } catch (Exception e) {
                        Log.e(TAG, "Decision Parsing Error: " + e.getMessage());
                        onComplete.accept(null);
                    }
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "Request Build Error: " + e.getMessage());
            onComplete.accept(null);
        }
    }

    // ─── Build prompt ─────────────────────────────────────

    private String buildPrompt(JSONObject data) {
        return "You are the Dispatch AI for University of San Carlos campus shuttles.\n\n" +
            "DATA:\n" + data.toString() + "\n\n" +
            "INSTRUCTIONS:\n" +
            "1. Match waiting passengers to active shuttles. Match their 'waitingAt' stop to shuttle routes.\n" +
            "2. Use the EXACT 'uid' strings provided in the data.\n" +
            "3. Use EXACT stop names from the stops list provided.\n" +
            "4. Capacity is 16 passengers per shuttle. Do not exceed it.\n" +
            "5. Respond ONLY with this JSON format (no markdown, no extra text):\n" +
            "{\n" +
            "  \"shuttleETAs\": [{\"shuttleId\": 1, \"estimatedMinutes\": 5, \"nextStop\": \"Bunzel\"}],\n" +
            "  \"passengerAllocations\": [{\"uid\": \"EXACT_UID\", \"assignedShuttleId\": 1, \"recommendedStop\": \"Bunzel\", \"estimatedWaitMinutes\": 3}],\n" +
            "  \"announcement\": \"Shuttle 1 heading to Bunzel.\"\n" +
            "}";
    }

    // ─── STEP 3: Apply decisions to Firebase ─────────────

    private void applyDecisions(JSONObject decisions) {
        try {
            QueueManager queueManager = new QueueManager();

            // 1. Update shuttle ETAs
            if (decisions.has("shuttleETAs")) {
                JSONArray etas = decisions.getJSONArray("shuttleETAs");
                for (int i = 0; i < etas.length(); i++) {
                    JSONObject eta  = etas.getJSONObject(i);
                    int shuttleId   = eta.getInt("shuttleId");
                    int minutes     = eta.getInt("estimatedMinutes");
                    String nextStop = eta.optString("nextStop", "");

                    db.child("shuttles").child(String.valueOf(shuttleId)).child("eta").setValue(minutes);
                    db.child("shuttles").child(String.valueOf(shuttleId)).child("nextStop").setValue(nextStop);

                    if (!nextStop.isEmpty()) {
                        db.child("shuttles").child(String.valueOf(shuttleId)).child("plateNumber")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String plate = snapshot.getValue(String.class);
                                    if (plate != null) {
                                        queueManager.allocatePassengersToShuttle(shuttleId, plate, nextStop, minutes, () -> {});
                                    }
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) {}
                            });
                    }
                }
            }

            // 2. Process specific passenger allocations from AI
            if (decisions.has("passengerAllocations")) {
                JSONArray allocations = decisions.getJSONArray("passengerAllocations");
                for (int i = 0; i < allocations.length(); i++) {
                    JSONObject alloc = allocations.getJSONObject(i);
                    String uid = alloc.getString("uid");
                    int shuttleId = alloc.getInt("assignedShuttleId");
                    String stopName = alloc.optString("recommendedStop", "");
                    int eta = alloc.optInt("estimatedWaitMinutes", 0);

                    if (shuttleId > 0 && !uid.isEmpty()) {
                        db.child("shuttles").child(String.valueOf(shuttleId)).child("plateNumber")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String plate = snapshot.getValue(String.class) != null ? snapshot.getValue(String.class) : "BUS-" + shuttleId;
                                    queueManager.assignPassengerToShuttle(uid, shuttleId, plate, stopName, 1, eta);
                                    Log.d(TAG, "AI Decision Applied: Assigned " + uid + " to Shuttle " + shuttleId);
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) {}
                            });
                    }
                }
            }

            // 3. Announcement
            if (decisions.has("announcement")) {
                String msg = decisions.getString("announcement");
                if (!msg.isEmpty()) {
                    Map<String, Object> announcement = new HashMap<>();
                    announcement.put("message",   msg);
                    announcement.put("timestamp", System.currentTimeMillis());
                    announcement.put("priority",  "info");
                    announcement.put("source",    "AI");
                    db.child("announcements").child("current").setValue(announcement);
                }
            }

            Log.d(TAG, "AI Cycle Complete: All decisions written to database.");

        } catch (JSONException e) {
            Log.e(TAG, "Decision Application Failed: " + e.getMessage());
        }
    }
}
