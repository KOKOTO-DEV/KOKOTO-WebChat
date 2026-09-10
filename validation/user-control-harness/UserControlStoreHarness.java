package dev.kokoto.webchat;

/* KWC 파일 안내 / KWC file guide
 * UserControlStoreHarness는 사용자별 관리자 제재, 모데레이터 위임 권한, 개인 사용자 차단 preference의 저장/해제를 검증한다.
 * UserControlStoreHarness verifies persistence and removal of per-user administrative restrictions, delegated moderator capabilities, and personal user-block preferences.
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UserControlStoreHarness {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("kwc-user-controls-");
        try {
            UserControlStore controls = new UserControlStore(root, null);
            String uuid = "11111111-2222-3333-4444-555555555555";

            Map<String,Object> empty = controls.restrictions(uuid);
            check(Boolean.FALSE.equals(empty.get("chatBanned")), "chat ban defaults off");
            check(Boolean.FALSE.equals(empty.get("uploadBanned")), "upload ban defaults off");
            check(controls.setRestrictions(uuid, true, false), "restriction save");
            check(controls.chatBanned(uuid), "chat ban persisted");
            check(!controls.uploadBanned(uuid), "upload ban stays off");

            UserControlStore reloaded = new UserControlStore(root, null);
            check(reloaded.chatBanned(uuid), "chat ban survives reload");
            check(reloaded.setRestrictions(uuid, false, true), "restriction update");
            check(!reloaded.chatBanned(uuid) && reloaded.uploadBanned(uuid), "restriction update applied");
            check(reloaded.setRestrictions(uuid, false, false), "restriction clear");
            check(!reloaded.chatBanned(uuid) && !reloaded.uploadBanned(uuid), "restriction clear applied");

            Map<String,Boolean> defaults = controls.moderatorCapabilities(uuid);
            check(Boolean.TRUE.equals(defaults.get("view-online")), "legacy moderator view-online default");
            check(Boolean.TRUE.equals(defaults.get("message-delete")), "legacy moderator message-delete default");
            check(Boolean.TRUE.equals(defaults.get("guest-mute")), "legacy moderator guest-mute default");
            check(Boolean.TRUE.equals(defaults.get("pin-manage")), "legacy moderator pin-manage default");
            check(Boolean.FALSE.equals(defaults.get("user-restrictions")), "new user-restrictions default off");
            check(Boolean.FALSE.equals(defaults.get("profile-avatar-delete")), "new avatar-delete default off");
            check(!controls.moderatorAllowed(uuid, "server-settings"), "unknown capability denied");

            LinkedHashMap<String,Boolean> delegated = new LinkedHashMap<>();
            delegated.put("view-online", false);
            delegated.put("message-delete", false);
            delegated.put("guest-mute", true);
            delegated.put("pin-manage", false);
            delegated.put("user-restrictions", true);
            delegated.put("profile-avatar-delete", true);
            check(controls.setModeratorCapabilities(uuid, delegated), "moderator capability save");
            Map<String,Boolean> savedCaps = new UserControlStore(root, null).moderatorCapabilities(uuid);
            for (Map.Entry<String,Boolean> entry : delegated.entrySet()) {
                check(entry.getValue().equals(savedCaps.get(entry.getKey())), "capability persisted: " + entry.getKey());
            }

            Account owner = new Account();
            owner.id = "owner"; owner.username = "owner"; owner.uuid = uuid;
            UserPreferenceStore prefs = new UserPreferenceStore(root.resolve("prefs"), null);
            String target = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
            check(prefs.blockedUsers(owner).isEmpty(), "personal block list defaults empty");
            UserPreferenceStore.SaveResult blocked = prefs.setUserBlocked(owner, target, true);
            check(blocked.ok(), "personal block save");
            check(prefs.isUserBlocked(owner, target.toUpperCase()), "personal block normalized and persisted");
            check(!prefs.setUserBlocked(owner, uuid, true).ok(), "self block rejected");
            UserPreferenceStore.SaveResult unblocked = prefs.setUserBlocked(owner, target, false);
            check(unblocked.ok(), "personal unblock save");
            check(!prefs.isUserBlocked(owner, target), "personal unblock applied");

            check(UserControlStore.normalizeUuid(" A/B:C_1.~ ").equals("ab:c_1.~"), "control-store id normalization");
            System.out.println("USER_CONTROL_STORE_PASS assertions=" + assertions);
        } finally {
            deleteTree(root);
        }
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private static void deleteTree(Path path) throws Exception {
        if (path == null || !Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path p : stream.sorted((a,b) -> b.getNameCount() - a.getNameCount()).toList()) Files.deleteIfExists(p);
        }
    }
}
