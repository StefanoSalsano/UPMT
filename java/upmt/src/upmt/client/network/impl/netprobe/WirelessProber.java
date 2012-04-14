/*
* MMUSE - https://netgroup.uniroma2.it/twiki/bin/view.cgi/Netgroup/MMUSEProject
* Copyright (C) 2004  by:
* by Luca Veltri - University of Parma - Italy
* Andrea Polidoro - University of Rome "Tor Vergata" - Italy
* Stefano Salsano - University of Rome "Tor Vergata" - Italy
*
* MMUSE is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* MMUSE is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with MMUSE; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*
* Author(s):
* Luca Veltri (luca.veltri@unipr.it)
* Andrea Polidoro (andrea.polidoro@uniroma2.it)
* Stefano Salsano (stefano.salsano@uniroma2.it)
*/
package upmt.client.network.impl.netprobe;

public class WirelessProber
{
    static
    {
    	System.out.print("Loading WirelessProber library...");
        System.loadLibrary("WirelessProber");
        System.out.println(" library successfully loaded!");
    }
    
    /**
	 * Retrieve Wi-Fi devices detected by the Windows WMI
	 * @return a string containing the names of the Wi-Fi devices detected by
	 * the Windows WMI. These names are separated by a pipe character "|"
	 */
	public native String WiFiDevices ();
    
	/**
	 * WiFi network interface signal level
	 * @parm deviceName Wi-Fi interface device name
	 * @return the signal level in a range between 0 and 5
	 */
	public native int WiFiLevel(String deviceName);
	
	/**
	 * UMTS network interface signal level
	 * @parm portNumber serial port number to which the device is connected
	 * @return the signal level in a range between 0 and 5
	 */
	public native int UMTSLevel(int portNumber);
	
	/**
	 * GSM network interface signal level
	 * @parm portNumber serial port number to which the device is connected
	 * @return the signal level in a range between 0 and 5
	 */
	public native int GSMLevel(int portNumber);
}
