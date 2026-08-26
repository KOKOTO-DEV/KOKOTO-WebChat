package dev.kokoto.webchat;

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
