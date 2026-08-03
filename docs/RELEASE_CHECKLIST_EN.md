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
