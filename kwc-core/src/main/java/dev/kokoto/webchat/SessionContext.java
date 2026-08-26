package dev.kokoto.webchat;

public class SessionContext {
    public final Account account;
    public final Session session;

    public SessionContext(Account account, Session session) {
        this.account = account;
        this.session = session;
    }
}
