package upmt.server.vipa.model;

public class NumIp
{
	private int one=0, two=0, three=0, four=0;
	public NumIp(String address)
	{
		if (!address.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")) return;
		String[] token = address.split("\\.");
		for (String num : token) if (Integer.parseInt(num)>256) return;
		one = Integer.parseInt(token[0]);
		two = Integer.parseInt(token[1]);
		three = Integer.parseInt(token[2]);
		four = Integer.parseInt(token[3]);
	}

	public NumIp(NumIp numAddress)
	{
		this.one = numAddress.one;
		this.two = numAddress.two;
		this.three = numAddress.three;
		this.four = numAddress.four;
	}

	public void increase()
	{
		if (four!=256) four++;
		else {four = 0; if (three!=256) three++;
		else {three = 0; if (two!=256) two++;
		else {two = 0; if (one!=256) one++;}}}
	}

	public boolean isLesserThan(NumIp numAddress)
	{
		if(this.one*256+this.two<numAddress.one*256+numAddress.two) return true;
		if((this.one*256+this.two==numAddress.one*256+numAddress.two) &&
		(this.three*256+this.four<numAddress.three*256+numAddress.four)) return true;
		return false;
	}

	public String toString() {return one+"."+two+"."+three+"."+four;}
}
