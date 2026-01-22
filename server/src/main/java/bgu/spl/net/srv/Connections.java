package bgu.spl.net.srv;

public interface Connections<T> {

    boolean send(int connectionId, T msg);

    void send(String channel, T msg);
    void disconnect(int connectionId);
    void subscribe(int connectionId, String channel, String subscriptionId);
    void unsubscribe(int connectionId, String channel);
}
