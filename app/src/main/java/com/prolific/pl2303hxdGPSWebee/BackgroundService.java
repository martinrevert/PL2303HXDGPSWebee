package com.prolific.pl2303hxdGPSWebee;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import tw.com.prolific.driver.pl2303.PL2303Driver;

public class BackgroundService extends Service {

    private static String TAG = "BackgroundService";

    private static final boolean SHOW_DEBUG = true;

    PL2303Driver mSerial;

    private PL2303Driver.BaudRate mBaudrate = PL2303Driver.BaudRate.B4800;
    private PL2303Driver.DataBits mDataBits = PL2303Driver.DataBits.D8;
    private PL2303Driver.Parity mParity = PL2303Driver.Parity.NONE;
    private PL2303Driver.StopBits mStopBits = PL2303Driver.StopBits.S1;
    private PL2303Driver.FlowControl mFlowControl = PL2303Driver.FlowControl.OFF;

    private static final String ACTION_USB_PERMISSION = "com.prolific.pl2303hxdGPSWebee.USB_PERMISSION";

    public BackgroundService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");


        Log.d(TAG, "Leave onCreate");



    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");

        // get service
        mSerial = new PL2303Driver((UsbManager) getSystemService(Context.USB_SERVICE),
                this, ACTION_USB_PERMISSION);

        // check USB host function.
        if (!mSerial.PL2303USBFeatureSupported()) {

            Toast.makeText(this, "No Support USB host API", Toast.LENGTH_SHORT)
                    .show();

            Log.d(TAG, "No Support USB host API");

            //mSerial = null;

        }


        openUsbSerial();
        readDataFromSerial();

        return 0;
        //return super.onStartCommand(intent, flags, startId);

    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void openUsbSerial() {
        Log.d(TAG, "Enter  openUsbSerial");

        if (null == mSerial)
            return;

        if (mSerial.isConnected()) {
            if (SHOW_DEBUG) {
                Log.d(TAG, "openUsbSerial : isConnected ");
            }

            mBaudrate = PL2303Driver.BaudRate.B4800;

            if (!mSerial.InitByBaudRate(mBaudrate, 700)) {
                if (!mSerial.PL2303Device_IsHasPermission()) {
                    Toast.makeText(this, "cannot open, maybe no permission", Toast.LENGTH_SHORT).show();
                }

                if (mSerial.PL2303Device_IsHasPermission() && (!mSerial.PL2303Device_IsSupportChip())) {
                    Toast.makeText(this, "cannot open, maybe this chip has no support, please use PL2303HXD / RA / EA chip.", Toast.LENGTH_SHORT).show();
                }
            } else {

                Toast.makeText(this, "connected : " + mBaudrate.toString() , Toast.LENGTH_SHORT).show();

            }
        }//isConnected

        Log.d(TAG, "Leave openUsbSerial");
    }//openUsbSerial


    private void readDataFromSerial() {

        int len;
        byte[] rbuf = new byte[4096];
        //byte[] rbuf = new byte[20];
        StringBuffer sbHex = new StringBuffer();

        Log.d(TAG, "Enter readDataFromSerial");


        while (mSerial.isConnected()) {

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
                    //String temp=Integer.toHexString(rbuf[j]&0x000000FF);
                    //Log.i(TAG, "str_rbuf["+j+"]="+temp);
                    //int decimal = Integer.parseInt(temp, 16);
                    //Log.i(TAG, "dec["+j+"]="+decimal);
                    //sbHex.append((char)decimal);
                    //sbHex.append(temp);
                    sbHex.append((char) (rbuf[j] & 0x000000FF));
                }

                Log.v(TAG, sbHex.toString());

                NMEA nmea = new NMEA();
                nmea.parse(sbHex.toString());
                Log.v(TAG, nmea.position.toString());


            } else {
                if (SHOW_DEBUG) {
                    Log.d(TAG, "read len : 0 ");
                }

                return;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "Leave readDataFromSerial");
        }//readDataFromSerial
    }



}
