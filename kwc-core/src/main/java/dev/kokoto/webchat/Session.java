package dev.kokoto.webchat;

public class Session {
    public String tokenHash;
    public String accountId;
    public long createdAt;
    public long expiresAt;
    public String lastIp;

    public boolean expired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }
}
