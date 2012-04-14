package upmt.client.rme;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.jsonref.JSONArray;
import org.jsonref.JSONObject;
import org.jsonref.JSONTokener;

import upmt.client.UPMTClient;


public class ParseJson {
	
	HashMap<String, ArrayList<Route>> jmap;
	String LVIPA;
	FileReader netconf;
	
	public ParseJson() throws IOException, org.jsonref.JSONException {
		try {
			netconf = new FileReader(UPMTClient.getRMEConfig());
		} catch (Exception e) {
			System.err.println("[ROUTING_CHECK]: file configuration not found");
			System.exit(0);
		}
		this.jmap = new HashMap<String, ArrayList<Route>>();
		JSONTokener tok = new JSONTokener(netconf);
		JSONObject obj = new JSONObject(tok);
		LVIPA = obj.get("LVIPA").toString();
		JSONArray allIP = new JSONArray(obj.get("allIP").toString());
		for(int i=0; i<allIP.length(); i++) {
			String VIP = allIP.getJSONObject(i).get("VIP").toString();
			JSONArray inf = new JSONArray(allIP.getJSONObject(i).get("if").toString());
			ArrayList<Route> rlist = new ArrayList<Route>();
			for(int j=0; j<inf.length(); j++) {
				String ifname = inf.getJSONObject(j).getString("ifname");
				String IP = inf.getJSONObject(j).getString("IP");
				Route routes = new Route(IP, ifname);
				rlist.add(routes);
			}
			this.jmap.put(VIP, rlist);
		}
	}
	
	public HashMap<String, ArrayList<Route>> getJmap() {
		return this.jmap;
	}
	
	public String getLVIPA() {
		return this.LVIPA;
	}
	
}
