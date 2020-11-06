package pb.app;

import org.apache.commons.codec.binary.Base64;
import pb.IndexServer;
import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.IOThread;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;


/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {
	private static Logger log = Logger.getLogger(WhiteboardApp.class.getName());
	
	/**
	 * Emitted to another peer to subscribe to updates for the given board. Argument
	 * must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String listenBoard = "BOARD_LISTEN";

	/**
	 * Emitted to another peer to unsubscribe to updates for the given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unlistenBoard = "BOARD_UNLISTEN";

	/**
	 * Emitted to another peer to get the entire board data for a given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String getBoardData = "GET_BOARD_DATA";

	/**
	 * Emitted to another peer to give the entire board data for a given board.
	 * Argument must have format "host:port:boardid%version%PATHS".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardData = "BOARD_DATA";

	/**
	 * Emitted to another peer to add a path to a board managed by that peer.
	 * Argument must have format "host:port:boardid%version%PATH". The numeric value
	 * of version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

	/**
	 * Emitted to another peer to indicate a new path has been accepted. Argument
	 * must have format "host:port:boardid%version%PATH". The numeric value of
	 * version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

	/**
	 * Emitted to another peer to remove the last path on a board managed by that
	 * peer. Argument must have format "host:port:boardid%version%". The numeric
	 * value of version must be equal to the version of the board without the undo
	 * applied, i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

	/**
	 * Emitted to another peer to indicate an undo has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the undo applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

	/**
	 * Emitted to another peer to clear a board managed by that peer. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

	/**
	 * Emitted to another peer to indicate an clear has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

	/**
	 * Emitted to another peer to indicate a board no longer exists and should be
	 * deleted. Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardDeleted = "BOARD_DELETED";

	/**
	 * Emitted to another peer to indicate an error has occurred.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardError = "BOARD_ERROR";
	
	/**
	 * White board map from board name to board object 
	 */
	Map<String,Whiteboard> whiteboards;
	
	/**
	 * The currently selected white board
	 */
	Whiteboard selectedBoard = null;
	
	/**
	 * The peer:port string of the peer. This is synonomous with IP:port, host:port,
	 * etc. where it may appear in comments.
	 */
	String peerport="standalone"; // a default value for the non-distributed version

	/**
	 * port to use when contacting the index server
	 */
	private static int whiteboardServerPort; // default port number for index server

	/**
	 * host to use when contacting the index server
	 */
	private static String whiteboardServerHost; // default host for the index server


	PeerManager peerManager; // Manages all connections
	ClientManager indexClientManager; // Client manager that connects to index server
	Endpoint indexEndpoint; // Endpoint that connects to the index server
	ClientManager listenClientManager; // Client manager that listens for board from a peer host
	Endpoint listenEndpoint; //Endpoint that listens for a board from a peer host

	/**
	 * Maps whiteboard name to endpoints of all peers listening to the whiteboard
	 */
	Map<String, Set<Endpoint>> listeningPeers;
	
	/*
	 * GUI objects, you probably don't need to modify these things... you don't
	 * need to modify these things... don't modify these things [LOTR reference?].
	 */
	
	JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
	JCheckBox sharedCheckbox ;
	DrawArea drawArea;
	JComboBox<String> boardComboBox;
	boolean modifyingComboBox=false;
	boolean modifyingCheckBox=false;
	
	/**
	 * Initialize the white board app.
	 */
	public WhiteboardApp(int peerPort,String whiteboardServerHost, 
			int whiteboardServerPort) {
		whiteboards=new HashMap<>();
		listeningPeers=new HashMap<>();
		this.whiteboardServerPort = whiteboardServerPort;
		this.whiteboardServerHost = whiteboardServerHost;
		this.peerport = whiteboardServerHost+":"+peerPort; //Since threads are local, serverIP = peerIP
		this.peerManager = new PeerManager(peerPort);
		startPeerManager();
		show(peerport);

		// Jin: Should heavily reference with the logic for FileSharingPeer
	}
	
	/******
	 * 
	 * Utility methods to extract fields from argument strings.
	 * 
	 ******/
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer:port:boardid
	 */
	public static String getBoardName(String data) {
		String[] parts=data.split("%",2);
		return parts[0];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return boardid%version%PATHS
	 */
	public static String getBoardIdAndData(String data) {
		String[] parts=data.split(":");
		return parts[2];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version%PATHS
	 */
	public static String getBoardData(String data) {
		String[] parts=data.split("%",2);
		return parts[1];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version
	 */
	public static long getBoardVersion(String data) {
		String[] parts=data.split("%",3);
		return Long.parseLong(parts[1]);
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return PATHS
	 */
	public static String getBoardPaths(String data) {
		String[] parts=data.split("%",3);
		return parts[2];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer
	 */
	public static String getIP(String data) {
		String[] parts=data.split(":");
		return parts[0];
	}
	
	/**
	 * 
	 * @param data = peer:port:boardid%version%PATHS
	 * @return port
	 */
	public static int getPort(String data) {
		String[] parts=data.split(":");
		return Integer.parseInt(parts[1]);
	}
	
	/******
	 * 
	 * Methods called from events.
	 * 
	 ******/

	/**
	 * Start up peer manager
	 */
	private void startPeerManager() {
		peerManager.on(PeerManager.peerStarted, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			onConnectionFromPeerClient(endpoint);
		}).on(PeerManager.peerStopped,(args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Disconnected from whiteboard peer: "+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerError,(args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("There was an error communicating with the whiteboard peer: "
					+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerServerManager, (args)->{
			ServerManager serverManager = (ServerManager)args[0];
			serverManager.on(IOThread.ioThread, (args2)->{
				String peerport = (String) args2[0];
				onPeerStartup(peerport);
			});
		});
		peerManager.start();
	}

	/**
	 * Actions taken upon peer manager initialisation of its server manager
	 * @param peerport: IP of peer host, host:port
	 */
	private void onPeerStartup(String peerport){
		log.info(peerport +" successfully established as peer host.");
		try {
			indexClientManager = peerManager.connect(whiteboardServerPort, whiteboardServerHost);
			shareBoards(indexClientManager);
		} catch (InterruptedException e) {
			System.out.println("Interrupted while trying to listen from whiteboard server.");
		} catch (UnknownHostException e) {
			System.out.println("Unable to locate whiteboard server while trying to listen from it.");
		}
	}
	
	// From whiteboard server
	/**
	 * Peer action upon starting connection with whiteboard server
	 */
	private void onConnectionWithServer(Endpoint endpoint) {
		System.out.println("Connected to whiteboard server: " + endpoint.getOtherEndpointId());
		System.out.println("Listening to boards shared....");
		endpoint.on(WhiteboardServer.error, (args2) -> {
			String errorMessage = (String) args2[0];
			System.out.println("Whiteboard server failed to share board: "
					+ errorMessage);
		}).on(WhiteboardServer.sharingBoard, (args2)-> {
			String sharedBoardName = (String) args2[0];
			// create an empty remote board if not in whiteboards list, request from peer only upon board selection
			if (!whiteboards.containsKey(sharedBoardName)){
				System.out.println("Received shared board: "+ sharedBoardName);
				Whiteboard whiteboard = new Whiteboard(sharedBoardName,true);
				addBoard(whiteboard,false);
			} else {
				// Peer is owner of board, or already had this board in menu, do nothing
			}
		}).on(WhiteboardServer.unsharingBoard, (args2)-> {
			String unsharedBoardName = (String) args2[0];
			System.out.println("Received unshared board: "+unsharedBoardName);
			// remove a remote board if not in whiteboards list
			if (whiteboards.containsKey(unsharedBoardName)){
				deleteBoard(unsharedBoardName);
			} else {
				//Board does not exist, log info and do nothing
				log.info("The unshared board is not present in peer. Continuing...");
			}
		});
	}

	/**
	 * Subscribe to boards shared from the index server
	 */
	private void shareBoards(ClientManager clientManager) {
		clientManager.on(PeerManager.peerStarted, (args)-> {
			Endpoint endpoint = (Endpoint) args[0];
			this.indexEndpoint = endpoint;
			onConnectionWithServer(indexEndpoint);
		}).on(WhiteboardServer.error, (args)->{
			String rejectedBoardName = (String) args[0];
			System.out.println("Whiteboard server failed to (un)share board: "
					+rejectedBoardName);
		}).on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Disconnected from the whiteboard server: "+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerError, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("There was an error communicating with the whiteboard server: "
					+endpoint.getOtherEndpointId());
		});
		clientManager.start();
		// Thread doesnt end until peerManager.shutdown() called, no need to .join()
	}

	/**
	 * Emit shared board event using endpoint connected to index server
	 */
	private void uploadSharedBoard(String boardName, String peerport) {
		System.out.println("Transmitting shared board: "+ boardName +" to whiteboard server.");
		indexEndpoint.emit(WhiteboardServer.shareBoard, boardName);
		log.info("Peer " + peerport + " successfully shared board "+boardName);
		log.info("Initialising list of listeners for board: "+boardName);
		Set<Endpoint> activeEndpoints = new HashSet<Endpoint>();
		listeningPeers.put(boardName, activeEndpoints);
	}

	/**
	 * Emit shared board event using endpoint connected to index server
	 */
	private void uploadUnsharedBoard(String boardName, String peerport) {
		System.out.println("Transmitting unshared board: "+ boardName +" to whiteboard server.");
		indexEndpoint.emit(WhiteboardServer.unshareBoard, boardName);
		log.info("Peer " + peerport + " successfully shared board "+boardName);
	}


	// From whiteboard peer
	/**
	 * Connect to whiteboard peer that is hosting the shared board and obtain board info
	 * @param boardName Board shared by peer host, peer:port:boardid
	 */
	private void getBoardDataFromPeer(String boardName){
		String host = getIP(boardName) + ":" + getPort(boardName); // For logging purposes
		ClientManager clientManager;
		try {
			clientManager = peerManager.connect(getPort(boardName), getIP(boardName));
			this.listenClientManager = clientManager;
		} catch (InterruptedException e) {
			System.out.println("Interrupted while trying to connect to peer host: "+host);
			return;
		} catch (UnknownHostException e) {
			System.out.println("Could not find the peer host: "+host);
			return;
		}
		clientManager.on(PeerManager.peerStarted, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Connected to host peer: "+endpoint.getOtherEndpointId());
			this.listenEndpoint = endpoint;
			onConnectionToPeerHost(clientManager, listenEndpoint, boardName);
		}).on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Disconnected from peer host: "+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerError, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("There was error while communication with peer host: "
					+endpoint.getOtherEndpointId());
		});
		clientManager.start();
	}

	/**
	 * Peer actions upon starting connection with peer host
	 * @param clientManager: clientManager responsible for connection to peer host
	 * @param endpoint: endpoint responsible for connection to peer host
	 * @param boardName: Board shared by peer host, peer:port:boardid
	 */
	private void onConnectionToPeerHost(ClientManager clientManager,
										Endpoint endpoint, String boardName){
		endpoint.on(boardData,(args2)->{
			String receivedData = (String) args2[0];
			onBoardData(receivedData);
		}).on(boardPathAccepted, (args2)->{
			String boardNameAndData = (String) args2[0];
			onBoardPathAccepted(boardNameAndData);
		}).on(boardUndoAccepted, (args2)->{
			String boardNameAndVer = (String) args2[0];
			// accept and undo (via whiteboard.addPath) if version number is same (w/o undo applied yet)
			// emit boardUndoAccepted to all endpoints listening to current board
		}).on(boardClearAccepted, (args2)->{
			String boardNameAndVer = (String) args2[0];
			// accept and clear (via whiteboard.addPath) if version number is same (w/o clear applied yet)
			// emit boardClearAccepted to all endpoints listening to current board
		}).on(boardDeleted, (args2)->{
			//TODO
			String deletedBoardName = (String) args2[0];
			System.out.println("Host peer deleted board: "+deletedBoardName);
			clientManager.shutdown();
		}).on(boardError, (args2)->{
			System.out.println("Error receiving board data");
			clientManager.shutdown();
		});
		System.out.println("Getting board "+boardName+" from "+endpoint.getOtherEndpointId());
		System.out.println("Listening to board from peer host: "+ boardName);
		endpoint.emit(listenBoard, boardName);
		System.out.println("Requesting board data from peer host: "+ boardName);
		endpoint.emit(getBoardData, boardName);
	}

	/**
	 * Actions upon receiving boardData event, involves board initialisation
	 * @param boardNameAndData: string received through boardData event
	 */
	private void onBoardData(String boardNameAndData){
		// Initialise board
		String boardName = getBoardName(boardNameAndData);
		String boardData = getBoardData(boardNameAndData);
		Whiteboard boardToInitialise = whiteboards.get(boardName);
		boardToInitialise.whiteboardFromString(boardName, boardData);
		drawSelectedWhiteboard();
		log.info("selected board: "+selectedBoard.getName());
	}

	/**
	 * Actions taken by clients listening to particular board
	 * Basically, just redraw board according to data received from peer host
	 *
	 * @param boardNameAndData: String data received from peer host, host:port:boardid%ver%PATHS
	 */
	private void onBoardPathAccepted(String boardNameAndData){
		String boardName = getBoardName(boardNameAndData);
		String boardData = getBoardData(boardNameAndData);
		Whiteboard boardToUpdate = whiteboards.get(boardName);
		boardToUpdate.whiteboardFromString(boardName, boardData);
		// Redraw board if selected
		if (selectedBoard == boardToUpdate) {
			drawSelectedWhiteboard();
		}
	}

	/**
	 * Actions taken by peer host upon connection from client to listen to a board
	 * @param endpoint: endpoint responsible for connection to peer client
	 */
	private void onConnectionFromPeerClient(Endpoint endpoint){
		System.out.println("Connection from peer: "+endpoint.getOtherEndpointId());
		System.out.println("Listening for shared boards from peer....");
		endpoint.on(listenBoard, (args2)->{
			String boardToListen = (String) args2[0];
			onBoardListen(boardToListen, endpoint); // Add endpoint ot list of listening endpoints
		}).on(unlistenBoard, (args2)->{
			String boardToUnlisten = (String) args2[0];
			onBoardUnlisten(boardToUnlisten, endpoint); // Remove endpoint from list of listening endpoints
		}).on(getBoardData, (args2)->{
			String boardToGet = (String) args2[0];
			onGetBoardData(boardToGet, endpoint); // Convert board to string and send to listening client
		}).on(boardPathUpdate, (args2)->{
			String boardNameAndData = (String) args2[0];
			onBoardPathUpdate(boardNameAndData, endpoint);
		}).on(boardUndoUpdate, (args2)->{
			String boardNameAndVer = (String) args2[0];
			// accept and undo (via whiteboard.addPath) if version number is same (w/o undo applied yet)
			// emit boardUndoAccepted to all endpoints listening to current board
		}).on(boardClearUpdate, (args2)->{
			String boardNameAndVer = (String) args2[0];
			// accept and clear (via whiteboard.addPath) if version number is same (w/o clear applied yet)
			// emit boardClearAccepted to all endpoints listening to current board
		}).on(boardDeleted, (args2)->{
			String boardName = (String) args2[0];
			// Remove endpoint from list of listening endpoints
		});
	}

	/**
	 * Add endpoint to list of endpoints currently listening to the board
	 * @param boardName: Name of board to be listened
	 * @param endpoint: Endpoint used to communicate with client listening to board
	 */
	private void onBoardListen(String boardName, Endpoint endpoint){
		log.info("Adding to list of boards available for listening: "+boardName);
		synchronized (listeningPeers) {
			if (listeningPeers.containsKey(boardName)) {
				Set<Endpoint> activeEndpoints = listeningPeers.get(boardName);
				activeEndpoints.add(endpoint);
			} else {
				endpoint.emit(boardError, "Board is not shared!");
			}
		}
	}

	/**
	 * Remove endpoint to list of endpoints currently listening to the board
	 * @param boardName: Name of board to be listened
	 * @param endpoint: Endpoint used to communicate with client listening to board
	 */
	private void onBoardUnlisten(String boardName, Endpoint endpoint){
		log.info("Removing from endpoint from list of active endpoints: "+boardName);
		synchronized (listeningPeers) {
			if (listeningPeers.containsKey(boardName)) {
				Set<Endpoint> activeEndpoints = listeningPeers.get(boardName);
				activeEndpoints.remove(endpoint);
			} else {
				// Trying to remove endpoint that doesnt exist
				endpoint.emit(boardError, "Endpoint does not exist in list of active endpoints.");
			}
		}
	}

	/**
	 * Creating board string and send it (boardData) to receiver
	 * @param boardName: Name corresponding to board of interest
	 * @param endpoint: Endpoint that emits to peer
	 */
	private void onGetBoardData(String boardName, Endpoint endpoint){
		Whiteboard boardToGet = whiteboards.get(boardName);
		System.out.println("Transmitting board data: " + boardName
				+ " to peer: "+ endpoint.getOtherEndpointId());
		endpoint.emit(boardData, boardToGet.toString());
	}

	/**
	 * Actions taken by host peer upon receiving update by a listening client
	 * @param boardNameAndData: Info for board to be updated. host:port:boardid%ver%path
	 * @param endpoint: Endpoint connected to peer that updates the hosted board
	 */
	private void onBoardPathUpdate(String boardNameAndData, Endpoint endpoint){
		String boardName = getBoardName(boardNameAndData);
		Long updatedBoardVersion = getBoardVersion(boardNameAndData);
		String boardData = getBoardData(boardNameAndData);
		synchronized (listeningPeers){
			if (!listeningPeers.containsKey(boardName)){
				System.out.println("Peer client updated a board that is not shared!");
			} else {
				Whiteboard boardToUpdate = whiteboards.get(boardName);
				// If hosted board version one less than updated version
				// add path and transmit to all listening peers
				if (boardToUpdate.getVersion() == --updatedBoardVersion) {
					boardToUpdate.whiteboardFromString(boardName, boardData);
					// Redraw board if selected
					if (selectedBoard==boardToUpdate) {
						drawSelectedWhiteboard();
					}
					Set<Endpoint> activeEndpoints = listeningPeers.get(boardName);
					for (Endpoint e: activeEndpoints){
						// Skip transmission to peer client that first sent the update
						if (e != endpoint){
							e.emit(boardPathAccepted, boardNameAndData);
						}
					}
				} else {
					endpoint.emit(boardError, "Version mismatch with host peer!");
				}
			}
		}
	}

	/******
	 * 
	 * Methods to manipulate data locally. Distributed systems related code has been
	 * cut from these methods.
	 * 
	 ******/
	
	/**
	 * Wait for the peer manager to finish all threads.
	 */
	public void waitToFinish() {
		peerManager.joinWithClientManagers();
		peerManager.shutdown();
	}
	
	/**
	 * Add a board to the list that the user can select from. If select is
	 * true then also select this board.
	 * @param whiteboard
	 * @param select
	 */
	public void addBoard(Whiteboard whiteboard,boolean select) {
		synchronized(whiteboards) {
			whiteboards.put(whiteboard.getName(), whiteboard);
		}
		updateComboBox(select?whiteboard.getName():null);
	}
	
	/**
	 * Delete a board from the list.
	 * @param boardname must have the form peer:port:boardid
	 */
	public void deleteBoard(String boardname) {
		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardname);
			if(whiteboard!=null) {
				whiteboards.remove(boardname);
			}
		}
		updateComboBox(null);
	}
	
	/**
	 * Create a new local board with name peer:port:boardid.
	 * The boardid includes the time stamp that the board was created at.
	 */
	public void createBoard() {
		String name = peerport+":board"+Instant.now().toEpochMilli();
		Whiteboard whiteboard = new Whiteboard(name,false);
		addBoard(whiteboard,true);
	}
	
	/**
	 * Add a path to the selected board. The path has already
	 * been drawn on the draw area; so if it can't be accepted then
	 * the board needs to be redrawn without it.
	 * @param currentPath
	 */
	public void pathCreatedLocally(WhiteboardPath currentPath) {
		if(selectedBoard!=null) {
			if(!selectedBoard.addPath(currentPath,selectedBoard.getVersion())) {
				// some other peer modified the board in between
				System.out.println("Another peer modified the board while drawing." +
						"Rejecting path drawn and redrawing board according to modification.");
				drawSelectedWhiteboard(); // just redraw the screen without the path
			} else {
				// was accepted locally, so do remote stuff if needed
				// If is a remote board, emit updated board info to host peer
				if (selectedBoard.isRemote()) {
					listenEndpoint.emit(boardPathUpdate, selectedBoard.toString());
				} else if (listeningPeers.containsKey(selectedBoard.getName())) {
					// If is board hosted by peer, emit boardPathAcccepted to all listening peers
					Set<Endpoint> activeEndpoints = listeningPeers.get(selectedBoard.getName());
					if (!activeEndpoints.isEmpty()) {
						for (Endpoint e: activeEndpoints){
							e.emit(boardPathAccepted, selectedBoard.toString());
						}
					}
				}
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("path created without a selected board: "+currentPath);
		}
	}
	
	/**
	 * Clear the selected whiteboard.
	 */
	public void clearedLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.clear(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				// was accepted locally, so do remote stuff if needed
				
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("cleared without a selected board");
		}
	}
	
	/**
	 * Undo the last path of the selected whiteboard.
	 */
	public void undoLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.undo(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("undo without a selected board");
		}
	}
	
	/**
	 * The variable selectedBoard has been set.
	 */
	public void selectedABoard() {
		//Remote: get board data from host peer, then use thread listen to updates
		if (selectedBoard.isRemote()){
			getBoardDataFromPeer(selectedBoard.getName());
		}  else {
			// Emit unlisten event and terminate previous client session
			if (listenClientManager!=null){
				listenEndpoint.emit(unlistenBoard, selectedBoard.getName());
				listenClientManager.shutdown();
				listenClientManager=null; // Not sure if needed, but just in case
			}
			drawSelectedWhiteboard();
			log.info("selected board: "+selectedBoard.getName());
		}

	}
	
	/**
	 * Set the share status on the selected board.
	 */
	public void setShare(boolean share) {
		if(selectedBoard!=null) {
        	selectedBoard.setShared(share);
        	if (share){
				uploadSharedBoard(selectedBoard.getName(), peerport);
			}
			else {
				uploadUnsharedBoard(selectedBoard.getName(), peerport);
			}
		} else {
        	log.severe("there is no selected board");
        }
	}
	
	/**
	 * Called by the gui when the user closes the app.
	 */
	public void guiShutdown() {
		// do some final cleanup
		HashSet<Whiteboard> existingBoards= new HashSet<>(whiteboards.values());
		existingBoards.forEach((board)->{
			deleteBoard(board.getName());
		});
    	whiteboards.values().forEach((whiteboard)->{
    		//Unshare currently shared boards
			uploadUnsharedBoard(whiteboard.getName(), peerport);
		});
    	peerManager.shutdown();
		Utils.getInstance().cleanUp();
	}
	
	

	/******
	 * 
	 * GUI methods and callbacks from GUI for user actions.
	 * You probably do not need to modify anything below here.
	 * 
	 ******/
	
	/**
	 * Redraw the screen with the selected board
	 */
	public void drawSelectedWhiteboard() {
		drawArea.clear();
		if(selectedBoard!=null) {
			selectedBoard.draw(drawArea);
		}
	}
	
	/**
	 * Setup the Swing components and start the Swing thread, given the
	 * peer's specific information, i.e. peer:port string.
	 */
	public void show(String peerport) {
		// create main frame
		JFrame frame = new JFrame("Whiteboard Peer: "+peerport);
		Container content = frame.getContentPane();
		// set layout on content pane
		content.setLayout(new BorderLayout());
		// create draw area
		drawArea = new DrawArea(this);

		// add to content pane
		content.add(drawArea, BorderLayout.CENTER);

		// create controls to apply colors and call clear feature
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		/**
		 * Action listener is called by the GUI thread.
		 */
		ActionListener actionListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == clearBtn) {
					clearedLocally();
				} else if (e.getSource() == blackBtn) {
					drawArea.setColor(Color.black);
				} else if (e.getSource() == redBtn) {
					drawArea.setColor(Color.red);
				} else if (e.getSource() == boardComboBox) {
					if(modifyingComboBox) return;
					if(boardComboBox.getSelectedIndex()==-1) return;
					String selectedBoardName=(String) boardComboBox.getSelectedItem();
					if(whiteboards.get(selectedBoardName)==null) {
						log.severe("selected a board that does not exist: "+selectedBoardName);
						return;
					}
					selectedBoard = whiteboards.get(selectedBoardName);
					// remote boards can't have their shared status modified
					if(selectedBoard.isRemote()) {
						sharedCheckbox.setEnabled(false);
						sharedCheckbox.setVisible(false);
					} else {
						modifyingCheckBox=true;
						sharedCheckbox.setSelected(selectedBoard.isShared());
						modifyingCheckBox=false;
						sharedCheckbox.setEnabled(true);
						sharedCheckbox.setVisible(true);
					}
					selectedABoard();
				} else if (e.getSource() == createBoardBtn) {
					createBoard();
				} else if (e.getSource() == undoBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to undo");
						return;
					}
					undoLocally();
				} else if (e.getSource() == deleteBoardBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to delete");
						return;
					}
					deleteBoard(selectedBoard.getName());
				}
			}
		};
		
		clearBtn = new JButton("Clear Board");
		clearBtn.addActionListener(actionListener);
		clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
		clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		blackBtn = new JButton("Black");
		blackBtn.addActionListener(actionListener);
		blackBtn.setToolTipText("Draw with black pen");
		blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		redBtn = new JButton("Red");
		redBtn.addActionListener(actionListener);
		redBtn.setToolTipText("Draw with red pen");
		redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		deleteBoardBtn = new JButton("Delete Board");
		deleteBoardBtn.addActionListener(actionListener);
		deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
		deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		createBoardBtn = new JButton("New Board");
		createBoardBtn.addActionListener(actionListener);
		createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
		createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		undoBtn = new JButton("Undo");
		undoBtn.addActionListener(actionListener);
		undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
		undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		sharedCheckbox = new JCheckBox("Shared");
		sharedCheckbox.addItemListener(new ItemListener() {    
	         public void itemStateChanged(ItemEvent e) { 
	            if(!modifyingCheckBox) setShare(e.getStateChange()==1);
	         }    
	      }); 
		sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
		sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);
		

		// create a drop list for boards to select from
		JPanel controlsNorth = new JPanel();
		boardComboBox = new JComboBox<String>();
		boardComboBox.addActionListener(actionListener);
		
		
		// add to panel
		controlsNorth.add(boardComboBox);
		controls.add(sharedCheckbox);
		controls.add(createBoardBtn);
		controls.add(deleteBoardBtn);
		controls.add(blackBtn);
		controls.add(redBtn);
		controls.add(undoBtn);
		controls.add(clearBtn);

		// add to content pane
		content.add(controls, BorderLayout.WEST);
		content.add(controlsNorth,BorderLayout.NORTH);

		frame.setSize(600, 600);
		
		// create an initial board
		createBoard();
		
		// closing the application
		frame.addWindowListener(new WindowAdapter() {
		    @Override
		    public void windowClosing(WindowEvent windowEvent) {
		        if (JOptionPane.showConfirmDialog(frame, 
		            "Are you sure you want to close this window?", "Close Window?", 
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
		        {
		        	guiShutdown();
		            frame.dispose();
		        }
		    }
		});
		
		// show the swing paint result
		frame.setVisible(true);
		
	}
	
	/**
	 * Update the GUI's list of boards. Note that this method needs to update data
	 * that the GUI is using, which should only be done on the GUI's thread, which
	 * is why invoke later is used.
	 * 
	 * @param select, board to select when list is modified or null for default
	 *                selection
	 */
	private void updateComboBox(String select) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				modifyingComboBox=true;
				boardComboBox.removeAllItems();
				int anIndex=-1;
				synchronized(whiteboards) {
					ArrayList<String> boards = new ArrayList<String>(whiteboards.keySet());
					Collections.sort(boards);
					for(int i=0;i<boards.size();i++) {
						String boardname=boards.get(i);
						boardComboBox.addItem(boardname);
						if(select!=null && select.equals(boardname)) {
							anIndex=i;
						} else if(anIndex==-1 && selectedBoard!=null && 
								selectedBoard.getName().equals(boardname)) {
							anIndex=i;
						} 
					}
				}
				modifyingComboBox=false;
				if(anIndex!=-1) {
					boardComboBox.setSelectedIndex(anIndex);
				} else {
					if(whiteboards.size()>0) {
						boardComboBox.setSelectedIndex(0);
					} else {
						drawArea.clear();
						createBoard();
					}
				}
				
			}
		});
	}
	
}
