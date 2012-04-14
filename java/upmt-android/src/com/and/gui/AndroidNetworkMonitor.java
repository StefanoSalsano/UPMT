package com.and.gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;

import upmt.client.UPMTClient;
import upmt.client.core.InterfaceInfo;
import upmt.client.network.NetworkMonitor;
import upmt.client.network.NetworkMonitorListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ContextWrapper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class AndroidNetworkMonitor implements NetworkMonitor
{
	private static final String LOG_TAG = "Android Network Monitor";

	/** The listener for the Network Interface change event */
	private NetworkMonitorListener listener;

	/** List of the interfaces out of UPMT control */
	private String[] ifToSkip = new String[] {};

	private Hashtable<String, InterfaceInfo> handlerList;

	final ConnectivityManager connectivity;
	NetworkInfo[] Networks = null;
	int networkType;

	public AndroidNetworkMonitor()
	{
		String service = Context.CONNECTIVITY_SERVICE;
		connectivity = (ConnectivityManager) UPMTClient.getContext()
				.getSystemService(service);
	}

	public Hashtable<String, InterfaceInfo> getInterfaceList()
	{
		return null;
	}

	private boolean deviceToBeSkipped(String devName)
	{
		return Arrays.asList(ifToSkip).contains(devName);
	}

	public void startListen(NetworkMonitorListener listener)
	{
		this.listener = listener;
		this.handlerList = new Hashtable<String, InterfaceInfo>();

		if (listener != null)
		{
			UPMTClient.getContext().registerReceiver(
					new BroadcastReceiver()
					{
						@Override
						public void onReceive(Context context, Intent intent)
						{

						}
					},
					new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
			
			Networks = connectivity.getAllNetworkInfo();

			if (Networks.length != 0)
			{
				for (int i = 0; i < Networks.length; i++)
				{
					networkType = Networks[i].getType();
					if (!Networks[i].isConnectedOrConnecting())
						continue;

					String netName = Networks[i].getTypeName();
					if (deviceToBeSkipped(netName))
						continue;

					switch (networkType)
					{
					case (ConnectivityManager.TYPE_MOBILE):
					{
						String ipAddr = getLocalIpAddress("rmnet0");
						String ip_netmask = getIPNetMask("rmnet0");
						String[] out = ip_netmask.split("/");
						String netmask = getPrefix(
								Integer.toString(ipToInt(out[0])), out[1]);
						handlerList.put(
								netName,
								new InterfaceInfo(netName, "", ipAddr,
										ipToInt(ipAddr), Integer
												.parseInt(netmask),
										getGateway("rmnet0"), ""));
						break;
					}
					case (ConnectivityManager.TYPE_WIFI):
					{
						String ipAddr = getLocalIpAddress("eth0");
						WifiManager wifi = (WifiManager) UPMTClient
								.getContext().getSystemService(
										Context.WIFI_SERVICE);
						handlerList.put(netName,
								new InterfaceInfo(netName, "", ipAddr,
										ipToInt(ipAddr),
										wifi.getDhcpInfo().netmask,
										ipFromInt(wifi.getDhcpInfo().gateway),
										""));
						break;
					}
					default:
						break;
					}

				}

			}
		}
	}

	public void stop()
	{
	}

	public void setInterfToSkip(String[] interfs)
	{
		this.ifToSkip = interfs;
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

	public static int ipToInt(final String addr)
	{
		final String[] addressBytes = addr.split("\\.");
		int ip = 0;
		for (int i = 0; i < 4; i++)
		{
			ip <<= 8;
			ip |= Integer.parseInt(addressBytes[i]);
		}
		return ip;
	}
}
