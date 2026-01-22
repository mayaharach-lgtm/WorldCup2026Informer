package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class StompMessagingProtocolimpl implements StompMessagingProtocol<StompFrame> {

    private boolean shouldTerminate = false;
    private int connectionId;
    private ConnectionsImpl<StompFrame> connections;
    private boolean loggedIn = false;
    private String userName = null;
    
    // Local mapping to track subId -> channel for this specific connection
    private final Map<String, String> subIdToChannel = new HashMap<>();
    private final Database database = Database.getInstance();

    @Override
    public void start(int connectionId, Connections<StompFrame> connections) {
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl<StompFrame>) connections;
    }

    @Override
    public void process(StompFrame message) {

        String command = message.getCommand();

        // Security check: Only CONNECT is allowed if not logged in
        if (!loggedIn && !command.equals("CONNECT")) {
            sendError("You must be logged in to perform this action", message);
            return;
        }

        // Using IF-ELSE logic as requested, following the reference code's behavior
        if (command.equals("CONNECT")) {
            handleConnect(message);
        } 
        else if (command.equals("SEND")) {
            handleSend(message);
        } 
        else if (command.equals("SUBSCRIBE")) {
            handleSubscribe(message);
        } 
        else if (command.equals("UNSUBSCRIBE")) {
            handleUnsubscribe(message);
        } 
        else if (command.equals("DISCONNECT")) {
            handleDisconnect(message);
        } 
        else {
            sendError("Unknown command", message);
        }

        // Send receipt if requested and the connection isn't closing
        if (!shouldTerminate) {
            checkAndSendReceipt(message);
        }
        if (shouldTerminate) {
            terminateConnection();
        }
    }

    private void handleConnect(StompFrame message) {
        String login = message.GetHeader("login");
        String passcode = message.GetHeader("passcode");

        bgu.spl.net.impl.data.LoginStatus st = database.login(connectionId, login, passcode);

        if (st == bgu.spl.net.impl.data.LoginStatus.ADDED_NEW_USER || st == bgu.spl.net.impl.data.LoginStatus.LOGGED_IN_SUCCESSFULLY) {
            this.loggedIn = true;
            this.userName = login;
            sendConnected();
        } 
        else if (st == bgu.spl.net.impl.data.LoginStatus.WRONG_PASSWORD) {
            sendError("Wrong password", message);
        } 
        else if (st == bgu.spl.net.impl.data.LoginStatus.ALREADY_LOGGED_IN || st == bgu.spl.net.impl.data.LoginStatus.CLIENT_ALREADY_CONNECTED) {
            sendError("User already logged in", message);
        } 
    }




    private void handleSend(StompFrame message) {
        String destination = message.GetHeader("destination");
        if (destination == null || destination.isEmpty()) {
            sendError("unvalid SEND frame: missing destination", message);
            return;
        }
        if (!connections.isSubscribed(connectionId, destination)) {
            sendError("User not subscribed to channel " + destination, message);
            return;
        }

        // Check for file upload headers (optional implementation based on requirement to track files)
        String filename = message.GetHeader("filename");
        if (filename == null) {
            filename = message.GetHeader("file");
        }
        
        if (filename != null && !filename.isEmpty()) {
            database.trackFileUpload(this.userName, filename, destination);
        }

        connections.send(destination, message);
    }

    private void handleSubscribe(StompFrame message) {
        String destination = message.GetHeader("destination");
        String subId = message.GetHeader("id");

        if (destination == null || subId == null) {
            sendError("unvalid SUBSCRIBE frame: missing destination or id", message);
            return;
        }

        // Register subscription in both database and local protocol map
        try {
            int id = Integer.parseInt(subId);
            database.subscribeToGame(destination, connectionId, id);
            subIdToChannel.put(subId, destination);
            
            // Note: Connections implementation might also track this
            connections.subscribe(connectionId, destination, subId);
        } catch (NumberFormatException e) {
            sendError("Invalid subscription ID format", message);
        }
    }

    private void handleUnsubscribe(StompFrame message) {
        String subId = message.GetHeader("id");
        if (subId == null) {
            sendError("unvalid UNSUBSCRIBE frame: missing id", message);
            return;
        }

        try {
            String destination = subIdToChannel.remove(subId);
            if (destination != null) {
                database.unsubscribeFromGame(connectionId, Integer.parseInt(subId));
                connections.unsubscribe(connectionId, destination);
            } else {
                sendError("Subscription ID not found", message);
            }
        } catch (NumberFormatException e) {
            sendError("Invalid subscription ID format", message);
        }
    }

    private void handleDisconnect(StompFrame message) {
        checkAndSendReceipt(message);
        shouldTerminate = true;
        // connection logout is handled in terminateConnection which calls database.logout
    }

    private void sendError(String errorMsg, StompFrame originalFrame) {
        Map<String, String> headers = new HashMap<>();
        headers.put("message", errorMsg);

        String receiptId = originalFrame.GetHeader("receipt");
        if (receiptId != null) {
            headers.put("receipt-id", receiptId);
        }

        String body = "The error message: " + errorMsg + "\n\nOriginal frame:\n---\n"
                + originalFrame.toStompString() + "\n---";

        connections.send(connectionId, new StompFrame("ERROR", headers, body));
        shouldTerminate = true;
    }


    @Override
    public void terminateConnection() {
        database.unsubscribeFromAll(connectionId);
        database.logout(connectionId);
        connections.disconnect(connectionId);
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void checkAndSendReceipt(StompFrame frame) {
        String receiptId = frame.GetHeader("receipt");
        if (receiptId != null) {
            Map<String, String> headers = new HashMap<>();
            headers.put("receipt-id", receiptId);
            connections.send(connectionId, new StompFrame("RECEIPT", headers, ""));
        }
    }

    private void sendConnected() {
        Map<String, String> headers = new HashMap<>();
        headers.put("version", "1.2");
        connections.send(connectionId, new StompFrame("CONNECTED", headers, ""));
    }
}