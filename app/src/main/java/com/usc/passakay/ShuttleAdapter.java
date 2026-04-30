package com.usc.passakay;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class ShuttleAdapter extends RecyclerView.Adapter<ShuttleAdapter.ShuttleViewHolder> {

    private final Context context;
    private final List<ShuttleItem> shuttleList;
    private int expandedPosition = -1;
    private DatabaseReference db;
    private final boolean isPassengerView;

    public ShuttleAdapter(Context context, List<ShuttleItem> shuttleList) {
        this.context = context;
        this.shuttleList = shuttleList;
        this.db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        // Determine if this is the passenger view to load the correct layout
        this.isPassengerView = context instanceof PassengerHomeActivity || context instanceof MainActivity;
    }

    @NonNull
    @Override
    public ShuttleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Load item_shuttle for passengers (with ETA) and item_shuttle_2 for others
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

        // Handle ETA display for passenger view
        if (isPassengerView && holder.tvEta != null) {
            holder.tvEta.setText(String.valueOf(shuttle.getEta()));
            if (holder.etaBadge != null) {
                if (!shuttle.isAvailable()) {
                    holder.etaBadge.setBackgroundResource(R.drawable.rounded_gray_badge);
                    holder.tvEta.setText("0");
                } else {
                    holder.etaBadge.setBackgroundResource(R.drawable.rounded_yellow_badge);
                }
            }
        }

        // Handle card styling
        if (!shuttle.isAvailable()) {
            holder.cardShuttle.setCardBackgroundColor(Color.parseColor("#E0E0E0"));
            holder.tvBusName.setTextColor(Color.parseColor("#757575"));
        } else {
            holder.cardShuttle.setCardBackgroundColor(Color.WHITE);
            holder.tvBusName.setTextColor(Color.parseColor("#1A1A1A"));
        }

        // Handle Map Visibility
        if (holder.cardMap != null) {
            holder.cardMap.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            if (isExpanded) {
                holder.bindMap(shuttle);
            }
        }

        // Toggle Expand/Collapse on card click
        holder.cardShuttle.setOnClickListener(v -> {
            if (!shuttle.isAvailable() && !isPassengerView) return;
            
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

    @Override
    public int getItemCount() {
        return shuttleList.size();
    }

    static class ShuttleViewHolder extends RecyclerView.ViewHolder implements OnMapReadyCallback {
        TextView tvBusName, tvDriverName, tvPlateNumber, tvEta;
        View etaBadge;
        CardView cardShuttle, cardMap;
        MapView mapView;
        GoogleMap googleMap;
        ShuttleItem currentShuttle;

        ShuttleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBusName = itemView.findViewById(R.id.tvBusName);
            tvDriverName = itemView.findViewById(R.id.tvDriverName);
            tvPlateNumber = itemView.findViewById(R.id.tvPlateNumber);
            tvEta = itemView.findViewById(R.id.tvEta);
            etaBadge = itemView.findViewById(R.id.etaBadge);
            cardShuttle = itemView.findViewById(R.id.cardShuttle);
            cardMap = itemView.findViewById(R.id.cardMap);
            mapView = itemView.findViewById(R.id.mapView);

            if (mapView != null) {
                mapView.onCreate(null);
                mapView.getMapAsync(this);
            }
        }

        @Override
        public void onMapReady(GoogleMap googleMap) {
            this.googleMap = googleMap;
            googleMap.getUiSettings().setAllGesturesEnabled(false);
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
