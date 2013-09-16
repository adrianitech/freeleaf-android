package com.adrian.freeleaf;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.*;
import com.adrian.freeleaf.Utils.DiscoveryThread;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private DiscoveryThread discoveryThread;

    private TextView textOnOff;
    private Switch switchDiscovery;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        discoveryThread = new DiscoveryThread(this);

        final String refreshRate = prefs.getString("discovery_refresh", "3000");
        discoveryThread.setRefreshRate(Integer.parseInt(refreshRate));
        discoveryThread.setIsActive(prefs.getBoolean("auto_discovery", true));
        discoveryThread.setUsername(prefs.getString("discovery_name", "Unknown"));
        discoveryThread.start();

        textOnOff = (TextView)findViewById(R.id.textOnOff);





        /*IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(rec, ifilter);
        rec.onReceive(this, batteryStatus);


        File path = Environment.getExternalStorageDirectory();
        stat = new StatFs(path.getPath());


        wifiManager = (WifiManager)MainActivity.this.getSystemService(Context.WIFI_SERVICE);
        rssi = wifiManager.getConnectionInfo().getRssi();  */
    }


    /*BroadcastReceiver rec = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            batt = (int)(100 * level / (float)scale);
        }
    }; */

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if(s.equals("discovery_refresh")) {
            final String value = sharedPreferences.getString("discovery_refresh", "3000");
            discoveryThread.setRefreshRate(Integer.parseInt(value));
        } else if(s.equals("discovery_name")) {
            discoveryThread.setUsername(sharedPreferences.getString("discovery_name", "Unknown"));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        switchDiscovery = (Switch)menu.findItem(R.id.switchDiscovery).getActionView().findViewById(R.id.switchForActionBar);
        switchDiscovery.setChecked(discoveryThread.getIsActive());
        switchDiscovery.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                textOnOff.setText(b ? switchDiscovery.getTextOn() : switchDiscovery.getTextOff());
                discoveryThread.setIsActive(b);
            }
        });

        return true;
    }
}
