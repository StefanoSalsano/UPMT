package upmt.client.application.manager.impl;

import java.util.Enumeration;
import java.util.Hashtable;

import upmt.client.UPMTClient;
import upmt.client.application.manager.ApplicationManagerListener;
import upmt.client.core.InterfaceInfo;

public class AutomaticApplicationManager extends Thread //implements ApplicationManager
{
	private ApplicationManagerListener listener;
	private int wifi_threshold;
	private int umts_threshold;
	
	/** Polling time in ms */
	private long pollingTime = 10000;
	
	private InterfaceInfo chooseRoute(Hashtable<String, InterfaceInfo> interfaces) {
		printLog("chooseRoute(): choosing network interface to use", 2);
		InterfaceInfo chosenInterface = null;
		Enumeration<InterfaceInfo> e = interfaces.elements();

		while (e.hasMoreElements()) {
			InterfaceInfo wrapper = (InterfaceInfo) e.nextElement(); // start with element eth1: wi-fi
			if (chosenInterface == null) {// la prima volta
				chosenInterface = wrapper; // eth1 wi-fi
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
							continue;
						}
						printLog("chooseRoute(): discarding " + wrapper.id + " and choosing " + chosenInterface.id, 2);
					} else {
						printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id, 2);
						chosenInterface = wrapper;
						continue;
					}
				}
				else if (chosenInterface.isUMTS()) {
					if (wrapper.isWifi()) {
						if (this.wifi_threshold == 0) {
							printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id + " (Wi-Fi always chosen)", 2);
							chosenInterface = wrapper;
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

						//if (wrapper.getSigLevel() > 2 && chosenInterface.getSigLevel() < 4) { chosenInterface = wrapper; continue; }
					} else if (wrapper.isUMTS()) {
						if (wrapper.getSigLevel() > chosenInterface.getSigLevel()) {
							printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id, 2);
							chosenInterface = wrapper;
							continue;
						}
					} else {
						printLog("chooseRoute(): discarding " + chosenInterface.id + " and choosing " + wrapper.id, 2);
						chosenInterface = wrapper;
						continue;
					}
				} else {
					printLog("chooseRoute(): discarding " + wrapper.id + " and choosing " + chosenInterface.id, 2);
					System.out.println("chooseRoute(): discarding " + wrapper.id + " and choosing " + chosenInterface.id);
					// chosenInterface is not a wireless interface, then we temporarily
					// assume it is already the best interface without comparing the IP QoS
					// measures with wrapper
					// TODO -> IP QoS CHECKING...
					continue;
				}

				/*
				 * if (!chosenInterface.isWifi() && !chosenInterface.isUMTS()) { if (wrapper.isWifi() || wrapper.isUMTS()) continue; // TODO -> IP QoS CHECKING... }
				 * 
				 * 
				 * if (chosenInterface.isWifi() && wrapper.isWifi()) { if (wrapper.getSigLevel() > chosenInWirelessProberterface.getSigLevel()) { chosenInterface = wrapper;
				 * continue; } }
				 * 
				 * if (chosenInterface.isUMTS() && wrapper.isWifi()) {
				 * 
				 * 
				 * chosenInterface = wrapper; continue; }
				 * 
				 * if (chosenInterface.isWifi() && wrapper.isUMTS()) { // temporarily do nothing... }
				 */
			}
		}

		return chosenInterface;
	}
	
	public void run() {
		while (true) {
			//listener.setInterfaceToUse(chooseRoute(listener.getInterfacesList()));
			try {Thread.sleep(pollingTime);}
			catch (InterruptedException e) {e.printStackTrace();}
		}
	}

	public void startListen(ApplicationManagerListener listener) {
		this.listener = listener;
	}
	
	private void printLog(String text, int logLevel) {
		UPMTClient.printGenericLog(this, text, logLevel);
	}
}
