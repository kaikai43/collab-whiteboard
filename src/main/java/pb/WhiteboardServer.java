package pb;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


import pb.app.WhiteboardApp;
import pb.managers.IOThread;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

/**
 * Simple whiteboard server to provide whiteboard peer notifications.
 * @author aaron
 *
 */
public class WhiteboardServer {
	private static Logger log = Logger.getLogger(WhiteboardServer.class.getName());
	
	/**
	 * Emitted by a client to tell the server that a board is being shared. Argument
	 * must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String shareBoard = "SHARE_BOARD";

	/**
	 * Emitted by a client to tell the server that a board is no longer being
	 * shared. Argument must have the format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unshareBoard = "UNSHARE_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is being shared</li>
	 * <li>to a newly connected client, it emits this event several times, for all
	 * boards that are currently known to be being shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String sharingBoard = "SHARING_BOARD";

	/**
	 * The server emits this event:
	 * <ul>
	 * <li>to all connected clients to tell them that a board is no longer
	 * shared</li>
	 * </ul>
	 * Argument has format "host:port:boardid"
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unsharingBoard = "UNSHARING_BOARD";

	/**
	 * Emitted by the server to a client to let it know that there was an error in a
	 * received argument to any of the events above. Argument is the error message.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String error = "ERROR";
	
	/**
	 * Default port number.
	 */
	private static int port = Utils.indexServerPort;

	/**
	 * Storage of boards and their hosting Whiteboard peer
	 * host:port -> boardId
	 */
	//public static final HashMap<String,Set<String>> keyValueMap=new HashMap<>();
	public static final HashSet<String>  sharedBoards = new HashSet<String>();

	/**
	 * Get peer that is hosting a board
	 * @param data: host:port:boardID
	 * @return host:port
	 */
	private static String getBoardHost(String data){
		String[] parts = data.split(":");
		return parts[0] + ":" + parts[1];
	}

	/**
	 * Get boardId for hosted board
	 * @param data: host:port:boardID
	 * @return boardId
	 */
	private static String getBoardId(String data){
		String[] parts = data.split(":");
		return parts[2];
	}

	/**
	 * Insert newly hosted board into list of currently shared boards
	 * when "shareBoard" event is received.
	 * @param data: host:port:boardID
	 */
	private static void sharedBoardsInsert(String data){
		//sharedBoards.add(data);
		synchronized (sharedBoards){
			sharedBoards.add(data);
		}
/*		//HashMap version of same implementation
		String host = getBoardHost(data);
		String boardId = getBoardId(data);
		synchronized (keyValueMap) {
			if(!keyValueMap.containsKey(host)) {
				keyValueMap.put(host, new HashSet<String>());
			}
			Set<String> sharedBoards=keyValueMap.get(host);
			sharedBoards.add(boardId);
		}
*/
	}

	/**
	 * Delete hosted board from list of currently shared boards
	 * when "unshareBoard" event is received.
	 * @param data: host:port:boardID
	 */
	private static void sharedBoardsDelete(String data) {
		//sharedBoards.remove(data);
		synchronized (sharedBoards) {
			sharedBoards.remove(data);
		}
	}
	
	private static void help(Options options){
		String header = "PB Whiteboard Server for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("pb.IndexServer", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main( String[] args ) throws IOException, InterruptedException
    {
    	// set a nice log format
		System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");
        
    	// parse command line options
        Options options = new Options();
        options.addOption("port",true,"server port, an integer");
        options.addOption("password",true,"password for server");
        
       
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
			cmd = parser.parse( options, args);
		} catch (ParseException e1) {
			help(options);
		}
        
        if(cmd.hasOption("port")){
        	try{
        		port = Integer.parseInt(cmd.getOptionValue("port"));
			} catch (NumberFormatException e){
				System.out.println("-port requires a port number, parsed: "+cmd.getOptionValue("port"));
				help(options);
			}
        }

        // create a server manager and setup event handlers
        ServerManager serverManager;
        
        if(cmd.hasOption("password")) {
        	serverManager = new ServerManager(port,cmd.getOptionValue("password"));
        } else {
        	serverManager = new ServerManager(port);
        }
        
        /**
         * TODO: Put some server related code here.
         */
        // Should heavily reference IndexServer
		serverManager.on(ServerManager.sessionStarted,(eventArgs)-> {
			Endpoint endpoint = (Endpoint)eventArgs[0];
			log.info("Client session started: "+endpoint.getOtherEndpointId());
			endpoint.on(shareBoard, (eventArgs2)->{
				String boardName = (String) eventArgs2[0];
				log.info("Received shared board: "+boardName);
				sharedBoardsInsert(boardName);
				// Pass on boardName to other clients
				log.info("Transmitting board share to all peers.");
				endpoint.emit(sharingBoard, boardName);
			}).on(unshareBoard, (eventArgs2)->{
				String boardName = (String) eventArgs2[0];
				log.info("Received unshared board: "+boardName);
				if (sharedBoards.contains(boardName)){
					sharedBoardsDelete(boardName);
				} else {
					//Peer trying to unshare a board that does not exist
					endpoint.emit(error, "Board does not exist.");
				}
				log.info("Transmitting board unshare to all peers.");
				endpoint.emit(unsharingBoard, boardName);
			});
			// Sharing currently share boards to newly connected clients
			if (!sharedBoards.isEmpty()){
				log.info("Transmitting all currently shared boards to client.");
				for (String board : sharedBoards){
					endpoint.emit(sharingBoard, board);
				}
			}
		}).on(IOThread.ioThread, (eventArgs)->{
			String peerport = (String) eventArgs[0];
			// we don't need this info, but let's log it
			log.info("using Internet address: "+peerport);
		});

		// Jin: Listen to peerStarted event from new clients, emit multiple sharingBoard events to that client
			// How do we keep track of all of the shared boards? maybe we add a Map of <Peer, Board>?
		// Jin: Listen to unshareBoard event from any client, emit unsharingBoard event to all other clients/all clients

        
        // start up the server
        log.info("Whiteboard Server starting up");
        serverManager.start();
        
    }

}
