package bgu.spl.net.impl.stomp;
import java.util.Map;

public class StompFrame {
    private final String command;
    private final Map<String,String> headers;
    private final String body;

    public StompFrame(String command, Map<String,String> headers, String body){
        this.command = command;
        this.headers = headers;
        this.body = body;
    }

    public String getCommand(){
        return this.command;
    }

    public String getBody(){
        return this.body;
    }
    
    public Map<String,String> getMap(){
        return this.headers;
    }

    public String GetHeader(String key){
        return this.headers.get(key);
    }

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }
    
    public String toStompString() {
    StringBuilder sb = new StringBuilder();
    sb.append(command).append("\n");
    for (Map.Entry<String, String> header : headers.entrySet()) {
        sb.append(header.getKey()).append(":").append(header.getValue()).append("\n");
    }
    sb.append("\n");
    if (body != null) {
        sb.append(body);
    }
    sb.append("\u0000");
    return sb.toString();
}
}

  