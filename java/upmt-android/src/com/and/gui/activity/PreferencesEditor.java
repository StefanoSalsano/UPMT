package com.and.gui.activity;

import com.and.gui.R;

import android.app.Activity;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class PreferencesEditor extends Activity implements OnSeekBarChangeListener
{
	// members which hold the required views
	SeekBar mSbCost;
	SeekBar mSbBatteryLife;
	SeekBar mSbSecurityLevel;
	SeekBar mSbQualityLevel;
	Spinner mSpinLowestQuality;
	
	// members that contain the preference information for the application 
	// that is currently edited.
	
	// constants
	private static final int GAP_BATTERY = 2;
	private static final int GAP_SECURITY = 3;
	private static final int GAP_QUALITY = 3;
	private final int SEEK_MAX = 8;
	private static final String TAG = "PoliciesEditor";
	
	@Override
	/**
	 * This method is called when the activity starts initially or when it is restarted
	 * after it was killed by Android because of memory shortage.
	 */
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		// inflate the view
		setContentView(R.layout.preferences_editor);
				
		// Dynamically set the activity title, according to the application for
		// which the preferences shall be edited.
		String activityTitle = getString(R.string.preferences_label);
		Bundle extras = getIntent().getExtras();
		if (extras != null)
		{
			activityTitle += " - ";
			String apTitle = extras.getString("APP_TITLE");
			activityTitle += apTitle;
		}
		
		this.setTitle(activityTitle);
		
		// register the buttons
		initButtons();
	}
	
	/**
	 * Called on start of the activity.
	 */
	@Override
	public void onStart() {
		super.onStart();
		
	}
	

	/**
	 * Find all needed views and save them to the members, put all settings to initial values. 
	 */
	private void initButtons(){
		mSbCost = (SeekBar)findViewById(R.id.SbCost);
		mSbBatteryLife = (SeekBar)findViewById(R.id.SbBattery);
		mSbSecurityLevel = (SeekBar)findViewById(R.id.SbSecurityLevel);
		mSbQualityLevel = (SeekBar)findViewById(R.id.SbQualityLevel);
		
		mSbCost.setMax(SEEK_MAX);
		mSbBatteryLife.setMax(SEEK_MAX);
		mSbSecurityLevel.setMax(SEEK_MAX);
		mSbQualityLevel.setMax(SEEK_MAX);
		
		mSbCost.setProgress(SEEK_MAX/2);
		mSbBatteryLife.setProgress(SEEK_MAX/2);
		mSbSecurityLevel.setProgress(SEEK_MAX/2);
		mSbQualityLevel.setProgress(SEEK_MAX/2);
		
		mSbCost.setOnSeekBarChangeListener(this);
		mSbBatteryLife.setOnSeekBarChangeListener(this);
		mSbSecurityLevel.setOnSeekBarChangeListener(this);
		mSbQualityLevel.setOnSeekBarChangeListener(this);
	}
	
	@Override
	/**
	 * Monitor the change of the SeekBar level and adjust depending sliders if needed.
	 */
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
		if (!fromTouch)
			return;
		
		int cost = mSbCost.getProgress();
		int battery = mSbBatteryLife.getProgress();
		int security = mSbSecurityLevel.getProgress();
		int quality = mSbQualityLevel.getProgress();
		
		switch(seekBar.getId()){
		case R.id.SbCost:
			battery = Math.min(battery, SEEK_MAX - seekBar.getProgress() + GAP_BATTERY);
			security = Math.min(security, SEEK_MAX - seekBar.getProgress() + GAP_SECURITY);
			quality = Math.min(quality, SEEK_MAX - seekBar.getProgress() + GAP_QUALITY);
			mSbBatteryLife.setProgress(battery);
			mSbSecurityLevel.setProgress(security);
			mSbQualityLevel.setProgress(quality);
			break;
		case R.id.SbBattery:
			cost = Math.min(cost, SEEK_MAX - seekBar.getProgress() + GAP_BATTERY);
			mSbCost.setProgress(cost);
			break;
		case R.id.SbSecurityLevel:
			cost = Math.min(cost, SEEK_MAX - seekBar.getProgress() + GAP_SECURITY);
			mSbCost.setProgress(cost);
			break;
		case R.id.SbQualityLevel:
			cost = Math.min(cost, SEEK_MAX - seekBar.getProgress() + GAP_QUALITY);
			mSbCost.setProgress(cost);
			break;
		}
	}
	
	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{
		// handled in onProgressChanged
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{
		// handled in onProgressChanged
	}

	@Override
	/**
	 * Called once the Activity is being destroyed.
	 */
	protected void onDestroy()
	{		
		super.onDestroy();
		
		//TODO if changes were made...do something
	}	
}
