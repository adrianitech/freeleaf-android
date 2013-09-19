package com.adrian.freeleaf.Utils;

import android.app.Service;
import android.content.*;
import android.os.*;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.format.Formatter;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

public class DiscoveryService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static String DISCOVERY_MESSAGE = "com.adrian.freeleaf.DiscoveryService.Message";
    public static String DISCOVERY_ENABLED = "com.adrian.freeleaf.DiscoveryService.Enabled";

    private final Integer PORT = 8888;
    private final Integer INTERVAL = 3000;

    private SharedPreferences prefs;
    private Timer timer;

    private DatagramSocket datagramSocket;
    private InetAddress inetAddress;
    private String msgTemplate;

    private String username;
    private Integer battery;

    @Override
    public void onCreate() {
        super.onCreate();

        final String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        msgTemplate = "[\"" + id + "\", \"%s\", \"" + Build.MODEL + "\", \"%s%%\", \"%s\"]";

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        username = prefs.getString("discovery_name", "Unknown");

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(batteryReceiver, filter);
        batteryReceiver.onReceive(this, batteryStatus);

        try {
            datagramSocket = new DatagramSocket();
            datagramSocket.setBroadcast(true);
            inetAddress = InetAddress.getByName("255.255.255.255");
        } catch(Exception e) {
            e.printStackTrace();
        }

        timer = new Timer();
        timer.scheduleAtFixedRate(discoveryTask, 0, INTERVAL);

        Intent intent = new Intent(DISCOVERY_MESSAGE);
        intent.putExtra(DISCOVERY_ENABLED, true);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        timer.cancel();
        unregisterReceiver(batteryReceiver);

        datagramSocket.disconnect();
        datagramSocket.close();

        Intent intent = new Intent(DISCOVERY_MESSAGE);
        intent.putExtra(DISCOVERY_ENABLED, false);
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    TimerTask discoveryTask = new TimerTask() {
        @Override
        public void run() {
            final String msg = String.format(msgTemplate, username, battery, getFreeSpace());
            final byte[] buffer = msg.getBytes();
            DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, inetAddress, PORT);

            try { datagramSocket.send(datagramPacket); }
            catch (IOException e) { e.printStackTrace(); }
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if(s.equals("discovery_name")) {
            username = prefs.getString("discovery_name", "Unknown");
        }
    }

    BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            battery = (int)(100 * level / (float)scale);
        }
    };

    private String getFreeSpace() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long size = stat.getAvailableBlocks() * stat.getBlockSize();
        return Formatter.formatShortFileSize(this, size);
    }
}
