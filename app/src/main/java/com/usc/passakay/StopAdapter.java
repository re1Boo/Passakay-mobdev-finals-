package com.usc.passakay;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StopAdapter extends RecyclerView.Adapter<StopAdapter.StopViewHolder> {

    private final Context context;
    private final List<StopItem> stopList;

    public StopAdapter(Context context, List<StopItem> stopList) {
        this.context  = context;
        this.stopList = stopList;
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

        // Yellow button = passengers waiting; gray = no passengers
        if (stop.isHasWaiting()) {
            holder.btnPickUp.setBackgroundResource(R.drawable.rounded_yellow_badge);
            holder.btnPickUp.setTextColor(Color.parseColor("#000000"));
        } else {
            holder.btnPickUp.setBackgroundResource(R.drawable.rounded_gray_badge);
            holder.btnPickUp.setTextColor(Color.parseColor("#888888"));
        }

        holder.btnPickUp.setOnClickListener(v -> {
            // TODO: implement pick-up action (update Firebase, notify passengers, etc.)
            Toast.makeText(context,
                    "Picking up at " + stop.getStopName(),
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return stopList.size();
    }

    static class StopViewHolder extends RecyclerView.ViewHolder {
        TextView tvStopName, tvWaiting, tvDistance;
        Button btnPickUp;

        StopViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStopName = itemView.findViewById(R.id.tvStopName);
            tvWaiting  = itemView.findViewById(R.id.tvWaiting);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            btnPickUp  = itemView.findViewById(R.id.btnPickUp);
        }
    }
}