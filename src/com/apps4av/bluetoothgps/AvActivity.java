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

import java.util.UUID;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;

import java.io.OutputStream;


import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;

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
    private TextView mTv;
    private TextView mTvGps;
    private Button mPairButton;

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
    }  

       
    /**
     * 
     */
    @Override
    public void onPause() {
        super.onPause();
        
        /*
         * Stop location manager
         */
        if(mLocationManager != null) {
            mLocationManager.removeUpdates((LocationListener)this);
            mLocationManager.removeNmeaListener(this);
            mLocationManager = null;
        }
        
        /*
         * Stop the task
         */
        if(mListenTask != null) {
            if(mListenTask.getStatus() == AsyncTask.Status.RUNNING) {
                mListenTask.cancel(true);
            }
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
    
    /**
     * 
     */
    @Override
    public void onResume() {
        super.onResume();

        /*
         * Get adapter. If cannot get now, it will be get from the task
         */
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
        
            if (!mBluetoothAdapter.isEnabled()) {
                mTv.setText(getString(R.string.Disabled));
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }  
        }
        else {
            mTv.setText(getString(R.string.Disabled));
        }

        /*
         * Listen NMEA GPS
         */
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,(LocationListener)this);
            mLocationManager.addNmeaListener(this);          
        }
        catch (Exception e) {
            mLocationManager = null;
            mTv.setText(getString(R.string.Disabled));
        } 
        
        /*
         * Start BT task
         */
        mListenTask = new BluetoothProcess();
        mListenTask.execute();
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

    
    /**
     * 
     * @author zkhan
     *
     */
    private class BluetoothProcess extends AsyncTask<Void, String, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            
            
            while (true) {

                try {
                    Thread.sleep(100);
                } 
                catch (Exception e) {
                }
                
                /*
                 * This should handle the case where initially BT was disabled
                 */
                if (mBluetoothAdapter == null) {
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    mTv.setText(getString(R.string.Disabled));
                    continue;
                }

                
                /*
                 * Start listening with server socket
                 */
                if(null == mServerSocket) {
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
                else {
                    
                    /*
                     * Got a connection?
                     */
                    if(null == mBtSocket) {
                        try {
                            mBtSocket = mServerSocket.accept();
                            /*
                             * Get output stream
                             */
                            mStream = mBtSocket.getOutputStream();
                        } 
                        catch (Exception e) {
                        }
                    }
                    if(null != mBtSocket) {
                        publishProgress(getString(R.string.Connected));
                    }
                }
            }
        }        

        @Override
        protected void onPostExecute(Void params) {             
        }

        @Override
        protected void onPreExecute() {
        }
        
        @Override
        protected void onProgressUpdate(String... val) {
            /*
             * Touch views only in UI thread
             */
            mTv.setText(val[0]);            
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
        mTvGps.setText(getString(R.string.GPSL));
        if(mBtSocket != null) {

            /*
             * Write to BT
             */
            int wrote = write(nmea.getBytes());
            if(wrote < 0) {
                mTv.setText(getString(R.string.NotConnected));
                try {
                    mBtSocket.close();
                } catch (Exception e) {
                }
                mBtSocket = null;
            }
        }
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
