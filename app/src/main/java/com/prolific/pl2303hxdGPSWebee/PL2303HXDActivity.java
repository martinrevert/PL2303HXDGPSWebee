package com.prolific.pl2303hxdGPSWebee;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.socket.client.IO;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;
import tw.com.prolific.driver.pl2303.PL2303Driver;

import android.app.Activity;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.google.nodejsmanager.nodejsmanager.ConnectionManager;
import com.example.google.nodejsmanager.nodejsmanager.SocketManager;

import org.json.JSONException;
import org.json.JSONObject;

import static com.prolific.pl2303hxdGPSWebee.Constants.API_KEY;
import static com.prolific.pl2303hxdGPSWebee.Constants.API_SECRET;
import static java.util.concurrent.Executors.newScheduledThreadPool;

public class PL2303HXDActivity extends Activity implements ConnectionManager.EventCallbackListener {

    private static final boolean SHOW_DEBUG = true;

    // Defines of Display Settings
    private static final int DISP_CHAR = 0;

    // Linefeed Code Settings
    //    private static final int LINEFEED_CODE_CR = 0;
    private static final int LINEFEED_CODE_CRLF = 1;
    private static final int LINEFEED_CODE_LF = 2;

    PL2303Driver mSerial;

    String TAG = "PL2303HXD_APLog";

    private Button btWrite;
    private EditText etWrite;

    private Button btRead;
    private EditText etRead;

    private Button btLoopBack;
    private ProgressBar pbLoopBack;
    private TextView tvLoopBack;

    private Button btGetSN;
    private TextView tvShowSN;

    private int mDisplayType = DISP_CHAR;
    private int mReadLinefeedCode = LINEFEED_CODE_LF;
    private int mWriteLinefeedCode = LINEFEED_CODE_LF;

    //BaudRate.B4800, DataBits.D8, StopBits.S1, Parity.NONE, FlowControl.RTSCTS
    private PL2303Driver.BaudRate mBaudrate = PL2303Driver.BaudRate.B4800;
    private PL2303Driver.DataBits mDataBits = PL2303Driver.DataBits.D8;
    private PL2303Driver.Parity mParity = PL2303Driver.Parity.NONE;
    private PL2303Driver.StopBits mStopBits = PL2303Driver.StopBits.S1;
    private PL2303Driver.FlowControl mFlowControl = PL2303Driver.FlowControl.OFF;


    int len; // lenght of buffer of bytes
    byte[] rbuf; // buufer itself
    private StringBuffer sbHex;

    private SocketManager socketManager;
    private int connectionIntents = 0;
    private ScheduledExecutorService schedulePingViot;

    private static final String ACTION_USB_PERMISSION = "com.prolific.pl2303hxdGPSWebee.USB_PERMISSION";

