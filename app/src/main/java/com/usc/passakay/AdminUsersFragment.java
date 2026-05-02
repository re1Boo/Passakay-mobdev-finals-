package com.usc.passakay;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminUsersFragment extends Fragment {

    private RecyclerView recyclerView;
    private UserAdapter adapter;
    private EditText etSearch;
    private DatabaseReference db;
    private List<User> allUsers = new ArrayList<>();
    private List<User> filteredUsers = new ArrayList<>();
    private List<String> allUids = new ArrayList<>();
    private List<String> filteredUids = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_users, container, false);
        
        recyclerView = view.findViewById(R.id.recyclerUsers);
        etSearch = view.findViewById(R.id.etSearchUsers);
        
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UserAdapter();
        recyclerView.setAdapter(adapter);

        loadUsers();
        setupSearch();
        
        return view;
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filter(String query) {
        filteredUsers.clear();
        filteredUids.clear();
        String lowerQuery = query.toLowerCase().trim();
        
        for (int i = 0; i < allUsers.size(); i++) {
            User u = allUsers.get(i);
            String fullName = (u.getFirstName() + " " + u.getLastName()).toLowerCase();
            String studentId = u.getStudentId().toLowerCase();
            
            if (fullName.contains(lowerQuery) || studentId.contains(lowerQuery)) {
                filteredUsers.add(u);
                filteredUids.add(allUids.get(i));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadUsers() {
        db.child("users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                allUsers.clear();
                allUids.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    User user = child.getValue(User.class);
                    if (user != null && !user.getRole().equals("admin")) {
                        allUsers.add(user);
                        allUids.add(child.getKey());
                    }
                }
                filter(etSearch.getText().toString());
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void toggleUserStatus(String uid, User user) {
        String newStatus = user.getStatus().equals("active") ? "blocked" : "active";
        db.child("users").child(uid).child("status").setValue(newStatus)
                .addOnSuccessListener(a -> Toast.makeText(getContext(), "User " + newStatus, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
        @NonNull
        @Override
        public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new UserViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
            User user = filteredUsers.get(position);
            String uid = filteredUids.get(position);

            holder.tvName.setText(user.getFirstName() + " " + user.getLastName());
            holder.tvId.setText("ID: " + user.getStudentId());
            holder.tvRole.setText(user.getRole().toUpperCase());
            holder.tvStatus.setText(user.getStatus().toUpperCase());

            if (user.getStatus().equals("active")) {
                holder.btnToggle.setText("BLOCK");
                holder.btnToggle.setTextColor(0xFFE53935);
            } else {
                holder.btnToggle.setText("UNBLOCK");
                holder.btnToggle.setTextColor(0xFF43A047);
            }

            holder.btnToggle.setOnClickListener(v -> toggleUserStatus(uid, user));
        }

        @Override
        public int getItemCount() {
            return filteredUsers.size();
        }

        class UserViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvId, tvRole, tvStatus;
            MaterialButton btnToggle;
            UserViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvName);
                tvId = itemView.findViewById(R.id.tvStudentId);
                tvRole = itemView.findViewById(R.id.tvRole);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                btnToggle = itemView.findViewById(R.id.btnToggle);
            }
        }
    }
}