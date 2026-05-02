package com.usc.passakay;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StopAdapter extends RecyclerView.Adapter<StopAdapter.StopViewHolder> {

    public interface OnPickUpClickListener {
        void onPickUp(StopItem item);
    }

    private final Context context;
    private final List<StopItem> stopList;
    private final DatabaseReference db;
    private final Set<Integer> expandedPositions = new HashSet<>();
    private final OnPickUpClickListener pickUpListener;

    public StopAdapter(Context context, List<StopItem> stopList, OnPickUpClickListener listener) {
        this.context  = context;
        this.stopList = stopList;
        this.db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        this.pickUpListener = listener;
    }

    @NonNull
    @Override
    public StopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_stop, parent, false);
        return new StopViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StopViewHolder holder, int position) {
        StopItem stop = stopList.get(position);

        holder.tvStopName.setText(stop.getStopName());
        holder.tvWaiting.setText("Waiting: " + stop.getWaitingCount());
        holder.tvDistance.setText("Distance: " + stop.getDistanceMeters() + "m");

        if (stop.getWaitingCount() > 0) {
            holder.btnPickUp.setEnabled(true);
            holder.btnPickUp.setBackgroundResource(R.drawable.rounded_yellow_badge);
            holder.btnPickUp.setTextColor(Color.parseColor("#000000"));
            holder.btnPickUp.setAlpha(1.0f);
            holder.ivExpand.setVisibility(View.VISIBLE);
            
            // Toggle expansion
            boolean isExpanded = expandedPositions.contains(position);
            holder.recyclerPassengers.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.ivExpand.setRotation(isExpanded ? 180 : 0);

            if (isExpanded) {
                loadPassengersForStop(stop.getStopName(), holder.recyclerPassengers);
            }
        } else {
            holder.btnPickUp.setEnabled(false);
            holder.btnPickUp.setBackgroundResource(R.drawable.rounded_gray_badge);
            holder.btnPickUp.setTextColor(Color.parseColor("#888888"));
            holder.btnPickUp.setAlpha(0.5f);
            holder.ivExpand.setVisibility(View.GONE);
            holder.recyclerPassengers.setVisibility(View.GONE);
            expandedPositions.remove(position);
        }

        holder.layoutMain.setOnClickListener(v -> {
            if (stop.getWaitingCount() > 0) {
                if (expandedPositions.contains(position)) {
                    expandedPositions.remove(position);
                } else {
                    expandedPositions.add(position);
                }
                notifyItemChanged(position);
            }
        });

        holder.btnPickUp.setOnClickListener(v -> {
            if (pickUpListener != null) {
                pickUpListener.onPickUp(stop);
            }
            clearWaitingStatusAtStop(stop.getStopName());
        });
    }

    private void loadPassengersForStop(String stopName, RecyclerView rv) {
        db.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<User> passengers = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    User user = ds.getValue(User.class);
                    Boolean isWaiting = ds.child("isWaiting").getValue(Boolean.class);
                    String userStop = ds.child("lastScannedStop").getValue(String.class);

                    if (user != null && isWaiting != null && isWaiting && userStop != null) {
                        if (isMatch(userStop, stopName)) {
                            passengers.add(user);
                        }
                    }
                }
                rv.setLayoutManager(new LinearLayoutManager(context));
                rv.setAdapter(new PassengerAdapter(context, passengers));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void clearWaitingStatusAtStop(String stopName) {
        db.child("users").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String userStop = ds.child("lastScannedStop").getValue(String.class);
                    if (userStop != null && isMatch(userStop, stopName)) {
                        ds.getRef().child("isWaiting").setValue(false);
                        ds.getRef().child("lastScannedStop").setValue("");
                        ds.getRef().child("waitingAt").setValue("");
                    }
                }
                // No need for Toast here as Reilly's listener handles it
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private boolean isMatch(String s1, String s2) {
        if (s1 == null || s2 == null) return false;
        String v1 = s1.toLowerCase().trim();
        String v2 = s2.toLowerCase().trim();
        return v1.contains(v2) || v2.contains(v1);
    }

    @Override public int getItemCount() { return stopList.size(); }

    static class StopViewHolder extends RecyclerView.ViewHolder {
        TextView tvStopName, tvWaiting, tvDistance;
        Button btnPickUp;
        RecyclerView recyclerPassengers;
        View layoutMain;
        ImageView ivExpand;

        StopViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStopName = itemView.findViewById(R.id.tvStopName);
            tvWaiting  = itemView.findViewById(R.id.tvWaiting);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            btnPickUp  = itemView.findViewById(R.id.btnPickUp);
            recyclerPassengers = itemView.findViewById(R.id.recyclerPassengers);
            layoutMain = itemView.findViewById(R.id.layoutMain);
            ivExpand = itemView.findViewById(R.id.ivExpand);
        }
    }
}
