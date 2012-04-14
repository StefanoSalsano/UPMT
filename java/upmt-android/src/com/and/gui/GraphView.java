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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.and.gui.HistoryBuffer.CircularBuffer;


public class GraphView extends View {
	
	final private int TICKS = 1;
	final private Paint mBackgroundPaint = makePaint(Color.BLACK);
	final private Paint mAxisPaint = makePaint(Color.WHITE);
	
//	final private Paint mIn = makePaint(Color.RED);
//	final private Paint mOut = makePaint(Color.GREEN);
	
	final private Paint m3g = makePaint(Color.RED);
	final private Paint mWifi = makePaint(Color.GREEN);
	final private Paint mUsb = makePaint(Color.LTGRAY);

//	final private Paint mCpu = makePaint(Color.LTGRAY);
	
	private Vector<StatCounter> mCounters = null;
//	private HistoryBuffer mCpuCounter = null;
	
	private int mResolution = 0;
	private int mRefreshTicks = 0;

	
	class Projection {
		final public int mWidth;
		final public int  mHeight;
		final public int mOffset;
		final public int mXrange;
		final public int mYrange;
		final private float mXscale;
		final private float mYscale;
		public Projection(int width, int height, int offset,
						int x_range, int y_range) {
			mWidth = width;
			mHeight = height;
			mOffset = offset;
			mXrange = x_range;
			mYrange = y_range;
			mXscale = (float)(width) / x_range;
			mYscale = (float)(height) / y_range;
		}
		public float x(int x) {
			return x * mXscale + 5;
		}
		public float y(int y) {
			return mHeight - y * mYscale + mOffset;
		}
	}
	
	public GraphView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public String toggleScale() {
		mResolution += 1;
		mResolution %= 7;
		if (mResolution > getMaxTimescale()) {
			mResolution = 0;
		}
		invalidate();
		mRefreshTicks = mResolution  * 2 + 1;
		return getBanner();
	}
	
	public void refresh() {
		if (mRefreshTicks == 0) {
			invalidate();
			mRefreshTicks = mResolution * 2 + 1;
		} else {
			--mRefreshTicks;
		}
	}
	
	public void linkCounters(Vector<StatCounter> counters
//							,HistoryBuffer cpu
							) {
		mCounters = counters;
//		mCpuCounter = cpu;
		mResolution = getMaxTimescale();
		invalidate();
	}
	
	@Override
	public void onDraw(Canvas canvas) {
        canvas.drawPaint(mBackgroundPaint);
        if (mCounters == null
//        		|| mCpuCounter == null
        		) return;
  
//        Projection cell_proj = getDataScale(0);
        Projection wifi_proj = getDataScale(0);
//        int offset = (getHeight() - 15) / 3 * 2;
//        Projection wifi_proj = new Projection(310,
////				23,
//        		120,
//        		0,
////				offset,
//				180, 100);
//        Projection cpu_proj = new Projection(cell_proj.mWidth,
//        									cell_proj.mHeight,
//        									offset,
//        									cell_proj.mXrange, 100);
        
//        drawAxis(canvas, cell_proj, "cellular", "bps");
        drawAxis(canvas, wifi_proj, "wifi", "3g", "usb", "bps");
//        drawAxis(canvas, cpu_proj, "cpu", "%");
        
		
//		canvas.drawText(getBanner(),
//				cpu_proj.x(cpu_proj.mXrange / 2), cpu_proj.y(0) + 12,
//				mAxisPaint);
        
//        drawGraph(canvas, cell_proj, mIn, 
//        		mCounters.get(0).getHistory().getData(mResolution));
//        drawGraph(canvas, cell_proj, mOut, 
//        		mCounters.get(1).getHistory().getData(mResolution));
        
        drawGraph(canvas, wifi_proj, m3g, 
        		mCounters.get(0).getHistory().getData(mResolution));
        drawGraph(canvas, wifi_proj, mWifi, 
        		mCounters.get(1).getHistory().getData(mResolution));
        drawGraph(canvas, wifi_proj, mUsb, 
        		mCounters.get(2).getHistory().getData(mResolution));
        
//        drawGraph(canvas, wifi_proj, mIn, 
//        		mCounters.get(2).getHistory().getData(mResolution));
//        drawGraph(canvas, wifi_proj, mOut, 
//        		mCounters.get(3).getHistory().getData(mResolution));

//        drawGraph(canvas, cpu_proj, mCpu,
//        		mCpuCounter.getData(mResolution));
	}
	
