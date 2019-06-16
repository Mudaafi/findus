package com.example.findus;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.lang.reflect.Array;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
    private static final String PATHX = "Testing";
    private static final String ACTUAL_PATH = "Access Points";
    WifiManager wifiManager;
    List<ScanResult> wifiList;
    StringBuilder test = new StringBuilder(); // Holder to hold the strings to display
    TextView resultsDisplay; // Declaring the display
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    String testString = "";
    Map<String, Map<String, Long>> locationList = new HashMap<String, Map<String, Long>>();
    private static final String CASE_CALIBRATE = "Calibrator"; // Not Needed
    private static final String CASE_LOCALIZER = "Localizer";
    private String m_Text = "lel";
    private String standardIncomplete = "INCOMPLETE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        resultsDisplay = findViewById(R.id.resultsDisplay); // Declaring the display

        Button rescanButton = findViewById(R.id.rescanButton);
        rescanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Map<String, Long> localBssidMap = new HashMap<String, Long>();
                if (!wifiManager.isWifiEnabled()) {
                    Toast.makeText(MainActivity.this, "Please Enable WiFi", Toast.LENGTH_SHORT).show();
                } else if (!isLocationEnabled()) {
                    Toast.makeText(MainActivity.this, "Please Enable Location Services", Toast.LENGTH_SHORT).show();
                    // API > 28 requires explicit permission for Location Services
                } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                            PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
                    // OnPermissionResults will wait to launch getAndScanResults
                } else {
                    getUserLocation(new UserStringCallback() { // Get User Input for Location Variable
                        @Override
                        public void onCallback(String location) {
                            Log.d("LOGGED: ", "Jumped Async problem(?). Location Value now: " + m_Text);
                            getAndShowScanResults(location);
                        }
                    });
                }
            }
        });

        Button testButton = findViewById(R.id.testButton);

        testButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                /* Testing Function "queryFirestore"
                String testAP = "02:00:00:00:01:00";
                Map<String,Long> testMap = new HashMap<String, Long>();
                testMap.put(testAP, -50);
                testMap.put("testthis2", 9);
                testMap.put("testthis3", 9);
                queryFirestore(testMap);
                */
                localizationAlgorithm();
            }
        });
    }

    // Localization Algorithm
    public void localizationAlgorithm() {
        int location; // TO-DO
        // Get a map of BSSID -> RSSI of Current Location
        Map<String, Long> localBssidMap = getAndShowScanResults(CASE_LOCALIZER);

        // Pulling from Firestore an Array of classes containing possible locations and the BSSID Hashmaps
        Map<String, Map<String, Long>> locationArray = queryFirestore(localBssidMap);

        // Start of Copypasta
        double d = 0;
        double minimum = 999999;
        /*int*/ String finalLocation = "123";
        String finalBssid = "";
        String testBssid = "";

        for(String currentLocation : locationArray.keySet()) {
            d = 0;
            for (String currentBssid : localBssidMap.keySet()) {
                if (locationArray.get(currentLocation).containsKey(currentBssid)) {
                    d += ((localBssidMap.get(currentBssid) - locationArray.get(currentLocation).get(currentBssid)) * (localBssidMap.get(currentBssid) - locationArray.get(currentLocation).get(currentBssid)));
                    testBssid = currentBssid;
                }
            }
            d = Math.sqrt(d);
            if (d < minimum) {
                minimum = d;
                finalLocation = currentLocation;
                finalBssid = testBssid;
            }
        }
        // End of Copypasta

        StringBuilder holder = new StringBuilder();
        holder.append("Minimum Value: ");
        holder.append(minimum);
        holder.append(System.getProperty("line.separator"));
        holder.append("Final Location: ");
        holder.append(finalLocation);
        holder.append(System.getProperty("line.separator"));
        holder.append("Final BSSID: ");
        holder.append(finalBssid);
        resultsDisplay.setText(holder);
    }

    // Querying Function
    public Map<String, Map<String, Long>> queryFirestore(Map<String, Long> localBssidMap) {
        locationList = new HashMap<String, Map<String, Long>>();
        for(final String currentBssid : localBssidMap.keySet()) {
            /* onCompleteListener Method
            db.collection(PATHX).document(currentBssid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) { // exception handler
                            Map<String, Long> bssidMap = new HashMap<String, Long>();
                            Map<String, Long> firestoreMap = ((HashMap<String, Long>) document.getData().get("AnchorRefs"));
                            for (String currentLocation : firestoreMap.keySet()) {
                                if (locationList.get(currentLocation) == null) {
                                    // If 2 Async Calls use this if-clause, overwriting errors will occur
                                    bssidMap = new HashMap<String, Long>();
                                    bssidMap.put(currentBssid, firestoreMap.get(currentLocation));
                                    locationList.put(currentLocation, bssidMap);
                                    Log.d("LOGGED: ", "No map found at " + currentLocation + " inserting new map, " + bssidMap.toString());
                                    Log.d("Logged: ", "LocationList now: " + locationList.toString());
                                } else {
                                    Log.d("LOGGED: ", "Map found: " + bssidMap.toString());
                                    locationList.get(currentLocation).put(currentBssid, firestoreMap.get(currentLocation));
                                    Log.d("LOGGED: ", "Updated Map: " + bssidMap.toString());
                                }
                            }
                        }
                    }
                    resultsDisplay.setText(locationList.toString()); // Live Async update
                }
            }); */
            // Alternative solution uses a while-loop. May incur a lot of client-end load.
            // no OnCompleteListener as it is DANGEROUS, CODE WILL CONTINUE DUE TO ASYNCHRONOUS CALLS
            Task<DocumentSnapshot> task = db.collection(ACTUAL_PATH).document(currentBssid).get();
            while (!task.isComplete()) {
                try {
                    wait(1000);
                } catch (Exception e) {}
            } // Work around
            DocumentSnapshot document  =  task.getResult();
            if (document.exists()) { // exception handler
                Map<String, Long> bssidMap = new HashMap<String, Long>();
                Map<String, Long> firestoreMap = ((HashMap<String, Long>) document.getData().get("AnchorRefs"));
                for (String currentLocation : firestoreMap.keySet()) {
                    if (locationList.get(currentLocation) == null) {
                        bssidMap = new HashMap<String, Long>();
                        bssidMap.put(currentBssid, firestoreMap.get(currentLocation));
                        locationList.put(currentLocation, bssidMap);
                        Log.d("LOGGED: ", "No map found at " + currentLocation + " inserting new map, " + bssidMap.toString());
                        Log.d("Logged: ", "LocationList now: " + locationList.toString());
                    } else {
                        Log.d("LOGGED: ", "Map found: " + bssidMap.toString());
                        locationList.get(currentLocation).put(currentBssid, firestoreMap.get(currentLocation));
                        Log.d("LOGGED: ", "Updated Map: " + bssidMap.toString());
                    }
                }
            }
        }
        return locationList;
    }

    //This function returns a boolean indicating if Location Services is activated
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

    //This function executes every time the app checks for permissions
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        Map<String, Long> localBssidMap = new HashMap<String, Long>();
        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // Permission for Location Services
            getUserLocation(new UserStringCallback() { // Get User Input for Location Variable
                @Override
                public void onCallback(String location) {
                    Log.d("LOGGED: ", "Jumped Async problem(?). Location Value now: " + m_Text);
                    getAndShowScanResults(location);
                }
            });
        }
    }

    // This is a public interface used to bypass Asynchronous runnables,
    // specifically for user-input string in Function: getUserLocation
    public interface UserStringCallback {
        void onCallback(String value);
    }

    // This function obtains a user-input string for location. This string is used in calibration
    // to set up anchor points
    public void getUserLocation(final UserStringCallback myCallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Please label this location.");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        // NOTE: OnClickListener is an ASYNCHRONOUS CALL. VALUE MIGHT NOT BE READY FOR CODE EXECUTION
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                m_Text = input.getText().toString();
                myCallback.onCallback(m_Text);
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
    public Map<String, Long> getAndShowScanResults(String location) {
        test = new StringBuilder();
        Map<String, Long> localBssidMap = new HashMap<String, Long>();
        Map<String, Long> counterMap = new HashMap<String, Long>();
        Map<String, String> ssidMap = new HashMap<String, String>();
        if (location == "No Location Provided") {
            resultsDisplay.setText(location);
            return localBssidMap;
        }

        for (int i = 0; i < 4; i++) {
            wifiManager.startScan(); //Deprecated, not required?
            wifiList = wifiManager.getScanResults();
            for (final ScanResult scanResult : wifiList) {
                // Storing BSSID and RSSI for return
                if (counterMap.containsKey(scanResult.BSSID)) {
                    counterMap.put(scanResult.BSSID, counterMap.get(scanResult.BSSID) + (long) 1);
                    localBssidMap.put(scanResult.BSSID, (long) (scanResult.level + localBssidMap.get(scanResult.BSSID)));
                } else {
                    counterMap.put(scanResult.BSSID, (long) 1);
                    localBssidMap.put(scanResult.BSSID, (long) scanResult.level);
                    ssidMap.put(scanResult.BSSID, scanResult.SSID);
                }
            }
        }
        for (String key : localBssidMap.keySet()) {
            localBssidMap.put(key, localBssidMap.get(key)/counterMap.get(key));
            // Printing SSID and RSSI for Client View
            test.append("The RSSI of " + ssidMap.get(key) + " : " + key + " is " +
                    String.valueOf(localBssidMap.get(key)) + "\n");
        }

        if (location != CASE_LOCALIZER) {
            resultsDisplay.setText(test);
            pushCalibration(location, localBssidMap, ssidMap);
        } // Display List of APs
        TextView sizex = findViewById(R.id.sizex);
        sizex.setText("Number of Access Point(s): " +  String.valueOf(wifiList.size()));
        return localBssidMap;
    }

    public void pushCalibration(String location, Map<String, Long> localBssidMap,  Map<String, String> ssidMap) {
        //Firestore Test
        //The structure will be: A Collection of APs each containing a map of keys: Location,
        // values: RSSI

        Map<String,Object> accessPointDescription = new HashMap<>();
        //accessPointDescription.put("BSSID",scanResult.BSSID);
        //accessPointDescription.put("SSID", scanResult.SSID);

        // For Firestore Document MAP Access
        String locationPath = "AnchorRefs." + location;
        Map<String, Boolean> initialized = new HashMap<String, Boolean>();
        initialized.put("Initialized", true);

        for (String key : localBssidMap.keySet()) {
            accessPointDescription.put("BSSID",key);
            accessPointDescription.put("SSID", ssidMap.get(key));
            // Creates Document if non-existent
            db.collection(PATHX).document(key)
                    .set(initialized, SetOptions.merge());
            // Adds the RSSI value of the Access Point at that LOCATION using the Location as the Key
            db.collection(PATHX).document(key)
                    .update(
                            locationPath, localBssidMap.get(key)
                    ).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if (task.isSuccessful()) {
                        Log.d("LOGGED: ", "Success");
                    } else {
                        Log.w("LOGGED: ", "Failed.", task.getException());
                    }
                }
            });
        }

        // m_Text is obtained ASYNCHORNOUSLY. Might not be ready
        // Update: Implemented public interface callback method to remedy Asynchronity
        Log.d("LOGGED: ", "Checking m_Text value for possible Async race error: " + m_Text + " vs " + location);
    }

    /* TEST SEGMENTS, CAN REMOVE
    //Test Segment 02
    public void testWifiScanner(View view) { // Its a mess
        // Obtaining Permissions
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        } else {
            final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE); // No leak
            wifiManager.setWifiEnabled(true);
            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String results = ""; // Holder to hold the strings to display
                    TextView resultsDisplay = findViewById(R.id.resultsDisplay); // Declaring the display
                    List<ScanResult> wifiList = wifiManager.getScanResults();

                    for (ScanResult scanResult : wifiList) {
                        results += "The RSSI of " + scanResult.BSSID + " is " + String.valueOf(scanResult.level) + "\n";
                    }
                    resultsDisplay.setText(results);

                    TextView sizex = findViewById(R.id.sizex);
                    sizex.setText(String.valueOf(wifiList.size()));
                }
            }, filter);


            BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                        String results = ""; // Holder to hold the strings to display
                        TextView resultsDisplay = findViewById(R.id.resultsDisplay); // Declaring the display
                        List<ScanResult> wifiList = wifiManager.getScanResults();

                        for (ScanResult scanResult : wifiList) {
                            results += "The RSSI of " + scanResult.BSSID + " is " + String.valueOf(scanResult.level) + "\n";

                            //int level = WifiManager.calculateSignalLevel(scanResult.level, 5);
                            // results += "Level is " + String.valueOf(level) + " out of 5\n";
                            //Toast.makeText(this, "Level is " + String.valueOf(level) + " out of 5", Toast.LENGTH_SHORT).show();
                        }
                        resultsDisplay.setText(results);
                    }
                }
            };
            broadcastReceiver.onReceive();

            wifiManager.startScan();

            int rssix = wifiManager.getConnectionInfo().getRssi();
            //int levelx = WifiManager.calculateSignalLevel(rssix, 5);
            Toast.makeText(this, "RSSI is " + String.valueOf(rssix), Toast.LENGTH_SHORT).show();
        }
    }

    //Test Segment 01
    public void getConnectedWifiInfo(View view) { // Exploring the WifiManager Module by checking current connection
        // Added Permissions? IT WORKS
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }
        //Some tests
        String results = "";
        TextView resultsDisplay = (TextView) findViewById(R.id.resultsDisplay);
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        results = "netID: " + String.valueOf(wifiInfo.getNetworkId());
        results += "\nRSSI: " + String.valueOf(wifiInfo.getRssi());
        results += "\nIP Address: " + String.valueOf(wifiInfo.getIpAddress());
        results += "\nMac Address: " + String.valueOf(wifiInfo.getMacAddress());
        results += "\nSSID: " + String.valueOf(wifiInfo.getSSID());
        results += "\nHidden SSID: " + String.valueOf(wifiInfo.getHiddenSSID());
        resultsDisplay.setText(results);
    }*/
}