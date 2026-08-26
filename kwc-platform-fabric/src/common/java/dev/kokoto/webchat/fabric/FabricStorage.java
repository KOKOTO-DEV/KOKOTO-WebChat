package dev.kokoto.webchat.fabric;

import dev.kokoto.webchat.*;


import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FabricStorage implements WebChatStorage {
    private final KwcFabricRuntime plugin;
    private final File accountsFile;
    private final File sessionsFile;
    private final File pinnedFile;
    private final File knownDisplayNamesFile;

    private final Map<String, Account> accountsById = new ConcurrentHashMap<>();
    private final Map<String, String> usernameIndex = new ConcurrentHashMap<>();
    private final Map<String, Session> sessionsByTokenHash = new ConcurrentHashMap<>();
    private final Map<String, PinnedMessage> pinnedById = new ConcurrentHashMap<>();
    private final Map<String, String> knownDisplayNamesByUuid = new ConcurrentHashMap<>();
    private final Map<String, String> knownUsernamesByUuid = new ConcurrentHashMap<>();

    public FabricStorage(KwcFabricRuntime plugin) {
        this.plugin = plugin;
        File dataDir = plugin.dataDirectory().toFile();
        this.accountsFile = new File(dataDir, "accounts.yml");
        this.sessionsFile = new File(dataDir, "sessions.yml");
        this.pinnedFile = new File(dataDir, "pinned.yml");
        this.knownDisplayNamesFile = new File(dataDir, "known-display-names.yml");
    }

    public synchronized void load() {
        accountsById.clear();
        usernameIndex.clear();
        sessionsByTokenHash.clear();
        pinnedById.clear();
        knownDisplayNamesByUuid.clear();
        knownUsernamesByUuid.clear();

        loadKnownDisplayNames();

        FabricYamlConfiguration accounts = FabricYamlConfiguration.loadConfiguration(accountsFile);
        FabricYamlSection root = accounts.getConfigurationSection("accounts");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                FabricYamlSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                Account a = new Account();
                a.id = id;
                a.username = s.getString("username", id);
                a.uuid = s.getString("uuid", null);
                a.lastDisplayName = s.getString("lastDisplayName", "");
                if ((a.lastDisplayName == null || a.lastDisplayName.isBlank()) && a.uuid != null && !a.uuid.isBlank()) {
                    a.lastDisplayName = knownDisplayNamesByUuid.getOrDefault(a.uuid, "");
                }
                a.role = Role.fromString(s.getString("role", "USER"), Role.USER);
                a.passwordHash = s.getString("passwordHash", null);
                a.local = s.getBoolean("local", false);
                a.createdAt = s.getLong("createdAt", System.currentTimeMillis());
                a.lastLogin = s.getLong("lastLogin", 0);
                putAccountInMemory(a);
            }
        }

        FabricYamlConfiguration sessions = FabricYamlConfiguration.loadConfiguration(sessionsFile);
        FabricYamlSection sr = sessions.getConfigurationSection("sessions");
        if (sr != null) {
            for (String hash : sr.getKeys(false)) {
                FabricYamlSection s = sr.getConfigurationSection(hash);
                if (s == null) continue;
                Session session = new Session();
                session.tokenHash = hash;
                session.accountId = s.getString("accountId", "");
                session.createdAt = s.getLong("createdAt", 0);
                session.expiresAt = s.getLong("expiresAt", 0);
                session.lastIp = s.getString("lastIp", "");
                if (!session.expired() && accountsById.containsKey(session.accountId)) {
                    sessionsByTokenHash.put(hash, session);
                }
            }
        }
        saveSessions();
        loadPinnedMessages();
    }

    public synchronized void saveAll() {
        saveAccounts();
        saveSessions();
        saveKnownDisplayNames();
        savePinnedMessages();
    }

    public synchronized void saveAccounts() {
        FabricYamlConfiguration yml = new FabricYamlConfiguration();
        for (Account a : accountsById.values()) {
            String p = "accounts." + a.id + ".";
            yml.set(p + "username", a.username);
            yml.set(p + "uuid", a.uuid);
            yml.set(p + "lastDisplayName", a.lastDisplayName);
            yml.set(p + "role", a.role.name());
            yml.set(p + "passwordHash", a.passwordHash);
            yml.set(p + "local", a.local);
            yml.set(p + "createdAt", a.createdAt);
            yml.set(p + "lastLogin", a.lastLogin);
        }
        try {
            yml.save(accountsFile);
        } catch (IOException ex) {
            plugin.warn("Failed to save accounts.yml: " + ex.getMessage());
        }
    }

    public synchronized void saveSessions() {
        FabricYamlConfiguration yml = new FabricYamlConfiguration();
        for (Session s : sessionsByTokenHash.values()) {
            if (s.expired()) continue;
            String p = "sessions." + s.tokenHash + ".";
            yml.set(p + "accountId", s.accountId);
            yml.set(p + "createdAt", s.createdAt);
            yml.set(p + "expiresAt", s.expiresAt);
            yml.set(p + "lastIp", s.lastIp);
        }
        try {
            yml.save(sessionsFile);
        } catch (IOException ex) {
            plugin.warn("Failed to save sessions.yml: " + ex.getMessage());
        }
    }


    private synchronized void loadKnownDisplayNames() {
        knownDisplayNamesByUuid.clear();
        knownUsernamesByUuid.clear();
        FabricYamlConfiguration yml = FabricYamlConfiguration.loadConfiguration(knownDisplayNamesFile);
        FabricYamlSection root = yml.getConfigurationSection("players");
        if (root == null) return;
        for (String uuid : root.getKeys(false)) {
            FabricYamlSection s = root.getConfigurationSection(uuid);
            if (s == null) continue;
            String displayName = s.getString("lastDisplayName", "");
            String username = s.getString("username", "");
            if (displayName != null && !displayName.isBlank()) knownDisplayNamesByUuid.put(uuid, displayName);
            if (username != null && !username.isBlank()) knownUsernamesByUuid.put(uuid, username);
        }
    }

    private synchronized void saveKnownDisplayNames() {
        FabricYamlConfiguration yml = new FabricYamlConfiguration();
        for (String uuid : knownDisplayNamesByUuid.keySet()) {
            if (uuid == null || uuid.isBlank()) continue;
            String p = "players." + uuid + ".";
            yml.set(p + "username", knownUsernamesByUuid.getOrDefault(uuid, ""));
            yml.set(p + "lastDisplayName", knownDisplayNamesByUuid.getOrDefault(uuid, ""));
        }
        try {
            yml.save(knownDisplayNamesFile);
        } catch (IOException ex) {
            plugin.warn("Failed to save known-display-names.yml: " + ex.getMessage());
        }
    }

    private synchronized void rememberKnownDisplayName(String uuid, String username, String displayName) {
        if (uuid == null || uuid.isBlank() || displayName == null || displayName.isBlank()) return;
        String oldDisplay = knownDisplayNamesByUuid.put(uuid, displayName);
        String oldUsername = username == null || username.isBlank() ? knownUsernamesByUuid.get(uuid) : knownUsernamesByUuid.put(uuid, username);
        if (!Objects.equals(oldDisplay, displayName) || (username != null && !username.isBlank() && !Objects.equals(oldUsername, username))) {
            saveKnownDisplayNames();
        }
    }

    public String knownDisplayName(String uuid) {
        if (uuid == null || uuid.isBlank()) return "";
        return knownDisplayNamesByUuid.getOrDefault(uuid, "");
    }

    public synchronized void loadPinnedMessages() {
        pinnedById.clear();
        FabricYamlConfiguration yml = FabricYamlConfiguration.loadConfiguration(pinnedFile);
        FabricYamlSection root = yml.getConfigurationSection("pinned");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            FabricYamlSection s = root.getConfigurationSection(id);
            PinnedMessage pin = pinnedMessageFromSection(s);
            if (pin == null || pin.pinId == null || pin.pinId.isBlank()) continue;
            if (pin.message == null || pin.message.isBlank()) continue;
            pinnedById.put(pin.pinId, pin);
        }
    }


    private static PinnedMessage pinnedMessageFromSection(FabricYamlSection s) {
        if (s == null) return null;
        PinnedMessage pin = new PinnedMessage();
        pin.pinId = yamlValue(s.getString("pinId"), s.getName());
        pin.messageId = yamlValue(s.getString("messageId"), "");
        pin.pinnedAt = s.getLong("pinnedAt", System.currentTimeMillis());
        pin.sortOrder = s.getLong("sortOrder", pin.pinnedAt);
        pin.pinnedBy = yamlValue(s.getString("pinnedBy"), "");
        pin.time = s.getLong("time", pin.pinnedAt);
        pin.source = yamlValue(s.getString("source"), "web");
        pin.sender = yamlValue(s.getString("sender"), "Unknown");
        pin.realSender = yamlValue(s.getString("realSender"), "");
        pin.playerUuid = yamlValue(s.getString("playerUuid"), "");
        pin.role = yamlValue(s.getString("role"), "USER");
        pin.message = yamlValue(s.getString("message"), "");
        pin.i18nKey = yamlValue(s.getString("i18nKey"), "");
        pin.i18nArgs = yamlValue(s.getString("i18nArgs"), "");
        pin.hidden = s.getBoolean("hidden", false);
        return pin;
    }

    private static String yamlValue(String value, String fallback) {
        return value == null ? fallback : value;
    }

    public synchronized void savePinnedMessages() {
        FabricYamlConfiguration yml = new FabricYamlConfiguration();
        for (PinnedMessage pin : pinnedById.values()) {
            String safeId = pin.pinId == null ? SecurityUtil.randomToken(8) : pin.pinId.replaceAll("[^A-Za-z0-9._-]", "_");
            String p = "pinned." + safeId + ".";
            yml.set(p + "pinId", pin.pinId);
            yml.set(p + "messageId", pin.messageId);
            yml.set(p + "pinnedAt", pin.pinnedAt);
            yml.set(p + "sortOrder", pin.sortOrder);
            yml.set(p + "pinnedBy", pin.pinnedBy);
            yml.set(p + "time", pin.time);
            yml.set(p + "source", pin.source);
            yml.set(p + "sender", pin.sender);
            yml.set(p + "realSender", pin.realSender);
            yml.set(p + "playerUuid", pin.playerUuid);
            yml.set(p + "role", pin.role);
            yml.set(p + "message", pin.message);
            yml.set(p + "i18nKey", pin.i18nKey);
            yml.set(p + "i18nArgs", pin.i18nArgs);
            yml.set(p + "hidden", pin.hidden);
        }
        try {
            yml.save(pinnedFile);
        } catch (IOException ex) {
            plugin.warn("Failed to save pinned.yml: " + ex.getMessage());
        }
    }

    public List<PinnedMessage> listPinnedMessages() {
        List<PinnedMessage> out = new ArrayList<>(pinnedById.values());
        sortPinnedMessages(out);
        return out;
    }

    private void sortPinnedMessages(List<PinnedMessage> pins) {
        pins.sort(Comparator
                .comparingLong((PinnedMessage p) -> p.sortOrder > 0 ? p.sortOrder : p.pinnedAt).reversed()
                .thenComparing(Comparator.comparingLong((PinnedMessage p) -> p.pinnedAt).reversed())
                .thenComparing(p -> p.pinId == null ? "" : p.pinId));
    }

    public synchronized boolean movePinnedMessage(String pinId, String direction) {
        if (pinId == null || pinId.isBlank()) return false;
        String dir = String.valueOf(direction == null ? "" : direction).trim().toLowerCase(Locale.ROOT);
        List<PinnedMessage> pins = new ArrayList<>(pinnedById.values());
        sortPinnedMessages(pins);
        int index = -1;
        for (int i = 0; i < pins.size(); i++) {
            if (pinId.equals(pins.get(i).pinId)) {
                index = i;
                break;
            }
        }
        if (index < 0) return false;
        int target;
        if ("up".equals(dir)) {
            target = index - 1;
        } else if ("down".equals(dir)) {
            target = index + 1;
        } else {
            return false;
        }
        if (target < 0 || target >= pins.size()) return false;

        normalizePinnedSortOrders(pins);
        PinnedMessage current = pins.get(index);
        PinnedMessage other = pins.get(target);
        long tmp = current.sortOrder;
        current.sortOrder = other.sortOrder;
        other.sortOrder = tmp;
        savePinnedMessages();
        return true;
    }

    private void normalizePinnedSortOrders(List<PinnedMessage> sortedPins) {
        int n = sortedPins == null ? 0 : sortedPins.size();
        for (int i = 0; i < n; i++) {
            // Larger value appears earlier. Spacing by 1000 leaves room for
            // future direct insertion if needed while preserving deterministic order.
            sortedPins.get(i).sortOrder = (long) (n - i) * 1000L;
        }
    }

    public synchronized PinnedMessage pinMessage(ChatMessage msg, String pinnedBy, int maxPins) {
        if (msg == null || msg.id == null || msg.id.isBlank() || msg.hidden) return null;
        for (PinnedMessage existing : pinnedById.values()) {
            if (msg.id.equals(existing.messageId)) return existing;
        }
        if (maxPins > 0 && pinnedById.size() >= maxPins) return null;
        PinnedMessage pin = PinnedMessage.fromMessage(msg, pinnedBy);
        pinnedById.put(pin.pinId, pin);
        savePinnedMessages();
        return pin;
    }

    public synchronized boolean unpinMessage(String pinId) {
        if (pinId == null || pinId.isBlank()) return false;
        boolean removed = pinnedById.remove(pinId) != null;
        if (removed) savePinnedMessages();
        return removed;
    }

    public boolean isMessagePinned(String messageId) {
        if (messageId == null || messageId.isBlank()) return false;
        for (PinnedMessage pin : pinnedById.values()) {
            if (messageId.equals(pin.messageId)) return true;
        }
        return false;
    }

    public Set<String> pinnedMessageIds() {
        Set<String> out = new HashSet<>();
        for (PinnedMessage pin : pinnedById.values()) {
            if (pin.messageId != null && !pin.messageId.isBlank()) out.add(pin.messageId);
        }
        return out;
    }

    public List<String> pinnedMessageTexts() {
        List<String> out = new ArrayList<>();
        for (PinnedMessage pin : pinnedById.values()) {
            if (pin.message != null && !pin.message.isBlank()) out.add(pin.message);
        }
        return out;
    }

    public synchronized Account upsertLinkedAccount(String uuid, String username, Role role) {
        return upsertLinkedAccount(uuid, username, role, "");
    }

    public synchronized Account upsertLinkedAccount(String uuid, String username, Role role, String lastDisplayName) {
        String id = "uuid:" + uuid;
        Account a = accountsById.get(id);
        if (a == null) {
            a = new Account();
            a.id = id;
            a.uuid = uuid;
            a.createdAt = System.currentTimeMillis();
            a.local = false;
        }
        a.username = username;
        String remembered = lastDisplayName;
        if ((remembered == null || remembered.isBlank()) && uuid != null && !uuid.isBlank()) {
            remembered = knownDisplayNamesByUuid.getOrDefault(uuid, "");
        }
        if (remembered != null && !remembered.isBlank()) {
            a.lastDisplayName = remembered;
            rememberKnownDisplayName(uuid, username, remembered);
        }
        if (role.atLeast(a.role)) {
            a.role = role;
        }
        putAccountInMemory(a);
        saveAccounts();
        return a;
    }


    public synchronized void updateLastDisplayName(String uuid, String displayName) {
        updateLastDisplayName(uuid, "", displayName);
    }

    public synchronized void updateLastDisplayName(String uuid, String username, String displayName) {
        if (uuid == null || uuid.isBlank() || displayName == null || displayName.isBlank()) return;

        // Check-only calls are common: online account display rendering, join refresh,
        // and chat forwarding can all observe the current display name repeatedly.
        // Persist only when the actual remembered name or real username changed.
        rememberKnownDisplayName(uuid, username, displayName);

        Account a = accountsById.get("uuid:" + uuid);
        if (a == null) return;

        boolean changed = false;
        if (looksLikeMinecraftUsername(username) && !Objects.equals(a.username, username)) {
            // Only real Minecraft account names may update the login username. Display nicknames must never do this.
            a.username = username;
            changed = true;
        }
        if (!Objects.equals(a.lastDisplayName, displayName)) {
            a.lastDisplayName = displayName;
            changed = true;
        }
        if (!changed) return;

        putAccountInMemory(a);
        saveAccounts();
    }

    public synchronized Account createLocalAccount(String username, Role role) {
        String normalized = normalizeUsername(username);
        String id = "local:" + normalized;
        Account a = accountsById.get(id);
        if (a == null) {
            a = new Account();
            a.id = id;
            a.createdAt = System.currentTimeMillis();
            a.local = true;
        }
        a.username = username;
        a.uuid = null;
        a.role = role;
        putAccountInMemory(a);
        saveAccounts();
        return a;
    }

    public synchronized void setPassword(Account account, String password) {
        account.passwordHash = SecurityUtil.hashPassword(password.toCharArray());
        putAccountInMemory(account);
        saveAccounts();
    }

    public synchronized void setRole(Account account, Role role) {
        account.role = role;
        putAccountInMemory(account);
        saveAccounts();
    }

    public Account findAccountByUsername(String username) {
        if (username == null) return null;
        String normalized = normalizeUsername(username);
        String id = usernameIndex.get(normalized);
        if (id != null) return accountsById.get(id);

        return null;
    }

    public Account findAccountById(String id) {
        return accountsById.get(id);
    }

    public synchronized String createSession(Account account, String ip, int days) {
        long now = System.currentTimeMillis();
        long expiresAt = days <= 0 ? 0L : now + days * 24L * 60L * 60L * 1000L;
        return createSessionUntil(account, ip, now, expiresAt);
    }

    public synchronized String createSessionForDuration(Account account, String ip, long durationMillis) {
        long now = System.currentTimeMillis();
        long expiresAt = durationMillis <= 0L ? 0L : now + durationMillis;
        return createSessionUntil(account, ip, now, expiresAt);
    }

    private String createSessionUntil(Account account, String ip, long createdAt, long expiresAt) {
        String token = SecurityUtil.randomToken(32);
        String hash = SecurityUtil.sha256Hex(token);
        Session s = new Session();
        s.tokenHash = hash;
        s.accountId = account.id;
        s.createdAt = createdAt;
        s.expiresAt = expiresAt;
        s.lastIp = ip == null ? "" : ip;
        sessionsByTokenHash.put(hash, s);
        account.lastLogin = System.currentTimeMillis();
        saveAccounts();
        saveSessions();
        saveKnownDisplayNames();
        savePinnedMessages();
        return token;
    }

    public SessionContext getSession(String token) {
        if (token == null || token.isBlank()) return null;
        String hash = SecurityUtil.sha256Hex(token);
        Session s = sessionsByTokenHash.get(hash);
        if (s == null) return null;
        if (s.expired()) {
            sessionsByTokenHash.remove(hash);
            saveSessions();
            return null;
        }
        Account a = accountsById.get(s.accountId);
        if (a == null) return null;
        return new SessionContext(a, s);
    }

    public synchronized boolean revokeSession(String token) {
        if (token == null || token.isBlank()) return false;
        boolean removed = sessionsByTokenHash.remove(SecurityUtil.sha256Hex(token)) != null;
        if (removed) saveSessions();
        return removed;
    }



    public PlayerIdentity findKnownPlayerByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        String normalized = uuid.trim().toLowerCase(Locale.ROOT);
        Account account = accountsById.get("uuid:" + normalized);
        String username = "";
        String displayName = "";
        if (account != null) {
            username = account.safeUsername();
            displayName = account.lastDisplayName == null ? "" : account.lastDisplayName;
        }
        if (username == null || username.isBlank()) username = knownUsernamesByUuid.getOrDefault(normalized, "");
        if (displayName == null || displayName.isBlank()) displayName = knownDisplayNamesByUuid.getOrDefault(normalized, "");
        if ((username == null || username.isBlank()) && (displayName == null || displayName.isBlank())) return null;
        return new PlayerIdentity(normalized, username, displayName);
    }

    public PlayerIdentity findKnownPlayer(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.trim();
        String qLower = q.toLowerCase(Locale.ROOT);
        String qPlain = plainLookupText(q).toLowerCase(Locale.ROOT);
        PlayerIdentity byUuid = findKnownPlayerByUuid(qLower);
        if (byUuid != null) return byUuid;

        PlayerIdentity displayMatch = null;
        for (PlayerIdentity player : listKnownPlayers("", 0)) {
            if (player.username != null && player.username.equalsIgnoreCase(q)) return player;
            String display = player.displayName == null ? "" : player.displayName;
            if (displayMatch == null && display.equalsIgnoreCase(q)) displayMatch = player;
            if (displayMatch == null && !qPlain.isBlank() && plainLookupText(display).equalsIgnoreCase(qPlain)) displayMatch = player;
        }
        return displayMatch;
    }

    /**
     * Resolves only a player known to this server. Remote relay identities are
     * deliberately excluded so an unqualified name can never jump to another
     * server merely because the same username/display name was observed there.
     */
    public PlayerIdentity findKnownLocalPlayer(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.trim();
        String qLower = q.toLowerCase(Locale.ROOT);
        String qPlain = plainLookupText(q).toLowerCase(Locale.ROOT);

        PlayerIdentity byUuid = findKnownPlayerByUuid(qLower);
        if (byUuid != null && !RemotePlayerRef.isRemote(byUuid.uuid)) return byUuid;

        PlayerIdentity displayMatch = null;
        for (PlayerIdentity player : listKnownPlayers("", 0)) {
            if (player == null || player.uuid == null || RemotePlayerRef.isRemote(player.uuid)) continue;
            if (player.username != null && player.username.equalsIgnoreCase(q)) return player;
            String display = player.displayName == null ? "" : player.displayName;
            if (displayMatch == null && display.equalsIgnoreCase(q)) displayMatch = player;
            if (displayMatch == null && !qPlain.isBlank() && plainLookupText(display).equalsIgnoreCase(qPlain)) displayMatch = player;
        }
        return displayMatch;
    }

    private String plainLookupText(String value) {
        String out = String.valueOf(value == null ? "" : value);
        out = out.replaceAll("(?i)[§&]x(?:[§&][0-9a-f]){6}", "");
        out = out.replaceAll("(?i)&#[0-9a-f]{6}", "");
        out = stripLegacyColors(translateAlternateColorCodes(out));
        if (out == null) out = "";
        return out.trim();
    }

    public List<PlayerIdentity> listKnownPlayers(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String qPlain = plainLookupText(query).toLowerCase(Locale.ROOT);
        int max = limit <= 0 ? Integer.MAX_VALUE : limit;
        Map<String, PlayerIdentity> byUuid = new LinkedHashMap<>();

        for (Account account : accountsById.values()) {
            if (account == null || account.uuid == null || account.uuid.isBlank()) continue;
            String uuid = account.uuid.trim().toLowerCase(Locale.ROOT);
            String username = account.safeUsername();
            String display = account.lastDisplayName == null ? "" : account.lastDisplayName;
            String rememberedUser = knownUsernamesByUuid.getOrDefault(uuid, "");
            String rememberedDisplay = knownDisplayNamesByUuid.getOrDefault(uuid, "");
            if ((username == null || username.isBlank()) && !rememberedUser.isBlank()) username = rememberedUser;
            if ((display == null || display.isBlank()) && !rememberedDisplay.isBlank()) display = rememberedDisplay;
            byUuid.put(uuid, new PlayerIdentity(uuid, username, display));
        }
        for (String uuidRaw : knownDisplayNamesByUuid.keySet()) {
            if (uuidRaw == null || uuidRaw.isBlank()) continue;
            String uuid = uuidRaw.trim().toLowerCase(Locale.ROOT);
            if (byUuid.containsKey(uuid)) continue;
            byUuid.put(uuid, new PlayerIdentity(uuid, knownUsernamesByUuid.getOrDefault(uuid, ""), knownDisplayNamesByUuid.getOrDefault(uuid, "")));
        }
        for (String uuidRaw : knownUsernamesByUuid.keySet()) {
            if (uuidRaw == null || uuidRaw.isBlank()) continue;
            String uuid = uuidRaw.trim().toLowerCase(Locale.ROOT);
            if (byUuid.containsKey(uuid)) continue;
            byUuid.put(uuid, new PlayerIdentity(uuid, knownUsernamesByUuid.getOrDefault(uuid, ""), knownDisplayNamesByUuid.getOrDefault(uuid, "")));
        }

        List<PlayerIdentity> out = new ArrayList<>();
        for (PlayerIdentity player : byUuid.values()) {
            if (player.uuid == null || player.uuid.isBlank()) continue;
            if (!q.isBlank()) {
                String haystack = (player.username + " " + player.displayName + " " + player.uuid).toLowerCase(Locale.ROOT);
                String plainHaystack = (plainLookupText(player.username) + " " + plainLookupText(player.displayName) + " " + player.uuid).toLowerCase(Locale.ROOT);
                if (!haystack.contains(q) && (qPlain.isBlank() || !plainHaystack.contains(qPlain))) continue;
            }
            out.add(player);
        }
        out.sort(Comparator
                .comparingInt((PlayerIdentity p) -> RemotePlayerRef.isRemote(p.uuid) ? 1 : 0)
                .thenComparing(p -> String.valueOf(p.displayName == null || p.displayName.isBlank() ? p.username : p.displayName).toLowerCase(Locale.ROOT))
                .thenComparing(p -> String.valueOf(p.username).toLowerCase(Locale.ROOT))
                .thenComparing(p -> String.valueOf(p.uuid)));
        if (out.size() > max) return new ArrayList<>(out.subList(0, max));
        return out;
    }

    public List<Account> listAccounts() {
        List<Account> out = new ArrayList<>(accountsById.values());
        out.sort(Comparator.comparing(a -> a.safeUsername().toLowerCase(Locale.ROOT)));
        return out;
    }

    public List<SessionContext> listSessions() {
        List<SessionContext> out = new ArrayList<>();
        for (Session s : sessionsByTokenHash.values()) {
            if (s.expired()) continue;
            Account a = accountsById.get(s.accountId);
            if (a != null) out.add(new SessionContext(a, s));
        }
        out.sort(Comparator.comparingLong(ctx -> ctx.session.createdAt));
        return out;
    }

    public synchronized int revokeSessionsForAccount(Account account) {
        if (account == null) return 0;
        int before = sessionsByTokenHash.size();
        sessionsByTokenHash.entrySet().removeIf(e -> account.id.equals(e.getValue().accountId));
        int removed = before - sessionsByTokenHash.size();
        if (removed > 0) saveSessions();
        return removed;
    }

    public synchronized int revokeSessionsForUsername(String username) {
        Account account = findAccountByUsername(username);
        return revokeSessionsForAccount(account);
    }

    public synchronized int cleanupExpiredSessions() {
        int before = sessionsByTokenHash.size();
        sessionsByTokenHash.entrySet().removeIf(e -> e.getValue().expired());
        int removed = before - sessionsByTokenHash.size();
        if (removed > 0) saveSessions();
        return removed;
    }

    @Override
    public synchronized SessionPolicyUpdate recalculateSessionExpiry(boolean adminSessions, long durationMillis) {
        long now = System.currentTimeMillis();
        int updated = 0;
        int expired = 0;
        java.util.Iterator<java.util.Map.Entry<String, Session>> it = sessionsByTokenHash.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, Session> entry = it.next();
            Session session = entry.getValue();
            if (session == null || session.expired()) {
                it.remove();
                expired++;
                continue;
            }
            Account account = accountsById.get(session.accountId);
            if (account == null) {
                it.remove();
                expired++;
                continue;
            }
            boolean isAdmin = account.role == Role.ADMIN;
            if (isAdmin != adminSessions) continue;
            long newExpiry = 0L;
            if (durationMillis > 0L) {
                long base = Math.max(0L, session.createdAt);
                if (base > Long.MAX_VALUE - durationMillis) newExpiry = Long.MAX_VALUE;
                else newExpiry = base + durationMillis;
            }
            if (newExpiry > 0L && newExpiry <= now) {
                it.remove();
                expired++;
                continue;
            }
            if (session.expiresAt != newExpiry) {
                session.expiresAt = newExpiry;
                updated++;
            }
        }
        if (updated > 0 || expired > 0) saveSessions();
        return new SessionPolicyUpdate(updated, expired);
    }

    private void putAccountInMemory(Account a) {
        accountsById.put(a.id, a);
        if (a.username != null) {
            usernameIndex.put(normalizeUsername(a.username), a.id);
        }
    }

    private static boolean looksLikeMinecraftUsername(String username) {
        return username != null && username.matches("[A-Za-z0-9_]{2,16}");
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String translateAlternateColorCodes(String value) {
        if (value == null || value.isEmpty()) return "";
        char[] chars = value.toCharArray();
        for (int i = 0; i + 1 < chars.length; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) >= 0) {
                chars[i] = '\u00A7';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    private static String stripLegacyColors(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)\\u00A7[0-9A-FK-ORX]", "");
    }
}
