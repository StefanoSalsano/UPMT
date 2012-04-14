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

import java.util.Vector;


import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

/**
 * Local service which operates in close cooperation with NetMeter activity.
 * 
 * Execute monitoring through periodic polling, update in-memory history
 * buffers and update display if linkage has been established by the
 * activity after binding to the service.
 * 
 * Whenever running, maintain a persistent notification in the status bar, which
 * sends an intent to (re)start NetMeter activity.
 */
public class NetMeterService extends Service {
	final private String TAG="NetMeterService";
	final private int SAMPLING_INTERVAL = 2;

	//	private NotificationManager mNM;

	/**
	 * 
	 * Binder implementation which passes through a reference to
	 * this service. Since this is a local service, the activity
	 * can then call directly methods on this service instance.
	 */
	public class NetMeterBinder extends Binder {
		public NetMeterService getService() {
			return NetMeterService.this;
		}
	}
	private final IBinder mBinder = new NetMeterBinder();

	// various stats collection objects
	private StatsProcessor mStatsProc;
	//	private CpuMon mCpuMon;
	private GraphView mGraph = null;
	private long mLastTime;

	// All the polling and display updating is driven from this
	// hander which is periodically executed every SAMPLING_INTERVAL seconds.
	private Handler mHandler = new Handler();
	private Runnable mRefresh = new Runnable() {
		public void run() {
			// Compensate for sleep time, since this hander is not getting called
			// when the device is asleep/suspended
			long last_time = SystemClock.elapsedRealtime();
			if (last_time - mLastTime > 2 * SAMPLING_INTERVAL * 1000) {
				int padding = (int) ((last_time - mLastTime) / (SAMPLING_INTERVAL * 1000));
				//				mCpuMon.getHistory().pad(padding);

				Vector<StatCounter> counters = mStatsProc.getCounters();
				for (int i = 0; i < counters.size(); i++) {
					counters.get(i).getHistory().pad(padding);
				}
			}
			mLastTime = last_time;
			mStatsProc.processUpdate();
			//			mCpuMon.readStats();
			if (mGraph != null) mGraph.refresh();
			mHandler.postDelayed(mRefresh, SAMPLING_INTERVAL * 1000);
		}
	};
	/**
	 * Reset the counters - triggered by the reset menu of the controller activity
	 */
	public void resetCounters() {
		mStatsProc.reset();
	}

	/**
	 * 
	 * Link the display objects set up by the controller activity
	 * to the service so that they can be updated with the latest
	 * state after each polling interval
	 * 
	 * In retrospect, this is probably a rather hacky architecture.
	 * 
	 * @param stats_views text view to display network counters
	 * @param info_views text views to display network status info
	 * @param cpu_views text views to display CPU usage information
	 * @param graph reference to graph widget
	 */
	public void setDisplay(Vector<TextView> stats_views,
			Vector<TextView> info_views,
			//			Vector<TextView> cpu_views,
			GraphView graph) {
		mGraph = graph;
		mStatsProc.linkDisplay(stats_views, info_views, graph);
		//		mCpuMon.linkDisplay(cpu_views);
		graph.linkCounters(mStatsProc.getCounters()
				//						,mCpuMon.getHistory()
				);
	}

	/**
	 * Framework method called when the service is first created.
	 */
	@Override
	public void onCreate() {
		Log.i(TAG, "onCreate");
		WifiManager wifi = (WifiManager)getSystemService(WIFI_SERVICE);
		TelephonyManager cellular = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		ConnectivityManager cx = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);

		mStatsProc = new StatsProcessor(SAMPLING_INTERVAL, cellular, wifi, cx);
		//		mCpuMon = new CpuMon();

		mStatsProc.processUpdate();
		mStatsProc.reset();
		//		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		//		postNotification();
		mLastTime = SystemClock.elapsedRealtime();
		mHandler.postDelayed(mRefresh, SAMPLING_INTERVAL * 1000);
		setForeground(true);
	}

	/**
	 * Framework method called when the service is stopped/destroyed
	 */
	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		//		mNM.cancel(R.string.iconized);
		mHandler.removeCallbacks(mRefresh);
	}

	/**
	 * Framework method called whenever an activity binds to this service.
	 */
	@Override
	public IBinder onBind(Intent arg0) {
		Log.i(TAG, "onBind");
		return mBinder;
	}
	/**
	 * Framework method called when an activity binding to the service
	 * is broken.
	 */
	@Override
	public boolean onUnbind(Intent arg) {
		Log.i(TAG, "onUnbind");
		mStatsProc.unlinkDisplay();
		//		mCpuMon.unlinkDisplay();
		mGraph = null;
		return true;
	}

}