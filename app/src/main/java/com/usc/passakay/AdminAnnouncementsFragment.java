package com.usc.passakay;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AdminAnnouncementsFragment extends Fragment {

    private TextView tvCurrentAnnouncement;
    private EditText etAnnouncement;
    private Button btnUpdateAnnouncement;
    private RadioGroup rgPriority;
    private RecyclerView recyclerHistory;
    private DatabaseReference db;
    private java.util.List<java.util.Map<String, Object>> historyList = new java.util.ArrayList<>();
    private java.util.List<String> historyKeys = new java.util.ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_announcements, container, false);

        tvCurrentAnnouncement = view.findViewById(R.id.tvCurrentAnnouncement);
        etAnnouncement = view.findViewById(R.id.etAnnouncement);
        btnUpdateAnnouncement = view.findViewById(R.id.btnUpdateAnnouncement);
        rgPriority = view.findViewById(R.id.rgPriority);
        recyclerHistory = view.findViewById(R.id.recyclerAnnouncementHistory);
        
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        loadCurrentAnnouncement();
        loadHistory();

        btnUpdateAnnouncement.setOnClickListener(v -> updateAnnouncement());
        
        view.findViewById(R.id.btnClearAnnouncement).setOnClickListener(v -> clearAnnouncement());

        return view;
    }

    private void loadHistory() {
        db.child("announcements").child("history").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                historyList.clear();
                historyKeys.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) ds.getValue();
                    if (map != null) {
                        historyList.add(0, map); // Newest first
                        historyKeys.add(0, ds.getKey());
                    }
                }
                setupHistoryRecycler();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupHistoryRecycler() {
        AnnouncementHistoryAdapter adapter = new AnnouncementHistoryAdapter(getContext(), historyList, new AnnouncementHistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(java.util.Map<String, Object> data) {
                etAnnouncement.setText((String) data.get("message"));
                String priority = (String) data.get("priority");
                if ("warning".equals(priority)) rgPriority.check(R.id.rbWarning);
                else if ("emergency".equals(priority)) rgPriority.check(R.id.rbEmergency);
                else rgPriority.check(R.id.rbNormal);
                
                Toast.makeText(getContext(), "Loaded for editing/resending", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onItemLongClick(java.util.Map<String, Object> data, int position) {
                showDeleteConfirm(historyKeys.get(position));
            }
        });
        
        recyclerHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerHistory.setAdapter(adapter);
    }

    private void showDeleteConfirm(String key) {
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Delete History Item")
                .setMessage("Are you sure you want to remove this from the log?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.child("announcements").child("history").child(key).removeValue();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearAnnouncement() {
        java.util.Map<String, Object> clearData = new java.util.HashMap<>();
        clearData.put("message", "Shuttle operation is normal.");
        clearData.put("priority", "normal");
        clearData.put("expiresAt", System.currentTimeMillis() + (24 * 60 * 60 * 1000)); // Valid for 24 hours

        db.child("announcements").child("current").setValue(clearData)
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Announcement cleared!", Toast.LENGTH_SHORT).show());
    }

    private void loadCurrentAnnouncement() {
        db.child("announcements").child("current").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String message = snapshot.child("message").getValue(String.class);
                    Long expiresAt = snapshot.child("expiresAt").getValue(Long.class);
                    
                    if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
                        tvCurrentAnnouncement.setText("No active announcement (Last expired).");
                    } else {
                        tvCurrentAnnouncement.setText(message);
                    }
                } else {
                    tvCurrentAnnouncement.setText("No active announcement.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateAnnouncement() {
        String newMessage = etAnnouncement.getText().toString().trim();
        if (newMessage.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        String tempPriority = "normal";
        int checkedId = rgPriority.getCheckedRadioButtonId();
        if (checkedId == R.id.rbWarning) tempPriority = "warning";
        else if (checkedId == R.id.rbEmergency) tempPriority = "emergency";
        
        final String finalPriority = tempPriority;

        // Set expiry to 30 minutes from now
        long expiryTime = System.currentTimeMillis() + (30 * 60 * 1000);

        java.util.Map<String, Object> announcementData = new java.util.HashMap<>();
        announcementData.put("message", newMessage);
        announcementData.put("priority", finalPriority);
        announcementData.put("expiresAt", expiryTime);
        announcementData.put("timestamp", System.currentTimeMillis());

        // Smart Priority Check
        db.child("announcements").child("current").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean shouldUpdate = true;
                
                if (snapshot.exists()) {
                    String currentPriority = snapshot.child("priority").getValue(String.class);
                    Long expiresAt = snapshot.child("expiresAt").getValue(Long.class);
                    
                    if (expiresAt != null && System.currentTimeMillis() < expiresAt) {
                        if ("emergency".equals(currentPriority) && !"emergency".equals(finalPriority)) {
                            shouldUpdate = false;
                        }
                    }
                }

                final boolean finalShouldUpdate = shouldUpdate;
                if (finalShouldUpdate) {
                    db.child("announcements").child("current").setValue(announcementData);
                }
                
                db.child("announcements").child("history").push().setValue(announcementData)
                    .addOnSuccessListener(aVoid -> {
                        String msg = finalShouldUpdate ? "Broadcasted!" : "Added to history (Emergency alert remains active)";
                        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                        etAnnouncement.setText("");
                    });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
