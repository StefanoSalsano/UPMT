package upmt.client.application.monitor.impl;

import java.util.Hashtable;
import java.util.Vector;

import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.exceptions.DBusException;

import upmt.client.application.monitor.ApplicationMonitor;
import upmt.client.application.monitor.ApplicationMonitorListener;
import upmt.client.application.monitor.impl.dbus.AppMonitorProxy;
import upmt.client.core.Socket;

/** Network monitor for Linux. Require DBus. */
public class DBusApplicationMonitor implements DBusSigHandler<AppMonitorProxy.Signal>, ApplicationMonitor
{
	private static final String PROXY_NAME = AppMonitorProxy.class.getCanonicalName();
	private static final String PROXY_PATH = "/"+PROXY_NAME.replace('.', '/');	

	/** The listener for the application-or-socket change event */
	private ApplicationMonitorListener listener = null;

	/** The connection to DBus. Used to interrogate and receive signal from the AppMonitorProxy */
	private DBusConnection connection = null;

	/** The AppMonitorProxy instance */
	private AppMonitorProxy proxy = null;

	/**
	 * hash table that contains for each application the vector of sockets
	 */
	private Hashtable<String,Vector<Socket>> socketForApp;
	private Hashtable<String,String> appForSocket;


	public DBusApplicationMonitor()
	{
		socketForApp = new Hashtable<String, Vector<Socket>>();
		appForSocket = new Hashtable<String, String>();

		try
		{
			connection = DBusConnection.getConnection(DBusConnection.SYSTEM);
			proxy = connection.getRemoteObject(PROXY_NAME, PROXY_PATH, AppMonitorProxy.class);
		}
		catch (DBusException De)
		{
			System.err.println("Could not connect to bus");
			De.printStackTrace();
		}
	}

	public void startListen(ApplicationMonitorListener listener)
	{
		this.listener = listener;
		
		if (listener!=null && connection!=null && proxy!=null)
			try {connection.addSigHandler(AppMonitorProxy.Signal.class, proxy, this);}
			catch (DBusException e) {e.printStackTrace();}
	}

	public void stop()
	{
		this.listener = null;
		
		if(connection!=null && proxy!=null)
			try {connection.removeSigHandler(AppMonitorProxy.Signal.class, proxy, this);}
			catch (DBusException e) {e.printStackTrace();}		
	}

	/** Process the signal from DBus and notify to the listener the change */
	public synchronized void handle(AppMonitorProxy.Signal sig) {
		if (sig instanceof AppMonitorProxy.SocketOpened) {
			AppMonitorProxy.SocketOpened signal = (AppMonitorProxy.SocketOpened)sig;

			if(socketForApp.containsKey(signal.appName)) {
				// the application already had some open socket
				socketForApp.get(signal.appName).add(signal.socket);
				listener.socketOpened(signal.appName, signal.socket);
			} else {
				// the application did not have any open sockets controlled by UPMT
				Vector<Socket> socketList = new Vector<Socket>();
				socketList.add(signal.socket);
				socketForApp.put(signal.appName, socketList);
				appForSocket.put(signal.socket.id(), signal.appName);
				listener.appOpened(signal.appName);
				listener.socketOpened(signal.appName, signal.socket);
			}
		} else if (sig instanceof AppMonitorProxy.SocketClosed) {
			AppMonitorProxy.SocketClosed signal = (AppMonitorProxy.SocketClosed)sig;

			if(!appForSocket.containsKey(signal.socket.id())) {
				System.err.println("DBusAppMonitor ERROR!!!\n Closing a non-listed socket");return;
			}

			String appName = appForSocket.get(signal.socket.id());
			if(!socketForApp.containsKey(appName))
				{System.err.println("DBusAppMonitor ERROR!!!\n Closing a non-listed socket");return;}

			Vector<Socket> socketList = socketForApp.get(appName);
			if(!socketList.contains(signal.socket))
				{System.err.println("DBusAppMonitor ERROR!!!\n Closing a non-listed socket");return;}

			socketList.remove(signal.socket);

			if(socketList.size()==0) {
				listener.appClosed(appName);
			} else {
				listener.socketClosed(signal.socket);
			}
		}
	}

	public void setApp(String appName, int tunnelID)
	{
		if(connection!=null)
			try {
				connection.sendSignal(new AppMonitorProxy.SetApp(PROXY_PATH,appName,tunnelID));
			}
			catch (DBusException e) {e.printStackTrace();}
	}

	public void rmApp(String appName)
	{
		if(connection!=null)
			try {connection.sendSignal(new AppMonitorProxy.RmApp(PROXY_PATH,appName));}
			catch (DBusException e) {e.printStackTrace();}
	}

	public void setDefault(int tunnelID)
	{
		if(connection!=null)
			try {connection.sendSignal(new AppMonitorProxy.SetDefault(PROXY_PATH,tunnelID));}
			catch (DBusException e) {e.printStackTrace();}
	}

	public void flushAppList()
	{
		if(connection!=null)
			try {connection.sendSignal(new AppMonitorProxy.FlushList(PROXY_PATH));}
			catch (DBusException e) {e.printStackTrace();}
	}

	public void setNoUpmtApp(String appName) {}
	public void rmNoUpmtApp(String appName) {}
	public void flushNoUpmtList() {}
	public void setAppFlow(String dstIp, int tunnelID) {}
	public void rmAppFlow(String dstIp, int port) {}
	public void flushAppFlowList() {}

}
