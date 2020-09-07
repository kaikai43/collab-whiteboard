package pb.protocols.session;

import java.util.logging.Logger;

import pb.Endpoint;
import pb.EndpointUnavailable;
import pb.Manager;
import pb.Utils;
import pb.protocols.Message;
import pb.protocols.Protocol;
import pb.protocols.IRequestReplyProtocol;
import pb.protocols.keepalive.KeepAliveRequest;

/**
 * Allows the client to request the session to start and to request the session
 * to stop, which in turns allows the sockets to be properly closed at both
 * ends. Actually, either party can make such requests, but usually the client
 * would make the session start request as soon as it connects, and usually the
 * client would make the session stop request. The server may however send a
 * session stop request to the client if it wants (needs) to stop the session,
 * e.g. perhaps the server is becoming overloaded and needs to shed some
 * clients.
 * 
 * @see {@link pb.Manager}
 * @see {@link pb.Endpoint}
 * @see {@link pb.protocols.Protocol}
 * @see {@link pb.protocols.IRequestReplyProtocol}
 * @see {@link pb.protocols.session.SessionStartRequest}
 * @see {@link pb.protocols.session.SessionStartReply}
 * @see {@link pb.protocols.session.SessionStopRequest}
 * @see {@link pb.protocols.session.SessionStopReply}
 * @author aaron
 *
 */
public class SessionProtocol extends Protocol implements IRequestReplyProtocol {
	private static Logger log = Logger.getLogger(SessionProtocol.class.getName());
	
	/**
	 * The unique name of the protocol.
	 */
	public static final String protocolName="SessionProtocol";
	
	// Use of volatile is in case the thread that calls stopProtocol is different
	// to the endpoint thread, although in this case it hardly needed.
	
	/**
	 * Whether the protocol has started, i.e. start request and reply have been sent,
	 * or not.
	 */
	private volatile boolean protocolRunning=false;

	/**
	 * requestReceived = Indicates whether server receives SessionStartRequest from client
	 * replyReceived = Indicates whether client received SessionStartReply or SessionStopReply from server
	 */
	private boolean requestReceived = false;
	private boolean replyReceived = false;
	
	/**
	 * Initialise the protocol with an endpoint and manager.
	 * @param endpoint
	 * @param manager
	 */
	public SessionProtocol(Endpoint endpoint, Manager manager) {
		super(endpoint,manager);
	}
	
	/**
	 * @return the name of the protocol.
	 */
	@Override
	public String getProtocolName() {
		return protocolName;
	}

	/**
	 * If this protocol is stopped while it is still in the running
	 * state then this indicates something may be a problem.
	 */
	@Override
	public void stopProtocol() {
		if(protocolRunning) {
			log.severe("protocol stopped while it is still underway");
		}
	}
	
	/*
	 * Interface methods
	 */

	
	/**
	 * Called by the manager that is acting as a client.
	 * Comment out to simulate client not sending SessionStartRequest, causing server to call timeout
	 */
	@Override
	public void startAsClient() throws EndpointUnavailable {
		sendRequest(new SessionStartRequest());

	}

	/**
	 * Called by the manager that is acting as a server.
	 * Modified according to task 3, server call timeout if SessionStartRequest not received after 20s
	 */
	@Override
	public void startAsServer() {
		Utils.getInstance().setTimeout(() -> {
			{
				// Server calls timeout if SessionStartRequest not received after 30s
				if (!requestReceived) {
					manager.endpointTimedOut(endpoint, this);
				}
			}
		}, 20000);
	}
	
	/**
	 * Generic stop session call, for either client or server.
	 * @throws EndpointUnavailable if the endpoint is not ready or has terminated
	 */
	public void stopSession() throws EndpointUnavailable {
		sendRequest(new SessionStopRequest());
	}
	
	/**
	 * Modified according to task 3
	 * Send a request, if reply not received within 20s, client calls timeout
	 * Don't need to do anything if sending out SessionStartRequest, since initially replyReceived is false
	 * @param msg
	 */
	@Override
	public void sendRequest(Message msg) throws EndpointUnavailable {
		// If sending out a SessionStopRequest, reset replyReceived state
		if (msg instanceof SessionStopRequest){
			replyReceived = false;
		}
		endpoint.send(msg);

		// Client calls timeout if no reply received from server after 20s of sending any request
		Utils.getInstance().setTimeout(() -> {
			{
				if (!replyReceived) {
					manager.endpointTimedOut(endpoint, this);
				}
			}
		}, 20000);
	}

	/**
	 * If the reply is a session start reply then tell the manager that
	 * the session has started, otherwise if its a session stop reply then
	 * tell the manager that the session has stopped. If something weird 
	 * happens then tell the manager that something weird has happened.
	 * Modified according to task 3, indicates whether client receives any reply from server
	 * @param msg
	 */
	@Override
	public void receiveReply(Message msg) {
		if(msg instanceof SessionStartReply) {
			if(protocolRunning){
				// error, received a second reply?
				manager.protocolViolation(endpoint,this);
				return;
			}
			protocolRunning=true;
			replyReceived=true;
			manager.sessionStarted(endpoint);
		} else if(msg instanceof SessionStopReply) {
			if(!protocolRunning) {
				// error, received a second reply?
				manager.protocolViolation(endpoint,this);
				return;
			}
			protocolRunning=false;
			replyReceived=true;
			manager.sessionStopped(endpoint);
		}
	}

	/**
	 * If the received request is a session start request then reply and
	 * tell the manager that the session has started. If the received request
	 * is a session stop request then reply and tell the manager that
	 * the session has stopped. If something weird has happened then...
	 *
	 * Modified according to task 3, indicates whether server receives session start request
	 *
	 * Comment out sendReply and its subsequent manager.sessionStarted/Stopped statements
	 * to simulate server not sending out a particular reply, causing client timeout
	 *
	 * (Weird, if serverManager stopped its endpoint, clientManager wouldn't know and
	 * should wait for timeout instead, so shouldn't need to comment out manager.sessionStarted/Stopped,
	 * unless there something wrong with current implementation logic.
	 * Current implementation works on SessionStartRequest, ie do not need to comment out manager.sessionStarted, but
	 * not SessionStopRequest)
	 * @param msg
	 */
	@Override
	public void receiveRequest(Message msg) throws EndpointUnavailable {
		if(msg instanceof SessionStartRequest) {
			if(protocolRunning) {
				// error, received a second request?
				manager.protocolViolation(endpoint,this);
				return;
			}
			protocolRunning=true;
			requestReceived=true;
			sendReply(new SessionStartReply());
			manager.sessionStarted(endpoint);
		} else if(msg instanceof SessionStopRequest) {
			if(!protocolRunning) {
				// error, received a second request?
				manager.protocolViolation(endpoint,this);
				return;
			}
			protocolRunning=false;
			sendReply(new SessionStopReply());
			manager.sessionStopped(endpoint);
		}
		
	}

	/**
	 * Just send a reply, nothing special to do.
	 * @param msg
	 */
	@Override
	public void sendReply(Message msg) throws EndpointUnavailable {
		endpoint.send(msg);
	}

	

	
}
