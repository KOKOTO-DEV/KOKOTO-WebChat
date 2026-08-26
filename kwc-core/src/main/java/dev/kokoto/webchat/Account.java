package dev.kokoto.webchat;

public class Account {
    public String id;
    public String username;
    public String uuid;
    public String lastDisplayName;
    public Role role = Role.USER;
    public String passwordHash;
    public boolean local;
    public long createdAt;
    public long lastLogin;

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public String safeUsername() {
        return username == null ? id : username;
    }
}
