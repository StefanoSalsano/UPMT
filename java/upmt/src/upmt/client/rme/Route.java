package upmt.client.rme;

public class Route {
	
	private String ip;
	private String ifName;
	private String gateway;
	
	public Route(String ip, String ifName) {
		this.ip=ip;
		this.ifName=ifName;
	}
	
	public Route(String ip, String ifName, String gateway) {
		this.ip=ip;
		this.ifName=ifName;
		this.gateway=gateway;
	}
	
	public void setIp(String ip) {
		this.ip=ip;
	}
	
	public String getIp() {
		return this.ip;
	}
	
	public void setIfName(String ifName) {
		this.ifName=ifName;
	}
	
	public String getIfName() {
		return this.ifName;
	}
	
	public void setGateway(String gateway) {
		this.gateway=gateway;
	}
	
	public String getGateway() {
		return this.gateway;
	}

}
