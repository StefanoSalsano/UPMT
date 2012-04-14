package com.and.gui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class BRNetworkMonitor extends BroadcastReceiver
{
	NetworkInfo[] Networks = null;

	@Override
	public void onReceive(Context context, Intent intent)
	{
		// TODO Auto-generated method stub

		int networkType = 0;
		String tickerText = "";

		ConnectivityManager connectivity = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		Networks = connectivity.getAllNetworkInfo();

		for (int i = 0; i < Networks.length; i++)
		{
			networkType = Networks[i].getType();
			if (!Networks[i].isConnectedOrConnecting())
				continue;

			switch (networkType)
			{
			case (ConnectivityManager.TYPE_MOBILE):
			{
				tickerText += ("Mobile");
				break;
			}
			case (ConnectivityManager.TYPE_WIFI):
			{
				tickerText += ("WIFI");
				break;
			}
			default:
				break;
			}
		}

		if (tickerText.equals(""))
			tickerText += ("No connection");
	}
}
