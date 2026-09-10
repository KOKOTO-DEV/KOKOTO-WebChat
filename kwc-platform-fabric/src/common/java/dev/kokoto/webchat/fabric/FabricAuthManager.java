package dev.kokoto.webchat.fabric;


/* KWC 파일 안내 / KWC file guide
 * FabricAuthManager는 인증·입력 검증·접속 제한 중 하나를 담당하는 보안 경계 코드다.
 * FabricAuthManager is security-boundary code responsible for authentication, input validation, or access limiting.
 *
 * 화면에서 버튼을 숨기는 것은 권한 검사가 아니므로, 모든 민감한 작업은 서버에서 UUID/세션/권한을 다시 검증해야 한다.
 * Hiding a button is not authorization; every sensitive action must revalidate UUID/session/permission on the server.
 */
import dev.kokoto.webchat.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FabricAuthManager implements WebChatAuth {
    public static class LinkCode extends WebAuthCode {
        public Account account;
        public boolean delivered;
    }

    public static class LinkResult {
        public boolean ok;
        public String message;
        public Account account;
    }

    private final KwcFabricRuntime plugin;
    private final FabricStorage storage;
    private final Map<String, LinkCode> byCode = new ConcurrentHashMap<>();
    private final Map<String, LinkCode> byPoll = new ConcurrentHashMap<>();
    private final Map<String, LoginFailState> loginFailures = new ConcurrentHashMap<>();

    private static final class LoginFailState {
        int count;
        long firstFailureAt;
        long lockedUntil;
    }

    public FabricAuthManager(KwcFabricRuntime plugin, FabricStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public LinkCode issueCode() {
        cleanup();
        ConfigValues config = plugin.configValues();
        String code;
        do {
            code = SecurityUtil.randomNumericCode(Math.max(4, config.linkCodeLength));
        } while (byCode.containsKey(code));

        LinkCode lc = new LinkCode();
        lc.code = code;
        lc.pollToken = SecurityUtil.randomToken(24);
        lc.expiresAt = System.currentTimeMillis() + Math.max(30, config.linkCodeExpireSeconds) * 1000L;
        byCode.put(lc.code, lc);
        byPoll.put(lc.pollToken, lc);
        return lc;
    }

    public LinkResult completeCode(String code, PlatformPlayer player) {
        cleanup();
        LinkResult result = new LinkResult();
        if (code == null || player == null) {
            result.ok = false;
            result.message = plugin.langManager().text("command.authInvalid", "Invalid authentication request.");
            return result;
        }

        LinkCode lc = byCode.get(code);
        if (lc == null || lc.expiresAt < System.currentTimeMillis()) {
            result.ok = false;
            result.message = plugin.langManager().text("command.authCodeExpired", "The authentication code is missing or expired.");
            return result;
        }

        ConfigValues config = plugin.configValues();
        Role role = Role.USER;
        if (config.autoAdminFromPermission && plugin.platformAdapter().hasPermission(player.uuid(), config.adminPermission)) {
            role = Role.ADMIN;
        }
        Account account = storage.upsertLinkedAccount(player.uuid().toString(), player.name(), role, player.displayName());
        lc.account = account;

        result.ok = true;
        result.message = plugin.langManager().text("command.authComplete", "Web chat account linking is complete. Return to your browser.");
        result.account = account;
        return result;
    }

    public String pollStatusJson(String pollToken, String ip) {
        cleanup();
        LinkCode lc = byPoll.get(pollToken);
        Map<String, Object> m = new LinkedHashMap<>();
        if (lc == null || lc.expiresAt < System.currentTimeMillis()) {
            m.put("ok", false);
            m.put("status", "expired");
            return JsonUtil.obj(m);
        }
        if (lc.account == null) {
            m.put("ok", true);
            m.put("status", "waiting");
            m.put("expiresAt", lc.expiresAt);
            return JsonUtil.obj(m);
        }

        if (lc.delivered) {
            m.put("ok", false);
            m.put("status", "delivered");
            return JsonUtil.obj(m);
        }

        if (lc.account.role == Role.ADMIN && !adminIpAllowed(ip)) {
            m.put("ok", false);
            m.put("status", "admin_ip_not_allowed");
            m.put("error", "admin_ip_not_allowed");
            return JsonUtil.obj(m);
        }

        lc.delivered = true;
        byCode.remove(lc.code);
        byPoll.remove(lc.pollToken);

        String token = storage.createSessionForDuration(lc.account, ip, sessionDurationMillis(lc.account));

        m.put("ok", true);
        m.put("status", "linked");
        m.put("token", token);
        m.put("username", lc.account.safeUsername());
        m.put("uuid", lc.account.uuid == null ? "" : lc.account.uuid);
        m.put("displayName", plugin.displayNameForAccount(lc.account));
        m.put("role", lc.account.role.name());
        m.put("passwordSet", lc.account.hasPassword());
        return JsonUtil.obj(m);
    }

    public String login(String username, String password, String ip) {
        Map<String, Object> m = new LinkedHashMap<>();
        ConfigValues config = plugin.configValues();
        if (!config.passwordLogin) {
            m.put("ok", false);
            m.put("error", "password_login_disabled");
            return JsonUtil.obj(m);
        }

        String userKey = loginUserKey(username);
        String ipKey = loginIpKey(ip);
        long lockedUntil = loginLockedUntil(userKey, ipKey);
        long now = System.currentTimeMillis();
        if (lockedUntil > now) {
            m.put("ok", false);
            m.put("error", "login_locked");
            m.put("retryAfterSeconds", Math.max(1, (int)Math.ceil((lockedUntil - now) / 1000.0)));
            return JsonUtil.obj(m);
        }

        Account account = storage.findAccountByUsername(username);
        if (account == null || !account.hasPassword() || !SecurityUtil.verifyPassword(password == null ? new char[0] : password.toCharArray(), account.passwordHash)) {
            lockedUntil = recordFailedLogin(userKey, ipKey);
            m.put("ok", false);
            m.put("error", lockedUntil > System.currentTimeMillis() ? "login_locked" : "invalid_login");
            if (lockedUntil > System.currentTimeMillis()) {
                m.put("retryAfterSeconds", Math.max(1, (int)Math.ceil((lockedUntil - System.currentTimeMillis()) / 1000.0)));
            }
            return JsonUtil.obj(m);
        }

        if (account.role == Role.ADMIN && !adminIpAllowed(ip)) {
            m.put("ok", false);
            m.put("error", "admin_ip_not_allowed");
            return JsonUtil.obj(m);
        }

        clearLoginFailures(userKey, ipKey);

        String token = storage.createSessionForDuration(account, ip, sessionDurationMillis(account));
        m.put("ok", true);
        m.put("token", token);
        m.put("username", account.safeUsername());
        m.put("uuid", account.uuid == null ? "" : account.uuid);
        m.put("displayName", plugin.displayNameForAccount(account));
        m.put("role", account.role.name());
        return JsonUtil.obj(m);
    }


    private long sessionDurationMillis(Account account) {
        ConfigValues config = plugin.configValues();
        if (account != null && account.role == Role.ADMIN) {
            long hours = config.adminSessionExpireHours;
            return hours <= 0L ? 0L : hours * 60L * 60L * 1000L;
        }
        int days = config.rememberSessionDays;
        return days <= 0 ? 0L : days * 24L * 60L * 60L * 1000L;
    }

    public String setPassword(String token, String password) {
        Map<String, Object> m = new LinkedHashMap<>();
        SessionContext ctx = storage.getSession(token);
        if (ctx == null) {
            m.put("ok", false);
            m.put("error", "not_logged_in");
            return JsonUtil.obj(m);
        }
        if (password == null || password.length() < 6) {
            m.put("ok", false);
            m.put("error", "password_too_short");
            return JsonUtil.obj(m);
        }
        storage.setPassword(ctx.account, password);
        m.put("ok", true);
        return JsonUtil.obj(m);
    }

    public String me(String token) {
        Map<String, Object> m = new LinkedHashMap<>();
        SessionContext ctx = storage.getSession(token);
        if (ctx == null) {
            m.put("ok", false);
            return JsonUtil.obj(m);
        }
        m.put("ok", true);
        m.put("username", ctx.account.safeUsername());
        m.put("uuid", ctx.account.uuid == null ? "" : ctx.account.uuid);
        m.put("displayName", plugin.displayNameForAccount(ctx.account));
        m.put("role", ctx.account.role.name());
        m.put("passwordSet", ctx.account.hasPassword());
        return JsonUtil.obj(m);
    }

    private long loginLockedUntil(String userKey, String ipKey) {
        ConfigValues config = plugin.configValues();
        if (config.loginFailLimit <= 0 || config.loginLockSeconds <= 0) return 0L;
        long now = System.currentTimeMillis();
        cleanupLoginFailures(now);
        return Math.max(stateLockedUntil(userKey, now), stateLockedUntil(ipKey, now));
    }

    private long stateLockedUntil(String key, long now) {
        if (key == null || key.isBlank()) return 0L;
        LoginFailState state = loginFailures.get(key);
        if (state == null) return 0L;
        if (state.lockedUntil <= now) return 0L;
        return state.lockedUntil;
    }

    private long recordFailedLogin(String userKey, String ipKey) {
        ConfigValues config = plugin.configValues();
        if (config.loginFailLimit <= 0) return 0L;
        long now = System.currentTimeMillis();
        long userLockedUntil = recordFailedLoginKey(userKey, now, config);
        long ipLockedUntil = recordFailedLoginKey(ipKey, now, config);
        return Math.max(userLockedUntil, ipLockedUntil);
    }

    private long recordFailedLoginKey(String key, long now, ConfigValues config) {
        if (key == null || key.isBlank()) return 0L;
        LoginFailState state = loginFailures.computeIfAbsent(key, ignored -> new LoginFailState());
        long windowMillis = Math.max(1, config.loginFailWindowSeconds) * 1000L;
        boolean expiredLock = state.lockedUntil > 0L && state.lockedUntil <= now;
        if (state.firstFailureAt <= 0L || now - state.firstFailureAt > windowMillis || expiredLock) {
            state.firstFailureAt = now;
            state.count = 0;
            if (expiredLock) state.lockedUntil = 0L;
        }
        state.count++;
        if (config.loginLockSeconds > 0 && state.count >= config.loginFailLimit) {
            state.lockedUntil = now + config.loginLockSeconds * 1000L;
            state.firstFailureAt = now;
            state.count = 0;
        }
        return state.lockedUntil;
    }

    private void clearLoginFailures(String userKey, String ipKey) {
        if (userKey != null) loginFailures.remove(userKey);
        if (ipKey != null) loginFailures.remove(ipKey);
    }

    private void cleanupLoginFailures(long now) {
        ConfigValues config = plugin.configValues();
        long windowMillis = Math.max(1, config.loginFailWindowSeconds) * 1000L;
        loginFailures.entrySet().removeIf(entry -> {
            LoginFailState state = entry.getValue();
            boolean unlocked = state.lockedUntil <= now;
            boolean oldWindow = state.firstFailureAt <= 0L || now - state.firstFailureAt > windowMillis;
            return unlocked && oldWindow;
        });
    }

    private String loginUserKey(String username) {
        String value = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        return value.isBlank() ? "user:<blank>" : "user:" + value;
    }

    private String loginIpKey(String ip) {
        String value = ip == null ? "" : ip.trim();
        return value.isBlank() ? "ip:<unknown>" : "ip:" + value;
    }

    @Override
    public boolean roleAllowedFromIp(Role role, String ip) {
        return role != Role.ADMIN || adminIpAllowed(ip);
    }

    private boolean adminIpAllowed(String ip) {
        ConfigValues config = plugin.configValues();
        if (config.allowAdminLoginFrom == null || config.allowAdminLoginFrom.isEmpty()) return true;
        return IpAddressMatcher.matchesAny(ip, config.allowAdminLoginFrom);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        byCode.entrySet().removeIf(e -> e.getValue().expiresAt < now || e.getValue().delivered);
        byPoll.entrySet().removeIf(e -> e.getValue().expiresAt < now || e.getValue().delivered);
    }
}
