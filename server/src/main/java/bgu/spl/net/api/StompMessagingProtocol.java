package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;

public interface StompMessagingProtocol<T>  {
	/**
	 * Used to initiate the current client protocol with it's personal connection ID and the connections implementation
	**/
    void start(int connectionId, Connections<T> connections);
    
    void process(T message);
	
	/**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();

    /**
	 * Called to forcefully terminate the connection or handle cleanup when the connection is closed.
	 * This method should release any resources associated with the connection,
	 * such as unsubscribing the user from topics or removing them from the active users map.
	 */
	void terminateConnection();
}
