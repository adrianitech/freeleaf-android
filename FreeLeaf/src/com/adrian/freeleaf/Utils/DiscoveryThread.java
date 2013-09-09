package com.adrian.freeleaf.Utils;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class DiscoveryThread extends Thread {

    OnDataSentEventListener mListener;

    public interface OnDataSentEventListener {
        public void onDataSent();
    }

    public void setDataSentEventListener(OnDataSentEventListener eventListener) {
        mListener = eventListener;
    }

    private final int PORT = 8888;

    private int refreshRate = 3000;
    private Boolean isActive = true;
    private String username;

    private DatagramSocket datagramSocket;
    private InetAddress inetAddress;
    private String msgTemplate;

    public int getRefreshRate() {
        return refreshRate;
    }

    public void setRefreshRate(int refreshRate) {
        this.refreshRate = refreshRate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean refreshRate) {
        this.isActive = refreshRate;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public DiscoveryThread(Context context) {
        final String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        final String device = Build.MODEL;
        msgTemplate = "[\"" + id + "\", \"%s\", \"" + device + "\", \"%s%%\", \"%s\", \"%s%%\", \"%s\"]";

        try {
            datagramSocket = new DatagramSocket();
            datagramSocket.setBroadcast(true);
            inetAddress = InetAddress.getByName("255.255.255.255");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while(true) {
            if(isActive) {
                final String newMsg = String.format(msgTemplate, username, 0, 0, 0, refreshRate);
                final byte[] buffer = newMsg.getBytes();
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, inetAddress, PORT);
                try {
                    datagramSocket.send(datagramPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(mListener != null) mListener.onDataSent();
            }
            try {
                Thread.sleep(refreshRate);
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }
}
