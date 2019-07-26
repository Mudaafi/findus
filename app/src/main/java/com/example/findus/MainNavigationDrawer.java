package com.example.findus;

import android.content.Intent;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

public class MainNavigationDrawer extends CoreFunctions implements NavigationView.OnNavigationItemSelectedListener {
    public DrawerLayout parent_drawer_layout;
    public ConstraintLayout layout_to_populate;
    private Toolbar toolbar;

    @Override
    public void setContentView(int layoutResID) {
        selectedStore = getString(R.string.default_store); // selectedStore declared in CoreFunctions
        parent_drawer_layout = (DrawerLayout) getLayoutInflater().inflate(R.layout.nav_drawer_main, null);
        layout_to_populate = parent_drawer_layout.findViewById(R.id.nav_constr_layout);
        getLayoutInflater().inflate(layoutResID, layout_to_populate, true);
        super.setContentView(parent_drawer_layout);
        // Fulllscreen Code
        View overlay = findViewById(R.id.nav_drawer_layout_main);
        overlay.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        // Listeners and what not
        // Activates the Navigation Bar Buttons (custom function, see below)
        setNavigationViewListener();
        /*
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);*/
    }


    // Navigation Bar Listeners
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.calibration_link) {
            item.setChecked(true);
            startActivity(new Intent(this, CalibrationActivity.class));

        } else if (id == R.id.nav_map) {
            item.setChecked(true);
            startActivity(new Intent(this, LocalizerActivity.class));
        } else if (id == R.id.scanner_link) {
            item.setChecked(true);
            startActivity(new Intent(this, ScannerActivity.class));
        }
        return true;
    }

    // Declare Navigation View
    public void setNavigationViewListener() {
        NavigationView navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(MainNavigationDrawer.this);
    }
}
