package com.example.findus;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;

public class WelcomeActivity extends MainNavigationDrawer {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        // "Tap screen to continue" Listener
        ConstraintLayout currView = findViewById(R.id.welcome_layout);
        currView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent newIntent = new Intent(getApplicationContext(), CalibrationActivity.class);
                startActivity(newIntent);
            }
        });

        /* //Broken toolbar doesn't work
        private Toolbar toolbar;
        toolbar = findViewById(R.id.toolBar);
        setSupportActionBar(toolbar);
        */

        // Animations
        ArrayList<View> letters = new ArrayList<View>();
        letters.add(findViewById(R.id.fLetter));
        letters.add(findViewById(R.id.iLetter));
        letters.add(findViewById(R.id.nLetter));
        letters.add(findViewById(R.id.dLetter));
        letters.add(findViewById(R.id.uLetter));
        letters.add(findViewById(R.id.sLetter));
        Animation anim1 = AnimationUtils.loadAnimation(WelcomeActivity.this, R.anim.fadeinup);
        for (View view : letters) {
            view.startAnimation(anim1);
        }
        Animation anim2 = AnimationUtils.loadAnimation(WelcomeActivity.this, R.anim.fadeinup);
        TextView selfCredit = findViewById(R.id.many_letters);
        selfCredit.setAlpha(0);
        anim1.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                Animation anim2 = AnimationUtils.loadAnimation(WelcomeActivity.this, R.anim.fadein);
                TextView selfCredit = findViewById(R.id.many_letters);
                selfCredit.setAlpha(1);
                selfCredit.startAnimation(anim2);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }
}
