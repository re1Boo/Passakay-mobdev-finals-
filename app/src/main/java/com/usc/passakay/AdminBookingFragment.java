package com.usc.passakay;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminBookingFragment extends Fragment {

    private ListView listView;
    private DatabaseReference db;
    private List<Trip> tripList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_bookings, container, false);
        listView = view.findViewById(R.id.listBookings);
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
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
                    if (trip != null) tripList.add(trip);
                }
                setupListView();
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void setupListView() {
        ArrayAdapter<Trip> adapter = new ArrayAdapter<Trip>(getContext(),
                R.layout.item_booking, tripList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_booking, parent, false);
                }
                Trip trip = tripList.get(position);

                TextView tvTripId      = convertView.findViewById(R.id.tvTripId);
                TextView tvDriver      = convertView.findViewById(R.id.tvDriver);
                TextView tvPassenger   = convertView.findViewById(R.id.tvPassenger);
                TextView tvRoute       = convertView.findViewById(R.id.tvRoute);
                TextView tvDateTime    = convertView.findViewById(R.id.tvDateTime);

                tvTripId.setText("Trip #" + trip.getTripId());
                tvDriver.setText("Driver ID: " + trip.getDriverId());
                tvPassenger.setText("Passenger ID: " + trip.getPassengerId());
                tvRoute.setText("From: " + trip.getPickupId() + " → To: " + trip.getDestinationId());
                tvDateTime.setText(trip.getDate() + " " + trip.getTime());

                return convertView;
            }
        };
        listView.setAdapter(adapter);
    }
}