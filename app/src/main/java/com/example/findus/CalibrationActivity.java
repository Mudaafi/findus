package com.example.findus;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CalibrationActivity extends MainNavigationDrawer {
    // -- View Variables
    ModifiedImageView mapView; // Map
    boolean keyboardOpen = false; // To hide and show keyboard // For revealing overlapping dropdown menu
    boolean dropdownStoreOpen = false; // To hide and show storeInput dropdown(For UI purposes)
    private final static String CALIBRATION_PIN_NAME = "Calibration Pin"; // For naming the pin to be stored together with location
    private String currentLocation;

    // -- OnCreate (i.e. main)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calibration_main);
        // pulls the name of all collections from Firestore
        refreshStoreList();
        Log.d("LOGGED", selectedStore);

        // -- Map Codes
        mapView = (ModifiedImageView) findViewById(R.id.calibration_map);
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
                        // ToggleButton to check if pin-placement is intended (default: true)
                        ToggleButton toggleButton = findViewById(R.id.toggleButton);
                        if (isTap(event)){
                            // start_x and start_y are class variables declared in CoreFunctions
                            final PointF source_coord = mapView.viewToSourceCoord((float) start_x, (float) start_y);
                            if (toggleButton.isChecked()) {
                                // Remove all visible pins
                                if (mapView.hasPins()) {mapView.removePins();}
                                // Place the to-be-registered pin
                                mapView.setPin(CALIBRATION_PIN_NAME,source_coord);
                            } else { // toggleButton is not checked
                                String areaMap = String.valueOf(((EditText) findViewById(R.id.inputAreaMap)).getText());
                                final String inputStore = String.valueOf(((AutoCompleteTextView) findViewById(R.id.inputStore)).getText());
                                // Everytime you tap it will pull from firestore
                                db.collection(PATH_TO_LOCATION_LISTS).document(areaMap).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                    @Override
                                    public void onSuccess(DocumentSnapshot document) {
                                        Map<String, Object> firestoreCoordMap = (HashMap<String, Object>) document.getData().get(inputStore);
                                        Map<String, PointF> convertedCoordMap = firestoreMapToCoordMap(firestoreCoordMap);

                                        double dy = 0, dx = 0;
                                        double min_dist= 999999;
                                        String finalLocation = "???";
                                        PointF finalCoord = null;
                                        for (String point : convertedCoordMap.keySet()) {
                                            if (convertedCoordMap.get(point) != null) {
                                                dy = Math.abs(source_coord.y - convertedCoordMap.get(point).y);
                                                dx = Math.abs(source_coord.x - convertedCoordMap.get(point).x);
                                                if (Math.sqrt(dy*dy + dx*dx) < min_dist) {
                                                    min_dist = Math.sqrt(dy*dy + dx*dx);
                                                    finalLocation = point;
                                                    finalCoord = convertedCoordMap.get(point);
                                                }
                                            }
                                        }
                                        if (finalCoord != null) {
                                            mapView.removePins();
                                            mapView.setPin(CALIBRATION_PIN_NAME, finalCoord);
                                            currentLocation = finalLocation;
                                            EditText inputLocation = (EditText) findViewById(R.id.inputLocation);
                                            inputLocation.setText(currentLocation);
                                        }
                                    }
                                });
                            }
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


        // -- GUI Codes

        final ToggleButton toggleButton = findViewById(R.id.toggleButton); // default: true
        final Button mainButton = findViewById(R.id.registerButton);
        final AutoCompleteTextView inputStore = findViewById(R.id.inputStore);
        ConstraintLayout currView = findViewById(R.id.calibration_main);
        final EditText inputLocation = findViewById(R.id.inputLocation);
        final EditText inputAreaMap = findViewById(R.id.inputAreaMap);

        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainButton.setFocusableInTouchMode(true); // to clear focus from other input fields
                mainButton.requestFocus();
                // Obtain values from input fields as variables
                final String collectionNamePath = String.valueOf(inputStore.getText());
                final String locationName = String.valueOf(inputLocation.getText());
                final String areaMap = String.valueOf(inputAreaMap.getText());
                final PointF coordinatesInput = mapView.getPinCoords(CALIBRATION_PIN_NAME);

                if (toggleButton.isChecked()) {
                    registerLocation(CalibrationActivity.this, areaMap, locationName, collectionNamePath, coordinatesInput);
                } else {
                    redefineLocation(currentLocation, locationName, areaMap, collectionNamePath, coordinatesInput);
                    currentLocation = locationName;
                }
                mainButton.setFocusableInTouchMode(false); // resets so that the button doesn't require two taps to launch
            }
        });

        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                final String areaMap = String.valueOf(inputAreaMap.getText());
                final String collectionNamePath = String.valueOf(inputStore.getText());
                if (!isChecked) {
                    getAndShowLocations(R.id.calibration_map, areaMap, collectionNamePath);
                    mainButton.setText(R.string.redefine_button_text);
                } else {
                    mainButton.setText(R.string.register_button_text);
                }
             }
        });

        // If Input Field for "Store" is clicked again, hide the keyboard and show dropdown menu
        inputStore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!keyboardOpen) {
                    keyboardOpen = true;
                    dropdownStoreOpen = false;
                    inputStore.dismissDropDown();
                } else {
                    inputStore.showDropDown();
                    dropdownStoreOpen = true;
                    hideKeyboard(v);
                    keyboardOpen = false;
                }
            }
        });

        // If item is selected from inputStore's dropdown menu, declare relevant booleans to be false;
        inputStore.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dropdownStoreOpen = false;
                keyboardOpen = false;
            }
        });

        // If Background is clicked, dismiss keyboard and show dropdown menus (if any) for currently focused input fields
        currView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(v);
                keyboardOpen = false;
                if (inputLocation.isFocused()) {
                    inputLocation.clearFocus();
                }
                if (inputStore.isFocused()) {
                    inputStore.clearFocus();
                    if (!dropdownStoreOpen) {
                        inputStore.showDropDown();
                        dropdownStoreOpen = true;
                    } else {
                        inputStore.dismissDropDown();
                        dropdownStoreOpen = false;
                    }
                }
            }
        });
    }

    // -- Other Functions (GUI Related)
    // Access Firestore and pull a list of Collection names from a document
    private void refreshStoreList() {
        // db refers to Firestore Database which is declared in CoreFunctions.java
        Task<DocumentSnapshot> task = db.document("Miscellaneous/CollectionList").get();
        task.addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    ArrayList<String> firestoreCollections = new ArrayList<String>();
                    Map<String, Object> collectionList = new HashMap<String, Object>();
                    collectionList = task.getResult().getData();
                    for (String id : collectionList.keySet()) {
                        if (!FORBIDDEN_STORES.contains(id)) {
                            firestoreCollections.add(id);
                        }
                    }
                    AutoCompleteTextView storeList = findViewById(R.id.inputStore);
                    storeList.setThreshold(1);
                    ArrayAdapter<String> adaptedArray = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_dropdown_item, firestoreCollections);
                    storeList.setAdapter(adaptedArray);
                    Log.d("LOGGED: ", "List of Collections Loaded");
                }
            }
        });
    }

    // Function to hide keyboard (do you know how dumb this is)
    public void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        //If no view currently has focus, escape?
        if (view == null) {
            return;
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // Custom Interface for Boolean Alert Dialogs
    public interface UserBooleanCallback {
        void onCallback(Boolean confirm);
    }

    // Function to confirm storing without map coordinates
    public void UserConfirmation(final UserBooleanCallback myCallback, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Notice: You're doing great sweetie");
        builder.setMessage(msg);
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                myCallback.onCallback(true);
            }
        });
        builder.setNegativeButton("Oops", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                myCallback.onCallback(false);
            }
        });
        builder.show();
    }

    // Register a location anchor
    public void registerLocation(final Context context, final String areaMap, final String locationInput, final String collectionNamePath, final PointF coordinatesInput) {
        // Input-Conditionals Check
        if (!collectionNamePath.equals("") && !locationInput.equals("") && !collectionNamePath.equals("Access Points") && !collectionNamePath.equals("Miscellaneous")) { // Access Points is protected
            // Continue
            // Check if location is already registered
            db.collection(PATH_TO_LOCATION_LISTS).document(areaMap).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    boolean anchorExists = false;
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            Map<String, PointF> firestoreMap = (HashMap<String, PointF>) document.getData().get(collectionNamePath);
                            if (firestoreMap != null) {
                                Set<String> locationsStored = firestoreMap.keySet();
                                for (String location : locationsStored) {
                                    if (location.equals(locationInput)) {
                                        anchorExists = true;
                                    }
                                }
                            }
                        }
                    }
                    // anchorExists = false means either the LocationList doesn't exist or the location was not stored before
                    if (coordinatesInput == null || anchorExists) {
                        // setting te warning msg
                        String msgHolder;
                        if (anchorExists && coordinatesInput == null) {
                            msgHolder = "Are you sure you wish to overwrite the existing LocationAnchor: "
                                    + locationInput + " and register this Anchor without Map Coordinates?";
                        } else if (anchorExists) {
                            msgHolder = "Are you sure you wish to overwrite the existing LocationAnchor: "
                                    + locationInput;
                        } else { // if (coordinatesInput == null)
                            msgHolder = "Are you sure you wish to register this Anchor without Map Coordinates?";
                        }
                        // confirm with user this is intentional
                        UserConfirmation(new UserBooleanCallback() {
                            @Override
                            public void onCallback(Boolean confirm) {
                                if (!confirm) {
                                    return;
                                } else {
                                    Log.d("LOGGED: ", "Firestore Collection Used: " + collectionNamePath);
                                    // Insert codes here
                                    if (systemsCheck(context)) {
                                        getAndShowScanResults(locationInput, collectionNamePath, coordinatesInput, areaMap);
                                    }
                                }
                            }
                        }, msgHolder);
                    } else { // coordinates != null
                        if (systemsCheck(context)) {
                            getAndShowScanResults(locationInput, collectionNamePath, coordinatesInput, areaMap);
                        }
                    }
                }
            });
        } else { // error in input somewhere
            Toast.makeText(context, "Invalid inputs", Toast.LENGTH_SHORT).show();
        }
    }

}
