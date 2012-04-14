package upmt.client.application.manager.impl;

import upmt.client.core.Socket;

public class GuiSocket
{
	private Socket socket;

	public GuiSocket(Socket socket) {this.socket = socket;}
	public Socket getSocket() {return socket;}
	public String toString() {return socket.tostring();}
}
