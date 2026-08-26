package dev.kokoto.webchat;

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
