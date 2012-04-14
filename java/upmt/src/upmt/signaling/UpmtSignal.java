package upmt.signaling;

import org.istsms.util.Javabean2JSON;
import org.jsonref.JSONArray;
import org.jsonref.JSONException;
import org.jsonref.JSONObject;
import org.zoolu.tools.Log;

import upmt.client.UPMTClient;
import upmt.signaling.message.Signal;

public class UpmtSignal
{
	public static final String subject = "UPMT";
	public static final String type = "application/text";
	
	public static Signal[] deSerialize(String msgBody) {
		if (msgBody==null) {
			printLog("[UpmtSigna] deSerialize: msgBody is null ", Log.LEVEL_HIGH);
			return null;
		}
		try {
			JSONArray array = null ;
			try {
				array = new JSONArray(msgBody);
			} catch (NullPointerException e) {
				printLog("NullPointerException, msgBody: " + msgBody, Log.LEVEL_HIGH);
			}
			Signal[] ret = new Signal[array.length()];
			
			for(int i=0; i<array.length(); i++) {
				JSONObject signalContainer = array.getJSONObject(i);
				JSONObject jsonSignal = signalContainer.getJSONObject("MContent");
				String signalType = Signal.class.getPackage().getName() + "." + signalContainer.getString("MType");
				Class<?> clazz =  Class.forName(signalType);
				ret[i] =  (Signal) Javabean2JSON.fromJSONObject(jsonSignal, clazz);
			}
			return ret;
		}
		catch (JSONException e) {return null;}
		catch (ClassNotFoundException e) {return null;}
	}

	public static String serialize(Signal signal)
	{return serialize(new Signal[]{signal});}

	public static String serialize(Signal[] signalList)
	{
		JSONObject[] containerList = new JSONObject[signalList.length];
		for (int i=0; i<signalList.length; i++)
			if(signalList[i]==null) containerList[i] = new JSONObject();
			else containerList[i] = new JSONObject(new SignalContainer(signalList[i]));
		
		try {return new JSONArray(containerList).toString();}
		catch (JSONException e) {e.printStackTrace();return null;}
	}

	public static Signal createResp(Signal req)
	{
		String reqClass = req.getClass().getCanonicalName();
		String respClass = reqClass.substring(0, reqClass.length()-1)+"sp";
		Signal resp = null;

		try {resp = (Signal) Class.forName(respClass).newInstance();}
		catch (InstantiationException e) {return null;}
		catch (IllegalAccessException e) {return null;}
		catch (ClassNotFoundException e) {return null;}

		resp.setSipId(req.getSipId());
		return resp;
	}
	
	private static void printLog(String text, int logLevel) {
		UPMTClient.printStaticLog("[UpmtSignal] "+text, logLevel);
	}

}
