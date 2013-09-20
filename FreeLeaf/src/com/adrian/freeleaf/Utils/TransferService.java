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

    public static String BROADCAST_ACTION = "com.adrian.freeleaf.transfer";

    private final Integer PORT = 8000;
    private ServerSocket serverSocket;
    private Boolean forceClose;

    @Override
    public void onCreate() {
        super.onCreate();
        forceClose = false;
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

        forceClose = true;
        try { serverSocket.close(); }
        catch (IOException e) { e.printStackTrace(); }
        transferThread.interrupt();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    Thread transferThread = new Thread() {
        @Override
        public void run() {

            Integer nextIsFile = 0;
            String name = null, path = null;
            long size = 0;

            byte[] buffer = new byte[8192];
            int bytesRead;

            while(!this.isInterrupted() && serverSocket != null && !serverSocket.isClosed()) {
                if(forceClose) break;
                try {
                    Socket client = serverSocket.accept();
                    InputStream iStream = client.getInputStream();
                    OutputStream oStream = client.getOutputStream();

                    client.setTcpNoDelay(true);
                    client.setSendBufferSize(8192);
                    client.setReceiveBufferSize(8192);

                    if(nextIsFile == 1 && size != 0) {
                        nextIsFile = 0;

                        File file = new File(path, name);
                        FileOutputStream fileStream = new FileOutputStream(file);

                        Intent intent = new Intent(BROADCAST_ACTION);
                        intent.putExtra("active", true);
                        intent.putExtra("message1", "Receiving file from " + client.getInetAddress().getHostAddress());
                        intent.putExtra("message2", file.getName());
                        sendBroadcast(intent);

                        while((bytesRead = iStream.read(buffer, 0, buffer.length)) > 0) {
                            if(forceClose) break;
                            fileStream.write(buffer, 0, bytesRead);
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
                            oStream.write(EncodingUtils.getBytes(response, "UTF-8"));
                            oStream.close();
                        } else if(cmds[0].equals("up")) {
                            if(cmds[1].equals(
                                    Environment.getExternalStorageDirectory().getAbsolutePath())) {
                                response = Environment.getExternalStorageDirectory().getAbsolutePath();
                            } else {
                                File file = new File(cmds[1]);
                                response = file.getParent();
                            }
                            oStream.write(EncodingUtils.getBytes(response, "UTF-8"));
                            oStream.close();
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
                            oStream.write(EncodingUtils.getBytes(response, "UTF-8"));
                            oStream.close();
                        } else if(cmds[0].equals("send")) {
                            name = cmds[1];
                            path = cmds[2];
                            size = Integer.parseInt(cmds[3]);

                            nextIsFile = 1;
                            oStream.write(EncodingUtils.getBytes(response, "UTF-8"));
                            oStream.close();
                        } else if(cmds[0].equals("delete")) {
                            File file = new File(cmds[1]);
                            DeleteRecursive(file);
                        } else if(cmds[0].equals("receive")) {
                            if(nextIsFile == 2) {
                                nextIsFile = 0;

                                File file = new File(path);
                                FileInputStream fis = new FileInputStream(file);

                                Intent intent = new Intent(BROADCAST_ACTION);
                                intent.putExtra("active", true);
                                intent.putExtra("message1", "Sending file to " + client.getInetAddress().getHostAddress());
                                intent.putExtra("message2", file.getName());
                                sendBroadcast(intent);

                                while((bytesRead = fis.read(buffer, 0, buffer.length)) > 0) {
                                    if(forceClose) break;
                                    oStream.write(buffer, 0, bytesRead);
                                }
                                oStream.flush();

                                intent.putExtra("active", false);
                                sendBroadcast(intent);

                                path = null;
                                size = 0;

                            } else {
                                path = cmds[1];

                                File file = new File(path);
                                size = file.length();

                                response = String.valueOf(size);
                                nextIsFile = 2;
                            }
                        } else if(cmds[0].equals("stream")) {
                            final String pp = cmds[1];
                            final OutputStream os = oStream;
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    File file = new File(pp);
                                    FileInputStream fis = null;
                                    try {
                                        fis = new FileInputStream(file);
                                        int xxx = 0;
                                        byte[] butter = new byte[1024];
                                        while((xxx = fis.read(butter, 0, butter.length)) > 0) {
                                            //if(forceClose) break;
                                            os.write(butter, 0, xxx);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                    }

                                }
                            }).start();
                        }

                        if(nextIsFile == 0) {
                            //oStream.write(EncodingUtils.getBytes(response, "UTF-8"));
                        }
                        //oStream.flush();

                        //client.close();
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
