package bgu.spl.net.srv;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.impl.stomp.StompFrame;
import bgu.spl.net.impl.stomp.User;

public class ConnectionsImpl <T> implements Connections <T>{
    private final Map<Integer, ConnectionHandler<T>> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, String>> channelToSubscribers = new ConcurrentHashMap<>();
    //FOR channelToSubscribers: left string-BIG channel, right Map- SUBSCRIBERS
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final AtomicInteger messageIdCounter = new AtomicInteger(1);

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }



    @Override
    public void send(String channel, T msg) {
        Map<Integer, String> subs = channelToSubscribers.get(channel);
        if (subs != null && msg instanceof StompFrame) {
            StompFrame originalFrame = (StompFrame) msg;

            for (Map.Entry<Integer, String> entry : subs.entrySet()) {
                Integer connId = entry.getKey();
                String subId = entry.getValue();
                Map<String, String> messageHeaders = new HashMap<>();
                messageHeaders.put("destination", channel);
                messageHeaders.put("subscription", subId);
                messageHeaders.put("message-id", String.valueOf(messageIdCounter.getAndIncrement()));
                StompFrame messageFrame = new StompFrame("MESSAGE",messageHeaders,originalFrame.getBody());
                send(connId, (T) messageFrame);
            }

        }

    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }
    
    @Override
    public void disconnect(int connectionId){
        activeConnections.remove(connectionId);
        for(Map<Integer, String> channelsubs:channelToSubscribers.values()){
            channelsubs.remove(connectionId);
        }
    }

    @Override
    public void unsubscribe(int connectionId, String channel){
        Map<Integer,String> mychannelsubs = channelToSubscribers.get(channel);
        if(mychannelsubs!=null)
            mychannelsubs.remove(connectionId);
    }



    @Override
    public void subscribe(int connectionId,String channel, String subscriptionId){
        if(channelToSubscribers.containsKey(channel)){
            Map<Integer,String> mychannelsubs = channelToSubscribers.get(channel);
            mychannelsubs.put(connectionId,subscriptionId);
            System.out.println("DEBUG: Client " + connectionId + " subscribed to channel: [" + channel + "]");
        
        }
        else{
        Map<Integer, String> channelSubs=new ConcurrentHashMap<>();
        channelSubs.put(connectionId, subscriptionId);
        channelToSubscribers.put(channel,channelSubs);
        System.out.println("DEBUG: Client " + connectionId + " subscribed to channel: [" + channel + "]");
        }

    }

    public User getUser(String username) {
        return users.get(username);
    }

    public void addUser(String username, String password) {
        users.put(username, new User(username, password));
    }
    
}   
