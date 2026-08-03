# BlueMapWebChat Installation and Troubleshooting

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
target/BlueMapWebChat-4.6.0.jar
```

## Install or upgrade

1. Stop the Minecraft server.
2. Replace the old BlueMapWebChat jar in `plugins/` with the new jar.
3. Start the server.
4. Check `plugins/BlueMapWebChat/config.yml`.
5. Run `/bmchat reload` or restart if you changed important paths.
6. Run `/bluemap reload` if BlueMap does not pick up webapp changes automatically.
7. Hard-refresh the browser.

## Verify web addon registration

```bash
grep -R "bluemap-web-chat" -n /opt/minecraft/server/plugins/BlueMap/webapp.conf
```

The entries should include the current version query, for example:

```text
addons/bluemap-web-chat/config.js?v=4.6.0-<cache-token>
addons/bluemap-web-chat/chat.js?v=4.6.0-<cache-token>
addons/bluemap-web-chat/chat.css?v=4.6.0-<cache-token>
```

Also verify the actual web files were updated:

```bash
find /opt/minecraft/server -path "*addons/bluemap-web-chat/chat.js" -printf "%p  %TY-%Tm-%Td %TH:%TM\n"
```

## BlueMap webroot mismatch

If `/api/config` works but the chat panel does not appear, BlueMap may be serving a different webroot than the one configured in BlueMapWebChat.

Check BlueMap's `webapp.conf` and make sure these match your setup:

```yaml
web-addon:
  bluemap-web-root: "bluemap/web"
  bluemap-webapp-conf: "plugins/BlueMap/webapp.conf"
  addon-path: "addons/bluemap-web-chat"
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

If BlueMap still loads an older BlueMapWebChat addon version after updating, run `/bmchat reload` once more or restart the server, then hard-refresh the browser.

## HTTPS reverse proxy

For public servers, use HTTPS through Caddy or nginx. See:

- `docs/CADDY_HTTPS_EN.md` and `examples/caddy/Caddyfile`
- `docs/NGINX_HTTPS_EN.md` and `examples/nginx/bluemapwebchat.conf`

