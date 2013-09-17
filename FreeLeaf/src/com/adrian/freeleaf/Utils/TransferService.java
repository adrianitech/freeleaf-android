package com.adrian.freeleaf.Utils;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.Log;
import org.apache.http.util.EncodingUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TransferService extends Service {

    private final Integer PORT = 8080;

    private ServerSocket serverSocket;

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            serverSocket = new ServerSocket(PORT);
            transferThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try { serverSocket.close(); }
        catch (IOException e) { e.printStackTrace(); }

        transferThread.interrupt();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private String getTimeToETA(long timeLeft) {
        String msgLeft;

        if(timeLeft < 60) {
            if(timeLeft == 1) {
                msgLeft = " second left";
            } else {
                msgLeft = " seconds left";
            }
        } else if(timeLeft < 3600) {
            timeLeft /= 60;
            if(timeLeft == 1) {
                msgLeft = " minute left";
            } else {
                msgLeft = " minutes left";
            }
        } else {
            timeLeft /= 3600;
            if(timeLeft == 1) {
                msgLeft = " hour left";
            } else {
                msgLeft = " hours left";
            }
        }

        return timeLeft + msgLeft;
    }

    Thread transferThread = new Thread() {
        @Override
        public void run() {

            Integer nextIsFile = 0;
            String name = null, path = null;
            long size = 0;

            byte[] buffer = new byte[16384];
            int bytesRead;

            while(!this.isInterrupted() && serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    InputStream iStream = client.getInputStream();
                    OutputStream oStream = client.getOutputStream();

                    if(nextIsFile == 1 && size != 0) {
                        nextIsFile = 0;

                        long bytesTotal = 0, lastRead = 0, lastUpdate = SystemClock.uptimeMillis();
                        String totalSize = Formatter.formatFileSize(TransferService.this, size);
                        int lastLeft = 0;

                        File file = new File(path, name);
                        FileOutputStream fileStream = new FileOutputStream(file);

                        Intent intent = new Intent("t");
                        intent.putExtra("active", true);
                        intent.putExtra("message", "Receiving file from " + client.getInetAddress().getHostAddress());
                        intent.putExtra("message1", file.getAbsolutePath());

                        while((bytesRead = iStream.read(buffer, 0, buffer.length)) > 0) {
                            fileStream.write(buffer, 0, bytesRead);
                            bytesTotal += bytesRead;
                            lastRead += bytesRead;

                            int diff = (int)(SystemClock.uptimeMillis() - lastUpdate);

                            if(diff >= 1000) {
                                lastUpdate = SystemClock.uptimeMillis();

                                String readSize = Formatter.formatFileSize(TransferService.this, bytesTotal);
                                String lastSize = Formatter.formatFileSize(TransferService.this, lastRead);

                                float t1 = (size - bytesTotal) / (float)lastRead;
                                float t2 = diff / (float)1000;
                                int timeLeft = (int)(t1 / t2) + 1;

                                if(lastLeft == 0) lastLeft = timeLeft;
                                if(timeLeft > lastLeft) timeLeft = lastLeft + 1;
                                lastLeft = timeLeft;

                                intent.putExtra("message2", readSize + "/" + totalSize + " (" + lastSize + "/s)");
                                intent.putExtra("message3", getTimeToETA(timeLeft));
                                sendBroadcast(intent);

                                lastRead = 0;
                            }
                        }
                        fileStream.close();

                        intent.putExtra("active", false);
                        sendBroadcast(intent);

                        name = null;
                        path = null;
                        size = 0;

                    } else {
                        bytesRead = iStream.read(buffer, 0, buffer.length);

                        String cmd = EncodingUtils.getString(buffer, 0, bytesRead, "UTF-8");
                        String[] cmds = cmd.split(":");

                        if(cmds.length == 0) continue;

                        String response = "";

                        if(cmds[0].equals("root")) {
                            response = Environment.getExternalStorageDirectory().getAbsolutePath();
                        } else if(cmds[0].equals("up")) {
                            if(cmds[1].equals(
                                    Environment.getExternalStorageDirectory().getAbsolutePath())) {
                                response = Environment.getExternalStorageDirectory().getAbsolutePath();
                            } else {
                                File file = new File(cmds[1]);
                                response = file.getParent();
                            }
                        } else if(cmds[0].equals("list")) {
                            File dir = new File(cmds[1]);
                            File[] files = dir.listFiles();

                            if(files != null) {
                                JSONArray jsonArray = new JSONArray();

                                for(File f : files) {
                                    if(f.isHidden()) continue;
                                    JSONObject object = new JSONObject();
                                    try {
                                        object.put("name", f.getName());
                                        object.put("path", f.getAbsolutePath());
                                        object.put("date", f.lastModified());
                                        object.put("size", f.isFile() ? f.length() : "");
                                        object.put("folder", f.isDirectory());
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                    jsonArray.put(object);
                                }

                                response = jsonArray.toString();
                            }
                        } else if(cmds[0].equals("send")) {
                            name = cmds[1];
                            path = cmds[2];
                            size = Integer.parseInt(cmds[3]);

                            nextIsFile = 1;
                        } else if(cmds[0].equals("receive")) {
                            if(nextIsFile == 2) {
                                nextIsFile = 0;

                                long bytesTotal = 0, lastRead = 0, lastUpdate = SystemClock.uptimeMillis();
                                String totalSize = Formatter.formatFileSize(TransferService.this, size);
                                int lastLeft = 0;

                                File file = new File(path);
                                FileInputStream fis = new FileInputStream(file);

                                Intent intent = new Intent("t");
                                intent.putExtra("active", true);
                                intent.putExtra("message", "Sending file to " + client.getInetAddress().getHostAddress());
                                intent.putExtra("message1", path);

                                while((bytesRead = fis.read(buffer, 0, buffer.length)) > 0) {
                                    oStream.write(buffer, 0, bytesRead);
                                    bytesTotal += bytesRead;
                                    lastRead += bytesRead;

                                    if(SystemClock.uptimeMillis() - lastUpdate >= 1000) {
                                        lastUpdate = SystemClock.uptimeMillis();

                                        String readSize = Formatter.formatFileSize(TransferService.this, bytesTotal);
                                        String lastSize = Formatter.formatFileSize(TransferService.this, lastRead);

                                        int timeLeft = (int)((size - bytesTotal) / lastRead) + 1;

                                        if(lastLeft == 0) lastLeft = timeLeft;
                                        if(timeLeft > lastLeft) timeLeft = lastLeft + 1;
                                        lastLeft = timeLeft;

                                        intent.putExtra("message2", readSize + "/" + totalSize + " (" + lastSize + "/s)");
                                        intent.putExtra("message3", getTimeToETA(timeLeft));
                                        sendBroadcast(intent);

                                        lastRead = 0;
                                    }
                                }

                                intent.putExtra("active", false);
                                sendBroadcast(intent);

                                path = null;
                                size = 0;

                            } else {
                                path = cmds[1];

                                File file = new File(path);
                                size = file.length();

                                response = size + "";



                                nextIsFile = 2;
                            }
                        }

                        oStream.write(EncodingUtils.getBytes(response, "UTF-8"));
                        oStream.flush();

                        client.close();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
}
