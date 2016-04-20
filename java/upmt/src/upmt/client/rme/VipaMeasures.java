package upmt.client.rme;

public class VipaMeasures {
	
	private String Vipa;
	double measuredSpread;
	double potentialnGain;
	double reallocationPriority;
	
	public VipaMeasures(String Vipa, double measuredSpread, double potentialGain, double reallocationPriority){
		this.Vipa = Vipa;
		this.measuredSpread = measuredSpread;
		this.potentialnGain = potentialGain;
		this.reallocationPriority = reallocationPriority;
	}

	public String getVipa() {
		return Vipa;
	}

	public void setVipa(String vipa) {
		Vipa = vipa;
	}

	public double getMeasuredSpread() {
		return measuredSpread;
	}

	public void setMeasuredSpread(double measuredSpread) {
		this.measuredSpread = measuredSpread;
	}

	public double getPotentialnGain() {
		return potentialnGain;
	}

	public void setPotentialnGain(double potentialnGain) {
		this.potentialnGain = potentialnGain;
	}

	public double getReallocationPriority() {
		return reallocationPriority;
	}

	public void setReallocationPriority(double reallocationPriority) {
		this.reallocationPriority = reallocationPriority;
	}
	
}
