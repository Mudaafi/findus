package com.example.findus;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CalibrationActivity extends MainNavigationDrawer {
    // -- View Variables

    ModifiedImageView mapView; // Map
    boolean keyboardOpen = false; // To hide and show keyboard // For revealing overlapping dropdown menu
    boolean dropdownStoreOpen = false; // To hide and show storeInput dropdown(For UI purposes)
    private final static String CALIBRATION_PIN_NAME = "Calibration Pin"; // For naming the pin to be stored together with location
    private String currentLocation;
    private Button btnChoose, btnUpload;
    private Uri filePath;
    private final int PICK_IMAGE_REQUEST = 71;
    // -- OnCreate (i.e. main)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calibration_main);
        final AutoCompleteTextView inputAreaMap = findViewById(R.id.inputAreaMap);
        refreshMapList(inputAreaMap);
        // -- Map Codes

        mapView = (ModifiedImageView) findViewById(R.id.calibration_map);
        mapView.setImage(ImageSource.resource(R.drawable.floorplan_com1_l2_ver1));
        // For placing pin (singular)
        mapView.setOnImageEventListener(new ModifiedImageView.OnImageEventListener() {
            @Override
            public void onReady() {}

            @Override
            public void onImageLoaded() {
                mapView.setMinimumScaleType(ModifiedImageView.SCALE_TYPE_CENTER_CROP);
                mapView.setScaleAndCenter(1.2f, mapView.getCenter());

                Log.d("LOGGED", "Calibration/onImageLoaded - getScale() / maxScale: " + String.valueOf(mapView.getScale()) + " / " + String.valueOf(mapView.getMaxScale()));
                Log.d("LOGGED", "Calibration/onImageLoaded - center: " + String.valueOf(mapView.getCenter()));
                Log.d("LOGGED", "Calibration/onImageLoaded - height: " + String.valueOf(mapView.getSHeight()));
                Log.d("LOGGED", "Calibration/onImageLoaded - width: " + String.valueOf(mapView.getSWidth()));

                mapView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        // ToggleButton to check if pin-placement is intended (default: true)
                        ToggleButton toggleButton = findViewById(R.id.toggleButton);
                        if (isTap(event)){
                            // start_x and start_y are class variables declared in CoreFunctions
                            final PointF source_coord = mapView.viewToSourceCoord((float) start_x, (float) start_y);
                            EditText coordInput = findViewById(R.id.inputCoord);
                            coordInput.setText(String.valueOf(source_coord));
                            if (toggleButton.isChecked()) {
                                // Remove all visible pins
                                if (mapView.hasPins()) {mapView.removePins();}
                                // Place the to-be-registered pin
                                mapView.setPin(CALIBRATION_PIN_NAME,source_coord);
                            } else { // toggleButton is not checked
                                String areaMap = String.valueOf(((EditText) findViewById(R.id.inputAreaMap)).getText());
                                final String collectionNamePath = selectedStore;
                                // Everytime you tap it will pull from firestore
                                db.collection(PATH_TO_LOCATION_LISTS).document(areaMap).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                    @Override
                                    public void onSuccess(DocumentSnapshot document) {
                                        Map<String, Object> firestoreCoordMap = (HashMap<String, Object>) document.getData().get(collectionNamePath);
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
        final Button btnChoose = findViewById(R.id.choose_image);
        final Button btnUpload = findViewById(R.id.upload_image);
        final ToggleButton toggleButton = findViewById(R.id.toggleButton); // default: true
        final Button mainButton = findViewById(R.id.registerButton);
        ConstraintLayout currView = findViewById(R.id.calibration_main);
        final EditText inputLocation = findViewById(R.id.inputLocation);
        //final AutoCompleteTextView inputAreaMap = findViewById(R.id.inputAreaMap);//declared earlier
        final EditText inputCoords = findViewById(R.id.inputCoord);

        btnChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseImage();
            }
        });

        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadImage();
            }
        });

        mainButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mainButton.setFocusableInTouchMode(true); // to clear focus from other input fields
                mainButton.requestFocus();
                // Obtain values from input fields as variables
                final String collectionNamePath = selectedStore;
                final String locationName = String.valueOf(inputLocation.getText());
                final String areaMap = String.valueOf(inputAreaMap.getText());
                final PointF coordinatesInput = mapView.getPinCoords(CALIBRATION_PIN_NAME);

                if (locationName.equals("")|| !locationName.matches(".*[a-zA-Z]+.*") ||
                        locationName.contains(".") || locationName.contains("/")) {
                    Toast.makeText(CalibrationActivity.this, "Improper name for location detected", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (areaMap.equals("")|| !areaMap.matches(".*[a-zA-Z]+.*") ||
                        areaMap.contains(".") || areaMap.contains("/")) {
                    Toast.makeText(CalibrationActivity.this, "Improper name for areaMap detected", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (toggleButton.isChecked()) {
                    registerLocation(CalibrationActivity.this, areaMap, locationName, collectionNamePath, coordinatesInput);
                    refreshMapList(inputAreaMap);
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
                final String collectionNamePath = selectedStore;
                if (!isChecked) {
                    if (registeredAreaMaps.containsKey(areaMap)) {
                        getAndShowLocations(R.id.calibration_map, areaMap, collectionNamePath);
                        mainButton.setText(R.string.redefine_button_text);
                    } else {
                        Toast.makeText(CalibrationActivity.this, "Area Map Unregistered. Please try again.", Toast.LENGTH_SHORT).show();
                        toggleButton.setChecked(false);
                    }
                } else {
                    mainButton.setText(R.string.register_button_text);
                }
             }
        });

        // If Input Field for "Map Coordinates" is clicked, prompt user to click map
        inputCoords.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(CalibrationActivity.this, "Please tap the map to input coordinates", Toast.LENGTH_SHORT).show();
            }
        });

        // If an item is selected from the dropdown
        inputAreaMap.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //Toast.makeText(CalibrationActivity.this, inputAreaMap.getText().toString(), Toast.LENGTH_SHORT).show();
                getImage(CalibrationActivity.this, inputAreaMap.getText().toString(), mapView);
            }
        });
        // If Input Field for "AreaMap" is clicked again, hide the keyboard and show dropdown menu
        inputAreaMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!keyboardOpen) {
                    keyboardOpen = true;
                    dropdownStoreOpen = false;
                    inputAreaMap.dismissDropDown();
                } else {
                    inputAreaMap.showDropDown();
                    dropdownStoreOpen = true;
                    hideKeyboard(v);
                    keyboardOpen = false;
                }
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
                if (inputAreaMap.isFocused()) {
                    inputAreaMap.clearFocus();
                    if (!dropdownStoreOpen) {
                        inputAreaMap.showDropDown();
                        dropdownStoreOpen = true;
                    } else {
                        inputAreaMap.dismissDropDown();
                        dropdownStoreOpen = false;
                    }
                }
                if (inputCoords.isFocused()) {
                    inputCoords.clearFocus();
                }
            }
        });
    }

    private void chooseImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null )
        {
            filePath = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);
                mapView.setImage(ImageSource.bitmap(bitmap));
                AutoCompleteTextView inputAreaMap = findViewById(R.id.inputAreaMap);
                inputAreaMap.setText("");
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }


    private void uploadImage() {
        if(filePath != null)
        {
            final AutoCompleteTextView areaMapInput = findViewById(R.id.inputAreaMap);
            final String mapName = areaMapInput.getText().toString();
            // Validate input to contain at least one alphabet
            if (mapName.equals("")|| !mapName.matches(".*[a-zA-Z]+.*") ||
                    mapName.contains(".") || mapName.contains("/")) {
                Toast.makeText(CalibrationActivity.this, "Improper name detected", Toast.LENGTH_SHORT).show();
                return;
            }
            final ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setTitle("Uploading...");
            progressDialog.show();
            StorageReference ref = storageReference.child("images/"+ mapName);
            ref.putFile(filePath)
                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                            progressDialog.dismiss();
                            Toast.makeText(CalibrationActivity.this, "Uploaded", Toast.LENGTH_SHORT).show();
                            // Adding to database Miscellaneous Page
                            Map<String, Long> sourceDimen = new HashMap<String, Long>();
                            sourceDimen.put("height", (long) mapView.getSHeight());
                            sourceDimen.put("width", (long) mapView.getSWidth());
                            db.document(PATH_TO_AREAMAPS_LIST).update(mapName, sourceDimen);
                            refreshMapList(areaMapInput);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressDialog.dismiss();
                            Toast.makeText(CalibrationActivity.this, "Failed "+e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                            double progress = (100.0*taskSnapshot.getBytesTransferred()/taskSnapshot
                                    .getTotalByteCount());
                            progressDialog.setMessage("Uploaded "+(int)progress+"%");
                        }
                    });
        } else {
            Toast.makeText(CalibrationActivity.this, "No Image Detected", Toast.LENGTH_SHORT).show();
        }
    }
    // -- Other Functions (GUI Related)

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
        if (!registeredAreaMaps.containsKey(areaMap)) {
            Toast.makeText(CalibrationActivity.this, "Area Map not registered.", Toast.LENGTH_SHORT).show();
            return;
        }
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
