/* KWC 파일 안내 / KWC file guide
 * PresenceHarness는 validation 모듈의 KWC 구현 파일이다. 클래스 이름이 나타내는 책임을 이 파일 안에 한정해 다른 계층과의 결합을 줄인다.
 * PresenceHarness is a KWC implementation file in the validation module. Keep the responsibility implied by the class name localized here to reduce cross-layer coupling.
 *
 * 변경 시 호출자와 반환값뿐 아니라 인증/권한, thread context, persistence, multi-loader 호환성에 미치는 영향을 함께 확인한다.
 * When changing it, review not only callers/returns but also effects on authorization, thread context, persistence, and multi-loader compatibility.
 */
import dev.kokoto.webchat.Account;
import dev.kokoto.webchat.CoreLogger;
import dev.kokoto.webchat.PresencePolicy;
import dev.kokoto.webchat.UserPreferenceStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PresenceHarness {
    private static int assertions;
    private static void check(boolean value, String name) {
        assertions++;
        if (!value) throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        var both = PresencePolicy.resolve(true, true, "online", false);
        check(both.online(), "both online");
        check("game".equals(both.source()), "game preferred over web");
        check("online".equals(both.status()), "manual online visible while connected");
        check(both.gameOnline() && both.webOnline(), "both underlying states visible");

        var web = PresencePolicy.resolve(false, true, "online", false);
        check(web.online() && "web".equals(web.source()), "web-only representative");
        var disconnected = PresencePolicy.resolve(false, false, "online", false);
        check(!disconnected.online() && "offline".equals(disconnected.source()) && "offline".equals(disconnected.status()), "disconnected representative");

        var busy = PresencePolicy.resolve(true, true, "busy", false);
        check(busy.online(), "busy remains online");
        check("busy".equals(busy.status()), "busy manual status visible");
        check("game".equals(busy.source()), "busy still prefers game representative");
        check(busy.gameOnline() && busy.webOnline(), "busy does not hide underlying connections");

        var hiddenOther = PresencePolicy.resolve(true, true, "offline", false);
        check(!hiddenOther.online(), "offline preference masks representative online");
        check("offline".equals(hiddenOther.status()), "offline preference status");
        check(!hiddenOther.gameOnline() && !hiddenOther.webOnline(), "offline preference masks raw game/web from others");
        check(!hiddenOther.invisible(), "privacy flag not exposed to others");

        var hiddenSelf = PresencePolicy.resolve(true, true, "offline", true);
        check(hiddenSelf.gameOnline() && hiddenSelf.webOnline(), "self sees underlying game/web");
        check("game".equals(hiddenSelf.source()), "self still gets game representative");
        check("offline".equals(hiddenSelf.status()), "self sees chosen offline status");
        check(hiddenSelf.invisible(), "self sees legacy invisible compatibility flag");

        Path root = Files.createTempDirectory("kwc-presence-pref-");
        UserPreferenceStore prefs = new UserPreferenceStore(root, CoreLogger.of(x -> {}, x -> {}));
        Account account = new Account();
        account.id = "presence-user";
        account.uuid = "00000000-0000-0000-0000-000000000001";
        account.username = "PresenceUser";
        check("online".equals(prefs.presencePreferences(account).get("status")), "presence status defaults online");
        check(Boolean.FALSE.equals(prefs.presencePreferences(account).get("invisible")), "legacy invisible default false");

        var savedBusy = prefs.savePresencePreferences(account, Map.of("status", "busy"));
        check(savedBusy.ok() && "busy".equals(savedBusy.value().get("status")), "save busy status");
        check(Boolean.FALSE.equals(savedBusy.value().get("invisible")), "busy is not invisible");

        var savedOffline = prefs.savePresencePreferences(account, Map.of("status", "offline"));
        check(savedOffline.ok() && "offline".equals(savedOffline.value().get("status")), "save offline status");
        check(Boolean.TRUE.equals(savedOffline.value().get("invisible")), "offline writes legacy invisible compatibility flag");

        UserPreferenceStore reloaded = new UserPreferenceStore(root, CoreLogger.of(x -> {}, x -> {}));
        check("offline".equals(reloaded.presencePreferences(account).get("status")), "presence status persists across store reload");
        check(Boolean.TRUE.equals(reloaded.presencePreferences(account).get("invisible")), "legacy invisible persists across store reload");

        var legacyOnline = prefs.savePresencePreferences(account, Map.of("invisible", "false"));
        check(legacyOnline.ok() && "online".equals(legacyOnline.value().get("status")), "legacy invisible false migrates to online");
        var legacyOffline = prefs.savePresencePreferences(account, Map.of("invisible", "true"));
        check(legacyOffline.ok() && "offline".equals(legacyOffline.value().get("status")), "legacy invisible true migrates to offline");
        var invalid = prefs.savePresencePreferences(account, Map.of("status", "away"));
        check(!invalid.ok(), "invalid presence status rejected");

        System.out.println("PRESENCE_HARNESS_PASS assertions=" + assertions);
    }
}
