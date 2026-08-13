# BlueMapWebChat Release Checklist

Before publishing a release:

- Update `pom.xml` version.
- Update `src/main/resources/plugin.yml` version.
- Update the version comment in `src/main/resources/config.yml`.
- Update README build output examples.
- Add a changelog entry.
- Run JavaScript syntax checks:

```bash
node --check inner.js
node --check src/main/resources/web/chat.js
```

- Validate YAML files:

```bash
python3 - <<'PY'
import yaml, glob
for path in ['src/main/resources/config.yml'] + glob.glob('src/main/resources/lang/*.yml'):
    with open(path, encoding='utf-8') as f:
        yaml.safe_load(f)
    print('OK', path)
PY
```

- Verify language key parity against `en-US.yml`.
- Build with Maven:

```bash
mvn clean package
```

- Test that `webapp.conf` points to the new version query.
- Test browser loading with DevTools cache disabled.

- [ ] `USER_MANUAL_EN.md`, `USER_MANUAL_KO.md`, `USER_MANUAL_JA.md`, and `USER_MANUAL_ZH_CN.md` exist, use the same major section structure, and reflect current commands, permissions, defaults, and feature behavior.

## 4.6.2 delivery-state release checks

- [ ] `pom.xml`, `plugin.yml`, bundled `config-version`, artifact examples, and current manuals show `4.6.2`.
- [ ] A reviewed 4.6.1 config generates `config-migration-4.6.2.yml` with only `config-version: "4.6.2"` when there are no other real differences.
- [ ] An unqualified same-name DM resolves only to the current-server player; a remote player is used only with explicit server-scoped metadata.
- [ ] Remote DM starts as `pending`, becomes `delivered` only after destination-store acknowledgement, and becomes `failed` on HTTP 502, timeout, routing, or destination rejection.
- [ ] Retrying a failed DM reuses the same relay ID and does not duplicate the receiver message.
- [ ] Restarting with an interrupted pending DM restores it as `failed` / retryable.
- [ ] Web DM request retry reuses the same client message ID when the browser did not receive a response.
- [ ] Group-chat web retry reuses the same client message ID and returns the existing stored message instead of inserting a duplicate.
- [ ] Hub/chain private relay reports success only after the final destination confirms storage.
- [ ] Every DM and group-chat message exposes read state. A 1:1 DM shows the short `Unread` label before the recipient reads it and `✓` afterward; group chat shows the unread-recipient count and changes to `✓` at zero. All DM and group-chat messages are included in the receipt calculation.

## 4.6.0 relay, DM, and game reply checks

- [ ] A config with a missing/different `config-version` generates `config-migration-4.6.0.yml` without overwriting `config.yml`, even when `config-version` is the only required change.
- [ ] Matching `config-version: "4.6.0"` skips comparison and removes stale same-version migration guidance.
- [ ] The migration fragment contains only the 4.6.0 missing keys and the two changed Discord format defaults as YAML settings; customized values and metadata sections are omitted.
- [ ] Relay starts with expected `activePeers=<usable>/<configured>` and invalid peers log a reason.
- [ ] HTTPS relay path, HMAC failures, unknown peer, clock skew, and reload are tested.
- [ ] The current server badge is hidden; remote server badges are stable and distinguish different IDs.
- [ ] Game/Discord output identifies the origin server.
- [ ] With multiple servers sharing one Discord channel, a native DiscordSRV game message receives exactly one origin-server label and one emoji-link set.
- [ ] Local game sender click suggests `/w`; web/remote-game sender click suggests `/bmchat dm`; non-URL body click suggests `/bmchat reply`; URL click remains a link.
- [ ] `capture-game-whispers` mirrors messages to both DM participants only when DM is enabled.
- [ ] SQLite relay-column migration preserves existing history.
- [ ] en-US, ko-KR, ja-JP, and zh-CN language key sets match.
- [ ] ImageEmojis-Bero 1.9.0: shared-folder PNG, normal chat, `/bmchat reply`, `/bmchat dm`, URL+emoji click coexistence, and remote relay rendering are verified.

## 4.6.1 remote DM search and administrator audit checks

- [ ] `pom.xml`, `plugin.yml`, bundled `config-version`, artifact examples, and cache-facing docs show `4.6.1`.
- [ ] A config marked `4.6.0` generates `config-migration-4.6.1.yml` containing only `direct-message.admin-audit.enabled: false` and `config-version: "4.6.1"` unless other settings are actually missing.
- [ ] A relayed game or linked-web sender with a player UUID appears in the existing DM recipient search by display name, real name, and UUID; UUID-less guest/Discord senders do not.
- [ ] Remote-player identities are restored from retained public history after restart.
- [ ] Ordinary ADMIN/MODERATOR accounts cannot read other users' DM bodies.
- [ ] A listed `private-chat-super-admins` account still sees metadata only while `direct-message.admin-audit.enabled` is false.
- [ ] With both gates enabled, administrator DM rows open a read-only audit view, sending/hiding/mark-read controls remain unavailable, and globally hidden messages are excluded.
- [ ] Each audit page read appends `admin.dm-audit-read` with actor, thread ID, pagination, limit, and returned count; message bodies are not copied into the audit log.
