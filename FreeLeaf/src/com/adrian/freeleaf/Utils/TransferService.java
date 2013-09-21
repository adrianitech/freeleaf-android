package com.adrian.freeleaf.Utils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.IBinder;
import org.apache.http.util.EncodingUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TransferService extends Service {

    public static String BROADCAST_ACTION = "com.adrian.freeleaf.transfer";

    private final Integer PORT = 8000;
    private ServerSocket serverSocket;
    private Boolean forceClose;
    WifiManager.WifiLock lock;

    @Override
    public void onCreate() {
        super.onCreate();
        forceClose = false;
        try {
            serverSocket = new ServerSocket(PORT);
            listenThread.start();

            WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
            lock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "TransferWifiLock");
            if(!lock.isHeld()) lock.acquire();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        forceClose = true;
        try { serverSocket.close(); }
        catch (IOException e) { e.printStackTrace(); }
        listenThread.interrupt();

        if(lock != null && lock.isHeld()) {
            lock.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class SendThread extends Thread {
        Socket client;
        File file;

        public SendThread(Socket s, File f) {
            client = s;
            file = f;

            Intent intent = new Intent(TransferService.BROADCAST_ACTION);
            intent.putExtra("id", this.getId());
            intent.putExtra("message1", "Sending file to " + client.getInetAddress().getHostAddress());
            intent.putExtra("message2", file.getName());
            intent.putExtra("active", true);
            sendBroadcast(intent);
        }

        @Override
        public void run() {
            try {
                OutputStream oStream = client.getOutputStream();
                FileInputStream fis = new FileInputStream(file);

                int bytesRead;
                byte[] buffer = new byte[8192];

                while((bytesRead = fis.read(buffer, 0, buffer.length)) > 0) {
                    if(forceClose) break;
                    oStream.write(buffer, 0, bytesRead);
                }

                oStream.flush();
                oStream.close();
                client.close();
                fis.close();

            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                Intent intent = new Intent(TransferService.BROADCAST_ACTION);
                intent.putExtra("id", this.getId());
                intent.putExtra("active", false);
                sendBroadcast(intent);
            }
        }
    };

    private class ReceiveThread extends Thread {
        Socket client;
        File file;

        public ReceiveThread(Socket s, File f) {
            client = s;
            file = f;

            Intent intent = new Intent(TransferService.BROADCAST_ACTION);
            intent.putExtra("id", this.getId());
            intent.putExtra("message1", "Receiving file from " + client.getInetAddress().getHostAddress());
            intent.putExtra("message2", file.getName());
            intent.putExtra("active", true);
            sendBroadcast(intent);
        }

        @Override
        public void run() {
            try {
                InputStream iStream = client.getInputStream();
                FileOutputStream fileStream = new FileOutputStream(file);

                int bytesRead;
                byte[] buffer = new byte[8192];

                while((bytesRead = iStream.read(buffer, 0, buffer.length)) > 0) {
                    if(forceClose) break;
                    fileStream.write(buffer, 0, bytesRead);
                }

                fileStream.flush();
                fileStream.close();
                iStream.close();

            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                Intent intent = new Intent(TransferService.BROADCAST_ACTION);
                intent.putExtra("id", this.getId());
                intent.putExtra("active", false);
                sendBroadcast(intent);
            }
        }
    };

    Thread listenThread = new Thread() {
        @Override
        public void run() {

            int bytesRead;
            Boolean nextIsFile = false;
            byte[] buffer = new byte[8192];
            String name = null, path = null;

            while(!this.isInterrupted() && serverSocket != null && !serverSocket.isClosed()) {
                if(forceClose) break;
                try {
                    Socket client = serverSocket.accept();
                    InputStream iStream = client.getInputStream();
                    OutputStream oStream = client.getOutputStream();

                    client.setTcpNoDelay(true);
                    client.setSendBufferSize(8192);
                    client.setReceiveBufferSize(8192);

                    if(nextIsFile) {
                        nextIsFile = false;
                        File file = new File(path, name);
                        ReceiveThread receiveThread = new ReceiveThread(client, file);
                        receiveThread.start();
                        continue;
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
                            if(cmds.length >= 2) {
                                File dir = new File(cmds[1]);
                                File[] files = dir.listFiles();

                                if(files != null) {
                                    JSONArray jsonArray = new JSONArray();

                                    for(File f : files) {
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
                            }
                        } else if(cmds[0].equals("send")) {
                            name = cmds[1];
                            path = cmds[2];
                            nextIsFile = true;
                        } else if(cmds[0].equals("delete")) {
                            File file = new File(cmds[1]);
                            DeleteRecursive(file);
                        } else if(cmds[0].equals("mkdir")) {
                            File file = new File(cmds[1], cmds[2]);
                            file.mkdir();
                        } else if(cmds[0].equals("rename")) {
                            File oldFile = new File(cmds[1]);
                            File newFile = new File(oldFile.getParent(), cmds[2]);
                            oldFile.renameTo(newFile);
                        } else if(cmds[0].equals("receive")) {
                            File file = new File(cmds[1]);
                            SendThread sendThread = new SendThread(client, file);
                            sendThread.start();
                            continue;
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

    private void DeleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                DeleteRecursive(child);
        fileOrDirectory.delete();
    }
}
