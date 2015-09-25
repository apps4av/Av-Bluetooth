/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.apps4av.bluetoothgps;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.io.OutputStream;
import java.util.UUID;

/**
 * 
 * @author zkhan
 *
 */
public class AvActivity extends Activity implements android.location.GpsStatus.NmeaListener, LocationListener {

    /*
     * Init everything. The UUID is unique for Avare
     */
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothServerSocket mServerSocket = null;
    private static final UUID MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket mBtSocket = null;
    private static OutputStream mStream = null;
    private BluetoothProcess mListenTask = null;
    private LocationManager mLocationManager = null;
    private boolean mRun;
    private TextView mTv;
    private TextView mTvGps;
    private Button mPairButton;

    /**
     * Stop the tasks and everything, then exit
     */
    private void stop() {
        /*
         * Stop the task
         */
        mRun = false;
        if(mListenTask != null) {
            if(mListenTask.getStatus() == AsyncTask.Status.RUNNING) {
                mListenTask.cancel(true);
            }
        }

        /*
         * Stop location manager
         */
        if(mLocationManager != null) {
            mLocationManager.removeUpdates((LocationListener)this);
            mLocationManager.removeNmeaListener(this);
            mLocationManager = null;
        }

        /*
         * Clear all sockets
         */
        if(null != mServerSocket) {
            try {
                mServerSocket.close();
            }
            catch (Exception e) {
            }
        }
        if(null != mBtSocket) {
            try {
                mBtSocket.close();
            }
            catch (Exception e) {
            }
        }
        mBtSocket = null;
        mServerSocket = null;
        mStream = null;
        mBluetoothAdapter = null;
        mListenTask = null;
    }


    /*
 * (non-Javadoc)
 * @see android.app.Activity#onBackPressed()
 */
    @Override
    public void onBackPressed() {

        /*
         * And may exit
         */
        AlertDialog exitDialog = new AlertDialog.Builder(AvActivity.this).create();
        exitDialog.setTitle(getString(R.string.Exit));
        exitDialog.setCanceledOnTouchOutside(true);
        exitDialog.setCancelable(true);
        exitDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.Yes), new DialogInterface.OnClickListener() {
            /* (non-Javadoc)
             * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
             */
            public void onClick(DialogInterface dialog, int which) {
                /*
                 * Go to background
                 */
                dialog.dismiss();
                stop();
                System.exit(0);
            }
        });
        exitDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.No), new DialogInterface.OnClickListener() {
            /* (non-Javadoc)
             * @see android.content.DialogInterface.OnClickListener#onClick(android.content.DialogInterface, int)
             */
            public void onClick(DialogInterface dialog, int which) {
                /*
                 * Go to background
                 */
                dialog.dismiss();
            }
        });

        exitDialog.show();

    }

    /**
     * 
     */
    @Override
    protected void onDestroy() {
        stop();
        super.onDestroy();
    }
    
    /**
     * 
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
         * Portrait and screen on
         */
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);            

        LayoutInflater layoutInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = layoutInflater.inflate(R.layout.main, null);
        setContentView(view);
        
        /*
         * Get a pairing button to start pairing initially.
         */
        mPairButton = (Button)view.findViewById(R.id.main_button_pair);
        mPairButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent discoverableIntent = new
                        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivity(discoverableIntent);
            }
        });

        mTv = (TextView)view.findViewById(R.id.main_text_state);
        mTvGps = (TextView)view.findViewById(R.id.main_text_gps);
        mTv.setText(getString(R.string.NotConnected));
        mTvGps.setText(getString(R.string.GPSNotConnected));
        mTv.setTextColor(Color.RED);
        mTvGps.setTextColor(Color.RED);

        
        /*
         * Start BT task
         */
        mRun = true;
        mListenTask = new BluetoothProcess();
        mListenTask.execute();
    }  

    /**
     * 
     * @param sock
     */
    private synchronized void setSocket(BluetoothSocket sock) {
        mBtSocket = sock;
    }

    /**
     * 
     */
    private synchronized BluetoothSocket getSocket() {
        return mBtSocket;
    }

    /**
     * 
     * @author zkhan
     *
     */
    private class BluetoothProcess extends AsyncTask<Void, String, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            
            while (mRun) {

                try {
                    Thread.sleep(100);
                } 
                catch (Exception e) {
                }
                                
                /*
                 * This should handle the GPS if it was off
                 */
                
                /*
                 * Listen NMEA GPS
                 */
                if(null == mLocationManager) {
                    publishProgress("GPS");
                }
                
                /*
                 * This should handle the case where initially BT was disabled
                 */
                if (null == mBluetoothAdapter) {
                    publishProgress("BT");
                }
                
                if(null == mLocationManager || null == mBluetoothAdapter) {
                    continue;
                }

                /*
                 * Start listening with server socket
                 */
                if(null != mServerSocket) {
                    
                    /*
                     * Got a connection?
                     */
                    if(null == getSocket()) {
                        try {
                            BluetoothSocket s = mServerSocket.accept();
                            /*
                             * Get output stream
                             */
                            mStream = s.getOutputStream();
                            setSocket(s); 
                        } 
                        catch (Exception e) {
                        }
                    }
                }
            }
            return null;
        }        

        @Override
        protected void onPostExecute(Void params) {             
        }

        @Override
        protected void onPreExecute() {
        }
        
        @Override
        protected void onProgressUpdate(String... val) {
            if(val[0].equals("GPS")) {
                mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
                try {
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,(LocationListener)AvActivity.this);
                    if(null != mLocationManager) {
                        mLocationManager.addNmeaListener(AvActivity.this);
                    }
                }
                catch (Exception e) {
                    mLocationManager = null;
                }
            }
            else if(val[0].equals("BT")) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();                
                if(null != mBluetoothAdapter && null == mServerSocket) {
                    /*
                     * Start listening
                     */
                    BluetoothServerSocket tmp = null;
                    try {
                        tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(getString(R.string.app_name), MY_UUID_SECURE);
                    } 
                    catch (Exception e) {
                    }
                    mServerSocket = tmp;
                }
            }
        }
    }

    /**
     * 
     */
    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        /*
         * Should be locked when receiving
         */
        mTvGps.setText(getString(R.string.GPSConnected));
        mTvGps.setTextColor(Color.GREEN);

        if(getSocket() != null) {
            mTv.setText(getString(R.string.Connected));
            mTv.setTextColor(Color.GREEN);

            /*
             * Write to BT
             */
            int wrote = write(nmea.getBytes());
            if(wrote <= 0) {
                mTv.setText(getString(R.string.NotConnected));
                mTv.setTextColor(Color.RED);
                try {
                    getSocket().close();
                } catch (Exception e) {
                }
                setSocket(null);
            }
            else {
                mTv.setText(getString(R.string.Connected));
                mTv.setTextColor(Color.GREEN);
            }
        }
        else {
            mTv.setText(getString(R.string.NotConnected));
            mTv.setTextColor(Color.RED);
        }
    }

    /**
     * 
     * @return
     */
    private int write(byte[] buffer) {
        /*
         * Simply write the NMEA output, no need to parse it.
         */
        int wrote = buffer.length;
        try {
            mStream.write(buffer, 0, buffer.length);
        } 
        catch(Exception e) {
            wrote = -1;
        }
        return wrote;
    }    

    @Override
    public void onLocationChanged(Location location) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
        
    }


    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
        
    }
}
