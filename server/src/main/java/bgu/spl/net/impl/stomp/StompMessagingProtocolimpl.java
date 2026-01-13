package bgu.spl.net.impl.stomp;
import java.util.HashMap;
import java.util.Map;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class StompMessagingProtocolimpl implements StompMessagingProtocol <StompFrame> {
    
    private boolean shouldTerminate = false; 
    private int connectionId;
    private Connections <StompFrame> connections;
    private boolean loggedIn = false;
    private String userName=null;
    private final Map<String, String> subIdToChannel = new HashMap<>();
    
    @Override
    public void start(int connectionId, Connections<StompFrame> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }
    
    @Override
    public StompFrame process(StompFrame message){
        if (!loggedIn && !message.getCommand().equals("CONNECT")) {
        sendError("You must be logged in to perform this action", message);
        return null;
        }
        if (message.getCommand().equals("DISCONNECT")){
            shouldTerminate = true;
            checkAndSendReceipt(message); 
            ConnectionsImpl<StompFrame> conn = (ConnectionsImpl<StompFrame>) connections;
            User user = conn.getUser(this.userName);
            if (user != null) {
                user.setLogged(false);
            }
            connections.disconnect(this.connectionId);
        }
        else if(message.getCommand().equals("SEND")){
            String destination = message.GetHeader("destination");
            if (destination != null) {
                connections.send(destination,message);
                checkAndSendReceipt(message);
                String dest = message.GetHeader("destination");
                System.out.println("DEBUG: Received SEND to destination: [" + dest + "]");
            }
        }
    
        else if(message.getCommand().equals("SUBSCRIBE")){
            String destination = message.GetHeader("destination");
            String subId = message.GetHeader("id");
            connections.subscribe(connectionId,destination,subId);
            subIdToChannel.put(subId, destination);
            checkAndSendReceipt(message);
        }
        else if(message.getCommand().equals("UNSUBSCRIBE")){
            String subId = message.GetHeader("id");
            String channel = subIdToChannel.remove(subId); 
                if (channel != null) {
                    connections.unsubscribe(connectionId, channel);
                    checkAndSendReceipt(message);
                }
                else {
                    sendError("Subscription ID not found", message);
                }
        }  
        
        else if(message.getCommand().equals("CONNECT")){
            String username = message.GetHeader("login");
            String password = message.GetHeader("passcode");
            if (username == null || password == null) {
                System.out.println("Login or passcode missing");
                return null;
            }
            //if exists, get password from database
            ConnectionsImpl<StompFrame> conn = (ConnectionsImpl<StompFrame>) connections;
            User user = conn.getUser(username);
            
            if (user == null) {
                //new user- register
                conn.addUser(username, password);
                loginSuccess(username, message);
            }
            else if (!user.getPassword().equals(password)) {
                sendError("Wrong password", message);
                this.shouldTerminate = true; 
            } 
            else if (user.isLogged()) {
                sendError("User already logged in", message);
                this.shouldTerminate = true;
            } 
            else {
                loginSuccess(username, message);
            }
        }
        return null;
    }

    @Override
    public boolean shouldTerminate(){
        return this.shouldTerminate;
    }

    private void loginSuccess(String username, StompFrame frame) {
        this.loggedIn = true;
        this.userName = username;
        User user = ((ConnectionsImpl<StompFrame>) connections).getUser(username);
        user.setLogged(true);
        user.setConnectionId(this.connectionId);

        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("version", "1.2");
        connections.send(connectionId, new StompFrame("CONNECTED", responseHeaders, ""));

        checkAndSendReceipt(frame);
    }

    private void sendError(String errorMessage, StompFrame originalFrame) {
        Map<String, String> errorHeaders = new HashMap<>();
        errorHeaders.put("message", errorMessage);
        String receiptId = originalFrame.GetHeader("receipt");
        if (receiptId != null) {
            errorHeaders.put("receipt-id", receiptId);
        }
        connections.send(connectionId, new StompFrame("ERROR", errorHeaders, "The error message: " + errorMessage));
        this.shouldTerminate=true;
    }

    private void checkAndSendReceipt(StompFrame frame) {
        String receiptId = frame.GetHeader("receipt");
        if (receiptId != null) {
            Map<String, String> receiptHeaders = new HashMap<>();
            receiptHeaders.put("receipt-id", receiptId);
            connections.send(connectionId, new StompFrame("RECEIPT", receiptHeaders, ""));
        }
    }


}
