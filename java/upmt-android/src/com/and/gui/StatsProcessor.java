/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.and.gui;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

import upmt.os.Shell;

import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

public class StatsProcessor {
	
	final private String DEV_FILE = "/proc/self/net/dev";

	static private int NUM_COUNTERS = 3;

	final private String WIFI_DEV = "eth0";
	final private String CELL_DEV = "rmnet0";
	final private String USB_DEV = "usb0";
	
	private int mSamplingInterval;
	
	private WifiManager mWifi;
	private TelephonyManager mCellular;
	private ConnectivityManager mCx;
	
	private Vector<StatCounter> mCounters;
	private Vector<TextView> mCounterViews;
	private Vector<TextView> mInfoViews;
	
	StatsProcessor(int sampling_interval,
				TelephonyManager cellular,
				WifiManager wifi,
				ConnectivityManager cx) {
		
		mSamplingInterval = sampling_interval;
		mCellular = cellular;
		mWifi = wifi;
		mCx = cx;
		mCounters = new Vector<StatCounter>();
		
		for (int i = 0; i < NUM_COUNTERS; ++i) {
			mCounters.addElement(new StatCounter("B"));
		}
		
	}

	public void reset() {
		
		for (int i=0; i < NUM_COUNTERS; ++i ) {
			mCounters.get(i).reset();
			if (mCounterViews != null) {
				mCounters.get(i).paint(mCounterViews.get(i));
			}
		}
		
	}
	
	public Vector<StatCounter> getCounters() {
		return mCounters;
	}
	
	public void linkDisplay(Vector<TextView> counter_views,
							Vector<TextView> info_views,
							GraphView graph) {
		
		mCounterViews = counter_views;
		mInfoViews = info_views;
		
		for (int i=0; i < NUM_COUNTERS; ++i ) {
			mCounters.get(i).paint(mCounterViews.get(i));
		}
		
		processNetStatus();
	}
	
	public void unlinkDisplay() {
		mCounterViews = null;
		mInfoViews = null;
	}
	
	public boolean processUpdate() {
		processNetStatus();
		return processIfStats();
//		return processIfStatsBis();
	}
	
	public Boolean processIfStatsBis() {
		readTraffic(CELL_DEV);
		readTraffic(WIFI_DEV);
		readTraffic(USB_DEV);
		return true;
	}
		
	public void readTraffic(String iface) {
		String bytes = Shell.executeCommand( new String[] { "sh", "-c", "ifconfig " + iface + " | grep bytes" }).trim();
		int rxBegin = bytes.indexOf(":") + 1;
		int rxEnd = bytes.indexOf(" ", rxBegin);
		String rxS = bytes.substring(rxBegin, rxEnd);
		/**
		 * received bytes
		 */
		long rx = Long.parseLong(rxS);

		int txBegin = bytes.indexOf(":", rxEnd) + 1;
		int txEnd = bytes.indexOf(" ", txBegin);
		String txS = bytes.substring(txBegin, txEnd);
		/**
		 * transmitted bytes
		 */
		long tx = Long.parseLong(txS);

		double value = tx + rx;
		
		if(iface.equals(CELL_DEV)) 
			updateStatCounter(Double.toString(value), "0", 0);
		else if(iface.equals(WIFI_DEV)) 
			updateStatCounter(Double.toString(value), "0", 1);
		else if(iface.equals(USB_DEV)) 
			updateStatCounter(Double.toString(value), "0", 2);

	}
	
	public boolean processIfStats() {
		FileReader fstream;
		try {
			fstream = new FileReader(DEV_FILE);
		} catch (FileNotFoundException e) {
			Log.e("MonNet", "Could not read " + DEV_FILE);
			return false;
		}
		BufferedReader in = new BufferedReader(fstream, 500);
		String line;
		String[] segs;
		
		boolean[] updated = new boolean[]{false, false, false}; 

		
		try {
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.startsWith(CELL_DEV)) {
					segs = line.trim().split("[: ]+");
					updateStatCounter(segs[1], segs[9], 0);
					updated[0] = true;
				} else if (line.startsWith(WIFI_DEV)) {
					segs = line.trim().split("[: ]+");
					updateStatCounter(segs[1], segs[9], 1);
					updated[1] = true;
				} else if (line.startsWith(USB_DEV)) {
					segs = line.trim().split("[: ]+");
					updateStatCounter(segs[1], segs[9], 2);
					updated[2] = true;
				}	
			}
			
			for(int i = 0; i < updated.length; i++){
				if(!updated[i]){
					updateStatCounter("0", "0", i);
				}
			}

			
			return true;
		} catch (IOException e) {
			Log.e("MonNet", e.toString());
			return false;
		}
	}
	
	private void updateStatCounter(String text1, String text2, int index) {
				
		if (mCounters.get(index).update(text1, text2, mSamplingInterval)) {
			if (mCounterViews != null) {
				mCounters.get(index).paint(mCounterViews.get(index));
			}
		}
	}
	
	private void processNetStatus() {
		if (mInfoViews != null) {
			NetworkInfo cell_cx = mCx.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
			NetworkInfo wifi_cx = mCx.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
			WifiInfo wifi_info = mWifi.getConnectionInfo();
			
			String cell_label = "";
			String wifi_label = "";
			
			// Cellular data
			if (cell_cx != null && cell_cx.getState() == NetworkInfo.State.CONNECTED) {
			if (mCellular.isNetworkRoaming()) {
				cell_label += "ROAMING ";
			}
			cell_label += mCellular.getNetworkOperatorName();
			cell_label += getCellularType(mCellular.getNetworkType());
			}
			mInfoViews.get(0).setText(cell_label);
			mInfoViews.get(0).setTextColor(Color.GREEN);
			
			// Wifi
			if (wifi_cx != null && wifi_cx.getState() == NetworkInfo.State.CONNECTED) {			
				wifi_label = wifi_info.getSSID();
			}
			mInfoViews.get(1).setText(wifi_label);
			mInfoViews.get(1).setTextColor(Color.GREEN);
		}
	}

	private String getCellularType(int type) {
		switch (type) {
		case TelephonyManager.NETWORK_TYPE_GPRS:
			return " GPRS";
		case TelephonyManager.NETWORK_TYPE_EDGE:
			return " EDGE";
		case TelephonyManager.NETWORK_TYPE_UMTS:
			return " UMTS";
		default:
			return "";
		}
	}
}