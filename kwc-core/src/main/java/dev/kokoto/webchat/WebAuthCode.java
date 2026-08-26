package dev.kokoto.webchat;

/** Public view of a browser-to-game authentication link code. */
public class WebAuthCode {
    public String code;
    public String pollToken;
    public long expiresAt;
}
