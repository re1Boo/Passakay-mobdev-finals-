package com.usc.passakay;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.tabs.TabLayout;
import androidx.viewpager2.widget.ViewPager2;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class AdminDashboardActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        tabLayout  = findViewById(R.id.tabLayout);
        viewPager  = findViewById(R.id.viewPager);

        // Set up ViewPager with fragments
        AdminPagerAdapter adapter = new AdminPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Link TabLayout with ViewPager
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                tabLayout.selectTab(tabLayout.getTabAt(position));
            }
        });
    }

    // Adapter for tabs
    static class AdminPagerAdapter extends FragmentStateAdapter {
        public AdminPagerAdapter(FragmentActivity fa) { super(fa); }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new AdminUsersFragment();
                case 1: return new AdminDriversFragment();
                case 2: return new AdminBookingFragment();
                default: return new AdminUsersFragment();
            }
        }

        @Override
        public int getItemCount() { return 3; }
    }
}