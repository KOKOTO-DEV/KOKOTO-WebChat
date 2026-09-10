package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * WebChatAuth는 인증·입력 검증·접속 제한 중 하나를 담당하는 보안 경계 코드다.
 * WebChatAuth is security-boundary code responsible for authentication, input validation, or access limiting.
 *
 * 화면에서 버튼을 숨기는 것은 권한 검사가 아니므로, 모든 민감한 작업은 서버에서 UUID/세션/권한을 다시 검증해야 한다.
 * Hiding a button is not authorization; every sensitive action must revalidate UUID/session/permission on the server.
 */
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
