package com.example.findus;

import android.content.Context;
import android.graphics.PointF;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class ScannerActivity extends MainNavigationDrawer {
    // Class Variables
    Button scanButton;
    Switch bssidSwitch;
    TextView resultDisplay;
    StringBuilder resultsConcat;

    // -- OnCreate (i.e. main)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scanner_main);
        scanButton = findViewById(R.id.scanNow);
        bssidSwitch = findViewById(R.id.bssidSwitch);
        resultDisplay = findViewById(R.id.resultsDisplay);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (systemsCheck(ScannerActivity.this)) {
                    Map<String, AccessPoint> localBssidMap =
                            specialgetAndShowScanResults(CASE_LOCALIZER, "lel", new PointF(1,2), "lel");
                    resultsConcat = new StringBuilder();
                    for (AccessPoint AP : localBssidMap.values()) {
                        if (bssidSwitch.isChecked()) {
                            resultsConcat.append("SSID: " + AP.ssid + "| BSSID: " + AP.bssid + " --> RSSI: " + String.valueOf(AP.rssi));
                        } else {
                            resultsConcat.append("SSID: " + AP.ssid + " --> RSSI: " + String.valueOf(AP.rssi));
                        }
                    }
                    resultDisplay.setText(resultsConcat);
                }

            }
        });
        bssidSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (bssidSwitch.isChecked()) {
                    bssidSwitch.setText("BSSID Enabled ");
                } else {
                    bssidSwitch.setText("Show BSSIDs ");
                }
            }
        });

    }
    public class AccessPoint {
        String ssid;
        String bssid;
        Long rssi;
    }

    //Calibrating function
    public Map<String, AccessPoint> specialgetAndShowScanResults(String location, @NonNull String collectionNamePath, PointF coordinates, String areaMap) {
        Map<String, Long> localBssidMap = new HashMap<String, Long>(); // The Map to return
        Map<String, Long> counterMap = new HashMap<String, Long>(); // for division purposes
        Map<String, String> ssidMap = new HashMap<String, String>(); // for pushing to Firestore
        Map<String, AccessPoint> results = new HashMap<>();
        if (location == "") {
            return results;
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
                AccessPoint holder = new AccessPoint();
                holder.ssid = scanResult.SSID;
                holder.bssid = scanResult.BSSID;
                results.put(holder.bssid, holder);
            }
        }
        // Averaging out the RSSI Values found by dividing based on the number of times the BSSID was found (since it's a reverse-strength scale)
        for (String key : localBssidMap.keySet()) {
            results.get(key).rssi = localBssidMap.get(key)/counterMap.get(key);
        }
        /*
        if (location != CASE_LOCALIZER) {
            pushCalibration(location, localBssidMap, ssidMap, collectionNamePath, coordinates, areaMap);
        }*/
        return results;
    }

}
