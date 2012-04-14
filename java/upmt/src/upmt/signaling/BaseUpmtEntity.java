package upmt.signaling;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import local.server.Proxy;
import local.server.ServerProfile;

import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.message.MessageFactory;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.transaction.TransactionClient;
import org.zoolu.sip.transaction.TransactionClientListener;
import org.zoolu.sip.transaction.TransactionServer;
import org.zoolu.tools.Log;

import upmt.client.sip.SipSignalManager;
import upmt.server.UPMTServer;
import upmt.signaling.message.ANListReq;
import upmt.signaling.message.ANListResp;
import upmt.signaling.message.AssociationReq;
import upmt.signaling.message.AssociationResp;
import upmt.signaling.message.BrokerRegistrationReq;
import upmt.signaling.message.HandoverReq;
import upmt.signaling.message.HandoverResp;
import upmt.signaling.message.KeepAlive;
import upmt.signaling.message.SetHandoverModeReq;
import upmt.signaling.message.SetHandoverModeResp;
import upmt.signaling.message.Signal;
import upmt.signaling.message.TsRedirect;
import upmt.signaling.message.TsaBinding;
import upmt.signaling.message.TunnelSetupReq;
import upmt.signaling.message.TunnelSetupResp;

public class BaseUpmtEntity extends Proxy
{
	protected static PrintStream os = null;
	protected static Integer totalOverhead = new Integer(0);
	protected static long startOverheadCounting= 0;
	protected static HashMap<String, Integer[]> iFaceOverHead = new HashMap<String, Integer[]>();
	protected static int minute = 1;

	public BaseUpmtEntity(String file) {
		super(new SipProvider(file), new ServerProfile(file));
	}

	public BaseUpmtEntity(String file, SipProvider provider) {
		super(provider, new ServerProfile(file));
	}

