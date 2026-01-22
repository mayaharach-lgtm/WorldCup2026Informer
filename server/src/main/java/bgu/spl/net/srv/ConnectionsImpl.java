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
                StompFrame personalized = new StompFrame("MESSAGE", new HashMap<>(), originalFrame.getBody());
                personalized.getMap().putAll(originalFrame.getMap());
                personalized.addHeader("subscription", subId);
                personalized.addHeader("message-id", String.valueOf(messageIdCounter.getAndIncrement()));
                send(connId, (T) personalized);
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
        channelToSubscribers.computeIfAbsent(channel, k -> new ConcurrentHashMap<>())
                .put(connectionId, subscriptionId);


    }

    public User getUser(String username) {
        return users.get(username);
    }

    public void addUser(String username, String password) {
        users.put(username, new User(username, password));
    }

    public boolean isSubscribed(int connectionId, String channel) {
        Map<Integer, String> subs = channelToSubscribers.get(channel);
        return subs != null && subs.containsKey(connectionId);
    }

    public enum LoginStatus { OK, NEW_USER, WRONG_PASSWORD, ALREADY_LOGGED_IN, MISSING_FIELDS }

    public LoginStatus tryLogin(String username, String passcode, int connectionId) {
        if (username == null || passcode == null) 
            return LoginStatus.MISSING_FIELDS;
        final LoginStatus[] res = new LoginStatus[] { LoginStatus.MISSING_FIELDS };
        users.compute(username, (k, u) -> {
            if (u == null) {
                User nu = new User(username, passcode);
                nu.setLogged(true);
                nu.setConnectionId(connectionId);
                res[0] = LoginStatus.NEW_USER;
                return nu;
            }

            if (!u.getPassword().equals(passcode)) {
                res[0] = LoginStatus.WRONG_PASSWORD;
                return u;
            }

            if (u.isLogged()) {
                res[0] = LoginStatus.ALREADY_LOGGED_IN;
                return u;
            }

            u.setLogged(true);
            u.setConnectionId(connectionId);
            res[0] = LoginStatus.OK;
            return u;
        });

        return res[0];
    }

    public void logout(String username, int connectionId) {
    if (username == null) return;
    users.computeIfPresent(username, (k, u) -> {
        if (u.getConnectionId() == connectionId) {
            u.setLogged(false);
            u.setConnectionId(-1);
        }
        return u;
    });
}
    
}   