	private int getMaxTimescale() {
		int capacity = mCounters.get(0).getHistory()
						.getData(5).getCapacity();
		int size = mCounters.get(0).getHistory()
					.getData(5).getSize();
		
		capacity -= capacity/10;
		if (size > capacity/2) return 6;
		if (size > capacity/4) return 5;
		if (size > capacity/8) return 4;
		if (size > capacity/24) return 3;
		if (size > capacity/48) return 2;
		if (size > capacity/96) return 1;
		return 0;
	}
	
	private String getBanner() {
		switch(mResolution) {
		case 0:
			return "15min";
		case 1:
			return "30min";
		case 2:
			return "1hour";
		case 3:
			return "3hours";
		case 4:
			return "6hours";
		case 5:
			return "12hours";
		case 6:
			return "24hours";
		default:
			return "invalid";
		}
	}
	
	private Projection getDataScale(int index) {
		int xscale = mCounters.get(index * 2).getHistory()
					.getData(mResolution).getCapacity();
		if (mResolution == 0) {
			xscale /= 4;
		}
		else if (mResolution % 2 == 1) {
			xscale /= 2;
		}
		int yscale = 10;
		

		for (int i=0; i< mCounters.size(); ++i) {
			int val = mCounters.get(index * 2 + i).getHistory()
					.getData(mResolution).getMax(xscale);
			if (val > yscale) {
				yscale = val;
			}
		}
		
		yscale = yscale + (yscale/10); // + 10%
		yscale = ((yscale / 10) + 1) * 10;
		int height = (getHeight() - 15);
		
		return new Projection(getWidth() - 10,
				height - 5,
				height * index,
				xscale, yscale);
	}
	
	private void drawGraph(Canvas canvas,
			Projection proj,
			Paint color,
			CircularBuffer data) {
		
		int y_start = data.lookBack(0);
		int y_end;
		for (int i = 1; i < data.getSize(); ++i) {
			y_end = data.lookBack(i);
			canvas.drawLine(proj.x(proj.mXrange - i + 1), proj.y(y_start),
					proj.x(proj.mXrange - i), proj.y(y_end),
					color);
			y_start = y_end;
		}
		
	}
	
	private void drawAxis(Canvas canvas, Projection proj,
			String title1, String title2, String title3, String unit) {
		
		canvas.drawLine(proj.x(0), proj.y(0),
				proj.x(proj.mXrange), proj.y(0),
				mAxisPaint);

		
		canvas.drawLine(proj.x(0), proj.y(0), proj.x(0),
				proj.y(proj.mYrange),
				mAxisPaint);
		
		
		int x_step = proj.mXrange / TICKS;
		int y_step = proj.mYrange / TICKS;
		for (int i=1; i <= TICKS; ++i) {
			canvas.drawLine(proj.x(x_step * i), proj.y(0),
					proj.x(x_step * i), proj.y(0) - 10, mAxisPaint);
			canvas.drawLine(proj.x(0), proj.y(y_step * i),
					proj.x(0) + 10, proj.y(y_step * i), mAxisPaint);	
		}
		canvas.drawText(Integer.toString(proj.mYrange) + unit,
				proj.x(0) + 10, proj.y(proj.mYrange) + 10, mAxisPaint);
		
		canvas.drawText(title1, proj.x(proj.mXrange / 2),
				proj.y(proj.mYrange) + 10,
				mWifi);
		
		canvas.drawText(title2, proj.x(proj.mXrange / 2) + 30,
				proj.y(proj.mYrange) + 10,
				m3g);
		
		canvas.drawText(title3, proj.x(proj.mXrange / 2) + 60,
				proj.y(proj.mYrange) + 10,
				mUsb);
	}
		
	
	private Paint makePaint(int color) {
		Paint p = new Paint();
		p.setColor(color);
		return p;
	}
}
