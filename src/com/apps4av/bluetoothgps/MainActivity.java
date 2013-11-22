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
import android.os.Bundle;

import java.io.OutputStream;


import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;

/**
 * 
 * @author zkhan
 *
 */
public class MainActivity extends Activity implements android.location.GpsStatus.NmeaListener, LocationListener {

    public BluetoothAdapter mBluetoothAdapter;
    private BluetoothServerSocket mServerSocket;
    private static final UUID MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket mBtSocket = null;
    private static OutputStream mStream = null;
    private Button mPairButton;
    private Button mListenButton;
    private Thread mListenThread = null;
    private LocationManager mLocationManager;
    private TextView mTv;

    /**
     * 
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LayoutInflater layoutInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = layoutInflater.inflate(R.layout.main, null);
        setContentView(view);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }   
        
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

        mListenButton = (Button)view.findViewById(R.id.main_button_listen);
        mListenButton.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                
                if(mListenButton.getText().equals(getString(R.string.Listen))) {
                    BluetoothServerSocket tmp = null;
                    try {
                        tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(getString(R.string.app_name), MY_UUID_SECURE);
                    } 
                    catch (Exception e) { 
                        return;
                    }
                    
                    mServerSocket = tmp;
                    mListenButton.setText(getString(R.string.Listening));
                    mListenThread = new BluetoothProcess();
                    mListenThread.start();                    
                }
                else {
                    mListenButton.setText(getString(R.string.Listen));
                    mListenThread.interrupt();
                }
            }
        });
        
        mTv = (TextView)view.findViewById(R.id.main_text);

    }  

    
    /**
     * 
     */
    @Override
    public void onPause() {
        super.onResume();
        if(mLocationManager != null) {
            mLocationManager.removeUpdates((LocationListener)this);
            mLocationManager.removeNmeaListener(this);
        }
    }
    
    /**
     * 
     */
    @Override
    public void onResume() {
        super.onResume();
        
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,(LocationListener)this);
            mLocationManager.addNmeaListener(this);          
        }
        catch (Exception e) {
            mLocationManager = null;
        }      
    }

    /**
     * 
     * @return
     */
    private int write(byte[] buffer) {
        int wrote = buffer.length;
        try {
            mStream.write(buffer, 0, buffer.length);
        } 
        catch(Exception e) {
            wrote = -1;
            mTv.setText(getString(R.string.NotConnected));
        }
        return wrote;
    }

    /**
     * 
     * @author zkhan
     *
     */
    private class BluetoothProcess extends Thread {

        @Override
        public void run() {
            
            /*
             * Now accept
             */
            while (true) {

                try {
                    mBtSocket = mServerSocket.accept();
                    mStream = mBtSocket.getOutputStream();
                } 
                catch (Exception e) {
                    break;
                }
                if (mBtSocket != null) {
                    try {
                       mServerSocket.close();
                    } 
                    catch (Exception e) {
                    }
                    break;
                }
                
            }
        }
    }

    /**
     * 
     */
    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        if(mBtSocket != null) {
            mTv.setText(getString(R.string.Connected));

            int wrote = write(nmea.getBytes());
            if(wrote < 0) {
                mListenButton.setText(getString(R.string.Listen));
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
