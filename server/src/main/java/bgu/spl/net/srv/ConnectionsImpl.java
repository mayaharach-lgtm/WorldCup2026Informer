package bgu.spl.net.srv;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl <T> implements Connections <T>{

    private final Map<Integer, ConnectionHandler<T>> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, String>> channelToSubscribers = new ConcurrentHashMap<>();
    
    @Override
    public boolean send(int connectionId , T msg){
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if(handler!=null ){
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg){
        Map<Integer, String> subs = channelToSubscribers.get(channel);
        if(subs!=null ){
            for(Integer connectionID:subs.keySet()){
                send(connectionID , msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId){
        activeConnections.remove(connectionId);
        for(Map<Integer, String> channelsubs:channelToSubscribers.values()){
            channelsubs.remove(connectionId);
        }
    }
}
