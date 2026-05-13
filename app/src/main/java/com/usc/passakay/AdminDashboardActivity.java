package com.usc.passakay;

import android.os.Bundle;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class AdminDashboardActivity extends BaseActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        // Set up logout button
        Button btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }

        // Set up ViewPager with fragments
        AdminPagerAdapter adapter = new AdminPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Sync TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Users"); break;
                case 1: tab.setText("Drivers"); break;
                case 2: tab.setText("Bookings"); break;
                case 3: tab.setText("Announce"); break;
                case 4: tab.setText("Add Driver"); break;
            }
        }).attach();
        
        // Ensure tabs are scrollable to fit all 5 items
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
    }

    private static class AdminPagerAdapter extends FragmentStateAdapter {
        public AdminPagerAdapter(@NonNull FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new AdminUsersFragment();
                case 1: return new AdminDriversFragment();
                case 2: return new AdminBookingFragment();
                case 3: return new AdminAnnouncementsFragment();
                case 4: return new AdminCreateDriverFragment();
                default: return new AdminUsersFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 5;
        }
    }
}
