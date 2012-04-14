package upmt.client.application.interfPolicy.policy;

import java.util.Enumeration;
import java.util.Hashtable;

import org.zoolu.tools.Logger;

import upmt.client.core.InterfaceInfo;

public class TestPolicy extends GenericPolicy
{
	private int wifi_threshold = 2;
	private int umts_threshold = 5;
	
	/** Object to perform logging */
	private Logger erLog;

	public boolean setParam(String[] param) {
		if (param.length != 2) return false;
		try {
			wifi_threshold = Integer.parseInt(param[0]);
			umts_threshold = Integer.parseInt(param[1]);
		}
		catch (NumberFormatException e) {return false;}
		return true;
	}

	public String getActiveInterf(Hashtable<String, InterfaceInfo> ifList, String actualInterf, int event) {
		return chooseInterf(ifList);
	}

	private String chooseInterf(Hashtable<String, InterfaceInfo> interfaces) {
		printLog("chooseRoute(): choosing network interface to use", 2);
		InterfaceInfo chosenInterface = null;
		String chosenInterfName = null;
		Enumeration<String> names = interfaces.keys();

		while (names.hasMoreElements()) {
			String ifName = names.nextElement();
			InterfaceInfo wrapper = (InterfaceInfo) interfaces.get(ifName); // start with element eth1: wi-fi
			
			if (chosenInterface == null) { // la prima volta
				chosenInterface = wrapper; // eth1 wi-fi
				chosenInterfName = ifName;
				printLog("chooseRoute(): first iteration, interface chosen is " + chosenInterface.id, 2);
				System.out.println("chooseRoute(): first iteration, interface chosen is " + chosenInterface.id);
				continue; // termina l'interazione corrente
			} else {
				printLog("chooseRoute(): comparing " + chosenInterface.id + " with " + wrapper.id, 2);
				System.out.println("chooseRoute(): comparing " + chosenInterface.id + " with " + wrapper.id);
				if (chosenInterface.isWifi()) {
					if (wrapper.isWifi()) {
						if (wrapper.getSigLevel() > chosenInterface.getSigLevel()) {
							printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id, 2);
							chosenInterface = wrapper;
							chosenInterfName = ifName;
							continue;
						}
						printLog("chooseRoute(): discarding " + wrapper.id + " and choosing " + chosenInterface.id, 2);
					} else if (wrapper.isUMTS()) {
						if (this.wifi_threshold == 0) {
							printLog("chooseRoute(): discarding " + wrapper.id + " and choosing " + chosenInterface.id + " (Wi-Fi always chosen)", 2);
							continue;
						}
						if (wrapper.getSigLevel() > this.umts_threshold && chosenInterface.getSigLevel() < this.wifi_threshold) {
							printLog("chooseRoute(): UMTS sig level > UMTS threshold (" + wrapper.getSigLevel() + " > " + this.umts_threshold
									+ ") AND Wi-Fi sig level < Wi-Fi threshold (" + chosenInterface.getSigLevel() + " < " + this.wifi_threshold, 2);
							printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id, 2);
							chosenInterface = wrapper;
							chosenInterfName = ifName;
							continue;
						}
						printLog("chooseRoute(): discarding " + wrapper.id + " and choosing " + chosenInterface.id, 2);
					} else {
						printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id, 2);
						chosenInterface = wrapper;
						chosenInterfName = ifName;
						continue;
					}
				}
				else if (chosenInterface.isUMTS()) {
					if (wrapper.isWifi()) {
						if (this.wifi_threshold == 0) {
							printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id + " (Wi-Fi always chosen)", 2);
							chosenInterface = wrapper;
							chosenInterfName = ifName;
							continue;
						}

						if (chosenInterface.getSigLevel() > this.umts_threshold && wrapper.getSigLevel() < this.wifi_threshold) {
							printLog("chooseRoute(): UMTS sig level > UMTS threshold (" + chosenInterface.getSigLevel() + " > " + this.umts_threshold
									+ ") AND Wi-Fi sig level < Wi-Fi threshold (" + wrapper.getSigLevel() + " < " + this.wifi_threshold, 2);

							printLog("chooseRoute(): discarding " + wrapper.id + " and choosing " + chosenInterface.id, 2);
							// chosenInterface = wrapper;
							continue;
						}

						printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id, 2);
						chosenInterface = wrapper;
						chosenInterfName = ifName;

						//if (wrapper.getSigLevel() > 2 && chosenInterface.getSigLevel() < 4) { chosenInterface = wrapper; continue; }
					} else if (wrapper.isUMTS()) {
						if (wrapper.getSigLevel() > chosenInterface.getSigLevel()) {
							printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id, 2);
							chosenInterface = wrapper;
							chosenInterfName = ifName;
							continue;
						}
					} else {
						printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id, 2);
						chosenInterface = wrapper;
						chosenInterfName = ifName;
						continue;
					}
				} else {
					printLog("chooseRoute(): discarding " + wrapper.id + " and choosing " + chosenInterface.id, 2);
					System.out.println("chooseRoute(): discarding " + wrapper.id + " and choosing " + chosenInterface.id);
					// chosenInterface is not a wireless interface, then we temporarily assume it is already the best interface without
					// comparing the IP QoS measures with wrapper TODO -> IP QoS CHECKING...
					continue;
				}

				/*
				 * if (!chosenInterface.isWifi() && !chosenInterface.isUMTS() && (wrapper.isWifi() || wrapper.isUMTS())) continue;
				 * //TODO: IP QoS CHECKING...
				 * 
				 * if (chosenInterface.isWifi() && wrapper.isWifi() && wrapper.getSigLevel() > chosenInWirelessProberterface.getSigLevel())
				 * 		{chosenInterface = wrapper; continue;}
				 * if (chosenInterface.isUMTS() && wrapper.isWifi()) {chosenInterface = wrapper; continue;}
				 * if (chosenInterface.isWifi() && wrapper.isUMTS()) { // temporarily do nothing... }
				 */
			}
		}

		return chosenInterfName;
	}

	public String getDesc()
	{
		return null;
	}

	/** Logging methods */
	private void printLog(String text, int loglevel)
	{erLog.println("MobileManager: " + text, loglevel);}

	public boolean isTriggeredBy(int event)
	{
		return false;
	}
}
