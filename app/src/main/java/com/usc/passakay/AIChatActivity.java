package com.usc.passakay;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AIChatActivity extends BaseActivity {

    private RecyclerView recyclerChat;
    private EditText etMessage;
    private ProgressBar progressLoading;
    private List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter adapter;
    private DatabaseReference db;
    private OkHttpClient client;

    // Use a stable model name and version
    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    private static final String GEMINI_URL = 
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + GEMINI_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        recyclerChat = findViewById(R.id.recyclerChat);
        etMessage = findViewById(R.id.etMessage);
        progressLoading = findViewById(R.id.progressLoading);
        
        adapter = new ChatAdapter();
        recyclerChat.setLayoutManager(new LinearLayoutManager(this));
        recyclerChat.setAdapter(adapter);

        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Initial Greeting
        addMessage("Hi! I'm your Passakay AI Assistant. How can I help you navigate the USC campus today?", ChatMessage.TYPE_AI);
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        etMessage.setText("");
        addMessage(text, ChatMessage.TYPE_USER);
        
        getCampusDataAndAskGemini(text);
    }

    private void addMessage(String text, int type) {
        messages.add(new ChatMessage(text, type));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerChat.scrollToPosition(messages.size() - 1);
    }

    private void getCampusDataAndAskGemini(String userPrompt) {
        progressLoading.setVisibility(View.VISIBLE);
        
        JSONObject contextData = new JSONObject();
        
        // Fetch snapshot of shuttles and stops for context
        db.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    // Collect Shuttles
                    JSONArray shuttles = new JSONArray();
                    for (DataSnapshot sSnap : snapshot.child("shuttles").getChildren()) {
                        Shuttle s = sSnap.getValue(Shuttle.class);
                        if (s != null) {
                            JSONObject obj = new JSONObject();
                            obj.put("id", s.getShuttleId());
                            obj.put("status", s.getStatus());
                            obj.put("passengers", s.getCurrentPassengers());
                            obj.put("nextStop", sSnap.child("nextStop").getValue(String.class));
                            shuttles.put(obj);
                        }
                    }
                    contextData.put("active_shuttles", shuttles);

                    // Collect Stops
                    JSONArray stops = new JSONArray();
                    for (DataSnapshot stopSnap : snapshot.child("shuttleStops").getChildren()) {
                        String name = stopSnap.child("stopName").getValue(String.class);
                        Integer waiting = snapshot.child("stopWaitingCounts").child(getScanKey(name)).getValue(Integer.class);
                        JSONObject obj = new JSONObject();
                        obj.put("name", name);
                        obj.put("waiting_count", waiting != null ? waiting : 0);
                        stops.put(obj);
                    }
                    contextData.put("stops_info", stops);

                    askGemini(userPrompt, contextData.toString());

                } catch (Exception e) {
                    Log.e("AIChat", "Data build error", e);
                    progressLoading.setVisibility(View.GONE);
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                progressLoading.setVisibility(View.GONE);
            }
        });
    }

    private String getScanKey(String stopName) {
        if (stopName == null) return "";
        if (stopName.equalsIgnoreCase("SAFAD")) return "SAFAD Building_com";
        if (stopName.equalsIgnoreCase("Bunzel")) return "Bunzel_com";
        return stopName + "_com";
    }

    private void askGemini(String userPrompt, String context) {
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.isEmpty()) {
            progressLoading.setVisibility(View.GONE);
            addMessage("Error: API Key is missing in BuildConfig.", ChatMessage.TYPE_AI);
            return;
        }

        // DEBUG: Extract last 4 digits of the key to verify it's the NEW one (should end in Suu7A)
        String keyEnd = (GEMINI_API_KEY.length() > 4) ? GEMINI_API_KEY.substring(GEMINI_API_KEY.length() - 4) : "****";
        Log.d("AIChat", "API Key ending with: " + keyEnd);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + GEMINI_API_KEY;

        try {
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", "You are the USC Passakay Assistant. Context: " + context + "\n\nUser: " + userPrompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        progressLoading.setVisibility(View.GONE);
                        addMessage("Network Error: " + e.getMessage(), ChatMessage.TYPE_AI);
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            String message = error.optString("message", "Unknown Google Error");
                            String status = error.optString("status", "");
                            
                            // If Lite fails, retry with the standard Flash model
                            if (message.contains("not found") || message.contains("not supported")) {
                                retryWithModel("gemini-2.5-flash", userPrompt, context);
                                return;
                            }

                            runOnUiThread(() -> {
                                progressLoading.setVisibility(View.GONE);
                                addMessage("Google Error [" + status + "]: " + message, ChatMessage.TYPE_AI);
                            });
                            return;
                        }

                        if (json.has("candidates")) {
                            String aiText = json.getJSONArray("candidates").getJSONObject(0)
                                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");

                            runOnUiThread(() -> {
                                progressLoading.setVisibility(View.GONE);
                                addMessage(aiText, ChatMessage.TYPE_AI);
                            });
                        } else {
                            runOnUiThread(() -> {
                                progressLoading.setVisibility(View.GONE);
                                addMessage("Unexpected Response Format: " + responseBody, ChatMessage.TYPE_AI);
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            progressLoading.setVisibility(View.GONE);
                            addMessage("Format Error. Raw: " + responseBody, ChatMessage.TYPE_AI);
                        });
                    }
                }
            });
        } catch (Exception e) {
            progressLoading.setVisibility(View.GONE);
            addMessage("App Exception: " + e.getMessage(), ChatMessage.TYPE_AI);
        }
    }

    private void retryWithModel(String modelName, String userPrompt, String context) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + GEMINI_API_KEY;
        try {
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", "Context: " + context + "\n\nUser: " + userPrompt);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> progressLoading.setVisibility(View.GONE));
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String body = response.body() != null ? response.body().string() : "";
                        JSONObject json = new JSONObject(body);
                        if (json.has("candidates")) {
                            String text = json.getJSONArray("candidates").getJSONObject(0)
                                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                            runOnUiThread(() -> {
                                progressLoading.setVisibility(View.GONE);
                                addMessage(text, ChatMessage.TYPE_AI);
                            });
                        } else if (modelName.equals("gemini-2.5-flash")) {
                            // Final attempt with gemini-pro (renamed to 2.5-flash-lite in user logic, but search says pro exists)
                            // User story says: 2.5-flash -> 2.5-flash-lite. Let's follow their exact chain.
                            retryWithModel("gemini-2.5-flash-lite", userPrompt, context);
                        } else {
                            runOnUiThread(() -> {
                                progressLoading.setVisibility(View.GONE);
                                addMessage("AI models are currently unavailable for this account.", ChatMessage.TYPE_AI);
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> progressLoading.setVisibility(View.GONE));
                    }
                }
            });
        } catch (Exception ignored) {
            progressLoading.setVisibility(View.GONE);
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) { return messages.get(position).getType(); }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = (viewType == ChatMessage.TYPE_USER) ? R.layout.item_chat_user : R.layout.item_chat_ai;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new ChatViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((ChatViewHolder) holder).tvMessage.setText(messages.get(position).getText());
        }

        @Override
        public int getItemCount() { return messages.size(); }

        class ChatViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage;
            ChatViewHolder(View v) { super(v); tvMessage = v.findViewById(R.id.tvMessage); }
        }
    }
}
