package org.zoolu.sip.header;

public class TimestampHeader extends Header {
	
	public TimestampHeader(String time)
	{
		super(SipHeaders.Timestamp, time);	
	}
	
	public TimestampHeader(Header hd)
	   {  super(hd);
	   }
	public String toString()
	   {  return "Timestamp: "+ super.getValue()+"\r\n";
	   }

}
