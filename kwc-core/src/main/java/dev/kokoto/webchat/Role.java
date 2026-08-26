package dev.kokoto.webchat;

public enum Role {
    GUEST(0),
    USER(1),
    MODERATOR(2),
    ADMIN(3);

    private final int level;

    Role(int level) {
        this.level = level;
    }

    public boolean atLeast(Role other) {
        return this.level >= other.level;
    }

    public static Role fromString(String value, Role fallback) {
        if (value == null) return fallback;
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
