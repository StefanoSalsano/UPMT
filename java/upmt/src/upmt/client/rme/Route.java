package upmt.client.rme;

public class Route {
	
	private String ip;
	private String ifName;
	private String gateway;
	
	/**
	 * Route object constructor
	 * @param ip
	 * @param ifName
	 */
	public Route(String ip, String ifName) {
		this.ip=ip;
		this.ifName=ifName;
	}
	
	/**
	 * Route object constructor
	 * @param ip
	 * @param ifName
	 * @param gateway
	 */
	public Route(String ip, String ifName, String gateway) {
		this.ip=ip;
		this.ifName=ifName;
		this.gateway=gateway;
	}
	
	/**
	 * Sets the IP field in the Route object
	 * @param ip
	 */
	public void setIp(String ip) {
		this.ip=ip;
	}
	
	/**
	 * Gets the IP field in the Route object
	 * @return String
	 */
	public String getIp() {
		return this.ip;
	}
	
	/**
	 * Sets the Ifname field in the Route object
	 * @param ifName
	 */
	public void setIfName(String ifName) {
		this.ifName=ifName;
	}
	
	/**
	 * Gets the Ifname field in the Route object
	 * @return String
	 */
	public String getIfName() {
		return this.ifName;
	}
	
	/**
	 * Sets the Gateway field in the Route object
	 * @param gateway
	 */
	public void setGateway(String gateway) {
		this.gateway=gateway;
	}
	
	/**
	 * Gets the Gateway field in the Route object
	 * @return String
	 */
	public String getGateway() {
		return this.gateway;
	}

}
