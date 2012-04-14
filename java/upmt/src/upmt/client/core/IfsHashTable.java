package upmt.client.core;

import java.util.Hashtable;
import java.util.Map.Entry;

import upmt.client.sip.SipSignalManager;

/**
 * Hashtable (interface name-> InterfaceInfo) 
 *  containing the detected network interfaces and associated ip addresses/interface gateway<BR>
 * 
 *
 */
@SuppressWarnings("serial")
public class IfsHashTable extends Hashtable<String, InterfaceInfo> {
	
	public IfsHashTable filterOnSignalingOK( String anchorNode) {
		IfsHashTable returnHashTable = new IfsHashTable();

		for (Entry<String, InterfaceInfo> myEntry : this.entrySet()) {
			Integer result = myEntry.getValue().getANIFStatus(anchorNode);
			if (result!=null && InterfaceInfo.IFANOK == result) {
				returnHashTable.put(myEntry.getKey(),myEntry.getValue() );
			}
		}

		return returnHashTable;
	}

	public IfsHashTable filterOnCanUseTunnel(  String anchorNode) {
		IfsHashTable returnHashTable = new IfsHashTable();

		for (Entry<String, InterfaceInfo> myEntry : this.entrySet()) {
			if (SipSignalManager.canUseTunnel(anchorNode, myEntry.getKey())){
				returnHashTable.put(myEntry.getKey(),myEntry.getValue() );
			}
		}
		
		return returnHashTable;
	}


	
}
