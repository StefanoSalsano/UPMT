package upmt.client.network.impl;

import org.freedesktop.dbus.*;
import org.freedesktop.dbus.exceptions.*;
import java.util.*;

@DBusInterfaceName("org.freedesktop.Networking")
public interface Networking extends DBusInterface {

    public UInt32 state();

    public void wake();

    public List<DBusInterface> GetDevices();

    public Path ActivateConnection(String serviceName, DBusInterface conn, DBusInterface dev, DBusInterface obj);

    public void DeactivateConnection(DBusInterface activeConn);

    public void Sleep(boolean sleep);

    public void sleep();

    public static class NetStateChanged extends DBusSignal {

        public UInt32 state;

        public NetStateChanged(String path, UInt32 state) throws DBusException {
            super(path, state);
            this.state = state;
        }
    }

    public static class NetPropertiesChanged extends DBusSignal {

        public Map<String, Variant> properties;

        public NetPropertiesChanged(String path, Map<String, Variant> properties) throws DBusException {
            super(path, properties);
            this.properties = properties;
        }
    }

    public static class NetDeviceAdded extends DBusSignal {

        public DBusInterface objPath;

        public NetDeviceAdded(String path, DBusInterface objPath) throws DBusException {
            super(path);
            this.objPath = objPath;
        }
    }

    public static class NetDeviceRemoved extends DBusSignal {

        public DBusInterface objPath;

        public NetDeviceRemoved(String path, DBusInterface objPath) throws DBusException {
            super(path);
            this.objPath = objPath;
        }
    }

    @DBusInterfaceName("org.freedesktop.NetworkInterface.AccessPoint")
    public interface NetAccessPoint extends DBusInterface {

        public static class NetPropertiesChanged extends DBusSignal {

            public Map<String, Variant> properties;

            public NetPropertiesChanged(String path, Map<String, Variant> properties) throws DBusException {
                super(path, properties);
                this.properties = properties;
            }
        }
    }

    @DBusInterfaceName("org.freedesktop.NetworkInterface.Device")
    public interface NetDevice extends DBusInterface {

        public static class NetStateChanged extends DBusSignal {

            public UInt32 newState;
            public UInt32 oldState;
            public UInt32 reason;

            public NetStateChanged(String path, UInt32 newState, UInt32 oldState, UInt32 reason) throws DBusException {
                super(path, newState, oldState, reason);
                this.newState = newState;
                this.oldState = oldState;
                this.reason = reason;
            }
        }

        @DBusInterfaceName("org.freedesktop.NetworkInterface.Device.Wired")
        public interface NetWired extends DBusInterface {

            public static class NetPropertiesChanged extends DBusSignal {

                public Map<String, Variant> properties;

                public NetPropertiesChanged(String path, Map<String, Variant> properties) throws DBusException {
                    super(path, properties);
                    this.properties = properties;
                }
            }
        }

        @DBusInterfaceName("org.freedesktop.NetworkInterface.Device.Wireless")
        public interface NetWireless extends DBusInterface {

            public List<DBusInterface> GetAccessPoints();

            public static class NetPropertiesChanged extends DBusSignal {

                public Map<String, Variant> properties;

                public NetPropertiesChanged(String path, Map<String, Variant> properties) throws DBusException {
                    super(path, properties);
                    this.properties = properties;
                }
            }

            public static class NetAccessPointAdded extends DBusSignal {

                public DBusInterface ap;

                public NetAccessPointAdded(String path, DBusInterface ap) throws DBusException {
                    super(path, ap);
                    this.ap = ap;
                }
            }

            public static class NetAccessPointRemoved extends DBusSignal {

                public DBusInterface ap;

                public NetAccessPointRemoved(String path, DBusInterface ap) throws DBusException {
                    super(path, ap);
                    this.ap = ap;
                }
            }
        }

        @DBusInterfaceName("org.freedesktop.NetworkInterface.Device.Cdma")
        public interface NetCDMA extends DBusInterface {

            public static class NetPropertiesChanged extends DBusSignal {

                public Map<String, Variant> properties;

                public NetPropertiesChanged(String path, Map<String, Variant> properties) throws DBusException {
                    super(path, properties);
                    this.properties = properties;
                }
            }
        }

