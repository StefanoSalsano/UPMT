package com.and.gui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.and.gui.App;
import com.and.gui.R;

public class Policy extends Activity
{
	private String currInterf;
	private String policy[];
	private String name;
	private String pack;
	private Bundle extras;

	private ImageView ivCI;
	private ImageView ivPol1;
	private ImageView ivPol2;
	private ImageView ivPol3;

	private ImageView ivSP1;
	private ImageView ivSP2;
	private ImageView ivSP3;


	private String currPolicy;
	private String storPolicy;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.policy);

		Intent intent = getIntent();
		extras = intent.getExtras();

		String activityTitle = getString(R.string.preferences_label);
		currInterf = extras.getString(getString(R.string.CURRENT_INTERFACE_TAG));
		currPolicy = extras.getString(getString(R.string.CURRENT_POLICY_TAG));
		storPolicy = extras.getString(getString(R.string.STORED_POLICY_TAG));
		name = extras.getString(getString(R.string.APP_NAME_TAG));
		pack = extras.getString(getString(R.string.PACK_NAME_TAG));
		activityTitle += " - ";
		activityTitle += name;
		this.setTitle(activityTitle);

		ivCI = (ImageView)findViewById(R.id.ivCI);

		ivPol1 = (ImageView)findViewById(R.id.ivPol1);
		ivPol2 = (ImageView)findViewById(R.id.ivPol2);
		ivPol3 = (ImageView)findViewById(R.id.ivPol3);

		ivSP1 = (ImageView)findViewById(R.id.ivSP1);
		ivSP2 = (ImageView)findViewById(R.id.ivSP2);
		ivSP3 = (ImageView)findViewById(R.id.ivSP3);

		ivSP1.setImageDrawable(null);
		ivSP2.setImageDrawable(null);
		ivSP3.setImageDrawable(null);
		ivPol1.setImageDrawable(null);
		ivPol2.setImageDrawable(null);
		ivPol3.setImageDrawable(null);


		if(currPolicy.equals("Block"))
			currInterf = null;

		if(currInterf != null)
			ivCI.setImageResource(currInterf.equals("eth0")?R.drawable.wifi_on:R.drawable.data_on);
		else
			ivCI.setImageResource(android.R.drawable.ic_delete);

		if(storPolicy != null)
		{
			policy = storPolicy.split("\\s+");

			if(policy.length > 1)
				ivSP1.setImageResource(intImageSel(policy[1]));
			if(policy.length > 2)
				ivSP2.setImageResource(intImageSel(policy[2]));
			if(policy.length > 3)
				ivSP3.setImageResource(intImageSel(policy[3]));
		}
		else{
			ivSP1.setImageResource(android.R.drawable.ic_delete);
			ivSP2.setImageDrawable(null);
			ivSP3.setImageDrawable(null);
		}

		//pcerqua
		if(storPolicy != null && storPolicy.equals("Block")){
			ivSP1.setImageResource(android.R.drawable.ic_lock_lock);
			ivSP2.setImageDrawable(null);
			ivSP3.setImageDrawable(null);
		}


		if(currPolicy == null) 
			currPolicy = storPolicy;

		policy = currPolicy.split("\\s+");

		if(policy.length > 1)
			ivPol1.setImageResource(intImageSel(policy[1]));
		if(policy.length > 2)
			ivPol2.setImageResource(intImageSel(policy[2]));
		if(policy.length > 3)
			ivPol3.setImageResource(intImageSel(policy[3]));

		//pcerqua
		if(currPolicy.equals("Block")){
			//System.out.println("\t\tstored Block Policy and null current policy -> currpolicy = storpolicy");
			ivPol1.setImageResource(android.R.drawable.ic_lock_lock);
			ivPol2.setImageDrawable(null);
			ivPol3.setImageDrawable(null);
		}
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		App app = GUI.singleApp(name);
		currInterf = app.getCurrentInterf();

		storPolicy = app.getStorPolicy();
		currPolicy = app.getCurrPolicy();

		ivSP1.setImageDrawable(null);
		ivSP2.setImageDrawable(null);
		ivSP3.setImageDrawable(null);
		ivPol1.setImageDrawable(null);
		ivPol2.setImageDrawable(null);
		ivPol3.setImageDrawable(null);

		if(currPolicy != null && currPolicy.equals("Block"))
			currInterf = null;

		if(currInterf != null){
			int cIRes = -1;

			if(currInterf.equals("eth0"))
				cIRes = R.drawable.wifi_on;
			if(currInterf.equals("rmnet0"))
				cIRes = R.drawable.data_on;
			if(currInterf.equals("usb0"))
				cIRes = R.drawable.usb_on2;
			if(currInterf.equals("usb0"))
				cIRes = android.R.drawable.btn_star_big_on;

			if(cIRes == -1)
				ivCI.setImageResource(android.R.drawable.alert_dark_frame);
		}
		else
			ivCI.setImageResource(android.R.drawable.ic_delete);

		if(storPolicy != null)
		{
			policy = storPolicy.split("\\s+");
			if(policy.length > 1)
				ivSP1.setImageResource(intImageSel(policy[1]));
			if(policy.length > 2)
				ivSP2.setImageResource(intImageSel(policy[2]));
			if(policy.length > 3)
				ivSP3.setImageResource(intImageSel(policy[3]));
		}
		else{
			ivSP1.setImageResource(android.R.drawable.ic_delete);
			ivSP2.setImageDrawable(null);
			ivSP3.setImageDrawable(null);
		}

		//pcerqua
		if(storPolicy != null && storPolicy.equals("Block")){
			//System.out.println("\t\tstored Block Policy");
			ivSP1.setImageResource(android.R.drawable.ic_lock_lock);
			ivSP2.setImageDrawable(null);
			ivSP3.setImageDrawable(null);
		}

		if(currPolicy == null) 
			currPolicy = storPolicy;
		policy = currPolicy.split("\\s+");

		if(policy.length > 1)
			ivPol1.setImageResource(intImageSel(policy[1]));
		if(policy.length > 2)
			ivPol2.setImageResource(intImageSel(policy[2]));
		if(policy.length > 3)
			ivPol3.setImageResource(intImageSel(policy[3]));

		//pcerqua
		if(currPolicy.equals("Block")){
			//System.out.println("\t\tstored Block Policy and null current policy -> currpolicy = storpolicy");
			ivPol1.setImageResource(android.R.drawable.ic_lock_lock);
			ivPol2.setImageDrawable(null);
			ivPol3.setImageDrawable(null);
		}
	}


	public void myClickHandler(View target)
	{
		switch(target.getId())
		{
		case(R.id.tvCurrentPolicy):
		{
			Intent intent = new Intent(target.getContext(), PolicyEditor.class);
			intent.putExtra(getString(R.string.CURRENT_POLICY_TAG), policy);
			intent.putExtra(getString(R.string.APP_NAME_TAG), name);
			intent.putExtra(getString(R.string.PACK_NAME_TAG), pack);
			intent.putExtra("iflist", extras.getSerializable("iflist"));
			startActivity(intent);
			break;
		}
		case(R.id.btnAdvanced):
		{
			Intent intent = new Intent(target.getContext(), PreferencesEditor.class);
			intent.putExtra(getString(R.string.APP_NAME_TAG), name);
			startActivity(intent);
			break;
		}
		}
	}

	private int intImageSel(String interf)
	{

		if(interf.equals("rmnet0"))
		{
			if(currInterf != null && currInterf.equals("rmnet0")) 
				return R.drawable.data_on;
			else 
				return R.drawable.data_off;
		}
		else if(interf.equals("eth0"))
		{
			if(currInterf != null && currInterf.equals("eth0")) 
				return R.drawable.wifi_on;
			else 
				return R.drawable.wifi_off;
		}
		else if(interf.equals("usb0"))
		{
			if(currInterf != null && currInterf.equals("usb0")) 
				return R.drawable.usb_on2;
			else 
				return R.drawable.usb_off2;
		}
		else if(interf.equals("any"))
		{
			if(currInterf != null && currInterf.equals("any"))
				return android.R.drawable.btn_star_big_on;
			else 
				return android.R.drawable.btn_star_big_off;
		}

		return android.R.drawable.alert_dark_frame;
	}
}
