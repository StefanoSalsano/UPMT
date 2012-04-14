package com.and.gui.utils;

import java.util.List;

import com.and.gui.App;
import com.and.gui.R;
import com.and.gui.activity.GUI;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ApplicationAdapter extends ArrayAdapter<App> {

	private int resource;
	private LayoutInflater inflater;
	private App app;
	private String currInterf;

	public ApplicationAdapter(Context context, int resourceId, List<App> objects)
	{
		super(context, resourceId, objects);
		resource = resourceId;
		inflater = LayoutInflater.from(context);
	}        

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		// Recuperiamo l'oggetto che dobbiamo inserire in questa posizione
		app = getItem(position);

		String currPolicy = app.getCurrPolicy();
		String storPolicy = app.getStorPolicy();

		AppViewCache viewCache;

		if(convertView == null)
		{
			convertView = (RelativeLayout)inflater.inflate(resource, null);
			viewCache = new AppViewCache(convertView);
			convertView.setTag(viewCache);
		}
		else
		{
			convertView = (RelativeLayout)convertView;
			viewCache = (AppViewCache)convertView.getTag();
		}

		// Prendiamo le view dalla cache e mettiamoci i valori
		TextView tvName = viewCache.getTextViewName();
		tvName.setText(app.getName());

		//if(app.getName().equals("Browser") || app.getName().equals("ASTRO"))
		//{
		//System.out.println("ok2");
		//}

		ImageView ivIcon = viewCache.getImageViewIcon();
		ivIcon.setImageDrawable(app.getIcon());

		ImageView ivDef = viewCache.getImageViewDef();
		if(currPolicy == null || !currPolicy.equals(GUI.getDefaultPolicy())) 
			ivDef.setImageResource(R.drawable.not_default);
		else 
			ivDef.setImageResource(R.drawable.default_);


		ImageView ivInt1 = viewCache.getImageViewInt1();
		ImageView ivInt2 = viewCache.getImageViewInt2();
		ImageView ivInt3 = viewCache.getImageViewInt3();


		currInterf = app.getCurrentInterf();

		String policy;
		if((policy = currPolicy) == null) 
			policy = storPolicy;

		String[] currPolicyInterf = policy.split("\\s+");


		ImageView ivRunning = viewCache.getImageRunning();
		if(app.isRunning())
		{
			if(currInterf != null)
			{
				ivRunning.setImageResource(R.drawable.running_green);
			}
			else
			{
				ivRunning.setImageResource(R.drawable.running);
			}
		}
		else ivRunning.setImageResource(0);

		ivInt1.setImageDrawable(null);
		ivInt2.setImageDrawable(null);
		ivInt3.setImageDrawable(null);

		if(currPolicyInterf[0].equals("PriorityList"))
		{
			//pcerqua
			if(currPolicyInterf.length > 1){
				ivInt1.setImageResource(intImageSel(currPolicyInterf[1]));
				//System.out.println(currPolicyInterf[1]);
			}
			if(currPolicyInterf.length > 2){
				ivInt2.setImageResource(intImageSel(currPolicyInterf[2]));
				//System.out.println(currPolicyInterf[2]);
			}
			if(currPolicyInterf.length > 3){
				ivInt3.setImageResource(intImageSel(currPolicyInterf[3]));
				//System.out.println(currPolicyInterf[3]);
			}
		}

		//pcerqua
		if(currPolicyInterf[0].equals("Block")){
			ivInt1.setImageResource(android.R.drawable.ic_lock_lock);
		}

		return convertView;
	}

	private int intImageSel(String interf)
	{
	
		if(interf.equals("rmnet0"))
		{
			if(currInterf != null && currInterf.equals("rmnet0")) return R.drawable.data_on;
			else return R.drawable.data_off;
		}
		else if(interf.equals("eth0"))
		{
			if(currInterf != null && currInterf.equals("eth0")) return R.drawable.wifi_on;
			else return R.drawable.wifi_off;
		}
		else if(interf.equals("usb0"))
		{
			if(currInterf != null && currInterf.equals("usb0")) return R.drawable.usb_on2;
			else return R.drawable.usb_off2;
		}
		else if(interf.equals("any"))
		{
			if(currInterf != null) return android.R.drawable.btn_star_big_on;
			else return android.R.drawable.btn_star_big_off;
		}
		else    	
			return android.R.drawable.alert_dark_frame;
	}
}