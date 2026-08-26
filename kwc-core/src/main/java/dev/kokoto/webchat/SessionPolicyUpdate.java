package dev.kokoto.webchat;

/** Summary returned after existing sessions are recalculated against a changed lifetime policy. */
public record SessionPolicyUpdate(int updated, int expired) {
    public static final SessionPolicyUpdate NONE = new SessionPolicyUpdate(0, 0);
}
