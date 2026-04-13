package com.usc.passakay;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminUsersFragment extends Fragment {

    private ListView listView;
    private DatabaseReference db;
    private List<User> userList = new ArrayList<>();
    private List<String> uidList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_users, container, false);
        listView = view.findViewById(R.id.listUsers);
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        loadUsers();
        return view;
    }

    private void loadUsers() {
        db.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                userList.clear();
                uidList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    User user = child.getValue(User.class);
                    if (user != null && !user.getRole().equals("admin")) {
                        userList.add(user);
                        uidList.add(child.getKey());
                    }
                }
                setupListView();
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void setupListView() {
        ArrayAdapter<User> adapter = new ArrayAdapter<User>(getContext(),
                R.layout.item_user, userList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_user, parent, false);
                }
                User user = userList.get(position);
                String uid = uidList.get(position);

                TextView tvName     = convertView.findViewById(R.id.tvName);
                TextView tvStudentId = convertView.findViewById(R.id.tvStudentId);
                TextView tvRole     = convertView.findViewById(R.id.tvRole);
                TextView tvStatus   = convertView.findViewById(R.id.tvStatus);
                Button btnToggle    = convertView.findViewById(R.id.btnToggle);

                tvName.setText(user.getFirstName() + " " + user.getLastName());
                tvStudentId.setText("ID: " + user.getStudentId());
                tvRole.setText("Role: " + user.getRole());
                tvStatus.setText("Status: " + user.getStatus());

                // Set button text based on status
                if (user.getStatus().equals("active")) {
                    btnToggle.setText("Block");
                    btnToggle.setBackgroundColor(0xFFE53935);
                } else {
                    btnToggle.setText("Unblock");
                    btnToggle.setBackgroundColor(0xFF43A047);
                }

                btnToggle.setOnClickListener(v -> toggleUserStatus(uid, user));

                return convertView;
            }
        };
        listView.setAdapter(adapter);
    }

    private void toggleUserStatus(String uid, User user) {
        String newStatus = user.getStatus().equals("active") ? "blocked" : "active";
        db.child("users").child(uid).child("status").setValue(newStatus)
                .addOnSuccessListener(a -> Toast.makeText(getContext(),
                        "User " + newStatus, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(),
                        "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}