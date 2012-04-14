package com.and.gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class NetworkMonitor extends Activity
{
	/** Called when the activity is first created. */

	private static final String LOG_TAG = "com.and.netmon.networkmonitor";
	NetworkInfo[] Networks = null;
	int networkType;
	ArrayList<String> al = new ArrayList<String>();

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setTitle("Network Manager");
		setContentView(R.layout.main);

		final ListView lv = (ListView) findViewById(R.id.ListView01);

		String service = Context.CONNECTIVITY_SERVICE;
		final ConnectivityManager connectivity = (ConnectivityManager) getSystemService(service);

		Networks = connectivity.getAllNetworkInfo();

		if (Networks.length == 0)
		{
			al.add("No connection");
		} else
		{
			for (int i = 0; i < Networks.length; i++)
			{
				networkType = Networks[i].getType();
				if(!Networks[i].isConnectedOrConnecting()) continue;

				switch (networkType)
				{
				case (ConnectivityManager.TYPE_MOBILE):
				{
					al.add("Mobile");
					al.add("IP: " + getLocalIpAddress("rmnet0"));
					al.add(Networks[i].getExtraInfo());
					al.add("Gateway: " + getGateway("rmnet0"));
					String ip_netmask = getIPNetMask("rmnet0");
					String[] out = ip_netmask.split("/");
					al.add("Netmask: "
							+ getPrefix(Integer.toString(ipToInt(out[0])),
									out[1]));
					al.add("-------------");
					break;
				}
				case (ConnectivityManager.TYPE_WIFI):
				{
					al.add("WIFI");
					al.add("IP: " + getLocalIpAddress("eth0"));
					WifiManager wifi = (WifiManager) getApplicationContext()
							.getSystemService(Context.WIFI_SERVICE);
					WifiInfo wi = wifi.getConnectionInfo();
					al.add("SSID: " + wi.getSSID());
					al.add("gateway: " + ipFromInt(wifi.getDhcpInfo().gateway));
					al.add("Netmask: " + ipFromInt(wifi.getDhcpInfo().netmask));
					al.add("-------------");
					break;
				}
				default:
					break;
				}

			}
			ArrayAdapter<String> aa = new ArrayAdapter<String>(
					getApplicationContext(),
					android.R.layout.simple_list_item_1, al);
			lv.setAdapter(aa);
			// aa.notifyDataSetChanged();
		}

//		 registerReceiver(
//		 new BroadcastReceiver()
		// {
		// @Override
		// public void onReceive(Context context, Intent intent)
		// {
		// activeNetwork = connectivity.getActiveNetworkInfo();
		// if(activeNetwork == null)
		// {
		// tv1.setText("No connection");
		// tv2.setText("");
		// tv3.setText("");
		// tv4.setText("");
		// tv5.setText("");
		// }
		// else
		// {
		// networkType = activeNetwork.getType();
		//
		// switch (networkType)
		// {
		// case (ConnectivityManager.TYPE_MOBILE) :
		// {
		// tv1.setText("Mobile");
		// tv2.setText("IP: " + getLocalIpAddress());
		// tv3.setText("");
		// tv4.setText("Gateway: " + getGateway());
		// String ip_netmask = getIPNetMask();
		// String [] out = ip_netmask.split("/");
		// tv5.setText("Prefix: " + getPrefix(Integer.toString(ipToInt(out[0])),
		// out[1]));
		// break;
		// }
		// case (ConnectivityManager.TYPE_WIFI) :
		// {
		// tv1.setText("WIFI");
		// tv2.setText("IP: " + getLocalIpAddress());
		// WifiManager wifi = (WifiManager)
		// context.getSystemService(Context.WIFI_SERVICE);
		// WifiInfo wi = wifi.getConnectionInfo();
		// tv3.setText("SSID: " + wi.getSSID());
		// tv4.setText("gateway: " + ipFromInt(wifi.getDhcpInfo().gateway));
		// tv5.setText("Prefix: " + ipFromInt(wifi.getDhcpInfo().netmask));
		// break;
		// }
		// default: break;
		// }
		// }
		// }
		// }
		// ,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
		// );
	}

	public String getLocalIpAddress(String interf)
	{
		try
		{
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();)
			{
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();)
				{
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (intf.getName().equals(interf))
					{
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ex)
		{
			Log.e(LOG_TAG, ex.toString());
		}
		return null;
	}

	// private static String intToIp(int i) {
	// return (i & 0xFF) + "." +
	// ((i >> 8) & 0xFF) + "." +
	// ((i >> 16) & 0xFF) + "." +
	// ((i >> 24) & 0xFF);
	// }

	public static int ipToInt(String addr)
	{
		String[] addrArray = addr.split("\\.");

		int num = 0;
		for (int i = 0; i < addrArray.length; i++)
		{
			num += ((Integer.parseInt(addrArray[i]) % 256 * Math.pow(256, i)));
		}
		return num;
	}

	private String getGateway(String interf)
	{
		String path = "/system/xbin/ip";
		String cmd = "ip r";
		final File file = new File(path);
		try
		{
			if (file.exists() == true)
			{
				String line;
				String[] out;
				Process p = Runtime.getRuntime().exec(cmd);
				BufferedReader r = new BufferedReader(new InputStreamReader(
						p.getInputStream()));
				while ((line = r.readLine()) != null)
				{
					out = line.split(" ");
					if (out[1].equals("via")
							&& out[out.length - 1].equals(interf))
						return out[2];
				}
			}
		} catch (Exception e)
		{
			Log.e(LOG_TAG, "Can't use native command: " + e.getMessage());
		}
		return null;
	}

	private String getIPNetMask(String interf)
	{
		String path = "/system/xbin/ip";
		String cmd = "ip -f inet addr show";
		final File file = new File(path);
		try
		{
			if (file.exists() == true)
			{
				String line;
				String[] out;
				Process p = Runtime.getRuntime().exec(cmd);
				BufferedReader r = new BufferedReader(new InputStreamReader(
						p.getInputStream()));
				while ((line = r.readLine()) != null)
				{
					out = line.split(": ");
					if (out[1].equals(interf))
					{
						line = r.readLine().trim();
						out = line.split(" ");
						return out[1];
					} else
						r.readLine();
				}
			}

		} catch (Exception e)
		{
			Log.e(LOG_TAG, "Can't use native command: " + e.getMessage());
		}
		return null;
	}

	private String getPrefix(String s_ip, String s_netmask)
	{
		int ip = Integer.parseInt(s_ip);
		int netmask = Integer.parseInt(s_netmask);

		int intNetmask = 0;
		int i = 0;
		for (; i < netmask && i < 8; i++)
		{
			intNetmask = intNetmask | (1 << (7 - i));
		}
		for (; i < netmask && i < 16; i++)
		{
			intNetmask = intNetmask | (1 << (8 + 15 - i));
		}
		for (; i < netmask && i < 24; i++)
		{
			intNetmask = intNetmask | (1 << (16 + 23 - i));
		}
		for (; i < netmask && i < 32; i++)
		{
			intNetmask = intNetmask | (1 << (24 + 31 - i));
		}

		int temp = 0;
		temp = ip & intNetmask;

		String toBeReturned = ipFromInt(temp);
		return toBeReturned;
	}

	public static String ipFromInt(int intIP)
	{
		byte[] byteIP = new byte[] { (byte) ((intIP & 0x000000ff)),
				(byte) ((intIP & 0x0000ff00) >> 8),
				(byte) ((intIP & 0x00ff0000) >> 16),
				(byte) ((intIP & 0xff000000) >> 24) };
		try
		{
			return Inet4Address.getByAddress(byteIP).getHostAddress();
		} catch (UnknownHostException e)
		{
			return "";
		}
	}
}