package com.adrian.freeleaf;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spanned;
import android.view.*;
import android.widget.*;
import com.adrian.freeleaf.Utils.DiscoveryService;
import com.adrian.freeleaf.Utils.TransferService;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

public class MainActivity extends Activity {

    private SharedPreferences prefs;

    private TextView textOnOff, textIPAddress, textName, textTransfer;
    private Switch switchDiscovery;
    private Button buttonName, buttonStop, buttonExit;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        textOnOff = (TextView)findViewById(R.id.textOnOff);
        textIPAddress = (TextView)findViewById(R.id.textIPAddress);
        textName = (TextView)findViewById(R.id.textName);
        textTransfer = (TextView)findViewById(R.id.textTransfer);
        buttonName = (Button)findViewById(R.id.buttonName);
        buttonStop = (Button)findViewById(R.id.buttonStop);
        buttonExit = (Button)findViewById(R.id.buttonExit);

        final Boolean serviceRunning = getDiscoveryServiceRunning();
        if(!serviceRunning && prefs.getBoolean("auto_discovery", true)) {
            Intent intent = new Intent(this, DiscoveryService.class);
            startService(intent);
        }

        Intent intent = new Intent(this, TransferService.class);
        startService(intent);

        textOnOff.setText(serviceRunning ? "ON" : "OFF");
        textIPAddress.setText(getWifiIPAddress());
        textName.setText(prefs.getString("discovery_name", "Unknown"));

        buttonName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final View content = getLayoutInflater().inflate(R.layout.dialog_name_layout, null);
                final EditText text = (EditText)content.findViewById(R.id.textName);
                text.setText(prefs.getString("discovery_name", "Unknown"));

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setView(content)
                        .setTitle("Change your name")
                        .setPositiveButton("Change", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                final String newName = text.getText().toString();
                                textName.setText(newName);

                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("discovery_name", newName);
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

        buttonExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage(getString(R.string.detail5))
                        .setTitle(getString(R.string.header5))
                        .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Intent intent = new Intent(MainActivity.this, DiscoveryService.class);
                                stopService(intent);
                                intent = new Intent(MainActivity.this, TransferService.class);
                                stopService(intent);
                                finish();
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

        IntentFilter filter = new IntentFilter();
        filter.addAction(DiscoveryService.BROADCAST_ACTION);
        filter.addAction(TransferService.BROADCAST_ACTION);
        registerReceiver(receiver, filter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        switchDiscovery = (Switch)menu.findItem(R.id.action_switch).getActionView().findViewById(R.id.switchDiscovery);
        switchDiscovery.setChecked(prefs.getBoolean("auto_discovery", true));
        switchDiscovery.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Intent intent = new Intent(MainActivity.this, DiscoveryService.class);
                if (b) startService(intent);
                else stopService(intent);

                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("auto_discovery", b);
                editor.commit();
            }
        });

        return true;
    }

    private Boolean getDiscoveryServiceRunning() {
        ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (DiscoveryService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
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

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(DiscoveryService.BROADCAST_ACTION)) {
                Bundle extra = intent.getExtras();
                Boolean active = extra.getBoolean("enabled");
                textOnOff.setText(active ? "ON" : "OFF");
            } else if(action.equals(TransferService.BROADCAST_ACTION)) {
                Bundle extra = intent.getExtras();
                Boolean active = extra.getBoolean("active");
                if(active) {
                    String message1 = extra.getString("message1");
                    String message2 = extra.getString("message2");

                    Spanned span = Html.fromHtml(message1 + "<br><b>File name:</b> " + message2);
                    textTransfer.setText(span);
                } else {
                    textTransfer.setText(getString(R.string.detail4));
                }
            }
        }
    };
}
