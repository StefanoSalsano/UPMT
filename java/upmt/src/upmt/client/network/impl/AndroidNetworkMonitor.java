//#ifdef ANDROID
//package upmt.client.network.impl;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.InputStreamReader;
//import java.net.Inet4Address;
//import java.net.InetAddress;
//import java.net.NetworkInterface;
//import java.net.SocketException;
//import java.net.UnknownHostException;
//import java.util.Arrays;
//import java.util.Enumeration;
//import java.util.Hashtable;
//
//import upmt.client.UPMTClient;
//import upmt.client.core.InterfaceInfo;
//import upmt.client.network.NetworkMonitor;
//import upmt.client.network.NetworkMonitorListener;
//import upmt.os.Shell;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.net.ConnectivityManager;
//import android.net.NetworkInfo;
//import android.net.wifi.WifiManager;
//import android.util.Log;
//import android.widget.Toast;
//
//public class AndroidNetworkMonitor implements NetworkMonitor
//{
//	private static final String LOG_TAG = "Android Network Monitor";
//
//	/** The listener for the Network Interface change event */
//	private NetworkMonitorListener listener;
//
//	/** List of the interfaces out of UPMT control */
//	private String[] ifToSkip = new String[] {};
//
//	private Hashtable<String, InterfaceInfo> handlerList;
//
//	BroadcastReceiver receiver;
//	final ConnectivityManager connectivity;
//	NetworkInfo[] Networks = null;
//	int networkType;
//
//	final static int TYPE_USB = 99;
//	
//	Thread usbconf = null;
//
//	public AndroidNetworkMonitor()
//	{
//		String service = Context.CONNECTIVITY_SERVICE;
//		connectivity = (ConnectivityManager) UPMTClient.getContext()
//				.getSystemService(service);
//	}
//
//	public Hashtable<String, InterfaceInfo> getInterfaceList()
//	{
//		Hashtable<String, InterfaceInfo> ret = new Hashtable<String, InterfaceInfo>();
//		Networks = connectivity.getAllNetworkInfo();
//		
//		try{
//			System.out.println("***********************************************************************************************");
//
//			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
//			{
//				NetworkInterface intf = en.nextElement();
//
//				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
//				{
//					InetAddress inetAddress = enumIpAddr.nextElement();
//					System.out.println("*    AndroidNetworkMonitor() intf: " + intf.getName() + " ----> ip: " + inetAddress.getHostAddress().toString());
//				}
//			}
//			System.out.println("***********************************************************************************************");
//
//		}
//		catch(Exception e){
//			e.printStackTrace();
//		}
//
//		if (Networks.length != 0)
//		{
//			for (int i = 0; i < Networks.length; i++)
//			{
//				if (!Networks[i].isConnectedOrConnecting())
//					continue;
//
//				String netName = Networks[i].getType() == ConnectivityManager.TYPE_MOBILE?"rmnet0":"eth0";
//
//				addDevice(netName, Networks[i].getType(), ret, false);
//			}
//
//		}
//
//		return ret;
//	}
//
//	private boolean deviceToBeSkipped(String devName)
//	{
//		return Arrays.asList(ifToSkip).contains(devName);
//	}
//
//	public void startListen(NetworkMonitorListener listener)
//	{
//		this.listener = listener;
//		this.handlerList = new Hashtable<String, InterfaceInfo>();
//
//		if (listener != null)
//		{
//			UPMTClient.getContext().registerReceiver(receiver = new BroadcastReceiver()
//			{
//				@Override
//				public void onReceive(Context context, Intent intent)
//				{
//					NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
//					String netName = info.getType() == ConnectivityManager.TYPE_MOBILE?"rmnet0":"eth0";
//					if (info.getState() == NetworkInfo.State.CONNECTED)
//					{
//						if(!handlerList.containsKey(netName))
//							addDevice(netName, info.getType(), null, true);
//					}
//					else if (info.getState() == NetworkInfo.State.DISCONNECTED)
//					{
//						rmvDevice(netName, true);
//					}
//				}
//			}, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
//
//			Networks = connectivity.getAllNetworkInfo();
//
//			if (Networks.length != 0)
//			{
//				for (int i = 0; i < Networks.length; i++)
//				{
//					if (!Networks[i].isConnectedOrConnecting())
//						continue;
//
//					//					String netName = Networks[i].getTypeName();
//					String netName = Networks[i].getType() == ConnectivityManager.TYPE_MOBILE?"rmnet0":"eth0";
//					addDevice(netName, Networks[i].getType(), null, false);
//				}
//
//			}
//		}
//
//
//	//usb configuration thread
//        usbconf = new Thread(){
//            public void run(){
//               
//                boolean added = false;
//               
//                String result = Shell.executeCommand(new String[]{"uname", "-r"});
//               
//                int scale = 1;
//
//                while(true && result.contains("usb")){
//                   
//                    String ipAddr = getLocalIpAddress("usb0");
//                    String res = Shell.executeRootCommand(new String[] {"ping -c 1 -I usb0 www.google.com"});
//
//                   
//                    System.out.println("usb0 check: added? " + added + " ip? " + ipAddr + " ping? " + res);
//
//                    if(ipAddr != null && res.contains("rtt") && !added){
//                        addDevice("usb0", AndroidNetworkMonitor.TYPE_USB, null, true);
//                        added = true;
//                    }
//                    else if(ipAddr == null){
//                        Shell.executeRootCommand(new String[] {"dhcpcd", "usb0"});
//                        if(added)
//                            rmvDevice("usb0", false);
//                        added = false;
//                        scale = 1;
//                    }
//                    else
//                        scale++;
//
//                    try {
//                        Thread.sleep(1000 * scale);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//
//            }
//        };
//
//        usbconf.start();
//
//	}
//
//	public void stop()
//	{
//		UPMTClient.getContext().unregisterReceiver(receiver);
//		usbconf.stop();
//	}
//
//	public void setInterfToSkip(String[] interfs)
//	{
//		this.ifToSkip = interfs;
//	}
//
//	//ret passata in getInterfaceList() altrimenti null, interfAdd se deve effettuare la chiamata a interfAdding
//	private synchronized void addDevice(String devName, int type, Hashtable<String, InterfaceInfo> ret, boolean interfAdd)
//	{
//		if(ret == null) 
//			ret = handlerList;
//
//		if(deviceToBeSkipped(devName)) 
//			return;
//
//		System.out.println("[AndroidNetworkMonitor] ADD DEVICE devName: " + devName + ", type: " + type + "<<<<<<<<<<<<<<<<");
//
//		if(type == ConnectivityManager.TYPE_MOBILE)
//		{
//			String ipAddr = getLocalIpAddress("rmnet0");
//			String ip_netmask = getIPNetMask("rmnet0");
//			String[] out = ip_netmask.split("/");
//			//			String netmask = getPrefix(
//			//					Integer.toString(ipToInt(out[0])), out[1]);
//			InterfaceInfo ii = new InterfaceInfo(devName, "", ipAddr,
//					ipToInt(ipAddr),
//					//					Integer.parseInt(netmask),
//					Integer.parseInt(out[1]),
//					getGateway("rmnet0"), "");
//			if(interfAdd) interfAdding(devName, ii);
//			else {
//				ret.put(devName, ii);
//				Toast.makeText(UPMTClient.getContext(), devName + " added to handerlist", Toast.LENGTH_SHORT).show();
//			}
//			return;
//		}
//		else if(type == ConnectivityManager.TYPE_WIFI)
//		{
//			WifiManager wifi = (WifiManager) UPMTClient
//					.getContext().getSystemService(
//							Context.WIFI_SERVICE);
//			//			String ipAddr = getLocalIpAddress("eth0");
//			int ipAddr = wifi.getDhcpInfo().ipAddress;
//			InterfaceInfo ii = new InterfaceInfo(devName, "", ipFromInt(ipAddr),
//					ipAddr,
//					netmaskToCidr(ipFromInt(wifi.getDhcpInfo().netmask)),
//					ipFromInt(wifi.getDhcpInfo().gateway),
//					"");
//			if(interfAdd) interfAdding(devName, ii);
//			else {
//				if(!ret.contains(devName))
//					ret.put(devName, ii);
//				Toast.makeText(UPMTClient.getContext(), devName + " added to handerlist", Toast.LENGTH_SHORT).show();
//			}
//			return;
//		}
//		else if(type == TYPE_USB)
//		{
//			String ipAddr = getLocalIpAddress(devName);
//			String ip_netmask = getIPNetMask(devName);
//			String[] out = ip_netmask.split("/");
//			InterfaceInfo ii = new InterfaceInfo(devName, "", ipAddr,
//					ipToInt(ipAddr),
//					Integer.parseInt(out[1]),
//					getGateway(devName), "");
//
//			ret.put(devName, ii);
//			if(interfAdd) interfAdding(devName, ii);
//			else {
//				ret.put(devName, ii);
//				System.out.println(devName + " added to handerlist");
//			}
//			return;
//		}
//
//
//
//
//	}
//
//	private synchronized void rmvDevice(String devName, boolean toast)
//	{
//		System.out.println("[AndroidNetworkMonitor] REMOVE DEVICE devName: " + devName +  "<<<<<<<<<<<<<<<<");
//		
//		if(deviceToBeSkipped(devName)) return;
//		if(handlerList.containsKey(devName)) handlerList.remove(devName);
//		interfRemoval(devName);
//		if(toast)
//			Toast.makeText(UPMTClient.getContext(), devName + " removed from handerlist", Toast.LENGTH_SHORT).show();
//	}
//
//	private synchronized void interfAdding(String devName, InterfaceInfo info)
//	{listener.onNetworkInterfaceAdding(devName, info);}
//
//	private synchronized void interfRemoval(String devName)
//	{listener.onNetworkInterfaceRemoval(devName);}
//
//	public String getLocalIpAddress(String interf)
//	{
//		try
//		{
//			for (Enumeration<NetworkInterface> en = NetworkInterface
//					.getNetworkInterfaces(); en.hasMoreElements();)
//			{
//				NetworkInterface intf = en.nextElement();
//				for (Enumeration<InetAddress> enumIpAddr = intf
//						.getInetAddresses(); enumIpAddr.hasMoreElements();)
//				{
//					InetAddress inetAddress = enumIpAddr.nextElement();
//					if (intf.getName().equals(interf))
//					{
//						return inetAddress.getHostAddress().toString();
//					}
//				}
//			}
//		} catch (SocketException ex)
//		{
//			Log.e(LOG_TAG, ex.toString());
//		}
//		return null;
//	}
//
//	private String getIPNetMask(String interf)
//	{
//		String path = "/system/xbin/ip";
//		String cmd = "ip -f inet addr show";
//		final File file = new File(path);
//		try
//		{
//			if (file.exists() == true)
//			{
//				String line;
//				String[] out;
//				Process p = Runtime.getRuntime().exec(cmd);
//				BufferedReader r = new BufferedReader(new InputStreamReader(
//						p.getInputStream()));
//				while ((line = r.readLine()) != null)
//				{
//					out = line.split(": ");
//					if (out[1].equals(interf))
//					{
//						line = r.readLine().trim();
//						out = line.split(" ");
//						return out[1];
//					} else
//						r.readLine();
//				}
//			}
//
//		} catch (Exception e)
//		{
//			Log.e(LOG_TAG, "Can't use native command: " + e.getMessage());
//		}
//		return null;
//	}
//
//	private String getPrefix(String s_ip, String s_netmask)
//	{
//		int ip = Integer.parseInt(s_ip);
//		int netmask = Integer.parseInt(s_netmask);
//
//		int intNetmask = 0;
//		int i = 0;
//		for (; i < netmask && i < 8; i++)
//		{
//			intNetmask = intNetmask | (1 << (7 - i));
//		}
//		for (; i < netmask && i < 16; i++)
//		{
//			intNetmask = intNetmask | (1 << (8 + 15 - i));
//		}
//		for (; i < netmask && i < 24; i++)
//		{
//			intNetmask = intNetmask | (1 << (16 + 23 - i));
//		}
//		for (; i < netmask && i < 32; i++)
//		{
//			intNetmask = intNetmask | (1 << (24 + 31 - i));
//		}
//
//		int temp = 0;
//		temp = ip & intNetmask;
//
//		String toBeReturned = ipFromInt(temp);
//		return toBeReturned;
//	}
//
//	private int netmaskToCidr(String netmask)
//	{
//		String out[] = netmask.split("\\.");
//		for(int i = out.length-1; i > -1; i--)
//		{
//			if(out[i].equals("255")) return ((i+1)*8);
//			if(!out[i].equals("0")) return ((i)*8 + Integer.toBinaryString(Integer.parseInt(out[i])).indexOf('0'));
//		}
//		return 0;
//	}
//
//	private String getGateway(String interf)
//	{
//		String path = "/system/xbin/ip";
//		String cmd = "ip r";
//		final File file = new File(path);
//		try
//		{
//			if (file.exists() == true)
//			{
//				String line;
//				String[] out;
//				Process p = Runtime.getRuntime().exec(cmd);
//				BufferedReader r = new BufferedReader(new InputStreamReader(
//						p.getInputStream()));
//				while ((line = r.readLine()) != null)
//				{
//					out = line.split(" ");
//					if (out[1].equals("via")
//							&& out[out.length - 1].equals(interf))
//						return out[2];
//				}
//			}
//		} catch (Exception e)
//		{
//			Log.e(LOG_TAG, "Can't use native command: " + e.getMessage());
//		}
//		return null;
//	}
//
//	public static String ipFromInt(int intIP)
//	{
//		byte[] byteIP = new byte[] {
//				(byte) ((intIP & 0x000000ff)),
//				(byte) ((intIP & 0x0000ff00) >> 8),
//				(byte) ((intIP & 0x00ff0000) >> 16),
//				(byte) ((intIP & 0xff000000) >> 24) };
//		//		byte[] byteIP = new byte[]	{
//		//				(byte) ((intIP & 0xff000000) >> 24),
//		//				(byte) ((intIP & 0x00ff0000) >> 16),
//		//				(byte) ((intIP & 0x0000ff00) >> 8),
//		//				(byte) ((intIP & 0x000000ff))		};
//		try
//		{
//			return Inet4Address.getByAddress(byteIP).getHostAddress();
//		} catch (UnknownHostException e)
//		{
//			return "";
//		}
//	}
//
//	public static int ipToInt(final String addr)
//	{
//		final String[] addressBytes = addr.split("\\.");
//		int ip = 0;
//		for (int i = 0; i < 4; i++)
//		{
//			ip <<= 8;
//			ip |= Integer.parseInt(addressBytes[i]);
//		}
//		return ip;
//	}
//}
//#endif
