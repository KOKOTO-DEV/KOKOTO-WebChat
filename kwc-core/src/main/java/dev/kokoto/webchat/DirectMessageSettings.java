package dev.kokoto.webchat;

/** Immutable configuration snapshot used by the platform-neutral direct-message store. */
public final class DirectMessageSettings {
    public final boolean directMessageEnabled;
    public final String directMessageStorage;
    public final String directMessageSqliteFile;
    public final String directMessageJsonlFile;
    public final int directMessageRetentionDays;
    public final int directMessageMaxMessagesPerThread;

    public DirectMessageSettings(boolean enabled, String storage, String sqliteFile, String jsonlFile,
                                 int retentionDays, int maxMessagesPerThread) {
        this.directMessageEnabled = enabled;
        this.directMessageStorage = storage;
        this.directMessageSqliteFile = sqliteFile;
        this.directMessageJsonlFile = jsonlFile;
        this.directMessageRetentionDays = retentionDays;
        this.directMessageMaxMessagesPerThread = maxMessagesPerThread;
    }
}
