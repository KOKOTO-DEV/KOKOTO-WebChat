# KOKOTO WebChat Installation and Troubleshooting

## Requirements

- Bukkit/Spigot/Paper-compatible server or compatible fork
- Java 17 or newer for build/runtime
- BlueMap plugin and a working BlueMap webroot
- The chat API port must be reachable by the browser. Default: `8899/tcp`
- DiscordSRV is optional and only needed when enabling the Discord bridge

## Build

```bash
mvn clean package
```

Output:

```text
kwc-platform-bukkit/target/KOKOTO-WebChat-5.3.0-Bukkit-1.18-26.2.jar
```

On Windows, the root validator can also be used as a convenient platform build helper:

```bat

> `validate-release-windows.bat`, its required PowerShell helpers, and development regression harnesses under `validation/` are included in the source archive. No separate validation-tools package is required to run the release build.

validate-release-windows.bat --bukkit
validate-release-windows.bat --bukkit --fast
validate-release-windows.bat --parallel
```

`--fast` skips `clean` and reuses existing outputs/caches for iteration. `--parallel` preserves clean/fast semantics; when Bukkit is selected it builds Bukkit first, then starts the remaining selected loaders concurrently after Bukkit passes. `validate-release-windows.bat --parallel` is therefore still a clean full release validation. The main console shows live overall/platform target progress; in `--parallel` mode each active platform also gets a separate live build window, while detailed logs remain in `validation-logs/`.

## Install or upgrade

1. Stop the Minecraft server.
2. Replace the old KOKOTO WebChat jar in `plugins/` with the new jar.
3. Start the server.
4. Check `plugins/KOKOTO-WebChat/config.yml`.
5. Run `/kchat reload` or restart if you changed important paths.
6. `/kchat reload` requests `bluemap reload light` automatically after changing the BlueMap webapp integration. If that dispatch fails, run `/bluemap reload light` manually.
7. Hard-refresh the browser.

## Verify web addon registration

```bash
grep -R "bluemap-web-chat" -n /opt/minecraft/server/plugins/BlueMap/webapp.conf
```

The entries should include the current version query, for example:

```text
addons/kokoto-web-chat/config.js?v=5.3.0-<cache-token>
addons/kokoto-web-chat/chat.js?v=5.3.0-<cache-token>
addons/kokoto-web-chat/chat.css?v=5.3.0-<cache-token>
```

Also verify the actual web files were updated:

```bash
find /opt/minecraft/server -path "*addons/kokoto-web-chat/chat.js" -printf "%p  %TY-%Tm-%Td %TH:%TM\n"
```

## BlueMap webroot mismatch

If `/api/config` works but the chat panel does not appear, BlueMap may be serving a different webroot than the one configured in KOKOTO WebChat.

Check BlueMap's `webapp.conf` and make sure these match your setup:

```yaml
adapters:
  bluemap:
    bluemap-web-root: "bluemap/web"
    bluemap-webapp-conf: "plugins/BlueMap/webapp.conf"
    addon-path: "addons/kokoto-web-chat"
```

## Browser cache

When testing web UI changes, open DevTools, enable **Network -> Disable cache**, then hard-refresh the page.

You can also check the loaded version in the console:

```js
[...document.scripts]
  .filter(s => s.src.includes("bluemap-web-chat"))
  .map(s => s.src)
```

## BlueMap still loads an older addon version

If BlueMap still loads an older KOKOTO WebChat addon version after updating, run `/kchat reload` once more or restart the server, then hard-refresh the browser.

## HTTPS reverse proxy

For public servers, use HTTPS through Caddy or nginx. See:

- `docs/en/CADDY_HTTPS.md` and `examples/caddy/Caddyfile`
- `docs/en/NGINX_HTTPS.md` and `examples/nginx/kchat.conf`

