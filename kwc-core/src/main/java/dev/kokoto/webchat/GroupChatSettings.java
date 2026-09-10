package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * GroupChatSettings는 KWC 설정을 core가 사용할 수 있는 형태로 읽거나 보관하는 설정 계층이다.
 * GroupChatSettings is part of the configuration layer that reads or carries KWC settings in a core-friendly form.
 *
 * 설정 키를 바꿀 때는 canonical config, 과거 baseline, migration, 다국어 template, 문서 reference가 함께 움직여야 한다.
 * When changing a setting key, update canonical config, historical baselines, migration, localized templates, and documentation references together.
 */
/** Immutable configuration snapshot used by the platform-neutral group-chat store. */
public final class GroupChatSettings {
    public final boolean groupChatEnabled;
    public final String groupChatSqliteFile;
    public final int groupChatRetentionDays;
    public final int groupChatMaxMessagesPerRoom;
    public final int groupChatMaxRoomsPerUser;
    public final int groupChatMaxMembersPerRoom;
    public final int groupChatInviteExpireHours;
    public final int groupChatMaxRoomNameLength;
    public final int groupChatMaxMessageLength;
    public final boolean groupChatAllowPublicRooms;
    public final boolean groupChatAllowRoomPasswords;

    public GroupChatSettings(boolean enabled, String sqliteFile, int retentionDays, int maxMessagesPerRoom,
                             int maxRoomsPerUser, int maxMembersPerRoom, int inviteExpireHours,
                             int maxRoomNameLength, int maxMessageLength,
                             boolean allowPublicRooms, boolean allowRoomPasswords) {
        this.groupChatEnabled = enabled;
        this.groupChatSqliteFile = sqliteFile;
        this.groupChatRetentionDays = retentionDays;
        this.groupChatMaxMessagesPerRoom = maxMessagesPerRoom;
        this.groupChatMaxRoomsPerUser = maxRoomsPerUser;
        this.groupChatMaxMembersPerRoom = maxMembersPerRoom;
        this.groupChatInviteExpireHours = inviteExpireHours;
        this.groupChatMaxRoomNameLength = maxRoomNameLength;
        this.groupChatMaxMessageLength = maxMessageLength;
        this.groupChatAllowPublicRooms = allowPublicRooms;
        this.groupChatAllowRoomPasswords = allowRoomPasswords;
    }
}