        @DBusInterfaceName("org.freedesktop.NetworkInterface.Device.Gsm")
        public interface NetGSM extends DBusInterface {

            public static class NetPropertiesChanged extends DBusSignal {

                public Map<String, Variant> properties;

                public NetPropertiesChanged(String path, Map<String, Variant> properties) throws DBusException {
                    super(path, properties);
                    this.properties = properties;
                }
            }
        }

        @DBusInterfaceName("org.freedesktop.NetworkInterface.Device.Serial")
        public interface NetSerial extends DBusInterface {

            public static class NetPppStats extends DBusSignal {

                public UInt32 rxBytes;
                public UInt32 txBytes;

                public NetPppStats(String path, UInt32 rxBytes, UInt32 txBytes) throws DBusException {
                    super(path, rxBytes, txBytes);
                    this.rxBytes = rxBytes;
                    this.txBytes = txBytes;
                }
            }
        }
    }

    @DBusInterfaceName("org.freedesktop.NetworkInterface.IP4Config")
    public interface NetIP4Config extends DBusInterface {
    }
    
    @DBusInterfaceName("org.freedesktop.NetworkInterface.DHCP4Config")
    public interface NetDHCP4Config extends DBusInterface {
        
        public static class NetPropertiesChanged extends DBusSignal {

                public Map<String, Variant> properties;

                public NetPropertiesChanged(String path, Map<String, Variant> properties) throws DBusException {
                    super(path, properties);
                    this.properties = properties;
                }
            }
        
    }

    @DBusInterfaceName("org.freedesktop.NetworkInterface.Connection")
    public interface NetConnection extends DBusInterface {

        @DBusInterfaceName("org.freedesktop.NetworkInterface.Connection.Active")
        public interface NetActive extends DBusInterface {

            public static class NetPropertiesChanged extends DBusSignal {

                public Map<String, Variant> properties;

                public NetPropertiesChanged(String path, Map<String, Variant> properties) throws DBusException {
                    super(path, properties);
                    this.properties = properties;
                }
            }
        }
    }

    @DBusInterfaceName("org.freedesktop.NetworkInterface.VPN")
    public interface NetVPN extends DBusInterface {

        @DBusInterfaceName("org.freedesktop.NetworkInterface.VPN.Connection")
        public interface NetConnection extends DBusInterface {

            public static class NetPropertiesChanged extends DBusSignal {

                public Map<String, Variant> properties;

                public NetPropertiesChanged(String path, Map<String, Variant> properties) throws DBusException {
                    super(path, properties);
                    this.properties = properties;
                }
            }

            public static class NetVpnStateChanged extends DBusSignal {

                public UInt32 state;
                public UInt32 reason;

                public NetVpnStateChanged(String path, UInt32 state, UInt32 reason) throws DBusException {
                    super(path, state, reason);
                    this.state = state;
                    this.reason = reason;
                }
            }
        }

        @DBusInterfaceName("org.freedesktop.NetworkInterface.VPN.Plugin")
        public interface NetPlugin extends DBusInterface {

            public void Connect(Map<String, Map<String, Variant>> connection);

            public String NeedSecrets(Map<String, Map<String, Variant>> connection);

            public void Disconnect();

            public void SetIp4Config(Map<String, Variant> config);

            public void SetFailure(String reason);

            public static class NetStateChanged extends DBusSignal {

                public UInt32 state;

                public NetStateChanged(String path, UInt32 state) throws DBusException {
                    super(path, state);
                    this.state = state;
                }
            }

            public static class NetIp4Config extends DBusSignal {

                public Map<String, Variant> ip4config;

                public NetIp4Config(String path, Map<String, Variant> ip4config) throws DBusException {
                    super(path, ip4config);
                    this.ip4config = ip4config;
                }
            }

            public static class NetLoginBanner extends DBusSignal {

                public String banner;

                public NetLoginBanner(String path, String banner) throws DBusException {
                    super(path, banner);
                    this.banner = banner;
                }
            }

            public static class NetFailure extends DBusSignal {

                public UInt32 reason;

                public NetFailure(String path, UInt32 reason) throws DBusException {
                    super(path, reason);
                    this.reason = reason;
                }
            }
        }
    }
}