    private static final String NULL = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Enter onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pl2303_hxdsimple_test);

        // get service
        mSerial = new PL2303Driver((UsbManager) getSystemService(Context.USB_SERVICE),
                getApplicationContext(), ACTION_USB_PERMISSION);

        // check USB host function.
        if (!mSerial.PL2303USBFeatureSupported()) {

            Toast.makeText(PL2303HXDActivity.this, "No Support USB host API", Toast.LENGTH_SHORT)
                    .show();

            Log.d(TAG, "No Support USB host API");

            mSerial = null;

        }


        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                // this code will be executed after 2 seconds

                openUsbSerial();

            }
        }, 10000);


        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                readDataFromSerial();
            }
        }, 0, 5000);


        connectSocketViot();

        Log.d(TAG, "Leave onCreate");
    }//onCreate

    protected void onStop() {
        Log.d(TAG, "Enter onStop");
        super.onStop();
        if (socketManager.getSocket() != null)
            socketManager.getSocket().disconnect();
        Log.d(TAG, "Leave onStop");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Enter onDestroy");

        if (mSerial != null) {
            mSerial.end();
            mSerial = null;
        }

        super.onDestroy();
        Log.d(TAG, "Leave onDestroy");
    }

    public void onStart() {
        Log.d(TAG, "Enter onStart");
        super.onStart();
        Log.d(TAG, "Leave onStart");
    }

    public void onResume() {
        Log.d(TAG, "Enter onResume");
        super.onResume();
        String action = getIntent().getAction();
        Log.d(TAG, "onResume:" + action);

        //if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action))
        if (!mSerial.isConnected()) {
            if (SHOW_DEBUG) {
                openUsbSerial();
                Log.d(TAG, "New instance : " + mSerial);
            }

            if (!mSerial.enumerate()) {

                Toast.makeText(this, "no more devices found", Toast.LENGTH_SHORT).show();
                return;
            } else {
                Log.d(TAG, "onResume:enumerate succeeded!");
            }
        }//if isConnected
        Toast.makeText(this, "attached", Toast.LENGTH_SHORT).show();

        ConnectionManager.subscribeToListener(this);

        Log.d(TAG, "Leave onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        ConnectionManager.unSubscribeToListener();
    }

    private void openUsbSerial() {
        Log.d(TAG, "Enter  openUsbSerial");

        if (null == mSerial)
            return;

        if (mSerial.isConnected()) {
            if (SHOW_DEBUG) {
                Log.d(TAG, "openUsbSerial : isConnected ");
            }


            if (!mSerial.InitByBaudRate(mBaudrate, 700)) {
                if (!mSerial.PL2303Device_IsHasPermission()) {
                    Toast.makeText(this, "cannot open, maybe no permission", Toast.LENGTH_SHORT).show();
                }

                if (mSerial.PL2303Device_IsHasPermission() && (!mSerial.PL2303Device_IsSupportChip())) {
                    Toast.makeText(this, "cannot open, maybe this chip has no support, please use PL2303HXD / RA / EA chip.", Toast.LENGTH_SHORT).show();
                }
            } else {

               // Toast.makeText(PL2303HXDActivity.this, "connected : ", Toast.LENGTH_SHORT).show();

            }
        }//isConnected

        Log.d(TAG, "Leave openUsbSerial");
    }//openUsbSerial


    private void readDataFromSerial() {

        //  while (SHOW_DEBUG) {

        Thread MyThread = new Thread(new Runnable() {
            @Override
            public void run() {

                // while (SHOW_DEBUG) {

                //your background code

                rbuf = new byte[320];

                sbHex = new StringBuffer();

                Log.d(TAG, "Enter readDataFromSerial");

                if (null == mSerial)
                    return;

                if (!mSerial.isConnected())
                    return;

                len = mSerial.read(rbuf);

                if (len < 0) {
                    Log.d(TAG, "Fail to bulkTransfer(read data)");
                    return;
                }

                if (len > 0) {
                    if (SHOW_DEBUG) {
                        Log.d(TAG, "read len : " + len);
                    }
                    //rbuf[len] = 0;
                    for (int j = 0; j < len; j++) {
                        sbHex.append((char) (rbuf[j] & 0x000000FF));
                    }

                    NMEA nmea = new NMEA();
                    String nmeastring = sbHex.toString();
                    try {
                        nmeastring = nmeastring.substring(nmeastring.indexOf("$GPGGA"));
                        Log.v(TAG, nmeastring);
                        nmea.parse(nmeastring);
                        Log.v(TAG, nmea.position.toJson().toString());
                        socketManager.getSocket().emit("webee-hub-logger", nmea.position.toJson());
                    } catch (Exception s) {
                        s.printStackTrace();
                    }

                } else {
                    if (SHOW_DEBUG) {
                        Log.d(TAG, "read len : 0 ");
                    }

                }

            }
        });

        MyThread.start();

/*
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
*/
        Log.d(TAG, "Leave readDataFromSerial");
    }//readDataFromSerial


    private void writeDataToSerial() {

        Log.d(TAG, "Enter writeDataToSerial");

        if (null == mSerial)
            return;

        if (!mSerial.isConnected())
            return;

        String strWrite = etWrite.getText().toString();
		/*
        //strWrite="012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";
       // strWrite = changeLinefeedcode(strWrite);
         strWrite="012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";
         if (SHOW_DEBUG) {
            Log.d(TAG, "PL2303Driver Write(" + strWrite.length() + ") : " + strWrite);
        }
        int res = mSerial.write(strWrite.getBytes(), strWrite.length());
		if( res<0 ) {
			Log.d(TAG, "setup: fail to controlTransfer: "+ res);
			return;
		} 

		Toast.makeText(this, "Write length: "+strWrite.length()+" bytes", Toast.LENGTH_SHORT).show();  
		 */
        // test data: 600 byte
        //strWrite="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        if (SHOW_DEBUG) {
            Log.d(TAG, "PL2303Driver Write 2(" + strWrite.length() + ") : " + strWrite);
        }
        int res = mSerial.write(strWrite.getBytes(), strWrite.length());
        if (res < 0) {
            Log.d(TAG, "setup2: fail to controlTransfer: " + res);
            return;
        }

        Toast.makeText(this, "Write length: " + strWrite.length() + " bytes", Toast.LENGTH_SHORT).show();

        Log.d(TAG, "Leave writeDataToSerial");
    }//writeDataToSerial


    private void ShowPL2303HXD_SerialNmber() {


        Log.d(TAG, "Enter ShowPL2303HXD_SerialNmber");

        if (null == mSerial)
            return;

        if (!mSerial.isConnected())
            return;

        if (mSerial.PL2303Device_GetSerialNumber() != NULL) {
            tvShowSN.setText(mSerial.PL2303Device_GetSerialNumber());

        } else {
            tvShowSN.setText("No SN");

        }

        Log.d(TAG, "Leave ShowPL2303HXD_SerialNmber");
    }//ShowPL2303HXD_SerialNmber

    //------------------------------------------------------------------------------------------------//
    //--------------------------------------LoopBack function-----------------------------------------//
    //------------------------------------------------------------------------------------------------//
    private static final int START_NOTIFIER = 0x100;
    private static final int STOP_NOTIFIER = 0x101;
    private static final int PROG_NOTIFIER_SMALL = 0x102;
    private static final int PROG_NOTIFIER_LARGE = 0x103;
    private static final int ERROR_BAUDRATE_SETUP = 0x8000;
    private static final int ERROR_WRITE_DATA = 0x8001;
    private static final int ERROR_WRITE_LEN = 0x8002;
    private static final int ERROR_READ_DATA = 0x8003;
    private static final int ERROR_READ_LEN = 0x8004;
    private static final int ERROR_COMPARE_DATA = 0x8005;

    Handler myMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case START_NOTIFIER:
                    pbLoopBack.setProgress(0);
                    tvLoopBack.setText("LoopBack Test start...");
                    btWrite.setEnabled(false);
                    btRead.setEnabled(false);
                    break;
                case STOP_NOTIFIER:
                    pbLoopBack.setProgress(pbLoopBack.getMax());
                    tvLoopBack.setText("LoopBack Test successfully!");
                    btWrite.setEnabled(true);
                    btRead.setEnabled(true);
                    break;
                case PROG_NOTIFIER_SMALL:
                    pbLoopBack.incrementProgressBy(5);
                    break;
                case PROG_NOTIFIER_LARGE:
                    pbLoopBack.incrementProgressBy(10);
                    break;
                case ERROR_BAUDRATE_SETUP:
                    tvLoopBack.setText("Fail to setup:baudrate " + msg.arg1);
                    break;
                case ERROR_WRITE_DATA:
                    tvLoopBack.setText("Fail to write:" + msg.arg1);
                    break;
                case ERROR_WRITE_LEN:
                    tvLoopBack.setText("Fail to write len:" + msg.arg2 + ";" + msg.arg1);
                    break;
                case ERROR_READ_DATA:
                    tvLoopBack.setText("Fail to read:" + msg.arg1);
                    break;
                case ERROR_READ_LEN:
                    tvLoopBack.setText("Length(" + msg.arg2 + ") is wrong! " + msg.arg1);
                    break;
                case ERROR_COMPARE_DATA:
                    tvLoopBack.setText("wrong:" +
                            String.format("rbuf=%02X,byteArray1=%02X", msg.arg1, msg.arg2));
                    break;

            }
            super.handleMessage(msg);
        }//handleMessage
    };

    private void Send_Notifier_Message(int mmsg) {
        Message m = new Message();
        m.what = mmsg;
        myMessageHandler.sendMessage(m);
        Log.d(TAG, String.format("Msg index: %04x", mmsg));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void Send_ERROR_Message(int mmsg, int value1, int value2) {
        Message m = new Message();
        m.what = mmsg;
        m.arg1 = value1;
        m.arg2 = value2;
        myMessageHandler.sendMessage(m);
        Log.d(TAG, String.format("Msg index: %04x", mmsg));
    }

    private Runnable tLoop = new Runnable() {
        public void run() {

            int res = 0, len, i;
            Time t = new Time();
            byte[] rbuf = new byte[4096];
            final int mBRateValue[] = {4800, 9600, 19200, 115200};
            PL2303Driver.BaudRate mBRate[] = {PL2303Driver.BaudRate.B4800, PL2303Driver.BaudRate.B9600, PL2303Driver.BaudRate.B19200, PL2303Driver.BaudRate.B115200};

            if (null == mSerial)
                return;

            if (!mSerial.isConnected())
                return;

            t.setToNow();
            Random mRandom = new Random(t.toMillis(false));

            byte[] byteArray1 = new byte[256]; //test pattern-1
            mRandom.nextBytes(byteArray1);//fill buf with random bytes
            Send_Notifier_Message(START_NOTIFIER);

            for (int WhichBR = 0; WhichBR < mBRate.length; WhichBR++) {

                try {
                    res = mSerial.setup(mBRate[WhichBR], mDataBits, mStopBits, mParity, mFlowControl);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                if (res < 0) {
                    Send_Notifier_Message(START_NOTIFIER);
                    Send_ERROR_Message(ERROR_BAUDRATE_SETUP, mBRateValue[WhichBR], 0);
                    Log.d(TAG, "Fail to setup=" + res);
                    return;
                }
                Send_Notifier_Message(PROG_NOTIFIER_LARGE);

                for (int times = 0; times < 2; times++) {

                    len = mSerial.write(byteArray1, byteArray1.length);
                    if (len < 0) {
                        Send_ERROR_Message(ERROR_WRITE_DATA, mBRateValue[WhichBR], 0);
                        Log.d(TAG, "Fail to write=" + len);
                        return;
                    }

                    if (len != byteArray1.length) {
                        Send_ERROR_Message(ERROR_WRITE_LEN, mBRateValue[WhichBR], len);
                        return;
                    }
                    Send_Notifier_Message(PROG_NOTIFIER_SMALL);

                    len = mSerial.read(rbuf);
                    if (len < 0) {
                        Send_ERROR_Message(ERROR_READ_DATA, mBRateValue[WhichBR], 0);
                        return;
                    }
                    Log.d(TAG, "read length=" + len + ";byteArray1 length=" + byteArray1.length);

                    if (len != byteArray1.length) {
                        Send_ERROR_Message(ERROR_READ_LEN, mBRateValue[WhichBR], len);
                        return;
                    }
                    Send_Notifier_Message(PROG_NOTIFIER_SMALL);

                    for (i = 0; i < len; i++) {
                        if (rbuf[i] != byteArray1[i]) {
                            Send_ERROR_Message(ERROR_COMPARE_DATA, rbuf[i], byteArray1[i]);
                            Log.d(TAG, "Data is wrong at " +
                                    String.format("rbuf[%d]=%02X,byteArray1[%d]=%02X", i, rbuf[i], i, byteArray1[i]));
                            return;
                        }//if
                    }//for
                    Send_Notifier_Message(PROG_NOTIFIER_LARGE);

                }//for(times)

            }//for(WhichBR)

            try {
                res = mSerial.setup(mBaudrate, mDataBits, mStopBits, mParity, mFlowControl);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (res < 0) {
                Send_ERROR_Message(ERROR_BAUDRATE_SETUP, 0, 0);
                return;
            }
            Send_Notifier_Message(STOP_NOTIFIER);

        }//run()
    };//Runnable tLoop

    public class MyOnItemSelectedListener implements OnItemSelectedListener {
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

            if (null == mSerial)
                return;

            if (!mSerial.isConnected())
                return;

            int baudRate = 0;
            String newBaudRate;
            Toast.makeText(parent.getContext(), "newBaudRate is-" + parent.getItemAtPosition(position).toString(), Toast.LENGTH_LONG).show();
            newBaudRate = parent.getItemAtPosition(position).toString();

            try {
                baudRate = Integer.parseInt(newBaudRate);
            } catch (NumberFormatException e) {
                System.out.println(" parse int error!!  " + e);
            }

            switch (baudRate) {
                case 4800:
                    mBaudrate = PL2303Driver.BaudRate.B4800;
                    break;
                case 9600:
                    mBaudrate = PL2303Driver.BaudRate.B9600;
                    break;
                case 19200:
                    mBaudrate = PL2303Driver.BaudRate.B19200;
                    break;
                case 115200:
                    mBaudrate = PL2303Driver.BaudRate.B115200;
                    break;
                default:
                    mBaudrate = PL2303Driver.BaudRate.B9600;
                    break;
            }

            int res = 0;
            try {
                res = mSerial.setup(mBaudrate, mDataBits, mStopBits, mParity, mFlowControl);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (res < 0) {
                Log.d(TAG, "fail to setup");
            }
        }

        public void onNothingSelected(AdapterView<?> parent) {
            // Do nothing.
        }
    }//MyOnItemSelectedListener

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private Emitter.Listener onAuthenticated = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.d(TAG, "VIoT - onAuthenticated");
            socketManager.getSocket().emit("lb-ping");
            if (schedulePingViot == null) {
                schedulePingViot = newScheduledThreadPool(5);
            }
            schedulePingViot.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    try {
                        if (socketManager.getSocket() != null
                                && socketManager.getSocket().connected()) {
                            socketManager.getSocket().emit("lb-ping");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 15, TimeUnit.SECONDS);
        }
    };

    private Emitter.Listener onAndroidPongVIOT = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            if (socketManager.getSocket() != null) {
                Log.d(TAG, "VIoT - pong received ...");
            }
        }
    };

    private void connectSocketViot() {
        if (connectionIntents > 3) {
            showErrorMessage();
            return;
        }
        connectionIntents++;
        socketManager = new SocketManager(this);
        IO.Options opts = new IO.Options();
        opts.transports = new String[]{WebSocket.NAME};
        //opts.forceNew = true;
        socketManager.createSocket(Constants.VIOT_BASE_URL, opts);


        socketManager.getSocket().on("authenticated", onAuthenticated);
        socketManager.getSocket().on("lb-pong", onAndroidPongVIOT);


        if (socketManager.getSocket().connected()) {
            socketManager.getSocket().disconnect();
        }
        socketManager.getSocket().connect();
    }

    private void showErrorMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PL2303HXDActivity.this, "Socket disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    JSONObject getCredentials() {
        try {
            String path = "/api/connections/generateToken?api_key=%s&api_secret=%s";
            String[] APIs = new String[]{API_KEY, API_SECRET};
            String generateTokenApi = Constants.VIOT_BASE_URL + path;
            URL url = new URL(String.format(generateTokenApi, APIs[0],
                    APIs[1]));
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            StringBuilder json = new StringBuilder(1024);
            String tmp;
            while ((tmp = reader.readLine()) != null)
                json.append(tmp).append("\n");
            reader.close();
            return new JSONObject(json.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onEventCallbackReceived(String event, String socketIdentifier) {
        switch (event) {
            case ConnectionManager.EVENT_CONNECT: {
                Log.d(TAG, "VIoT - onConnectEvent");
                if (socketManager.getSocket() != null) {
                    JSONObject json = getCredentials();
                    try {
                        if (json != null) {
                            JSONObject requestJSONObject = new JSONObject();
                            requestJSONObject.put("id", json.getString("id"));
                            requestJSONObject.put("connectionId", json.getString("connectionId"));
                            requestJSONObject.put("agent", "hub");
                            requestJSONObject.put("uuid", "ATV329QGPSSensorPOC");
                            socketManager.getSocket().emit("webee-auth-strategy", requestJSONObject);
                            Log.i(TAG, "json: " + requestJSONObject);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
            }
            case ConnectionManager.EVENT_DISCONNECT: {
                Log.d(TAG, "VIoT - onDisconnectEvent");
                connectSocketViot();
                break;
            }
        }
    }

/*
    @Override
    public void onReadDataGPSSensor(JsonObject message) {
        Log.v(TAG, "message" + message);
        socketManager.getSocket().emit("webee-hub-logger", message);
    }

    */
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
