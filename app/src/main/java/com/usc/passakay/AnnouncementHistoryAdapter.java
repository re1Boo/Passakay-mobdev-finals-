package com.usc.passakay;

import android.content.Context;
import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

public class AnnouncementHistoryAdapter extends RecyclerView.Adapter<AnnouncementHistoryAdapter.ViewHolder> {

    private Context context;
    private List<Map<String, Object>> historyList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Map<String, Object> data);
        void onItemLongClick(Map<String, Object> data, int position);
    }

    public AnnouncementHistoryAdapter(Context context, List<Map<String, Object>> historyList, OnItemClickListener listener) {
        this.context = context;
        this.historyList = historyList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_history_announcement, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> data = historyList.get(position);
        
        String message = (String) data.get("message");
        String priority = (String) data.get("priority");
        Long timestamp = (Long) data.get("timestamp");

        holder.tvMessage.setText(message);

        if (timestamp != null) {
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
            holder.tvTime.setText(relativeTime);
        }

        if ("warning".equals(priority)) {
            holder.ivIcon.setColorFilter(Color.parseColor("#FB8C00"));
        } else if ("emergency".equals(priority)) {
            holder.ivIcon.setColorFilter(Color.parseColor("#E53935"));
        } else {
            holder.ivIcon.setColorFilter(Color.parseColor("#FBC02D"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(data);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onItemLongClick(data, position);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        ImageView ivIcon;

        ViewHolder(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvHistoryMessage);
            tvTime = v.findViewById(R.id.tvHistoryTime);
            ivIcon = v.findViewById(R.id.ivPriorityIcon);
        }
    }
}
