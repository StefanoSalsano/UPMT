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

public class HistoryBuffer {

	private CircularBuffer mHourly;
	private CircularBuffer mSixHours;
	private CircularBuffer mDaily;
	
	class CircularBuffer {
		
		final private double EMA_FILTER = 0.5;
		private int[] mData;
		final private int mCapacity;
		final private int mSampleRate;
		private int mSize=0;
		private int mWritePos= 0;
		private int mSum;
		private int mSampleCount;
		private double mEMA = 0;


		public CircularBuffer(int size, int sampling) 
		{
			mData = new int[size];
			mCapacity = size;
			mSampleRate = sampling;
			mSum = 0;
			mSampleCount = 0;
		}
		
		final public void add(int element) {
			mSum += element;
			if (++mSampleCount < mSampleRate) return;
			mEMA = (1.0 - EMA_FILTER) * mEMA + EMA_FILTER * (mSum / mSampleRate);
			if (mSize < mCapacity) {
				mData[mWritePos] = (int)mEMA;
				++mSize;
			} else {
				mData[mWritePos] = (int)mEMA;
			}
			++mWritePos;
			mWritePos %= mCapacity;
			mSum = 0;
			mSampleCount = 0;
		}
		
		final public int lookBack(int steps) {
			if (mSize == 0) return 0;
			if (steps > mWritePos - 1) {
				return mData[mCapacity - (steps - (mWritePos - 1))];
			} else {
				return mData[mWritePos -1  - steps];
			}
		}
		
		final public int getSize() {
			return mSize;
		}
		
		final public int getCapacity() {
			return mCapacity;
		}
		
		final public int getMax(int window) {
			int max = 0;
			if (window >= mSize) {
				window = mSize -1;
			}
			for (int i = 0; i < window; ++i) {
				if ( lookBack(i) > max) {
					max = lookBack(i);
				}
			}
			return max;
		}
	}
	
	public HistoryBuffer() {
		mHourly = new CircularBuffer(720, 1);
		mSixHours = new CircularBuffer(360, 12);
		mDaily = new CircularBuffer(720, 24);
	}
	
	public void add(int element) {
		mHourly.add(element);
		mSixHours.add(element);	
		mDaily.add(element);
	}	
	
	public void pad(int count) {
		for (int i=0; i < count; i++) {
			add(0);
		}
	}
	
	public CircularBuffer getData(int resolution) {
		switch (resolution) {
		case 0:
		case 1:
		case 2:
			return mHourly;
		case 3:
		case 4:
			return mSixHours;
		default:
			return mDaily;
		}
	}
}
