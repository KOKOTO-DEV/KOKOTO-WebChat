package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 원격 read receipt 적용 결과를 caller에게 명시적으로 반환한다.
 * Explicit result returned after applying a remote read receipt.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
/** Platform-neutral result of applying a remote DM read receipt. */
public final class RelayReadApplyResult {
    public final boolean ok;
    public final boolean changed;
    public final String error;
    public final String localUserUuid;
    public final String remoteUserUuid;
    public final String threadId;

    public RelayReadApplyResult(boolean ok, boolean changed, String error,
                                String localUserUuid, String remoteUserUuid, String threadId) {
        this.ok = ok;
        this.changed = changed;
        this.error = nz(error);
        this.localUserUuid = nz(localUserUuid);
        this.remoteUserUuid = nz(remoteUserUuid);
        this.threadId = nz(threadId);
    }

    public static RelayReadApplyResult failed(String error) {
        return new RelayReadApplyResult(false, false, error, "", "", "");
    }

    private static String nz(String value) { return value == null ? "" : value; }
}
