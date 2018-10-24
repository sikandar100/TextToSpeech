package com.example.dell.ttsfinal;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.PriorityQueue;

import static android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID;

public class MainActivity extends AppCompatActivity {

    private final int External_Storage_Write_Permission = 1;
    private final int External_Storage_Read_Permission = 2;
   // PrintWriter os;
    //BufferedReader is;
    TextToSpeech t1;
    String tempDestFile;
    String Language, temStr, keycode;
    String st;

    PriorityQueue<String> pq = new PriorityQueue<String>();


    TextView infoIp;
    ServerSocket httpServerSocket;
    //Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);



        infoIp = (TextView) findViewById(R.id.text1);

        infoIp.setText(getIpAddress() + ":"
                + HttpServerThread.HttpServerPORT + "\n");

        HttpServerThread httpServerThread = new HttpServerThread();
        httpServerThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();


        if (httpServerSocket != null) {
            try {
                httpServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();

                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += "SiteLocalAddress: "
                                + inetAddress.getHostAddress() + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }


    private class HttpServerThread extends Thread {

        static final int HttpServerPORT = 8888;

        @Override
        public void run() {
            Socket socket = null;

            try {
                httpServerSocket = new ServerSocket(HttpServerPORT);

                while (true) {
                    socket = httpServerSocket.accept();

                    HttpResponseThread httpResponseThread =
                            new HttpResponseThread(socket);
                    httpResponseThread.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }


    }

    private class HttpResponseThread extends Thread {

        Socket socket;

        HttpResponseThread(Socket sock) {
            this.socket = sock;
        }

        @Override
        public void run() {

            BufferedReader is;
            final PrintWriter os;
            String request;
            Log.d("chala", "run: ");

            try {
                    is = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    os = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);


                Log.d("OS", "check: " + os);
                request = is.readLine();

                //-------------------------------------------------------------------
                StringBuilder raw = new StringBuilder();
                raw.append("" + request);
                boolean isPost = request.startsWith("POST");
                int contentLength = 0;
                while (!(request = is.readLine()).equals("")) {
                    raw.append('\n' + request);
                    if (isPost) {
                        final String contentHeader = "Content-Length: ";
                        Log.d("contentH", "req=: " + request.startsWith(contentHeader));
                        if (request.startsWith(contentHeader)) {
                            contentLength = Integer.parseInt(request.substring(contentHeader.length()));
                        }
                    }
                }
                StringBuilder body = new StringBuilder();

                Log.d("ispost", "post=: " + isPost);
                if (isPost) {
                    int c;
                    for (int i = 0; i < contentLength; i++) {
                        c = is.read();
                        body.append((char) c);
                    }
                    Log.d("cccc", "cccc=: " + body);
                }
                raw.append(body.toString());


                os.write("HTTP/1.1 200 OK\r\n");
                os.write("Content-Type: audio/wav\r\n");
                os.write("\r\n");

                if (isPost) {
                    String temp = body.toString();
                    //String str2 = temp;
                    //String upToNCharacters = str2.substring(0, Math.min(str2.length(), 4));
                    String tem = temp.substring(4);
                    // if(upToNCharacters.equals("nam=")) {

                    String afterDecode = URLDecoder.decode(tem, "UTF-8");

                    try {

                        JSONObject obj = new JSONObject(afterDecode);
                        Language = obj.getString("lang");
                        temStr = obj.getString("text");
                        keycode = obj.getString("key");
                        Log.d("Kutta", "run: ");
                        pq.add(keycode);
                        //writeAudio();
                        //------------------Testing start----------------------------------

                        if(writeAudio()) {

                            t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                                @Override
                                public void onInit(int i) {
                                    if (i != TextToSpeech.ERROR) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                            t1.setLanguage(Locale.forLanguageTag(Language));
                                        } else {
                                            Locale locale = new Locale(Language);
                                            Locale.setDefault(locale);
                                            t1.setLanguage(locale);
                                        }


                                        HashMap<String, String> myHashRender = new HashMap();
                                        myHashRender.put(KEY_PARAM_UTTERANCE_ID, keycode);

                                        String exStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
                                        File appTmpPath = new File(exStoragePath + "/sounds/");
                                        boolean isDirectoryCreated = appTmpPath.mkdirs();
                                        String tempFilename = keycode + ".wav";
                                        tempDestFile = appTmpPath.getAbsolutePath() + File.separator + tempFilename;
                                        Log.d("bitch", "onInit: worked");
                                        t1.synthesizeToFile(temStr, myHashRender, tempDestFile);

                                        t1.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                                            @Override
                                            public void onUtteranceCompleted(String s) {
                                                if (readAudio()) {

                                                    String ex = Environment.getExternalStorageDirectory().getAbsolutePath();
                                                    File appTmpPath = new File(ex + "/sounds/");
                                                    String te = pq.remove() + ".wav";
                                                    Log.d("key", "key: " + te);
                                                    String AgainPath = appTmpPath.getAbsolutePath() + File.separator + te;

                                                    File file = new File(AgainPath);
                                                    FileInputStream fin = null;
                                                    try {

                                                        fin = new FileInputStream(file);
                                                        byte fileContent[] = new byte[(int) file.length()];
                                                        fin.read(fileContent);
                                                        Log.d("Path", "Path =  " + AgainPath);
                                                        Log.d("fileContent", "bytes = " + fileContent.length);
                                                        st = Base64.encodeToString(fileContent, 0);

                                                        os.write(st);
                                                        os.flush();
                                                        os.close();

                                                        clearfile(AgainPath);

                                                    } catch (FileNotFoundException e) {
                                                        Log.d("exception", "File not found" + e);
                                                    } catch (IOException ioe) {
                                                        Log.d("exception ", "Exception while reading file " + ioe);
                                                    } finally {
                                                        try {
                                                            if (fin != null) {
                                                                fin.close();
                                                            }

                                                        } catch (IOException ioe) {
                                                            Log.d("Exception", "Error while closing stream: " + ioe);
                                                        }
                                                    }
                                                }

                                                if (pq.isEmpty()) {
                                                    //os.flush();
                                                    //os.close();
                                                    try {
                                                        socket.close();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }

                                            }
                                        });
                                    }

                                }

                            });



                        }

                        //-----------------Testing End-------------------------------------

                        //textToSpeech(Language,temStr,keycode,os);

                    } catch (Throwable t) {
                        Log.d("Throw", "Throwable t = " + t);
                    }

                } else {
                    os.write("<form method=\"post\">");
                    os.write("<textarea name=\"nam\"></textarea>");
                    os.write("<input type=\"submit\" />");
                    os.write("</form>");
                }

