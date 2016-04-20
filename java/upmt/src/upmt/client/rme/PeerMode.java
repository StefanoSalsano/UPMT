package upmt.client.rme;

import upmt.client.UPMTClient;

public class PeerMode implements Runnable {

	private UPMTClient upmtClient;
	private String VIP;
	private String aNAddress;
	private String LVIP;
	private Thread threadPeerMode;
	private String ifNameDest;

	public PeerMode(UPMTClient upmtClient, String VIP, String aNAddress,
			String LVIP, String ifNameDest) {
		this.upmtClient = upmtClient;
		this.VIP = VIP;
		this.aNAddress = aNAddress;
		this.LVIP = LVIP;
		this.threadPeerMode = new Thread(this, "Thread Peer Mode");
		this.ifNameDest = ifNameDest;
	}
	
	public PeerMode(UPMTClient upmtClient, String VIP, String aNAddress,
			String LVIP) {
		this.upmtClient = upmtClient;
		this.VIP = VIP;
		this.aNAddress = aNAddress;
		this.LVIP = LVIP;
		this.threadPeerMode = new Thread(this, "Thread Peer Mode");
	}

	public void setPeerMode() {
		int lvip = Integer.parseInt((this.LVIP.split("\\."))[3]);
		int vip = Integer.parseInt((this.VIP.split("\\."))[3]);
		if ((lvip % 2 == 0 && vip % 2 == 0) || (lvip % 2 != 0 && vip % 2 != 0)) {
			int min = Math.min(lvip, vip);
			if (min == lvip) {
				addToANList(this.VIP, this.aNAddress);
			}
		} else {
			int min = Math.min(lvip, vip);
			if (min != lvip) {
				addToANList(this.VIP, this.aNAddress);
			}
		}
	}
	
	
	public void addToANList(String VIP, String aNAddress) {
		synchronized (UPMTClient.getAvailableIfs()) {
			synchronized (upmtClient.getTunnelProviders()) {
				if (UPMTClient.getCfgANList().contains(aNAddress)) {
					UPMTClient.addRMETunnelsToGUI(aNAddress);
					this.upmtClient.getRmeTunnelIpInterfaceList().put(aNAddress, this.ifNameDest);
					if(this.upmtClient.getTunnelManager().getTid(this.ifNameDest, aNAddress)==UPMTClient.TID_DROP) {
						this.upmtClient.createRmeTunnels(aNAddress, this.ifNameDest);
					}
				}
				else {
					UPMTClient.addCfgAnlist(aNAddress);
					UPMTClient.addRMETunnelsToGUI(aNAddress);
					this.upmtClient.getRmeTunnelIpInterfaceList().put(aNAddress, this.ifNameDest);
					this.upmtClient.setANSipPort(aNAddress, new Integer(5060));
					this.upmtClient.createRmeTunnels(aNAddress, this.ifNameDest);
				}
			}
		}
	}

	@Override
	public void run() {
		setPeerMode();
	}

	public void start() {
		this.threadPeerMode.start();
	}

}