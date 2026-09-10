package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 게임에서 발급해 웹 로그인에 사용하는 단기 인증 코드를 표현한다.
 * Represents a short-lived authentication code issued in-game for web login.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
/** Public view of a browser-to-game authentication link code. */
public class WebAuthCode {
    public String code;
    public String pollToken;
    public long expiresAt;
}
