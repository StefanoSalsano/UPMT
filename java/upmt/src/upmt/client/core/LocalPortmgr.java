package upmt.client.core;

import java.util.Hashtable;

public class LocalPortmgr
{
	private int startPortNumber;
	private int size;
	private Hashtable<String,Integer> TableInterfacePort = new Hashtable<String,Integer>();

	public LocalPortmgr(int startPort, int size)
	{
		startPortNumber = startPort;
		this.size = size;
	}
	
	/**TODO:#descrizione da rifare
	 * it gives the specific src_Port Sip from these interface id 
	 * @param idTunnel
	 * @return
	 */
	public int getport(String idTunnel)
	{
		//Se l'interfaccia già esiste ritorno la porta
		//if(TableInterfacePort.containsKey(idTunnel)) return TableInterfacePort.get(idTunnel);
		
		//Se non esiste
		for(int i=startPortNumber; i<startPortNumber+size; i++)
			if(!TableInterfacePort.contains(i)) //Cerco il primo numero di porta nn ancora assegnato
			{
				TableInterfacePort.put(idTunnel, i); //Lo assegno all'interfaccia richiesta
				return i;
			}
		return 0; //Tutte le 'SIZE' porte sono già assegnate
	}

	public void releaseport(String idTunnel)
	{if (TableInterfacePort.containsKey(idTunnel)) TableInterfacePort.remove(idTunnel);}
}
