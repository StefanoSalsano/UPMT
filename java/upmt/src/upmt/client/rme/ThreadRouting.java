package upmt.client.rme;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ThreadRouting implements Runnable {
	
	private boolean interrupt = false;
	
	public ThreadRouting() {
		Thread t = new Thread(this, "Thread di Routing");
		System.out.println("ROUTING_CHECK: Thread di Routing creato");
		t.start();
	}
	
	@Override
	public void run() {
	    try {
			while(!interrupt) {
				try {
					HashMap<String, ArrayList<Route>> routehash = RoutingCheck.routeHash();
					RoutingCheck.clearTable(routehash);
					RoutingCheck.manageTable(routehash);
				} catch (IOException e) {
					e.printStackTrace();
				}
				Thread.sleep(10000);
			}
		}
		catch (InterruptedException e) {
			System.out.println("ROUTING_CHECK: Thread di Routing interrotto");
		}
		System.out.println("ROUTING_CHECK: Uscita Thread di Routing");
	}
	
	public void stop() {
		this.interrupt=true;
	}

}
