package bgu.spl.net.impl.stomp;

public class User {
    private final String username;
    private final String password;
    private boolean isLogged;
    private int connectionId; 

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.isLogged = false;
        this.connectionId = -1; 
    }

    public String getUsername() { 
        return username; 

    }

    public String getPassword() {
        return password;
    }
    
    public boolean isLogged() {
        return isLogged;
        }
    public void setLogged(boolean logged) {
        isLogged = logged;
        }

    public int getConnectionId() { return connectionId; }
    public void setConnectionId(int connectionId) {
        this.connectionId = connectionId;
        }

}
