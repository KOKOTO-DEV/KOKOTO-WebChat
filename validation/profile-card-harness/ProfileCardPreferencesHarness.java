package dev.kokoto.webchat;

/* KWC 파일 안내 / KWC file guide
 * ProfileCardPreferencesHarness는 5.3.0 프로필 카드의 공개 preference와 전용 아바타 저장 회귀를 검증한다.
 * ProfileCardPreferencesHarness verifies public profile-card preferences and dedicated avatar persistence for 5.3.0.
 */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ProfileCardPreferencesHarness {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("kwc-profile-card-");
        try {
            Account account = new Account();
            account.id = "tester";
            account.username = "tester";
            account.uuid = "11111111-2222-3333-4444-555555555555";
            UserPreferenceStore store = new UserPreferenceStore(root, null);

            Map<String,Object> initial = store.profileCardPreferences(account);
            check("minecraft".equals(initial.get("avatarMode")), "default avatar mode is Minecraft Head");
            check("".equals(initial.get("about")), "default about");

            UserPreferenceStore.SaveResult saved = store.saveProfileCardPreferences(account, Map.of(
                    "about", "hello\u0000 world\r\nline2",
                    "avatarMode", "minecraft"
            ));
            check(saved.ok(), "profile card save");
            check("minecraft".equals(saved.value().get("avatarMode")), "minecraft avatar mode");
            check("hello world\nline2".equals(saved.value().get("about")), "about control cleanup");

            String longAbout = "x".repeat(400);
            saved = store.saveProfileCardPreferences(account, Map.of("about", longAbout));
            check(saved.ok(), "long about save");
            check(String.valueOf(saved.value().get("about")).length() == 280, "about length limit");

            byte[] fakePng = new byte[]{(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3};
            UserPreferenceStore.SaveResult avatar = store.saveProfileAvatar(account, "png", fakePng);
            check(avatar.ok(), "avatar save");
            check("custom".equals(avatar.value().get("avatarMode")), "avatar save selects custom mode");
            check("png".equals(avatar.value().get("avatarExtension")), "avatar extension");
            Path avatarFile = store.profileAvatarFile(account);
            check(avatarFile != null && Files.isRegularFile(avatarFile), "avatar file exists");
            check(avatarFile.toString().contains("profile-assets"), "avatar uses dedicated profile asset directory");
            check(Files.readAllBytes(avatarFile).length == fakePng.length, "avatar bytes preserved by store");

            saved = store.saveProfileCardPreferences(account, Map.of("avatarMode", "none"));
            check(saved.ok() && "minecraft".equals(saved.value().get("avatarMode")), "legacy none mode falls back to Minecraft Head without deleting custom file");
            check(store.profileAvatarFile(account) != null, "custom avatar retained for later reuse");

            saved = store.saveProfileCardPreferences(account, Map.of("avatarMode", "custom"));
            check(saved.ok() && "custom".equals(saved.value().get("avatarMode")), "reuse custom avatar");

            UserPreferenceStore.SaveResult bad = store.saveProfileCardPreferences(account, Map.of("avatarMode", "javascript"));
            check(!bad.ok() && "invalid_profile_avatar_mode".equals(bad.error()), "invalid avatar mode rejected");

            check(store.deleteProfileAvatar(account), "avatar delete reports deletion");
            check(store.profileAvatarFile(account) == null, "avatar file deleted");
            check("minecraft".equals(store.profileCardPreferences(account).get("avatarMode")), "delete resets custom mode to Minecraft Head");

            UserPreferenceStore.SaveResult badExt = store.saveProfileAvatar(account, "svg", new byte[]{1});
            check(!badExt.ok(), "unsafe avatar extension rejected");

            System.out.println("PROFILE_CARD_PREFERENCES_PASS assertions=" + assertions);
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
