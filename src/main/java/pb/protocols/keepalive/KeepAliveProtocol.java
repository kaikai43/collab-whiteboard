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
	 * requestReceived = indication if server received request from client for previous round
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
	 * Signal the protocol to stop. More specifically this method
	 * is called when the protocol should not undertake any more
	 * actions, such as processing messages or sending messages.
	 *
	 * Protocol stops when a timeout occurs
	 */
	@Override
	public void stopProtocol() {
		manager.endpointTimedOut(endpoint, this);
		Utils.getInstance().cleanUp();
	}
	
	/*
	 * Interface methods
	 */
	
	/**
	 * Start 20s timer for client's keepAliveRequest to arrive
	 */
	public void startAsServer() {
		// Start a 20s client timeout timer
		checkClientTimeout();
	}
	
	/**
	 * TODO: Server side: check if 20s timer is up
	 */
	public void checkClientTimeout() {
		if (requestReceived) {
			requestReceived = false;
		} else {
			stopProtocol();
		}
	}
	
	/**
	 * Called by the manager that is acting as a client.
	 */
	public void startAsClient() throws EndpointUnavailable {
		// Send request and start 2 20s timers: 1 for server reply and 1 for sending KeepAliveRequest
		sendRequest(new KeepAliveRequest());

	}

	/**
	 * Send a request to server, reset state and run Timeout after
	 * @param msg
	 */
	@Override
	public void sendRequest(Message msg) throws EndpointUnavailable {
		endpoint.send(msg);
		replyReceived = false;

		// Send request again after 5 seconds, if we receive a reply previously
		Utils.getInstance().setTimeout(() -> {
			try {
				if (replyReceived) {
					sendRequest(new KeepAliveRequest());
				}
				else {
					stopProtocol();
				}
			} catch (EndpointUnavailable e) {
				//ignore...
			}
		}, 5000);
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
	 * @param msg
	 * @throws EndpointUnavailable 
	 */
	@Override
	public void receiveRequest(Message msg) throws EndpointUnavailable {
		if(msg instanceof KeepAliveRequest) {
			// Reset server-side timeout timer and send KeepAliveReply to client
			sendReply(new KeepAliveReply());
			requestReceived = true;
			Utils.getInstance().cleanUp();
			checkClientTimeout();
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
