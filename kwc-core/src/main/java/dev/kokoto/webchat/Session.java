package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 웹 로그인 세션의 계정·토큰·만료 정보를 표현한다.
 * Represents account, token, and expiry information for a web login session.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
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
