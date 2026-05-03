package com.usc.passakay;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StopAdapter extends RecyclerView.Adapter<StopAdapter.StopViewHolder> {
    public interface OnPickUpClickListener {
        void onPickUp(StopItem item);
    }

    private final Context context;
    private final List<StopItem> stopList;
    private final OnPickUpClickListener pickUpListener;

    public StopAdapter(Context context, List<StopItem> stopList, OnPickUpClickListener listener) {
        this.context  = context;
        this.stopList = stopList;
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
        } else {
            holder.btnPickUp.setEnabled(false);
            holder.btnPickUp.setBackgroundResource(R.drawable.rounded_gray_badge);
            holder.btnPickUp.setTextColor(Color.parseColor("#888888"));
            holder.btnPickUp.setAlpha(0.5f);
        }

        holder.btnPickUp.setOnClickListener(v -> {
            if (pickUpListener != null) {
                pickUpListener.onPickUp(stop);
            }
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