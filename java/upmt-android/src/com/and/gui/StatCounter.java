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

import java.text.DecimalFormat;
import android.widget.TextView;

public class StatCounter {
	final private long KILO = 1000;
	final private long MEGA = KILO * 1000;
	final private long GIGA = MEGA * 1000;
	
	final private DecimalFormat mFmt = new DecimalFormat("###,###.0");
	final private String mUnit;
	
	private String mLastUpdate;
	private long mBase;
	private long mValue;
	private HistoryBuffer mRateHistory;
	
	
	StatCounter(String unit) {
		mUnit = unit;
		mBase = 0;
		mValue = 0;
		mRateHistory = new HistoryBuffer();
		mLastUpdate = null;
	}
	final public void reset() {
		mBase = mValue;
	}
	
	final public boolean update(String val_str1, String val_str2, int time_delta) {
		int val = Integer.parseInt(val_str1) + Integer.parseInt(val_str2);
		String val_str = Integer.toString(val);
				
		//pcerqua
		if(val < 0)
			val = 0;
		
		if (val_str == mLastUpdate) {
			mRateHistory.add(0);
			return false;
		}
		
//		int val = Integer.parseInt(val_str);
		if (val < mBase || mLastUpdate == null) {
			mBase = 0; // wrap-around or reset
		} 
		else if (val == 0){
			mRateHistory.add(0);
		}
		else {
			mRateHistory.add((int)((val - mBase) - (mValue - mBase)) / time_delta * 8);
		}
		mValue = val;	
		mLastUpdate = val_str;
		return true;
	}
	
	final public void paint(TextView view) {
		Long disp_val = mValue - mBase;
		if ( disp_val > GIGA) {
			view.setText(mFmt.format((double)disp_val / GIGA) + " G" + mUnit);
		} else if ( disp_val > MEGA) {
			view.setText(mFmt.format((double)disp_val / MEGA) + " M" + mUnit);
		} else if ( disp_val > KILO) {
			view.setText(mFmt.format((double)disp_val / KILO) + " k" + mUnit);
		} else {
			view.setText(Long.toString(disp_val) + " " + mUnit);
		}
	}
	
	final public HistoryBuffer getHistory() {
		return mRateHistory;
	}
}
