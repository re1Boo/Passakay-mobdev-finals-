package com.usc.passakay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
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

import java.util.List;

public class ShuttleAdapter extends RecyclerView.Adapter<ShuttleAdapter.ShuttleViewHolder> {

    private final Context context;
    private final List<ShuttleItem> shuttleList;
    private int expandedPosition = -1;
    private DatabaseReference db;

    public ShuttleAdapter(Context context, List<ShuttleItem> shuttleList) {
        this.context     = context;
        this.shuttleList = shuttleList;
        this.db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
    }

    @NonNull
    @Override
    public ShuttleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_shuttle_2, parent, false);
        return new ShuttleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShuttleViewHolder holder, int position) {
        ShuttleItem shuttle = shuttleList.get(position);
        boolean isExpanded  = position == expandedPosition;

        holder.tvBusName.setText(shuttle.getBusName());
        holder.tvDriverName.setText("Driver: " + shuttle.getDriverName());
        holder.tvPlateNumber.setText("Plate Number: " + shuttle.getPlateNumber());

        if (!shuttle.isAvailable()) {
            holder.cardShuttle.setCardBackgroundColor(Color.parseColor("#E0E0E0"));
            holder.tvBusName.setTextColor(Color.parseColor("#AAAAAA"));
        } else {
            holder.cardShuttle.setCardBackgroundColor(Color.WHITE);
            holder.tvBusName.setTextColor(Color.parseColor("#1A1A1A"));
        }

        holder.cardMap.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

        holder.cardShuttle.setOnClickListener(v -> {
            if (!shuttle.isAvailable()) return;
            int previousExpanded = expandedPosition;
            expandedPosition = isExpanded ? -1 : holder.getAdapterPosition();
            if (previousExpanded != -1) notifyItemChanged(previousExpanded);
            if (expandedPosition != -1) notifyItemChanged(expandedPosition);
        });

        if (shuttle.isStandby()) {
            holder.btnStatus.setText("Standby");
            holder.btnStatus.setBackgroundResource(R.drawable.rounded_green_btn);
            holder.btnStatus.setBackgroundTintList(null);
            holder.btnStatus.setOnClickListener(v -> deployShuttle(shuttle));
        } else {
            holder.btnStatus.setText("Deployed");
            holder.btnStatus.setBackgroundResource(R.drawable.rounded_yellow_badge);
            holder.btnStatus.setBackgroundTintList(null);
            holder.btnStatus.setOnClickListener(null);
        }

        if (isExpanded) {
            holder.bindMap(shuttle);
        }
    }

    private void deployShuttle(ShuttleItem shuttleItem) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    String driverName = user.getFirstName() + " " + user.getLastName();

                    DatabaseReference shuttleRef = db.child("shuttles").child(shuttleItem.getShuttleId());
                    shuttleRef.child("status").setValue("Deployed");
                    shuttleRef.child("driverName").setValue(driverName);
                    shuttleRef.child("driverId").setValue(uid);

                    Intent intent = new Intent(context, ShuttleStopActivity.class);
                    intent.putExtra(ShuttleStopActivity.EXTRA_BUS_NAME,   shuttleItem.getBusName());
                    intent.putExtra(ShuttleStopActivity.EXTRA_DRIVER_LAT, shuttleItem.getDriverLat());
                    intent.putExtra(ShuttleStopActivity.EXTRA_DRIVER_LNG, shuttleItem.getDriverLng());
                    intent.putExtra("shuttleId", shuttleItem.getShuttleId());
                    context.startActivity(intent);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public int getItemCount() {
        return shuttleList.size();
    }

    static class ShuttleViewHolder extends RecyclerView.ViewHolder implements OnMapReadyCallback {
        TextView tvBusName, tvDriverName, tvPlateNumber;
        Button btnStatus;
        CardView cardShuttle, cardMap;
        MapView mapView;
        GoogleMap googleMap;
        ShuttleItem currentShuttle;

        ShuttleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBusName    = itemView.findViewById(R.id.tvBusName);
            tvDriverName = itemView.findViewById(R.id.tvDriverName);
            tvPlateNumber = itemView.findViewById(R.id.tvPlateNumber);
            btnStatus     = itemView.findViewById(R.id.btnStatus);
            cardShuttle  = itemView.findViewById(R.id.cardShuttle);
            cardMap      = itemView.findViewById(R.id.cardMap);
            mapView      = itemView.findViewById(R.id.mapView);

            if (mapView != null) {
                mapView.onCreate(null);
                mapView.getMapAsync(this);
            }
        }

        @Override
        public void onMapReady(GoogleMap googleMap) {
            this.googleMap = googleMap;
            MapsInitializer.initialize(itemView.getContext());
            updateMapContents();
        }

        void bindMap(ShuttleItem shuttle) {
            currentShuttle = shuttle;
            updateMapContents();
        }

        private void updateMapContents() {
            if (googleMap == null || currentShuttle == null) return;
            LatLng shuttleLocation = new LatLng(currentShuttle.getDriverLat(), currentShuttle.getDriverLng());
            googleMap.clear();
            googleMap.addMarker(new MarkerOptions()
                    .position(shuttleLocation)
                    .title(currentShuttle.getBusName())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(shuttleLocation, 16));
        }
    }
}