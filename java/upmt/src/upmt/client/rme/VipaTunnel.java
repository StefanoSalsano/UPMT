package upmt.client.rme;

/**
 * describes a tunnel including its VIPA, the remote IP address, the local ifName and the Tid
 * NB the VIPA is redundant as it could be derived from the remote IP address
 * @author root
 *
 */
public class VipaTunnel {
	
	private String ifName;
	private String endPointAddress;
	private String VIPA;
	private int tid;
	
	public VipaTunnel(String VIPA, String ifName, String endPointAddress, int tid) {
		this.VIPA = VIPA;
		this.ifName = ifName;
		this.endPointAddress = endPointAddress;
		this.tid = tid;
	}
	
//	public VipaTunnel(String ifName, String endPointAddress) {
//		this.VIPA = null;
//		this.ifName = ifName;
//		this.endPointAddress = endPointAddress;
//		this.tid = 0;
//	}

	public String getIfName() {
		return ifName;
	}

	public void setIfName(String ifName) {
		this.ifName = ifName;
	}

	public String getEndPointAddress() {
		return endPointAddress;
	}

	public void setEndPointAddress(String endPointAddress) {
		this.endPointAddress = endPointAddress;
	}

	public String getVIPA() {
		return VIPA;
	}

	public void setVIPA(String vIPA) {
		VIPA = vIPA;
	}

	public int getTid() {
		return tid;
	}

	public void setTid(int tid) {
		this.tid = tid;
	}
	

}
