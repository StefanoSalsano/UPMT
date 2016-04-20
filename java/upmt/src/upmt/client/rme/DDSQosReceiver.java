package upmt.client.rme;

import java.io.*;
import java.net.*;
import java.util.Iterator;

import org.jsonref.JSONArray;
import org.jsonref.JSONException;
import org.jsonref.JSONObject;

import upmt.client.UPMTClient;

public class DDSQosReceiver implements Runnable {

	/** The TCP server address. */ 
	private String vepa;

	/** The TCP server port. */ 
	private int ddsQosPort;

	/** Whether it has been halted */
	private boolean interrupt;

	private Thread ddsQosThread;

	/** The TCP server socket */ 
	private ServerSocket ddsQosSocket;

	/** Socket timeout */
	int socket_timeout;

	/** Default value for the maximum time that the tcp server can remain active after been halted (in milliseconds) */
	public static final int DEFAULT_SOCKET_TIMEOUT=5000; // 5sec 

	/** Default ServerSocket backlog value */
	public static int DEFAULT_SOCKET_BACKLOG=50;

	private UPMTClient upmtClient;


	public DDSQosReceiver(String vepa, int ddsQosPort, UPMTClient upmtClient) {
		this.upmtClient = upmtClient;
		this.vepa = vepa;
		this.ddsQosPort = ddsQosPort;
		this.interrupt = false;
		this.socket_timeout=DEFAULT_SOCKET_TIMEOUT;
		this.ddsQosThread = new Thread(this, "DDS QoS receiver thread");
		try {
			this.ddsQosSocket = new ServerSocket(this.ddsQosPort, DEFAULT_SOCKET_BACKLOG, InetAddress.getByName(this.vepa));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		System.out.println("[DDS QoS Receiver] Server Qos DDS has connected!");
		String msg="";
		JSONObject msgDDS = null;
		try {
			while(!interrupt) {
				Socket connectionSocket = this.ddsQosSocket.accept();
				BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
				String inMsg="";
				while((inMsg=inFromClient.readLine())!=null) {
					msg+=inMsg;
				}
				inFromClient.close();
				if(!msg.equals("")) {
					msgDDS = new JSONObject(msg);
					if(msgDDS.has("msg")) {
						String typeMessage = msgDDS.getString("msg");
						if(typeMessage.equals("SetPriorityMap")) {
							if(msgDDS.has("PrioMap")) {
								JSONObject listQos = msgDDS.getJSONObject("PrioMap");
								Iterator<String> iter = listQos.keys();
								while(iter.hasNext()) {
									String key = iter.next();
									int effectiveLatency = (Integer)listQos.get(key);
									upmtClient.updateDDSQos(key,effectiveLatency);
									//						System.err.println("Found new key---> "+key+" with delay "+effectiveLatency);
								}
								System.out.println("[DDS QoS receiver] Received msg: " + msg);
							}

						}
					}
				}
				else {
					System.out.println("[DDS QoS receiver] Received empty msg");
				}
				msg="";
			}
		} catch (IOException e) {
			System.out.println("[DDS QoS receiver] Connessione chiusa");
		} catch (JSONException e) {
			System.out.println("[DDS QoS receiver] Errore in messaggio ricevuto");
			stopDDSQosThread();
			startDDSQosThread();
		}
		System.out.println("[DDS QoS receiver] Uscita DDS QoS receiver thread");

	}


	public void startDDSQosThread() {
		System.out.println("[DDS QoS receiver] DDS QoS receiver Thread started");
		ddsQosThread.start();
	}

	public void stopDDSQosThread() {
		this.interrupt=true;
		try {
			this.ddsQosSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
