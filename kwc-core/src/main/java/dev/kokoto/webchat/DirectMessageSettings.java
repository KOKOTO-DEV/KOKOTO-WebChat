package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * DirectMessageSettings는 KWC 설정을 core가 사용할 수 있는 형태로 읽거나 보관하는 설정 계층이다.
 * DirectMessageSettings is part of the configuration layer that reads or carries KWC settings in a core-friendly form.
 *
 * 설정 키를 바꿀 때는 canonical config, 과거 baseline, migration, 다국어 template, 문서 reference가 함께 움직여야 한다.
 * When changing a setting key, update canonical config, historical baselines, migration, localized templates, and documentation references together.
 */
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
