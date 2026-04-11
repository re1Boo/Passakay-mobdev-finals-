package com.usc.passakay;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);

        View dot1 = findViewById(R.id.dot1);
        View dot2 = findViewById(R.id.dot2);
        View dot3 = findViewById(R.id.dot3);

        animateDots(dot1, dot2, dot3);

        // Run Seeder to populate database
        DataSeeder seeder = new DataSeeder();
        seeder.seedAll();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            finish();
        }, 3000); // Slightly longer delay to allow seeding to start
    }

    private void animateDots(View dot1, View dot2, View dot3) {
        View[] dots = {dot1, dot2, dot3};
        for (int i = 0; i < dots.length; i++) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(dots[i], "alpha", 0.2f, 1f, 0.2f);
            animator.setDuration(300);
            animator.setStartDelay(i * 200L);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.start();
        }
    }
}
