package upmt.client.application.monitor.impl.dbus;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.exceptions.DBusException;

import upmt.client.core.Socket;

public interface AppMonitorProxy extends DBusInterface
{
	static abstract class Signal extends DBusSignal
	{protected Signal(String objectpath, Object... args) throws DBusException {super(objectpath, args);}}

	public static class SetApp extends Signal
	{
		public final String appName;
		public final int tunnelID;
		public SetApp(String path, String appName, int tunnelID) throws DBusException
		{
			super(path, appName, tunnelID);
			this.appName = appName;
			this.tunnelID = tunnelID;
		}
	}
	public static class RmeSetAppAnVipa extends Signal
	{
		public final String appName;
		public final String VIPA;
		public final int tunnelID;
		public RmeSetAppAnVipa(String path, String appName, String VIPA, int tunnelID) throws DBusException
		{
			super(path, VIPA, appName, tunnelID);
			this.appName = appName;
			this.VIPA = VIPA;
			this.tunnelID = tunnelID;
		}
	}
	public static class RmApp extends Signal
	{
		public final String appName;
		public RmApp(String path, String appName) throws DBusException
		{
			super(path, appName);
			this.appName = appName;
		}
	}
	public static class SetDefault extends Signal
	{
		public final int tunnelID;
		public SetDefault(String path, int tunnelID) throws DBusException
		{
			super(path, tunnelID);
			this.tunnelID = tunnelID;
		}
	}
	public static class FlushList extends Signal
	{
		public FlushList(String path) throws DBusException
		{
			super(path);
		}
	}
	public static class SocketOpened extends Signal
	{
		public final Socket socket;
		public final String appName;
		public final int tunnelID;
		public SocketOpened(String path, Socket socket, String appName, int tunnelID) throws DBusException
		{
			super(path, socket, appName, tunnelID);
			this.socket = socket;
			this.appName = appName;
			this.tunnelID = tunnelID;
		}
	}
	public static class SocketClosed extends Signal
	{
		public final Socket socket;
		public SocketClosed(String path, Socket socket) throws DBusException
		{
			super(path, socket);
			this.socket = socket;
		}
	}
}
