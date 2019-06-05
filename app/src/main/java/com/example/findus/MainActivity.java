package com.example.findus;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 1;
    WifiManager wifiManager;
    List<ScanResult> wifiList;
    StringBuilder test = new StringBuilder(); // Holder to hold the strings to display
    TextView resultsDisplay; // Declaring the display


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        resultsDisplay = findViewById(R.id.resultsDisplay); // Declaring the display

        Button rescanButton = findViewById(R.id.rescanButton);
        rescanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
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
                    getAndShowScanResults();
                }
            }
        });
    }

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

    //Continue following checking permissions
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) { // Permission for Location Services
            getAndShowScanResults();
        }
    }

    //Scanning function
    public void getAndShowScanResults() {
        wifiManager.startScan(); //Deprecated, not required?
        wifiList = wifiManager.getScanResults();
        test = new StringBuilder();

        for (ScanResult scanResult : wifiList) {
            test.append("The RSSI of " + scanResult.SSID + " is " + String.valueOf(scanResult.level) + "\n");
        }
        resultsDisplay.setText(test);

        TextView sizex = findViewById(R.id.sizex);
        sizex.setText("Number of Access Point(s): " +  String.valueOf(wifiList.size()));

    }

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

            /*
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
            */
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
    }
}