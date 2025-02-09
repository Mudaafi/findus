package com.example.findus;

import android.content.Context;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class LocalizerActivity extends MainNavigationDrawer {
    // -- View Variables
    ModifiedImageView mapView;
    final static public String SELECTED_LOCATION_NAME = "SELECTED LOCATION";
    public String SELECTED_MAP = "COM 1 Level 2, NUS";
    final static public String CURRENT_LOCATION_PIN_NAME = "Current Location";
    boolean keyboardOpen = false;

    // -- OnCreate (i.e. main)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Fulllscreen Code
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window w = getWindow();
            w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.localizer_main);

        final AutoCompleteTextView areaMap = findViewById(R.id.localizer_areamap_input);
        View overlay = findViewById(R.id.localizer_map);
        overlay.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        Log.d("LOGGED", selectedStore);
        // TO-DO
        refreshMapList(areaMap);

        // -- Map Codes
        mapView = (ModifiedImageView) findViewById(R.id.localizer_map);
        // Pull Map from Firestore
        if (systemsCheck(LocalizerActivity.this)) {
            queryFirestore(new UserStringCallback() {
                @Override
                public void onCallback(String currLocation) {
                    if (currLocation != null && !currLocation.equals("No Viable Location Found")) {
                        Log.d("LOGGED", "Localizer/OnCreate: " + currLocation);
                        // TODO: Figure out how to get an AreaMap from a Location Anchor Name
                        //displayLocation(R.id.localizer_map, currLocation, SELECTED_MAP, selectedStore);
                    } else {
                        Log.d("LOGGED", "No Matching Area Map Found");
                        Toast.makeText(LocalizerActivity.this, "No Matching Area Map Found. Sorry", Toast.LENGTH_SHORT).show();
                       // mapView.setImage(ImageSource.resource(R.drawable.floorplan_com1_l2_ver1));
                    }
                }
            }, selectedStore);
        }
        mapView.setImage(ImageSource.resource(R.drawable.floorplan_com1_l2_ver1));
        Log.d("LOGGED", "getScale() / maxScale: " + String.valueOf(mapView.getScale()) + " / " + String.valueOf(mapView.getMaxScale()));
        // For placing pin (singular)
        mapView.setOnImageEventListener(new ModifiedImageView.OnImageEventListener() {
            @Override
            public void onReady() {}

            @Override
            public void onImageLoaded() {
                mapView.setMinimumScaleType(ModifiedImageView.SCALE_TYPE_CENTER_CROP);
                mapView.setScaleAndCenter(1.2f, mapView.getCenter());
                Log.d("LOGGED", "getScale() / maxScale: " + String.valueOf(mapView.getScale()) + " / " + String.valueOf(mapView.getMaxScale()));

                mapView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        areaMap.setVisibility(View.GONE);
                        hideKeyboard(v);
                        keyboardOpen = false;
                        if (areaMap.isFocused()) {
                            areaMap.clearFocus();
                        }
                        if (isTap(event)) {
                            // start_x and start_y are class variables declared in CoreFunctions
                            final PointF source_coord = mapView.viewToSourceCoord((float) start_x, (float) start_y);
                            // FIX THIS
                            String areaMap = SELECTED_MAP;
                            final String inputStore = selectedStore;
                            // Everytime you tap it will pull from firestore
                            db.collection(PATH_TO_LOCATION_LISTS).document(areaMap).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                @Override
                                public void onSuccess(DocumentSnapshot document) {
                                    if (!document.exists()) {
                                        Toast.makeText(LocalizerActivity.this, "This map has no registered Location Anchors.", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    Map<String, Object> firestoreCoordMap = (HashMap<String, Object>) document.getData().get(inputStore);
                                    Map<String, PointF> convertedCoordMap = firestoreMapToCoordMap(firestoreCoordMap);

                                    double dy = 0, dx = 0;
                                    double min_dist = 999999;
                                    String finalLocation = "???";
                                    PointF finalCoord = null;
                                    for (String point : convertedCoordMap.keySet()) {
                                        if (convertedCoordMap.get(point) != null) {
                                            dy = Math.abs(source_coord.y - convertedCoordMap.get(point).y);
                                            dx = Math.abs(source_coord.x - convertedCoordMap.get(point).x);
                                            if (Math.sqrt(dy * dy + dx * dx) < min_dist) {
                                                min_dist = Math.sqrt(dy * dy + dx * dx);
                                                finalLocation = point;
                                                finalCoord = convertedCoordMap.get(point);
                                            }
                                        }
                                    }
                                    if (finalCoord != null) {
                                        Toast.makeText(LocalizerActivity.this, "Location: " + finalLocation, Toast.LENGTH_SHORT).show();
                                        mapView.removePins();
                                        mapView.setPin(SELECTED_LOCATION_NAME, finalCoord);
                                    }
                                }
                            });
                        }
                        return false;
                    }
                });
            }
            @Override
            public void onPreviewLoadError(Exception e) {}
            @Override
            public void onImageLoadError(Exception e) {}
            @Override
            public void onTileLoadError(Exception e) {}
            @Override
            public void onPreviewReleased() {}
        });

        // -- GUI CODES

        Button mainButton = findViewById(R.id.localizerButton);
        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (systemsCheck(LocalizerActivity.this)) {
                    queryFirestore(new UserStringCallback() {
                        @Override
                        public void onCallback(String currLocation) {
                            if (currLocation != null && !currLocation.equals("No Viable Location Found")) {
                                Log.d("LOGGED", "@/Localizer/: " + currLocation);
                                displayLocation(R.id.localizer_map, currLocation, SELECTED_MAP, selectedStore);
                            } else {
                                Log.d("LOGGED", "No corresponding location found2");
                                Toast.makeText(LocalizerActivity.this, "No corresponding location found", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, selectedStore);
                }
            }
        });
        mainButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                areaMap.setVisibility(View.VISIBLE);
                return false;
            }
        });

        areaMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                keyboardOpen = true;
                areaMap.showDropDown();
            }
        });

        // If an item is selected from the dropdown
        areaMap.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //Toast.makeText(CalibrationActivity.this, inputAreaMap.getText().toString(), Toast.LENGTH_SHORT).show();
                hideKeyboard(areaMap);
                keyboardOpen = false;
                SELECTED_MAP = areaMap.getText().toString();
                mapView.removePins();
                getImage(LocalizerActivity.this, areaMap.getText().toString(), mapView);
            }
        });


    }

    public void displayLocation(Integer idImage, final String currLocation, String areaMap, final String collectionNamePath) {
        final ModifiedImageView mapView = findViewById(idImage);
        db.collection(PATH_TO_LOCATION_LISTS).document(areaMap).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful())  {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        Map<String, Object> fsMapCoords = (HashMap<String, Object>) document.getData().get(collectionNamePath);
                        Map<String, PointF> convertedMapCoords = firestoreMapToCoordMap(fsMapCoords);
                        PointF pinCoords = convertedMapCoords.get(currLocation);
                        mapView.removePins();
                        mapView.setPin(CURRENT_LOCATION_PIN_NAME, pinCoords);
                        mapView.setScaleAndCenter(1.2f, convertedMapCoords.get(currLocation));
                        return;
                    }
                }
                // Add failure indicator here
                Log.d("LOGGED: ", "No corresponding location found1");
            }
        });
    }
    public void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        //If no view currently has focus, escape?
        if (view == null) {
            return;
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
