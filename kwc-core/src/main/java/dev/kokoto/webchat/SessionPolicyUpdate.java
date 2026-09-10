package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 세션/접속 정책이 바뀌었을 때 적용할 변경 결과를 표현한다.
 * Represents a policy update to apply when session/access rules change.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
/** Summary returned after existing sessions are recalculated against a changed lifetime policy. */
public record SessionPolicyUpdate(int updated, int expired) {
    public static final SessionPolicyUpdate NONE = new SessionPolicyUpdate(0, 0);
}
