package dev.kokoto.webchat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Platform-neutral Server-Sent Events connection metadata and writer. */
public final class SseConnection implements AutoCloseable {
    private final OutputStream out;
    private final String ip;
    private final String accountUuid;
    private final String token;
    private final boolean privateChatSuperAdmin;
    private volatile boolean open = true;

    SseConnection(OutputStream out, String ip, String accountUuid, String token, boolean privateChatSuperAdmin) {
        this.out = out;
        this.ip = ip == null ? "" : ip;
        this.accountUuid = accountUuid == null ? "" : accountUuid;
        this.token = token == null ? "" : token;
        this.privateChatSuperAdmin = privateChatSuperAdmin;
    }

    public String ip() { return ip; }
    public String accountUuid() { return accountUuid; }
    public String token() { return token; }
    public boolean privateChatSuperAdmin() { return privateChatSuperAdmin; }
    public boolean isOpen() { return open; }
    public boolean hasToken() { return token != null && !token.isBlank(); }

    public synchronized void sendRaw(String value) throws IOException {
        if (!open) throw new IOException("closed");
        out.write(String.valueOf(value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() {
        open = false;
        try {
            out.close();
        } catch (IOException ignored) {
        }
    }
}
