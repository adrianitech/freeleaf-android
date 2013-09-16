package com.adrian.freeleaf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.*;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.view.*;
import android.widget.*;
import com.adrian.freeleaf.Utils.DiscoveryThread;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private DiscoveryThread discoveryThread;
    private SharedPreferences prefs;

    private TextView textOnOff, textIPAddress, textName;
    private Switch switchDiscovery;
    private Button buttonName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        textOnOff = (TextView)findViewById(R.id.textOnOff);
        textIPAddress = (TextView)findViewById(R.id.textIPAddress);
        textName = (TextView)findViewById(R.id.textName);
        buttonName = (Button)findViewById(R.id.buttonName);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        discoveryThread = new DiscoveryThread(this);
        discoveryThread.setRefreshRate(3000);
        discoveryThread.setIsActive(prefs.getBoolean("auto_discovery", true));
        discoveryThread.setUsername(prefs.getString("discovery_name", "Unknown"));
        discoveryThread.start();

        textOnOff.setText(discoveryThread.getIsActive() ? "ON" : "OFF");
        textIPAddress.setText(getWifiIPAddress());
        textName.setText(discoveryThread.getUsername());

        buttonName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final View content = getLayoutInflater().inflate(R.layout.dialog_name_layout, null);
                final EditText textName = (EditText)content.findViewById(R.id.textName);
                textName.setText(discoveryThread.getUsername());

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setView(content)
                        .setTitle("Change your name")
                        .setPositiveButton("Change", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("discovery_name", textName.getText().toString());
                                editor.commit();
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                            }
                        });

                Dialog dialog = builder.create();
                dialog.show();
            }
        });


        /*IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(rec, ifilter);
        rec.onReceive(this, batteryStatus);

        File path = Environment.getExternalStorageDirectory();
        stat = new StatFs(path.getPath());*/
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_exit:
                System.exit(0);
                return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private String getWifiIPAddress() {
        WifiManager wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try { ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress(); }
        catch (UnknownHostException ex) { ipAddressString = "Unknown"; }

        return ipAddressString;
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
        if(s.equals("discovery_name")) {
            final String username = sharedPreferences.getString("discovery_name", "Unknown");
            discoveryThread.setUsername(username);
            textName.setText(username);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        switchDiscovery = (Switch)menu.findItem(R.id.action_switch).getActionView().findViewById(R.id.switchDiscovery);
        switchDiscovery.setChecked(discoveryThread.getIsActive());
        switchDiscovery.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                textOnOff.setText(b ? "ON" : "OFF");
                discoveryThread.setIsActive(b);

                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("auto_discovery", b);
                editor.commit();
            }
        });

        return true;
    }
}
