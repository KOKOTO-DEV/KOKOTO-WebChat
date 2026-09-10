package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * HTTP 요청을 처리할 때 해석된 현재 세션과 viewer 정보를 묶어 전달한다.
 * Carries the resolved session and viewer information while handling an HTTP request.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
public class SessionContext {
    public final Account account;
    public final Session session;

    public SessionContext(Account account, Session session) {
        this.account = account;
        this.session = session;
    }
}
