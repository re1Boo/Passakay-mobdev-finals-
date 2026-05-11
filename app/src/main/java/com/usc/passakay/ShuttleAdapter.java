package com.usc.passakay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShuttleAdapter extends RecyclerView.Adapter<ShuttleAdapter.ShuttleViewHolder> {

    private final Context context;
    private final List<ShuttleItem> shuttleList;
    private int expandedPosition = -1;
    private DatabaseReference db;
    private final boolean isPassengerView;
    private double userLat, userLng;
    private final List<ShuttleStop> cachedStops = new ArrayList<>();

    public ShuttleAdapter(Context context, List<ShuttleItem> shuttleList, FragmentManager fragmentManager) {
        this.context = context;
        this.shuttleList = shuttleList;
        this.db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        this.isPassengerView = context instanceof PassengerHomeActivity;
        
        loadStops();
    }

    private void loadStops() {
        db.child("shuttleStops").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cachedStops.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    ShuttleStop stop = ds.getValue(ShuttleStop.class);
                    if (stop != null) cachedStops.add(stop);
                }
                // Sort by route order
                cachedStops.sort((s1, s2) -> Integer.compare(getRouteOrder(s1.getStopName()), getRouteOrder(s2.getStopName())));
                notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private int getRouteOrder(String stopName) {
        if (stopName == null) return 99;
        String name = stopName.toLowerCase();
        if (name.contains("bunzel")) return 1;
        if (name.contains("portal")) return 2;
        if (name.contains("dorm"))   return 3;
        if (name.contains("pe"))     return 4;
        if (name.contains("shcp"))   return 5;
        if (name.contains("lrc"))    return 6;
        if (name.contains("mr"))     return 7;
        if (name.contains("safad"))  return 8;
        if (name.contains("chapel")) return 9;
        if (name.contains("among"))  return 10;
        return 99;
    }

    public void updateUserLocation(double lat, double lng) {
        this.userLat = lat;
        this.userLng = lng;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ShuttleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isPassengerView ? R.layout.item_shuttle : R.layout.item_shuttle_2;
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new ShuttleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShuttleViewHolder holder, int position) {
        ShuttleItem shuttle = shuttleList.get(position);
        boolean isExpanded = (position == expandedPosition);

        holder.tvBusName.setText(shuttle.getBusName());
        holder.tvDriverName.setText("Driver: " + (shuttle.isAvailable() ? shuttle.getDriverName() : "Unavailable"));
        holder.tvPlateNumber.setText("Plate: " + shuttle.getPlateNumber());

        if (isPassengerView && holder.tvEta != null) {
            if (!shuttle.isAvailable()) {
                holder.tvEta.setText("0");
                if (holder.etaBadge != null) holder.etaBadge.setBackgroundResource(R.drawable.rounded_gray_badge);
            } else {
                holder.tvEta.setText(String.valueOf(shuttle.getEta()));
                if (holder.etaBadge != null) holder.etaBadge.setBackgroundResource(R.drawable.rounded_yellow_badge);
            }
        }

        if (!shuttle.isAvailable()) {
            holder.cardShuttle.setCardBackgroundColor(Color.parseColor("#EEEEEE"));
            holder.cardShuttle.setAlpha(0.7f);
            holder.tvBusName.setTextColor(Color.parseColor("#9E9E9E"));
            holder.tvDriverName.setTextColor(Color.parseColor("#9E9E9E"));
            holder.tvPlateNumber.setTextColor(Color.parseColor("#9E9E9E"));
            
            if (holder.ivBusIcon != null) {
                holder.ivBusIcon.setAlpha(0.3f);
            }

            if (isPassengerView && holder.tvEta != null) {
                holder.tvEta.setText("--");
                holder.tvEta.setTextColor(Color.parseColor("#9E9E9E"));
                if (holder.etaBadge != null) {
                    holder.etaBadge.setBackgroundResource(R.drawable.rounded_gray_badge);
                    holder.etaBadge.setAlpha(0.5f);
                }
            }

            // For Driver view, if it's standby, show it differently
            if (!isPassengerView && holder.btnStatus != null) {
                if (shuttle.isStandby()) {
                    holder.btnStatus.setText("Standby");
                    holder.btnStatus.setBackgroundResource(R.drawable.rounded_green_badge);
                    holder.btnStatus.setAlpha(1.0f);
                    holder.btnStatus.setOnClickListener(v -> deployShuttle(shuttle));
                } else {
                    holder.btnStatus.setText("Unavailable");
                    holder.btnStatus.setBackgroundResource(R.drawable.rounded_gray_badge);
                    holder.btnStatus.setAlpha(1.0f);
                    holder.btnStatus.setOnClickListener(null);
                }
            }

            // Hide progress info if offline
            if (isPassengerView) {
                for (View v : holder.progressStops) {
                    if (v != null) {
                        v.findViewById(R.id.viewUserDot).setVisibility(View.GONE);
                        v.findViewById(R.id.ivShuttleIcon).setVisibility(View.GONE);
                        v.setAlpha(0.3f);
                    }
                }
            }
        } else {
            holder.cardShuttle.setCardBackgroundColor(Color.WHITE);
            holder.cardShuttle.setAlpha(1.0f);
            holder.tvBusName.setTextColor(Color.parseColor("#1A1A1A"));
            holder.tvDriverName.setTextColor(Color.parseColor("#444444"));
            holder.tvPlateNumber.setTextColor(Color.parseColor("#444444"));
            
            if (holder.ivBusIcon != null) {
                holder.ivBusIcon.setAlpha(1.0f);
            }

            if (isPassengerView && holder.tvEta != null) {
                holder.tvEta.setText(String.valueOf(shuttle.getEta()));
                holder.tvEta.setTextColor(Color.parseColor("#1A1A1A"));
                if (holder.etaBadge != null) {
                    holder.etaBadge.setBackgroundResource(R.drawable.rounded_yellow_badge);
                    holder.etaBadge.setAlpha(1.0f);
                }
            }

            // For Driver view
            if (!isPassengerView && holder.btnStatus != null) {
                holder.btnStatus.setText("Deployed");
                holder.btnStatus.setBackgroundResource(R.drawable.rounded_yellow_badge);
                holder.btnStatus.setAlpha(1.0f);
                holder.btnStatus.setOnClickListener(v -> {
                    // Restriction: Even if deployed, only the assigned driver should enter
                    String uid = FirebaseAuth.getInstance().getUid();
                    if (uid == null) return;

                    db.child("users").child(uid).child("assignedShuttleId").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            Integer assignedId = snapshot.getValue(Integer.class);
                            int targetId = -1;
                            try { targetId = Integer.parseInt(shuttle.getShuttleId()); } catch (Exception ignored) {}

                            if (assignedId != null && assignedId == targetId) {
                                context.startActivity(new Intent(context, ShuttleStopActivity.class)
                                        .putExtra(ShuttleStopActivity.EXTRA_BUS_NAME, shuttle.getBusName())
                                        .putExtra("shuttleId", shuttle.getShuttleId()));
                            } else {
                                Toast.makeText(context, "Unauthorized: You are not assigned to this shuttle.", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
                });
            }
            
            if (isPassengerView) {
                for (View v : holder.progressStops) {
                    if (v != null) v.setAlpha(1.0f);
                }
                updateProgressIndicators(holder, shuttle);
            }
        }

        if (holder.cardMap != null) {
            holder.cardMap.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            if (isExpanded) {
                holder.bindMap(shuttle, userLat, userLng);
            }
        }

        holder.cardShuttle.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            int oldPos = expandedPosition;
            if (oldPos == currentPos) {
                expandedPosition = -1;
            } else {
                expandedPosition = currentPos;
            }

            if (oldPos != -1) notifyItemChanged(oldPos);
            if (expandedPosition != -1) notifyItemChanged(expandedPosition);
        });
    }

    private void updateProgressIndicators(ShuttleViewHolder holder, ShuttleItem shuttle) {
        if (cachedStops.isEmpty()) return;

        int shuttleNearestIdx = findNearestStopIndex(shuttle.getDriverLat(), shuttle.getDriverLng());
        int userNearestIdx = (userLat != 0) ? findNearestStopIndex(userLat, userLng) : -1;

        for (int i = 0; i < holder.progressStops.length; i++) {
            View stopView = holder.progressStops[i];
            if (stopView == null) continue;

            if (i < cachedStops.size()) {
                stopView.setVisibility(View.VISIBLE);
                TextView tvLabel = stopView.findViewById(R.id.tvStopLabel);
                tvLabel.setText(cachedStops.get(i).getStopName());
                tvLabel.setVisibility(View.VISIBLE);

                stopView.findViewById(R.id.ivShuttleIcon).setVisibility(i == shuttleNearestIdx ? View.VISIBLE : View.GONE);
                stopView.findViewById(R.id.viewUserDot).setVisibility(i == userNearestIdx ? View.VISIBLE : View.GONE);
            } else {
                stopView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void deployShuttle(ShuttleItem shuttle) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user == null) return;

                // Restriction: Ensure the driver can only deploy their assigned shuttle
                int targetId = -1;
                try { targetId = Integer.parseInt(shuttle.getShuttleId()); } catch (Exception ignored) {}

                if (user.getAssignedShuttleId() != targetId) {
                    Toast.makeText(context, "You are only authorized to drive Bus " + user.getAssignedShuttleId(), Toast.LENGTH_SHORT).show();
                    return;
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("active", true);
                updates.put("status", "Deployed");
                updates.put("driverId", uid);
                updates.put("driverName", user.getFirstName() + " " + user.getLastName());

                db.child("shuttles").child(shuttle.getShuttleId()).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        context.startActivity(new Intent(context, ShuttleStopActivity.class)
                                .putExtra(ShuttleStopActivity.EXTRA_BUS_NAME, shuttle.getBusName())
                                .putExtra("shuttleId", shuttle.getShuttleId()));
                    })
                    .addOnFailureListener(e -> Toast.makeText(context, "Failed to deploy: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private int findNearestStopIndex(double lat, double lng) {
        int nearestIdx = -1;
        float minDist = Float.MAX_VALUE;

        // Only consider the first 8 stops as that's what's in the UI
        int limit = Math.min(cachedStops.size(), 8);
        for (int i = 0; i < limit; i++) {
            ShuttleStop stop = cachedStops.get(i);
            float[] results = new float[1];
            Location.distanceBetween(lat, lng, stop.getLatitude(), stop.getLongitude(), results);
            if (results[0] < minDist) {
                minDist = results[0];
                nearestIdx = i;
            }
        }
        return nearestIdx;
    }

    @Override
    public int getItemCount() {
        return shuttleList.size();
    }

    static class ShuttleViewHolder extends RecyclerView.ViewHolder implements OnMapReadyCallback {
        TextView tvBusName, tvDriverName, tvPlateNumber, tvEta;
        View etaBadge;
        ImageView ivBusIcon;
        Button btnStatus;
        CardView cardShuttle, cardMap;
        MapView mapView;
        GoogleMap googleMap;
        ShuttleItem currentShuttle;
        double userLat, userLng;
        View[] progressStops = new View[8];
        DatabaseReference db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();

        ShuttleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBusName = itemView.findViewById(R.id.tvBusName);
            tvDriverName = itemView.findViewById(R.id.tvDriverName);
            tvPlateNumber = itemView.findViewById(R.id.tvPlateNumber);
            tvEta = itemView.findViewById(R.id.tvEta);
            etaBadge = itemView.findViewById(R.id.etaBadge);
            ivBusIcon = itemView.findViewById(R.id.ivBusIcon);
            btnStatus = itemView.findViewById(R.id.btnStatus);
            cardShuttle = itemView.findViewById(R.id.cardShuttle);
            cardMap = itemView.findViewById(R.id.cardMap);
            mapView = itemView.findViewById(R.id.mapView);

            progressStops[0] = itemView.findViewById(R.id.stop1);
            progressStops[1] = itemView.findViewById(R.id.stop2);
            progressStops[2] = itemView.findViewById(R.id.stop3);
            progressStops[3] = itemView.findViewById(R.id.stop4);
            progressStops[4] = itemView.findViewById(R.id.stop5);
            progressStops[5] = itemView.findViewById(R.id.stop6);
            progressStops[6] = itemView.findViewById(R.id.stop7);
            progressStops[7] = itemView.findViewById(R.id.stop8);

            if (mapView != null) {
                mapView.onCreate(null);
                mapView.getMapAsync(this);
            }
        }

        @Override
        public void onMapReady(GoogleMap googleMap) {
            this.googleMap = googleMap;
            googleMap.getUiSettings().setAllGesturesEnabled(true);
            googleMap.getUiSettings().setZoomControlsEnabled(true);
            MapsInitializer.initialize(itemView.getContext());
            updateMapContents();
        }

        void bindMap(ShuttleItem shuttle, double uLat, double uLng) {
            currentShuttle = shuttle;
            userLat = uLat;
            userLng = uLng;
            if (mapView != null) mapView.onResume();
            updateMapContents();
        }

        private void updateMapContents() {
            if (googleMap == null || currentShuttle == null) return;
            googleMap.clear();

            if (!currentShuttle.isAvailable()) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(10.3541, 123.9115), 16));
                return;
            }

            LatLng shuttleLocation = new LatLng(currentShuttle.getDriverLat(), currentShuttle.getDriverLng());
            googleMap.addMarker(new MarkerOptions()
                    .position(shuttleLocation)
                    .title(currentShuttle.getBusName())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));

            if (userLat != 0) {
                googleMap.addMarker(new MarkerOptions()
                        .position(new LatLng(userLat, userLng))
                        .title("Me")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            }

            db.child("shuttleStops").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot stopSnap : snapshot.getChildren()) {
                        ShuttleStop stop = stopSnap.getValue(ShuttleStop.class);
                        if (stop != null) {
                            googleMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(stop.getLatitude(), stop.getLongitude()))
                                    .title(stop.getStopName())
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                        }
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });

            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(shuttleLocation, 16));
        }
    }
}
