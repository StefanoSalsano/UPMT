package upmt.client.rme;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import upmt.client.UPMTClient;

public class OLSRChecker implements Runnable {

	public static long millisToWait = 5000;
	private boolean isRunning;

	private Thread olsrCheckerThread;

	public OLSRChecker() {
		this.olsrCheckerThread = new Thread(this, "OLSRChecker Thread");
		this.isRunning = false;
	}

	public void init() {
		this.isRunning = true;
		this.olsrCheckerThread.start();
	}

	public void stop() {
		this.isRunning = false;
	}

	@Override
	public void run() {

		while(isRunning) {
			try {
				Thread.sleep(15000);
				Process p = Runtime.getRuntime().exec("ps -lC olsrd");
				BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
				boolean check = true;
				br.readLine();
				while(check) {
					String line = br.readLine();
					if(line != null) {
						String[] splittedLine = line.split("\\s+");
						if(splittedLine.length >= 14) {
							if(splittedLine[13].trim().equals("olsrd") && !splittedLine[10].trim().equals("poll_s")) {
								Runtime.getRuntime().exec("sudo killall olsrd");
								Thread.sleep(1000);
								for(String ifName: UPMTClient.getRMEInterfacesList()) {
									UPMTClient.runOlsrd(ifName);
								}
							}
						}
					}
					else {
						check = false;
					}
				}
				Thread.sleep(millisToWait);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public Thread getOlsrCheckerThread() {
		return olsrCheckerThread;
	}

	public void setOlsrCheckerThread(Thread olsrCheckerThread) {
		this.olsrCheckerThread = olsrCheckerThread;
	}

}
