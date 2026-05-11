package com.usc.passakay;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class PassengerAdapter extends RecyclerView.Adapter<PassengerAdapter.ViewHolder> {

    private final Context context;
    private final List<User> passengers;

    public PassengerAdapter(Context context, List<User> passengers) {
        this.context = context;
        this.passengers = passengers;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_passenger, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User p = passengers.get(position);
        
        // Censor Name for Privacy (e.g., John Doe -> J*** D**)
        String censoredName = maskPart(p.getFirstName()) + " " + maskPart(p.getLastName());
        holder.tvName.setText(censoredName);
        
        // Censor ID Number for Privacy (e.g., 21101234 -> 21******)
        if (p.getStudentId() != null && !p.getStudentId().isEmpty()) {
            holder.tvCourse.setText("ID: " + maskId(p.getStudentId()));
        } else {
            holder.tvCourse.setText("Verified Passenger");
        }

        // Hide Profile Image for Privacy - Always use default icon
        holder.ivPhoto.setImageResource(R.drawable.ic_default_profile);
    }

    private String maskPart(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.length() <= 1) return s;
        StringBuilder sb = new StringBuilder();
        sb.append(s.charAt(0));
        for (int i = 1; i < s.length(); i++) sb.append("*");
        return sb.toString();
    }

    private String maskId(String id) {
        if (id == null || id.length() <= 2) return id != null ? id : "";
        StringBuilder sb = new StringBuilder();
        sb.append(id.substring(0, 2));
        for (int i = 2; i < id.length(); i++) sb.append("*");
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return passengers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivPhoto;
        TextView tvName, tvCourse;
        ViewHolder(View v) {
            super(v);
            ivPhoto = v.findViewById(R.id.ivPassengerPhoto);
            tvName = v.findViewById(R.id.tvPassengerName);
            tvCourse = v.findViewById(R.id.tvPassengerCourse);
        }
    }
}