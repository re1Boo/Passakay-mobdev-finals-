package com.usc.passakay;

import android.content.Context;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

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
        holder.tvName.setText(p.getFirstName() + " " + p.getLastName());
        
        // Show ID Number instead of a hardcoded "Passenger" label
        if (p.getStudentId() != null && !p.getStudentId().isEmpty()) {
            holder.tvCourse.setText("ID: " + p.getStudentId());
        } else {
            holder.tvCourse.setText("Verified Passenger");
        }

        String photoStr = p.getProfileImageUrl();
        if (photoStr != null && !photoStr.isEmpty()) {
            try {
                // Decode Base64 string for Glide
                byte[] imageBytes = Base64.decode(photoStr, Base64.DEFAULT);
                Glide.with(context)
                        .asBitmap()
                        .load(imageBytes)
                        .placeholder(R.drawable.ic_default_profile)
                        .into(holder.ivPhoto);
            } catch (Exception e) {
                holder.ivPhoto.setImageResource(R.drawable.ic_default_profile);
            }
        } else {
            holder.ivPhoto.setImageResource(R.drawable.ic_default_profile);
        }
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