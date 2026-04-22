package com.usc.passakay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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

import java.util.List;

public class ShuttleAdapter extends RecyclerView.Adapter<ShuttleAdapter.ShuttleViewHolder> {

    private final Context context;
    private final List<ShuttleItem> shuttleList;
    private int expandedPosition = -1;

    public ShuttleAdapter(Context context, List<ShuttleItem> shuttleList) {
        this.context     = context;
        this.shuttleList = shuttleList;
    }

    @NonNull
    @Override
    public ShuttleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_shuttle, parent, false);
        return new ShuttleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShuttleViewHolder holder, int position) {
        ShuttleItem shuttle = shuttleList.get(position);
        boolean isExpanded  = position == expandedPosition;

        // Set bus info
        holder.tvBusName.setText(shuttle.getBusName());
        holder.tvDriverName.setText("Driver: " + shuttle.getDriverName());
        holder.tvPlateNumber.setText("Plate: " + shuttle.getPlateNumber());
        holder.tvEta.setText(String.valueOf(shuttle.getEta()));
        
        // New fields
        holder.tvCapacity.setText("Capacity: " + shuttle.getCurrentPassengers() + "/" + shuttle.getCapacity());
        holder.tvLastUpdated.setText("Updated: " + shuttle.getLastUpdated());

        // Unavailable shuttle styling
        if (!shuttle.isAvailable()) {
            holder.cardShuttle.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
            holder.tvBusName.setTextColor(Color.parseColor("#AAAAAA"));
            holder.etaBadge.setBackgroundResource(R.drawable.rounded_gray_badge);
            holder.tvEta.setText("0");
            holder.layoutDetails.setVisibility(View.GONE);
        } else {
            holder.cardShuttle.setCardBackgroundColor(Color.WHITE);
            holder.tvBusName.setTextColor(Color.parseColor("#1A1A1A"));
            holder.etaBadge.setBackgroundResource(R.drawable.rounded_yellow_badge);
            holder.layoutDetails.setVisibility(View.VISIBLE);
        }

        // Draw stop indicators
        drawStopIndicators(holder.layoutStops, shuttle.isAvailable());

        // Show/hide map section
        holder.cardMap.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

        // Click to expand/collapse
        holder.cardShuttle.setOnClickListener(v -> {
            if (!shuttle.isAvailable()) return;

            int previousExpanded = expandedPosition;
            expandedPosition = isExpanded ? -1 : holder.getAdapterPosition();

            if (previousExpanded != -1) notifyItemChanged(previousExpanded);
            if (expandedPosition != -1) notifyItemChanged(expandedPosition);
        });

        // Initialize and bind map if expanded
        if (isExpanded) {
            holder.bindMap(shuttle);
        }
    }

    private void drawStopIndicators(LinearLayout layout, boolean isAvailable) {
        layout.removeAllViews();
        int stopCount = 7;
        int youIndex = 4; // Simulated "You" position
        int dotColor  = isAvailable ? Color.parseColor("#FFEA08") : Color.parseColor("#DDDDDD");
        int lineColor = isAvailable ? Color.parseColor("#EEEEEE") : Color.parseColor("#EEEEEE");
        int youColor  = Color.parseColor("#FF5722");

        for (int i = 0; i < stopCount; i++) {
            // Dot
            View dot = new View(context);
            int size = (i == youIndex) ? 14 : 10;
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(size, size);
            dotParams.gravity = android.view.Gravity.CENTER_VERTICAL;
            dot.setLayoutParams(dotParams);
            
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(i == youIndex ? youColor : dotColor);
            if (i != youIndex && isAvailable) {
                circle.setStroke(1, Color.parseColor("#FFD600"));
            }
            dot.setBackground(circle);
            layout.addView(dot);

            // Line
            if (i < stopCount - 1) {
                View line = new View(context);
                LinearLayout.LayoutParams lineParams =
                        new LinearLayout.LayoutParams(0, 4, 1f);
                lineParams.gravity = android.view.Gravity.CENTER_VERTICAL;
                line.setLayoutParams(lineParams);
                line.setBackgroundColor(lineColor);
                layout.addView(line);
            }
        }
    }

    @Override
    public int getItemCount() {
        return shuttleList.size();
    }

    static class ShuttleViewHolder extends RecyclerView.ViewHolder implements OnMapReadyCallback {
        TextView tvBusName, tvDriverName, tvPlateNumber, tvEta, tvCapacity, tvLastUpdated;
        LinearLayout layoutStops, etaBadge, layoutDetails;
        CardView cardShuttle, cardMap;
        MapView mapView;
        GoogleMap googleMap;
        ShuttleItem currentShuttle;

        ShuttleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBusName     = itemView.findViewById(R.id.tvBusName);
            tvDriverName  = itemView.findViewById(R.id.tvDriverName);
            tvPlateNumber = itemView.findViewById(R.id.tvPlateNumber);
            tvEta         = itemView.findViewById(R.id.tvEta);
            tvCapacity    = itemView.findViewById(R.id.tvCapacity);
            tvLastUpdated = itemView.findViewById(R.id.tvLastUpdated);
            layoutStops   = itemView.findViewById(R.id.layoutStops);
            etaBadge      = itemView.findViewById(R.id.etaBadge);
            layoutDetails = itemView.findViewById(R.id.layoutDetails);
            cardShuttle   = itemView.findViewById(R.id.cardShuttle);
            cardMap       = itemView.findViewById(R.id.cardMap);
            mapView       = itemView.findViewById(R.id.mapView);

            if (mapView != null) {
                mapView.onCreate(null);
                mapView.getMapAsync(this);
            }
        }

        @Override
        public void onMapReady(GoogleMap googleMap) {
            this.googleMap = googleMap;
            googleMap.getUiSettings().setAllGesturesEnabled(false); // Static feel
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

            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(shuttleLocation, 15));
        }
    }
}
