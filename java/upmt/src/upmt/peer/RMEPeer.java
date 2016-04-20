package upmt.peer;

import java.io.IOException;

import upmt.server.rme.RMEServer;
import upmt.server.rme.RMEsbc;

public class RMEPeer {

	private static String[] param;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		param=args;
		
		try {
			Runtime.getRuntime().exec("sudo service network-manager stop");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		Thread server = new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				RMEServer.startRMEServer(param);
			}
		}, "Server RME Thread");

		server.start();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		RMEsbc.main(args);
		
		/* OLD Implementation using Thread
		Thread sbc = new Thread(new Runnable() {

			@Override
			public void run() {
				RMEsbc.startRMEsbc(param);
			}
		}, "SBC RME Thread");
		
		sbc.start();
		*/
		
	}

}