	private void initOverheadTrackingFunction(){
		try {
			os = new PrintStream(new FileOutputStream("log/overhead-" + SipSignalManager.CLIENT_KEEP_ALIVE_INTERVAL + ".csv", true));
			try {
				byte[] resp = new byte[100];
				String[] cmd = {"bash", "-c", "echo $PPID"};
				Process p;

				p = Runtime.getRuntime().exec(cmd);
				p.getInputStream().read(resp);
				System.out.println("Client pid: " + new String(resp));
				int x = -1;
				for(int i = 0; i < resp.length; i++){
					if(resp[i] == 0){
						x = i;
						break;
					}
				}

				System.err.println("watch -n 0.1 'ps -p"+new Integer(new String(resp).substring(0, x-1))+" -o pcpu= >> overheadCPU"+SipSignalManager.CLIENT_KEEP_ALIVE_INTERVAL+".csv'\n");

			} catch (IOException e) {
				e.printStackTrace();
			}

			startOverheadCounting = System.currentTimeMillis();
			os.println("\ntime iface traffic count " + startOverheadCounting);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/** If the message is recognized as UPMT. */
	protected boolean isUpmtMessage(Message msg)
	{return msg.isMessage() && msg.hasSubjectHeader() && msg.getSubjectHeader().getSubject().equals("UPMT");}

	/** Adds a new string to the default Log. */
	protected void printLog(String str, int level)
	{if (log!=null) {log.println(str, level);}}



	// ***************************** Proxy *****************************
	/** When a new request message is received for a remote UA */
	public void processRequestToRemoteUA(Message req)
	{printLog("\nHandler: processRequestToRemoteUA", Log.LEVEL_LOWER);super.processRequestToRemoteUA(req);}
	/** When a new request message is received for a locally registered user */
	public void processRequestToLocalUser(Message req)
	{printLog("\nHandler: processRequestToLocalUser", Log.LEVEL_LOWER);super.processRequestToLocalUser(req);}	   
	/** When a new response message is received */
	public void processResponse(Message resp)
	{printLog("\nHandler: processResponse", Log.LEVEL_LOWER);super.processResponse(resp);}
	/** When a new request request is received for the local server */
	public void processRequestToLocalServer(Message msg)
	{if (isUpmtMessage(msg)) processRequestToLocalUpmtServer(msg); else processRequestToLocalSipServer(msg);}
	/** When a new request request is received for the local SIP server */
	public void processRequestToLocalSipServer(Message msg)
	{printLog("\nHandler: processRequestToLocalSipServer", Log.LEVEL_LOWER);super.processRequestToLocalServer(msg);}

	/** When a new request request is received for the local UPMT server */
	protected void processRequestToLocalUpmtServer(Message msg)
	{
		printLog("\n" + new java.util.Date() + " Handler: processRequestToUpmtServer", Log.LEVEL_LOWER);
		printLog("\nMessage: " + msg.toString(), Log.LEVEL_LOWER);
		Signal[] reqSignalList = UpmtSignal.deSerialize(msg.getBody());
		Signal[] respSignalList = new Signal[reqSignalList.length];
		for (int i=0; i<reqSignalList.length; i++)
		{
			Signal reqSignal = reqSignalList[i];
			Signal respSignal;
			if (reqSignal instanceof AssociationReq) respSignal = handleAssociation((AssociationReq)reqSignal);
			else if (reqSignal instanceof TunnelSetupReq) respSignal = handleTunnelSetup((TunnelSetupReq)reqSignal);
			else if (reqSignal instanceof HandoverReq) respSignal = handleHandover((HandoverReq)reqSignal);
			else if (reqSignal instanceof SetHandoverModeReq) respSignal = handleSetHandoverMode((SetHandoverModeReq)reqSignal);
			else if (reqSignal instanceof ANListReq){ 
				//printOverheadLog(msg.toString(), false, false, "");

				printLog("ANListReq", 2);
				respSignal = handleANListReq((ANListReq)reqSignal);
				Message resp = MessageFactory.createResponse(msg, 200, "OK", null);
				resp.setBody("application/text", UpmtSignal.serialize(respSignal));

				//printOverheadLog(resp.toString(), true, false, "");
			}
			else if (reqSignal instanceof BrokerRegistrationReq) {
				//printOverheadLog(msg.toString(), false, false, "");

				boolean added = handleBrokerRegistration((BrokerRegistrationReq)reqSignal);
				if(added){
					//printOverheadLog(MessageFactory.createResponse(msg, 200, "OK", null).toString(), true, false, "");
					continue;
				}
				else {
					Message resp = MessageFactory.createResponse(msg, 501, "not registered", null);
					//printOverheadLog(resp.toString(), true, false, "");
					sip_provider.sendMessage(resp);
					return;
				}
			}
			else if (reqSignal instanceof KeepAlive) {
				if(UPMTServer.SERVER_KEEP_ALIVE){

					//printSimplifiedOverheadLog(msg.toString(), "tsa");

					boolean alive = handleKeepAlive((KeepAlive)reqSignal); 

					if(alive){
						//printSimplifiedOverheadLog(MessageFactory.createResponse(msg, 200, "OK", null).toString(), "tsa");
						continue;
					}
					else {
						Message resp = MessageFactory.createResponse(msg, 501, "dead tunnel", null);
						//printSimplifiedOverheadLog(resp.toString(), "tsa");
						sip_provider.sendMessage(resp);
						return;
					}
				}
			}
			else if (reqSignal instanceof TsaBinding) respSignal = handleTsaBinding((TsaBinding)reqSignal);
			else if (reqSignal instanceof TsRedirect) respSignal = handleTsRedirect((TsRedirect)reqSignal);


			//response code 484 = "Address Incomplete"; response code 485 = "Ambiguous"; response code 501 = "Not Implemented";
			else {sip_provider.sendMessage(MessageFactory.createResponse(msg, 501, "bad UPMT signal", null));return;}
			respSignalList[i] = respSignal;
		}

		Message resp = MessageFactory.createResponse(msg, 200, "OK", null);
		resp.setBody("application/text", UpmtSignal.serialize(respSignalList));
		new TransactionServer(sip_provider, msg, null).respondWith(resp);
	}

	public static void printOverheadLog(String overhead, boolean exiting, boolean tunnel, String iface){
		synchronized (totalOverhead) {
			if(overhead != null){
				int overh = overhead.length() + 8;
				if(tunnel)
					overh += 20;
				totalOverhead += overh;
				os.print(((System.currentTimeMillis() - startOverheadCounting)/1000.0) + " " + exiting + " " + tunnel + " " + iface + " "  + overh + " " + totalOverhead + "\n");
			}
		}
	}

	public static void printSimplifiedOverheadLog(String overhead, String iface){
		synchronized (iFaceOverHead) {

			if((System.currentTimeMillis() - startOverheadCounting)/1000.0 >= 60 * minute){
				int traffic = 0;
				int count = 0;

				for(String key: iFaceOverHead.keySet()){
					Integer[] stats = iFaceOverHead.get(key);
					os.print(minute + " " + key + " " + stats[0] + " " + stats[1] + "\n");
					traffic = traffic + stats[0];
					count = count + stats[1];
					stats[0] = 0;
					stats[1] = 0;
					iFaceOverHead.put(key, stats);				
				}
				os.print(minute + " " + "total" + " " + traffic + " " + count + "\n\n");

				minute++;
			}

			if(iFaceOverHead.containsKey(iface)){
				Integer[] stats = iFaceOverHead.get(iface);
				stats[0] = stats[0] + overhead.length() + 28;
				stats[1] = stats[1] + 1;				
			}
			else{
				Integer[] stats = new Integer[2];
				stats[0] = overhead.length() + 28;
				stats[1] = 1;
				iFaceOverHead.put(iface, stats);				
			}
		}
	}



	protected boolean handleKeepAlive(KeepAlive req){return false;}
	protected boolean handleBrokerRegistration(BrokerRegistrationReq reqSignal) {return false;}
	protected ANListResp handleANListReq(ANListReq reqSignal) {return null;}

	protected AssociationResp handleAssociation(AssociationReq req){return null;}
	protected TunnelSetupResp handleTunnelSetup(TunnelSetupReq req){return null;}
	protected HandoverResp handleHandover(HandoverReq req){return null;}
	protected SetHandoverModeResp handleSetHandoverMode(SetHandoverModeReq req){return null;}
	protected Signal handleTsaBinding(TsaBinding req){return null;}
	protected Signal handleTsRedirect(TsRedirect req){return null;}


	// ************************** Transaction **************************
	private class NullMessage extends Message{}
	protected boolean isNull(Message msg) {return msg instanceof NullMessage;}

	protected Message newUpmtMessage(NameAddress recipient, NameAddress from, Signal req) {
		return newUpmtMessage(sip_provider, recipient, from, req);
	}
	protected Message newUpmtMessage(NameAddress recipient, NameAddress from, Signal[] req) {
		return newUpmtMessage(sip_provider, recipient, from, req);
	}

	protected Message startBlockingTransaction(Message message) {
		return startBlockingTransaction(null, message);
	}
	protected void startNonBlockingTransaction(Message message) {
		startNonBlockingTransaction(null, message);
	}
	protected void startNonBlockingTransaction(Message message, String handlerName, Object... paramForHandler) {
		startNonBlockingTransaction(null, message, handlerName, paramForHandler);
	}



	protected Message newUpmtMessage(SipProvider sip_provider, NameAddress recipient, NameAddress from, Signal req) {
		return newUpmtMessage(sip_provider, recipient, from, new Signal[]{req});
	}
	protected Message newUpmtMessage(SipProvider sip_provider, NameAddress recipient, NameAddress from, Signal[] req) {
		return MessageFactory.createMessageRequest(sip_provider, recipient, from, UpmtSignal.subject, UpmtSignal.type, UpmtSignal.serialize(req));
	}

	protected void startNonBlockingTransaction(SipProvider provider, Message message) {
		if (provider==null) {
			provider = sip_provider;
		}
		new TransactionClient(provider, message, null).request();
	}

	protected Message startBlockingTransaction(SipProvider provider, Message message) {
		if (provider==null) {
			provider = sip_provider;
		}
		//		class MessageContainer {Message msg;}
		MessageContainer lock = new MessageContainer();

		BlockingTransactionClientListener blockingTransactionClientListener = new BlockingTransactionClientListener();
		blockingTransactionClientListener.setLock(lock);

		TransactionClient transactionAgent = new TransactionClient(provider, message, blockingTransactionClientListener);


		//		TransactionClient transactionAgent = new TransactionClient(provider, message, new TransactionClientListener()
		//		{
		//			private MessageContainer lock;
		//			public TransactionClientListener setLock(MessageContainer l) {lock = l;return this;}
		//			private void setMsg(Message msg){synchronized(lock){lock.msg = msg;lock.notify();}}
		//
		//			public void onTransProvisionalResponse(TransactionClient tc, Message msg) {}
		//			public void onTransTimeout(TransactionClient tc) {setMsg(new NullMessage());}
		//			public void onTransFailureResponse(TransactionClient tc, Message msg) {setMsg(new NullMessage());}
		//			public void onTransSuccessResponse(TransactionClient tc, Message msg) {setMsg(msg);}
		//
		//		}.setLock(lock));

		synchronized(lock) {
			transactionAgent.request();
			try {lock.wait();} catch (InterruptedException e) {e.printStackTrace();}
		}
		return lock.msg;
	}

	class MessageContainer {
		Message msg;
	}

	class BlockingTransactionClientListener  implements TransactionClientListener {
		private MessageContainer lock;
		public TransactionClientListener setLock(MessageContainer l) {lock = l;return this;}
		private void setMsg(Message msg){synchronized(lock){lock.msg = msg;lock.notify();}}

		public void onTransProvisionalResponse(TransactionClient tc, Message msg) {}
		public void onTransTimeout(TransactionClient tc) {setMsg(new NullMessage());}
		public void onTransFailureResponse(TransactionClient tc, Message msg) {setMsg(new NullMessage());}
		public void onTransSuccessResponse(TransactionClient tc, Message msg) {setMsg(msg);}
	}



	/** The handler MUST have as first parameter a "Message" to pass the response*/
	protected void startNonBlockingTransaction(SipProvider provider, Message message, String handlerName, Object... paramForHandler) {
		// Object... is a construct called varargs to pass an arbitrary number of values to a method 
		// The method can be called either with an array or with a sequence of arguments. The code in the method body will 
		// treat the parameter as an array in either case. 


		if (provider==null) provider = sip_provider;
		int paramNum = paramForHandler.length;
		Class<?>[] clazz = new Class<?>[paramNum+1];
		clazz[0] = Message.class;
		for(int i=0; i<paramNum; i++) clazz[i+1]=paramForHandler[i].getClass();


		Method responseHandler;
		try {responseHandler =  SipSignalManager.class.getMethod(handlerName, clazz);}
		catch (SecurityException e) {e.printStackTrace(); return;}
		catch (NoSuchMethodException e) {e.printStackTrace(); return;}

		NonBlockingTransactionClientListener myTransactionClientListener = new NonBlockingTransactionClientListener();
		myTransactionClientListener.setHandler(responseHandler, paramForHandler, this);

		TransactionClient transactionAgent = new TransactionClient(provider, message, myTransactionClientListener);

		//		TransactionClient transactionAgent = new TransactionClient(provider, message, new TransactionClientListener()
		//		{
		//			private void launchHandler(Message msg)
		//			{
		//				Object[] p = new Object[v.length+1]; p[0] = msg; for(int i=0;i<v.length;i++) p[i+1] = v[i];
		//				try {h.invoke(s, p);}
		//				catch (IllegalArgumentException e) {e.printStackTrace();}
		//				catch (IllegalAccessException e) {e.printStackTrace();}
		//				catch (InvocationTargetException e) {e.printStackTrace();}
		//			}
		//			private Method h; private Object[] v; BaseUpmtEntity s;
		//			public TransactionClientListener setHandler(Method a, Object[] b, BaseUpmtEntity c){h=a;v=b;s=c;return this;}
		//			public void onTransProvisionalResponse(TransactionClient tc, Message msg) {}
		//			public void onTransTimeout(TransactionClient tc) {launchHandler(new NullMessage());}
		//			public void onTransFailureResponse(TransactionClient tc, Message msg) {launchHandler(new NullMessage());}
		//			public void onTransSuccessResponse(TransactionClient tc, Message msg) {launchHandler(msg);}
		//		}.setHandler(responseHandler, paramForHandler, this));

		transactionAgent.request();
	}

	class NonBlockingTransactionClientListener  implements TransactionClientListener {
		private void launchHandler(Message msg) {
			Object[] p = new Object[v.length+1]; p[0] = msg; for(int i=0;i<v.length;i++) p[i+1] = v[i];
			try {h.invoke(s, p);}
			catch (IllegalArgumentException e) {e.printStackTrace();}
			catch (IllegalAccessException e) {e.printStackTrace();}
			catch (InvocationTargetException e) {e.printStackTrace();}
		}
		private Method h; private Object[] v; BaseUpmtEntity s;
		public TransactionClientListener setHandler(Method a, Object[] b, BaseUpmtEntity c){h=a;v=b;s=c;return this;}
		public void onTransProvisionalResponse(TransactionClient tc, Message msg) {}
		public void onTransTimeout(TransactionClient tc) {
			launchHandler(new NullMessage());}
		public void onTransFailureResponse(TransactionClient tc, Message msg) {
			launchHandler(new NullMessage());}
		public void onTransSuccessResponse(TransactionClient tc, Message msg) {
			if(h.getName().equals("keepAliveResp") && v.length >= 5){
				v[3]=tc.getDelay();
				v[4]=tc.getNumberRetry();
			}
			launchHandler(msg);
		}
	}
}	
