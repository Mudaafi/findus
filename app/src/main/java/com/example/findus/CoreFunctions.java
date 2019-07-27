package com.example.findus;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CoreFunctions extends AppCompatActivity {
    // ----- Class Variables
    // Constants
    public static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 1; // for systemCheck
    public static final String PATH_TO_COLLECTION_LIST = "Miscellaneous/CollectionList";
    public static final String PATH_TO_AREAMAPS_LIST = "Miscellaneous/AreaMapsList";
    public static final String PATH_TO_LOCATION_LISTS = "LocationLists";
    public static final String PATH_TO_LOCATION_TO_AREAMAPS_LIST = "Miscellaneous/LocationToAreaMaps";
    public static final List<String> FORBIDDEN_STORES = Arrays.asList("Miscellaneous", "LocationLists");
    public static final String CASE_LOCALIZER = "Localizer"; // case for getAndShowScanResults
    private static final String MAP_NAME_IN_FS_DOC = "AnchorRefs"; // for pushCalibration
    // Database Variables
    public FirebaseFirestore db = FirebaseFirestore.getInstance();
    public FirebaseStorage storage = FirebaseStorage.getInstance();
    public StorageReference storageReference = storage.getReference();
    public Map<String, Object> registeredAreaMaps = new HashMap<String, Object>();

    // String Variables
    public String dialogString = "lel"; // For Alert Dialog in getUserLocation
    public String selectedStore;
    // Other Variables
    List<ScanResult> wifiList; // For getAndScanResults' wifi scan
    Map<String, Map<String, Long>> locationList = new HashMap<String, Map<String, Long>>(); // For queryFirestore's asynchronous
    private Integer asyncCounter = 0; // For queryFirestore's asynchronous calls
    private boolean asyncBoolDone = false;

    int start_x = 0, start_y = 0, touchSlop = 5; // Pin placement

    // -- Core Functions
    //Returns a boolean indicating if Location Services is activated
    public boolean isLocationEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            //  API 28
            LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            return lm.isLocationEnabled();

        } else {
            // This is Deprecated in API 28
            int mode = Settings.Secure.getInt(getApplicationContext().getContentResolver(), Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF);
            return  (mode != Settings.Secure.LOCATION_MODE_OFF);

        }
    }

    //Checks if device has WiFi and Location Services enabled.
    public boolean systemsCheck(Context context) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(context, "Please Enable WiFi", Toast.LENGTH_SHORT).show();
        } else if (!isLocationEnabled()) {
            Toast.makeText(context, "Please Enable Location Services", Toast.LENGTH_SHORT).show();
            // API > 28 requires explicit permission for Location Services
        } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
            Toast.makeText(context, "User Permission yet to be obtained. Please try again.", Toast.LENGTH_SHORT).show();
        } else {
            return true;
        }
        return false; // Failed a criteria above
    }

    // This is a public interface used to bypass Asynchronous runnables,
    // specifically for user-input string in Function: getUserLocation
    public interface UserStringCallback {
        void onCallback(String value);
    }

    // Creates Alert Dialogue that obtains a user-input string for location. This was formerly used in calibration
    // to set up anchor points
    public void getUserLocation(final UserStringCallback myCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Please label this location.");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        // NOTE: OnClickListener is an ASYNCHRONOUS CALL.
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialogString = input.getText().toString();
                myCallback.onCallback(dialogString);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                myCallback.onCallback("No Location Provided");
            }
        });
        builder.show();
    }

    //Calibrating function
    public Map<String, Long> getAndShowScanResults(String location, @NonNull String collectionNamePath, PointF coordinates, String areaMap) {
        Map<String, Long> localBssidMap = new HashMap<String, Long>(); // The Map to return
        Map<String, Long> counterMap = new HashMap<String, Long>(); // for division purposes
        Map<String, String> ssidMap = new HashMap<String, String>(); // for pushing to Firestore
        if (location == "") {
            return localBssidMap;
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        // To reduce inconsistency, scan 4 times and sum the RSSI values obtained. (Division will be done after this loop)
        for (int i = 0; i < 4; i++) {
            wifiManager.startScan(); //Deprecated, not required?
            wifiList = wifiManager.getScanResults();
            for (final ScanResult scanResult : wifiList) {
                // Storing BSSID and RSSI for return
                if (counterMap.containsKey(scanResult.BSSID)) {
                    // Increment Counter
                    counterMap.put(scanResult.BSSID, counterMap.get(scanResult.BSSID) + (long) 1);
                    // Add to the total sum of RSSI values
                    localBssidMap.put(scanResult.BSSID, (long) (scanResult.level + localBssidMap.get(scanResult.BSSID)));
                } else {
                    // Set Counter to 1
                    counterMap.put(scanResult.BSSID, (long) 1);
                    // Set RSSI and SSID to key BSSID
                    localBssidMap.put(scanResult.BSSID, (long) scanResult.level);
                    ssidMap.put(scanResult.BSSID, scanResult.SSID);
                }
            }
        }
        // Averaging out the RSSI Values found by dividing based on the number of times the BSSID was found (since it's a reverse-strength scale)
        for (String key : localBssidMap.keySet()) {
            localBssidMap.put(key, localBssidMap.get(key)/counterMap.get(key)); // Overwrite
        }

        if (location != CASE_LOCALIZER) {
            pushCalibration(location, localBssidMap, ssidMap, collectionNamePath, coordinates, areaMap);
        }
        return localBssidMap;
    }

    private void pushCalibration(final String location, Map<String, Long> localBssidMap, Map<String, String> ssidMap, @NonNull final String collectionNamePath, final PointF coordinates, final String areaMap) {
        // Structure of Database will be: A Collection of APs each containing a map of keys -> Location; values -> RSSI
        // This reduces the number of document reads during queryFirestore
        Map<String,Object> accessPointDescription = new HashMap<>();

        // For Firestore Document MAP Access
        String locationPath = MAP_NAME_IN_FS_DOC +"." + location;
        // initialization is coded bc I'm scared ".update" for a preexisting map might not work if the document has not been created.
        Map<String, Boolean> initialized = new HashMap<String, Boolean>();
        initialized.put("Initialized", true);

        // Creates a Document with name: BSSID containing location -> RSSI
        for (String key : localBssidMap.keySet()) {
            accessPointDescription.put("BSSID",key);
            accessPointDescription.put("SSID", ssidMap.get(key));
            // Creates Document if non-existent
            db.collection(collectionNamePath).document(key)
                    .set(accessPointDescription, SetOptions.merge());
            // Adds the RSSI value of the Access Point(key) at that LOCATION (doc [location -> RSSI])
            db.collection(collectionNamePath).document(key)
                    .update(locationPath, localBssidMap.get(key))
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        Log.d("LOGGED", "@/CoreFunctions/pushCalibration/: Successfully stored BSSID -> RSSI values for '" + String.valueOf(location)
                                + "' in Collection: " + String.valueOf(collectionNamePath));
                    } else {
                        Log.w("LOGGED", "@/CoreFunctions/pushCalibration/: Failed to store BSSID -> RSSI values for '" + String.valueOf(location)
                                + "' in Collection: " + String.valueOf(collectionNamePath), task.getException());
                    }
                }
            });
        }

        // Append new location to Firestore's LocationList
        String pathToAreaSpecificLocationList = PATH_TO_LOCATION_LISTS + "/" + areaMap;
        // for Groupings in Firestore
        locationPath = collectionNamePath + "." + location;
        db.document(pathToAreaSpecificLocationList).update(locationPath, coordinates).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Log.d("LOGGED", "@/CoreFunctions/pushCalibration/: Successfully stored Location -> SourceCoordinates values for '" + String.valueOf(location)
                            + "' -> " + coordinates + " in Document: " + String.valueOf(areaMap));
                } else {
                    Log.d("LOGGED", "@/CoreFunctions/pushCalibration/: Failed to store Location -> SourceCoordinates values for '" + String.valueOf(location)
                            + "' -> " + coordinates + " in Document: " + String.valueOf(areaMap));
                }
            }
        });
        // Append new collections to Firestore's CollectionList
        db.document(PATH_TO_COLLECTION_LIST).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                Set<String> collectionList = documentSnapshot.getData().keySet();
                if (!collectionList.contains(collectionNamePath)) {
                    Map<String, Integer> holder = new HashMap<String, Integer>();
                    holder.put(collectionNamePath, 69);
                    db.document(PATH_TO_COLLECTION_LIST).set(holder, SetOptions.merge());
                }
            }
        });
    }

    // Localization Algorithm
    public String localizationAlgorithm(Map<String, Map<String, Long>> locationArray, Map<String, Long> localBssidMap) {
        double d = 0;
        int counter = 0;
        double minimum = 999999;
        String finalLocation = "No Viable Location Found";
        //String finalBssid = "";
        //String testBssid = "";

        for(String currentLocation : locationArray.keySet()) {
            d = 0;
            counter = 0;
            for (String currentBssid : localBssidMap.keySet()) {
                if (locationArray.get(currentLocation).containsKey(currentBssid)) {
                    counter += 1;
                    d += ((localBssidMap.get(currentBssid) - locationArray.get(currentLocation).get(currentBssid)) * (localBssidMap.get(currentBssid) - locationArray.get(currentLocation).get(currentBssid)));
                    //testBssid = currentBssid;
                }
            }
            if (counter != 0) {
                d /= counter;
                d = Math.sqrt(d);
                if (d < minimum) {
                    minimum = d;
                    finalLocation = currentLocation;
                   // finalBssid = testBssid;
                }
            }
        }
        Log.d("LOGGED", "@/CoreFunctions/localizationAlgorithm/:Test Complete");
        return finalLocation;
    }

    // Querying Function
    public void queryFirestore(final UserStringCallback myCallback, @NonNull String collectionNamePath) {
        locationList = new HashMap<String, Map<String, Long>>(); // resetting holder 'locationList'
        asyncCounter = 0; // resetting holder 'doneSignal'

        // Get a map of BSSID -> RSSI of Current Location
        final Map<String, Long> localBssidMap = getAndShowScanResults(CASE_LOCALIZER, collectionNamePath, null, null);
        // Pulling from Firestore an Array of classes containing possible locations and the BSSID Hashmaps
        for (final String currentBssid : localBssidMap.keySet()) {
            ++asyncCounter;
            // Asynchronous Tasks
            db.collection(collectionNamePath).document(currentBssid).get().addOnCompleteListener(this, new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) { // exception handler
                            Map<String, Long> bssidMap = new HashMap<String, Long>();
                            // firestoreMap structure: bssid -> (location -> RSSI) // Compare to locationList structure
                            Map<String, Long> firestoreMap = ((HashMap<String, Long>) document.getData().get(MAP_NAME_IN_FS_DOC ));
                            for (String currentLocation : firestoreMap.keySet()) {
                                if (locationList.get(currentLocation) == null) {
                                    // If 2 Async Calls use this if-clause, overwriting errors will occur
                                    bssidMap = new HashMap<String, Long>();
                                    // bssid -> RSSI
                                    bssidMap.put(currentBssid, firestoreMap.get(currentLocation));
                                    // locationList structure: location -> bssidMap (bssid -> RSSI)
                                    locationList.put(currentLocation, bssidMap);
                                    Log.d("LOGGED", "@/CoreFunctions/queryFirestore/: No map found at " + currentLocation + " inserting new map, " + bssidMap.toString());
                                    Log.d("LOGGED", "@/CoreFunctions/queryFirestore/: LocationList now: " + locationList.toString());
                                } else {
                                    Log.d("LOGGED", "@/CoreFunctions/queryFirestore/: Map found: " + bssidMap.toString());
                                    locationList.get(currentLocation).put(currentBssid, firestoreMap.get(currentLocation));
                                    Log.d("LOGGED", "@/CoreFunctions/queryFirestore/: Updated Map: " + bssidMap.toString());
                                }
                            }
                        }
                    }
                    --asyncCounter;
                    if (asyncCounter == 0) { //If this is  the last async task left
                        String result = localizationAlgorithm(locationList, localBssidMap);
                        myCallback.onCallback(result);
                    }
                }
            });
        }
    }

    public void updateCounters() {
        //TO-DO
        // Updates all the counters in the Firestore Console to reflect the number of items in each
        // Useful for a quick glance at total locations, bssid, etc. stored.
    }

    public void redefineLocation(final String nameFrom, final String nameTo, final String areaMap, final String storeInput, @Nullable final PointF coordinatesTo) {
        //TO-CHECK
        if (storeInput.equals("") || nameTo.equals("")) {return;}
        // Checks if a Location is registered
        final DocumentReference specificLocationList = db.collection(PATH_TO_LOCATION_LISTS).document(areaMap);
        specificLocationList.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (!task.isSuccessful()) {
                    Log.d("LOGGED", "@/CoreFunctions/redefineLocation/: Error accessing document at " + PATH_TO_LOCATION_LISTS + " / " + areaMap,
                            task.getException());
                } else {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        Map<String, Object> locationCoords = (HashMap<String, Object>) document.getData().get(storeInput);
                        Map<String, PointF> convertedLocationCoords = firestoreMapToCoordMap(locationCoords);
                        String locationPath;
                        // Changing a Location's Name
                        if (!nameFrom.equals(nameTo)) {
                            //perform name change
                            // For Access Points
                            db.collection(storeInput).get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                @Override
                                public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                                        Map<String, Long> firestoreMap = (HashMap<String, Long>) document.getData().get(MAP_NAME_IN_FS_DOC);
                                        db.collection(storeInput).document(document.getId()).update(MAP_NAME_IN_FS_DOC + "." + nameTo, firestoreMap.get(nameFrom));
                                        db.collection(storeInput).document(document.getId()).update(MAP_NAME_IN_FS_DOC + "." + nameFrom, FieldValue.delete());
                                    }
                                }
                            });

                            // for LocationList
                            PointF holder = convertedLocationCoords.get(nameFrom);
                            locationPath = storeInput + "." + nameFrom;
                            specificLocationList.update(locationPath, FieldValue.delete());
                            locationPath = storeInput + "." + nameTo;
                            specificLocationList.update(locationPath, holder);
                        }
                        // Changing a Location's Coordinates
                        if (coordinatesTo != null) {
                            PointF coordinatesFrom = convertedLocationCoords.get(nameFrom);
                            if (coordinatesFrom != null) {
                                int dX = Math.abs((int) coordinatesFrom.x - (int) coordinatesTo.x);
                                int dY = Math.abs((int) coordinatesFrom.y - (int) coordinatesTo.y);
                                Float touchSlop = 1f;
                                if (Math.sqrt(Math.pow(dX, 2) + Math.pow(dY, 2)) >= touchSlop) {
                                    // Change the Coordinates
                                    locationPath = storeInput + "." + nameTo;
                                    specificLocationList.update(nameTo, coordinatesTo);
                                }
                            } else {
                                Log.d("LOGGED", "@/CoreFunctions/redefineLocation/: " + nameFrom + " has no coordinates.");
                            }
                        }
                    }
                }
            }
        });
    }

    // Convert data pulled from Firestore to String -> PointF
    public Map<String, PointF> firestoreMapToCoordMap(Map<String, Object> firestoreMap) {
        Map<String, PointF> coordMap = new HashMap<String, PointF>();
        for (String point : firestoreMap.keySet()) {
            if (firestoreMap.get(point) != null) {
                double xd = ((HashMap<String, Double>) firestoreMap.get(point)).get("x");
                double yd = ((HashMap<String, Double>) firestoreMap.get(point)).get("y");
                float xf = (float)xd;
                float yf = (float)yd;
                PointF coordHolder = new PointF(xf, yf);
                coordMap.put(point, coordHolder);
            } else {
                coordMap.put(point, null);
            }
        }
        return coordMap;
    }

    // Function to obtain and show pins for all locations in storeInput
    public void getAndShowLocations(final Integer id, String areaMap, final String storeInput) {
        if (areaMap == null || storeInput == null) {return;}
        final ModifiedImageView mapView = (ModifiedImageView) findViewById(id);
        // Remove all visible pins
        if (mapView.hasPins()) {
            mapView.removePins();
        }
        db.document(PATH_TO_LOCATION_LISTS + "/" + areaMap).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (!task.isSuccessful()) {
                    Log.d("LOGGED","@/CoreFunctions/getAndShowLocations/: Could not find the corresponding location list" ,task.getException());
                } else {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        Map<String, Object> pinCoords = (HashMap<String, Object>) document.getData().get(storeInput);
                        Log.d("LOGGED", "@/CoreFunctions/getAndShowLocations/: " + String.valueOf(pinCoords));
                        if (pinCoords == null) {
                            return;
                        }

                        Map<String, PointF> convPinCoord = firestoreMapToCoordMap(pinCoords);
                        for (String point : convPinCoord.keySet()) {
                            if (pinCoords.get(point) != null) {
                                mapView.setPin(point, convPinCoord.get(point));
                            }
                        }
                    }
                }
            }
        });
    }

    public boolean isTap(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            start_x = (int) event.getX();
            start_y = (int) event.getY();
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            int endX = (int) event.getX();
            int endY = (int) event.getY();
            int dX = Math.abs(endX - start_x);
            int dY = Math.abs(endY - start_y);
            Log.d("LOGGED", "@/CoreFunctions/isTap/: Dist moved: " + String.valueOf(Math.sqrt(Math.pow(dX, 2) + Math.pow(dY, 2))));
            // If tap (dist move from finger-touch to finger-lift < touchslop)
            if (Math.sqrt(Math.pow(dX, 2) + Math.pow(dY, 2)) <= touchSlop) {
                return true;
            }
        }
        return false;
    }

    public void getUserInputString(final UserStringCallback myCallback, final Context context, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("User Input Required");
        builder.setMessage(msg);
        final AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("Set", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialogString = input.getText().toString();
                myCallback.onCallback(dialogString);
            }
        });
        builder.show();


        // Pull Data for Firestore as suggestions
        asyncBoolDone = false;
        db.document(PATH_TO_COLLECTION_LIST).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot document) {
                if (document.exists()) {
                    final Set<String> holder = document.getData().keySet();
                    final ArrayList<String> collectionList = new ArrayList<String>();
                    for (String collection : holder) {
                        if (!FORBIDDEN_STORES.contains(collection)) {
                            collectionList.add(collection);
                        }
                    }
                    ArrayAdapter<String> adaptedCollectionList = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, collectionList);
                    if (!asyncBoolDone) {
                        input.setAdapter(adaptedCollectionList);
                        input.setThreshold(1);
                    }
                }
            }
        });
    }

    // Pulls an image from Firebase given a name and uploads it onto a mapview
    public void getImage(final Context context, final String mapName, final ModifiedImageView mapView) {
        final ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("Retrieving...");
        progressDialog.show();

        final StorageReference ref = storageReference.child("images/"+ mapName);
        // Get the Source Dimensions First
        db.document(PATH_TO_AREAMAPS_LIST).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot document) {
                if (!document.exists()) {
                    Log.d("LOGGED", "CoreFunctions - Error accessing areaMapsList");
                    return;
                } else {
                    Map<String, Object> mapList = new HashMap<String, Object>();
                    mapList = document.getData();
                    final Map<String, Long> sourceDimen = (HashMap<String, Long>) mapList.get(mapName);

                    if (sourceDimen == null || sourceDimen.isEmpty()) {
                        Log.d("LOGGED", "CoreFunctions - NO DIMENSIONS FOUND");
                        return;
                    }

                    // Get The Image and Resize it
                    final long ONE_MEGABYTE = 1024 * 1024;
                    ref.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                        @Override
                        public void onSuccess(byte[] bytes) {
                            // Data for "images/island.jpg" is returns, use this as needed
                            Bitmap retrievedImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            Bitmap resizedImage = Bitmap.createScaledBitmap(retrievedImage,
                                    sourceDimen.get("width").intValue(),sourceDimen.get("height").intValue(), true);
                            mapView.setImage(ImageSource.bitmap(resizedImage));
                            progressDialog.dismiss();
                            Toast.makeText(context, "Retrieved", Toast.LENGTH_SHORT).show();
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            progressDialog.dismiss();
                            Toast.makeText(context, "Failed "+e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    })
                    ;
                }
            }
        });
    }

    // Access Firestore and pull a list of Collection names from a document
    public void refreshMapList(final AutoCompleteTextView inputAreaMap) {
        // db refers to Firestore Database which is declared in CoreFunctions.java
        Task<DocumentSnapshot> task = db.document(PATH_TO_AREAMAPS_LIST).get();
        task.addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    ArrayList<String> firestoreMaps = new ArrayList<String>();
                    registeredAreaMaps = task.getResult().getData();
                    for (String id : registeredAreaMaps.keySet()) {
                        firestoreMaps.add(id);
                    }
                    inputAreaMap.setThreshold(1);
                    ArrayAdapter<String> adaptedArray = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_dropdown_item, firestoreMaps);
                    inputAreaMap.setAdapter(adaptedArray);
                    Log.d("LOGGED: ", "List of AreaMaps Loaded");
                }
            }
        });
    }

    // get AreaMap from a Location Anchor
    /*
    public void yolo() {
        db.collection("Access Points").whereEqualTo("cd", 3).whereEqualTo()
    }*/

    // -- Code for Initializations

    public void initalizeLocationsMaps() {
        Log.d("LOGGED", "@/CoreFunctions/initializeLocations/: Initializing code");
        final String areaMap = "COM 1 Level 2, NUS";
        db.collection(PATH_TO_LOCATION_LISTS).document(areaMap).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot document) {
                if (document.exists()) {
                    Map<String, Object> firstMap = new HashMap<>();
                    firstMap = document.getData();
                    Map<String, Object> secondMap = new HashMap<>();
                    secondMap = (HashMap<String, Object>)firstMap.get("Access Points");
                    for (String key : secondMap.keySet()) {
                        db.document(PATH_TO_LOCATION_TO_AREAMAPS_LIST).update(key, areaMap);
                    }
                }
            }
        });
    }

    public Uri getFilePath(Integer id, String imageRefName) {
        Uri filePath = Uri.parse("android.resource://"+this.getPackageName()+"/drawable/" + id);
        return filePath;
        /**
        FirebaseStorage storage;
        storage = FirebaseStorage.getInstance();
        StorageReference storageReference = storage.getReference();
        StorageReference ref = storageReference.child("images/"+ imageRefName);
        ref.putFile(filePath); */
    }

    // Pulls from a Collection of BSSID Docs[location -> RSSI] the Locations and stores them in a
    // separate Document (areaMap) in the LocationLists Collection
    public void initializeLocationsCoords() {
        Log.d("LOGGED", "@/CoreFunctions/initializeLocations/: Initializing code");
        final String areaMap = "COM 1 Level 2, NUS";

        // Add new Collection to Firestore's CollectionList
        Map<String, Integer> holder = new HashMap<String, Integer>();
        holder.put(PATH_TO_LOCATION_LISTS, 69);
        db.document(PATH_TO_COLLECTION_LIST).set(holder, SetOptions.merge());

        // Add new areaMap to Firestore's AreaMapsList
        holder = new HashMap<String, Integer>();
        holder.put(areaMap, 69);
        db.document(PATH_TO_AREAMAPS_LIST).set(holder, SetOptions.merge());

        // Create Document if not yet created
        Map<String, PointF> initializerHolder = new HashMap<String, PointF>();
        PointF randomCoord = new PointF(6, 9);
        initializerHolder.put("Initialized with Code", randomCoord);
        db.collection(PATH_TO_LOCATION_LISTS).document(areaMap).set(initializerHolder, SetOptions.merge()).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d("LOGGED", "@/CoreFunctions/initializeLocations/: New Document Created: " + areaMap);

                db.collection("Access Points").get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        PointF coordsHolder = new PointF(5, 9);
                        Integer counter = 1; // for Log
                        Integer totalCounter = queryDocumentSnapshots.size(); // for Log
                        for (DocumentSnapshot document : queryDocumentSnapshots) {
                            Map<String, Long> firestoreMap = (HashMap<String, Long>) document.getData().get(MAP_NAME_IN_FS_DOC);
                            Set<String> firestoreLocations = firestoreMap.keySet();
                            Log.d("LOGGED", "@/CoreFunctions/initializeLocations/: " + String.valueOf(counter++) + " / " + String.valueOf(totalCounter) + " documents processed");
                            for (final String location : firestoreLocations) {
                                // Put the locations in a map to differentiate diff stores/collections
                                String locationPath = "Access Points." + location;
                                db.document(PATH_TO_LOCATION_LISTS + "/" + areaMap).update(locationPath, coordsHolder).addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> task) {
                                        if (task.isSuccessful()) {
                                            Log.d("LOGGED", "@/CoreFunctions/initializeLocations/: Successfully stored " + location);
                                        } else {
                                            Log.d("LOGGED", "@/CoreFunctions/initializeLocations/: ERROR");
                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }
        });
    }
}
