package com.usc.passakay;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminBookingFragment extends Fragment {

    private RecyclerView recyclerView;
    private BookingAdapter adapter;
    private DatabaseReference db;
    private List<Trip> tripList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_bookings, container, false);
        
        recyclerView = view.findViewById(R.id.recyclerBookings);
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new BookingAdapter();
        recyclerView.setAdapter(adapter);

        loadBookings();
        return view;
    }

    private void loadBookings() {
        db.child("trips").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                tripList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Trip trip = child.getValue(Trip.class);
                    if (trip != null) tripList.add(0, trip); // Latest first
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {
        @NonNull
        @Override
        public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booking, parent, false);
            return new BookingViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
            Trip trip = tripList.get(position);

            holder.tvTripId.setText("Trip #" + trip.getTripId());
            holder.tvDriver.setText("Driver: " + (trip.getDriverId() == 0 ? "Pending" : trip.getDriverId()));
            holder.tvPassenger.setText("Passenger: " + trip.getPassengerId());
            holder.tvRoute.setText(trip.getPickupId() + " → " + trip.getDestinationId());
            holder.tvDateTime.setText(trip.getDate() + " | " + trip.getTime());
        }

        @Override
        public int getItemCount() { return tripList.size(); }

        class BookingViewHolder extends RecyclerView.ViewHolder {
            TextView tvTripId, tvDriver, tvPassenger, tvRoute, tvDateTime;
            BookingViewHolder(View itemView) {
                super(itemView);
                tvTripId = itemView.findViewById(R.id.tvTripId);
                tvDriver = itemView.findViewById(R.id.tvDriver);
                tvPassenger = itemView.findViewById(R.id.tvPassenger);
                tvRoute = itemView.findViewById(R.id.tvRoute);
                tvDateTime = itemView.findViewById(R.id.tvDateTime);
            }
        }
    }
}