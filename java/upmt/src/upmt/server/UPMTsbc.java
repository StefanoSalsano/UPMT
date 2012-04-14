package upmt.server;

import java.util.Date;
import java.util.Vector;

import local.sbc.ExtendedSipProvider;
import local.sbc.SessionBorderController;
import local.sbc.SessionBorderControllerProfile;
import local.server.ServerProfile;

import org.zoolu.sip.message.Message;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipStack;

import upmt.Default;

/**Stub class. It extends the SessionBorderController adding some println for debug use.*/
public class UPMTsbc extends SessionBorderController
{
	public UPMTsbc(ExtendedSipProvider sip_provider, ServerProfile server_profile, SessionBorderControllerProfile sbc_profile)
	{super(sip_provider, server_profile, sbc_profile);
	}
	
	public void onReceivedMessage(SipProvider provider, Message msg)
	{System.out.println("\n[" + new Date() + "]\nReceived message! from " + msg.getRemoteAddress() + "\n" + msg.getDump() + msg.getBody() + "\t");super.onReceivedMessage(provider, msg);}
	/** When a new request message is received for a remote UA */
	public void processRequestToRemoteUA(Message req)
	{System.out.println("Handler: processRequestToRemoteUA\n\n");super.processRequestToRemoteUA(req);}
	/** When a new request message is received for a locally registered user */
	public void processRequestToLocalUser(Message req)
	{System.out.println("Handler: processRequestToLocalUser\n\n");super.processRequestToLocalUser(req);}	   
	/** When a new response message is received */
	public void processResponse(Message resp)
	{System.out.println("Handler: processResponse\n\n");super.processResponse(resp);}
	/** When a new request request is received for the local server */
	public void processRequestToLocalServer(Message msg)
	{System.out.println("Handler: processRequestToLocalServer\n\n");super.processRequestToLocalServer(msg);}


	public static void main(String[] args)
	{
		String file = Default.SBC_CONFIG_FILE;
		boolean memory_debugging = false;
			
		int first_port=0;
		int last_port=0;
		
		for(int i=0; i<args.length; i++)
		{
			if (args[i].equals("-f") && args.length>(i+1))
			{
				file = args[++i];
				continue;
			}
			if(args[i].equals("-d"))
			{
				memory_debugging = true;
				continue;
			}
			if(args[i].equals("-m")) // set the local media ports
			{
				first_port = Integer.parseInt(args[++i]);
				last_port = Integer.parseInt(args[++i]);
				continue;
			}
			if(args[i].equals("-h"))
			{
				System.out.println("usage:\n   java SessionBorderController [options]");
				System.out.println("   options:");
				System.out.println("   -f <config_file>   specifies a configuration file");
				System.out.println("   -m <fist_port> <last_port> interval of media ports");
				System.exit(0);
			}
		}
		
		SipStack.init(file);
		ServerProfile server_profile = new ServerProfile(file);
		SessionBorderControllerProfile sbc_profile = new SessionBorderControllerProfile(file);
		
		if(first_port>0 && last_port>=first_port)
		{
			Vector media_ports = new Vector();
			for(int i=first_port; i<=last_port; i+=2) media_ports.addElement(new Integer(i));
			sbc_profile.media_ports = media_ports;
		}
		// create a new ExtendedSipProvider
		long keepalive_aggressive_time = (sbc_profile.keepalive_aggressive)? sbc_profile.keepalive_time : 0;
		ExtendedSipProvider extended_provider = new ExtendedSipProvider(file,sbc_profile.binding_timeout,keepalive_aggressive_time);
				
		// create and start the SBC
		new UPMTsbc(extended_provider,server_profile,sbc_profile);
	}

}
