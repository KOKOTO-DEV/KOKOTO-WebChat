package dev.kokoto.webchat;

/** Loader-neutral browser authentication boundary used by the web server. */
public interface WebChatAuth {
    WebAuthCode issueCode();
    String pollStatusJson(String pollToken, String ip);
    String login(String username, String password, String ip);
    String setPassword(String token, String password);
    String me(String token);

    /** Whether a session role may be used from the resolved client IP. */
    default boolean roleAllowedFromIp(Role role, String ip) { return true; }
}