               /* os.flush();
                os.close();
                socket.close(); */

            } catch (Exception e) {
                e.printStackTrace();
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));

            }





        }

    }


    public void clearfile(String pathfile) {

        File fdelete = new File(pathfile);
        if (fdelete.exists()) {
            if (fdelete.delete()) {
                System.out.println("file Deleted :" + pathfile);
            } else {
                System.out.println("file not Deleted :");
            }
        }
        Log.d("deleted", "clearfile: worked?");

    }


    public boolean writeAudio() {
        if (askPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, External_Storage_Write_Permission)) {
            //textToSpeech(Language, temStr, keycode);
            return true;
        } else {
            Toast.makeText(getApplicationContext(), "Write Permission Is denied", Toast.LENGTH_SHORT).show();
        }

        return false;
    }

    public boolean readAudio() {
        if (askPermission(Manifest.permission.READ_EXTERNAL_STORAGE, External_Storage_Read_Permission)) {
            //Toast.makeText(getApplicationContext(),"Read Permission Is given finally",Toast.LENGTH_SHORT).show();
            return true;
        } else {
            Toast.makeText(getApplicationContext(), "Read Permission Is denied", Toast.LENGTH_SHORT).show();
        }
        return false;
    }


    private boolean askPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case External_Storage_Write_Permission:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    writeAudio();
                } else {
                    Toast.makeText(this, "Write Permission Denied", Toast.LENGTH_SHORT).show();
                }
            case External_Storage_Read_Permission:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    readAudio();
                } else {
                    Toast.makeText(this, "Read Permission Denied", Toast.LENGTH_SHORT).show();
                }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}
