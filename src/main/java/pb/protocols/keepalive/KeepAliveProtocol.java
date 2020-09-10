package pb.protocols.keepalive;

import java.time.Instant;
import java.util.logging.Logger;

import pb.Endpoint;
import pb.EndpointUnavailable;
import pb.Manager;
import pb.Utils;
import pb.protocols.Message;
import pb.protocols.Protocol;
import pb.protocols.IRequestReplyProtocol;

/**
 * Provides all of the protocol logic for both client and server to undertake
 * the KeepAlive protocol. In the KeepAlive protocol, the client sends a
 * KeepAlive request to the server every 20 seconds using
 * {@link pb.Utils#setTimeout(pb.protocols.ICallback, long)}. The server must
 * send a KeepAlive response to the client upon receiving the request. If the
 * client does not receive the response within 20 seconds (i.e. at the next time
 * it is to send the next KeepAlive request) it will assume the server is dead
 * and signal its manager using
 * {@link pb.Manager#endpointTimedOut(Endpoint,Protocol)}. If the server does
 * not receive a KeepAlive request at least every 20 seconds (again using
 * {@link pb.Utils#setTimeout(pb.protocols.ICallback, long)}), it will assume
 * the client is dead and signal its manager. Upon initialisation, the client
 * should send the KeepAlive request immediately, whereas the server will wait
 * up to 20 seconds before it assumes the client is dead. The protocol stops
 * when a timeout occurs.
 * 
 * @see {@link pb.Manager}
 * @see {@link pb.Endpoint}
 * @see {@link pb.protocols.Message}
 * @see {@link pb.protocols.keepalive.KeepAliveRequest}
 * @see {@link pb.protocols.keepalive.KeepAliveReply}
 * @see {@link pb.protocols.Protocol}
 * @see {@link pb.protocols.IRequestReplyProtocol}
 * @author aaron
 *
 */
public class KeepAliveProtocol extends Protocol implements IRequestReplyProtocol {
	private static Logger log = Logger.getLogger(KeepAliveProtocol.class.getName());
	
	/**
	 * Name of this protocol. 
	 */
	public static final String protocolName="KeepAliveProtocol";

	/**
	 * Initialise the protocol with an endopint and a manager.
	 * @param endpoint
	 * @param manager
	 */
	public KeepAliveProtocol(Endpoint endpoint, Manager manager) {
		super(endpoint,manager);
	}

	/**
	 * replyReceived = indication if client received reply from server for previous round
	 * 	Initialised to false to assume client hasn't receive a reply
	 * requestReceived = indication if server received request from client for previous round
	 * 	Initialised to true to avoid 20s timing issues, so client have a 40s window to send
	 */
	private boolean replyReceived = false;
	private boolean requestReceived = true;

	/**
	 * @return the name of the protocol
	 */
	@Override
	public String getProtocolName() {
		return protocolName;
	}

	/**
	 * Generate log output that KeepAliveProtocol was stopped while still underway
	 */
	@Override
	public void stopProtocol() {
		log.severe("protocol stopped while underway");
	}
	
	/*
	 * Interface methods
	 */
	
	/**
	 * Start server timer
	 */
	public void startAsServer() {
		// Start a 20s client timeout check
		checkClientTimeout();
	}
	
	/**
	 * Server checks if client timed out.
	 * Resets requestReceived every 20s, and times out if client did not send a KeepAliveRequest 20s after a reset.
	 */
	public void checkClientTimeout() {
		Utils.getInstance().setTimeout(() -> {
			{
				if (requestReceived) {
					requestReceived = false;
					checkClientTimeout();
				} else {
					// If previous reply didnt fall through and 20s passed, stop protocol
					manager.endpointTimedOut(endpoint, this);
				}
			}
		}, 20000);

	}
	
	/**
	 * Client manager sends initial request
	 */
	public void startAsClient() throws EndpointUnavailable {
		sendRequest(new KeepAliveRequest());
	}

	/**
	 * Send a request to server, reset state and schedule the next message
	 * Comment out sendRequest statement to simulate a client not sending subsequent KeepAliveRequests,
	 * causing server to call time out
	 *
	 * @param msg
	 */
	@Override
	public void sendRequest(Message msg) throws EndpointUnavailable {
		replyReceived = false;
		endpoint.send(msg);

 		Utils.getInstance().setTimeout(() -> {
 			try {
 		 		if (replyReceived) {
 		 			sendRequest(new KeepAliveRequest());
                } else {
 		 			// If no reply received from server after 20s, stop protocol
 		 			manager.endpointTimedOut(endpoint, this);
                }
 			} catch (EndpointUnavailable e) {
 				// ignore...
 			}
 		 }, 20000);

	}

	/**
	 * A reply from server came through for previous round
	 * @param msg
	 */
	@Override
	public void receiveReply(Message msg) {
		if(msg instanceof KeepAliveReply) {
			replyReceived = true;
		}
	}

	/**
	 * A request from client came through for previous round, and reset the 20s timer
	 * Comment out to simulate server not sending reply, causing client to call timeout
	 * @param msg
	 * @throws EndpointUnavailable 
	 */
	@Override
	public void receiveRequest(Message msg) throws EndpointUnavailable {
		 if(msg instanceof KeepAliveRequest) {
		 	sendReply(new KeepAliveReply());
		 	requestReceived = true;
		 }

	}

	/**
	 * Server side send a reply
	 * @param msg
	 */
	@Override
	public void sendReply(Message msg) throws EndpointUnavailable {
		endpoint.send(msg);
	}
	
	
}
